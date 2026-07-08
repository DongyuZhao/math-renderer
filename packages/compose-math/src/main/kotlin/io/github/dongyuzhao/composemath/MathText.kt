package io.github.dongyuzhao.composemath

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun MathText(
    tex: String,
    modifier: Modifier = Modifier,
    options: MathRenderOptions = MathRenderOptions(),
    color: Color = LocalContentColor.current,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val service = remember(context.applicationContext) {
        MathRenderService.getInstance(context.applicationContext)
    }
    val renderer = remember(service) { MathRenderer(service) }
    val rasterizer = remember { MathRasterizer() }
    var renderState by remember { mutableStateOf<MathRenderState>(MathRenderState.Pending) }

    LaunchedEffect(tex, options) {
        renderer.render(tex, options).collect { state ->
            renderState = state
        }
    }

    val fallbackText = MathJaxBridge.fallbackText(tex, options.displayMode)

    when (val state = renderState) {
        MathRenderState.Pending -> {
            Box(
                modifier = modifier
                    .testTag("math-render-pending")
                    .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
            )
        }
        is MathRenderState.Succeeded -> {
            RasterizedMath(
                rendered = state.rendered,
                rasterizer = rasterizer,
                options = options,
                color = color,
                density = density,
                fallbackText = fallbackText,
                modifier = modifier,
            )
        }
        is MathRenderState.Failed -> {
            Text(
                text = state.fallback.text,
                modifier = modifier.testTag("math-render-failed"),
                color = color,
            )
        }
    }
}

@Composable
private fun RasterizedMath(
    rendered: MathRenderedFormula,
    rasterizer: MathRasterizer,
    options: MathRenderOptions,
    color: Color,
    density: Density,
    fallbackText: String,
    modifier: Modifier,
) {
    val colorArgb = color.toArgb()
    val fontPx = with(density) {
        finitePositive(options.fontSizeSp, 16f).sp.toPx()
    } * finitePositive(options.scale, 1f)
    val accessibilityText = rendered.svg.fallbackText ?: fallbackText

    val formula by produceState<MathRasterizedFormula?>(
        initialValue = null,
        rendered.document,
        colorArgb,
        fontPx,
    ) {
        value = withContext(Dispatchers.Default) {
            rasterizer.rasterize(
                markup = rendered.svg.markup,
                document = rendered.document,
                colorArgb = colorArgb,
                fontPx = fontPx,
                fallbackText = accessibilityText,
            )
        }
    }

    val rasterized = formula
    if (rasterized == null) {
        Box(
            modifier = modifier
                .testTag("math-render-pending")
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        )
        return
    }

    val displaySize = with(density) {
        DpSize(rasterized.widthPx.toDp(), rasterized.heightPx.toDp())
    }
    val baselineFromTopPx = (rasterized.heightPx + rasterized.baselineOffsetPx)
        .roundToInt()
        .coerceIn(0, rasterized.heightPx)

    Box(
        modifier = modifier
            .mathBaseline(baselineFromTopPx)
            .testTag("math-render-success")
            .size(displaySize),
    ) {
        Image(
            bitmap = rasterized.image,
            contentDescription = accessibilityText,
            modifier = Modifier
                .matchParentSize()
                .testTag("math-render-svg-loaded"),
            contentScale = ContentScale.FillBounds,
        )
    }
}

internal fun Modifier.mathBaseline(baselineFromTopPx: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val constrainedBaseline = baselineFromTopPx.coerceIn(0, placeable.height)
    layout(
        width = placeable.width,
        height = placeable.height,
        alignmentLines = mapOf(
            FirstBaseline to constrainedBaseline,
            LastBaseline to constrainedBaseline,
        ),
    ) {
        placeable.place(0, 0)
    }
}

/**
 * The layout footprint of the rendered formula, folding sp font scale and the extra render scale
 * into MathJax's 1000-unit em coordinate space. Kept for callers that size around a formula ahead
 * of rasterization.
 */
internal fun MathJaxSvg.displaySize(fontSizeSp: Float, scale: Float, density: Density): DpSize {
    val safeFontSizeSp = finitePositive(fontSizeSp, 16f)
    val safeScale = finitePositive(scale, 1f)
    return with(density) {
        val fontPixels = safeFontSizeSp.sp.toPx() * safeScale
        DpSize(
            width = (viewBox.width / 1000f * fontPixels).toDp().coerceAtLeast(1.dp),
            height = (viewBox.height / 1000f * fontPixels).toDp().coerceAtLeast(1.dp),
        )
    }
}

private fun finitePositive(value: Float, fallback: Float): Float =
    if (value.isFinite() && value > 0f) value else fallback
