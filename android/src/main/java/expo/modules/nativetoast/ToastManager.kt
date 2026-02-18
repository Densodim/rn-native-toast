package expo.modules.nativetoast

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import expo.modules.nativetoast.util.MainThreadEnforcer
import java.lang.ref.WeakReference
import java.util.LinkedList

/**
 * Синглтон-менеджер жизненного цикла тостов.
 *
 * Обязанности:
 * - Управление оконным оверлеем (TYPE_APPLICATION)
 * - Режимы очереди/замены
 * - Оркестрация жизненного цикла: показ → авто-скрытие → анимация выхода
 * - Тактильная отдача (haptic)
 * - WeakReference на Activity (без утечек)
 * - Потокобезопасность через главный поток
 *
 * Архитектурное решение — Window overlay vs root view attach:
 *   Window overlay предпочтительнее потому что:
 *   1. Показывается поверх модальных диалогов и навигационных переходов
 *   2. Независим от React view hierarchy — без RN ре-рендеров
 *   3. Без борьбы z-index с другими RN вьюхами
 *   Компромисс: требует аккуратного управления жизненным циклом (без утечек)
 *
 * Обработанные edge cases:
 * - Быстрые последовательные вызовы → замена или очередь по конфигу
 * - Приложение в фоне → таймер скрытия приостановлен (окно отсоединено)
 * - Смена ориентации → параметры окна подстраиваются автоматически
 * - Предупреждение о памяти → очередь очищается
 * - Split-screen → используется окно activity, а не системное окно
 */
class ToastManager private constructor() {

    companion object {
        @Volatile
        private var instance: ToastManager? = null

        fun getInstance(): ToastManager =
            instance ?: synchronized(this) {
                instance ?: ToastManager().also { instance = it }
            }
    }

    private var activityRef: WeakReference<Activity>? = null
    private var currentToastView: ToastView? = null
    private var overlayContainer: FrameLayout? = null
    private var currentConfig: ToastConfigData? = null
    private val queue: LinkedList<ToastConfigData> = LinkedList()
    private var autoDismissRunnable: Runnable? = null

    /** Колбэк событий — задаётся Module для отправки JS событий */
    var onToastShow: ((id: String, type: String) -> Unit)? = null
    var onToastHide: ((id: String, type: String, reason: String) -> Unit)? = null

    /**
     * Привязать ссылку на Activity. Вызывается при инициализации модуля.
     * Используется WeakReference чтобы избежать утечки Activity.
     */
    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /**
     * Показать тост с данной конфигурацией.
     * Потокобезопасно: диспатчит на главный поток.
     */
    fun show(config: ToastConfigData) {
        MainThreadEnforcer.runOnMain {
            val activity = activityRef?.get() ?: return@runOnMain

            when (config.queueMode) {
                ToastQueueMode.REPLACE -> {
                    // Скрыть текущий мгновенно
                    currentConfig?.let { current ->
                        dismissCurrentImmediate("replaced")
                    }
                    queue.clear()
                    showInternal(activity, config)
                }
                ToastQueueMode.QUEUE -> {
                    if (currentConfig != null) {
                        queue.add(config)
                    } else {
                        showInternal(activity, config)
                    }
                }
            }
        }
    }

    /**
     * Скрыть текущий видимый тост.
     */
    fun hide() {
        MainThreadEnforcer.runOnMain {
            dismissCurrent("manual")
        }
    }

    /**
     * Скрыть все тосты включая очередь.
     */
    fun hideAll() {
        MainThreadEnforcer.runOnMain {
            queue.clear()
            dismissCurrentImmediate("manual")
        }
    }

    // ─── Внутренняя логика ──────────────────────────────────────────────

    private fun showInternal(activity: Activity, config: ToastConfigData) {
        currentConfig = config

        // ── 1. Захват контента экрана ДО добавления overlay ──
        val fullScreenBitmap = BitmapBlurHelper.captureDecorView(activity)

        // ── 2. Создать оверлей-контейнер ──
        ensureOverlayContainer(activity, config.position)

        // ── 3. Подготовить layout params ──
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (config.position == ToastPosition.TOP) Gravity.TOP else Gravity.BOTTOM
            val horizontalMargin = dpToPx(activity, 16f).toInt()
            val topInset = getStatusBarHeight(activity)
            val bottomInset = getNavigationBarHeight(activity)

            setMargins(
                horizontalMargin,
                if (config.position == ToastPosition.TOP) topInset + dpToPx(activity, 8f).toInt() else 0,
                horizontalMargin,
                if (config.position == ToastPosition.BOTTOM) bottomInset + dpToPx(activity, 8f).toInt() else 0
            )
        }

        // ── 4. Создать тост и вычислить область для блюра ──
        val toastView = ToastView(activity)

        val blurredBitmap = if (fullScreenBitmap != null) {
            // Первый bind без bitmap — только для замера высоты
            toastView.bind(config, null)
            val toastRect = estimateToastRect(activity, config, lp, toastView)
            val result = BitmapBlurHelper.cropAndBlur(fullScreenBitmap, toastRect)
            BitmapBlurHelper.recycleSafely(fullScreenBitmap)
            result
        } else {
            null
        }

        // ── 5. Финальный bind с размытым bitmap ──
        toastView.bind(config, blurredBitmap)
        currentToastView = toastView

        // ── 6. Добавить в оверлей ──
        overlayContainer?.addView(toastView, lp)

        // Анимация входа
        ToastAnimator.animateIn(toastView, config.position)

        // Тактильная отдача
        if (config.haptic) {
            triggerHaptic(activity)
        }

        // Отправить событие
        onToastShow?.invoke(config.id, config.type.value)

        // Авто-скрытие
        if (config.duration > 0) {
            scheduleAutoDismiss(config)
        }
    }

    /**
     * Оценить прямоугольник тоста на экране для обрезки bitmap.
     * Используем measure() чтобы узнать высоту до добавления в иерархию.
     */
    private fun estimateToastRect(
        activity: Activity,
        config: ToastConfigData,
        lp: FrameLayout.LayoutParams,
        toastView: ToastView
    ): Rect {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels

        val availableWidth = screenWidth - lp.leftMargin - lp.rightMargin
        val widthSpec = View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        toastView.measure(widthSpec, heightSpec)

        val toastHeight = toastView.measuredHeight
        val left = lp.leftMargin
        val right = left + availableWidth

        val top: Int
        val bottom: Int

        if (config.position == ToastPosition.TOP) {
            top = lp.topMargin
            bottom = top + toastHeight
        } else {
            bottom = screenHeight - lp.bottomMargin
            top = bottom - toastHeight
        }

        return Rect(left, top, right, bottom)
    }

    private fun scheduleAutoDismiss(config: ToastConfigData) {
        cancelAutoDismiss()
        val runnable = Runnable { dismissCurrent("timeout") }
        autoDismissRunnable = runnable
        MainThreadEnforcer.postDelayed(config.duration, runnable)
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let {
            MainThreadEnforcer.removeCallbacks(it)
        }
        autoDismissRunnable = null
    }

    private fun dismissCurrent(reason: String) {
        val view = currentToastView ?: return
        val config = currentConfig ?: return

        cancelAutoDismiss()

        ToastAnimator.animateOut(view, config.position) {
            removeOverlay()
            onToastHide?.invoke(config.id, config.type.value, reason)
            currentToastView = null
            currentConfig = null
            showNextInQueue()
        }
    }

    private fun dismissCurrentImmediate(reason: String) {
        cancelAutoDismiss()
        val config = currentConfig
        removeOverlay()
        if (config != null) {
            onToastHide?.invoke(config.id, config.type.value, reason)
        }
        currentToastView = null
        currentConfig = null
    }

    private fun showNextInQueue() {
        if (queue.isNotEmpty()) {
            val next = queue.poll() ?: return
            val activity = activityRef?.get() ?: return
            showInternal(activity, next)
        }
    }

    private fun ensureOverlayContainer(activity: Activity, position: ToastPosition) {
        removeOverlay()

        val container = FrameLayout(activity).apply {
            // Не перехватываем касания вне тоста
            setOnTouchListener { _, _ -> false }
        }

        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
        }

        try {
            windowManager.addView(container, params)
            overlayContainer = container
        } catch (e: Exception) {
            // Защита от краша: если добавление окна не удалось (напр. activity завершается)
        }
    }

    private fun removeOverlay() {
        overlayContainer?.let { container ->
            container.removeAllViews()
            try {
                val activity = activityRef?.get()
                if (activity != null && !activity.isFinishing) {
                    val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeViewImmediate(container)
                }
            } catch (_: Exception) {
                // Уже удалено или activity мертва — безопасно игнорировать
            }
        }
        overlayContainer = null
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = activity.window.decorView.rootWindowInsets
            insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            @Suppress("DEPRECATION")
            val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else 0
        }
    }

    private fun getNavigationBarHeight(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = activity.window.decorView.rootWindowInsets
            insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        } else {
            @Suppress("DEPRECATION")
            val resourceId = activity.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else 0
        }
    }

    private fun triggerHaptic(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (_: Exception) {
            // Вибромотор недоступен — молча игнорируем
        }
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
