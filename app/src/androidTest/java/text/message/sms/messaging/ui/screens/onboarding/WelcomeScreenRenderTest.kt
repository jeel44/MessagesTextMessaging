package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Crash-free rendering coverage for the Welcome screen. Exercises [WelcomeScreenContent] directly
 * (not [WelcomeScreen]) to match every other onboarding render test's split, even though this
 * screen no longer has any real side effect to keep out of the test.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersWithoutCrashing() {
        composeRule.setContent {
            WelcomeScreenContent(onContinueClick = {})
        }

        composeRule.onNodeWithText("Welcome To").assertExists()
        composeRule.onNodeWithText("Continue").assertExists()
    }
}
