#if canImport(CoreGraphics)
    import CoreGraphics

    /// Parses an SVG path `d` attribute string into a `CGPath`.
    enum SVGPathParser {
        private static let maximumPathDataByteCount = 64_000
        private static let maximumTokenCount = 16_384

        static func parse(_ d: String) -> CGPath? {
            guard d.utf8.count <= maximumPathDataByteCount,
                let tokens = Tokenizer(d).tokens(maximumCount: maximumTokenCount),
                !tokens.isEmpty
            else { return nil }

            var parser = Parser(tokens: tokens)
            return parser.parse()
        }
    }

    // MARK: - Tokenizer

    extension SVGPathParser {
        fileprivate enum Token {
            case command(UInt8)
            case number(CGFloat)
        }

        fileprivate struct Tokenizer {
            private enum LastToken { case command, number, comma }

            let bytes: [UInt8]

            init(_ string: String) { bytes = Array(string.utf8) }

            func tokens(maximumCount: Int) -> [Token]? {
                var tokens: [Token] = []
                var index = 0
                var lastToken: LastToken?
                var sawSeparator = false

                while index < bytes.count {
                    let byte = bytes[index]

                    if Self.isWhitespace(byte) {
                        sawSeparator = true; index += 1
                    } else if byte == 44 {                         // comma
                        guard lastToken == .number else { return nil }
                        lastToken = .comma; sawSeparator = true; index += 1
                    } else if Self.isCommand(byte) {
                        guard lastToken != .comma else { return nil }
                        guard append(.command(byte), to: &tokens, max: maximumCount) else { return nil }
                        lastToken = .command; sawSeparator = false; index += 1
                    } else if Self.isNumberStart(byte) {
                        if lastToken == .number, !sawSeparator, !Self.isSign(byte) { return nil }
                        guard let num = number(at: index) else { return nil }
                        guard append(.number(CGFloat(num.value)), to: &tokens, max: maximumCount) else { return nil }
                        lastToken = .number; sawSeparator = false; index = num.nextIndex
                    } else {
                        return nil
                    }
                }
                guard lastToken != .comma else { return nil }
                return tokens
            }

            private func append(_ token: Token, to tokens: inout [Token], max: Int) -> Bool {
                guard tokens.count < max else { return false }
                tokens.append(token); return true
            }

            private func number(at start: Int) -> (value: Double, nextIndex: Int)? {
                var i = start
                if i < bytes.count, Self.isSign(bytes[i]) { i += 1 }

                var whole = 0
                while i < bytes.count, Self.isDigit(bytes[i]) { whole += 1; i += 1 }

                var frac = 0
                if i < bytes.count, bytes[i] == 46 {              // '.'
                    i += 1
                    while i < bytes.count, Self.isDigit(bytes[i]) { frac += 1; i += 1 }
                }
                guard whole > 0 || frac > 0 else { return nil }

                if i < bytes.count, bytes[i] == 69 || bytes[i] == 101 { // 'E' or 'e'
                    i += 1
                    if i < bytes.count, Self.isSign(bytes[i]) { i += 1 }
                    var exp = 0
                    while i < bytes.count, Self.isDigit(bytes[i]) { exp += 1; i += 1 }
                    guard exp > 0 else { return nil }
                }

                let text = String(decoding: bytes[start..<i], as: UTF8.self)
                guard let value = Double(text), value.isFinite else { return nil }
                return (value, i)
            }

            private static func isWhitespace(_ b: UInt8) -> Bool { b == 32 || b == 9 || b == 10 || b == 12 || b == 13 }
            private static func isCommand(_ b: UInt8) -> Bool { (b >= 65 && b <= 90) || (b >= 97 && b <= 122) }
            private static func isNumberStart(_ b: UInt8) -> Bool { isSign(b) || b == 46 || isDigit(b) }
            private static func isSign(_ b: UInt8) -> Bool { b == 43 || b == 45 }
            private static func isDigit(_ b: UInt8) -> Bool { b >= 48 && b <= 57 }
        }
    }

    // MARK: - Parser

    extension SVGPathParser {
        fileprivate struct Parser {
            let tokens: [Token]
            let path = CGMutablePath()
            var index = 0
            var currentPoint = CGPoint.zero
            var subpathStart = CGPoint.zero
            var hasCurrentPoint = false
            var lastCubicControl: CGPoint?
            var lastQuadraticControl: CGPoint?

            mutating func parse() -> CGPath? {
                var activeCommand: UInt8?

                while !isAtEnd {
                    if let cmd = readCommand() {
                        guard Self.isSupported(cmd) else { return nil }
                        activeCommand = cmd
                    } else if activeCommand == nil {
                        return nil
                    }
                    guard let cmd = activeCommand else { return nil }

                    switch cmd {
                    case 77, 109: // M m
                        guard consumeMove(cmd) else { return nil }
                        activeCommand = cmd == 109 ? 108 : 76
                    case 76, 108: guard consumeLines(cmd) else { return nil }           // L l
                    case 72, 104: guard consumeHorizontalLines(cmd) else { return nil } // H h
                    case 86, 118: guard consumeVerticalLines(cmd) else { return nil }   // V v
                    case 67, 99:  guard consumeCubicCurves(cmd) else { return nil }     // C c
                    case 83, 115: guard consumeSmoothCubic(cmd) else { return nil }     // S s
                    case 81, 113: guard consumeQuadraticCurves(cmd) else { return nil } // Q q
                    case 84, 116: guard consumeSmoothQuadratic(cmd) else { return nil } // T t
                    case 90, 122:                                                        // Z z
                        guard consumeClosePath() else { return nil }
                        activeCommand = nil
                    default: return nil
                    }
                }
                return path.copy()
            }

            private var isAtEnd: Bool { index >= tokens.count }
            private var nextIsNumber: Bool {
                guard index < tokens.count, case .number = tokens[index] else { return false }
                return true
            }

            private mutating func readCommand() -> UInt8? {
                guard index < tokens.count, case .command(let c) = tokens[index] else { return nil }
                index += 1; return c
            }
            private mutating func readNumber() -> CGFloat? {
                guard index < tokens.count, case .number(let n) = tokens[index] else { return nil }
                index += 1; return n
            }

            private mutating func consumeMove(_ cmd: UInt8) -> Bool {
                guard let x = readNumber(), let y = readNumber() else { return false }
                var pt = resolved(x: x, y: y, relative: cmd == 109)
                path.move(to: pt); currentPoint = pt; subpathStart = pt
                hasCurrentPoint = true; resetControls()
                while nextIsNumber {
                    guard let x = readNumber(), let y = readNumber() else { return false }
                    pt = resolved(x: x, y: y, relative: cmd == 109)
                    path.addLine(to: pt); currentPoint = pt
                }
                return true
            }

            private mutating func consumeLines(_ cmd: UInt8) -> Bool {
                guard hasCurrentPoint else { return false }
                var consumed = false
                while nextIsNumber {
                    guard let x = readNumber(), let y = readNumber() else { return false }
                    let pt = resolved(x: x, y: y, relative: cmd == 108)
                    path.addLine(to: pt); currentPoint = pt; resetControls(); consumed = true
                }
                return consumed
            }

            private mutating func consumeHorizontalLines(_ cmd: UInt8) -> Bool {
                guard hasCurrentPoint else { return false }
                var consumed = false
                while nextIsNumber {
                    guard let x = readNumber() else { return false }
                    let rx = cmd == 104 ? currentPoint.x + x : x
                    currentPoint = CGPoint(x: rx, y: currentPoint.y)
                    path.addLine(to: currentPoint); resetControls(); consumed = true
                }
                return consumed
            }

            private mutating func consumeVerticalLines(_ cmd: UInt8) -> Bool {
                guard hasCurrentPoint else { return false }
                var consumed = false
                while nextIsNumber {
                    guard let y = readNumber() else { return false }
                    let ry = cmd == 118 ? currentPoint.y + y : y
                    currentPoint = CGPoint(x: currentPoint.x, y: ry)
                    path.addLine(to: currentPoint); resetControls(); consumed = true
                }
                return consumed
            }

            private mutating func consumeCubicCurves(_ cmd: UInt8) -> Bool {
                guard hasCurrentPoint else { return false }
                var consumed = false
                while nextIsNumber {
                    guard
                        let x1 = readNumber(), let y1 = readNumber(),
                        let x2 = readNumber(), let y2 = readNumber(),
                        let x  = readNumber(), let y  = readNumber()
                    else { return false }
                    let c1 = resolved(x: x1, y: y1, relative: cmd == 99)
                    let c2 = resolved(x: x2, y: y2, relative: cmd == 99)
                    let pt = resolved(x: x,  y: y,  relative: cmd == 99)
                    path.addCurve(to: pt, control1: c1, control2: c2)
                    currentPoint = pt; lastCubicControl = c2; lastQuadraticControl = nil
                    consumed = true
                }
                return consumed
            }

            private mutating func consumeSmoothCubic(_ cmd: UInt8) -> Bool {
                guard hasCurrentPoint else { return false }
                var consumed = false
                while nextIsNumber {
                    guard
                        let x2 = readNumber(), let y2 = readNumber(),
                        let x  = readNumber(), let y  = readNumber()
                    else { return false }
                    let c1 = reflected(lastCubicControl)
                    let c2 = resolved(x: x2, y: y2, relative: cmd == 115)
                    let pt = resolved(x: x,  y: y,  relative: cmd == 115)
                    path.addCurve(to: pt, control1: c1, control2: c2)
                    currentPoint = pt; lastCubicControl = c2; lastQuadraticControl = nil
                    consumed = true
                }
                return consumed
            }

            private mutating func consumeQuadraticCurves(_ cmd: UInt8) -> Bool {
                guard hasCurrentPoint else { return false }
                var consumed = false
                while nextIsNumber {
                    guard
                        let x1 = readNumber(), let y1 = readNumber(),
                        let x  = readNumber(), let y  = readNumber()
                    else { return false }
                    let ctrl = resolved(x: x1, y: y1, relative: cmd == 113)
                    let pt   = resolved(x: x,  y: y,  relative: cmd == 113)
                    path.addQuadCurve(to: pt, control: ctrl)
                    currentPoint = pt; lastCubicControl = nil; lastQuadraticControl = ctrl
                    consumed = true
                }
                return consumed
            }

            private mutating func consumeSmoothQuadratic(_ cmd: UInt8) -> Bool {
                guard hasCurrentPoint else { return false }
                var consumed = false
                while nextIsNumber {
                    guard let x = readNumber(), let y = readNumber() else { return false }
                    let ctrl = reflected(lastQuadraticControl)
                    let pt   = resolved(x: x, y: y, relative: cmd == 116)
                    path.addQuadCurve(to: pt, control: ctrl)
                    currentPoint = pt; lastCubicControl = nil; lastQuadraticControl = ctrl
                    consumed = true
                }
                return consumed
            }

            private mutating func consumeClosePath() -> Bool {
                guard hasCurrentPoint else { return false }
                path.closeSubpath(); currentPoint = subpathStart; resetControls(); return true
            }

            private func resolved(x: CGFloat, y: CGFloat, relative: Bool) -> CGPoint {
                relative ? CGPoint(x: currentPoint.x + x, y: currentPoint.y + y) : CGPoint(x: x, y: y)
            }
            private func reflected(_ pt: CGPoint?) -> CGPoint {
                guard let pt else { return currentPoint }
                return CGPoint(x: currentPoint.x * 2 - pt.x, y: currentPoint.y * 2 - pt.y)
            }
            private mutating func resetControls() {
                lastCubicControl = nil; lastQuadraticControl = nil
            }

            private static func isSupported(_ cmd: UInt8) -> Bool {
                switch cmd {
                case 67, 72, 76, 77, 81, 83, 84, 86, 90,
                     99, 104, 108, 109, 113, 115, 116, 118, 122: return true
                default: return false
                }
            }
        }
    }
#endif
