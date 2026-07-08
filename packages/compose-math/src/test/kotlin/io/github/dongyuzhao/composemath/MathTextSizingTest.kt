package io.github.dongyuzhao.composemath

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTextSizingTest {
    @Test
    fun displaySizeUsesSpFontScaleAndRenderScale() {
        val svg = MathJaxSvg(
            ok = true,
            markup = """<svg viewBox="0 -1000 2000 1000"></svg>""",
            viewBox = MathJaxViewBox(minX = 0f, minY = -1000f, width = 2000f, height = 1000f),
        )

        val base = svg.displaySize(
            fontSizeSp = 16f,
            scale = 1f,
            density = Density(density = 2f, fontScale = 1f),
        )
        val scaled = svg.displaySize(
            fontSizeSp = 16f,
            scale = 2f,
            density = Density(density = 2f, fontScale = 1.5f),
        )

        assertEquals(32f, base.width.value, 0.001f)
        assertEquals(16f, base.height.value, 0.001f)
        assertTrue(scaled.width > base.width * 2f)
        assertTrue(scaled.height > base.height * 2f)
    }
}
