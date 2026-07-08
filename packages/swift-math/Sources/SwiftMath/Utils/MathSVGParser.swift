#if canImport(UIKit) || canImport(AppKit)
    import CoreGraphics
    import Foundation

    struct MathSVGDocument {
        var viewBox: CGRect
        var paths: [String: CGPath]
        var drawCommands: [MathSVGDrawCommand]
        var textCommands: [MathSVGTextCommand]
    }

    struct MathSVGDrawCommand {
        var path: CGPath
        var transform: CGAffineTransform
    }

    struct MathSVGTextCommand {
        var text: String
        var position: CGPoint
        var transform: CGAffineTransform
    }

    enum MathSVGParser {
        private static let maximumSVGByteCount = 256_000

        static func parse(_ markup: String) -> MathSVGDocument? {
            guard markup.utf8.count <= maximumSVGByteCount,
                !containsForbiddenMarkupDeclaration(markup),
                let data = markup.data(using: .utf8)
            else {
                return nil
            }

            let delegate = Delegate()
            let parser = XMLParser(data: data)
            parser.delegate = delegate
            parser.shouldProcessNamespaces = false
            parser.shouldReportNamespacePrefixes = false
            parser.shouldResolveExternalEntities = false

            guard parser.parse(), !delegate.rejected else {
                return nil
            }

            return delegate.document
        }

        private static func containsForbiddenMarkupDeclaration(_ markup: String) -> Bool {
            let pattern = #"<!\s*(doctype|entity|element|attlist|notation)\b"#
            guard
                let expression = try? NSRegularExpression(
                    pattern: pattern, options: [.caseInsensitive])
            else {
                return true
            }

            return expression.firstMatch(
                in: markup,
                range: NSRange(markup.startIndex..<markup.endIndex, in: markup)
            ) != nil
        }
    }

    private final class Delegate: NSObject, XMLParserDelegate {
        private static let maximumElementCount = 2_048
        private static let maximumPathCount = 2_048
        private static let maximumDrawCommandCount = 2_048
        private static let maximumTextCommandCount = 128
        private static let maximumAttributeByteCount = 64_000
        private static let maximumTextByteCount = 8_192
        private static let maximumViewBoxByteCount = 128
        private static let maximumTransformByteCount = 2_048

        var paths: [String: CGPath] = [:]
        var drawCommands: [MathSVGDrawCommand] = []
        var textCommands: [MathSVGTextCommand] = []
        var rejected = false

        private var viewBox: CGRect?
        private var sawRootSVG = false
        private var elementCount = 0
        private var elementStack: [String] = []
        private var defsDepth = 0
        private var transformStack: [CGAffineTransform] = [.identity]
        private var pendingText: PendingText?

        var document: MathSVGDocument? {
            guard sawRootSVG, let viewBox else {
                return nil
            }

            return MathSVGDocument(
                viewBox: viewBox,
                paths: paths,
                drawCommands: drawCommands,
                textCommands: textCommands
            )
        }

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String] = [:]
        ) {
            guard !rejected else {
                return
            }

            elementCount += 1
            guard elementCount <= Self.maximumElementCount else {
                reject(parser)
                return
            }

            let element = Self.localName(qName ?? elementName)
            guard validateElementPosition(element, parser: parser),
                validateAttributes(attributeDict, parser: parser)
            else {
                return
            }

            switch element {
            case "svg":
                guard
                    let parsedViewBox = Self.parseViewBox(
                        Self.attribute(named: "viewBox", in: attributeDict))
                else {
                    reject(parser)
                    return
                }
                viewBox = parsedViewBox
                sawRootSVG = true

            case "defs":
                defsDepth += 1

            case "g":
                guard let localTransform = Self.parseElementTransform(attributeDict) else {
                    reject(parser)
                    return
                }
                transformStack.append(localTransform.concatenating(currentTransform))

            case "path":
                parsePath(attributeDict, parser: parser)

            case "use":
                parseUse(attributeDict, parser: parser)

            case "rect":
                parseRect(attributeDict, parser: parser)

            case "text":
                beginText(attributeDict, parser: parser)

            case "title", "desc", "metadata":
                break

            default:
                reject(parser)
                return
            }

            elementStack.append(element)
        }

        func parser(
            _ parser: XMLParser,
            didEndElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?
        ) {
            guard !rejected else {
                return
            }

            let element = Self.localName(qName ?? elementName)
            switch element {
            case "defs":
                defsDepth = max(0, defsDepth - 1)
            case "g":
                guard transformStack.count > 1 else {
                    reject(parser)
                    return
                }
                transformStack.removeLast()
            case "text":
                finishText(parser)
            default:
                break
            }

            guard elementStack.last == element else {
                reject(parser)
                return
            }
            elementStack.removeLast()
        }

        func parser(
            _ parser: XMLParser,
            foundExternalEntityDeclarationWithName name: String,
            publicID: String?,
            systemID: String?
        ) {
            reject(parser)
        }

        func parser(
            _ parser: XMLParser,
            foundInternalEntityDeclarationWithName name: String,
            value: String?
        ) {
            reject(parser)
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) {
            guard !rejected else {
                return
            }

            if var text = pendingText {
                text.value += string
                guard text.value.utf8.count <= Self.maximumTextByteCount else {
                    reject(parser)
                    return
                }
                pendingText = text
                return
            }

            if string.contains(where: { !$0.isWhitespace }) {
                reject(parser)
            }
        }

        func parser(_ parser: XMLParser, foundCDATA cdataBlock: Data) {
            guard !rejected else {
                return
            }

            reject(parser)
        }

        private var currentTransform: CGAffineTransform {
            transformStack.last ?? .identity
        }

        private func beginText(_ attributes: [String: String], parser: XMLParser) {
            guard defsDepth == 0,
                pendingText == nil,
                let x = Self.numberAttribute(named: "x", in: attributes, defaultValue: 0),
                let y = Self.numberAttribute(named: "y", in: attributes, defaultValue: 0),
                let localTransform = Self.parseElementTransform(attributes)
            else {
                reject(parser)
                return
            }

            pendingText = PendingText(
                value: "",
                position: CGPoint(x: x, y: y),
                transform: localTransform.concatenating(currentTransform)
            )
        }

        private func finishText(_ parser: XMLParser) {
            guard let text = pendingText else {
                reject(parser)
                return
            }
            pendingText = nil

            guard textCommands.count < Self.maximumTextCommandCount else {
                reject(parser)
                return
            }

            guard text.value.contains(where: { !$0.isWhitespace }) else {
                return
            }

            textCommands.append(
                MathSVGTextCommand(
                    text: text.value,
                    position: text.position,
                    transform: text.transform
                )
            )
        }

        private func parsePath(_ attributes: [String: String], parser: XMLParser) {
            guard let pathData = Self.attribute(named: "d", in: attributes),
                pathData.utf8.count <= Self.maximumAttributeByteCount,
                let path = SVGPathParser.parse(pathData)
            else {
                reject(parser)
                return
            }

            if defsDepth > 0 {
                guard Self.attribute(named: "transform", in: attributes) == nil,
                    let id = Self.attribute(named: "id", in: attributes),
                    !id.isEmpty,
                    paths.count < Self.maximumPathCount,
                    paths[id] == nil
                else {
                    reject(parser)
                    return
                }
                paths[id] = path
            } else {
                guard let localTransform = Self.parseElementTransform(attributes) else {
                    reject(parser)
                    return
                }
                appendDrawCommand(
                    MathSVGDrawCommand(
                        path: path,
                        transform: localTransform.concatenating(currentTransform)
                    ),
                    parser: parser
                )
            }
        }

        private func parseUse(_ attributes: [String: String], parser: XMLParser) {
            guard defsDepth == 0,
                Self.attribute(named: "transform", in: attributes) == nil,
                let reference = Self.attribute(named: "href", in: attributes),
                reference.hasPrefix("#"),
                reference.count > 1,
                !reference.contains("://")
            else {
                reject(parser)
                return
            }

            let id = String(reference.dropFirst())
            guard let path = paths[id],
                let x = Self.numberAttribute(named: "x", in: attributes, defaultValue: 0),
                let y = Self.numberAttribute(named: "y", in: attributes, defaultValue: 0)
            else {
                reject(parser)
                return
            }

            let localTransform = CGAffineTransform(translationX: x, y: y)
            appendDrawCommand(
                MathSVGDrawCommand(
                    path: path,
                    transform: localTransform.concatenating(currentTransform)
                ),
                parser: parser
            )
        }

        private func parseRect(_ attributes: [String: String], parser: XMLParser) {
            guard defsDepth == 0,
                let x = Self.numberAttribute(named: "x", in: attributes, defaultValue: 0),
                let y = Self.numberAttribute(named: "y", in: attributes, defaultValue: 0),
                let width = Self.requiredNumberAttribute(named: "width", in: attributes),
                let height = Self.requiredNumberAttribute(named: "height", in: attributes),
                width >= 0,
                height >= 0,
                let localTransform = Self.parseElementTransform(attributes)
            else {
                reject(parser)
                return
            }

            let path = CGPath(
                rect: CGRect(x: x, y: y, width: width, height: height), transform: nil)
            appendDrawCommand(
                MathSVGDrawCommand(
                    path: path,
                    transform: localTransform.concatenating(currentTransform)
                ),
                parser: parser
            )
        }

        private func appendDrawCommand(_ command: MathSVGDrawCommand, parser: XMLParser) {
            guard drawCommands.count < Self.maximumDrawCommandCount else {
                reject(parser)
                return
            }

            drawCommands.append(command)
        }

        private func validateElementPosition(_ element: String, parser: XMLParser) -> Bool {
            if elementStack.isEmpty {
                guard element == "svg", !sawRootSVG else {
                    reject(parser)
                    return false
                }
            } else {
                guard sawRootSVG, element != "svg" else {
                    reject(parser)
                    return false
                }
            }

            return true
        }

        private func validateAttributes(_ attributes: [String: String], parser: XMLParser) -> Bool {
            for (name, value) in attributes {
                let localName = Self.localName(name).lowercased()

                if localName.hasPrefix("on")
                    || value.range(of: "javascript:", options: [.caseInsensitive]) != nil
                {
                    reject(parser)
                    return false
                }

                if localName == "href", !value.hasPrefix("#") || value.contains("://") {
                    reject(parser)
                    return false
                }
            }

            return true
        }

        private func reject(_ parser: XMLParser) {
            rejected = true
            parser.abortParsing()
        }

        private static func parseViewBox(_ value: String?) -> CGRect? {
            guard let value,
                value.utf8.count <= maximumViewBoxByteCount,
                let numbers = NumberListParser.parse(value, maximumValueCount: 4),
                numbers.count == 4,
                numbers[2] > 0,
                numbers[3] > 0
            else {
                return nil
            }

            return CGRect(x: numbers[0], y: numbers[1], width: numbers[2], height: numbers[3])
        }

        private static func parseElementTransform(_ attributes: [String: String])
            -> CGAffineTransform?
        {
            guard let transform = attribute(named: "transform", in: attributes) else {
                return .identity
            }

            guard transform.utf8.count <= maximumTransformByteCount else {
                return nil
            }

            return parseTransform(transform)
        }

        private static func parseTransform(_ value: String) -> CGAffineTransform? {
            let pattern = #"([A-Za-z]+)\s*\(([^)]*)\)"#
            guard let expression = try? NSRegularExpression(pattern: pattern) else {
                return nil
            }

            var composedTransform = CGAffineTransform.identity
            var cursor = value.startIndex
            let matches = expression.matches(
                in: value,
                range: NSRange(value.startIndex..<value.endIndex, in: value)
            )

            guard !matches.isEmpty else {
                return nil
            }

            for match in matches {
                guard let fullRange = Range(match.range(at: 0), in: value),
                    let nameRange = Range(match.range(at: 1), in: value),
                    let argsRange = Range(match.range(at: 2), in: value),
                    isTransformSeparator(value[cursor..<fullRange.lowerBound])
                else {
                    return nil
                }

                let name = String(value[nameRange])
                guard
                    let args = NumberListParser.parse(
                        String(value[argsRange]), maximumValueCount: 6),
                    let nextTransform = transformCommand(named: name, args: args)
                else {
                    return nil
                }

                composedTransform = nextTransform.concatenating(composedTransform)
                cursor = fullRange.upperBound
            }

            guard isTransformSeparator(value[cursor..<value.endIndex]) else {
                return nil
            }

            return composedTransform
        }

        private static func transformCommand(named name: String, args: [CGFloat])
            -> CGAffineTransform?
        {
            switch name {
            case "translate":
                guard args.count == 1 || args.count == 2 else {
                    return nil
                }
                return CGAffineTransform(translationX: args[0], y: args.count == 2 ? args[1] : 0)

            case "scale":
                guard args.count == 1 || args.count == 2 else {
                    return nil
                }
                return CGAffineTransform(scaleX: args[0], y: args.count == 2 ? args[1] : args[0])

            case "matrix":
                guard args.count == 6 else {
                    return nil
                }
                return CGAffineTransform(
                    a: args[0],
                    b: args[1],
                    c: args[2],
                    d: args[3],
                    tx: args[4],
                    ty: args[5]
                )

            default:
                return nil
            }
        }

        private static func numberAttribute(
            named name: String,
            in attributes: [String: String],
            defaultValue: CGFloat
        ) -> CGFloat? {
            guard let value = attribute(named: name, in: attributes) else {
                return defaultValue
            }

            return parseNumber(value)
        }

        private static func requiredNumberAttribute(
            named name: String,
            in attributes: [String: String]
        ) -> CGFloat? {
            guard let value = attribute(named: name, in: attributes) else {
                return nil
            }

            return parseNumber(value)
        }

        private static func parseNumber(_ value: String) -> CGFloat? {
            guard let numbers = NumberListParser.parse(value, maximumValueCount: 1),
                numbers.count == 1
            else {
                return nil
            }

            return numbers[0]
        }

        private static func attribute(named name: String, in attributes: [String: String])
            -> String?
        {
            if let value = attributes[name] {
                return value
            }

            return attributes.first { localName($0.key) == name }?.value
        }

        private static func localName(_ name: String) -> String {
            guard let separator = name.lastIndex(of: ":") else {
                return name
            }

            return String(name[name.index(after: separator)...])
        }

        private struct PendingText {
            var value: String
            var position: CGPoint
            var transform: CGAffineTransform
        }

        private static func isTransformSeparator(_ text: Substring) -> Bool {
            text.allSatisfy { character in
                character == "," || character.isWhitespace
            }
        }
    }

    private enum NumberListParser {
        static func parse(_ string: String, maximumValueCount: Int = 128) -> [CGFloat]? {
            let bytes = Array(string.utf8)
            var values: [CGFloat] = []
            var index = 0

            index = skipWhitespace(bytes, from: index)
            guard index < bytes.count else {
                return []
            }

            while index < bytes.count {
                guard let number = number(in: bytes, at: index) else {
                    return nil
                }

                guard values.count < maximumValueCount else {
                    return nil
                }

                values.append(CGFloat(number.value))
                index = number.nextIndex

                var consumedComma = false
                var consumedSeparator = false

                while index < bytes.count {
                    if isWhitespace(bytes[index]) {
                        consumedSeparator = true
                        index += 1
                    } else if bytes[index] == 44 {
                        guard !consumedComma else {
                            return nil
                        }
                        consumedComma = true
                        consumedSeparator = true
                        index += 1
                    } else {
                        break
                    }
                }

                if index == bytes.count {
                    return consumedComma ? nil : values
                }

                if !consumedSeparator, !isSign(bytes[index]) {
                    return nil
                }
            }

            return values
        }

        private static func number(in bytes: [UInt8], at startIndex: Int) -> (
            value: Double, nextIndex: Int
        )? {
            var index = startIndex

            if index < bytes.count, isSign(bytes[index]) {
                index += 1
            }

            var wholeDigits = 0
            while index < bytes.count, isDigit(bytes[index]) {
                wholeDigits += 1
                index += 1
            }

            var fractionDigits = 0
            if index < bytes.count, bytes[index] == 46 {
                index += 1

                while index < bytes.count, isDigit(bytes[index]) {
                    fractionDigits += 1
                    index += 1
                }
            }

            guard wholeDigits > 0 || fractionDigits > 0 else {
                return nil
            }

            if index < bytes.count, bytes[index] == 69 || bytes[index] == 101 {
                index += 1

                if index < bytes.count, isSign(bytes[index]) {
                    index += 1
                }

                var exponentDigits = 0
                while index < bytes.count, isDigit(bytes[index]) {
                    exponentDigits += 1
                    index += 1
                }

                guard exponentDigits > 0 else {
                    return nil
                }
            }

            let text = String(decoding: bytes[startIndex..<index], as: UTF8.self)
            guard let value = Double(text), value.isFinite else {
                return nil
            }

            return (value, index)
        }

        private static func skipWhitespace(_ bytes: [UInt8], from startIndex: Int) -> Int {
            var index = startIndex
            while index < bytes.count, isWhitespace(bytes[index]) {
                index += 1
            }
            return index
        }

        private static func isWhitespace(_ byte: UInt8) -> Bool {
            byte == 32 || byte == 9 || byte == 10 || byte == 12 || byte == 13
        }

        private static func isSign(_ byte: UInt8) -> Bool {
            byte == 43 || byte == 45
        }

        private static func isDigit(_ byte: UInt8) -> Bool {
            byte >= 48 && byte <= 57
        }
    }
#endif
