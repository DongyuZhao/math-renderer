/// MathRenderer — pure MathJax-driven LaTeX formula renderer.
///
/// Runs MathJax inside JavaScriptCore to produce SVG, then rasterises the SVG
/// to a platform image (UIImage / NSImage) using an in-house CoreGraphics
/// SVG subset renderer.
///
/// ## Quick start
///
/// ```swift
/// // Inline formula, system scale, 16pt font, black
/// if let image = await MathRenderer.render("x^2 + y^2 = r^2") {
///     // image.image    → UIImage / NSImage
///     // image.size     → logical size in points
///     // image.baselineOffset → for inline text placement
/// }
///
/// // Display (block) mode, custom colour
/// let opts = FormulaRenderOptions(standalone: true, fontSize: 20, scale: 2)
/// if let image = await MathRenderer.render("\\int_0^\\infty e^{-x^2}\\,dx", options: opts) { ... }
/// ```
public enum MathRenderer {

#if canImport(CoreGraphics)
    /// Renders a LaTeX formula string to a platform image.
    ///
    /// - Parameters:
    ///   - latex:   LaTeX source without surrounding `$` delimiters.
    ///   - options: Rendering options (mode, font size, scale).
    ///   - color:   Fill colour; `nil` defaults to black.
    ///   - scale:   Screen scale; uses `options.scale` when this parameter is `nil`.
    /// - Returns:   A `FormulaImage` on success, or `nil` if rendering failed.
    @MainActor
    public static func render(
        _ latex: String,
        options: FormulaRenderOptions = FormulaRenderOptions(),
        color: CGColor? = nil,
        scale: CGFloat? = nil
    ) async -> FormulaImage? {
        guard let svg = await MathJaxBridge.shared.svg(for: latex, options: options) else {
            return nil
        }
        let resolvedScale = scale ?? CGFloat(options.scale)
        return FormulaRasterizer.shared.image(
            from: svg,
            color: color,
            scale: resolvedScale,
            fontSize: CGFloat(options.fontSize)
        )
    }

    /// Returns only the SVG markup for a LaTeX formula (no rasterisation).
    public static func svg(
        _ latex: String,
        options: FormulaRenderOptions = FormulaRenderOptions()
    ) async -> MathJaxSVG? {
        await MathJaxBridge.shared.svg(for: latex, options: options)
    }
#endif
}
