package expo.modules.nativetoast

import android.os.Build
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Expo Modules мост для NativeToast.
 *
 * Этот файл — ТОЛЬКО мост, без бизнес-логики.
 * Вся логика делегирована синглтону ToastManager.
 *
 * Expo Modules API автоматически обрабатывает:
 * - TurboModule кодогенерацию на New Architecture
 * - NativeModule мост на старой архитектуре
 * - Обратную совместимость — без изменений на стороне потребителя
 *
 * Потокобезопасность: Expo Modules диспатчит Function-вызовы в очереди модуля.
 * ToastManager.show() внутри постит на главный поток.
 */
class NativeToastModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("NativeToast")

        // ── Константы ──
        Constants {
            mapOf(
                "supportsBlur" to BlurFactory.supportsBlur(),
                "platformVersion" to Build.VERSION.SDK_INT
            )
        }

        // ── События ──
        Events("onToastShow", "onToastHide")

        // ── Жизненный цикл ──
        OnCreate {
            val activity = appContext.currentActivity ?: return@OnCreate
            val manager = ToastManager.getInstance()
            manager.setActivity(activity)

            // Пробрасываем нативные события → JS события
            manager.onToastShow = { id, type ->
                sendEvent("onToastShow", mapOf("id" to id, "type" to type))
            }
            manager.onToastHide = { id, type, reason ->
                sendEvent("onToastHide", mapOf("id" to id, "type" to type, "reason" to reason))
            }
        }

        OnActivityEntersForeground {
            val activity = appContext.currentActivity ?: return@OnActivityEntersForeground
            ToastManager.getInstance().setActivity(activity)
        }

        // ── Функции ──

        Function("show") { config: Map<String, Any> ->
            val toastConfig = ToastConfigData.fromMap(config)
            ToastManager.getInstance().show(toastConfig)
        }

        Function("hide") {
            ToastManager.getInstance().hide()
        }

        Function("hideAll") {
            ToastManager.getInstance().hideAll()
        }
    }
}
