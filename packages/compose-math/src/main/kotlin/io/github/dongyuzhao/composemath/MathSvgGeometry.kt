package io.github.dongyuzhao.composemath

/**
 * A 2D affine transform using the same component layout and composition semantics as Core
 * Graphics' `CGAffineTransform`, so the native SVG pipeline mirrors the iOS implementation.
 *
 * A point `(x, y)` is mapped to `(a*x + c*y + tx, b*x + d*y + ty)`.
 */
data class MathSvgMatrix(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float) {
    /** Returns the transform that applies `this` first and then [other] (`this * other`). */
    fun concatenating(other: MathSvgMatrix): MathSvgMatrix = MathSvgMatrix(
        a = a * other.a + b * other.c,
        b = a * other.b + b * other.d,
        c = c * other.a + d * other.c,
        d = c * other.b + d * other.d,
        tx = tx * other.a + ty * other.c + other.tx,
        ty = tx * other.b + ty * other.d + other.ty,
    )

    companion object {
        val Identity = MathSvgMatrix(1f, 0f, 0f, 1f, 0f, 0f)

        fun translation(x: Float, y: Float): MathSvgMatrix = MathSvgMatrix(1f, 0f, 0f, 1f, x, y)

        fun scale(x: Float, y: Float): MathSvgMatrix = MathSvgMatrix(x, 0f, 0f, y, 0f, 0f)

        fun of(a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float): MathSvgMatrix =
            MathSvgMatrix(a, b, c, d, tx, ty)
    }
}

/** A single drawing instruction of a parsed SVG path, in the path's local coordinate space. */
sealed interface MathSvgPathSegment {
    data class MoveTo(val x: Float, val y: Float) : MathSvgPathSegment
    data class LineTo(val x: Float, val y: Float) : MathSvgPathSegment
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) :
        MathSvgPathSegment

    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : MathSvgPathSegment

    data object Close : MathSvgPathSegment
}

/** A resolution-independent vector path built from [MathSvgPathSegment]s. */
data class MathSvgPath(val segments: List<MathSvgPathSegment>)

/** A filled path plus the transform that maps its local coordinates into viewBox space. */
data class MathSvgDrawCommand(val path: MathSvgPath, val transform: MathSvgMatrix)

/** A run of text plus its baseline position and the transform into viewBox space. */
data class MathSvgTextCommand(val text: String, val x: Float, val y: Float, val transform: MathSvgMatrix)

/** A fully parsed MathJax SVG document ready to rasterize. */
data class MathSvgDocument(
    val viewBox: MathJaxViewBox,
    val drawCommands: List<MathSvgDrawCommand>,
    val textCommands: List<MathSvgTextCommand>,
)
