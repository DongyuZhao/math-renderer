#if canImport(SwiftUI)
    import CoreGraphics
    import Foundation
    import SwiftUI

    public struct MathText: View {
        private let tex: String
        private let displayMode: MathDisplayMode
        private let color: CGColor?

        @Environment(\.displayScale) private var scale
        @Environment(\.colorScheme) private var colorScheme
        @ScaledMetric(relativeTo: .body) private var scaledFontSize: CGFloat = 16
        @State private var renderedState: MathRenderState = .pending

        public init(
            _ tex: String,
            displayMode: MathDisplayMode = .inline,
            fontSize: CGFloat = 16,
            color: CGColor? = nil
        ) {
            self.tex = tex
            self.displayMode = displayMode
            self.color = color
            _scaledFontSize = ScaledMetric(wrappedValue: fontSize, relativeTo: .body)
        }

        public var body: some View {
            view(for: renderedState)
                .id(taskID)
                .task(id: taskID) {
                    for await state in MathRenderer.render(
                        latex: tex,
                        options: MathRenderOptions(
                            standalone: displayMode.standalone,
                            fontSize: Double(scaledFontSize),
                            scale: Double(scale)
                        ),
                        color: resolvedColor
                    ) {
                        renderedState = state
                    }
                }
        }

        private var fallbackText: String {
            displayMode == .block ? #"\[\#(tex)\]"# : #"\(\#(tex)\)"#
        }

        private var taskID: String {
            [
                tex,
                "\(displayMode)",
                "\(scaledFontSize)",
                "\(scale)",
                MathRenderer.colorKey(resolvedColor),
            ].joined(separator: "\u{1F}")
        }

        @ViewBuilder
        private func view(for state: MathRenderState) -> some View {
            switch state {
            case .pending:
                Color.clear
                    .frame(height: max(ceil(scaledFontSize), 1))
                    .accessibilityLabel(Text(fallbackText))
            case .succeeded(let rasterized):
                platformImage(rasterized.image)
                    .resizable()
                    .interpolation(.high)
                    .antialiased(true)
                    .frame(width: rasterized.size.width, height: rasterized.size.height)
                    // Publish the formula's math baseline so it aligns with
                    // surrounding text (e.g. HStack(alignment: .firstTextBaseline)).
                    // offset is measured up from the bottom, so the
                    // distance from the top is height + offset.
                    .alignmentGuide(.firstTextBaseline) { _ in
                        rasterized.size.height + rasterized.offset
                    }
                    .alignmentGuide(.lastTextBaseline) { _ in
                        rasterized.size.height + rasterized.offset
                    }
                    .accessibilityLabel(Text(rasterized.fallback ?? fallbackText))
            case .failed(let fallback):
                Text(fallback.text)
                    .font(.system(size: scaledFontSize, design: .monospaced))
                    .foregroundStyle(swiftUIColor)
                    .accessibilityLabel(Text(fallback.text))
            }
        }

        private var resolvedColor: CGColor {
            if let color {
                return color
            }

            switch colorScheme {
            case .dark:
                return CGColor(gray: 1, alpha: 1)
            case .light:
                return CGColor(gray: 0, alpha: 1)
            @unknown default:
                return CGColor(gray: 0, alpha: 1)
            }
        }

        private var swiftUIColor: Color {
            #if canImport(UIKit)
                return Color(UIColor(cgColor: resolvedColor))
            #elseif canImport(AppKit)
                return Color(NSColor(cgColor: resolvedColor) ?? .labelColor)
            #endif
        }

        private func platformImage(_ image: MathPlatformImage) -> Image {
            #if canImport(UIKit)
                Image(uiImage: image)
            #else
                Image(nsImage: image)
            #endif
        }
    }
#endif
