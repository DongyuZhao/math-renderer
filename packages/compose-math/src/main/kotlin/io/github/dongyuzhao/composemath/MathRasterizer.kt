package io.github.dongyuzhao.composemath

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.ceil

/**
 * A MathJax formula rasterized to a tinted, pixel-resolution bitmap plus the layout metrics
 * needed to place it on a text baseline. Mirrors the iOS `MathRasterizedFormula`.
 */
data class MathRasterizedFormula(
    val image: ImageBitmap,
    val widthPx: Int,
    val heightPx: Int,
    val baselineOffsetPx: Float,
    val fallbackText: String? = null,
)

/**
 * Draws a parsed [MathSvgDocument] into an [ImageBitmap], honoring the requested fill color and
 * font pixel size, and caches results by markup + color + size.
 *
 * This is the Android analog of the iOS `MathRasterizer`: the MathJax SVG is turned into a native
 * raster image once, then only that image is composed on screen.
 */
class MathRasterizer {
    private val cache: LruCache<String, MathRasterizedFormula>

    init {
        cache = object : LruCache<String, MathRasterizedFormula>(MAX_CACHED_BITMAP_BYTES) {
            override fun sizeOf(key: String, value: MathRasterizedFormula): Int =
                value.widthPx * value.heightPx * BYTES_PER_PIXEL
        }
    }

    /**
     * Rasterizes [document] parsed from [markup].
     *
     * @param colorArgb packed ARGB color to fill every glyph and rule with.
     * @param fontPx the em size in device pixels (font scale and render scale already folded in).
     * @param fallbackText accessibility text carried through onto the resulting formula.
     */
    fun rasterize(
        markup: String,
        document: MathSvgDocument,
        colorArgb: Int,
        fontPx: Float,
        fallbackText: String? = null,
    ): MathRasterizedFormula? {
        val viewBox = document.viewBox
        if (!fontPx.isFinite() || fontPx <= 0f || viewBox.width <= 0f || viewBox.height <= 0f) {
            return null
        }

        val key = cacheKey(markup, colorArgb, fontPx, fallbackText)
        cache.get(key)?.let { return it }

        val widthPx = pixelDimension(viewBox.width, fontPx) ?: return null
        val heightPx = pixelDimension(viewBox.height, fontPx) ?: return null
        if (widthPx.toLong() * heightPx.toLong() > MAX_RENDERED_PIXEL_COUNT) {
            return null
        }

        val formula = render(document, colorArgb, fontPx, widthPx, heightPx, fallbackText)
            ?: return null
        cache.put(key, formula)
        return formula
    }

    private fun render(
        document: MathSvgDocument,
        colorArgb: Int,
        fontPx: Float,
        widthPx: Int,
        heightPx: Int,
        fallbackText: String?,
    ): MathRasterizedFormula? {
        val bitmap = runCatching {
            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null

        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            color = colorArgb
            style = Paint.Style.FILL
        }

        val viewBox = document.viewBox
        val unitScale = fontPx / 1000f

        // MathJax SVG coordinates are y-down like the destination bitmap, so no vertical flip is
        // needed: map viewBox space to pixels by scaling by the em ratio and shifting the origin.
        canvas.save()
        canvas.scale(unitScale, unitScale)
        canvas.translate(-viewBox.minX, -viewBox.minY)

        for (command in document.drawCommands) {
            canvas.save()
            canvas.concat(command.transform.toAndroidMatrix())
            canvas.drawPath(command.path.toAndroidPath(), paint)
            canvas.restore()
        }

        val textPaint = Paint(paint).apply {
            textSize = 1000f
        }
        for (command in document.textCommands) {
            if (command.text.isEmpty()) {
                continue
            }
            canvas.save()
            canvas.concat(command.transform.toAndroidMatrix())
            canvas.drawText(command.text, command.x, command.y, textPaint)
            canvas.restore()
        }

        canvas.restore()

        val baselineY = -viewBox.minY / 1000f * fontPx
        val baselineOffsetPx = -(heightPx - baselineY)

        return MathRasterizedFormula(
            image = bitmap.asImageBitmap(),
            widthPx = widthPx,
            heightPx = heightPx,
            baselineOffsetPx = baselineOffsetPx,
            fallbackText = fallbackText,
        )
    }

    private fun pixelDimension(viewBoxExtent: Float, fontPx: Float): Int? {
        val pixels = ceil(viewBoxExtent / 1000f * fontPx)
        if (!pixels.isFinite() || pixels <= 0f || pixels > MAX_PIXEL_DIMENSION) {
            return null
        }
        return pixels.toInt()
    }

    private fun cacheKey(markup: String, colorArgb: Int, fontPx: Float, fallbackText: String?): String = buildString {
        appendLengthPrefixed("markup", markup)
        appendLengthPrefixed("fallback", fallbackText ?: NULL_FALLBACK_SENTINEL)
        append("color=").append(colorArgb).append(KEY_FIELD_SEPARATOR)
        append("fontPx=").append(fontPx.toRawBits())
    }

    private fun StringBuilder.appendLengthPrefixed(name: String, value: String) {
        append(name)
            .appendLengthPrefixedValue(value)
            .append(KEY_FIELD_SEPARATOR)
    }

    private fun StringBuilder.appendLengthPrefixedValue(value: String): StringBuilder = append('(')
        .append(value.length)
        .append(")=")
        .append(value)

    private companion object {
        const val BYTES_PER_PIXEL = 4
        const val MAX_PIXEL_DIMENSION = 32_768f
        const val MAX_RENDERED_PIXEL_COUNT = 4_000_000L
        const val MAX_RENDERED_BITMAP_BYTES = MAX_RENDERED_PIXEL_COUNT.toInt() * BYTES_PER_PIXEL
        const val MAX_CACHED_BITMAP_BYTES = MAX_RENDERED_BITMAP_BYTES * 4
        const val KEY_FIELD_SEPARATOR = "|"
        const val NULL_FALLBACK_SENTINEL = "<math-null-fallback>"
    }
}

private fun MathSvgMatrix.toAndroidMatrix(): Matrix = Matrix().apply {
    setValues(
        floatArrayOf(
            a, c, tx,
            b, d, ty,
            0f, 0f, 1f,
        ),
    )
}

private fun MathSvgPath.toAndroidPath(): Path {
    val path = Path()
    for (segment in segments) {
        when (segment) {
            is MathSvgPathSegment.MoveTo -> path.moveTo(segment.x, segment.y)
            is MathSvgPathSegment.LineTo -> path.lineTo(segment.x, segment.y)
            is MathSvgPathSegment.CubicTo ->
                path.cubicTo(segment.x1, segment.y1, segment.x2, segment.y2, segment.x, segment.y)
            is MathSvgPathSegment.QuadTo ->
                path.quadTo(segment.x1, segment.y1, segment.x, segment.y)
            MathSvgPathSegment.Close -> path.close()
        }
    }
    return path
}
