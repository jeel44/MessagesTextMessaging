package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Crash-free rendering coverage for the recreated Welcome screen. Exercises
 * [WelcomeScreenContent] directly (not [WelcomeScreen]) since the real screen wires in a
 * Hilt-backed [WelcomeViewModel] via [androidx.hilt.navigation.compose.hiltViewModel], and this
 * module has no Hilt test harness set up -- see [WelcomeScreenContent]'s doc comment.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalState_rendersWithoutCrashing() {
        composeRule.setContent {
            WelcomeScreenContent(
                promptState = PermissionPromptState.Hidden,
                onContinueClick = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Welcome To").assertExists()
        composeRule.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun permanentlyDeniedState_rendersWithoutCrashing() {
        composeRule.setContent {
            WelcomeScreenContent(
                promptState = PermissionPromptState.PermanentlyDenied,
                onContinueClick = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Continue").assertExists()
    }
}
