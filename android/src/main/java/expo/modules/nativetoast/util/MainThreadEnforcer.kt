package expo.modules.nativetoast.util

import android.os.Handler
import android.os.Looper

/**
 * Утилита для гарантии выполнения на главном потоке.
 * Предотвращает ANR и краши от манипуляций с вьюхами вне UI-потока.
 */
object MainThreadEnforcer {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Выполнить на главном потоке. Если уже на главном — выполняет синхронно. */
    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /** Отложенный пост на главный поток */
    fun postDelayed(delayMs: Long, runnable: Runnable) {
        mainHandler.postDelayed(runnable, delayMs)
    }

    /** Удалить все отложенные колбэки */
    fun removeCallbacks(block: Runnable) {
        mainHandler.removeCallbacks(block)
    }
}
