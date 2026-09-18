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

    /** Regression coverage for the "far too sensitive" bug: a slight/partial drag, released at a
     * realistic swipe speed (well within the library's own fling-velocity commit path, not an
     * artificially slow drag) -- must spring back without deleting anything. 20% of the row's
     * width, in 400ms, previously still fired the action even after raising positionalThreshold
     * alone, because AnchoredDraggableState's internal ~125dp/s fling-velocity path ignores
     * positionalThreshold entirely and commits on velocity alone; the confirmValueChange distance
     * re-check exists specifically to close that gap. */
    @Test
    fun partialSwipeLeft_underThreshold_doesNotFireDelete() {
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

        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = right, endX = right - width * 0.2f, durationMillis = 400)
        }
        composeRule.waitForIdle()

        assertEquals(0, deleteCount)
    }

    /** Same partial-swipe guard for the archive direction, at the same realistic (not
     * artificially slowed) swipe speed. */
    @Test
    fun partialSwipeRight_underThreshold_doesNotFireArchive() {
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

        composeRule.onRoot().performTouchInput {
            swipeRight(startX = left, endX = left + width * 0.2f, durationMillis = 400)
        }
        composeRule.waitForIdle()

        assertEquals(0, archiveCount)
    }

    /** Even a fast, short flick -- high velocity, short distance -- must not commit. This is the
     * exact case positionalThreshold alone cannot cover (the library's fling path ignores it once
     * velocity crosses its internal, unconfigurable threshold), and is what motivated the
     * confirmValueChange distance re-check. */
    @Test
    fun fastShortFlickLeft_doesNotFireDelete() {
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

        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = right, endX = right - width * 0.15f, durationMillis = 80)
        }
        composeRule.waitForIdle()

        assertEquals(0, deleteCount)
    }

    /** A full, deliberate swipe (most of the row's width, released slowly so this is genuinely a
     * distance-driven commit rather than a fast-flick one) must still fire exactly once -- the
     * higher commit threshold must not accidentally make a real full swipe unreliable, and must
     * not reintroduce the 471ac5c duplicate-firing bug either. */
    @Test
    fun slowFullSwipeLeft_stillFiresDeleteExactlyOnce() {
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

        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = right, endX = left, durationMillis = 600)
        }
        composeRule.waitForIdle()

        assertEquals(1, deleteCount)
    }
}
