import Foundation

/// Toast type variants — maps 1:1 to JS ToastType
enum ToastType: String {
    case error
    case success
    case warning
    case info
}

/// Position on screen
enum ToastPosition: String {
    case top
    case bottom
}

/// Queue behavior
enum ToastQueueMode: String {
    case replace
    case queue
}

/// Blur style variant — maps to UIBlurEffect.Style
enum ToastBlurStyle: String {
    case light
    case dark
    case systemMaterial
}

/// Immutable configuration for a single toast
struct ToastConfigData {
    let id: String
    let type: ToastType
    let titleKey: String
    let messageKey: String
    let duration: TimeInterval
    let position: ToastPosition
    let haptic: Bool
    let blurStyle: ToastBlurStyle
    let queueMode: ToastQueueMode

    static func from(dictionary dict: [String: Any]) -> ToastConfigData {
        ToastConfigData(
            id: UUID().uuidString,
            type: ToastType(rawValue: dict["type"] as? String ?? "info") ?? .info,
            titleKey: dict["titleKey"] as? String ?? "",
            messageKey: dict["messageKey"] as? String ?? "",
            duration: TimeInterval((dict["duration"] as? NSNumber)?.doubleValue ?? 4000) / 1000.0,
            position: ToastPosition(rawValue: dict["position"] as? String ?? "top") ?? .top,
            haptic: dict["haptic"] as? Bool ?? true,
            blurStyle: ToastBlurStyle(rawValue: dict["blurStyle"] as? String ?? "systemMaterial") ?? .systemMaterial,
            queueMode: ToastQueueMode(rawValue: dict["queueMode"] as? String ?? "replace") ?? .replace
        )
    }
}
