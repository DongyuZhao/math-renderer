package io.github.dongyuzhao.composemath

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathJaxBridgeContractTest {
    @Test
    fun renderConsumesQuickJsStringResultDirectly() = runBlocking {
        val scripts = mutableListOf<String>()
        val bridge = MathJaxBridge(
            MathJaxRuntime { script ->
                scripts += script
                """
                {
                  "ok": true,
                  "markup": "<svg viewBox=\"0 0 20 30\"></svg>",
                  "viewBox": {"minX": 0, "minY": 0, "width": 20, "height": 30}
                }
                """.trimIndent()
            },
        )

        val rendered = bridge.render("x^2", MathRenderOptions())

        assertEquals(1, scripts.size)
        assertTrue(scripts.single().contains("MathRenderLatexToSvg.renderJSON"))
        assertEquals(true, rendered?.ok)
        assertEquals(20f, rendered?.viewBox?.width)
    }

    @Test
    fun fallbackTextUsesStandardLatexMathDelimiters() {
        assertEquals("""\(x+1\)""", MathJaxBridge.fallbackText("x+1", MathDisplayMode.Inline))
        assertEquals("""\[x+1\]""", MathJaxBridge.fallbackText("x+1", MathDisplayMode.Block))
    }

    @Test
    fun bridgeOptionsJsonUsesSwiftAlignedStandaloneFlag() {
        val json = MathJaxBridge.bridgeOptionsJson(
            MathRenderOptions(
                displayMode = MathDisplayMode.Block,
                fontSizeSp = 18f,
                scale = 2f,
            ),
        )

        assertTrue(json.contains("\"standalone\":true"))
        assertTrue(json.contains("\"fontSize\":18.0"))
        assertTrue(json.contains("\"scale\":2.0"))
        assertFalse(json.contains("display"))
        assertFalse(json.contains("containerWidth"))
    }

    @Test
    fun renderInvocationCallsMathJaxRuntimeWithoutHtmlProtocol() {
        val invocation = MathJaxBridge.renderInvocation(
            tex = "x^2",
            options = MathRenderOptions(),
        )

        assertTrue(invocation.contains("MathRenderLatexToSvg.renderJSON"))
        assertTrue(invocation.contains("\"x^2\""))
        assertFalse(invocation.contains("<html"))
        assertFalse(invocation.contains("document.getElementById"))
    }

    @Test
    fun parsePayloadAcceptsMarkupAndViewBoxObject() {
        val payload = MathJaxBridge.parsePayload(
            """
            {
              "ok": true,
              "svg": "<svg viewBox=\"0 -10 20 30\"><g></g></svg>",
              "viewBox": {"minX": 0, "minY": -10, "width": 20, "height": 30},
              "fallbackText": null
            }
            """.trimIndent(),
        )

        assertEquals(true, payload?.ok)
        assertEquals("<svg viewBox=\"0 -10 20 30\"><g></g></svg>", payload?.markup)
        assertEquals(0f, payload?.viewBox?.minX)
        assertEquals(-10f, payload?.viewBox?.minY)
        assertEquals(20f, payload?.viewBox?.width)
        assertEquals(30f, payload?.viewBox?.height)
    }
}
