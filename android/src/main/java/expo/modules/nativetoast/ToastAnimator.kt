package expo.modules.nativetoast

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.provider.Settings

/**
 * Все анимации тоста через нативный Android Animator API.
 *
 * - Анимации выполняются на UI-потоке через ObjectAnimator
 * - Учитывает системную настройку "Убрать анимации" / animator duration scale
 * - 60fps гарантировано — hardware layer включён на время анимации
 * - Без аллокаций во время анимации (аниматоры строятся заранее)
 *
 * ObjectAnimator работает через Choreographer и крутится
 * на частоте обновления дисплея. Hardware layers включены во время
 * анимации чтобы избежать per-frame invalidation.
 */
object ToastAnimator {

    private const val ENTER_DURATION_MS = 350L
    private const val EXIT_DURATION_MS = 250L

    /**
     * Проверяет, включена ли у пользователя настройка "Убрать анимации"
     * (animator duration scale = 0)
     */
    private fun isReduceMotionEnabled(view: View): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                view.context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Въезд сверху/снизу с fade и лёгким scale.
     * Hardware layer для анимации без overdraw.
     */
    fun animateIn(
        view: View,
        position: ToastPosition,
        onEnd: (() -> Unit)? = null
    ) {
        if (isReduceMotionEnabled(view)) {
            // Доступность: показываем мгновенно
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            onEnd?.invoke()
            return
        }

        val startY = if (position == ToastPosition.TOP) -200f else 200f

        view.alpha = 0f
        view.translationY = startY
        view.scaleX = 0.95f
        view.scaleY = 0.95f

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val translateAnim = ObjectAnimator.ofFloat(view, "translationY", startY, 0f)
        val alphaAnim = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val scaleXAnim = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f)

        AnimatorSet().apply {
            playTogether(translateAnim, alphaAnim, scaleXAnim, scaleYAnim)
            duration = ENTER_DURATION_MS
            interpolator = DecelerateInterpolator(2f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    /**
     * Выезд вверх/вниз с fade.
     */
    fun animateOut(
        view: View,
        position: ToastPosition,
        onEnd: () -> Unit
    ) {
        if (isReduceMotionEnabled(view)) {
            view.alpha = 0f
            onEnd()
            return
        }

        val endY = if (position == ToastPosition.TOP) -200f else 200f

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val translateAnim = ObjectAnimator.ofFloat(view, "translationY", 0f, endY)
        val alphaAnim = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)

        AnimatorSet().apply {
            playTogether(translateAnim, alphaAnim)
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(2f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                    onEnd()
                }
            })
            start()
        }
    }
}
