package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.ui.theme.AppTheme

/**
 * Rendering coverage for the Language picker under both the app's light and dark theme. Exercises
 * [LanguageScreenContent] directly (not [LanguageScreen]) to match every other onboarding render
 * test's split -- the real screen wires in a Hilt-backed [LanguageViewModel] that requests real
 * ads, and this module has no Hilt test harness. Ads stay in their no-ad states.
 *
 * The confirm-check tests put a real [ConfirmRevealTimer] behind the screen on the composition's
 * own coroutine scope, so its 2s delay runs on the test's manually-advanced main clock -- no
 * real sleeps.
 */
@RunWith(AndroidJUnit4::class)
class LanguageScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_rendersListWithoutApplyButton() = rendersListWithoutApplyButton(darkTheme = false)

    @Test
    fun darkTheme_rendersListWithoutApplyButton() = rendersListWithoutApplyButton(darkTheme = true)

    @Test
    fun lightTheme_selectedCardIsPrimaryFilled() = selectedCardIsPrimaryFilled(darkTheme = false)

    @Test
    fun darkTheme_selectedCardIsPrimaryFilled() = selectedCardIsPrimaryFilled(darkTheme = true)

    @Test
    fun tappingCards_keepsSingleSelection() {
        setContent(darkTheme = false, initialSelection = "system")

        tag("system").assertIsSelected()

        scrollTo("ko")
        tag("ko").performClick()
        tag("ko").assertIsSelected()
        scrollTo("system")
        tag("system").assertIsNotSelected()

        scrollTo("ar")
        tag("ar").performClick()
        tag("ar").assertIsSelected()
        scrollTo("ko")
        tag("ko").assertIsNotSelected()

        LanguageOptions.filter { it.id != "ar" }.forEach {
            scrollTo(it.id)
            tag(it.id).assertIsNotSelected()
        }
    }

    @Test
    fun systemDefaultRow_hasNoNativeNameLine() {
        setContent(darkTheme = false, initialSelection = null)

        tag("system").assertIsDisplayed()
        // The card merges its texts (selectable merges descendants): only the name, no native
        // name line -- System Default's stored nativeName ("English") must not show here.
        val systemText = tag("system").fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.Text) { emptyList() }
            .map { it.text }
        assertEquals(listOf("System Default"), systemText)
    }

    @Test
    fun preselectedRowFarDownTheList_isScrolledIntoView() {
        setContent(darkTheme = false, initialSelection = "th")
        composeRule.waitForIdle()
        tag("th").assertIsDisplayed()
    }

    @Test
    fun onboarding_confirmHiddenOnOpen_evenWithPreselection() = confirmHiddenOnOpen(inSettings = false)

    @Test
    fun settings_confirmHiddenOnOpen_evenWithPreselection() = confirmHiddenOnOpen(inSettings = true)

    @Test
    fun onboarding_confirmAppearsTwoSecondsAfterSelection() = confirmAppearsAfterDelay(inSettings = false)

    @Test
    fun settings_confirmAppearsTwoSecondsAfterSelection() = confirmAppearsAfterDelay(inSettings = true)

    @Test
    fun secondSelectionWithinDelay_restartsIt() {
        setTimedContent()

        tag("system").performClick()
        advance(1_500)
        tag("en").performClick()

        // 2s after the first tap, but only 0.5s after the second: still hidden.
        advance(500 + MARGIN_MILLIS)
        confirm().assertCountEquals(0)
        advance(CONFIRM_REVEAL_DELAY_MILLIS - 500 - 2 * MARGIN_MILLIS)
        confirm().assertCountEquals(0)
        advance(2 * MARGIN_MILLIS)
        settleReveal()
        confirmNode().assertIsDisplayed()
    }

    @Test
    fun confirmStaysVisibleAfterAnotherSelection() {
        setTimedContent()

        tag("system").performClick()
        advance(CONFIRM_REVEAL_DELAY_MILLIS + MARGIN_MILLIS)
        settleReveal()
        confirmNode().assertIsDisplayed()

        tag("en").performClick()
        advance(MARGIN_MILLIS)
        confirmNode().assertIsDisplayed()
        advance(2 * CONFIRM_REVEAL_DELAY_MILLIS)
        confirmNode().assertIsDisplayed()
    }

    @Test
    fun tappingConfirm_appliesOnce() {
        var applies = 0
        setTimedContent(onConfirm = { applies++ })

        tag("en").performClick()
        advance(CONFIRM_REVEAL_DELAY_MILLIS + MARGIN_MILLIS)
        settleReveal()
        confirmNode().assertContentDescriptionEquals("Apply language")

        confirmNode().performClick()
        confirmNode().performClick()
        confirmNode().performClick()
        composeRule.waitForIdle()

        assertEquals(1, applies)
    }

    private fun rendersListWithoutApplyButton(darkTheme: Boolean) {
        setContent(darkTheme = darkTheme, initialSelection = "system")

        composeRule.onAllNodes(hasText("Apply")).assertCountEquals(0)
        LanguageOptions.forEach {
            scrollTo(it.id)
            tag(it.id).assertIsDisplayed()
        }
        scrollTo("ar")
        composeRule.onNodeWithText("العربية").assertIsDisplayed()
    }

    private fun selectedCardIsPrimaryFilled(darkTheme: Boolean) {
        var primary = Color.Unspecified
        var surface = Color.Unspecified
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                primary = MaterialTheme.colorScheme.primary
                surface = MaterialTheme.colorScheme.surface
                LanguageScreenContent(
                    selectedLanguageId = "en",
                    nativeAdState = NativeAdState.Failed,
                    confirmVisible = false,
                    onLanguageClick = {},
                    onConfirmClick = {},
                    onBack = null,
                )
            }
        }

        assertEquals(primary.toArgb(), cardFillArgb("en"))
        assertEquals(surface.toArgb(), cardFillArgb("nl"))
    }

    private fun confirmHiddenOnOpen(inSettings: Boolean) {
        setTimedContent(inSettings = inSettings, initialSelection = "system")

        tag("system").assertIsSelected()
        advance(5 * CONFIRM_REVEAL_DELAY_MILLIS)
        confirm().assertCountEquals(0)
    }

    private fun confirmAppearsAfterDelay(inSettings: Boolean) {
        setTimedContent(inSettings = inSettings)

        tag("en").performClick()
        advance(CONFIRM_REVEAL_DELAY_MILLIS - MARGIN_MILLIS)
        confirm().assertCountEquals(0)
        advance(2 * MARGIN_MILLIS)
        settleReveal()
        confirmNode().assertIsDisplayed()
    }

    /** A pixel just inside the card's bottom edge, midway across -- past the rounded corners and
     * the 1dp border, and below the text column (which is vertically centered). */
    private fun cardFillArgb(id: String): Int {
        val image = tag(id).captureToImage()
        val map = image.toPixelMap()
        return map[map.width / 2, map.height - 4].toArgb()
    }

    private fun setContent(darkTheme: Boolean, initialSelection: String?) {
        composeRule.setContent {
            var selection by remember { mutableStateOf(initialSelection) }
            AppTheme(darkTheme = darkTheme) {
                LanguageScreenContent(
                    selectedLanguageId = selection,
                    nativeAdState = NativeAdState.Loading,
                    confirmVisible = false,
                    onLanguageClick = { selection = it.id },
                    onConfirmClick = {},
                    onBack = null,
                )
            }
        }
    }

    /** The screen with a real [ConfirmRevealTimer] behind it, wired the way [LanguageViewModel]
     * wires it: taps start the reveal, the initial (pre)selection doesn't. The main clock is
     * manual from here on, so only [advance] moves time. */
    private fun setTimedContent(
        inSettings: Boolean = false,
        initialSelection: String? = "system",
        onConfirm: () -> Unit = {},
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            val timer = remember { ConfirmRevealTimer(scope) }
            val visible by timer.visible.collectAsState()
            var selection by remember { mutableStateOf(initialSelection) }
            AppTheme(darkTheme = false) {
                LanguageScreenContent(
                    selectedLanguageId = selection,
                    nativeAdState = NativeAdState.Failed,
                    confirmVisible = visible,
                    onLanguageClick = {
                        selection = it.id
                        timer.onSelection()
                    },
                    onConfirmClick = onConfirm,
                    onBack = if (inSettings) ({}) else null,
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun advance(millis: Long) = composeRule.mainClock.advanceTimeBy(millis)

    /** Runs the check's short fade/scale-in to the end, once the reveal itself has fired. */
    private fun settleReveal() = advance(REVEAL_ANIMATION_BUDGET_MILLIS)

    private fun confirm() = composeRule.onAllNodesWithTag(LanguageConfirmTag)

    private fun confirmNode() = composeRule.onNodeWithTag(LanguageConfirmTag)

    private fun tag(id: String) = composeRule.onNodeWithTag(LanguageCardTagPrefix + id)

    private fun scrollTo(id: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(LanguageCardTagPrefix + id))
    }

    private companion object {
        /** Slack either side of the 2s mark, for the frame-quantized test clock. */
        const val MARGIN_MILLIS = 100L
        const val REVEAL_ANIMATION_BUDGET_MILLIS = 500L
    }
}
