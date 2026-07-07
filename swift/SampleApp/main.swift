/// MathRendererSample — command-line demonstration of the MathRenderer Swift package.
///
/// Run with:  swift run MathRendererSample
///
/// Renders several LaTeX formulas through MathJax (via JavaScriptCore) and
/// prints the resulting SVG viewBox dimensions to standard output.

import Foundation
import MathRenderer

// Use MathJaxBridge directly so this target compiles on every SwiftPM platform
// (MathRenderer.svg() is gated on #if canImport(CoreGraphics)).
private let bridge = MathJaxBridge()

private let formulas: [(latex: String, label: String)] = [
    ("x^2 + y^2 = r^2",                                   "Pythagorean circle"),
    (#"\int_0^\infty e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}"#, "Gaussian integral"),
    ("E = mc^2",                                           "Mass-energy equivalence"),
    (#"\sum_{n=1}^{\infty} \frac{1}{n^2} = \frac{\pi^2}{6}"#, "Basel problem"),
    (#"\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}"#,              "Quadratic formula"),
]

Task {
    print("MathRenderer Swift sample — rendering \(formulas.count) formulas...\n")

    var successCount = 0
    for (latex, label) in formulas {
        let opts = FormulaRenderOptions(standalone: true)
        if let svg = await bridge.svg(for: latex, options: opts) {
            let vb = svg.viewBox
            print("✅ \(label)")
            print("   LaTeX:   \(latex)")
            print("   viewBox: \(vb.minX) \(vb.minY) \(vb.width) × \(vb.height)")
            print("   markup:  \(svg.markup.count) chars\n")
            successCount += 1
        } else {
            print("❌ \(label) — rendering failed")
            print("   LaTeX: \(latex)\n")
        }
    }

    print("Done: \(successCount)/\(formulas.count) rendered successfully.")
    exit(successCount == formulas.count ? 0 : 1)
}

RunLoop.main.run()
