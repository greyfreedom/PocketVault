package com.turisla.hellopocket.ui.feature.passwordGenerator

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.ui.theme.HelloPocketTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "zh-rCN-w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratorRulePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectingRuleCardsTogglesOnceAndAddsRulesInSelectionOrder() {
        val rules = listOf(
            GeneratorRule("word", "固定开头", GeneratorSegment.Text("Maple")),
            GeneratorRule("symbol", "分隔符", GeneratorSegment.Text("@")),
            GeneratorRule("digits", "随机尾缀", GeneratorSegment.Digits(6, avoidConfusing = true)),
        )
        var added = emptyList<GeneratorRule>()
        compose.setContent {
            HelloPocketTheme {
                GeneratorRulePicker(
                    rules = rules, busy = false,
                    onDismiss = {}, onAdd = { added = it }, onNew = {},
                    onEdit = {}, onDuplicate = {}, onDelete = {},
                )
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("随机尾缀"))
        compose.onNodeWithText("随机尾缀").performClick().assertIsOn()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("固定开头"))
        compose.onNodeWithText("固定开头").performClick().assertIsOn()
        compose.onNodeWithText("固定开头").performClick().assertIsOff()
        compose.onNodeWithText("固定开头").performClick().assertIsOn()
        val addLabel = RuntimeEnvironment.getApplication().getString(R.string.generator_add_selected)
        compose.onNodeWithText(addLabel).performClick()
        compose.runOnIdle { assertEquals(listOf(rules[2], rules[0]), added) }
    }
}
