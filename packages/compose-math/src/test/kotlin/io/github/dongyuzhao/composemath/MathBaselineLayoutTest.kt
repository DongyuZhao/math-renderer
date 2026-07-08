package io.github.dongyuzhao.composemath

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MathBaselineLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mathBaselinePublishesAlignmentLine() {
        val boxSize = with(composeRule.density) { 10.toDp() }
        val expectedMathTop = with(composeRule.density) { 5.toDp() }

        composeRule.setContent {
            Row {
                Box(
                    modifier = Modifier
                        .alignBy { 8 }
                        .size(boxSize)
                        .testTag("reference"),
                )
                Box(
                    modifier = Modifier
                        .alignByBaseline()
                        .mathBaseline(3)
                        .size(boxSize)
                        .testTag("math"),
                )
            }
        }

        composeRule.onNodeWithTag("reference").assertTopPositionInRootIsEqualTo(0.dp)
        composeRule.onNodeWithTag("math").assertTopPositionInRootIsEqualTo(expectedMathTop)
    }
}
