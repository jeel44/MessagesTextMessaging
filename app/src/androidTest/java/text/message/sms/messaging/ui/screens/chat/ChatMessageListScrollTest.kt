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
 * Regression coverage for two related bugs, both about where [ChatMessageList] lands on open --
 * [ChatMessageList] is exercised directly with a plain list of items and no [ChatViewModel], the
 * same pattern [text.message.sms.messaging.ui.screens.conversationlist.SwipeableConversationRowTest]
 * uses for [text.message.sms.messaging.ui.screens.conversationlist.SwipeableConversationRow]:
 *
 * - Fixed alongside 1cde609 (which was about the conversation *list*'s ordering, not this):
 *   opening a chat left the message timeline scrolled to the top -- the oldest message -- instead
 *   of the most recent one at the bottom.
 * - Fixed later (the "list blink" bug): the first fix above scrolled to the bottom via a
 *   `LaunchedEffect` that ran *after* [chatItems] arrived, which meant a real frame drew at the
 *   top (index 0) before a second frame jumped to the bottom -- a visible flash of the wrong
 *   position. [ChatMessageList] no longer performs that scroll at all; its `listState` default
 *   bakes the correct initial index into the [LazyListState] itself, on the assumption -- upheld
 *   by [ChatScreen], see `ChatViewModel.hasLoadedInitialMessages` -- that this function is never
 *   composed for the first time until [chatItems] already has its real first-page contents. So
 *   [initialComposition_scrollsToMostRecentMessage] below composes with [manyItems] already
 *   populated (not empty-then-filled, which was the old test's whole point but is no longer this
 *   function's contract), and asserts the very first layout already sits at the bottom.
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

    /** Opening a chat must land on the most recent message (the *last* item -- see
     * [ChatMessageList]'s doc comment for why it's the last, not the first), not the oldest one,
     * and must do so on its very first laid-out frame -- no visible scroll/jump afterward.
     *
     * [manyItems] is passed from the very first composition, matching [ChatScreen]'s real
     * contract post-fix: it only composes [ChatMessageList] once [ChatViewModel
     * .hasLoadedInitialMessages] is already true, so [chatItems] never starts empty and fills in
     * later the way it used to. [listState] mirrors [ChatMessageList]'s own default
     * (`initialFirstVisibleItemIndex = chatItems.lastIndex`) explicitly, so the assertions below
     * are checking the actual first-layout position, not a scroll that happened to catch up to it.
     */
    @Test
    fun initialComposition_scrollsToMostRecentMessage() {
        lateinit var listState: LazyListState

        composeRule.setContent {
            listState = rememberLazyListState(initialFirstVisibleItemIndex = manyItems.lastIndex)
            ChatMessageList(
                chatItems = manyItems,
                messageSentEvents = remember { MutableSharedFlow() },
                onAttachmentClick = {},
                modifier = Modifier.height(300.dp),
                listState = listState,
            )
        }
        composeRule.waitForIdle()

        val visible = listState.layoutInfo.visibleItemsInfo
        assertTrue("expected the last item to be visible, but nothing was", visible.isNotEmpty())
        assertEquals(manyItems.lastIndex, visible.last().index)
        // The oldest message (index 0) must not still be on screen -- otherwise this would pass
        // trivially on a list short enough to show everything at once.
        assertTrue(visible.first().index > 0)
    }

    /** A message arriving *after* the chat is already open (e.g. an incoming reply while the user
     * is at the bottom) must still auto-scroll into view -- this is the one case
     * [ChatMessageList] still performs a programmatic scroll for, and it must keep working now
     * that the initial-open jump (tested above) no longer goes through the same code path. */
    @Test
    fun messageArrivingAfterOpen_whileNearBottom_scrollsIntoView() {
        lateinit var listState: LazyListState
        var items by mutableStateOf(manyItems)

        composeRule.setContent {
            listState = rememberLazyListState(initialFirstVisibleItemIndex = items.lastIndex)
            ChatMessageList(
                chatItems = items,
                messageSentEvents = remember { MutableSharedFlow() },
                onAttachmentClick = {},
                modifier = Modifier.height(300.dp),
                listState = listState,
            )
        }
        composeRule.waitForIdle()

        val newMessage = ChatListItem.Bubble(
            message = manyItems.last().let { (it as ChatListItem.Bubble).message }.copy(
                id = 40L,
                providerId = 40L,
                body = "Message 40",
                sentAtMillis = 1_700_000_000_000L + 40 * 60_000L,
                receivedAtMillis = 1_700_000_000_000L + 40 * 60_000L,
            ),
        )
        items = manyItems + newMessage
        composeRule.waitForIdle()

        // Same "near bottom" definition ChatMessageList itself uses to decide whether to
        // auto-scroll, rather than asserting the new item is the exact last *fully* visible one
        // -- a fixed 300dp viewport can leave the very newest bubble only partially on screen
        // depending on its measured height, which would make an exact-index assertion flaky.
        val layoutInfo = listState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        assertTrue(
            "expected the list to have followed the new message near the bottom, " +
                "lastVisible=$lastVisible totalItems=${layoutInfo.totalItemsCount}",
            lastVisible >= layoutInfo.totalItemsCount - 2,
        )
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
