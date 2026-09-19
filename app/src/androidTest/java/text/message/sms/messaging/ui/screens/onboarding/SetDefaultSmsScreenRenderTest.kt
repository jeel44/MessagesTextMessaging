package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Crash-free rendering coverage for the recreated Set-as-Default screen. Exercises
 * [SetDefaultSmsScreenContent] directly (not [SetDefaultSmsScreen]) since the real screen wires in
 * a Hilt-backed [SetDefaultSmsViewModel] via [androidx.hilt.navigation.compose.hiltViewModel], and
 * this module has no Hilt test harness set up -- see [SetDefaultSmsScreenContent]'s doc comment.
 */
@RunWith(AndroidJUnit4::class)
class SetDefaultSmsScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalState_rendersWithoutCrashing() {
        composeRule.setContent {
            SetDefaultSmsScreenContent(
                showDeclinedHint = false,
                onSetDefaultClick = {},
            )
        }

        composeRule.onNodeWithText("Set as Default").assertExists()
    }

    @Test
    fun declinedHintShown_rendersWithoutCrashing() {
        composeRule.setContent {
            SetDefaultSmsScreenContent(
                showDeclinedHint = true,
                onSetDefaultClick = {},
            )
        }

        composeRule.onNodeWithText("Set as Default").assertExists()
    }
}
