package io.github.dongyuzhao.mathrenderer

import android.graphics.Path
import android.graphics.PointF

/**
 * Parses an SVG path `d` attribute string into an Android [Path].
 * Supports: M m L l H h V v C c S s Q q T t Z z
 */
internal object SVGPathParser {
    private const val MAX_BYTE_COUNT = 64_000
    private const val MAX_TOKEN_COUNT = 16_384

    fun parse(d: String): Path? {
        if (d.toByteArray(Charsets.UTF_8).size > MAX_BYTE_COUNT) return null
        val tokens = tokenize(d) ?: return null
        if (tokens.isEmpty()) return null
        return buildPath(tokens)
    }

    // ── Tokeniser ─────────────────────────────────────────────────────────────

    private sealed class Token {
        data class Command(val c: Char) : Token()
        data class Number(val v: Float) : Token()
    }

    private fun tokenize(input: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        var lastWasNumber = false
        var lastWasComma = false

        while (i < input.length) {
            val ch = input[i]
            when {
                ch.isWhitespace() -> { lastWasComma = false; i++ }
                ch == ',' -> {
                    if (!lastWasNumber) return null
                    lastWasComma = true; lastWasNumber = false; i++
                }
                ch.isLetter() -> {
                    if (lastWasComma) return null
                    tokens.add(Token.Command(ch))
                    if (tokens.size > MAX_TOKEN_COUNT) return null
                    lastWasNumber = false; lastWasComma = false; i++
                }
                ch == '-' || ch == '+' || ch == '.' || ch.isDigit() -> {
                    if (lastWasNumber && ch != '-' && ch != '+') {
                        // implicit separator only allowed before sign
                    }
                    val (value, next) = readNumber(input, i) ?: return null
                    tokens.add(Token.Number(value))
                    if (tokens.size > MAX_TOKEN_COUNT) return null
                    lastWasNumber = true; lastWasComma = false; i = next
                }
                else -> return null
            }
        }
        if (lastWasComma) return null
        return tokens
    }

    private fun readNumber(s: String, start: Int): Pair<Float, Int>? {
        var i = start
        if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
        var whole = 0
        while (i < s.length && s[i].isDigit()) { whole++; i++ }
        var frac = 0
        if (i < s.length && s[i] == '.') {
            i++
            while (i < s.length && s[i].isDigit()) { frac++; i++ }
        }
        if (whole == 0 && frac == 0) return null
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            i++
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            var exp = 0
            while (i < s.length && s[i].isDigit()) { exp++; i++ }
            if (exp == 0) return null
        }
        val text = s.substring(start, i)
        val v = text.toFloatOrNull() ?: return null
        return Pair(v, i)
    }

    // ── Path builder ──────────────────────────────────────────────────────────

    private fun buildPath(tokens: List<Token>): Path? {
        val path = Path()
        var idx = 0
        var current = PointF(0f, 0f)
        var subpathStart = PointF(0f, 0f)
        var hasPoint = false
        var lastCubicCtrl: PointF? = null
        var lastQuadCtrl: PointF? = null
        var activeCmd: Char? = null

        fun next(): Float? {
            if (idx >= tokens.size) return null
            val t = tokens[idx]
            if (t is Token.Number) { idx++; return t.v }
            return null
        }
        fun hasNextNumber() = idx < tokens.size && tokens[idx] is Token.Number
        fun resolved(x: Float, y: Float, rel: Boolean) =
            if (rel) PointF(current.x + x, current.y + y) else PointF(x, y)
        fun reflect(ctrl: PointF?) =
            if (ctrl == null) current else PointF(2 * current.x - ctrl.x, 2 * current.y - ctrl.y)
        fun resetCtrl() { lastCubicCtrl = null; lastQuadCtrl = null }

        while (idx < tokens.size) {
            val token = tokens[idx]
            if (token is Token.Command) {
                val c = token.c
                if ("MmLlHhVvCcSsQqTtZz".indexOf(c) < 0) return null
                activeCmd = c
                idx++
            } else if (activeCmd == null) {
                return null
            }

            when (val cmd = activeCmd!!) {
                'M', 'm' -> {
                    val x = next() ?: return null
                    val y = next() ?: return null
                    val pt = resolved(x, y, cmd == 'm')
                    path.moveTo(pt.x, pt.y); current = pt; subpathStart = pt; hasPoint = true; resetCtrl()
                    activeCmd = if (cmd == 'm') 'l' else 'L'
                    while (hasNextNumber()) {
                        val lx = next() ?: return null; val ly = next() ?: return null
                        val lpt = resolved(lx, ly, activeCmd == 'l')
                        path.lineTo(lpt.x, lpt.y); current = lpt; resetCtrl()
                    }
                }
                'L', 'l' -> {
                    if (!hasPoint) return null
                    var consumed = false
                    while (hasNextNumber()) {
                        val x = next() ?: return null; val y = next() ?: return null
                        val pt = resolved(x, y, cmd == 'l')
                        path.lineTo(pt.x, pt.y); current = pt; resetCtrl(); consumed = true
                    }
                    if (!consumed) return null
                }
                'H', 'h' -> {
                    if (!hasPoint) return null
                    var consumed = false
                    while (hasNextNumber()) {
                        val x = next() ?: return null
                        val rx = if (cmd == 'h') current.x + x else x
                        current = PointF(rx, current.y); path.lineTo(current.x, current.y); resetCtrl(); consumed = true
                    }
                    if (!consumed) return null
                }
                'V', 'v' -> {
                    if (!hasPoint) return null
                    var consumed = false
                    while (hasNextNumber()) {
                        val y = next() ?: return null
                        val ry = if (cmd == 'v') current.y + y else y
                        current = PointF(current.x, ry); path.lineTo(current.x, current.y); resetCtrl(); consumed = true
                    }
                    if (!consumed) return null
                }
                'C', 'c' -> {
                    if (!hasPoint) return null
                    var consumed = false
                    while (hasNextNumber()) {
                        val x1=next()?:return null; val y1=next()?:return null
                        val x2=next()?:return null; val y2=next()?:return null
                        val x =next()?:return null; val y =next()?:return null
                        val c1=resolved(x1,y1,cmd=='c'); val c2=resolved(x2,y2,cmd=='c'); val pt=resolved(x,y,cmd=='c')
                        path.cubicTo(c1.x,c1.y,c2.x,c2.y,pt.x,pt.y)
                        current=pt; lastCubicCtrl=c2; lastQuadCtrl=null; consumed=true
                    }
                    if (!consumed) return null
                }
                'S', 's' -> {
                    if (!hasPoint) return null
                    var consumed = false
                    while (hasNextNumber()) {
                        val x2=next()?:return null; val y2=next()?:return null
                        val x =next()?:return null; val y =next()?:return null
                        val c1=reflect(lastCubicCtrl); val c2=resolved(x2,y2,cmd=='s'); val pt=resolved(x,y,cmd=='s')
                        path.cubicTo(c1.x,c1.y,c2.x,c2.y,pt.x,pt.y)
                        current=pt; lastCubicCtrl=c2; lastQuadCtrl=null; consumed=true
                    }
                    if (!consumed) return null
                }
                'Q', 'q' -> {
                    if (!hasPoint) return null
                    var consumed = false
                    while (hasNextNumber()) {
                        val x1=next()?:return null; val y1=next()?:return null
                        val x =next()?:return null; val y =next()?:return null
                        val ctrl=resolved(x1,y1,cmd=='q'); val pt=resolved(x,y,cmd=='q')
                        path.quadTo(ctrl.x,ctrl.y,pt.x,pt.y)
                        current=pt; lastCubicCtrl=null; lastQuadCtrl=ctrl; consumed=true
                    }
                    if (!consumed) return null
                }
                'T', 't' -> {
                    if (!hasPoint) return null
                    var consumed = false
                    while (hasNextNumber()) {
                        val x=next()?:return null; val y=next()?:return null
                        val ctrl=reflect(lastQuadCtrl); val pt=resolved(x,y,cmd=='t')
                        path.quadTo(ctrl.x,ctrl.y,pt.x,pt.y)
                        current=pt; lastCubicCtrl=null; lastQuadCtrl=ctrl; consumed=true
                    }
                    if (!consumed) return null
                }
                'Z', 'z' -> {
                    if (!hasPoint) return null
                    path.close(); current=subpathStart; resetCtrl(); activeCmd=null
                }
                else -> return null
            }
        }
        return path
    }
}
