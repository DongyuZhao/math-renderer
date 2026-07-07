package io.github.dongyuzhao.mathrenderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM-local unit tests for [MathJaxBridge] JSON/SVG parsing helpers.
 *
 * These tests exercise pure-Kotlin logic (no Android classes) and therefore
 * do not require Robolectric.
 * Run with: ./gradlew :mathrenderer:test
 */
class MathJaxBridgeParserTest {

    // ── parseViewBox ─────────────────────────────────────────────────────────

    @Test
    fun `parseViewBox with space-delimited values`() {
        val vb = MathJaxBridge.parseViewBox("-1.5 -0.5 10.2 5.8")
        assertNotNull(vb)
        assertEquals(-1.5, vb!!.minX, 1e-9)
        assertEquals(-0.5, vb.minY, 1e-9)
        assertEquals(10.2, vb.width, 1e-9)
        assertEquals(5.8, vb.height, 1e-9)
    }

    @Test
    fun `parseViewBox with comma-delimited values`() {
        val vb = MathJaxBridge.parseViewBox("0,0,100,50")
        assertNotNull(vb)
        assertEquals(0.0, vb!!.minX, 1e-9)
        assertEquals(0.0, vb.minY, 1e-9)
        assertEquals(100.0, vb.width, 1e-9)
        assertEquals(50.0, vb.height, 1e-9)
    }

    @Test
    fun `parseViewBox with mixed delimiters`() {
        val vb = MathJaxBridge.parseViewBox("0 0,80 40")
        assertNotNull(vb)
        assertEquals(80.0, vb!!.width, 1e-9)
        assertEquals(40.0, vb.height, 1e-9)
    }

    @Test
    fun `parseViewBox with integer values`() {
        val vb = MathJaxBridge.parseViewBox("10 20 300 150")
        assertNotNull(vb)
        assertEquals(10.0, vb!!.minX, 1e-9)
        assertEquals(20.0, vb.minY, 1e-9)
        assertEquals(300.0, vb.width, 1e-9)
        assertEquals(150.0, vb.height, 1e-9)
    }

    @Test
    fun `parseViewBox with leading and trailing whitespace`() {
        val vb = MathJaxBridge.parseViewBox("  0 0 100 50  ")
        assertNotNull(vb)
        assertEquals(100.0, vb!!.width, 1e-9)
    }

    @Test
    fun `parseViewBox with null returns null`() {
        assertNull(MathJaxBridge.parseViewBox(null))
    }

    @Test
    fun `parseViewBox with empty string returns null`() {
        assertNull(MathJaxBridge.parseViewBox(""))
    }

    @Test
    fun `parseViewBox with blank string returns null`() {
        assertNull(MathJaxBridge.parseViewBox("   "))
    }

    @Test
    fun `parseViewBox with too few values returns null`() {
        assertNull(MathJaxBridge.parseViewBox("0 0 100"))
    }

    @Test
    fun `parseViewBox with too many values returns null`() {
        assertNull(MathJaxBridge.parseViewBox("0 0 100 50 99"))
    }

    @Test
    fun `parseViewBox with non-numeric input returns null`() {
        assertNull(MathJaxBridge.parseViewBox("a b c d"))
    }

    // ── extractViewBox ───────────────────────────────────────────────────────

    @Test
    fun `extractViewBox from double-quoted attribute`() {
        val markup = """<svg viewBox="-1 -2 100 50" xmlns="http://www.w3.org/2000/svg"></svg>"""
        val vb = MathJaxBridge.extractViewBox(markup)
        assertNotNull(vb)
        assertEquals(-1.0, vb!!.minX, 1e-9)
        assertEquals(-2.0, vb.minY, 1e-9)
        assertEquals(100.0, vb.width, 1e-9)
        assertEquals(50.0, vb.height, 1e-9)
    }

    @Test
    fun `extractViewBox from single-quoted attribute`() {
        val markup = """<svg viewBox='0 0 200 100'></svg>"""
        val vb = MathJaxBridge.extractViewBox(markup)
        assertNotNull(vb)
        assertEquals(200.0, vb!!.width, 1e-9)
    }

    @Test
    fun `extractViewBox is case-insensitive`() {
        val markup = """<svg VIEWBOX="0 0 50 25"></svg>"""
        val vb = MathJaxBridge.extractViewBox(markup)
        assertNotNull(vb)
        assertEquals(50.0, vb!!.width, 1e-9)
    }

    @Test
    fun `extractViewBox returns null when attribute is absent`() {
        val markup = """<svg xmlns="http://www.w3.org/2000/svg"></svg>"""
        assertNull(MathJaxBridge.extractViewBox(markup))
    }

    @Test
    fun `extractViewBox returns null for empty string`() {
        assertNull(MathJaxBridge.extractViewBox(""))
    }

    // ── MathJaxViewBox companion ─────────────────────────────────────────────

    @Test
    fun `MathJaxViewBox ZERO has all zero fields`() {
        assertEquals(0.0, MathJaxViewBox.ZERO.minX, 1e-9)
        assertEquals(0.0, MathJaxViewBox.ZERO.minY, 1e-9)
        assertEquals(0.0, MathJaxViewBox.ZERO.width, 1e-9)
        assertEquals(0.0, MathJaxViewBox.ZERO.height, 1e-9)
    }
}
