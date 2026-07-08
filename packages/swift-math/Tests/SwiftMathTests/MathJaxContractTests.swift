import Foundation
import XCTest

@testable import SwiftMath

#if canImport(SwiftUI)
    /// Contract test: the native MathJax runtime renders every formula in the
    /// shared corpus successfully. SVG *generation* fidelity (exact markup) is
    /// covered once at the shared layer by the golden-SVG test; pixel
    /// *rasterization* is covered by `MathVisualSnapshotTests`.
    final class MathJaxContractTests: XCTestCase {
        func testEveryCorpusFormulaRenders() async throws {
            for fixture in try FormulaFixtures.all() {
                let result = await MathJaxBridge.shared.svg(
                    for: fixture.tex,
                    options: fixture.options()
                )
                let rendered = try XCTUnwrap(result, fixture.id)

                XCTAssertTrue(
                    rendered.ok,
                    "\(fixture.id): \(rendered.error ?? rendered.fallbackText ?? "unknown error")"
                )
                XCTAssertNil(rendered.fallbackText, fixture.id)
                XCTAssertGreaterThan(rendered.viewBox.width, 0, fixture.id)
                XCTAssertGreaterThan(rendered.viewBox.height, 0, fixture.id)
                XCTAssertTrue(
                    rendered.markup.localizedCaseInsensitiveContains("<svg"),
                    fixture.id
                )
                XCTAssertFalse(
                    rendered.markup.localizedCaseInsensitiveContains(#"data-mml-node="merror""#),
                    fixture.id
                )
            }
        }

        func testInvalidTexProducesFallback() async throws {
            let result = await MathJaxBridge.shared.svg(
                for: #"\frac{"#,
                options: MathRenderOptions(standalone: true, fontSize: 16, scale: 1)
            )
            let rendered = try XCTUnwrap(result)

            XCTAssertFalse(rendered.ok)
            XCTAssertNotNil(rendered.fallbackText)
        }
    }
#endif
