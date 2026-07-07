import XCTest
@testable import MathRenderer

final class MathRendererTests: XCTestCase {

    // MARK: - MathJaxBridge.parseViewBox

    func testParseViewBoxSpaceDelimited() {
        let vb = MathJaxBridge.parseViewBox("-1.5 -0.5 10.2 5.8")
        XCTAssertNotNil(vb)
        XCTAssertEqual(vb!.minX, -1.5, accuracy: 1e-9)
        XCTAssertEqual(vb!.minY, -0.5, accuracy: 1e-9)
        XCTAssertEqual(vb!.width, 10.2, accuracy: 1e-9)
        XCTAssertEqual(vb!.height, 5.8, accuracy: 1e-9)
    }

    func testParseViewBoxCommaDelimited() {
        let vb = MathJaxBridge.parseViewBox("0,0,100,50")
        XCTAssertNotNil(vb)
        XCTAssertEqual(vb!.minX, 0)
        XCTAssertEqual(vb!.minY, 0)
        XCTAssertEqual(vb!.width, 100)
        XCTAssertEqual(vb!.height, 50)
    }

    func testParseViewBoxMixedDelimiters() {
        let vb = MathJaxBridge.parseViewBox("0 0,80 40")
        XCTAssertNotNil(vb)
        XCTAssertEqual(vb!.width, 80)
        XCTAssertEqual(vb!.height, 40)
    }

    func testParseViewBoxTooFewValues() {
        XCTAssertNil(MathJaxBridge.parseViewBox("0 0 100"))
    }

    func testParseViewBoxTooManyValues() {
        XCTAssertNil(MathJaxBridge.parseViewBox("0 0 100 50 99"))
    }

    func testParseViewBoxEmptyString() {
        XCTAssertNil(MathJaxBridge.parseViewBox(""))
    }

    func testParseViewBoxNonNumeric() {
        XCTAssertNil(MathJaxBridge.parseViewBox("a b c d"))
    }

    func testViewBoxZeroConstant() {
        XCTAssertEqual(MathJaxViewBox.zero, MathJaxViewBox(minX: 0, minY: 0, width: 0, height: 0))
    }

    // MARK: - FormulaRenderOptions defaults

    func testFormulaRenderOptionsDefaults() {
        let opts = FormulaRenderOptions()
        XCTAssertFalse(opts.standalone)
        XCTAssertEqual(opts.fontSize, 16)
        XCTAssertEqual(opts.scale, 1)
    }

    func testFormulaRenderOptionsCustom() {
        let opts = FormulaRenderOptions(standalone: true, fontSize: 20, scale: 2)
        XCTAssertTrue(opts.standalone)
        XCTAssertEqual(opts.fontSize, 20)
        XCTAssertEqual(opts.scale, 2)
    }

    // MARK: - MathJaxBridge async SVG rendering (requires JSC + bundle)

    func testSVGInlineFormula() async throws {
        let bridge = MathJaxBridge()
        let svg = await bridge.svg(for: "x^2 + y^2 = r^2")
        XCTAssertNotNil(svg, "Expected SVG for a valid formula")
        XCTAssertTrue(svg?.markup.contains("<svg") ?? false, "Markup should contain <svg")
        XCTAssertFalse(svg?.viewBox.width == 0 && svg?.viewBox.height == 0,
                       "viewBox should have non-zero dimensions")
    }

    func testSVGDisplayFormula() async throws {
        let bridge = MathJaxBridge()
        let opts = FormulaRenderOptions(standalone: true, fontSize: 18)
        let svg = await bridge.svg(for: #"\int_0^\infty e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}"#,
                                   options: opts)
        XCTAssertNotNil(svg, "Expected SVG for a display formula")
        XCTAssertTrue(svg?.markup.contains("<svg") ?? false)
    }

    func testSVGSimpleExpression() async throws {
        let bridge = MathJaxBridge()
        let svg = await bridge.svg(for: "E = mc^2")
        XCTAssertNotNil(svg)
    }

    func testSVGErrorCaseDoesNotThrow() async throws {
        let bridge = MathJaxBridge()
        let opts = FormulaRenderOptions(standalone: true)
        // MathJax may return SVG with a fallback or nil — both are acceptable
        let result = await bridge.svg(for: "\\unknowncommand{bad}", options: opts)
        if let svg = result {
            // If a result was returned, it should have a fallback or markup
            XCTAssertTrue(!svg.markup.isEmpty || svg.fallbackText != nil)
        }
        // nil is also a valid result for an unknown command
    }

    // MARK: - MathRenderer convenience (CoreGraphics platforms only)

#if canImport(CoreGraphics)
    func testMathRendererSVGConvenienceMethod() async throws {
        let svg = await MathRenderer.svg("a^2 + b^2 = c^2")
        XCTAssertNotNil(svg, "MathRenderer.svg should return a result for a valid formula")
        XCTAssertTrue(svg?.markup.contains("<svg") ?? false)
    }

    func testMathRendererSVGWithOptions() async throws {
        let opts = FormulaRenderOptions(standalone: true, fontSize: 20, scale: 2)
        let svg = await MathRenderer.svg(#"\frac{1}{2}"#, options: opts)
        XCTAssertNotNil(svg)
    }
#endif
}
