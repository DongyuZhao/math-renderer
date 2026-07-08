import Foundation

public struct MathRenderOptions: Equatable, Sendable {
    public var standalone: Bool
    public var fontSize: Double
    public var scale: Double

    public init(
        standalone: Bool = false,
        fontSize: Double = 16,
        scale: Double = 1
    ) {
        self.standalone = standalone
        self.fontSize = fontSize
        self.scale = scale
    }
}

public enum MathDisplayMode: Equatable, Sendable {
    case inline
    case block

    public var standalone: Bool {
        self == .block
    }
}

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

public struct MathJaxSVG: Equatable, Sendable {
    public var ok: Bool
    public var markup: String
    public var viewBox: MathJaxViewBox
    public var fallbackText: String?
    public var error: String?

    public init(
        ok: Bool = true,
        markup: String,
        viewBox: MathJaxViewBox,
        fallbackText: String? = nil,
        error: String? = nil
    ) {
        self.ok = ok
        self.markup = markup
        self.viewBox = viewBox
        self.fallbackText = fallbackText
        self.error = error
    }
}

public final class MathJaxBridge: @unchecked Sendable {
    public static let shared = MathJaxBridge()

    public init() {}

    public func svg(
        for tex: String,
        options: MathRenderOptions = MathRenderOptions()
    ) async -> MathJaxSVG? {
        await MathRenderService.shared.svg(for: tex, options: options)
    }

    static func svg(
        from payload: MathJaxRenderPayload,
        tex: String,
        options: MathRenderOptions
    ) -> MathJaxSVG? {
        let markup = sanitizedMarkup(payload.markup)
        let viewBox = payload.viewBox ?? viewBox(from: markup)
        let payloadFallbackText = normalizedFallbackText(from: payload.fallbackText)
        let resolvedFallbackText =
            payloadFallbackText
            ?? (payload.isError ? fallbackText(for: tex, standalone: options.standalone) : nil)

        if containsSVG(markup), markup.range(of: "viewBox", options: .caseInsensitive) != nil,
            let viewBox
        {
            return MathJaxSVG(
                ok: !payload.isError,
                markup: markup,
                viewBox: viewBox,
                fallbackText: resolvedFallbackText,
                error: payload.error
            )
        }

        guard let resolvedFallbackText else {
            return nil
        }
        return MathJaxSVG(
            ok: !payload.isError,
            markup: markup,
            viewBox: viewBox ?? .zero,
            fallbackText: resolvedFallbackText,
            error: payload.error
        )
    }

    private static func normalizedFallbackText(from value: String?) -> String? {
        guard let value, !value.isEmpty else {
            return nil
        }
        return value
    }

    public static func fallbackText(for tex: String, standalone: Bool) -> String {
        standalone ? #"\[\#(tex)\]"# : #"\(\#(tex)\)"#
    }

    private static func containsSVG(_ markup: String) -> Bool {
        markup.range(of: "<svg", options: .caseInsensitive) != nil
    }

    static func sanitizedMarkup(_ markup: String) -> String {
        var output = markup
        output = replacing(pattern: #"<script[\s\S]*?</script>"#, in: output)
        output = replacing(pattern: #"\son[a-zA-Z]+\s*=\s*"[^"]*""#, in: output)
        output = replacing(pattern: #"\son[a-zA-Z]+\s*=\s*'[^']*'"#, in: output)
        output = replacing(pattern: #"\son[a-zA-Z]+\s*=\s*[^>\s]+"#, in: output)
        output = replacing(pattern: #"javascript:"#, in: output)
        return output
    }

    private static func replacing(pattern: String, in value: String) -> String {
        guard
            let expression = try? NSRegularExpression(
                pattern: pattern,
                options: [.caseInsensitive, .dotMatchesLineSeparators]
            )
        else {
            return value
        }
        let range = NSRange(value.startIndex..<value.endIndex, in: value)
        return expression.stringByReplacingMatches(in: value, range: range, withTemplate: "")
    }

    private static func viewBox(from markup: String) -> MathJaxViewBox? {
        for pattern in [#"viewBox\s*=\s*"([^"]+)""#, #"viewBox\s*=\s*'([^']+)'"#] {
            guard
                let expression = try? NSRegularExpression(
                    pattern: pattern,
                    options: [.caseInsensitive]
                ),
                let match = expression.firstMatch(
                    in: markup,
                    range: NSRange(markup.startIndex..<markup.endIndex, in: markup)
                ),
                let range = Range(match.range(at: 1), in: markup)
            else {
                continue
            }
            return parseViewBox(String(markup[range]))
        }
        return nil
    }

    fileprivate static func parseViewBox(_ value: String) -> MathJaxViewBox? {
        let separators = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ","))
        let values = value.components(separatedBy: separators).compactMap(Double.init)
        guard values.count == 4 else {
            return nil
        }
        return MathJaxViewBox(
            minX: values[0],
            minY: values[1],
            width: values[2],
            height: values[3]
        )
    }
}

extension MathRenderOptions {
    var bridgeOptions: [String: Any] {
        [
            "standalone": standalone,
            "fontSize": fontSize,
            "scale": scale,
        ]
    }
}

struct MathJaxRenderPayload: Decodable {
    var markup: String
    var viewBox: MathJaxViewBox?
    var fallbackText: String?
    var error: String?
    var isError: Bool

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        markup =
            try container.decodeIfPresent(String.self, forKey: .markup)
            ?? container.decodeIfPresent(String.self, forKey: .svg)
            ?? ""
        let decodedViewBox = try container.decodeIfPresent(
            MathJaxDecodedViewBox.self,
            forKey: .viewBox
        )
        viewBox = decodedViewBox?.viewBox
        fallbackText = try container.decodeIfPresent(String.self, forKey: .fallbackText)

        let ok = try container.decodeIfPresent(Bool.self, forKey: .ok)
        let decodedError = try container.decodeIfPresent(MathJaxErrorPayload.self, forKey: .error)
        error = decodedError?.message
        isError = decodedError?.isError ?? ok.map { !$0 } ?? false
    }

    private enum CodingKeys: String, CodingKey {
        case ok
        case markup
        case svg
        case viewBox
        case fallbackText
        case error
    }
}

private enum MathJaxDecodedViewBox: Decodable {
    case value(MathJaxViewBox)

    var viewBox: MathJaxViewBox {
        switch self {
        case .value(let viewBox): return viewBox
        }
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let raw = try? container.decode(String.self),
            let viewBox = MathJaxBridge.parseViewBox(raw)
        {
            self = .value(viewBox)
            return
        }

        let object = try container.decode(MathJaxViewBoxObject.self)
        self = .value(object.viewBox)
    }
}

private struct MathJaxViewBoxObject: Decodable {
    var minX: MathFlexibleDouble
    var minY: MathFlexibleDouble
    var width: MathFlexibleDouble
    var height: MathFlexibleDouble

    var viewBox: MathJaxViewBox {
        MathJaxViewBox(
            minX: minX.value,
            minY: minY.value,
            width: width.value,
            height: height.value
        )
    }
}

private enum MathJaxErrorPayload: Decodable {
    case missing
    case message(String)

    var message: String? {
        switch self {
        case .missing:
            return nil
        case .message(let message):
            return message
        }
    }

    var isError: Bool {
        switch self {
        case .missing:
            return false
        case .message:
            return true
        }
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .missing
            return
        }
        self = .message((try? container.decode(String.self)) ?? "")
    }
}

private struct MathFlexibleDouble: Decodable {
    var value: Double

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(Double.self) {
            self.value = value
            return
        }
        if let string = try? container.decode(String.self),
            let value = Double(string)
        {
            self.value = value
            return
        }
        throw DecodingError.dataCorruptedError(
            in: container,
            debugDescription: "Expected a number or numeric string."
        )
    }
}
