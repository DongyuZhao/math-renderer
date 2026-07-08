package io.github.dongyuzhao.composemath

enum class MathDisplayMode {
    Inline,
    Block,
}

data class MathRenderOptions(
    val displayMode: MathDisplayMode = MathDisplayMode.Inline,
    val fontSizeSp: Float = 16f,
    val scale: Float = 1f,
) {
    internal val standalone: Boolean
        get() = displayMode == MathDisplayMode.Block
}

data class MathJaxViewBox(val minX: Float, val minY: Float, val width: Float, val height: Float) {
    companion object {
        val Zero = MathJaxViewBox(0f, 0f, 0f, 0f)
    }
}

data class MathJaxSvg(
    val ok: Boolean,
    val markup: String,
    val viewBox: MathJaxViewBox,
    val fallbackText: String? = null,
    val error: String? = null,
)

enum class MathRenderFailureReason {
    InvalidInput,
    BridgeUnavailable,
    MathJaxError,
    RenderFailed,
}

data class MathRenderFallback(val text: String, val reason: MathRenderFailureReason, val error: String? = null)

data class MathRenderedFormula(val svg: MathJaxSvg, val document: MathSvgDocument)

sealed interface MathRenderState {
    data object Pending : MathRenderState
    data class Succeeded(val rendered: MathRenderedFormula) : MathRenderState
    data class Failed(val fallback: MathRenderFallback) : MathRenderState
}

fun interface MathJaxRuntime {
    suspend fun evaluate(script: String): String?
}
