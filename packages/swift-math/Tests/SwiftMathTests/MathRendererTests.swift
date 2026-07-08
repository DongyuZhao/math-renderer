import CoreGraphics
import SwiftMath
import XCTest

#if canImport(UIKit) || canImport(AppKit)
    final class MathRendererTests: XCTestCase {
        func testRenderPublishesPendingThenRasterizedFormulaForInlineLatex() async {
            let states = await collectStates(
                from: MathRenderer.render(
                    latex: "x^2 + y^2 = z^2",
                    options: MathRenderOptions(
                        standalone: false,
                        fontSize: 18,
                        scale: 2
                    ),
                    color: CGColor(gray: 0, alpha: 1)
                )
            )

            XCTAssertEqual(states.count, 2)
            guard case .pending = states[0] else {
                return XCTFail("Expected render to publish pending first")
            }
            guard case .succeeded(let rasterized) = states[1] else {
                return XCTFail("Expected render to succeed")
            }

            XCTAssertGreaterThan(rasterized.size.width, 0)
            XCTAssertGreaterThan(rasterized.size.height, 0)
            XCTAssertLessThanOrEqual(rasterized.offset, 0)
            XCTAssertNil(rasterized.fallback)
        }

        func testRenderFailsWithInlineLatexFallbackForInvalidInput() async {
            let states = await collectStates(
                from: MathRenderer.render(
                    latex: "x+y",
                    options: MathRenderOptions(
                        standalone: false,
                        fontSize: 0,
                        scale: 2
                    )
                )
            )

            XCTAssertEqual(states.count, 2)
            guard case .pending = states[0] else {
                return XCTFail("Expected render to publish pending first")
            }
            guard case .failed(let fallback) = states[1] else {
                return XCTFail("Expected render to fail")
            }

            XCTAssertEqual(fallback.reason, .invalidInput)
            XCTAssertEqual(fallback.text, #"\(x+y\)"#)
        }

        func testRenderFailsWithDisplayLatexFallbackForInvalidInput() async {
            let states = await collectStates(
                from: MathRenderer.render(
                    latex: "x+y",
                    options: MathRenderOptions(
                        standalone: true,
                        fontSize: 16,
                        scale: 0
                    )
                )
            )

            XCTAssertEqual(states.count, 2)
            guard case .pending = states[0] else {
                return XCTFail("Expected render to publish pending first")
            }
            guard case .failed(let fallback) = states[1] else {
                return XCTFail("Expected render to fail")
            }

            XCTAssertEqual(fallback.reason, .invalidInput)
            XCTAssertEqual(fallback.text, #"\[x+y\]"#)
        }

        func testRenderFailsWithFallbackForMathJaxError() async {
            let states = await collectStates(
                from: MathRenderer.render(
                    latex: #"\frac{"#,
                    options: MathRenderOptions(
                        standalone: true,
                        fontSize: 16,
                        scale: 1
                    )
                )
            )

            XCTAssertEqual(states.count, 2)
            guard case .pending = states[0] else {
                return XCTFail("Expected render to publish pending first")
            }
            guard case .failed(let fallback) = states[1] else {
                return XCTFail("Expected render to fail")
            }

            XCTAssertEqual(fallback.reason, .mathJaxError)
            XCTAssertEqual(fallback.text, #"\[\frac{\]"#)
        }

        func testRenderScalesSizeWithFontSize() async {
            let small = await renderedFormula(
                latex: "x+1",
                options: MathRenderOptions(standalone: false, fontSize: 12, scale: 1)
            )
            let large = await renderedFormula(
                latex: "x+1",
                options: MathRenderOptions(standalone: false, fontSize: 24, scale: 1)
            )

            XCTAssertNotNil(small)
            XCTAssertNotNil(large)
            XCTAssertGreaterThan(large?.size.width ?? 0, (small?.size.width ?? 0) * 1.5)
            XCTAssertGreaterThan(large?.size.height ?? 0, (small?.size.height ?? 0) * 1.5)
        }

        private func collectStates(from stream: AsyncStream<MathRenderState>) async
            -> [MathRenderState]
        {
            var states: [MathRenderState] = []
            for await state in stream {
                states.append(state)
            }
            return states
        }

        private func renderedFormula(
            latex: String,
            options: MathRenderOptions
        ) async -> MathRasterizedFormula? {
            let states = await collectStates(
                from: MathRenderer.render(latex: latex, options: options)
            )
            guard case .succeeded(let rasterized) = states.last else {
                return nil
            }
            return rasterized
        }
    }
#endif
