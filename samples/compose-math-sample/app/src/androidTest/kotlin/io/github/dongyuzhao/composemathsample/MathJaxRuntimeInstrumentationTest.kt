package io.github.dongyuzhao.composemathsample

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.dongyuzhao.composemath.MathDisplayMode
import io.github.dongyuzhao.composemath.MathJaxSvg
import io.github.dongyuzhao.composemath.MathRenderOptions
import io.github.dongyuzhao.composemath.MathRenderService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MathJaxRuntimeInstrumentationTest {
    @Test
    fun rendersLatexToSvgWithSharedRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = MathRenderService.getInstance(context)
        val first = withTimeout(30_000) {
            service.renderSvg("x^2 + y^2 = z^2")
        }
        val second = withTimeout(30_000) {
            service.renderSvg("e^{i\\pi} + 1 = 0")
        }

        assertValidSvg(first)
        assertValidSvg(second)
    }

    @Test
    fun commonFormulaFixturesRenderSuccessfullyWithSharedRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = MathRenderService.getInstance(context)

        for (fixture in readFormulaFixtures()) {
            val renderedSvg = withTimeout(30_000) {
                service.renderSvg(
                    fixture.tex,
                    MathRenderOptions(
                        displayMode = fixture.displayMode,
                        fontSizeSp = fixture.fontSizeSp,
                        scale = fixture.scale,
                    ),
                )
            }

            assertNotNull("${fixture.id}: MathJax bridge returned null.", renderedSvg)
            assertTrue(
                "${fixture.id}: ${renderedSvg?.error ?: renderedSvg?.fallbackText}",
                renderedSvg?.ok == true,
            )
            assertTrue("${fixture.id}: missing SVG markup.", renderedSvg?.markup.orEmpty().contains("<svg"))
            assertTrue("${fixture.id}: empty width.", (renderedSvg?.viewBox?.width ?: 0f) > 0f)
            assertTrue("${fixture.id}: empty height.", (renderedSvg?.viewBox?.height ?: 0f) > 0f)
        }
    }

    private fun assertValidSvg(svg: MathJaxSvg?) {
        assertNotNull("MathJax service returned null.", svg)
        assertTrue(svg?.error ?: "MathJax service returned a failed SVG.", svg?.ok == true)
        assertTrue(svg?.markup.orEmpty().contains("<svg"))
    }

    private fun readFormulaFixtures(): List<FormulaFixture> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val lines = context.assets.open("common-formulas.tsv").bufferedReader().use { reader ->
            reader.readLines().filter { it.isNotBlank() }
        }
        assertEquals("id\tcategory\tlevel\tdisplayMode\tfontSize\tscale\ttex", lines.first())

        return lines.drop(1).map { line ->
            val columns = line.split("\t", limit = 7)
            assertEquals(line, 7, columns.size)
            FormulaFixture(
                id = columns[0],
                displayMode = if (columns[3] == "block") MathDisplayMode.Block else MathDisplayMode.Inline,
                fontSizeSp = columns[4].toFloat(),
                scale = columns[5].toFloat(),
                tex = columns[6],
            )
        }
    }

    private data class FormulaFixture(
        val id: String,
        val displayMode: MathDisplayMode,
        val fontSizeSp: Float,
        val scale: Float,
        val tex: String,
    )
}
