package text.message.sms.messaging.ui.screens.conversationlist

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.ui.theme.AppTheme

/**
 * [HomeDrawer] (Archived, Blocked, Scheduled, Language) around the inbox's real [ConversationListTopBar], so "open" goes through the actual
 * hamburger. Drives [HomeDrawer] rather than the full inbox, whose Hilt ViewModel this module has
 * no test harness for -- [ConversationListScreen] passes `enabled = !isSelectionMode`, which
 * [selectionMode] stands in for here.
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

        composeRule.onNodeWithText("#Messages").assertIsDisplayed()
        composeRule.onNodeWithText("Archived").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked").assertIsDisplayed()
        composeRule.onNodeWithText("Scheduled").assertIsDisplayed()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onNodeWithText("App version").assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
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

    private fun openFromHamburger() {
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        assertTrue(drawerState.isOpen)
        composeRule.onNodeWithTag(HomeDrawerSheetTag).assertIsDisplayed()
    }

    private fun setDrawer(darkTheme: Boolean = false) {
        composeRule.setContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            AppTheme(darkTheme = darkTheme) {
                HomeDrawer(
                    drawerState = drawerState,
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
