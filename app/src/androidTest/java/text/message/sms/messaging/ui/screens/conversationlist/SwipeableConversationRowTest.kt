package text.message.sms.messaging.ui.screens.conversationlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.data.local.datastore.SwipeAction
import text.message.sms.messaging.data.local.datastore.SwipeActionPreference
import text.message.sms.messaging.domain.model.Conversation

/**
 * Regression coverage for [SwipeableConversationRow] firing its swipe action more than once per
 * physical swipe -- see its doc comment. `performTouchInput { swipeLeft() }` synthesizes several
 * intermediate drag-move events, the same shape as a real finger swipe, which is exactly what used
 * to make `confirmValueChange` (and therefore the action) run many times over for one gesture.
 */
@RunWith(AndroidJUnit4::class)
class SwipeableConversationRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val conversation = Conversation(
        id = 1L,
        threadId = 1L,
        recipients = emptyList(),
        snippet = "Hi",
        lastMessageAtMillis = 1_700_000_000_000L,
        unreadCount = 0,
        isArchived = false,
        isPinned = false,
        isBlocked = false,
        isMuted = false,
        draft = null,
    )

    /** The exact bug reported: one swipe-to-delete must queue exactly one delete request, not one
     * per drag frame past the threshold -- multiple would each queue their own undo-able snackbar,
     * so a single Undo tap on the first one left the rest to time out and delete for real. */
    @Test
    fun swipeLeftPastThreshold_firesDeleteExactlyOnce() {
        var deleteCount = 0

        composeRule.setContent {
            SwipeableConversationRow(
                conversation = conversation,
                swipeActionPreference = SwipeActionPreference(),
                onClick = {},
                onSwipeAction = {},
                onDeleteRequested = { deleteCount++ },
            )
        }

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, deleteCount)
    }

    /** Same dedup applies to every swipe action, not just delete -- archive is merely idempotent
     * enough that firing several times never surfaced as a visible bug. */
    @Test
    fun swipeRightPastThreshold_firesArchiveExactlyOnce() {
        var archiveCount = 0

        composeRule.setContent {
            SwipeableConversationRow(
                conversation = conversation,
                swipeActionPreference = SwipeActionPreference(),
                onClick = {},
                onSwipeAction = { action -> if (action == SwipeAction.ARCHIVE) archiveCount++ },
                onDeleteRequested = {},
            )
        }

        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(1, archiveCount)
    }
}
