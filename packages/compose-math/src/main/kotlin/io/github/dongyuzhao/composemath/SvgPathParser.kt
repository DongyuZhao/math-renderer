package io.github.dongyuzhao.composemath

/**
 * Parses the `d` attribute of an SVG `<path>` into a resolution-independent [MathSvgPath].
 *
 * This is a straight port of the iOS `SVGPathParser`: it supports the full absolute/relative
 * command set MathJax emits, resolves smooth-curve control-point reflection, and rejects any
 * malformed or oversized input rather than silently producing a partial path.
 */
object SvgPathParser {
    private const val MAX_PATH_DATA_BYTE_COUNT = 64_000
    private const val MAX_TOKEN_COUNT = 16_384

    fun parse(d: String): MathSvgPath? {
        if (d.toByteArray(Charsets.UTF_8).size > MAX_PATH_DATA_BYTE_COUNT) {
            return null
        }

        // Invisible glyphs such as U+2061 FUNCTION APPLICATION (inserted by
        // MathJax after \sin, \log, \lim, ...) render as `<path d="">`. Treat an
        // empty path as a valid no-op rather than rejecting the whole document.
        if (d.isBlank()) {
            return MathSvgPath(emptyList())
        }

        val tokens = Tokenizer(d).tokens(MAX_TOKEN_COUNT) ?: return null
        if (tokens.isEmpty()) {
            return null
        }

        return Parser(tokens).parse()
    }

    private sealed interface Token {
        data class Command(val byte: Int) : Token
        data class Number(val value: Float) : Token
    }

    private class Tokenizer(string: String) {
        private enum class LastToken { COMMAND, NUMBER, COMMA }

        private val bytes: ByteArray = string.toByteArray(Charsets.UTF_8)

        fun tokens(maximumCount: Int): List<Token>? {
            val tokens = ArrayList<Token>()
            var index = 0
            var lastToken: LastToken? = null
            var sawSeparator = false

            while (index < bytes.size) {
                val byte = bytes[index].toInt() and 0xFF

                when {
                    isWhitespace(byte) -> {
                        sawSeparator = true
                        index += 1
                    }

                    byte == 44 -> { // ','
                        if (lastToken != LastToken.NUMBER) {
                            return null
                        }
                        lastToken = LastToken.COMMA
                        sawSeparator = true
                        index += 1
                    }

                    isCommand(byte) -> {
                        if (lastToken == LastToken.COMMA) {
                            return null
                        }
                        if (!append(Token.Command(byte), tokens, maximumCount)) {
                            return null
                        }
                        lastToken = LastToken.COMMAND
                        sawSeparator = false
                        index += 1
                    }

                    isNumberStart(byte) -> {
                        if (lastToken == LastToken.NUMBER && !sawSeparator && !isSign(byte)) {
                            return null
                        }
                        val number = number(index) ?: return null
                        if (!append(Token.Number(number.value.toFloat()), tokens, maximumCount)) {
                            return null
                        }
                        lastToken = LastToken.NUMBER
                        sawSeparator = false
                        index = number.nextIndex
                    }

                    else -> return null
                }
            }

            if (lastToken == LastToken.COMMA) {
                return null
            }

            return tokens
        }

        private fun append(token: Token, tokens: ArrayList<Token>, maximumCount: Int): Boolean {
            if (tokens.size >= maximumCount) {
                return false
            }
            tokens.add(token)
            return true
        }

        private fun number(startIndex: Int): NumberResult? {
            var index = startIndex

            if (index < bytes.size && isSign(byteAt(index))) {
                index += 1
            }

            var wholeDigits = 0
            while (index < bytes.size && isDigit(byteAt(index))) {
                wholeDigits += 1
                index += 1
            }

            var fractionDigits = 0
            if (index < bytes.size && byteAt(index) == 46) { // '.'
                index += 1
                while (index < bytes.size && isDigit(byteAt(index))) {
                    fractionDigits += 1
                    index += 1
                }
            }

            if (wholeDigits == 0 && fractionDigits == 0) {
                return null
            }

            if (index < bytes.size && (byteAt(index) == 69 || byteAt(index) == 101)) { // 'E' / 'e'
                index += 1
                if (index < bytes.size && isSign(byteAt(index))) {
                    index += 1
                }
                var exponentDigits = 0
                while (index < bytes.size && isDigit(byteAt(index))) {
                    exponentDigits += 1
                    index += 1
                }
                if (exponentDigits == 0) {
                    return null
                }
            }

            val text = String(bytes, startIndex, index - startIndex, Charsets.UTF_8)
            val value = text.toDoubleOrNull() ?: return null
            if (!value.isFinite()) {
                return null
            }

            return NumberResult(value, index)
        }

        private fun byteAt(index: Int): Int = bytes[index].toInt() and 0xFF

        private data class NumberResult(val value: Double, val nextIndex: Int)

        private fun isWhitespace(byte: Int): Boolean = byte == 32 || byte == 9 || byte == 10 || byte == 12 || byte == 13

        private fun isCommand(byte: Int): Boolean = (byte in 65..90) || (byte in 97..122)

        private fun isNumberStart(byte: Int): Boolean = isSign(byte) || byte == 46 || isDigit(byte)

        private fun isSign(byte: Int): Boolean = byte == 43 || byte == 45

        private fun isDigit(byte: Int): Boolean = byte in 48..57
    }

    private class Parser(private val tokens: List<Token>) {
        private val segments = ArrayList<MathSvgPathSegment>()
        private var index = 0
        private var currentX = 0f
        private var currentY = 0f
        private var subpathStartX = 0f
        private var subpathStartY = 0f
        private var hasCurrentPoint = false
        private var lastCubicControlX: Float? = null
        private var lastCubicControlY: Float? = null
        private var lastQuadraticControlX: Float? = null
        private var lastQuadraticControlY: Float? = null

        fun parse(): MathSvgPath? {
            var activeCommand: Int? = null

            while (!isAtEnd()) {
                val read = readCommand()
                if (read != null) {
                    if (!isSupported(read)) {
                        return null
                    }
                    activeCommand = read
                } else if (activeCommand == null) {
                    return null
                }

                val command = activeCommand

                when (command) {
                    77, 109 -> { // M / m
                        if (!consumeMove(command)) return null
                        activeCommand = if (command == 109) 108 else 76
                    }
                    76, 108 -> if (!consumeLines(command)) return null // L / l
                    72, 104 -> if (!consumeHorizontalLines(command)) return null // H / h
                    86, 118 -> if (!consumeVerticalLines(command)) return null // V / v
                    67, 99 -> if (!consumeCubicCurves(command)) return null // C / c
                    83, 115 -> if (!consumeSmoothCubicCurves(command)) return null // S / s
                    81, 113 -> if (!consumeQuadraticCurves(command)) return null // Q / q
                    84, 116 -> if (!consumeSmoothQuadraticCurves(command)) return null // T / t
                    90, 122 -> { // Z / z
                        if (!consumeClosePath()) return null
                        activeCommand = null
                    }
                    else -> return null
                }
            }

            return MathSvgPath(segments)
        }

        private fun isAtEnd(): Boolean = index >= tokens.size

        private fun nextIsNumber(): Boolean {
            if (index >= tokens.size) {
                return false
            }
            return tokens[index] is Token.Number
        }

        private fun readCommand(): Int? {
            if (index >= tokens.size) {
                return null
            }
            val token = tokens[index]
            if (token !is Token.Command) {
                return null
            }
            index += 1
            return token.byte
        }

        private fun readNumber(): Float? {
            if (index >= tokens.size) {
                return null
            }
            val token = tokens[index]
            if (token !is Token.Number) {
                return null
            }
            index += 1
            return token.value
        }

        private fun consumeMove(command: Int): Boolean {
            val x = readNumber() ?: return false
            val y = readNumber() ?: return false
            val relative = command == 109
            resolvePoint(x, y, relative)
            segments.add(MathSvgPathSegment.MoveTo(currentX, currentY))
            subpathStartX = currentX
            subpathStartY = currentY
            hasCurrentPoint = true
            resetCurveControls()

            while (nextIsNumber()) {
                val nextX = readNumber() ?: return false
                val nextY = readNumber() ?: return false
                resolvePoint(nextX, nextY, relative)
                segments.add(MathSvgPathSegment.LineTo(currentX, currentY))
            }

            return true
        }

        private fun consumeLines(command: Int): Boolean {
            if (!hasCurrentPoint) return false
            val relative = command == 108
            var consumed = false
            while (nextIsNumber()) {
                val x = readNumber() ?: return false
                val y = readNumber() ?: return false
                resolvePoint(x, y, relative)
                segments.add(MathSvgPathSegment.LineTo(currentX, currentY))
                resetCurveControls()
                consumed = true
            }
            return consumed
        }

        private fun consumeHorizontalLines(command: Int): Boolean {
            if (!hasCurrentPoint) return false
            val relative = command == 104
            var consumed = false
            while (nextIsNumber()) {
                val x = readNumber() ?: return false
                currentX = if (relative) currentX + x else x
                segments.add(MathSvgPathSegment.LineTo(currentX, currentY))
                resetCurveControls()
                consumed = true
            }
            return consumed
        }

        private fun consumeVerticalLines(command: Int): Boolean {
            if (!hasCurrentPoint) return false
            val relative = command == 118
            var consumed = false
            while (nextIsNumber()) {
                val y = readNumber() ?: return false
                currentY = if (relative) currentY + y else y
                segments.add(MathSvgPathSegment.LineTo(currentX, currentY))
                resetCurveControls()
                consumed = true
            }
            return consumed
        }

        private fun consumeCubicCurves(command: Int): Boolean {
            if (!hasCurrentPoint) return false
            val relative = command == 99
            var consumed = false
            while (nextIsNumber()) {
                val x1 = readNumber() ?: return false
                val y1 = readNumber() ?: return false
                val x2 = readNumber() ?: return false
                val y2 = readNumber() ?: return false
                val x = readNumber() ?: return false
                val y = readNumber() ?: return false

                val control1 = resolvedPoint(x1, y1, relative)
                val control2 = resolvedPoint(x2, y2, relative)
                resolvePoint(x, y, relative)
                segments.add(
                    MathSvgPathSegment.CubicTo(
                        control1.first,
                        control1.second,
                        control2.first,
                        control2.second,
                        currentX,
                        currentY,
                    ),
                )
                lastCubicControlX = control2.first
                lastCubicControlY = control2.second
                lastQuadraticControlX = null
                lastQuadraticControlY = null
                consumed = true
            }
            return consumed
        }

        private fun consumeSmoothCubicCurves(command: Int): Boolean {
            if (!hasCurrentPoint) return false
            val relative = command == 115
            var consumed = false
            while (nextIsNumber()) {
                val x2 = readNumber() ?: return false
                val y2 = readNumber() ?: return false
                val x = readNumber() ?: return false
                val y = readNumber() ?: return false

                val control1 = reflectedCubicControl()
                val control2 = resolvedPoint(x2, y2, relative)
                resolvePoint(x, y, relative)
                segments.add(
                    MathSvgPathSegment.CubicTo(
                        control1.first,
                        control1.second,
                        control2.first,
                        control2.second,
                        currentX,
                        currentY,
                    ),
                )
                lastCubicControlX = control2.first
                lastCubicControlY = control2.second
                lastQuadraticControlX = null
                lastQuadraticControlY = null
                consumed = true
            }
            return consumed
        }

        private fun consumeQuadraticCurves(command: Int): Boolean {
            if (!hasCurrentPoint) return false
            val relative = command == 113
            var consumed = false
            while (nextIsNumber()) {
                val x1 = readNumber() ?: return false
                val y1 = readNumber() ?: return false
                val x = readNumber() ?: return false
                val y = readNumber() ?: return false

                val control = resolvedPoint(x1, y1, relative)
                resolvePoint(x, y, relative)
                segments.add(
                    MathSvgPathSegment.QuadTo(control.first, control.second, currentX, currentY),
                )
                lastCubicControlX = null
                lastCubicControlY = null
                lastQuadraticControlX = control.first
                lastQuadraticControlY = control.second
                consumed = true
            }
            return consumed
        }

        private fun consumeSmoothQuadraticCurves(command: Int): Boolean {
            if (!hasCurrentPoint) return false
            val relative = command == 116
            var consumed = false
            while (nextIsNumber()) {
                val x = readNumber() ?: return false
                val y = readNumber() ?: return false

                val control = reflectedQuadraticControl()
                resolvePoint(x, y, relative)
                segments.add(
                    MathSvgPathSegment.QuadTo(control.first, control.second, currentX, currentY),
                )
                lastCubicControlX = null
                lastCubicControlY = null
                lastQuadraticControlX = control.first
                lastQuadraticControlY = control.second
                consumed = true
            }
            return consumed
        }

        private fun consumeClosePath(): Boolean {
            if (!hasCurrentPoint) return false
            segments.add(MathSvgPathSegment.Close)
            currentX = subpathStartX
            currentY = subpathStartY
            resetCurveControls()
            return true
        }

        private fun resolvePoint(x: Float, y: Float, relative: Boolean) {
            if (relative) {
                currentX += x
                currentY += y
            } else {
                currentX = x
                currentY = y
            }
        }

        private fun resolvedPoint(x: Float, y: Float, relative: Boolean): Pair<Float, Float> = if (relative) {
            Pair(currentX + x, currentY + y)
        } else {
            Pair(x, y)
        }

        private fun reflectedCubicControl(): Pair<Float, Float> {
            val controlX = lastCubicControlX
            val controlY = lastCubicControlY
            if (controlX == null || controlY == null) {
                return Pair(currentX, currentY)
            }
            return Pair(currentX + (currentX - controlX), currentY + (currentY - controlY))
        }

        private fun reflectedQuadraticControl(): Pair<Float, Float> {
            val controlX = lastQuadraticControlX
            val controlY = lastQuadraticControlY
            if (controlX == null || controlY == null) {
                return Pair(currentX, currentY)
            }
            return Pair(currentX + (currentX - controlX), currentY + (currentY - controlY))
        }

        private fun resetCurveControls() {
            lastCubicControlX = null
            lastCubicControlY = null
            lastQuadraticControlX = null
            lastQuadraticControlY = null
        }

        private fun isSupported(command: Int): Boolean = when (command) {
            67, 72, 76, 77, 81, 83, 84, 86, 90, 99, 104, 108, 109, 113, 115, 116, 118, 122 -> true
            else -> false
        }
    }
}
