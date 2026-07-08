package io.github.dongyuzhao.composemath

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel (rasterization) snapshots for the whole corpus — one <id>.png per golden
 * SVG, matching packages/mathjax-core/test/golden/svg/<id>.svg. Runs on the JVM
 * via Roborazzi + Robolectric (no emulator, keeps AGP 9). Each formula's golden
 * SVG is parsed and rasterized through the same pure-Kotlin path the app uses,
 * without loading a JavaScript runtime.
 *
 * Record:  ./gradlew :packages:compose-math:recordRoborazziDebug
 * Verify:  ./gradlew :packages:compose-math:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MathScreenshotTest {
    @Test
    fun `corpus formulas match pixel snapshots`() {
        for (entry in goldenFormulas) {
            val markup = loadGoldenSvg(entry.id)
            captureRoboImage(filePath = "src/test/screenshots/${entry.id}.png") {
                GoldenFormula(markup, entry.fontSizeSp)
            }
        }
    }
}

// Rendered at 2x scale on the default (transparent) background; the MathJax SVG
// uses embedded vector glyphs, so this is deterministic across machines.
@Composable
private fun GoldenFormula(markup: String, fontSizeSp: Float) {
    val document = MathSvgParser.parse(markup) ?: return
    val rasterizer = MathRasterizer()
    val fontPx = fontSizeSp * 2f
    val formula = rasterizer.rasterize(
        markup = markup,
        document = document,
        colorArgb = Color.Black.toArgb(),
        fontPx = fontPx,
    ) ?: return

    val size = with(LocalDensity.current) {
        DpSize(formula.widthPx.toDp(), formula.heightPx.toDp())
    }
    Image(
        bitmap = formula.image,
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.FillBounds,
    )
}

private fun loadGoldenSvg(id: String): String {
    val stream = checkNotNull(GoldenFormulaEntry::class.java.getResourceAsStream("/golden/$id.svg")) {
        "Missing golden SVG resource for id \"$id\""
    }
    return stream.bufferedReader().use { it.readText() }
}
