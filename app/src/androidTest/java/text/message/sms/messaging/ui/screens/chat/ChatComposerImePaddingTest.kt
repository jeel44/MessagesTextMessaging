package text.message.sms.messaging.ui.screens.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ComposerTag = "chatComposer"

/**
 * Regression coverage for the type bar being hidden behind the keyboard: [ChatComposer] must move
 * up above the IME, not sit underneath it, once [text.message.sms.messaging.MainActivity]'s
 * `enableEdgeToEdge()` stops the system from reserving that space automatically -- see
 * [ChatComposer]'s doc comment for the full explanation.
 *
 * There's no real soft keyboard involved here -- showing one reliably from an instrumented test is
 * itself flaky across devices/OS versions, and this is really a layout-response question, not an
 * IME-visibility one. Instead, this dispatches a synthetic IME [WindowInsetsCompat] straight at the
 * content view, the same insets the real system would deliver once the keyboard opens, and checks
 * [ChatComposer] actually responds by moving up. This is deterministic and never depends on an
 * actual keyboard appearing.
 *
 * [ChatComposer] is hosted inside a [Box] with its real usage's own bottom alignment (matching
 * [androidx.compose.material3.Scaffold]'s `bottomBar` slot, which is what actually places it at the
 * screen's bottom in [ChatScreen]) rather than composed alone -- alone, with nothing pinning it to
 * the bottom of the screen, [Modifier.imePadding] would just grow its bounds *downward* (the
 * padding it adds still has to go somewhere), which is exactly backwards from what this is meant to
 * verify.
 */
@RunWith(AndroidJUnit4::class)
class ChatComposerImePaddingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composer_movesAboveASimulatedKeyboard() {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ChatComposer(
                    text = "",
                    onTextChange = {},
                    pendingAttachmentUri = null,
                    onRemoveAttachment = {},
                    onAttachClick = {},
                    onSendClick = {},
                    modifier = Modifier
                        .testTag(ComposerTag)
                        .align(Alignment.BottomStart),
                )
            }
        }
        composeRule.waitForIdle()

        val topWithoutKeyboard =
            composeRule.onNodeWithTag(ComposerTag).fetchSemanticsNode().boundsInRoot.top

        val simulatedImeHeightPx = 600
        composeRule.runOnUiThread {
            val contentView = composeRule.activity.findViewById<android.view.View>(android.R.id.content)
            val imeInsets = Insets.of(0, 0, 0, simulatedImeHeightPx)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), imeInsets)
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        }
        composeRule.waitForIdle()

        val topWithKeyboard =
            composeRule.onNodeWithTag(ComposerTag).fetchSemanticsNode().boundsInRoot.top
        val shiftUpPx = topWithoutKeyboard - topWithKeyboard

        assertTrue(
            "expected the composer to move up by roughly the simulated ${simulatedImeHeightPx}px " +
                "IME inset, but it only moved ${shiftUpPx}px -- it isn't responding to the " +
                "keyboard at all",
            shiftUpPx > simulatedImeHeightPx * 0.9f,
        )
    }
}
