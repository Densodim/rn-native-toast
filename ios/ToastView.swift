import UIKit

/// Native toast view — programmatic UIKit, no storyboard/xib.
///
/// Structure:
///   UIView (root, rounded + clipped)
///     ├── UIVisualEffectView (blur background)
///     ├── UIView (type-specific color overlay)
///     └── UIStackView (content)
///           └── UIStackView (text column)
///                 ├── UILabel (title)
///                 └── UILabel (message)
///
/// RTL: Uses semanticContentAttribute = .forceRightToLeft detection.
///       Auto Layout handles mirroring automatically.
/// Accessibility: Combined element with title + message.
/// Dark mode: Handled by systemMaterial blur (auto-adaptive).
/// Dynamic Island / notch: Handled by ToastManager via safeAreaInsets.
///
/// Performance:
/// - Single layout pass via Auto Layout
/// - No image allocations
/// - Reusable — call bind() to update content
class ToastView: UIView {

    private let blurContainer = UIView()
    private let colorOverlay = UIView()
    private let titleLabel = UILabel()
    private let messageLabel = UILabel()
    private let textStack = UIStackView()
    private let contentStack = UIStackView()

    private let cornerRadius: CGFloat = 16
    private let horizontalPadding: CGFloat = 16
    private let verticalPadding: CGFloat = 18

    private var blurView: UIVisualEffectView?

    private var toastPosition: ToastPosition = .top
    private var swipeTouchStartY: CGFloat = 0
    var onSwipeDismiss: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    private func setupView() {
        // Root
        layer.cornerRadius = cornerRadius
        clipsToBounds = true

        // Blur container (will hold the UIVisualEffectView)
        blurContainer.translatesAutoresizingMaskIntoConstraints = false
        addSubview(blurContainer)

        // Color overlay
        colorOverlay.translatesAutoresizingMaskIntoConstraints = false
        addSubview(colorOverlay)

        // Title
        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.numberOfLines = 1

        // Message
        messageLabel.font = .systemFont(ofSize: 14, weight: .regular)
        messageLabel.textColor = UIColor.white.withAlphaComponent(0.9)
        messageLabel.numberOfLines = 3

        // Text stack
        textStack.axis = .vertical
        textStack.spacing = 4
        textStack.addArrangedSubview(titleLabel)
        textStack.addArrangedSubview(messageLabel)

        // Content stack
        contentStack.axis = .horizontal
        contentStack.alignment = .center
        contentStack.spacing = 12
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.addArrangedSubview(textStack)
        addSubview(contentStack)

        // Constraints
        NSLayoutConstraint.activate([
            blurContainer.topAnchor.constraint(equalTo: topAnchor),
            blurContainer.leadingAnchor.constraint(equalTo: leadingAnchor),
            blurContainer.trailingAnchor.constraint(equalTo: trailingAnchor),
            blurContainer.bottomAnchor.constraint(equalTo: bottomAnchor),

            colorOverlay.topAnchor.constraint(equalTo: topAnchor),
            colorOverlay.leadingAnchor.constraint(equalTo: leadingAnchor),
            colorOverlay.trailingAnchor.constraint(equalTo: trailingAnchor),
            colorOverlay.bottomAnchor.constraint(equalTo: bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: topAnchor, constant: verticalPadding),
            contentStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: horizontalPadding),
            contentStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -horizontalPadding),
            contentStack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -verticalPadding),
        ])

        // Accessibility
        isAccessibilityElement = true
        accessibilityTraits = .staticText
    }

    /// Bind configuration — reusable pattern.
    func bind(config: ToastConfigData) {
        toastPosition = config.position
        titleLabel.text = config.titleKey
        messageLabel.text = config.messageKey
        messageLabel.isHidden = config.messageKey.isEmpty

        // Color overlay
        colorOverlay.backgroundColor = typeColor(for: config.type)

        // Blur
        blurView?.removeFromSuperview()
        let newBlur = BlurViewFactory.createBlurView(blurStyle: config.blurStyle, frame: bounds)
        blurContainer.addSubview(newBlur)
        newBlur.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            newBlur.topAnchor.constraint(equalTo: blurContainer.topAnchor),
            newBlur.leadingAnchor.constraint(equalTo: blurContainer.leadingAnchor),
            newBlur.trailingAnchor.constraint(equalTo: blurContainer.trailingAnchor),
            newBlur.bottomAnchor.constraint(equalTo: blurContainer.bottomAnchor),
        ])
        blurView = newBlur

        // Accessibility
        accessibilityLabel = "\(config.titleKey). \(config.messageKey)"
    }

    // MARK: - Swipe to dismiss

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        swipeTouchStartY = touch.location(in: superview).y
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first, superview != nil else { return }
        let dy = touch.location(in: superview).y - swipeTouchStartY
        let offset = toastPosition == .top ? min(0, dy) : max(0, dy)
        transform = CGAffineTransform(translationX: 0, y: offset)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let dy = touch.location(in: superview).y - swipeTouchStartY
        let shouldDismiss = toastPosition == .top ? dy < -50 : dy > 50
        if shouldDismiss {
            onSwipeDismiss?()
        } else {
            UIView.animate(withDuration: 0.3, delay: 0,
                           usingSpringWithDamping: 0.7, initialSpringVelocity: 0.5,
                           options: .curveEaseOut) { self.transform = .identity }
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        UIView.animate(withDuration: 0.3, delay: 0,
                       usingSpringWithDamping: 0.7, initialSpringVelocity: 0.5,
                       options: .curveEaseOut) { self.transform = .identity }
    }

    private func typeColor(for type: ToastType) -> UIColor {
        switch type {
        case .error:
            return UIColor(red: 220/255, green: 38/255, blue: 38/255, alpha: 0.45)
        case .success:
            return UIColor(red: 22/255, green: 163/255, blue: 74/255, alpha: 0.4)
        case .warning:
            return UIColor(red: 234/255, green: 179/255, blue: 8/255, alpha: 0.42)
        case .info:
            return UIColor(red: 37/255, green: 99/255, blue: 235/255, alpha: 0.4)
        }
    }
}
