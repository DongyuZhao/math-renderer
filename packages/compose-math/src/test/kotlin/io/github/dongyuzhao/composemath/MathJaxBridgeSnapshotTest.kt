package io.github.dongyuzhao.composemath

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class MathJaxBridgeSnapshotTest {
    @Test
    fun bridgeContractMatchesSnapshot() {
        val invocation = MathJaxBridge.renderInvocation(
            tex = "x^2 + y^2 = z^2",
            options = MathRenderOptions(
                displayMode = MathDisplayMode.Inline,
                fontSizeSp = 16f,
                scale = 1f,
            ),
        )
        val parsed = MathJaxBridge.parsePayload(
            """
            {
              "ok": true,
              "markup": "<svg viewBox=\"0 -442 1861 622\"><g fill=\"currentColor\"></g></svg>",
              "viewBox": {"minX": 0, "minY": -442, "width": 1861, "height": 622},
              "fallbackText": null,
              "error": null
            }
            """.trimIndent(),
        )

        val snapshot = buildString {
            appendLine("invocation=$invocation")
            appendLine("ok=${parsed?.ok}")
            appendLine("viewBox=${parsed?.viewBox}")
            appendLine("fallbackText=${parsed?.fallbackText}")
            appendLine("error=${parsed?.error}")
            appendLine("markupSha256=${parsed?.markup?.sha256HexDigest()}")
        }.trimEnd()

        assertEquals(readSnapshot("mathjax-bridge-contract.txt").trimEnd(), snapshot)
    }

    private fun readSnapshot(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("snapshots/$name"),
    ) { "Missing snapshot resource: $name" }.readText()

    private fun String.sha256HexDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
