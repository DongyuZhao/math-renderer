package io.github.dongyuzhao.composemath

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class MathRendererContractTest {
    @Test
    fun renderPublishesPendingThenSucceeded() = runBlocking {
        val renderer = MathRenderer(
            MathJaxBridge(
                FakeMathJaxRuntime(
                    """
                    {
                      "ok": true,
                      "markup": "<svg viewBox=\"0 -10 20 30\"><g></g></svg>",
                      "viewBox": {"minX": 0, "minY": -10, "width": 20, "height": 30},
                      "fallbackText": null,
                      "error": null
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val states = renderer.render("x^2").toList()

        assertEquals(2, states.size)
        assertEquals(MathRenderState.Pending, states[0])
        val succeeded = states[1] as MathRenderState.Succeeded
        assertEquals(true, succeeded.rendered.svg.ok)
        assertTrue(succeeded.rendered.svg.markup.contains("<svg"))
    }

    @Test
    fun renderPublishesFailedForInvalidInput() = runBlocking {
        val renderer = MathRenderer(MathJaxBridge(FakeMathJaxRuntime(null)))

        val states = renderer.render(
            tex = "x+y",
            options = MathRenderOptions(fontSizeSp = 0f),
        ).toList()

        assertEquals(2, states.size)
        assertEquals(MathRenderState.Pending, states[0])
        val failed = states[1] as MathRenderState.Failed
        assertEquals(MathRenderFailureReason.InvalidInput, failed.fallback.reason)
        assertEquals("""\(x+y\)""", failed.fallback.text)
    }

    @Test
    fun renderPublishesFailedForMathJaxError() = runBlocking {
        val renderer = MathRenderer(
            MathJaxBridge(
                FakeMathJaxRuntime(
                    """
                    {
                      "ok": false,
                      "markup": "<svg viewBox=\"0 0 10 10\"><g data-mml-node=\"merror\"></g></svg>",
                      "viewBox": {"minX": 0, "minY": 0, "width": 10, "height": 10},
                      "fallbackText": "\\[\\frac{\\]",
                      "error": "MathJax reported a TeX parse error."
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val states = renderer.render(
            tex = "\\frac{",
            options = MathRenderOptions(displayMode = MathDisplayMode.Block),
        ).toList()

        assertEquals(2, states.size)
        assertEquals(MathRenderState.Pending, states[0])
        val failed = states[1] as MathRenderState.Failed
        assertEquals(MathRenderFailureReason.MathJaxError, failed.fallback.reason)
        assertEquals("""\[\frac{\]""", failed.fallback.text)
    }

    private class FakeMathJaxRuntime(private val result: String?) : MathJaxRuntime {
        val script = AtomicReference<String?>()

        override suspend fun evaluate(script: String): String? {
            this.script.set(script)
            return result
        }
    }
}
