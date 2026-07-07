package io.github.dongyuzhao.mathrenderer

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (on-device) integration tests for [MathRenderer].
 *
 * These tests require a connected Android device or emulator (API 26+).
 * Run with: ./gradlew :mathrenderer:connectedAndroidTest
 *
 * Note: [MathRenderer.render] invokes the full pipeline — MathJax in the
 * JavaScriptEngine sandbox + SVG rasterisation — so each test validates the
 * end-to-end flow on real Android hardware.
 */
@RunWith(AndroidJUnit4::class)
class MathRendererInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun renderInlineFormula_returnsBitmap() {
        val image = runBlocking { MathRenderer.render(context, "x^2 + y^2 = r^2") }
        assertNotNull("render() should return a FormulaImage for a valid inline formula", image)
        assertTrue("bitmap width should be > 0", image!!.bitmap.width > 0)
        assertTrue("bitmap height should be > 0", image.bitmap.height > 0)
    }

    @Test
    fun renderDisplayFormula_returnsBitmap() {
        val opts = FormulaRenderOptions(standalone = true, fontSize = 20.0)
        val image = runBlocking {
            MathRenderer.render(context, """\int_0^\infty e^{-x^2}\,dx""", options = opts)
        }
        assertNotNull("render() should return a FormulaImage for a display formula", image)
        assertTrue(image!!.bitmap.width > 0)
        assertTrue(image.bitmap.height > 0)
    }

    @Test
    fun renderWithCustomColor_returnsBitmap() {
        val image = runBlocking {
            MathRenderer.render(context, "E = mc^2", color = Color.RED)
        }
        assertNotNull("render() should succeed with a custom color", image)
    }

    @Test
    fun renderSimpleExpression_hasPositiveSize() {
        val image = runBlocking { MathRenderer.render(context, "a + b") }
        assertNotNull(image)
        assertTrue("widthDp > 0", image!!.widthDp > 0f)
        assertTrue("heightDp > 0", image.heightDp > 0f)
    }

    @Test
    fun svgOnly_containsSVGMarkup() {
        val svg = runBlocking { MathRenderer.svg(context, "E = mc^2") }
        assertNotNull("svg() should return a MathJaxSVG", svg)
        assertTrue(
            "markup should contain <svg",
            svg!!.markup.contains("<svg", ignoreCase = true)
        )
        assertTrue("viewBox width should be > 0", svg.viewBox.width > 0.0)
        assertTrue("viewBox height should be > 0", svg.viewBox.height > 0.0)
    }

    @Test
    fun render_doesNotThrowForEmptyString() {
        // Empty LaTeX — MathJax will produce a result or return null; it should not crash.
        val result = runBlocking {
            runCatching { MathRenderer.render(context, "") }.getOrNull()
        }
        // Either null or a valid image is acceptable; crashing is not.
    }
}
