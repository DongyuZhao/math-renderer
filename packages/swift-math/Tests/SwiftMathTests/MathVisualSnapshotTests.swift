import CoreGraphics
import Foundation
import SwiftMath
import XCTest

#if canImport(UIKit) || canImport(AppKit)
    #if canImport(UIKit)
        import UIKit
    #elseif canImport(AppKit)
        import AppKit
    #endif

    /// Pixel (rasterization) snapshots — one per corpus formula, matching the
    /// shared golden SVGs 1:1 (`<id>.png` ↔ `<id>.svg`). Rendered at a fixed 2x
    /// scale for crisp baselines. Record with:
    ///   UPDATE_MATH_SNAPSHOTS=1 swift test --package-path packages/swift-math \
    ///     --filter MathVisualSnapshotTests
    final class MathVisualSnapshotTests: XCTestCase {
        func testFormulaImagesMatchSnapshots() async throws {
            continueAfterFailure = true
            for fixture in try FormulaFixtures.all() {
                let states = await collectStates(
                    from: MathRenderer.render(
                        latex: fixture.tex,
                        options: fixture.options(scale: 2),
                        color: CGColor(gray: 0, alpha: 1)
                    )
                )

                guard case .succeeded(let rasterized)? = states.last else {
                    XCTFail("Expected \(fixture.id) to render successfully; got \(states)")
                    continue
                }
                guard let pngData = pngSnapshotData(from: rasterized.image) else {
                    XCTFail("Could not encode PNG for \(fixture.id)")
                    continue
                }
                try assertImageSnapshot(pngData, named: fixture.id)
            }
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

        private func assertImageSnapshot(
            _ data: Data,
            named name: String,
            file: StaticString = #filePath,
            line: UInt = #line
        ) throws {
            let url = snapshotsDirectory.appendingPathComponent("\(name).png")
            if ProcessInfo.processInfo.environment["UPDATE_MATH_SNAPSHOTS"] == "1" {
                try FileManager.default.createDirectory(
                    at: snapshotsDirectory,
                    withIntermediateDirectories: true
                )
                try data.write(to: url, options: .atomic)
                return
            }

            let expected = try Data(contentsOf: url)
            XCTAssertEqual(data, expected, "\(name).png", file: file, line: line)
        }

        private var snapshotsDirectory: URL {
            URL(fileURLWithPath: #filePath)
                .deletingLastPathComponent()
                .appendingPathComponent("VisualSnapshots")
        }
    }

    private func pngSnapshotData(from image: MathPlatformImage) -> Data? {
        #if canImport(UIKit)
            return image.pngData()
        #elseif canImport(AppKit)
            guard let tiffRepresentation = image.tiffRepresentation,
                let bitmap = NSBitmapImageRep(data: tiffRepresentation)
            else {
                return nil
            }
            return bitmap.representation(using: .png, properties: [:])
        #endif
    }
#endif
