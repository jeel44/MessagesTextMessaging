package text.message.sms.messaging.ui.screens.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
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
 */
@RunWith(AndroidJUnit4::class)
class LanguageScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_rendersListAndApply() = rendersListAndApply(darkTheme = false)

    @Test
    fun darkTheme_rendersListAndApply() = rendersListAndApply(darkTheme = true)

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
    fun applyDisabledUntilSelectionArrives() {
        var selection by mutableStateOf<String?>(null)
        composeRule.setContent {
            AppTheme(darkTheme = false) {
                LanguageScreenContent(
                    selectedLanguageId = selection,
                    nativeAdState = NativeAdState.Failed,
                    onLanguageClick = { selection = it.id },
                    onApplyClick = {},
                    onBack = null,
                )
            }
        }
        composeRule.onNodeWithText("Apply").assertIsNotEnabled()
        selection = "system"
        composeRule.onNodeWithText("Apply").assertIsEnabled()
    }

    @Test
    fun preselectedRowFarDownTheList_isScrolledIntoView() {
        setContent(darkTheme = false, initialSelection = "th")
        composeRule.waitForIdle()
        tag("th").assertIsDisplayed()
    }

    private fun rendersListAndApply(darkTheme: Boolean) {
        setContent(darkTheme = darkTheme, initialSelection = "system")

        composeRule.onNodeWithText("Apply").assertIsDisplayed().assertIsEnabled()
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
                    onLanguageClick = {},
                    onApplyClick = {},
                    onBack = null,
                )
            }
        }

        assertEquals(primary.toArgb(), cardFillArgb("en"))
        assertEquals(surface.toArgb(), cardFillArgb("nl"))
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
                    onLanguageClick = { selection = it.id },
                    onApplyClick = {},
                    onBack = null,
                )
            }
        }
    }

    private fun tag(id: String) = composeRule.onNodeWithTag(LanguageCardTagPrefix + id)

    private fun scrollTo(id: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(LanguageCardTagPrefix + id))
    }
}
