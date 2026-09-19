package text.message.sms.messaging.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ShineButton] click and disabled-state coverage. The halo/shine animations themselves aren't
 * asserted here -- they're purely decorative and continuous, not something a single-frame
 * semantics tree can observe -- but every render path (animated, reduced-motion, disabled) is
 * exercised at least once so a crash in any of them fails this test.
 */
@RunWith(AndroidJUnit4::class)
class ShineButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun click_firesOnClickExactlyOnce() {
        var clickCount = 0

        composeRule.setContent {
            ShineButton(text = "Continue", onClick = { clickCount++ })
        }

        composeRule.onNodeWithText("Continue").performClick()

        assertEquals(1, clickCount)
    }

    @Test
    fun disabled_neverFiresOnClick() {
        var clickCount = 0

        composeRule.setContent {
            ShineButton(text = "Continue", onClick = { clickCount++ }, enabled = false)
        }

        composeRule.onNodeWithText("Continue").performClick()

        assertEquals(0, clickCount)
    }

    @Test
    fun showHaloFalse_stillRendersAndClicks() {
        var clickCount = 0

        composeRule.setContent {
            ShineButton(text = "Set as Default", onClick = { clickCount++ }, showHalo = false)
        }

        composeRule.onNodeWithText("Set as Default").performClick()

        assertEquals(1, clickCount)
    }
}
