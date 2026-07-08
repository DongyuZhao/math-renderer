package io.github.dongyuzhao.composemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgPathParserTest {
    @Test
    fun parsesAbsoluteMoveLineAndClose() {
        val path = SvgPathParser.parse("M0 0 L100 0 L100 100 Z")

        assertEquals(
            listOf(
                MathSvgPathSegment.MoveTo(0f, 0f),
                MathSvgPathSegment.LineTo(100f, 0f),
                MathSvgPathSegment.LineTo(100f, 100f),
                MathSvgPathSegment.Close,
            ),
            path?.segments,
        )
    }

    @Test
    fun resolvesRelativeCommandsAgainstCurrentPoint() {
        val path = SvgPathParser.parse("m10 10 l5 0 l0 5")

        assertEquals(
            listOf(
                MathSvgPathSegment.MoveTo(10f, 10f),
                MathSvgPathSegment.LineTo(15f, 10f),
                MathSvgPathSegment.LineTo(15f, 15f),
            ),
            path?.segments,
        )
    }

    @Test
    fun reflectsSmoothCubicControlPoint() {
        val path = SvgPathParser.parse("M0 0 C10 0 10 10 0 10 S-10 20 0 20")
        val segments = path?.segments
        assertTrue(segments != null && segments.size == 3)

        val smooth = segments!![2] as MathSvgPathSegment.CubicTo
        // Reflection of the previous cubic control (10,10) about the current point (0,10).
        assertEquals(-10f, smooth.x1, 0.001f)
        assertEquals(10f, smooth.y1, 0.001f)
    }

    @Test
    fun rejectsMalformedPath() {
        assertNull(SvgPathParser.parse("M0"))
        assertNull(SvgPathParser.parse("L10 10"))
        assertNull(SvgPathParser.parse("M0 0 Z Z junk"))
    }

    @Test
    fun treatsEmptyPathAsNoOp() {
        // Invisible glyphs (e.g. U+2061) render as `<path d="">`.
        assertEquals(emptyList<MathSvgPathSegment>(), SvgPathParser.parse("")?.segments)
        assertEquals(emptyList<MathSvgPathSegment>(), SvgPathParser.parse("   ")?.segments)
    }
}
