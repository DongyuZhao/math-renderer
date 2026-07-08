package io.github.dongyuzhao.composemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathSvgParserTest {
    @Test
    fun parsesViewBoxDefsAndUseIntoDrawCommand() {
        val markup = """
            <svg viewBox="0 -883 1000 883">
              <defs><path id="A" d="M0 0 L100 0 L100 100 Z"/></defs>
              <g transform="translate(5,7)"><use href="#A" x="10" y="20"/></g>
            </svg>
        """.trimIndent()

        val document = MathSvgParser.parse(markup)!!

        assertEquals(MathJaxViewBox(0f, -883f, 1000f, 883f), document.viewBox)
        assertEquals(1, document.drawCommands.size)

        val command = document.drawCommands.first()
        assertEquals(MathSvgPathSegment.MoveTo(0f, 0f), command.path.segments.first())
        // use(10,20) composed with g translate(5,7) => translate(15,27).
        assertEquals(15f, command.transform.tx, 0.001f)
        assertEquals(27f, command.transform.ty, 0.001f)
    }

    @Test
    fun parsesRectIntoClosedPath() {
        val markup =
            """<svg viewBox="0 0 10 10"><rect x="1" y="2" width="3" height="4"/></svg>"""

        val document = MathSvgParser.parse(markup)!!

        assertEquals(1, document.drawCommands.size)
        val segments = document.drawCommands.first().path.segments
        assertEquals(MathSvgPathSegment.MoveTo(1f, 2f), segments.first())
        assertTrue(segments.last() is MathSvgPathSegment.Close)
    }

    @Test
    fun rejectsDoctypeDeclaration() {
        val markup =
            """<!DOCTYPE svg><svg viewBox="0 0 10 10"><g></g></svg>"""

        assertNull(MathSvgParser.parse(markup))
    }

    @Test
    fun rejectsScriptableAttribute() {
        val markup =
            """<svg viewBox="0 0 10 10"><g onload="alert(1)"></g></svg>"""

        assertNull(MathSvgParser.parse(markup))
    }

    @Test
    fun rejectsExternalUseReference() {
        val markup =
            """<svg viewBox="0 0 10 10"><use href="https://evil/#A"/></svg>"""

        assertNull(MathSvgParser.parse(markup))
    }

    @Test
    fun rejectsUnknownElement() {
        val markup =
            """<svg viewBox="0 0 10 10"><foreignObject></foreignObject></svg>"""

        assertNull(MathSvgParser.parse(markup))
    }

    @Test
    fun parsesRealMathJaxOutput() {
        val markup = javaClass.getResourceAsStream("/fixtures/real-mathjax-svg.svg")!!
            .use { it.readBytes().toString(Charsets.UTF_8) }

        val document = MathSvgParser.parse(markup)!!

        // Real MathJax "x^2 + y^2 = z^2" output: a negative-minY viewBox and inline glyph paths.
        assertEquals(-833.9f, document.viewBox.minY, 0.01f)
        assertTrue(document.viewBox.width > 0f && document.viewBox.height > 0f)
        // The markup contains exactly 8 inline glyph paths (x, 2, +, y, 2, =, z, 2).
        assertEquals(8, document.drawCommands.size)
        assertTrue(
            "every glyph path should carry drawing segments",
            document.drawCommands.all { it.path.segments.isNotEmpty() },
        )
    }
}
