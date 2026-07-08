import XCTest

@testable import SwiftMath

#if canImport(SwiftUI)
    import CoreGraphics

    final class MathSVGPathParserTests: XCTestCase {
        func testAbsoluteLineCommandsReturnIntegralBoundingBox() throws {
            let path = try XCTUnwrap(SVGPathParser.parse("M0 0 H10 V20 L0 20 Z"))

            XCTAssertEqual(path.boundingBox.integral, CGRect(x: 0, y: 0, width: 10, height: 20))
        }

        func testSmoothQuadraticCurveParsesMathJaxLikePath() throws {
            let path = try XCTUnwrap(SVGPathParser.parse("M95 178Q89 178 81 186T72 200"))
            let boundingBox = path.boundingBox

            XCTAssertFalse(path.isEmpty)
            XCTAssertGreaterThan(boundingBox.width, 0)
            XCTAssertGreaterThan(boundingBox.height, 0)
        }

        func testUnsupportedArcReturnsNil() {
            XCTAssertNil(SVGPathParser.parse("M0 0 A10 10 0 0 1 20 20"))
        }
    }
#endif
