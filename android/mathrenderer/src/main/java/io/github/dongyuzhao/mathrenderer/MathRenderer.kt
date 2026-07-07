package io.github.dongyuzhao.mathrenderer

import android.content.Context
import android.graphics.Color

/**
 * MathRenderer — pure MathJax-driven LaTeX formula renderer for Android.
 *
 * Runs the MathJax bundle inside an isolated JavaScript engine to produce SVG,
 * then rasterises the SVG to a [FormulaImage] using an in-house Android
 * Canvas/Path SVG subset renderer.
 *
 * ## Quick start
 *
 * ```kotlin
 * val image = MathRenderer.render(context, "x^2 + y^2 = r^2")
 * // image?.bitmap → Bitmap ready to display
 * // image?.baselineOffsetDp → for inline text alignment
 * ```
 */
object MathRenderer {

    /**
     * Renders a LaTeX formula to a [FormulaImage].
     *
     * Must be called from a coroutine (suspend function). The JS evaluation
     * happens off the main thread automatically.
     *
     * @param context    Android context (Application context is recommended).
     * @param latex      LaTeX source without surrounding `$` delimiters.
     * @param options    Rendering options (mode, font size, scale).
     * @param color      Fill colour (ARGB int); defaults to black.
     * @param density    Screen density for rasterisation (from `DisplayMetrics.density`).
     */
    suspend fun render(
        context: Context,
        latex: String,
        options: FormulaRenderOptions = FormulaRenderOptions(),
        color: Int = Color.BLACK,
        density: Float = context.resources.displayMetrics.density,
    ): FormulaImage? {
        val svg = MathJaxBridge.shared(context).svg(latex, options) ?: return null
        return FormulaRasterizer(density).image(
            svg = svg,
            color = color,
            scale = density * options.scale.toFloat(),
            fontSizeDp = options.fontSize.toFloat(),
        )
    }

    /**
     * Returns only the [MathJaxSVG] for a LaTeX formula (no rasterisation).
     */
    suspend fun svg(
        context: Context,
        latex: String,
        options: FormulaRenderOptions = FormulaRenderOptions(),
    ): MathJaxSVG? = MathJaxBridge.shared(context).svg(latex, options)
}
