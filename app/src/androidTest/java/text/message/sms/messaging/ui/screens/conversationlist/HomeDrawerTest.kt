package text.message.sms.messaging.ui.screens.conversationlist

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.R
import text.message.sms.messaging.ui.theme.AppTheme
import java.util.Locale

/**
 * [HomeDrawer] (Archived, Blocked, Scheduled, Language) around the inbox's real [ConversationListTopBar], so "open" goes through the actual
 * hamburger. Drives [HomeDrawer] rather than the full inbox, whose Hilt ViewModel this module has
 * no test harness for -- [ConversationListScreen] passes `enabled = !isSelectionMode`, which
 * [selectionMode] stands in for here. Locale tests swap the composition's resources and layout
 * direction (see [Localized]) rather than the device language.
 */
@RunWith(AndroidJUnit4::class)
class HomeDrawerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var drawerState: DrawerState
    private var selectionMode by mutableStateOf(false)
    private val clicks = mutableMapOf("archived" to 0, "blocked" to 0, "scheduled" to 0, "language" to 0)

    @Test
    fun lightTheme_hamburgerOpensDrawerWithItemsAndVersion() = opensWithItemsAndVersion(darkTheme = false)

    @Test
    fun darkTheme_hamburgerOpensDrawerWithItemsAndVersion() = opensWithItemsAndVersion(darkTheme = true)

    @Test
    fun german_headerShowsUntranslatedMessages_notTranslatedAppName() {
        val german = localizedContext(Locale.GERMAN)
        val translatedAppName = german.getString(R.string.app_name)
        assertNotEquals("app_name is translated in German", "#Messages", translatedAppName)
        setDrawer(locale = Locale.GERMAN)
        openFromHamburger(menu = german.getString(R.string.action_menu))

        composeRule.onNodeWithText("Messages").assertIsDisplayed()
        composeRule.onNodeWithText(translatedAppName).assertDoesNotExist()
        composeRule.onNodeWithText("#Messages").assertDoesNotExist()
        // The rest of the drawer really is German, so this isn't passing on an English fallback.
        composeRule.onNodeWithText(german.getString(R.string.settings_app_version_title)).assertIsDisplayed()
        // German labels run longest; none of them may clip at the larger row size.
        assertDrawerContentUnclipped(
            headerSubtitle = german.getString(R.string.drawer_subtitle),
            labels = listOf(
                R.string.screen_archived,
                R.string.screen_blocked,
                R.string.drawer_scheduled,
                R.string.settings_language_title,
            ).map(german::getString),
        )
    }

    @Test
    fun arabic_chevronsMirrorAndNothingIsClipped() {
        val arabic = Locale.forLanguageTag("ar")
        val strings = localizedContext(arabic)
        setDrawer(darkTheme = true, locale = arabic)
        openFromHamburger(menu = strings.getString(R.string.action_menu))

        composeRule.onNodeWithText("Messages").assertIsDisplayed()
        val labels = listOf(
            R.string.screen_archived,
            R.string.screen_blocked,
            R.string.drawer_scheduled,
            R.string.settings_language_title,
        ).map(strings::getString)
        val chevrons = composeRule.onAllNodesWithTag(HomeDrawerChevronTag, useUnmergedTree = true)
        chevrons.assertCountEquals(4)
        labels.forEachIndexed { i, label ->
            // Unmerged: the merged node is the whole clickable row, chevron included.
            val labelBounds = composeRule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
            // RTL: the chevron sits at the row's start (left) edge, past the label, and points left.
            assertTrue("chevron $i left of '$label'", chevrons[i].getUnclippedBoundsInRoot().right <= labelBounds.left)
            assertTrue("chevron $i mirrored", chevrons[i].pointsLeft())
        }
        assertDrawerContentUnclipped(headerSubtitle = strings.getString(R.string.drawer_subtitle), labels = labels)
    }

    /** At font scale 1.5 on a short window the rows overflow: the footer must stay pinned on screen
     * and the last row must still be reachable by scrolling, then tappable. */
    @Test
    fun largeFontScale_footerStaysOnScreenAndLastRowIsReachable() {
        setDrawer(fontScale = 1.5f, height = 480.dp)
        openFromHamburger()

        val sheet = composeRule.onNodeWithTag(HomeDrawerSheetTag).getUnclippedBoundsInRoot()
        val footer = composeRule.onNodeWithTag(HomeDrawerVersionTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("footer inside sheet", footer.top >= sheet.top && footer.bottom <= sheet.bottom)
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()

        val language = composeRule.onNodeWithText("Language").performScrollTo().assertIsDisplayed()
        assertTrue("last row above the footer", language.getUnclippedBoundsInRoot().bottom <= footer.top)
        composeRule.onNodeWithTag(HomeDrawerVersionTag).assertIsDisplayed()

        language.performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks.getValue("language"))
        assertTrue(drawerState.isClosed)
    }

    @Test
    fun archived_navigatesAndCloses() = itemNavigatesAndCloses("Archived", "archived")

    @Test
    fun blocked_navigatesAndCloses() = itemNavigatesAndCloses("Blocked", "blocked")

    @Test
    fun scheduled_navigatesAndCloses() = itemNavigatesAndCloses("Scheduled", "scheduled")

    @Test
    fun language_navigatesAndCloses() = itemNavigatesAndCloses("Language", "language")

    @Test
    fun items_areArchivedBlockedScheduledLanguage_inThatOrder() {
        setDrawer()
        openFromHamburger()

        val tops = listOf("Archived", "Blocked", "Scheduled", "Language").map { label ->
            composeRule.onNodeWithText(label).getUnclippedBoundsInRoot().top
        }
        assertEquals(tops.sorted(), tops)
        assertEquals("four distinct rows", 4, tops.toSet().size)
        // The version footer stays pinned below every item.
        assertTrue(composeRule.onNodeWithTag(HomeDrawerVersionTag).getUnclippedBoundsInRoot().top > tops.last())
    }

    @Test
    fun backClosesTheDrawer() {
        setDrawer()
        openFromHamburger()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertTrue(drawerState.isClosed)
        composeRule.onNodeWithTag(HomeDrawerSheetTag).assertIsNotDisplayed()
    }

    @Test
    fun edgeSwipeDoesNotOpenTheDrawer() {
        setDrawer()

        composeRule.onRoot().performTouchInput { swipeRight(startX = left + 2f, endX = right - 2f) }
        composeRule.waitForIdle()

        assertTrue(drawerState.isClosed)
    }

    @Test
    fun selectionMode_closesTheDrawerAndKeepsItShut() {
        setDrawer()
        openFromHamburger()

        selectionMode = true
        composeRule.waitForIdle()
        assertTrue(drawerState.isClosed)

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        assertTrue(drawerState.isClosed)
        composeRule.onNodeWithTag(HomeDrawerSheetTag).assertIsNotDisplayed()
    }

    private fun opensWithItemsAndVersion(darkTheme: Boolean) {
        setDrawer(darkTheme = darkTheme)
        composeRule.onNodeWithTag(HomeDrawerSheetTag).assertIsNotDisplayed()

        openFromHamburger()

        composeRule.onNodeWithText("Messages").assertIsDisplayed()
        composeRule.onNodeWithText("#Messages").assertDoesNotExist()
        composeRule.onNodeWithText("SMS and MMS").assertIsDisplayed()
        composeRule.onNodeWithText("Archived").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked").assertIsDisplayed()
        composeRule.onNodeWithText("Scheduled").assertIsDisplayed()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onNodeWithText("App version").assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()

        val chevrons = composeRule.onAllNodesWithTag(HomeDrawerChevronTag, useUnmergedTree = true)
        chevrons.assertCountEquals(4)
        repeat(4) { assertFalse("LTR chevron $it points right", chevrons[it].pointsLeft()) }
        assertDrawerContentUnclipped(headerSubtitle = "SMS and MMS", labels = listOf("Archived", "Blocked", "Scheduled", "Language"))
    }

    private fun itemNavigatesAndCloses(label: String, key: String) {
        setDrawer()
        openFromHamburger()

        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()

        assertEquals(mapOf("archived" to 0, "blocked" to 0, "scheduled" to 0, "language" to 0) + (key to 1), clicks)
        assertTrue(drawerState.isClosed)
        composeRule.onNodeWithTag(HomeDrawerSheetTag).assertIsNotDisplayed()
    }

    private fun openFromHamburger(menu: String = "Menu") {
        composeRule.onNodeWithContentDescription(menu).performClick()
        composeRule.waitForIdle()
        assertTrue(drawerState.isOpen)
        composeRule.onNodeWithTag(HomeDrawerSheetTag).assertIsDisplayed()
    }

    /** The header card sits inside the sheet and holds both its lines; every item and the footer
     * sit inside the sheet. */
    private fun assertDrawerContentUnclipped(headerSubtitle: String, labels: List<String>) {
        fun DpRect.contains(inner: DpRect) =
            inner.left >= left && inner.right <= right && inner.top >= top && inner.bottom <= bottom

        val sheet = composeRule.onNodeWithTag(HomeDrawerSheetTag).getUnclippedBoundsInRoot()
        val header = composeRule.onNodeWithTag(HomeDrawerHeaderTag).getUnclippedBoundsInRoot()
        assertTrue("header inside sheet", sheet.contains(header))
        listOf("Messages", headerSubtitle).forEach {
            assertTrue("'$it' inside header", header.contains(composeRule.onNodeWithText(it).getUnclippedBoundsInRoot()))
        }
        labels.forEach {
            assertTrue("'$it' inside sheet", sheet.contains(composeRule.onNodeWithText(it).getUnclippedBoundsInRoot()))
        }
        assertTrue("footer inside sheet", sheet.contains(composeRule.onNodeWithTag(HomeDrawerVersionTag).getUnclippedBoundsInRoot()))
    }

    /**
     * A chevron's tip column is inked at its vertical middle; its open side is inked only at the
     * two arm ends. So the glyph points left iff its leftmost inked column is inked mid-way.
     */
    private fun SemanticsNodeInteraction.pointsLeft(): Boolean {
        val bitmap = captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val background = bitmap.getPixel(0, 0)
        fun inked(x: Int, y: Int) = bitmap.getPixel(x, y) != background
        val columns = (0 until bitmap.width).filter { x -> (0 until bitmap.height).any { y -> inked(x, y) } }
        val rows = (0 until bitmap.height).filter { y -> (0 until bitmap.width).any { x -> inked(x, y) } }
        val midY = (rows.first() + rows.last()) / 2
        val leftmost = columns.first()
        return (leftmost..leftmost + 1).any { x -> (midY - 1..midY + 1).any { y -> inked(x, y) } }
    }

    private fun setDrawer(darkTheme: Boolean = false, locale: Locale? = null, fontScale: Float? = null, height: Dp? = null) {
        composeRule.setContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                Localized(locale) {
                    AppTheme(darkTheme = darkTheme) {
                        HomeDrawer(
                            drawerState = drawerState,
                            modifier = if (height != null) Modifier.height(height) else Modifier,
                            enabled = !selectionMode,
                            onArchivedClick = { clicks["archived"] = clicks.getValue("archived") + 1 },
                            onBlockedClick = { clicks["blocked"] = clicks.getValue("blocked") + 1 },
                            onScheduledClick = { clicks["scheduled"] = clicks.getValue("scheduled") + 1 },
                            onLanguageClick = { clicks["language"] = clicks.getValue("language") + 1 },
                        ) { openDrawer ->
                            ConversationListTopBar(onMenuClick = openDrawer, onSearchClick = {}, onSettingsClick = {})
                        }
                    }
                }
            }
        }
    }

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
    }

    /** [content] with [locale]'s resources, configuration and layout direction; the device's own when null. */
    @Composable
    private fun Localized(locale: Locale?, content: @Composable () -> Unit) {
        if (locale == null) return content()
        val context = LocalContext.current
        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
        val localized = context.createConfigurationContext(configuration)
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides configuration,
            LocalResources provides localized.resources,
            LocalLayoutDirection provides
                if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content,
        )
    }
}
