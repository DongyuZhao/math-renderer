import Foundation

// MARK: - Public types

/// Options that control how MathJax renders a formula.
public struct FormulaRenderOptions: Equatable, Sendable {
    /// `true` for display (block) mode; `false` for inline mode.
    public var standalone: Bool
    /// Reference font size in points (affects em unit size).
    public var fontSize: Double
    /// Additional scale factor applied when rasterising.
    public var scale: Double

    public init(standalone: Bool = false, fontSize: Double = 16, scale: Double = 1) {
        self.standalone = standalone
        self.fontSize = fontSize
        self.scale = scale
    }
}

/// The SVG viewBox returned by MathJax.
public struct MathJaxViewBox: Equatable, Sendable {
    public var minX: Double
    public var minY: Double
    public var width: Double
    public var height: Double

    public init(minX: Double, minY: Double, width: Double, height: Double) {
        self.minX = minX
        self.minY = minY
        self.width = width
        self.height = height
    }

    public static let zero = MathJaxViewBox(minX: 0, minY: 0, width: 0, height: 0)
}

/// The SVG produced by MathJax for a LaTeX formula.
public struct MathJaxSVG: Equatable, Sendable {
    /// Sanitised SVG markup string.
    public var markup: String
    /// Coordinate space of the SVG.
    public var viewBox: MathJaxViewBox
    /// Present when MathJax could not render the formula.
    public var fallbackText: String?

    public init(markup: String, viewBox: MathJaxViewBox, fallbackText: String? = nil) {
        self.markup = markup
        self.viewBox = viewBox
        self.fallbackText = fallbackText
    }
}

// MARK: - Bridge

/// Renders LaTeX formulas to SVG by running MathJax inside JavaScriptCore.
public final class MathJaxBridge: @unchecked Sendable {
    public static let shared = MathJaxBridge()

    private let environment = JavaScriptEnvironment(
        bridgeName: "MathRendererMathJax",
        scripts: [
            JavaScriptResource(name: "mathjax-renderer", subdir: "MathJax"),
            JavaScriptResource(name: "mathjax-bridge", subdir: "MathJax"),
        ]
    )

    public init() {}

    /// Renders `latex` and returns the SVG representation, or `nil` on failure.
    public func svg(
        for latex: String,
        options: FormulaRenderOptions = FormulaRenderOptions()
    ) async -> MathJaxSVG? {
        guard
            let payload = await environment.call(
                "renderJSON",
                arguments: [latex, options.jsBridgeOptions],
                as: RenderPayload.self
            )
        else { return nil }

        return Self.svg(from: payload, latex: latex, options: options)
    }

    // MARK: - Private helpers

    private static func svg(
        from payload: RenderPayload,
        latex: String,
        options: FormulaRenderOptions
    ) -> MathJaxSVG? {
        let markup = sanitized(payload.markup)
        let viewBox = payload.viewBox ?? extractViewBox(from: markup)
        let payloadFallback = nonEmpty(payload.fallbackText)
        let resolvedFallback =
            payloadFallback
            ?? (payload.isError ? defaultFallback(for: latex, standalone: options.standalone) : nil)

        if containsSVG(markup), markup.range(of: "viewBox", options: .caseInsensitive) != nil,
            let viewBox
        {
            return MathJaxSVG(markup: markup, viewBox: viewBox, fallbackText: resolvedFallback)
        }

        guard let resolvedFallback else { return nil }
        return MathJaxSVG(markup: markup, viewBox: viewBox ?? .zero, fallbackText: resolvedFallback)
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    private static func defaultFallback(for latex: String, standalone: Bool) -> String {
        standalone ? "$$\(latex)$$" : "$\(latex)$"
    }

    private static func containsSVG(_ markup: String) -> Bool {
        markup.range(of: "<svg", options: .caseInsensitive) != nil
    }

    private static func sanitized(_ markup: String) -> String {
        var out = markup
        out = replacing(#"<script[\s\S]*?</script>"#, in: out)
        out = replacing(#"\son[a-zA-Z]+\s*=\s*"[^"]*""#, in: out)
        out = replacing(#"\son[a-zA-Z]+\s*=\s*'[^']*'"#, in: out)
        out = replacing(#"\son[a-zA-Z]+\s*=\s*[^>\s]+"#, in: out)
        out = replacing(#"javascript:"#, in: out)
        return out
    }

    private static func replacing(_ pattern: String, in value: String) -> String {
        guard
            let rx = try? NSRegularExpression(pattern: pattern,
                                               options: [.caseInsensitive, .dotMatchesLineSeparators])
        else { return value }
        let range = NSRange(value.startIndex..<value.endIndex, in: value)
        return rx.stringByReplacingMatches(in: value, range: range, withTemplate: "")
    }

    private static func extractViewBox(from markup: String) -> MathJaxViewBox? {
        for pattern in [#"viewBox\s*=\s*"([^"]+)""#, #"viewBox\s*=\s*'([^']+)'"#] {
            guard
                let rx = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
                let match = rx.firstMatch(in: markup, range: NSRange(markup.startIndex..<markup.endIndex, in: markup)),
                let range = Range(match.range(at: 1), in: markup)
            else { continue }
            return parseViewBox(String(markup[range]))
        }
        return nil
    }

    // Exposed for RenderPayload decoding.
    static func parseViewBox(_ value: String) -> MathJaxViewBox? {
        let seps = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ","))
        let values = value.components(separatedBy: seps).compactMap(Double.init)
        guard values.count == 4 else { return nil }
        return MathJaxViewBox(minX: values[0], minY: values[1], width: values[2], height: values[3])
    }
}

// MARK: - JSON payload types (private)

private struct RenderPayload: Decodable {
    var markup: String
    var viewBox: MathJaxViewBox?
    var fallbackText: String?
    var isError: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        markup = try c.decodeIfPresent(String.self, forKey: .markup)
            ?? c.decodeIfPresent(String.self, forKey: .svg)
            ?? ""
        viewBox = try c.decodeIfPresent(DecodedViewBox.self, forKey: .viewBox)?.parsed
        fallbackText = try c.decodeIfPresent(String.self, forKey: .fallbackText)
        let ok = try c.decodeIfPresent(Bool.self, forKey: .ok)
        let decodedError = try c.decodeIfPresent(DecodedError.self, forKey: .error)
        isError = decodedError?.isError ?? ok.map { !$0 } ?? false
    }

    private enum CodingKeys: String, CodingKey {
        case ok, markup, svg, viewBox, fallbackText, error
    }
}

// Decodes viewBox as either a plain string ("x y w h") or a JSON object.
private enum DecodedViewBox: Decodable {
    case string(String)
    case object(ViewBoxObject)

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let s = try? c.decode(String.self) {
            self = .string(s)
        } else {
            self = .object(try c.decode(ViewBoxObject.self))
        }
    }

    var parsed: MathJaxViewBox? {
        switch self {
        case .string(let s): return MathJaxBridge.parseViewBox(s)
        case .object(let o): return o.value
        }
    }
}

private struct ViewBoxObject: Decodable {
    var minX: FlexDouble
    var minY: FlexDouble
    var width: FlexDouble
    var height: FlexDouble
    var value: MathJaxViewBox {
        MathJaxViewBox(minX: minX.v, minY: minY.v, width: width.v, height: height.v)
    }
}

private struct FlexDouble: Decodable {
    var v: Double
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let d = try? c.decode(Double.self) { v = d; return }
        if let s = try? c.decode(String.self), let d = Double(s) { v = d; return }
        throw DecodingError.dataCorruptedError(in: c, debugDescription: "Expected number or numeric string")
    }
}

// Decodes error as either a bool (true = error) or a non-empty string.
private enum DecodedError: Decodable {
    case bool(Bool)
    case string(String)

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let b = try? c.decode(Bool.self) {
            self = .bool(b)
        } else {
            self = .string(try c.decode(String.self))
        }
    }

    var isError: Bool {
        switch self {
        case .bool(let b): return b
        case .string(let s): return !s.isEmpty
        }
    }
}

// MARK: - FormulaRenderOptions → JS dictionary

private extension FormulaRenderOptions {
    var jsBridgeOptions: NSDictionary {
        ["standalone": standalone, "fontSize": fontSize, "scale": scale]
    }
}
