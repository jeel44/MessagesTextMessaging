package text.message.sms.messaging.ui.screens.chat

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder

/**
 * Regression coverage for the bug fixed alongside 1cde609 (which was about the conversation
 * *list*'s ordering, not this): opening a chat left the message timeline scrolled to the top --
 * the oldest message -- instead of the most recent one at the bottom. [ChatMessageList] is
 * exercised directly with a plain list of items and no [ChatViewModel], the same pattern
 * [text.message.sms.messaging.ui.screens.conversationlist.SwipeableConversationRowTest] uses for
 * [text.message.sms.messaging.ui.screens.conversationlist.SwipeableConversationRow].
 */
@RunWith(AndroidJUnit4::class)
class ChatMessageListScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 40 one-per-day bubbles -- comfortably more than fit in the constrained-height viewport
     * below, so "scrolled to the bottom" is a real assertion and not trivially true because
     * everything happens to fit on screen. */
    private val manyItems: List<ChatListItem> = (0 until 40).map { index ->
        ChatListItem.Bubble(
            message = Message(
                id = index.toLong(),
                threadId = 1L,
                providerId = index.toLong(),
                channel = MessageChannel.SMS,
                folder = if (index % 2 == 0) MessageFolder.INBOX else MessageFolder.SENT,
                deliveryState = DeliveryState.NONE,
                address = "+15551234567",
                body = "Message $index",
                subject = null,
                sentAtMillis = 1_700_000_000_000L + index * 60_000L,
                receivedAtMillis = 1_700_000_000_000L + index * 60_000L,
                isRead = true,
                isSeen = true,
                subscriptionId = -1,
                errorCode = 0,
            ),
            isLastInRun = true,
        )
    }

    /** The bug: opening a chat must land on the most recent message (the *last* item -- see
     * [ChatMessageList]'s doc comment for why it's the last, not the first), not the oldest one
     * the list happens to start scrolled to by default.
     *
     * [items] starts empty and is only populated *after* the first frame has already composed
     * and laid out -- matching [ChatViewModel.messages]' real [kotlinx.coroutines.flow.StateFlow],
     * which is seeded with `emptyList()` and only gets the real messages once the underlying Room
     * query actually emits. A test that instead passes the full list from the very first frame
     * doesn't reproduce the bug: [androidx.compose.foundation.lazy.LazyListLayoutInfo
     * .totalItemsCount] defaults to 0 before any real layout has happened, which makes the old,
     * buggy `isNearBottom` check (`0 >= 0 - 2`) spuriously true on that first frame regardless of
     * the fix -- it takes a real prior layout pass with a real (non-placeholder) item count for
     * the bug to actually manifest, same as it does for the real, asynchronously-loaded screen.
     */
    @Test
    fun initialComposition_scrollsToMostRecentMessage() {
        lateinit var listState: LazyListState
        var items by mutableStateOf(emptyList<ChatListItem>())

        composeRule.setContent {
            listState = rememberLazyListState()
            ChatMessageList(
                chatItems = items,
                messageSentEvents = remember { MutableSharedFlow() },
                onAttachmentClick = {},
                modifier = Modifier.height(300.dp),
                listState = listState,
            )
        }
        composeRule.waitForIdle()

        items = manyItems
        composeRule.waitForIdle()

        val visible = listState.layoutInfo.visibleItemsInfo
        assertTrue("expected the last item to be visible, but nothing was", visible.isNotEmpty())
        assertEquals(manyItems.lastIndex, visible.last().index)
        // The oldest message (index 0) must not still be on screen -- otherwise this would pass
        // trivially on a list short enough to show everything at once.
        assertTrue(visible.first().index > 0)
    }

    /** An empty chat (no messages yet, e.g. a brand-new thread from New Message) must not crash
     * scrolling to a nonexistent last index. */
    @Test
    fun initialComposition_withNoMessages_doesNotCrash() {
        composeRule.setContent {
            ChatMessageList(
                chatItems = emptyList(),
                messageSentEvents = remember { MutableSharedFlow() },
                onAttachmentClick = {},
            )
        }
        composeRule.waitForIdle()
    }
}
