package io.github.dongyuzhao.mathrenderer

import android.graphics.*
import android.util.LruCache
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory
import kotlin.math.ceil

// ── Result type ───────────────────────────────────────────────────────────────

/** A rasterised formula image ready for embedding in text. */
data class FormulaImage(
    val bitmap: Bitmap,
    /** Logical size in dp (density-independent pixels). */
    val widthDp: Float,
    val heightDp: Float,
    /** Positive offset (in dp) from the image bottom to the text baseline. */
    val baselineOffsetDp: Float,
    val fallbackText: String? = null,
)

// ── Internal SVG document model ───────────────────────────────────────────────

internal data class SVGDocument(
    val viewBox: RectF,
    val drawCommands: List<DrawCommand>,
)

internal data class DrawCommand(val path: Path, val transform: Matrix)

// ── Rasterizer ────────────────────────────────────────────────────────────────

/**
 * Converts a [MathJaxSVG] into a [FormulaImage] using Android Canvas/Path.
 *
 * @param density  Screen density (from `DisplayMetrics.density`).
 */
class FormulaRasterizer(private val density: Float = 1f) {

    private val cache = LruCache<String, FormulaImage>(16)

    companion object {
        private const val MAX_SVG_BYTES = 256_000
        private const val MAX_BITMAP_PIXELS = 4_000_000
    }

    /**
     * Rasterises [svg] to a [FormulaImage].
     *
     * @param color     Fill colour (ARGB int); defaults to black.
     * @param scale     Additional scale multiplier.
     * @param fontSizeDp Reference font size in dp.
     */
    fun image(
        svg: MathJaxSVG,
        color: Int = Color.BLACK,
        scale: Float = density,
        fontSizeDp: Float = 16f,
    ): FormulaImage? {
        if (svg.markup.toByteArray(Charsets.UTF_8).size > MAX_SVG_BYTES) return null

        val key = "${svg.markup.hashCode()}|$color|$scale|$fontSizeDp"
        cache.get(key)?.let { return it }

        val doc = parseSVG(svg.markup) ?: return null
        if (doc.viewBox.width() <= 0f || doc.viewBox.height() <= 0f) return null

        // MathJax SVG units are 1000-unit ems → convert to dp
        val widthDp  = ceil(doc.viewBox.width()  / 1000f * fontSizeDp)
        val heightDp = ceil(doc.viewBox.height() / 1000f * fontSizeDp)
        val baselineOffsetDp = -doc.viewBox.top / 1000f * fontSizeDp

        val widthPx  = ceil(widthDp  * scale).toInt()
        val heightPx = ceil(heightDp * scale).toInt()
        if (widthPx <= 0 || heightPx <= 0 || widthPx * heightPx > MAX_BITMAP_PIXELS) return null

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        val unitScale = fontSizeDp * scale / 1000f
        canvas.save()
        canvas.scale(unitScale, unitScale)
        canvas.translate(-doc.viewBox.left, -doc.viewBox.top)

        for (cmd in doc.drawCommands) {
            canvas.save()
            canvas.concat(cmd.transform)
            canvas.drawPath(cmd.path, paint)
            canvas.restore()
        }

        canvas.restore()

        val result = FormulaImage(
            bitmap = bitmap,
            widthDp = widthDp,
            heightDp = heightDp,
            baselineOffsetDp = baselineOffsetDp,
            fallbackText = svg.fallbackText,
        )
        cache.put(key, result)
        return result
    }

    // ── SAX-based SVG parser ─────────────────────────────────────────────────

    private fun parseSVG(markup: String): SVGDocument? {
        return try {
            val handler = SVGHandler()
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            factory.newSAXParser().parse(markup.byteInputStream(Charsets.UTF_8), handler)
            handler.document
        } catch (_: Exception) { null }
    }

    private inner class SVGHandler : DefaultHandler() {
        private var viewBox: RectF? = null
        private val paths = mutableMapOf<String, Path>()
        private val drawCommands = mutableListOf<DrawCommand>()
        private val transformStack = ArrayDeque<Matrix>().apply { addLast(Matrix()) }
        private var defsDepth = 0
        private var rejected = false

        val document: SVGDocument?
            get() = viewBox?.let { SVGDocument(it, drawCommands) }

        private val currentTransform get() = transformStack.last()

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            if (rejected) return
            val el = (localName.ifEmpty { qName }).lowercase().removePrefix("svg:")

            when (el) {
                "svg" -> {
                    val vbStr = attrs.getValue("viewBox") ?: attrs.getValue("viewbox") ?: return
                    viewBox = parseViewBox(vbStr)
                }
                "defs" -> defsDepth++
                "g" -> {
                    val xf = Matrix(currentTransform)
                    parseTransform(attrs.getValue("transform"))?.let { xf.preConcat(it) }
                    transformStack.addLast(xf)
                }
                "path" -> {
                    val d = attrs.getValue("d") ?: return
                    val path = SVGPathParser.parse(d) ?: return
                    val id = attrs.getValue("id")
                    if (id != null) paths[id] = path
                    if (defsDepth == 0) {
                        val xf = Matrix(currentTransform)
                        parseTransform(attrs.getValue("transform"))?.let { xf.preConcat(it) }
                        drawCommands.add(DrawCommand(path, xf))
                    }
                }
                "use" -> {
                    if (defsDepth > 0) return
                    val href = (attrs.getValue("href") ?: attrs.getValue("xlink:href"))?.removePrefix("#") ?: return
                    val path = paths[href] ?: return
                    val xf = Matrix(currentTransform)
                    val tx = attrs.getValue("x")?.toFloatOrNull() ?: 0f
                    val ty = attrs.getValue("y")?.toFloatOrNull() ?: 0f
                    xf.preTranslate(tx, ty)
                    parseTransform(attrs.getValue("transform"))?.let { xf.preConcat(it) }
                    drawCommands.add(DrawCommand(path, xf))
                }
                "rect" -> {
                    if (defsDepth > 0) return
                    val x = attrs.getValue("x")?.toFloatOrNull() ?: 0f
                    val y = attrs.getValue("y")?.toFloatOrNull() ?: 0f
                    val w = attrs.getValue("width")?.toFloatOrNull() ?: return
                    val h = attrs.getValue("height")?.toFloatOrNull() ?: return
                    val path = Path().also { it.addRect(x, y, x + w, y + h, Path.Direction.CW) }
                    val xf = Matrix(currentTransform)
                    parseTransform(attrs.getValue("transform"))?.let { xf.preConcat(it) }
                    drawCommands.add(DrawCommand(path, xf))
                }
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val el = (localName.ifEmpty { qName }).lowercase().removePrefix("svg:")
            when (el) {
                "defs" -> defsDepth = maxOf(0, defsDepth - 1)
                "g" -> if (transformStack.size > 1) transformStack.removeLast()
            }
        }

        private fun parseViewBox(value: String): RectF? {
            val parts = value.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            return if (parts.size == 4) RectF(parts[0], parts[1], parts[0] + parts[2], parts[1] + parts[3]) else null
        }

        private fun parseTransform(value: String?): Matrix? {
            if (value.isNullOrBlank()) return null
            val m = Matrix()
            val pattern = Regex("""(\w+)\s*\(([^)]*)\)""")
            for (match in pattern.findAll(value)) {
                val fn = match.groupValues[1]
                val args = match.groupValues[2].split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                when (fn) {
                    "matrix" -> if (args.size == 6)
                        m.preConcat(Matrix().apply { setValues(floatArrayOf(args[0],args[2],args[4],args[1],args[3],args[5],0f,0f,1f)) })
                    "translate" -> m.preTranslate(args.getOrElse(0){0f}, args.getOrElse(1){0f})
                    "scale" -> m.preScale(args.getOrElse(0){1f}, args.getOrElse(1){args.getOrElse(0){1f}})
                    "rotate" -> if (args.isNotEmpty()) {
                        val angle = args[0]
                        if (args.size == 3) {
                            m.preTranslate(args[1], args[2]); m.preRotate(angle); m.preTranslate(-args[1], -args[2])
                        } else m.preRotate(angle)
                    }
                }
            }
            return m
        }
    }
}
