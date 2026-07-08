package io.github.dongyuzhao.composemathsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dongyuzhao.composemath.MathDisplayMode
import io.github.dongyuzhao.composemath.MathRenderOptions
import io.github.dongyuzhao.composemath.MathText

private const val BODY_FONT_SP = 18f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MathSampleScreen()
                }
            }
        }
    }
}

@Composable
private fun MathSampleScreen() {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Compose Math",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Android rendering backed by the shared MathJax core, in the three modes we " +
                "name across the stack: embedded, standalone, and block.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        EmbeddedSection()
        StandaloneSection()
        BlockSection()
    }
}

// Embedded: inline `$…$`, standalone = false — sized to the surrounding text and
// sitting on its baseline. (A full document renderer flows these inside wrapping
// paragraphs via AnnotatedString + InlineTextContent; here each fragment ends at
// its formula, since a Row cannot reflow a composable mid-sentence.)
@Composable
private fun EmbeddedSection() {
    SectionHeader("1 · Embedded")
    Text(
        text = "Embedded math is sized to the surrounding text and sits on its baseline:",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EmbeddedRow("For a right triangle,", "a^2 + b^2 = c^2")
    EmbeddedRow("Einstein showed", "E = mc^2")
    EmbeddedRow("Water forms via", "\\ce{2H2 + O2 -> 2H2O}")
}

@Composable
private fun EmbeddedRow(lead: String, tex: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = lead,
            modifier = Modifier.alignByBaseline(),
            fontSize = BODY_FONT_SP.sp,
        )
        MathText(
            tex = tex,
            modifier = Modifier.alignByBaseline(),
            options = MathRenderOptions(displayMode = MathDisplayMode.Inline, fontSizeSp = BODY_FONT_SP),
        )
    }
}

// Standalone: `$$…$$`, standalone = true — display style, isolated on its own line in-flow.
@Composable
private fun StandaloneSection() {
    SectionHeader("2 · Standalone")
    Text(
        text = "A standalone formula is rendered block-style on its own centered line:",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CenteredDisplay("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")
    CenteredDisplay("\\sum_{n=1}^{\\infty} \\frac{1}{n^2} = \\frac{\\pi^2}{6}")
}

@Composable
private fun CenteredDisplay(tex: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MathText(
            tex = tex,
            options = MathRenderOptions(displayMode = MathDisplayMode.Block, fontSizeSp = 24f),
        )
    }
}

// Block: a standalone formula that is the sole content of its block, promoted to a block element.
@Composable
private fun BlockSection() {
    SectionHeader("3 · Block")
    Text(
        text = "The same display render, presented on its own as a block-level figure:",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MathText(
                tex = "e^{i\\pi} + 1 = 0",
                options = MathRenderOptions(displayMode = MathDisplayMode.Block, fontSizeSp = 34f),
            )
            Text(
                text = "Euler's identity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}
