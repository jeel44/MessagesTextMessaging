package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Crash-free rendering coverage for the Set-as-Default screen, now also the screen that requests
 * every other onboarding permission after the role grant. Exercises [SetDefaultSmsScreenContent]
 * directly (not [SetDefaultSmsScreen]) since the real screen wires in a Hilt-backed
 * [SetDefaultSmsViewModel] via [androidx.hilt.navigation.compose.hiltViewModel], and this module has
 * no Hilt test harness set up -- see [SetDefaultSmsScreenContent]'s doc comment.
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
                promptState = PermissionPromptState.Hidden,
                onSetDefaultClick = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Set as Default").assertExists()
    }

    @Test
    fun roleDeclinedHintShown_rendersWithoutCrashing() {
        composeRule.setContent {
            SetDefaultSmsScreenContent(
                showDeclinedHint = true,
                promptState = PermissionPromptState.Hidden,
                onSetDefaultClick = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Set as Default").assertExists()
    }

    @Test
    fun permissionsPermanentlyDenied_rendersWithoutCrashing() {
        composeRule.setContent {
            SetDefaultSmsScreenContent(
                showDeclinedHint = false,
                promptState = PermissionPromptState.PermanentlyDenied,
                onSetDefaultClick = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Set as Default").assertExists()
    }
}
