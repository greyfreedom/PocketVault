package com.turisla.hellopocket.ui.feature.passwordGenerator

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratorClipboardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    // API 27 避开 Robolectric 缺失的 Magnifier 原生窗口，仍执行真实的文本选择与复制。
    @Config(sdk = [27])
    fun systemSelectionCopyMarksThePasswordSensitiveAndClearsItAfterSixtySeconds() {
        val context = RuntimeEnvironment.getApplication()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val helper = ClipboardManagerHelper(context)
        val copies = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                GeneratorClipboardProvider(onCopy = { copies += it; helper.copyTextToClipboard(R.string.password, it) }) {
                    GeneratorPasswordText("MapleSecret")
                }
            }
        }
        compose.onNodeWithText("MapleSecret").performTouchInput { longClick() }
        compose.onNodeWithText("MapleSecret").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.C)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertTrue(copies.isNotEmpty() && copies.all { it == "MapleSecret" })
            assertEquals("MapleSecret", clipboard.primaryClip?.getItemAt(0)?.text.toString())
            assertTrue(clipboard.primaryClip!!.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        assertTrue(clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty())
    }

    @Test
    fun ruleTextCopyIsProtectedAndExternalTextCanStillBePasted() {
        val context = RuntimeEnvironment.getApplication()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val helper = ClipboardManagerHelper(context)
        val copies = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                GeneratorClipboardProvider(onCopy = { copies += it; helper.copyTextToClipboard(R.string.password, it) }) {
                    GeneratorRuleEditor(
                        initial = GeneratorRule("word", "Word", GeneratorSegment.Text("Maple")),
                        libraryOnly = false, busy = false, onDismiss = {}, onSave = { _, _ -> },
                    )
                }
            }
        }
        val field = compose.onNodeWithText(context.getString(R.string.generator_fixed_text))
        field.performClick()
        field.performSemanticsAction(SemanticsActions.SetSelection) { it(0, 5, false) }
        field.performSemanticsAction(SemanticsActions.CopyText) { it() }
        compose.runOnIdle {
            assertEquals(listOf("Maple"), copies)
            assertEquals("Maple", clipboard.primaryClip?.getItemAt(0)?.text.toString())
            assertTrue(clipboard.primaryClip!!.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))
            helper.clearSensitiveClipboardIfOwned()
            assertNull(clipboard.primaryClip)
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "Oak"))
        }
        field.performSemanticsAction(SemanticsActions.SetSelection) { it(0, 5, false) }
        field.performSemanticsAction(SemanticsActions.PasteText) { it() }
        field.assertTextContains("Oak")
    }
}
