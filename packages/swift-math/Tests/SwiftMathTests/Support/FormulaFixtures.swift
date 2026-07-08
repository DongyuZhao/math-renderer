import Foundation

@testable import SwiftMath

/// One row of the shared corpus (packages/mathjax-core/fixtures/common-formulas.tsv).
struct FormulaFixture {
    let id: String
    let category: String
    let level: String
    let displayMode: String
    let fontSize: Double
    let scale: Double
    let tex: String

    var isBlock: Bool { displayMode == "block" }

    func options(scale overrideScale: Double? = nil) -> MathRenderOptions {
        MathRenderOptions(
            standalone: isBlock,
            fontSize: fontSize,
            scale: overrideScale ?? scale
        )
    }
}

/// Loads the shared formula corpus that drives every platform's tests. Reads the
/// TSV/JSON straight from the repo (located relative to this source file) so
/// there is a single source of truth across React, Swift, and Android.
enum FormulaFixtures {
    static let expectedHeader = "id\tcategory\tlevel\tdisplayMode\tfontSize\tscale\ttex"

    static func all(file: StaticString = #filePath) throws -> [FormulaFixture] {
        let url = fixturesDirectory(file: file).appendingPathComponent("common-formulas.tsv")
        let contents = try String(contentsOf: url, encoding: .utf8)
        var lines = contents.split(separator: "\n", omittingEmptySubsequences: true)

        let header = lines.removeFirst()
        guard header == expectedHeader else {
            throw FixtureError.badHeader(String(header))
        }

        return try lines.map { line in
            let columns = line.split(
                separator: "\t", maxSplits: 6, omittingEmptySubsequences: false)
            guard columns.count == 7 else {
                throw FixtureError.badRow(String(line))
            }
            guard let fontSize = Double(columns[4]), let scale = Double(columns[5]) else {
                throw FixtureError.badRow(String(line))
            }
            return FormulaFixture(
                id: String(columns[0]),
                category: String(columns[1]),
                level: String(columns[2]),
                displayMode: String(columns[3]),
                fontSize: fontSize,
                scale: scale,
                tex: String(columns[6])
            )
        }
    }

    /// Walk up from this file until the shared fixtures directory is found, so
    /// the tests do not hard-code a directory depth.
    static func fixturesDirectory(file: StaticString) -> URL {
        var url = URL(fileURLWithPath: "\(file)")
        while url.pathComponents.count > 1 {
            url.deleteLastPathComponent()
            let candidate = url.appendingPathComponent("packages/mathjax-core/fixtures")
            if FileManager.default.fileExists(
                atPath: candidate.appendingPathComponent("common-formulas.tsv").path
            ) {
                return candidate
            }
        }
        fatalError("Could not locate packages/mathjax-core/fixtures from \(file)")
    }

    enum FixtureError: Error {
        case badHeader(String)
        case badRow(String)
    }
}
