#if canImport(UIKit) || canImport(AppKit)
    import CoreGraphics

    enum SVGPathParser {
        private static let maximumPathDataByteCount = 64_000
        private static let maximumTokenCount = 16_384

        static func parse(_ d: String) -> CGPath? {
            // Invisible glyphs such as U+2061 FUNCTION APPLICATION (inserted by
            // MathJax after \sin, \log, \lim, ...) render as `<path d="">`. Treat
            // an empty path as a valid no-op rather than a rasterization failure.
            if d.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return CGMutablePath()
            }
            guard d.utf8.count <= maximumPathDataByteCount,
                let tokens = Tokenizer(d).tokens(maximumCount: maximumTokenCount),
                !tokens.isEmpty
            else {
                return nil
            }

            var parser = Parser(tokens: tokens)
            return parser.parse()
        }
    }

    extension SVGPathParser {
        fileprivate enum Token {
            case command(UInt8)
            case number(CGFloat)
        }

        fileprivate struct Tokenizer {
            private enum LastToken {
                case command
                case number
                case comma
            }

            let bytes: [UInt8]

            init(_ string: String) {
                bytes = Array(string.utf8)
            }

            func tokens(maximumCount: Int) -> [Token]? {
                var tokens: [Token] = []
                var index = 0
                var lastToken: LastToken?
                var sawSeparator = false

                while index < bytes.count {
                    let byte = bytes[index]

                    if Self.isWhitespace(byte) {
                        sawSeparator = true
                        index += 1
                    } else if byte == 44 {
                        guard lastToken == .number else {
                            return nil
                        }

                        lastToken = .comma
                        sawSeparator = true
                        index += 1
                    } else if Self.isCommand(byte) {
                        guard lastToken != .comma else {
                            return nil
                        }

                        guard append(.command(byte), to: &tokens, maximumCount: maximumCount) else {
                            return nil
                        }
                        lastToken = .command
                        sawSeparator = false
                        index += 1
                    } else if Self.isNumberStart(byte) {
                        if lastToken == .number, !sawSeparator, !Self.isSign(byte) {
                            return nil
                        }

                        guard let number = number(at: index) else {
                            return nil
                        }

                        guard
                            append(
                                .number(CGFloat(number.value)), to: &tokens,
                                maximumCount: maximumCount)
                        else {
                            return nil
                        }
                        lastToken = .number
                        sawSeparator = false
                        index = number.nextIndex
                    } else {
                        return nil
                    }
                }

                guard lastToken != .comma else {
                    return nil
                }

                return tokens
            }

            private func append(_ token: Token, to tokens: inout [Token], maximumCount: Int) -> Bool
            {
                guard tokens.count < maximumCount else {
                    return false
                }
                tokens.append(token)
                return true
            }

            private func number(at startIndex: Int) -> (value: Double, nextIndex: Int)? {
                var index = startIndex

                if index < bytes.count, Self.isSign(bytes[index]) {
                    index += 1
                }

                var wholeDigits = 0
                while index < bytes.count, Self.isDigit(bytes[index]) {
                    wholeDigits += 1
                    index += 1
                }

                var fractionDigits = 0
                if index < bytes.count, bytes[index] == 46 {
                    index += 1

                    while index < bytes.count, Self.isDigit(bytes[index]) {
                        fractionDigits += 1
                        index += 1
                    }
                }

                guard wholeDigits > 0 || fractionDigits > 0 else {
                    return nil
                }

                if index < bytes.count, bytes[index] == 69 || bytes[index] == 101 {
                    index += 1

                    if index < bytes.count, Self.isSign(bytes[index]) {
                        index += 1
                    }

                    var exponentDigits = 0
                    while index < bytes.count, Self.isDigit(bytes[index]) {
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

            private static func isWhitespace(_ byte: UInt8) -> Bool {
                byte == 32 || byte == 9 || byte == 10 || byte == 12 || byte == 13
            }

            private static func isCommand(_ byte: UInt8) -> Bool {
                (byte >= 65 && byte <= 90) || (byte >= 97 && byte <= 122)
            }

            private static func isNumberStart(_ byte: UInt8) -> Bool {
                isSign(byte) || byte == 46 || isDigit(byte)
            }

            private static func isSign(_ byte: UInt8) -> Bool {
                byte == 43 || byte == 45
            }

            private static func isDigit(_ byte: UInt8) -> Bool {
                byte >= 48 && byte <= 57
            }
        }

        fileprivate struct Parser {
            let tokens: [Token]
            let path = CGMutablePath()
            var index = 0
            var currentPoint = CGPoint.zero
            var subpathStart = CGPoint.zero
            var hasCurrentPoint = false
            var lastCubicControlPoint: CGPoint?
            var lastQuadraticControlPoint: CGPoint?

            mutating func parse() -> CGPath? {
                var activeCommand: UInt8?

                while !isAtEnd {
                    if let command = readCommand() {
                        guard Self.isSupported(command) else {
                            return nil
                        }

                        activeCommand = command
                    } else if activeCommand == nil {
                        return nil
                    }

                    guard let command = activeCommand else {
                        return nil
                    }

                    switch command {
                    case 77, 109:
                        guard consumeMove(command) else {
                            return nil
                        }

                        activeCommand = command == 109 ? 108 : 76
                    case 76, 108:
                        guard consumeLines(command) else {
                            return nil
                        }
                    case 72, 104:
                        guard consumeHorizontalLines(command) else {
                            return nil
                        }
                    case 86, 118:
                        guard consumeVerticalLines(command) else {
                            return nil
                        }
                    case 67, 99:
                        guard consumeCubicCurves(command) else {
                            return nil
                        }
                    case 83, 115:
                        guard consumeSmoothCubicCurves(command) else {
                            return nil
                        }
                    case 81, 113:
                        guard consumeQuadraticCurves(command) else {
                            return nil
                        }
                    case 84, 116:
                        guard consumeSmoothQuadraticCurves(command) else {
                            return nil
                        }
                    case 90, 122:
                        guard consumeClosePath() else {
                            return nil
                        }

                        activeCommand = nil
                    default:
                        return nil
                    }
                }

                return path.copy()
            }

            private var isAtEnd: Bool {
                index >= tokens.count
            }

            private var nextIsNumber: Bool {
                guard index < tokens.count else {
                    return false
                }

                if case .number = tokens[index] {
                    return true
                }

                return false
            }

            private mutating func readCommand() -> UInt8? {
                guard index < tokens.count, case .command(let command) = tokens[index] else {
                    return nil
                }

                index += 1
                return command
            }

            private mutating func readNumber() -> CGFloat? {
                guard index < tokens.count, case .number(let number) = tokens[index] else {
                    return nil
                }

                index += 1
                return number
            }

            private mutating func consumeMove(_ command: UInt8) -> Bool {
                guard let x = readNumber(), let y = readNumber() else {
                    return false
                }

                var point = resolvedPoint(x: x, y: y, relative: command == 109)
                path.move(to: point)
                currentPoint = point
                subpathStart = point
                hasCurrentPoint = true
                resetCurveControls()

                while nextIsNumber {
                    guard let x = readNumber(), let y = readNumber() else {
                        return false
                    }

                    point = resolvedPoint(x: x, y: y, relative: command == 109)
                    path.addLine(to: point)
                    currentPoint = point
                }

                return true
            }

            private mutating func consumeLines(_ command: UInt8) -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                var consumed = false
                while nextIsNumber {
                    guard let x = readNumber(), let y = readNumber() else {
                        return false
                    }

                    let point = resolvedPoint(x: x, y: y, relative: command == 108)
                    path.addLine(to: point)
                    currentPoint = point
                    resetCurveControls()
                    consumed = true
                }

                return consumed
            }

            private mutating func consumeHorizontalLines(_ command: UInt8) -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                var consumed = false
                while nextIsNumber {
                    guard let x = readNumber() else {
                        return false
                    }

                    let resolvedX = command == 104 ? currentPoint.x + x : x
                    currentPoint = CGPoint(x: resolvedX, y: currentPoint.y)
                    path.addLine(to: currentPoint)
                    resetCurveControls()
                    consumed = true
                }

                return consumed
            }

            private mutating func consumeVerticalLines(_ command: UInt8) -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                var consumed = false
                while nextIsNumber {
                    guard let y = readNumber() else {
                        return false
                    }

                    let resolvedY = command == 118 ? currentPoint.y + y : y
                    currentPoint = CGPoint(x: currentPoint.x, y: resolvedY)
                    path.addLine(to: currentPoint)
                    resetCurveControls()
                    consumed = true
                }

                return consumed
            }

            private mutating func consumeCubicCurves(_ command: UInt8) -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                var consumed = false
                while nextIsNumber {
                    guard
                        let x1 = readNumber(),
                        let y1 = readNumber(),
                        let x2 = readNumber(),
                        let y2 = readNumber(),
                        let x = readNumber(),
                        let y = readNumber()
                    else {
                        return false
                    }

                    let control1 = resolvedPoint(x: x1, y: y1, relative: command == 99)
                    let control2 = resolvedPoint(x: x2, y: y2, relative: command == 99)
                    let point = resolvedPoint(x: x, y: y, relative: command == 99)
                    path.addCurve(to: point, control1: control1, control2: control2)
                    currentPoint = point
                    lastCubicControlPoint = control2
                    lastQuadraticControlPoint = nil
                    consumed = true
                }

                return consumed
            }

            private mutating func consumeSmoothCubicCurves(_ command: UInt8) -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                var consumed = false
                while nextIsNumber {
                    guard
                        let x2 = readNumber(),
                        let y2 = readNumber(),
                        let x = readNumber(),
                        let y = readNumber()
                    else {
                        return false
                    }

                    let control1 = reflectedPoint(lastCubicControlPoint)
                    let control2 = resolvedPoint(x: x2, y: y2, relative: command == 115)
                    let point = resolvedPoint(x: x, y: y, relative: command == 115)
                    path.addCurve(to: point, control1: control1, control2: control2)
                    currentPoint = point
                    lastCubicControlPoint = control2
                    lastQuadraticControlPoint = nil
                    consumed = true
                }

                return consumed
            }

            private mutating func consumeQuadraticCurves(_ command: UInt8) -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                var consumed = false
                while nextIsNumber {
                    guard
                        let x1 = readNumber(),
                        let y1 = readNumber(),
                        let x = readNumber(),
                        let y = readNumber()
                    else {
                        return false
                    }

                    let control = resolvedPoint(x: x1, y: y1, relative: command == 113)
                    let point = resolvedPoint(x: x, y: y, relative: command == 113)
                    path.addQuadCurve(to: point, control: control)
                    currentPoint = point
                    lastCubicControlPoint = nil
                    lastQuadraticControlPoint = control
                    consumed = true
                }

                return consumed
            }

            private mutating func consumeSmoothQuadraticCurves(_ command: UInt8) -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                var consumed = false
                while nextIsNumber {
                    guard let x = readNumber(), let y = readNumber() else {
                        return false
                    }

                    let control = reflectedPoint(lastQuadraticControlPoint)
                    let point = resolvedPoint(x: x, y: y, relative: command == 116)
                    path.addQuadCurve(to: point, control: control)
                    currentPoint = point
                    lastCubicControlPoint = nil
                    lastQuadraticControlPoint = control
                    consumed = true
                }

                return consumed
            }

            private mutating func consumeClosePath() -> Bool {
                guard hasCurrentPoint else {
                    return false
                }

                path.closeSubpath()
                currentPoint = subpathStart
                resetCurveControls()
                return true
            }

            private func resolvedPoint(x: CGFloat, y: CGFloat, relative: Bool) -> CGPoint {
                if relative {
                    return CGPoint(x: currentPoint.x + x, y: currentPoint.y + y)
                }

                return CGPoint(x: x, y: y)
            }

            private func reflectedPoint(_ point: CGPoint?) -> CGPoint {
                guard let point else {
                    return currentPoint
                }

                return CGPoint(
                    x: currentPoint.x + (currentPoint.x - point.x),
                    y: currentPoint.y + (currentPoint.y - point.y)
                )
            }

            private mutating func resetCurveControls() {
                lastCubicControlPoint = nil
                lastQuadraticControlPoint = nil
            }

            private static func isSupported(_ command: UInt8) -> Bool {
                switch command {
                case 67, 72, 76, 77, 81, 83, 84, 86, 90, 99, 104, 108, 109, 113, 115, 116, 118, 122:
                    return true
                default:
                    return false
                }
            }
        }
    }
#endif
