package io.github.dongyuzhao.mathrenderer

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SVGPathParser].
 *
 * Robolectric is required because [android.graphics.Path] is an Android SDK class.
 * Run with: ./gradlew :mathrenderer:test
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SVGPathParserTest {

    // ── Valid path strings ────────────────────────────────────────────────────

    @Test
    fun `simple moveTo and lineTo returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 10 10 L 50 50"))
    }

    @Test
    fun `close subpath Z returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 L 100 0 L 100 100 Z"))
    }

    @Test
    fun `cubic bezier C returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 C 10 10 20 10 30 0"))
    }

    @Test
    fun `smooth cubic bezier S returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 C 10 10 20 10 30 0 S 50 -10 60 0"))
    }

    @Test
    fun `quadratic bezier Q returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 Q 15 30 30 0"))
    }

    @Test
    fun `smooth quadratic bezier T returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 Q 15 30 30 0 T 60 0"))
    }

    @Test
    fun `horizontal line H returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 H 100"))
    }

    @Test
    fun `vertical line V returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 V 100"))
    }

    @Test
    fun `relative moveTo lowercase m returns non-null path`() {
        assertNotNull(SVGPathParser.parse("M 10 10 m 5 5 l 20 20"))
    }

    @Test
    fun `negative coordinates are parsed correctly`() {
        assertNotNull(SVGPathParser.parse("M -10 -20 L -5 -5"))
    }

    @Test
    fun `comma-separated coordinates return non-null path`() {
        assertNotNull(SVGPathParser.parse("M 10,20 L 30,40 Z"))
    }

    @Test
    fun `multiple subpaths return non-null path`() {
        assertNotNull(SVGPathParser.parse("M 0 0 L 10 0 Z M 20 20 L 30 20 Z"))
    }

    @Test
    fun `real-world MathJax-like path returns non-null`() {
        // Simplified version of a typical MathJax-generated path
        val d = "M 234 464 C 234 473 226 478 218 478 C 205 478 200 469 200 461"
        assertNotNull(SVGPathParser.parse(d))
    }

    // ── Invalid / edge-case path strings ─────────────────────────────────────

    @Test
    fun `empty string returns null`() {
        assertNull(SVGPathParser.parse(""))
    }

    @Test
    fun `unknown command returns null`() {
        assertNull(SVGPathParser.parse("X 10 10"))
    }

    @Test
    fun `trailing comma returns null`() {
        assertNull(SVGPathParser.parse("M 10,10,"))
    }

    @Test
    fun `line L without prior moveTo returns null`() {
        assertNull(SVGPathParser.parse("L 10 10"))
    }

    @Test
    fun `moveTo with missing y coordinate returns null`() {
        assertNull(SVGPathParser.parse("M 10"))
    }

    @Test
    fun `non-numeric content returns null`() {
        assertNull(SVGPathParser.parse("M abc def"))
    }
}
