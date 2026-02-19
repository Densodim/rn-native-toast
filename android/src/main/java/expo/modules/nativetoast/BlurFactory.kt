package expo.modules.nativetoast

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Фабрика для создания блюр-фона тоста.
 *
 * Стратегия:
 * - Если передан blurredBitmap — реальный блюр через ImageView + тонирующий оверлей.
 *   Bitmap захватывается из decorView и размывается stack blur алгоритмом
 *   в BitmapBlurHelper. Работает на ВСЕХ API уровнях (minSdk 24+).
 * - Если bitmap = null (ошибка захвата, activity не готова) — полупрозрачный
 *   цветной фоллбэк.
 */
object BlurFactory {

    /** Bitmap blur работает на всех поддерживаемых API уровнях */
    fun supportsBlur(): Boolean = true

    /**
     * Создать вьюху с блюр-фоном.
     *
     * @param context        Android контекст
     * @param blurStyle      Вариант блюра (light/dark/systemMaterial)
     * @param isDarkMode     Включена ли тёмная тема
     * @param blurredBitmap  Размытый bitmap контента под тостом (из BitmapBlurHelper)
     * @return View с блюр/фоллбэк фоном
     */
    fun createBlurBackground(
        context: Context,
        blurStyle: BlurStyle,
        isDarkMode: Boolean,
        blurredBitmap: Bitmap? = null
    ): View {
        return if (blurredBitmap != null) {
            createBitmapBlurBackground(context, blurStyle, isDarkMode, blurredBitmap)
        } else {
            createFallbackBackground(context, blurStyle, isDarkMode)
        }
    }

    /**
     * Реальный блюр: ImageView с размытым bitmap + тонирующий оверлей.
     * Создаёт эффект frosted glass как UIVisualEffectView на iOS.
     */
    private fun createBitmapBlurBackground(
        context: Context,
        blurStyle: BlurStyle,
        isDarkMode: Boolean,
        blurredBitmap: Bitmap
    ): View {
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Размытый контент
        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageBitmap(blurredBitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Тонирующий оверлей для frosted glass эффекта
        val tintLayer = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getBitmapTintColor(blurStyle, isDarkMode))
        }

        container.addView(imageView)
        container.addView(tintLayer)
        return container
    }

    /**
     * Фоллбэк — полупрозрачный фон без реального блюра.
     * Используется если захват bitmap не удался.
     */
    private fun createFallbackBackground(
        context: Context,
        blurStyle: BlurStyle,
        isDarkMode: Boolean
    ): View {
        return View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getFallbackColor(blurStyle, isDarkMode))
        }
    }

    private fun getFallbackColor(blurStyle: BlurStyle, isDarkMode: Boolean): Int {
        return when (blurStyle) {
            BlurStyle.LIGHT -> Color.argb(200, 255, 255, 255)
            BlurStyle.DARK -> Color.argb(200, 30, 30, 30)
            BlurStyle.SYSTEM_MATERIAL -> {
                if (isDarkMode) Color.argb(200, 40, 40, 40)
                else Color.argb(200, 245, 245, 245)
            }
        }
    }

    /**
     * Тонирующий цвет поверх реального блюра.
     * Более прозрачный чем fallback — bitmap уже даёт визуальную сложность.
     */
    private fun getBitmapTintColor(blurStyle: BlurStyle, isDarkMode: Boolean): Int {
        return when (blurStyle) {
            BlurStyle.LIGHT -> Color.argb(60, 255, 255, 255)
            BlurStyle.DARK -> Color.argb(80, 0, 0, 0)
            BlurStyle.SYSTEM_MATERIAL -> {
                if (isDarkMode) Color.argb(80, 0, 0, 0)
                else Color.argb(50, 255, 255, 255)
            }
        }
    }
}
