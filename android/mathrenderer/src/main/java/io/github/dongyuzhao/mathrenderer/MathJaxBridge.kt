package io.github.dongyuzhao.mathrenderer

import android.content.Context
import android.os.Build
import androidx.javascriptengine.JavaScriptSandbox
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

// ── Public types ─────────────────────────────────────────────────────────────

data class FormulaRenderOptions(
    val standalone: Boolean = false,
    val fontSize: Double = 16.0,
    val scale: Double = 1.0,
)

data class MathJaxViewBox(
    val minX: Double,
    val minY: Double,
    val width: Double,
    val height: Double,
) {
    companion object {
        val ZERO = MathJaxViewBox(0.0, 0.0, 0.0, 0.0)
    }
}

data class MathJaxSVG(
    val markup: String,
    val viewBox: MathJaxViewBox,
    val fallbackText: String? = null,
)

// ── Bridge ───────────────────────────────────────────────────────────────────

/**
 * Renders LaTeX formulas to SVG by executing the MathJax bundle inside
 * an isolated JavaScript engine (androidx.javascriptengine, requires API 26+).
 *
 * The JS bundle is loaded from `assets/MathJax/mathjax-renderer.js`.
 * Generate it with `npm run build` from the repository root.
 */
class MathJaxBridge(private val context: Context) {

    companion object {
        @Volatile private var instance: MathJaxBridge? = null

        fun shared(context: Context): MathJaxBridge =
            instance ?: synchronized(this) {
                instance ?: MathJaxBridge(context.applicationContext).also { instance = it }
            }

        /** Parses a "minX minY width height" viewBox string. Internal for testing. */
        internal fun parseViewBox(raw: String?): MathJaxViewBox? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.trim().split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
            return if (parts.size == 4) MathJaxViewBox(parts[0], parts[1], parts[2], parts[3]) else null
        }

        /** Extracts a viewBox from an SVG markup string. Internal for testing. */
        internal fun extractViewBox(markup: String): MathJaxViewBox? {
            val m = Regex("""viewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(markup)
            return parseViewBox(m?.groupValues?.getOrNull(1))
        }
    }

    private var sandboxFuture: ListenableFuture<JavaScriptSandbox>? = null
    private var cachedScript: String? = null

    /** Returns the SVG representation of [latex], or null on failure. */
    suspend fun svg(
        latex: String,
        options: FormulaRenderOptions = FormulaRenderOptions(),
    ): MathJaxSVG? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // JavaScriptEngine requires API 26+
            return@withContext null
        }
        try {
            val script = loadScript() ?: return@withContext null
            val sandbox = getSandbox()
            val isolate = sandbox.createIsolate()
            try {
                isolate.evaluateJavaScriptAsync(script).await()

                val optJson = JSONObject().apply {
                    put("standalone", options.standalone)
                    put("fontSize", options.fontSize)
                    put("scale", options.scale)
                }.toString()

                val call = """
                    (function() {
                        try {
                            return MathRendererMathJax.renderJSON(
                                ${JSONObject.quote(latex)},
                                $optJson
                            );
                        } catch(e) {
                            return JSON.stringify({ markup:'', viewBox:null, ok:false, error:String(e), fallbackText:${
                                JSONObject.quote(if (options.standalone) "$$${latex}$$" else "$${latex}$")
                            } });
                        }
                    })()
                """.trimIndent()

                val jsonStr = isolate.evaluateJavaScriptAsync(call).await()
                parseSVG(jsonStr, latex, options)
            } finally {
                isolate.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        sandboxFuture?.let { future ->
            try { future.get()?.close() } catch (_: Exception) {}
        }
        sandboxFuture = null
        instance = null
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun getSandbox(): JavaScriptSandbox {
        val existing = sandboxFuture
        if (existing != null) {
            try { return existing.await() } catch (_: Exception) {}
        }
        val future = JavaScriptSandbox.createConnectedInstanceAsync(context)
        sandboxFuture = future
        return future.await()
    }

    @Throws(IOException::class)
    private fun loadScript(): String? {
        if (cachedScript != null) return cachedScript
        return try {
            context.assets.open("MathJax/mathjax-renderer.js")
                .bufferedReader()
                .use { it.readText() }
                .also { cachedScript = it }
        } catch (_: IOException) { null }
    }

    private fun parseSVG(jsonStr: String, latex: String, options: FormulaRenderOptions): MathJaxSVG? {
        val json = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return null
        val markup = (json.optString("markup").takeIf { it.isNotEmpty() }
            ?: json.optString("svg"))
            .sanitized()

        if (!markup.contains("<svg", ignoreCase = true)) {
            val fallback = json.optString("fallbackText").takeIf { it.isNotEmpty() }
                ?: if (options.standalone) "$$${latex}$$" else "$${latex}$"
            return MathJaxSVG(markup = markup, viewBox = MathJaxViewBox.ZERO, fallbackText = fallback)
        }

        val viewBox = parseViewBox(json.optString("viewBox")) ?: extractViewBox(markup) ?: MathJaxViewBox.ZERO
        val fallback = json.optString("fallbackText").takeIf { it.isNotEmpty() }
        return MathJaxSVG(markup = markup, viewBox = viewBox, fallbackText = fallback)
    }

    private fun parseViewBox(raw: String?): MathJaxViewBox? = Companion.parseViewBox(raw)

    private fun extractViewBox(markup: String): MathJaxViewBox? = Companion.extractViewBox(markup)

    private fun String.sanitized(): String = this
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\son[a-zA-Z]+=\s*"[^"]*""""), "")
        .replace(Regex("""\son[a-zA-Z]+=\s*'[^']*'"""), "")
        .replace(Regex("""\son[a-zA-Z]+=\s*[^>\s]+"""), "")
        .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "")
}
