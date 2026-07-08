package io.github.dongyuzhao.composemath

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MathRenderer private constructor(private val renderSvg: suspend (String, MathRenderOptions) -> MathJaxSvg?) {
    constructor(bridge: MathJaxBridge) : this(bridge::render)

    internal constructor(service: MathRenderService) : this(service::renderSvg)

    fun render(tex: String, options: MathRenderOptions = MathRenderOptions()): Flow<MathRenderState> = flow {
        emit(MathRenderState.Pending)
        emit(renderTerminalState(tex, options))
    }

    private suspend fun renderTerminalState(tex: String, options: MathRenderOptions): MathRenderState {
        val fallbackText = MathJaxBridge.fallbackText(tex, options.displayMode)
        if (!options.fontSizeSp.isFinite() ||
            options.fontSizeSp <= 0f ||
            !options.scale.isFinite() ||
            options.scale <= 0f
        ) {
            return MathRenderState.Failed(
                MathRenderFallback(
                    text = fallbackText,
                    reason = MathRenderFailureReason.InvalidInput,
                ),
            )
        }

        val svg = renderSvg(tex, options)
            ?: return MathRenderState.Failed(
                MathRenderFallback(
                    text = fallbackText,
                    reason = MathRenderFailureReason.BridgeUnavailable,
                ),
            )

        if (!svg.ok) {
            return MathRenderState.Failed(
                MathRenderFallback(
                    text = svg.fallbackText ?: fallbackText,
                    reason = MathRenderFailureReason.MathJaxError,
                    error = svg.error,
                ),
            )
        }

        if (svg.markup.isBlank() || svg.viewBox.width <= 0f || svg.viewBox.height <= 0f) {
            return MathRenderState.Failed(
                MathRenderFallback(
                    text = svg.fallbackText ?: fallbackText,
                    reason = MathRenderFailureReason.RenderFailed,
                    error = svg.error,
                ),
            )
        }

        val document = MathSvgParser.parse(svg.markup)
        if (document == null || document.viewBox.width <= 0f || document.viewBox.height <= 0f) {
            return MathRenderState.Failed(
                MathRenderFallback(
                    text = svg.fallbackText ?: fallbackText,
                    reason = MathRenderFailureReason.RenderFailed,
                    error = svg.error,
                ),
            )
        }

        return MathRenderState.Succeeded(MathRenderedFormula(svg = svg, document = document))
    }
}
