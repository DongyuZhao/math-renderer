#if canImport(CoreGraphics)
    import CoreGraphics
    import Foundation

    // MARK: - Result types

    struct SVGDocument {
        var viewBox: CGRect
        var paths: [String: CGPath]
        var drawCommands: [SVGDrawCommand]
        var textCommands: [SVGTextCommand]
    }

    struct SVGDrawCommand {
        var path: CGPath
        var transform: CGAffineTransform
    }

    struct SVGTextCommand {
        var text: String
        var position: CGPoint
        var transform: CGAffineTransform
    }

    // MARK: - Parser

    /// Parses the SVG subset produced by MathJax into CoreGraphics draw commands.
    enum FormulaSVGParser {
        private static let maximumSVGByteCount = 256_000

        static func parse(_ markup: String) -> SVGDocument? {
            guard markup.utf8.count <= maximumSVGByteCount,
                !containsForbiddenDeclaration(markup),
                let data = markup.data(using: .utf8)
            else { return nil }

            let delegate = Delegate()
            let parser = XMLParser(data: data)
            parser.delegate = delegate
            parser.shouldProcessNamespaces = false
            parser.shouldReportNamespacePrefixes = false
            parser.shouldResolveExternalEntities = false

            guard parser.parse(), !delegate.rejected else { return nil }
            return delegate.document
        }

        private static func containsForbiddenDeclaration(_ markup: String) -> Bool {
            let pattern = #"<!\s*(doctype|entity|element|attlist|notation)\b"#
            guard let rx = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return true }
            return rx.firstMatch(in: markup, range: NSRange(markup.startIndex..<markup.endIndex, in: markup)) != nil
        }
    }

    // MARK: - XMLParserDelegate

    private final class Delegate: NSObject, XMLParserDelegate {
        private static let maxElements    = 2_048
        private static let maxPaths       = 2_048
        private static let maxDrawCmds    = 2_048
        private static let maxTextCmds    = 128
        private static let maxAttrBytes   = 64_000
        private static let maxTextBytes   = 8_192
        private static let maxViewBoxBytes = 128
        private static let maxTransformBytes = 2_048

        var paths: [String: CGPath] = [:]
        var drawCommands: [SVGDrawCommand] = []
        var textCommands: [SVGTextCommand] = []
        var rejected = false

        private var viewBox: CGRect?
        private var sawRoot = false
        private var elementCount = 0
        private var elementStack: [String] = []
        private var defsDepth = 0
        private var transformStack: [CGAffineTransform] = [.identity]
        private var pendingText: PendingText?

        var document: SVGDocument? {
            guard sawRoot, let viewBox else { return nil }
            return SVGDocument(viewBox: viewBox, paths: paths,
                               drawCommands: drawCommands, textCommands: textCommands)
        }

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attrs: [String: String] = [:]
        ) {
            guard !rejected else { return }

            elementCount += 1
            guard elementCount <= Self.maxElements else { reject(parser); return }

            let element = localName(qName ?? elementName)
            guard validateAttributes(attrs, parser: parser) else { return }

            switch element {
            case "svg":
                guard let vb = parseViewBox(attr("viewBox", in: attrs)) else { reject(parser); return }
                viewBox = vb; sawRoot = true

            case "defs":
                defsDepth += 1

            case "g":
                guard let local = parseTransform(attrs) else { reject(parser); return }
                transformStack.append(local.concatenating(currentTransform))

            case "path":
                parsePath(attrs, parser: parser)

            case "use":
                parseUse(attrs, parser: parser)

            case "rect":
                parseRect(attrs, parser: parser)

            case "text":
                beginText(attrs, parser: parser)

            case "title", "desc", "metadata":
                break

            default:
                reject(parser); return
            }

            elementStack.append(element)
        }

        func parser(
            _ parser: XMLParser,
            didEndElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?
        ) {
            guard !rejected else { return }
            let element = localName(qName ?? elementName)
            switch element {
            case "defs": defsDepth = max(0, defsDepth - 1)
            case "g":
                guard transformStack.count > 1 else { reject(parser); return }
                transformStack.removeLast()
            case "text": finishText(parser)
            default: break
            }
            guard elementStack.last == element else { reject(parser); return }
            elementStack.removeLast()
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) {
            guard !rejected else { return }
            guard var pending = pendingText else { return }
            guard pending.text.utf8.count + string.utf8.count <= Self.maxTextBytes else { reject(parser); return }
            pending.text += string
            pendingText = pending
        }

        func parser(_ parser: XMLParser, parseErrorOccurred parseError: Error) { rejected = true }
        func parser(_ parser: XMLParser, validationErrorOccurred validationError: Error) { rejected = true }

        // MARK: - Helpers

        private var currentTransform: CGAffineTransform { transformStack.last ?? .identity }
        private var inDefs: Bool { defsDepth > 0 }

        private func reject(_ parser: XMLParser) { rejected = true; parser.abortParsing() }

        private func localName(_ name: String) -> String {
            if let colon = name.lastIndex(of: ":") {
                return String(name[name.index(after: colon)...])
            }
            return name.lowercased()
        }

        private func attr(_ name: String, in attrs: [String: String]) -> String? {
            attrs[name] ?? attrs[name.lowercased()]
        }

        private func validateAttributes(_ attrs: [String: String], parser: XMLParser) -> Bool {
            let totalBytes = attrs.values.reduce(0) { $0 + $1.utf8.count }
            guard totalBytes <= Self.maxAttrBytes else { reject(parser); return false }
            return true
        }

        private func parseViewBox(_ value: String?) -> CGRect? {
            guard let value, value.utf8.count <= Self.maxViewBoxBytes else { return nil }
            let seps = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ","))
            let parts = value.components(separatedBy: seps).compactMap(Double.init)
            guard parts.count == 4 else { return nil }
            return CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
        }

        private func parseTransform(_ attrs: [String: String]) -> CGAffineTransform? {
            guard let raw = attr("transform", in: attrs) else { return .identity }
            guard raw.utf8.count <= Self.maxTransformBytes else { return nil }
            return SVGTransformParser.parse(raw) ?? .identity
        }

        // MARK: - Element handlers

        private func parsePath(_ attrs: [String: String], parser: XMLParser) {
            guard let d = attr("d", in: attrs) else { return }
            guard let cgPath = SVGPathParser.parse(d) else { reject(parser); return }

            if let id = attr("id", in: attrs) {
                guard paths.count < Self.maxPaths else { reject(parser); return }
                paths[id] = cgPath
            }

            guard !inDefs else { return }
            guard drawCommands.count < Self.maxDrawCmds else { reject(parser); return }
            let xf = parseTransform(attrs) ?? currentTransform
            drawCommands.append(SVGDrawCommand(path: cgPath, transform: xf.concatenating(currentTransform)))
        }

        private func parseUse(_ attrs: [String: String], parser: XMLParser) {
            guard !inDefs else { return }
            guard let href = attr("href", in: attrs) ?? attr("xlink:href", in: attrs) else { return }
            let id = href.hasPrefix("#") ? String(href.dropFirst()) : href
            guard let cgPath = paths[id] else { return }

            let x = attr("x", in: attrs).flatMap(Double.init) ?? 0
            let y = attr("y", in: attrs).flatMap(Double.init) ?? 0
            let translate = CGAffineTransform(translationX: x, y: y)
            let xf = (parseTransform(attrs) ?? .identity).concatenating(translate).concatenating(currentTransform)

            guard drawCommands.count < Self.maxDrawCmds else { reject(parser); return }
            drawCommands.append(SVGDrawCommand(path: cgPath, transform: xf))
        }

        private func parseRect(_ attrs: [String: String], parser: XMLParser) {
            guard !inDefs else { return }
            let x = attr("x", in: attrs).flatMap(Double.init) ?? 0
            let y = attr("y", in: attrs).flatMap(Double.init) ?? 0
            guard
                let w = attr("width", in: attrs).flatMap(Double.init), w > 0,
                let h = attr("height", in: attrs).flatMap(Double.init), h > 0
            else { return }

            let rect = CGRect(x: x, y: y, width: w, height: h)
            let cgPath = CGPath(rect: rect, transform: nil)
            let xf = (parseTransform(attrs) ?? .identity).concatenating(currentTransform)
            guard drawCommands.count < Self.maxDrawCmds else { reject(parser); return }
            drawCommands.append(SVGDrawCommand(path: cgPath, transform: xf))
        }

        private func beginText(_ attrs: [String: String], parser: XMLParser) {
            guard !inDefs else { return }
            let x = attr("x", in: attrs).flatMap(Double.init) ?? 0
            let y = attr("y", in: attrs).flatMap(Double.init) ?? 0
            let xf = (parseTransform(attrs) ?? .identity).concatenating(currentTransform)
            pendingText = PendingText(position: CGPoint(x: x, y: y), transform: xf, text: "")
        }

        private func finishText(_ parser: XMLParser) {
            guard var pending = pendingText else { return }
            pendingText = nil
            let trimmed = pending.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return }
            guard textCommands.count < Self.maxTextCmds else { reject(parser); return }
            textCommands.append(SVGTextCommand(text: trimmed,
                                               position: pending.position,
                                               transform: pending.transform))
        }
    }

    private struct PendingText {
        var position: CGPoint
        var transform: CGAffineTransform
        var text: String
    }

    // MARK: - SVG transform parser

    private enum SVGTransformParser {
        static func parse(_ value: String) -> CGAffineTransform? {
            var transform = CGAffineTransform.identity
            let pattern = #"(\w+)\s*\(([^)]*)\)"#
            guard let rx = try? NSRegularExpression(pattern: pattern) else { return nil }
            let range = NSRange(value.startIndex..<value.endIndex, in: value)
            for match in rx.matches(in: value, range: range) {
                guard
                    let fnRange  = Range(match.range(at: 1), in: value),
                    let argRange = Range(match.range(at: 2), in: value)
                else { continue }
                let fn   = String(value[fnRange])
                let args = String(value[argRange])
                    .components(separatedBy: CharacterSet(charactersIn: ", ").union(.whitespacesAndNewlines))
                    .compactMap(Double.init)

                switch fn {
                case "matrix" where args.count == 6:
                    let m = CGAffineTransform(a: args[0], b: args[1], c: args[2], d: args[3], tx: args[4], ty: args[5])
                    transform = m.concatenating(transform)
                case "translate":
                    let tx = args.count >= 1 ? args[0] : 0
                    let ty = args.count >= 2 ? args[1] : 0
                    transform = CGAffineTransform(translationX: tx, y: ty).concatenating(transform)
                case "scale":
                    let sx = args.count >= 1 ? args[0] : 1
                    let sy = args.count >= 2 ? args[1] : sx
                    transform = CGAffineTransform(scaleX: sx, y: sy).concatenating(transform)
                case "rotate" where args.count >= 1:
                    let angle = args[0] * .pi / 180
                    if args.count == 3 {
                        let cx = args[1], cy = args[2]
                        transform = CGAffineTransform(translationX: cx, y: cy)
                            .rotated(by: angle)
                            .translatedBy(x: -cx, y: -cy)
                            .concatenating(transform)
                    } else {
                        transform = CGAffineTransform(rotationAngle: angle).concatenating(transform)
                    }
                default:
                    break
                }
            }
            return transform
        }
    }
#endif
