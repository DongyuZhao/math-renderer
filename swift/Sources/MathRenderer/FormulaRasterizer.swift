#if canImport(CoreGraphics)
    import CoreGraphics
    import CoreText
    import Foundation

    #if canImport(UIKit)
        import UIKit
        public typealias PlatformImage = UIImage
    #elseif canImport(AppKit)
        import AppKit
        public typealias PlatformImage = NSImage
    #endif

    // MARK: - Public result type

    /// A rasterised formula image ready for embedding in text.
    public struct FormulaImage {
        /// The rendered platform image.
        public var image: PlatformImage
        /// Logical size in points.
        public var size: CGSize
        /// Baseline offset (negative = descend below baseline) for inline text placement.
        public var baselineOffset: CGFloat
        /// Fallback LaTeX source string if rendering degraded.
        public var fallbackText: String?
    }

    // MARK: - Rasterizer

    /// Converts a `MathJaxSVG` into a `FormulaImage` using CoreGraphics.
    public final class FormulaRasterizer {
        public static let shared = FormulaRasterizer()

        private static let bytesPerPixel = 4
        private static let maxPixelDimension: CGFloat = 32_768
        private static let maxPixelCount = 4_000_000
        private static let maxBitmapBytes = maxPixelCount * bytesPerPixel
        private static let maxCachedBytes = maxBitmapBytes * 4

        private let cache: NSCache<NSString, Box>

        private init() {
            cache = NSCache()
            cache.totalCostLimit = Self.maxCachedBytes
        }

        /// Rasterises `svg` using `color` (defaults to black) at the given `scale` and `fontSize`.
        public func image(
            from svg: MathJaxSVG,
            color: CGColor? = nil,
            scale: CGFloat,
            fontSize: CGFloat
        ) -> FormulaImage? {
            guard scale.isFinite, scale > 0, fontSize.isFinite, fontSize > 0,
                let doc = FormulaSVGParser.parse(svg.markup),
                doc.viewBox.width > 0, doc.viewBox.height > 0
            else { return nil }

            let resolvedColor = color ?? CGColor(gray: 0, alpha: 1)
            let key = cacheKey(for: svg, color: resolvedColor, scale: scale, fontSize: fontSize)
            if let cached = cache.object(forKey: key) { return cached.value }

            let size = Self.imageSize(for: doc.viewBox, fontSize: fontSize)
            guard size.width.isFinite, size.height.isFinite, size.width > 0, size.height > 0,
                let bitmap = Self.bitmapAllocation(for: size, scale: scale),
                let platformImage = Self.render(doc: doc, color: resolvedColor, scale: scale,
                                                fontSize: fontSize, size: size, bitmap: bitmap)
            else { return nil }

            let result = FormulaImage(
                image: platformImage,
                size: size,
                baselineOffset: Self.baselineOffset(for: doc.viewBox, size: size, fontSize: fontSize),
                fallbackText: svg.fallbackText
            )
            cache.setObject(Box(result), forKey: key, cost: bitmap.byteCount)
            return result
        }

        // MARK: - Private

        private static func imageSize(for viewBox: CGRect, fontSize: CGFloat) -> CGSize {
            // MathJax SVG coordinates are in 1000-unit ems.
            CGSize(
                width:  ceil(viewBox.width  / 1000 * fontSize),
                height: ceil(viewBox.height / 1000 * fontSize)
            )
        }

        private static func baselineOffset(for viewBox: CGRect, size: CGSize, fontSize: CGFloat) -> CGFloat {
            let baselineY = -viewBox.minY / 1000 * fontSize
            return -(size.height - baselineY)
        }

        private static func render(
            doc: SVGDocument,
            color: CGColor,
            scale: CGFloat,
            fontSize: CGFloat,
            size: CGSize,
            bitmap: BitmapAllocation
        ) -> PlatformImage? {
            guard let ctx = CGContext(
                data: nil,
                width: bitmap.width, height: bitmap.height,
                bitsPerComponent: 8, bytesPerRow: 0,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return nil }

            ctx.setAllowsAntialiasing(true)
            ctx.setShouldAntialias(true)
            ctx.scaleBy(x: scale, y: scale)
            ctx.translateBy(x: 0, y: size.height)
            ctx.scaleBy(x: 1, y: -1)

            draw(doc: doc, color: color, fontSize: fontSize, in: ctx)

            guard let cgImage = ctx.makeImage() else { return nil }

            #if canImport(UIKit)
                return UIImage(cgImage: cgImage, scale: scale, orientation: .up)
            #elseif canImport(AppKit)
                return NSImage(cgImage: cgImage, size: size)
            #endif
        }

        private static func draw(doc: SVGDocument, color: CGColor, fontSize: CGFloat, in ctx: CGContext) {
            let unitScale = fontSize / 1000

            ctx.saveGState()
            ctx.setFillColor(color)
            ctx.scaleBy(x: unitScale, y: unitScale)
            ctx.translateBy(x: -doc.viewBox.minX, y: -doc.viewBox.minY)

            for cmd in doc.drawCommands {
                ctx.saveGState()
                ctx.concatenate(cmd.transform)
                ctx.addPath(cmd.path)
                ctx.fillPath()
                ctx.restoreGState()
            }

            for cmd in doc.textCommands {
                drawText(cmd, color: color, in: ctx)
            }

            ctx.restoreGState()
        }

        private static func drawText(_ cmd: SVGTextCommand, color: CGColor, in ctx: CGContext) {
            guard !cmd.text.isEmpty else { return }
            let font = CTFontCreateWithName("Helvetica" as CFString, 1000, nil)
            let attrs: [CFString: Any] = [kCTFontAttributeName: font, kCTForegroundColorAttributeName: color]
            guard let attributed = CFAttributedStringCreate(nil, cmd.text as CFString, attrs as CFDictionary) else { return }
            let line = CTLineCreateWithAttributedString(attributed)
            ctx.saveGState()
            ctx.concatenate(cmd.transform)
            ctx.textMatrix = .identity
            ctx.textPosition = cmd.position
            CTLineDraw(line, ctx)
            ctx.restoreGState()
        }

        private static func bitmapAllocation(for size: CGSize, scale: CGFloat) -> BitmapAllocation? {
            guard let w = pixelDim(size.width, scale: scale),
                  let h = pixelDim(size.height, scale: scale)
            else { return nil }
            let pixels = w * h
            let bytes  = pixels * bytesPerPixel
            guard pixels <= maxPixelCount, bytes <= maxBitmapBytes else { return nil }
            return BitmapAllocation(width: w, height: h, byteCount: bytes)
        }

        private static func pixelDim(_ points: CGFloat, scale: CGFloat) -> Int? {
            let px = ceil(points * scale)
            guard px.isFinite, px > 0, px <= maxPixelDimension else { return nil }
            return Int(px)
        }

        private func cacheKey(for svg: MathJaxSVG, color: CGColor, scale: CGFloat, fontSize: CGFloat) -> NSString {
            let colorParts = (color.components ?? []).map { String(format: "%.8g", Double($0)) }.joined(separator: ",")
            return [
                "m:\(svg.markup.hashValue)",
                "fb:\(svg.fallbackText ?? "nil")",
                "sc:\(String(format: "%.4g", scale))",
                "fs:\(String(format: "%.4g", fontSize))",
                "c:\(colorParts)",
            ].joined(separator: "|") as NSString
        }
    }

    private final class Box {
        let value: FormulaImage
        init(_ v: FormulaImage) { value = v }
    }

    private struct BitmapAllocation {
        var width: Int
        var height: Int
        var byteCount: Int
    }
#endif
