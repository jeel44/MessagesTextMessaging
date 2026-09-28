package text.message.sms.messaging.ui.components.ads

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.config.IntroAdConfig
import text.message.sms.messaging.ui.theme.AppTheme

/**
 * [FullScreenNativeAdFrame] + [FullScreenNativeAdAssets] with stand-in assets -- a loaded
 * [com.google.android.gms.ads.nativead.NativeAd] can't be built outside the ads SDK, so the real
 * [FullScreenNativeAdCard] isn't rendered here; this covers the layout and the close/back rules it
 * shares with it. Timing runs on the paused test clock, never real sleeps.
 *
 * Insets are zero in tests, so sizes that stand for a real screen subtract its bars up front
 * (24dp status bar, 48dp 3-button nav in portrait, 24dp gesture nav in landscape).
 */
@RunWith(AndroidJUnit4::class)
class FullScreenNativeAdFrameTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val closeDelay = IntroAdConfig.CLOSE_BUTTON_DELAY_MILLIS

    @Test
    fun lightTheme_rendersAssetsAndClose() = renders(darkTheme = false)

    @Test
    fun darkTheme_rendersAssetsAndClose() = renders(darkTheme = true)

    @Test
    fun smallPhone360x640_ctaAndCloseOnScreen() = ctaAndCloseOnScreen(360.dp, 640.dp - 72.dp)

    @Test
    fun tallPhone412x915_ctaAndCloseOnScreen() = ctaAndCloseOnScreen(412.dp, 915.dp - 72.dp)

    @Test
    fun landscape915x412_ctaAndCloseOnScreen() = ctaAndCloseOnScreen(915.dp, 412.dp - 48.dp)

    @Test
    fun tablet800x1280_ctaAndCloseOnScreen() = ctaAndCloseOnScreen(800.dp, 1280.dp - 72.dp)

    @Test
    fun closeButton_hiddenBeforeDelay_visibleAfter_andStays() {
        composeRule.mainClock.autoAdvance = false
        setFrame()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(FullScreenNativeAdCloseTag).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(closeDelay - 200)
        composeRule.onNodeWithTag(FullScreenNativeAdCloseTag).assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithTag(FullScreenNativeAdCloseTag)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Close ad")

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.onNodeWithTag(FullScreenNativeAdCloseTag).assertIsDisplayed()
    }

    /** The timer starts when the ad is on screen, not when it was requested: while the assets
     * haven't settled, the shimmer stays and the close button never comes. */
    @Test
    fun assetsNotReady_showsShimmer_andCloseTimerDoesNotStart() {
        composeRule.mainClock.autoAdvance = false
        setFrame(assetsReady = false)
        composeRule.mainClock.advanceTimeBy(closeDelay * 3)

        composeRule.onNodeWithTag(FullScreenNativeAdShimmerTag).assertExists()
        composeRule.onNodeWithTag(FullScreenNativeAdCloseTag).assertDoesNotExist()
    }

    @Test
    fun assetsReady_shimmerGoes() {
        composeRule.mainClock.autoAdvance = false
        setFrame()
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag(FullScreenNativeAdShimmerTag).assertDoesNotExist()
        composeRule.onNodeWithTag(CtaTag).assertIsDisplayed()
    }

    @Test
    fun backPress_blockedBeforeClose_thenClosesAfter() {
        var closes = 0
        composeRule.mainClock.autoAdvance = false
        setFrame(onClose = { closes++ })
        composeRule.mainClock.advanceTimeBy(closeDelay - 200)

        pressBack()
        assertEquals(0, closes)
        assertFalse("back finished the activity under the ad", composeRule.activity.isFinishing)

        composeRule.mainClock.advanceTimeBy(400)
        pressBack()
        assertEquals(1, closes)
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun closeButton_click_closes() {
        var closes = 0
        composeRule.mainClock.autoAdvance = false
        setFrame(onClose = { closes++ })
        composeRule.mainClock.advanceTimeBy(closeDelay + 200)

        composeRule.onNodeWithTag(FullScreenNativeAdCloseTag).performClick()
        assertEquals(1, closes)
    }

    private fun renders(darkTheme: Boolean) {
        composeRule.mainClock.autoAdvance = false
        setFrame(darkTheme = darkTheme)
        composeRule.mainClock.advanceTimeBy(closeDelay + 200)

        composeRule.onNodeWithText("Ad").assertIsDisplayed()
        composeRule.onNodeWithText(Headline).assertIsDisplayed()
        composeRule.onNodeWithTag(MediaTag).assertIsDisplayed()
        composeRule.onNodeWithTag(CtaTag).assertIsDisplayed()
        composeRule.onNodeWithTag(FullScreenNativeAdCloseTag).assertIsDisplayed()
    }

    private fun ctaAndCloseOnScreen(width: Dp, height: Dp) {
        composeRule.mainClock.autoAdvance = false
        setFrame(size = width to height)
        composeRule.mainClock.advanceTimeBy(closeDelay + 200)

        val screen = DpRect(0.dp, 0.dp, width, height)
        val cta = composeRule.onNodeWithTag(CtaTag).getUnclippedBoundsInRoot()
        val close = composeRule.onNodeWithTag(FullScreenNativeAdCloseTag).getUnclippedBoundsInRoot()
        val media = composeRule.onNodeWithTag(MediaTag).getUnclippedBoundsInRoot()
        assertInside("CTA", cta, screen)
        assertInside("close", close, screen)
        assertTrue("close is ${close.width} wide, under 48dp", close.width >= 48.dp && close.height >= 48.dp)
        assertTrue("media ${media.bottom} runs into the CTA ${cta.top}", media.bottom <= cta.top)
        assertTrue("close ${close.bottom} overlaps the media ${media.top}", close.bottom <= media.top)
        assertTrue("media has no height", media.height > 0.dp)
    }

    private fun assertInside(name: String, rect: DpRect, screen: DpRect) {
        assertTrue(
            "$name $rect is not inside the ${screen.right}x${screen.bottom} screen",
            rect.left >= screen.left && rect.top >= screen.top &&
                rect.right <= screen.right + 0.5.dp && rect.bottom <= screen.bottom + 0.5.dp,
        )
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun setFrame(
        darkTheme: Boolean = false,
        assetsReady: Boolean = true,
        onClose: () -> Unit = {},
        size: Pair<Dp, Dp>? = null,
    ) {
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                // requiredSize, anchored top-start: a landscape or tablet size is wider/taller than
                // the test device's own window, and plain size() would be clamped to it.
                val boxModifier = size?.let { (w, h) ->
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(w, h)
                } ?: Modifier.fillMaxSize()
                Box(modifier = boxModifier) {
                    FullScreenNativeAdFrame(closeDelayMillis = closeDelay, onClose = onClose) { onAssetsReady ->
                        if (assetsReady) LaunchedEffect(Unit) { onAssetsReady() }
                        StandInAssets()
                    }
                }
            }
        }
    }

    @Composable
    private fun StandInAssets() {
        FullScreenNativeAdAssets(
            icon = { Box(modifier = Modifier.fillMaxSize()) },
            headline = { Text(Headline) },
            media = { Box(modifier = Modifier.fillMaxSize().testTag(MediaTag)) },
            body = { lines -> Text(text = "Body text ".repeat(20), maxLines = lines) },
            cta = { Box(modifier = Modifier.fillMaxSize().testTag(CtaTag)) { Text("Install") } },
        )
    }

    private companion object {
        const val Headline = "Stand-in headline"
        const val MediaTag = "stand_in_media"
        const val CtaTag = "stand_in_cta"
    }
}
