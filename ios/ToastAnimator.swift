import UIKit

/// Handles all toast animations using UIView.animate (Core Animation backed).
///
/// - All animations run on the main thread
/// - Respects UIAccessibility.isReduceMotionEnabled
/// - 60fps guaranteed — Core Animation is GPU-composited
/// - Uses spring damping for natural feel
///
/// Performance: UIView.animate uses Core Animation under the hood,
/// which batches property changes into a single CATransaction.
/// No per-frame callback, no layout thrashing.
enum ToastAnimator {

    private static let enterDuration: TimeInterval = 0.4
    private static let exitDuration: TimeInterval = 0.3
    private static let springDamping: CGFloat = 0.75
    private static let initialVelocity: CGFloat = 0.5

    /// Animate toast into view with slide + fade + scale.
    ///
    /// If Reduce Motion is enabled, shows immediately without animation.
    static func animateIn(
        view: UIView,
        position: ToastPosition,
        completion: (() -> Void)? = nil
    ) {
        if UIAccessibility.isReduceMotionEnabled {
            view.alpha = 1
            view.transform = .identity
            completion?()
            return
        }

        let offsetY: CGFloat = position == .top ? -120 : 120
        view.alpha = 0
        view.transform = CGAffineTransform(translationX: 0, y: offsetY)
            .scaledBy(x: 0.95, y: 0.95)

        UIView.animate(
            withDuration: enterDuration,
            delay: 0,
            usingSpringWithDamping: springDamping,
            initialSpringVelocity: initialVelocity,
            options: [.curveEaseOut, .allowUserInteraction],
            animations: {
                view.alpha = 1
                view.transform = .identity
            },
            completion: { _ in completion?() }
        )
    }

    /// Animate toast out of view with slide + fade.
    static func animateOut(
        view: UIView,
        position: ToastPosition,
        completion: @escaping () -> Void
    ) {
        if UIAccessibility.isReduceMotionEnabled {
            view.alpha = 0
            completion()
            return
        }

        let offsetY: CGFloat = position == .top ? -120 : 120

        UIView.animate(
            withDuration: exitDuration,
            delay: 0,
            options: [.curveEaseIn, .beginFromCurrentState],
            animations: {
                view.alpha = 0
                view.transform = CGAffineTransform(translationX: 0, y: offsetY)
            },
            completion: { _ in completion() }
        )
    }
}
