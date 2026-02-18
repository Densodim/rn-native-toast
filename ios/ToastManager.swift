import UIKit

/// Singleton toast lifecycle manager for iOS.
///
/// Responsibilities:
/// - Manages a UIWindow overlay (above everything including modals)
/// - Handles queue/replace mode
/// - Orchestrates show → auto-dismiss → animate-out lifecycle
/// - Haptic feedback (UIImpactFeedbackGenerator)
/// - Safe area / Dynamic Island awareness
/// - Thread-safety via main-thread dispatch
///
/// Architecture: UIWindow overlay
///   A dedicated UIWindow at .alert + 1 level ensures the toast
///   shows above:
///   - Navigation transitions
///   - Modal presentations (including RN modals)
///   - Keyboard
///   Trade-off: slightly more complex lifecycle vs root view approach.
///   Benefit: zero coupling with React view hierarchy.
///
/// Edge cases:
/// - Rapid successive calls → replace or queue per config
/// - App backgrounded → timer invalidated on resign, rescheduled on active
/// - Orientation change → UIWindow auto-rotates
/// - Memory warning → queue cleared, current dismissed
/// - Dynamic Island → safeAreaInsets.top accounts for it automatically
final class ToastManager {

    static let shared = ToastManager()

    private var overlayWindow: UIWindow?
    private var currentToastView: ToastView?
    private var currentConfig: ToastConfigData?
    private var dismissTimer: Timer?
    private var queue: [ToastConfigData] = []

    /// Event callbacks — set by Module
    var onToastShow: ((_ id: String, _ type: String) -> Void)?
    var onToastHide: ((_ id: String, _ type: String, _ reason: String) -> Void)?

    private init() {
        // Listen for app lifecycle to pause/resume timers
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(didReceiveMemoryWarning),
            name: UIApplication.didReceiveMemoryWarningNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Public API

    func show(config: ToastConfigData) {
        ensureMainThread {
            switch config.queueMode {
            case .replace:
                self.dismissCurrentImmediate(reason: "replaced")
                self.queue.removeAll()
                self.showInternal(config)
            case .queue:
                if self.currentConfig != nil {
                    self.queue.append(config)
                } else {
                    self.showInternal(config)
                }
            }
        }
    }

    func hide() {
        ensureMainThread {
            self.dismissCurrent(reason: "manual")
        }
    }

    func hideAll() {
        ensureMainThread {
            self.queue.removeAll()
            self.dismissCurrentImmediate(reason: "manual")
        }
    }

    // MARK: - Internal

    private func showInternal(_ config: ToastConfigData) {
        currentConfig = config

        // Create overlay window
        setupOverlayWindow()

        guard let window = overlayWindow else { return }

        // Create toast view
        let toastView = ToastView()
        toastView.bind(config: config)
        toastView.translatesAutoresizingMaskIntoConstraints = false
        currentToastView = toastView

        window.rootViewController?.view.addSubview(toastView)

        // Layout constraints with safe area
        let safeArea = window.safeAreaInsets
        let guide = window.rootViewController!.view!

        NSLayoutConstraint.activate([
            toastView.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 16),
            toastView.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -16),
        ])

        if config.position == .top {
            toastView.topAnchor.constraint(
                equalTo: guide.topAnchor,
                constant: safeArea.top + 8
            ).isActive = true
        } else {
            toastView.bottomAnchor.constraint(
                equalTo: guide.bottomAnchor,
                constant: -(safeArea.bottom + 8)
            ).isActive = true
        }

        // Animate in
        ToastAnimator.animateIn(view: toastView, position: config.position)

        // Haptic
        if config.haptic {
            triggerHaptic()
        }

        // Emit event
        onToastShow?(config.id, config.type.rawValue)

        // Auto dismiss
        if config.duration > 0 {
            scheduleAutoDismiss(config: config)
        }
    }

    private func setupOverlayWindow() {
        teardownOverlayWindow()

        let windowScene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }

        guard let scene = windowScene else { return }

        let window = UIWindow(windowScene: scene)
        window.windowLevel = .alert + 1
        window.backgroundColor = .clear
        window.isUserInteractionEnabled = true

        let vc = ToastPassthroughViewController()
        window.rootViewController = vc
        window.isHidden = false
        overlayWindow = window
    }

    private func teardownOverlayWindow() {
        currentToastView?.removeFromSuperview()
        overlayWindow?.isHidden = true
        overlayWindow?.rootViewController = nil
        overlayWindow = nil
    }

    private func scheduleAutoDismiss(config: ToastConfigData) {
        cancelDismissTimer()
        dismissTimer = Timer.scheduledTimer(
            withTimeInterval: config.duration,
            repeats: false
        ) { [weak self] _ in
            self?.dismissCurrent(reason: "timeout")
        }
    }

    private func cancelDismissTimer() {
        dismissTimer?.invalidate()
        dismissTimer = nil
    }

    private func dismissCurrent(reason: String) {
        guard let view = currentToastView, let config = currentConfig else { return }
        cancelDismissTimer()

        ToastAnimator.animateOut(view: view, position: config.position) { [weak self] in
            self?.teardownOverlayWindow()
            self?.onToastHide?(config.id, config.type.rawValue, reason)
            self?.currentToastView = nil
            self?.currentConfig = nil
            self?.showNextInQueue()
        }
    }

    private func dismissCurrentImmediate(reason: String) {
        cancelDismissTimer()
        let config = currentConfig
        teardownOverlayWindow()
        if let config = config {
            onToastHide?(config.id, config.type.rawValue, reason)
        }
        currentToastView = nil
        currentConfig = nil
    }

    private func showNextInQueue() {
        guard !queue.isEmpty else { return }
        let next = queue.removeFirst()
        showInternal(next)
    }

    private func triggerHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }

    private func ensureMainThread(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }

    // MARK: - App Lifecycle

    private var remainingDuration: TimeInterval?
    private var pauseDate: Date?

    @objc private func appDidEnterBackground() {
        // Pause auto-dismiss timer
        if let timer = dismissTimer, timer.isValid {
            remainingDuration = timer.fireDate.timeIntervalSinceNow
            cancelDismissTimer()
            pauseDate = Date()
        }
    }

    @objc private func appDidBecomeActive() {
        // Resume auto-dismiss timer
        if let remaining = remainingDuration, let config = currentConfig {
            let adjustedDuration = max(remaining, 0.5)
            dismissTimer = Timer.scheduledTimer(
                withTimeInterval: adjustedDuration,
                repeats: false
            ) { [weak self] _ in
                self?.dismissCurrent(reason: "timeout")
            }
            remainingDuration = nil
            pauseDate = nil
        }
    }

    @objc private func didReceiveMemoryWarning() {
        // Clear queue on memory warning
        queue.removeAll()
    }
}

// MARK: - Passthrough VC

/// A view controller that passes through touches to the underlying UI.
/// Only the toast view itself captures touches.
private class ToastPassthroughViewController: UIViewController {
    override func loadView() {
        view = ToastPassthroughView()
    }

    // Auto-rotate support
    override var shouldAutorotate: Bool { true }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .all }
}

/// A view that only captures touches on its subviews (the toast).
/// All other touches pass through to the app underneath.
private class ToastPassthroughView: UIView {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hitView = super.hitTest(point, with: event)
        return hitView === self ? nil : hitView
    }
}
