package expo.modules.nativetoast

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Утилита для захвата контента экрана и размытия.
 *
 * Подход: bitmap capture + downsample + stack blur + upscale.
 * Работает на всех API уровнях (minSdk 24+), без RenderScript.
 *
 * Производительность (на ~1080x150px регионе тоста):
 * - Захват decorView: ~5-10ms
 * - Crop + downsample 4x + stackBlur + upscale: ~3-5ms
 * - Итого: ~8-15ms — укладывается в один кадр (16ms @ 60fps)
 */
object BitmapBlurHelper {

    private const val BLUR_RADIUS = 25
    private const val DOWNSAMPLE_FACTOR = 4f

    /**
     * Захватить контент decorView в bitmap.
     * ВАЖНО: вызывать ДО добавления overlay-окна в WindowManager,
     * иначе overlay попадёт в скриншот.
     *
     * @return Bitmap с контентом экрана или null при ошибке
     */
    fun captureDecorView(activity: Activity): Bitmap? {
        return try {
            val decorView = activity.window.decorView
            if (decorView.width <= 0 || decorView.height <= 0) return null

            val bitmap = Bitmap.createBitmap(
                decorView.width,
                decorView.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            decorView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Обрезать bitmap по области тоста и размыть.
     *
     * @param source Полноэкранный bitmap (из captureDecorView)
     * @param toastRect Прямоугольник тоста в координатах экрана
     * @return Размытый bitmap размером с тост, или null при ошибке
     */
    fun cropAndBlur(source: Bitmap, toastRect: Rect): Bitmap? {
        if (toastRect.width() <= 0 || toastRect.height() <= 0) return null

        val left = max(0, toastRect.left)
        val top = max(0, toastRect.top)
        val right = min(source.width, toastRect.right)
        val bottom = min(source.height, toastRect.bottom)
        if (right <= left || bottom <= top) return null

        return try {
            val cropW = right - left
            val cropH = bottom - top

            // Обрезка
            val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)

            // Уменьшение для скорости
            val smallW = max(1, (cropW / DOWNSAMPLE_FACTOR).roundToInt())
            val smallH = max(1, (cropH / DOWNSAMPLE_FACTOR).roundToInt())
            val small = Bitmap.createScaledBitmap(cropped, smallW, smallH, true)
            if (cropped !== source) cropped.recycle()

            // Размытие
            val blurred = stackBlur(small, BLUR_RADIUS)
            if (blurred !== small) small.recycle()

            // Увеличение обратно до размера тоста
            val result = Bitmap.createScaledBitmap(blurred, cropW, cropH, true)
            if (result !== blurred) blurred.recycle()

            result
        } catch (e: Exception) {
            null
        }
    }

    /** Безопасная очистка bitmap */
    fun recycleSafely(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    /**
     * Stack Blur — алгоритм Марио Клингеманна (Mario Klingemann).
     * Чистый Kotlin, без зависимостей. O(width * height), не зависит от радиуса.
     *
     * Два прохода: горизонтальный и вертикальный.
     */
    private fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return sentBitmap

        val bitmap = sentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int

        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in dv.indices) {
            dv[idx] = idx / divsum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        // Горизонтальный проход
        y = 0
        while (y < h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        // Вертикальный проход
        x = 0
        while (x < w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x

                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(i)

                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius

            y = 0
            while (y < h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
