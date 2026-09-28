package text.message.sms.messaging.ui.screens.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * Intro's animation must start below the top safe area -- the status bar *and* the display
 * cutout, whichever reaches lower -- plus its own top gap. The insets are synthesized and
 * dispatched straight to the content view (the same way [text.message.sms.messaging.ui.screens
 * .chat.ChatComposerImePaddingTest] fakes a keyboard), with a notch deliberately taller than the
 * status bar: exactly the phone where padding for the status bar alone would leave the animation
 * under the notch.
 */
@RunWith(AndroidJUnit4::class)
class IntroScreenInsetsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun animationStartsBelowNotchTallerThanStatusBar() {
        composeRule.setContent {
            AppTheme(darkTheme = false) {
                IntroScreenContent(nativeAdState = NativeAdState.Loading, onPageSettled = {}, onGetStarted = {})
            }
        }
        composeRule.waitForIdle()

        val density = composeRule.activity.resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).roundToInt()
        composeRule.runOnUiThread {
            val contentView = composeRule.activity.findViewById<android.view.View>(android.R.id.content)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(StatusBarDp), 0, 0))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, px(CutoutDp), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(NavBarDp)))
                .build()
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        }
        composeRule.waitForIdle()

        val animationTop = composeRule.onNodeWithTag(IntroAnimationTag).getUnclippedBoundsInRoot().top
        val safeTop = maxOf(StatusBarDp, CutoutDp).dp
        assertTrue("animation top $animationTop is inside the safe area ($safeTop)", animationTop >= safeTop)
        assertTrue(
            "animation top $animationTop leaves less than a 32dp gap below the safe area ($safeTop)",
            animationTop >= safeTop + 32.dp,
        )
        // The pager gave up the extra top space, not the bottom controls.
        composeRule.onNodeWithText("Next").assertIsDisplayed()
    }

    private companion object {
        const val StatusBarDp = 24f
        const val CutoutDp = 60f
        const val NavBarDp = 48f
    }
}
