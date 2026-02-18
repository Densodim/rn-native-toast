import ExpoModulesCore
import UIKit

/// Expo Modules bridge for NativeToast — iOS.
///
/// This file is ONLY the bridge layer — no business logic.
/// All logic delegated to ToastManager singleton.
///
/// Expo Modules API automatically handles:
/// - TurboModule codegen on New Architecture
/// - NativeModule bridge on old architecture
/// - Backward compatibility — zero consumer-side changes
public class NativeToastModule: Module {
    public func definition() -> ModuleDefinition {
        Name("NativeToast")

        // ── Constants ──
        Constants {
            [
                "supportsBlur": BlurViewFactory.supportsBlur,
                "platformVersion": ProcessInfo.processInfo.operatingSystemVersion.majorVersion
            ]
        }

        // ── Events ──
        Events("onToastShow", "onToastHide")

        // ── Lifecycle ──
        OnCreate {
            let manager = ToastManager.shared
            manager.onToastShow = { [weak self] id, type in
                self?.sendEvent("onToastShow", ["id": id, "type": type])
            }
            manager.onToastHide = { [weak self] id, type, reason in
                self?.sendEvent("onToastHide", ["id": id, "type": type, "reason": reason])
            }
        }

        // ── Functions ──

        Function("show") { (config: [String: Any]) in
            let toastConfig = ToastConfigData.from(dictionary: config)
            ToastManager.shared.show(config: toastConfig)
        }

        Function("hide") {
            ToastManager.shared.hide()
        }

        Function("hideAll") {
            ToastManager.shared.hideAll()
        }
    }
}
