package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.ui.theme.AppTheme

/**
 * Rendering coverage for Intro under both the app's light and dark theme. Exercises
 * [IntroScreenContent] directly (not [IntroScreen]) to match every other onboarding render test's
 * split -- the real screen wires in a Hilt-backed [IntroViewModel] that requests real ads, and this
 * module has no Hilt test harness.
 *
 * Each theme is forced through [AppTheme]'s own `darkTheme` parameter -- the same one MainActivity
 * feeds from the theme picker -- so these cover exactly what a user in either mode sees. Ads are
 * only ever in their no-ad states here ([NativeAdState.Loading]/[NativeAdState.Failed]): a loaded
 * [com.google.android.gms.ads.nativead.NativeAd] can't be built outside the ads SDK.
 */
@RunWith(AndroidJUnit4::class)
class IntroScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_firstSlide_rendersWithoutCrashing() = firstSlideRenders(darkTheme = false)

    @Test
    fun darkTheme_firstSlide_rendersWithoutCrashing() = firstSlideRenders(darkTheme = true)

    @Test
    fun lightTheme_lastSlide_showsGetStarted() = lastSlideShowsGetStarted(darkTheme = false)

    @Test
    fun darkTheme_lastSlide_showsGetStarted() = lastSlideShowsGetStarted(darkTheme = true)

    @Test
    fun lightTheme_textContrastIsReadable() = textContrastIsReadable(darkTheme = false)

    @Test
    fun darkTheme_textContrastIsReadable() = textContrastIsReadable(darkTheme = true)

    @Test
    fun nextAdvancesSlides_andBackStepsToPreviousSlide() {
        val settled = mutableListOf<Int>()
        composeRule.setContent {
            AppTheme(darkTheme = false) {
                IntroScreenContent(
                    nativeAdState = NativeAdState.Loading,
                    onPageSettled = { settled += it },
                    onGetStarted = {},
                )
            }
        }

        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Swipe to act").assertExists()

        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Schedule messages").assertExists()
        assertEquals(listOf(0, 1, 0), settled)
    }

    @Test
    fun getStartedOnLastSlide_callsFinish() {
        var finished = 0
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                IntroScreenContent(
                    nativeAdState = NativeAdState.Failed,
                    onPageSettled = {},
                    onGetStarted = { finished++ },
                    pagerState = rememberPagerState(initialPage = IntroPageCount - 1) { IntroPageCount },
                )
            }
        }

        composeRule.onNodeWithText("Get started").performClick()
        assertEquals(1, finished)
    }

    private fun firstSlideRenders(darkTheme: Boolean) {
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                IntroScreenContent(nativeAdState = NativeAdState.Loading, onPageSettled = {}, onGetStarted = {})
            }
        }

        composeRule.onNodeWithText("Schedule messages").assertExists()
        composeRule.onNodeWithText("Pick a time and it sends for you, right on schedule").assertExists()
        composeRule.onNodeWithText("Next").assertExists()
    }

    private fun lastSlideShowsGetStarted(darkTheme: Boolean) {
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                IntroScreenContent(
                    nativeAdState = NativeAdState.Failed,
                    onPageSettled = {},
                    onGetStarted = {},
                    pagerState = rememberPagerState(initialPage = IntroPageCount - 1) { IntroPageCount },
                )
            }
        }

        composeRule.onNodeWithText("Light or dark").assertExists()
        composeRule.onNodeWithText("Get started").assertExists()
    }

    /** The token pairs IntroScreen draws text with, checked against WCAG AA (4.5:1) in the given
     * theme -- a render test can't judge contrast by eye, but it can check the colors it's given. */
    private fun textContrastIsReadable(darkTheme: Boolean) {
        lateinit var pairs: Map<String, Pair<Color, Color>>
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                val scheme = MaterialTheme.colorScheme
                pairs = mapOf(
                    "title" to (scheme.onBackground to scheme.background),
                    "subtitle" to (scheme.onSurfaceVariant to scheme.background),
                    "button" to (scheme.onPrimary to scheme.primary),
                )
            }
        }
        composeRule.waitForIdle()

        pairs.forEach { (name, colors) ->
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue("$name contrast $ratio < 4.5 (darkTheme=$darkTheme)", ratio >= 4.5f)
        }
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
