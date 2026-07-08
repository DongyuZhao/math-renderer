import XCTest

@testable import SwiftMath

#if canImport(SwiftUI)
    import CoreGraphics

    final class MathSVGParserTests: XCTestCase {
        func testParsesLocalDefsAndUseNodes() throws {
            let svg = """
                <svg viewBox="0 -10 40 20">
                  <defs>
                    <path id="MJX-1" d="M0 0 H10 V10 H0 Z"></path>
                  </defs>
                  <use href="#MJX-1" x="5" y="-5"></use>
                </svg>
                """

            let document = try XCTUnwrap(MathSVGParser.parse(svg))

            XCTAssertEqual(document.viewBox, CGRect(x: 0, y: -10, width: 40, height: 20))
            XCTAssertEqual(document.paths.count, 1)
            XCTAssertNotNil(document.paths["MJX-1"])
            XCTAssertEqual(document.drawCommands.count, 1)
            XCTAssertEqual(document.drawCommands[0].transform.tx, 5, accuracy: 0.001)
            XCTAssertEqual(document.drawCommands[0].transform.ty, -5, accuracy: 0.001)
        }

        func testRejectsExternalReferences() {
            let svg =
                #"<svg viewBox="0 0 10 10"><use href="https://example.com/a.svg#x"></use></svg>"#

            XCTAssertNil(MathSVGParser.parse(svg))
        }

        func testRejectsDoctypeAndExternalEntityDeclarations() {
            let svg = """
                <!DOCTYPE svg [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <svg viewBox="0 0 10 10">
                  <path d="M0 0 H10 V10 H0 Z"></path>
                </svg>
                """

            XCTAssertNil(MathSVGParser.parse(svg))
        }
    }
#endif
