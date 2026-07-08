#if canImport(UIKit) || canImport(AppKit)
    import CoreGraphics
    import Foundation

    public enum MathRenderFailureReason: Equatable, Sendable {
        case invalidInput
        case bridgeUnavailable
        case mathJaxError
        case renderFailed
    }

    public struct MathRenderFallback: Equatable, Sendable {
        public var text: String
        public var reason: MathRenderFailureReason

        public init(text: String, reason: MathRenderFailureReason) {
            self.text = text
            self.reason = reason
        }
    }

    public enum MathRenderState {
        case pending
        case succeeded(rasterized: MathRasterizedFormula)
        case failed(fallback: MathRenderFallback)
    }

    public enum MathRenderer {
        public static func render(
            latex: String,
            options: MathRenderOptions = MathRenderOptions(),
            color: CGColor = CGColor(gray: 0, alpha: 1)
        ) -> AsyncStream<MathRenderState> {
            AsyncStream { continuation in
                continuation.yield(.pending)

                let task = Task {
                    let state = await terminalState(latex: latex, options: options, color: color)
                    guard !Task.isCancelled else {
                        return
                    }
                    continuation.yield(state)
                    continuation.finish()
                }

                continuation.onTermination = { _ in
                    task.cancel()
                }
            }
        }

        private static func terminalState(
            latex: String,
            options: MathRenderOptions,
            color: CGColor
        ) async -> MathRenderState {
            let fallbackText = fallbackText(for: latex, standalone: options.standalone)
            guard options.fontSize.isFinite, options.fontSize > 0,
                options.scale.isFinite, options.scale > 0
            else {
                return .failed(
                    fallback: MathRenderFallback(text: fallbackText, reason: .invalidInput)
                )
            }

            let bridgeOptions = MathRenderOptions(
                standalone: options.standalone,
                fontSize: options.fontSize,
                scale: 1
            )
            guard let svg = await MathJaxBridge.shared.svg(for: latex, options: bridgeOptions)
            else {
                return .failed(
                    fallback: MathRenderFallback(text: fallbackText, reason: .bridgeUnavailable)
                )
            }

            guard svg.ok else {
                return .failed(
                    fallback: MathRenderFallback(
                        text: svg.fallbackText ?? fallbackText,
                        reason: .mathJaxError
                    )
                )
            }

            guard
                let rasterized = MathRasterizer.shared.image(
                    from: svg,
                    color: color,
                    scale: CGFloat(options.scale),
                    fontSize: CGFloat(options.fontSize)
                )
            else {
                return .failed(
                    fallback: MathRenderFallback(
                        text: svg.fallbackText ?? fallbackText,
                        reason: .renderFailed
                    )
                )
            }

            return .succeeded(rasterized: rasterized)
        }

        static func fallbackText(for latex: String, standalone: Bool) -> String {
            MathJaxBridge.fallbackText(for: latex, standalone: standalone)
        }

        static func colorKey(_ color: CGColor) -> String {
            let colorSpace = color.colorSpace?.name as String? ?? "unknown"
            let components = color.components ?? []
            let componentKey = components.map(numericKey).joined(separator: ",")
            return "\(colorSpace)|\(numericKey(color.alpha))|\(componentKey)"
        }

        private static func numericKey(_ value: CGFloat) -> String {
            String(format: "%.12g", Double(value))
        }
    }
#endif
