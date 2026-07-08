import XCTest

@testable import SwiftMath

#if canImport(SwiftUI)
    final class MathJaxBridgeTests: XCTestCase {
        func testLatexToSvgBridgeLivesInBundledResourceWithMathNaming() throws {
            let url = try XCTUnwrap(
                Bundle.module.url(
                    forResource: "latex-to-svg",
                    withExtension: "js",
                    subdirectory: "MathJax"
                ) ?? Bundle.module.url(forResource: "latex-to-svg", withExtension: "js")
            )
            let bridge = try String(contentsOf: url, encoding: .utf8)
            let rendererURL = try XCTUnwrap(
                Bundle.module.url(
                    forResource: "mathjax-core-runtime",
                    withExtension: "js",
                    subdirectory: "MathJax"
                ) ?? Bundle.module.url(forResource: "mathjax-core-runtime", withExtension: "js")
            )
            let renderer = try String(contentsOf: rendererURL, encoding: .utf8)

            XCTAssertTrue(bridge.contains("MathRenderLatexToSvg"))
            XCTAssertTrue(bridge.contains("renderJSON"))
            XCTAssertTrue(bridge.contains("standalone"))
            XCTAssertFalse(bridge.contains("MathRenderMathJax"))
            XCTAssertFalse(bridge.contains("SwiftMarkdownRender"))
            XCTAssertFalse(renderer.contains("MathRenderLatexToSvg"))
        }

        func testMathRenderOptionsExposeOnlyFormulaInputs() {
            let labels = Mirror(reflecting: MathRenderOptions())
                .children
                .compactMap(\.label)

            XCTAssertEqual(labels, ["standalone", "fontSize", "scale"])
        }

        func testMathJaxBridgeReturnsSelfContainedSVGForInlineFormula() async throws {
            let rendered = await MathJaxBridge.shared.svg(
                for: "x^2 + y^2 = z^2",
                options: MathRenderOptions(
                    standalone: false,
                    fontSize: 16,
                    scale: 1
                )
            )
            let svg = try XCTUnwrap(rendered)

            XCTAssertTrue(svg.markup.contains("<svg"))
            XCTAssertTrue(svg.markup.contains("viewBox"))
            XCTAssertFalse(svg.markup.contains("<script"))
            XCTAssertFalse(svg.markup.contains("javascript:"))
            XCTAssertGreaterThan(svg.viewBox.width, 0)
            XCTAssertGreaterThan(svg.viewBox.height, 0)
        }

        func testMathJaxBridgeReturnsFallbackForInvalidFormulaWithoutThrowing() async {
            let svg = await MathJaxBridge.shared.svg(
                for: #"\frac{"#,
                options: MathRenderOptions(
                    standalone: true,
                    fontSize: 16,
                    scale: 1
                )
            )

            XCTAssertNotNil(svg)
            XCTAssertEqual(svg?.fallbackText, #"\[\frac{\]"#)
        }
    }
#endif
