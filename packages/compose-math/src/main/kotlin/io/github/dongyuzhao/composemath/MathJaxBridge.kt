package io.github.dongyuzhao.composemath

import org.json.JSONObject

class MathJaxBridge(private val runtime: MathJaxRuntime) {
    suspend fun render(tex: String, options: MathRenderOptions = MathRenderOptions()): MathJaxSvg? =
        parsePayload(runtime.evaluate(renderInvocation(tex, options)))

    companion object {
        fun fallbackText(tex: String, displayMode: MathDisplayMode): String =
            if (displayMode == MathDisplayMode.Block) """\[$tex\]""" else """\($tex\)"""

        fun bridgeOptionsJson(options: MathRenderOptions): String {
            val fontSize = finitePositive(options.fontSizeSp, 16f)
            val scale = finitePositive(options.scale, 1f)
            return buildString {
                append("{")
                append("\"standalone\":")
                append(options.standalone)
                append(",\"fontSize\":")
                append(fontSize)
                append(",\"scale\":")
                append(scale)
                append("}")
            }
        }

        fun renderInvocation(tex: String, options: MathRenderOptions): String = "MathRenderLatexToSvg.renderJSON(" +
            JSONObject.quote(tex) +
            "," +
            bridgeOptionsJson(options) +
            ")"

        fun parsePayload(json: String?): MathJaxSvg? {
            if (json.isNullOrBlank()) {
                return null
            }

            return runCatching {
                val payload = JSONObject(json)
                val markup = sanitizeMarkup(
                    payload.optString("markup", payload.optString("svg", "")),
                )
                val viewBox = payloadViewBox(payload) ?: parseViewBox(markup) ?: MathJaxViewBox.Zero
                val ok = payload.optBoolean("ok", !hasMathError(markup))
                val fallbackText = payload.optNullableString("fallbackText")
                val error = payload.optNullableString("error")
                val resolvedFallback =
                    fallbackText ?: if (!ok && error != null) error else null

                MathJaxSvg(
                    ok = ok && !hasMathError(markup),
                    markup = markup,
                    viewBox = viewBox,
                    fallbackText = resolvedFallback,
                    error = error,
                )
            }.getOrNull()
        }

        private fun finitePositive(value: Float, fallback: Float): Float =
            if (value.isFinite() && value > 0f) value else fallback

        private fun payloadViewBox(payload: JSONObject): MathJaxViewBox? {
            if (!payload.has("viewBox") || payload.isNull("viewBox")) {
                return null
            }

            val raw = payload.get("viewBox")
            if (raw is String) {
                return parseViewBoxValue(raw)
            }

            if (raw is JSONObject) {
                return MathJaxViewBox(
                    minX = raw.flexFloat("minX") ?: return null,
                    minY = raw.flexFloat("minY") ?: return null,
                    width = raw.flexFloat("width") ?: return null,
                    height = raw.flexFloat("height") ?: return null,
                )
            }

            return null
        }

        private fun parseViewBox(markup: String): MathJaxViewBox? {
            val match = Regex("""viewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(markup)
                ?: return null
            return parseViewBoxValue(match.groupValues[1])
        }

        private fun parseViewBoxValue(value: String): MathJaxViewBox? {
            val values = value
                .trim()
                .split(Regex("""[\s,]+"""))
                .filter { it.isNotEmpty() }
                .mapNotNull { it.toFloatOrNull() }
            if (values.size != 4) {
                return null
            }

            return MathJaxViewBox(values[0], values[1], values[2], values[3])
        }

        private fun sanitizeMarkup(markup: String): String = markup
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\son[a-zA-Z]+\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\son[a-zA-Z]+\s*=\s*'[^']*'""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\son[a-zA-Z]+\s*=\s*[^>\s]+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""javascript:""", RegexOption.IGNORE_CASE), "")

        private fun hasMathError(markup: String): Boolean =
            Regex("""data-mml-node=["']merror["']""", RegexOption.IGNORE_CASE)
                .containsMatchIn(markup)

        private fun JSONObject.flexFloat(name: String): Float? {
            if (!has(name) || isNull(name)) {
                return null
            }
            return when (val value = get(name)) {
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull()
                else -> null
            }
        }

        private fun JSONObject.optNullableString(name: String): String? {
            if (!has(name) || isNull(name)) {
                return null
            }
            return optString(name).takeIf { it.isNotEmpty() }
        }
    }
}
