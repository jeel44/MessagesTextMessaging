package text.message.sms.messaging.ui.screens.scheduled

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.domain.model.ScheduledStatus
import text.message.sms.messaging.ui.theme.AppTheme

/** [ScheduledMessagesContent] under both themes, fed a plain list the way its ViewModel would. */
@RunWith(AndroidJUnit4::class)
class ScheduledMessagesRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val pending = item(1L, "Alice", "see you at 9", ScheduledStatus.PENDING)
    private val failed = item(2L, "Bob", "happy birthday", ScheduledStatus.FAILED)
    private val sending = item(3L, "Carol", "on my way", ScheduledStatus.SENDING)

    private var items by mutableStateOf<List<ScheduledListItem>?>(listOf(pending, failed, sending))
    private val sentNow = mutableListOf<Long>()
    private val deleted = mutableListOf<Long>()

    @Test
    fun lightTheme_rendersList() = rendersList(darkTheme = false)

    @Test
    fun darkTheme_rendersList() = rendersList(darkTheme = true)

    @Test
    fun lightTheme_emptyState() = emptyState(darkTheme = false)

    @Test
    fun darkTheme_emptyState() = emptyState(darkTheme = true)

    @Test
    fun sendNowAndDelete_reachTheirCallbacks() {
        items = listOf(pending)
        setContent()

        composeRule.onAllNodesWithContentDescription("Send now")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Delete")[0].performClick()

        assertEquals(listOf(1L), sentNow)
        assertEquals(listOf(1L), deleted)
    }

    @Test
    fun beforeFirstLoad_showsNeitherListNorEmptyState() {
        items = null
        setContent()

        composeRule.onAllNodesWithText("No scheduled messages").assertCountEquals(0)
        composeRule.onNodeWithText("Alice").assertDoesNotExist()
    }

    private fun rendersList(darkTheme: Boolean) {
        setContent(darkTheme)

        composeRule.onNodeWithText("Scheduled messages").assertIsDisplayed()
        composeRule.onNodeWithTag(ScheduledMessagesListTestTag).assertIsDisplayed()
        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        composeRule.onNodeWithText("Not sent", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
        // Pending and failed rows have actions; a message already sending has none.
        composeRule.onAllNodesWithContentDescription("Send now").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Delete").assertCountEquals(2)
    }

    private fun emptyState(darkTheme: Boolean) {
        items = emptyList()
        setContent(darkTheme)

        composeRule.onNodeWithText("No scheduled messages").assertIsDisplayed()
    }

    private fun setContent(darkTheme: Boolean = false) {
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                ScheduledMessagesContent(
                    items = items,
                    onBack = {},
                    onOpen = {},
                    onSendNow = { sentNow += it.messageId },
                    onDelete = { deleted += it.messageId },
                )
            }
        }
    }

    private fun item(id: Long, recipient: String, body: String, status: ScheduledStatus) = ScheduledListItem(
        messageId = id,
        threadId = id,
        recipient = recipient,
        body = body,
        sendAtMillis = 1_800_000_000_000L,
        status = status,
    )
}
