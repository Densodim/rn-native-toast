package expo.modules.nativetoast

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Нативная вьюха тоста — создаётся программно, без XML.
 *
 * Структура:
 *   FrameLayout (корень, обрезка по скруглённым углам)
 *     ├── BlurBackground (из BlurFactory)
 *     ├── ColorOverlay (цвет по типу тоста)
 *     └── LinearLayout (контент)
 *           ├── LinearLayout (текстовая колонка)
 *           │     ├── Title TextView
 *           │     └── Message TextView
 *
 * RTL: Используется LAYOUT_DIRECTION_LOCALE — зеркалится автоматически.
 * Доступность: Title + message объявляются как один accessibilityLiveRegion.
 * Тёмная тема: Определяется через Configuration.uiMode.
 * Dynamic Island / вырез: Обрабатывается ToastManager через WindowInsets.
 */
class ToastView(context: Context) : FrameLayout(context) {

    private val titleView: TextView
    private val messageView: TextView
    private val contentContainer: LinearLayout
    private val textColumn: LinearLayout
    private var colorOverlay: View
    private var blurBackground: View? = null

    private val cornerRadiusPx = dpToPx(14f)
    private val horizontalPaddingPx = dpToPx(16f).toInt()
    private val verticalPaddingPx = dpToPx(16f).toInt()
    private val textSpacingPx = dpToPx(4f).toInt()

    init {
        // Обрезка по скруглённым углам
        clipToOutline = true
        outlineProvider = RoundRectOutlineProvider(cornerRadiusPx)

        // Цветной оверлей (тип тоста)
        colorOverlay = View(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        // Заголовок
        titleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        // Сообщение
        messageView = TextView(context).apply {
            setTextColor(Color.argb(220, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        // Текстовая колонка
        textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(titleView)
        textColumn.addView(messageView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = textSpacingPx
        })

        // Горизонтальная строка контента
        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        }
        contentContainer.addView(textColumn)

        addView(colorOverlay)
        addView(contentContainer)

        // Доступность: объявлять как вежливый live region
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

        // Поддержка RTL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
    }

    /**
     * Привязать конфигурацию к вьюхе — паттерн переиспользования.
     *
     * @param config         Конфигурация тоста
     * @param blurredBitmap  Размытый bitmap контента под тостом (null = фоллбэк)
     */
    fun bind(config: ToastConfigData, blurredBitmap: Bitmap? = null) {
        titleView.text = config.titleKey
        messageView.text = config.messageKey

        // Видимость сообщения
        messageView.visibility = if (config.messageKey.isEmpty()) View.GONE else View.VISIBLE

        // Цвет оверлея по типу
        val overlayColor = getTypeColor(config.type)
        colorOverlay.setBackgroundColor(overlayColor)

        // Блюр-фон
        val isDarkMode = isDarkMode()
        blurBackground?.let { removeView(it) }
        blurBackground = BlurFactory.createBlurBackground(context, config.blurStyle, isDarkMode, blurredBitmap).also {
            addView(it, 0) // Вставляем позади всего
        }

        // Описание для скринридера
        contentDescription = "${config.titleKey}. ${config.messageKey}"
    }

    /**
     * Переопределяем onMeasure, чтобы высота определялась только по contentContainer.
     * Фоновые слои (blur, colorOverlay) растягиваются под размер контента.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Сначала замеряем contentContainer чтобы узнать нужную высоту
        contentContainer.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val contentHeight = contentContainer.measuredHeight

        // Высота определяется контентом
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)

        // Замеряем все children с фиксированной высотой
        super.onMeasure(widthMeasureSpec, exactHeightSpec)
    }

    /** Цвет оверлея по типу тоста */
    private fun getTypeColor(type: ToastType): Int {
        return when (type) {
            ToastType.ERROR -> Color.argb(180, 220, 38, 38)     // красный
            ToastType.SUCCESS -> Color.argb(150, 22, 163, 74)   // зелёный
            ToastType.WARNING -> Color.argb(160, 234, 179, 8)   // жёлтый
            ToastType.INFO -> Color.argb(150, 37, 99, 235)      // синий
        }
    }

    /** Проверка тёмной темы */
    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        )
    }

    /**
     * OutlineProvider для обрезки по скруглённым углам.
     */
    private class RoundRectOutlineProvider(private val radiusPx: Float) :
        android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
}
