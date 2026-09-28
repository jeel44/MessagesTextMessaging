package text.message.sms.messaging.ui.screens.blocked

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.ui.theme.AppTheme

/**
 * [BlockedScreenContent] under both themes, fed a plain in-memory list the way [BlockedViewModel]
 * would feed it -- unblocking removes the row from that list, as Room's live query does for real.
 * The restore-to-inbox side of an unblock is covered by `UnblockNumberTest`.
 */
@RunWith(AndroidJUnit4::class)
class BlockedScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alice = BlockedNumberRow(number("+15550101234"), contactName = "Alice")
    private val unknown = BlockedNumberRow(number("5550109999"), contactName = null)

    private var rows by mutableStateOf<List<BlockedNumberRow>?>(listOf(alice, unknown))
    private val unblocked = mutableListOf<BlockedNumber>()

    @Test
    fun lightTheme_rendersList() = rendersList(darkTheme = false)

    @Test
    fun darkTheme_rendersList() = rendersList(darkTheme = true)

    @Test
    fun unblockButton_removesThatRowOnly() {
        setContent()

        composeRule.onNode(
            hasContentDescription("Unblock") and hasAnyAncestor(hasTestTag(BlockedRowTagPrefix + alice.number.address)),
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(alice.number), unblocked)
        composeRule.onNodeWithText("Alice").assertDoesNotExist()
        row(unknown).assertIsDisplayed()
    }

    @Test
    fun swipeRight_unblocksExactlyOnce() {
        setContent()

        row(unknown).performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf(unknown.number), unblocked)
        row(unknown).assertDoesNotExist()
    }

    @Test
    fun lightTheme_emptyState() = emptyState(darkTheme = false)

    @Test
    fun darkTheme_emptyState() = emptyState(darkTheme = true)

    @Test
    fun beforeFirstLoad_showsNeitherListNorEmptyState() {
        rows = null
        setContent()

        composeRule.onAllNodesWithText("No blocked numbers").assertCountEquals(0)
        row(alice).assertDoesNotExist()
    }

    private fun rendersList(darkTheme: Boolean) {
        setContent(darkTheme)

        composeRule.onNodeWithText("Blocked").assertIsDisplayed()
        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        row(alice).assertIsDisplayed()
        row(unknown).assertIsDisplayed()
        composeRule.onAllNodesWithText("No blocked numbers").assertCountEquals(0)
    }

    private fun emptyState(darkTheme: Boolean) {
        rows = emptyList()
        setContent(darkTheme)

        composeRule.onNodeWithText("No blocked numbers").assertIsDisplayed()
        composeRule.onNodeWithText("Numbers you block from a conversation show up here.").assertIsDisplayed()
    }

    private fun setContent(darkTheme: Boolean = false) {
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                BlockedScreenContent(
                    rows = rows,
                    onUnblock = { number ->
                        unblocked += number
                        rows = rows?.filterNot { it.number == number }
                    },
                    onBack = {},
                )
            }
        }
    }

    private fun row(row: BlockedNumberRow) = composeRule.onNodeWithTag(BlockedRowTagPrefix + row.number.address)

    private fun number(address: String) =
        BlockedNumber(id = address.hashCode().toLong(), address = address, reason = BlockReason.MANUAL, blockedAtMillis = 0L)
}
