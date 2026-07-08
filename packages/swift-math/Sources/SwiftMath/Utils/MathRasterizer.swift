#if canImport(UIKit) || canImport(AppKit)
    import CoreText
    import CoreGraphics
    import Foundation

    #if canImport(UIKit)
        import UIKit
        public typealias MathPlatformImage = UIImage
    #elseif canImport(AppKit)
        import AppKit
        public typealias MathPlatformImage = NSImage
    #endif

    public struct MathRasterizedFormula {
        public var image: MathPlatformImage
        public var size: CGSize
        public var offset: CGFloat
        public var fallback: String?

        public init(
            image: MathPlatformImage,
            size: CGSize,
            offset: CGFloat,
            fallback: String? = nil
        ) {
            self.image = image
            self.size = size
            self.offset = offset
            self.fallback = fallback
        }
    }

    final class MathRasterizer {
        static let shared = MathRasterizer()

        private static let bytesPerPixel = 4
        private static let maximumPixelDimension: CGFloat = 32768
        private static let maximumRenderedPixelCount = 4_000_000
        private static let maximumRenderedBitmapByteCount =
            maximumRenderedPixelCount * bytesPerPixel
        private static let maximumCachedBitmapBytes = maximumRenderedBitmapByteCount * 4

        private let cache: NSCache<NSString, MathImageBox>

        private init() {
            cache = NSCache<NSString, MathImageBox>()
            cache.totalCostLimit = Self.maximumCachedBitmapBytes
        }

        func image(
            from svg: MathJaxSVG,
            color: CGColor,
            scale: CGFloat,
            fontSize: CGFloat
        ) -> MathRasterizedFormula? {
            guard scale.isFinite, scale > 0, fontSize.isFinite, fontSize > 0,
                let document = MathSVGParser.parse(svg.markup),
                document.viewBox.width > 0,
                document.viewBox.height > 0
            else {
                return nil
            }

            let key = Self.cacheKey(for: svg, color: color, scale: scale, fontSize: fontSize)
            if let cached = cache.object(forKey: key) {
                return cached.image
            }

            let size = Self.imageSize(for: document.viewBox, fontSize: fontSize)
            guard size.width.isFinite, size.height.isFinite, size.width > 0, size.height > 0,
                let bitmap = Self.bitmapAllocation(for: size, scale: scale),
                let platformImage = Self.render(
                    document: document,
                    color: color,
                    scale: scale,
                    fontSize: fontSize,
                    size: size,
                    bitmap: bitmap
                )
            else {
                return nil
            }

            let image = MathRasterizedFormula(
                image: platformImage,
                size: size,
                offset: Self.offset(
                    for: document.viewBox, size: size, fontSize: fontSize),
                fallback: svg.fallbackText
            )
            cache.setObject(MathImageBox(image), forKey: key, cost: bitmap.byteCount)
            return image
        }

        private static func imageSize(for viewBox: CGRect, fontSize: CGFloat) -> CGSize {
            // MathJax SVG coordinates are emitted in 1000-unit ems.
            CGSize(
                width: ceil(viewBox.width / 1000 * fontSize),
                height: ceil(viewBox.height / 1000 * fontSize)
            )
        }

        private static func offset(for viewBox: CGRect, size: CGSize, fontSize: CGFloat)
            -> CGFloat
        {
            let baselineY = -viewBox.minY / 1000 * fontSize
            return -(size.height - baselineY)
        }

        private static func render(
            document: MathSVGDocument,
            color: CGColor,
            scale: CGFloat,
            fontSize: CGFloat,
            size: CGSize,
            bitmap: BitmapAllocation
        ) -> MathPlatformImage? {
            guard
                let context = CGContext(
                    data: nil,
                    width: bitmap.width,
                    height: bitmap.height,
                    bitsPerComponent: 8,
                    bytesPerRow: 0,
                    space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
                )
            else {
                return nil
            }

            context.setAllowsAntialiasing(true)
            context.setShouldAntialias(true)
            context.scaleBy(x: scale, y: scale)
            context.translateBy(x: 0, y: size.height)
            context.scaleBy(x: 1, y: -1)
            draw(document: document, color: color, fontSize: fontSize, in: context)

            guard let cgImage = context.makeImage() else {
                return nil
            }

            #if canImport(UIKit)
                return UIImage(cgImage: cgImage, scale: scale, orientation: .up)
            #elseif canImport(AppKit)
                return NSImage(cgImage: cgImage, size: size)
            #endif
        }

        private static func bitmapAllocation(for size: CGSize, scale: CGFloat) -> BitmapAllocation?
        {
            guard let width = pixelDimension(for: size.width, scale: scale),
                let height = pixelDimension(for: size.height, scale: scale)
            else {
                return nil
            }

            let pixelCount = width * height
            let byteCount = pixelCount * bytesPerPixel
            guard pixelCount <= maximumRenderedPixelCount,
                byteCount <= maximumRenderedBitmapByteCount
            else {
                return nil
            }

            return BitmapAllocation(width: width, height: height, byteCount: byteCount)
        }

        private static func pixelDimension(for points: CGFloat, scale: CGFloat) -> Int? {
            let pixels = ceil(points * scale)
            guard pixels.isFinite, pixels > 0, pixels <= maximumPixelDimension else {
                return nil
            }
            return Int(pixels)
        }

        private static func draw(
            document: MathSVGDocument,
            color: CGColor,
            fontSize: CGFloat,
            in context: CGContext
        ) {
            let unitScale = fontSize / 1000

            context.saveGState()
            context.setFillColor(color)
            context.scaleBy(x: unitScale, y: unitScale)
            context.translateBy(x: -document.viewBox.minX, y: -document.viewBox.minY)

            for command in document.drawCommands {
                context.saveGState()
                context.concatenate(command.transform)
                context.addPath(command.path)
                context.fillPath()
                context.restoreGState()
            }

            for command in document.textCommands {
                drawText(command, color: color, in: context)
            }

            context.restoreGState()
        }

        private static func drawText(
            _ command: MathSVGTextCommand,
            color: CGColor,
            in context: CGContext
        ) {
            guard !command.text.isEmpty else {
                return
            }

            let font = CTFontCreateWithName("Helvetica" as CFString, 1000, nil)
            let attributes: [CFString: Any] = [
                kCTFontAttributeName: font,
                kCTForegroundColorAttributeName: color,
            ]
            guard
                let attributed = CFAttributedStringCreate(
                    nil,
                    command.text as CFString,
                    attributes as CFDictionary
                )
            else {
                return
            }

            let line = CTLineCreateWithAttributedString(attributed)
            context.saveGState()
            context.concatenate(command.transform)
            context.textMatrix = .identity
            context.textPosition = command.position
            CTLineDraw(line, context)
            context.restoreGState()
        }

        private static func cacheKey(
            for svg: MathJaxSVG,
            color: CGColor,
            scale: CGFloat,
            fontSize: CGFloat
        ) -> NSString {
            [
                "markup:\(svg.markup)",
                "fallback:\(fallbackTextKey(svg.fallbackText))",
                "scale:\(numericKey(scale))",
                "fontSize:\(numericKey(fontSize))",
                "color:\(colorKey(color))",
            ].joined(separator: "\u{1F}") as NSString
        }

        private static func fallbackTextKey(_ fallbackText: String?) -> String {
            switch fallbackText {
            case .some(let fallbackText):
                return "string:\(fallbackText)"
            case .none:
                return "nil"
            }
        }

        private static func colorKey(_ color: CGColor) -> String {
            let colorSpace = color.colorSpace?.name as String? ?? "unknown"
            let components = color.components ?? []
            let componentKey = components.map(numericKey).joined(separator: ",")
            return "\(colorSpace)|\(numericKey(color.alpha))|\(componentKey)"
        }

        private static func numericKey(_ value: CGFloat) -> String {
            String(format: "%.12g", Double(value))
        }
    }

    private final class MathImageBox {
        let image: MathRasterizedFormula

        init(_ image: MathRasterizedFormula) {
            self.image = image
        }
    }

    private struct BitmapAllocation {
        var width: Int
        var height: Int
        var byteCount: Int
    }
#endif
