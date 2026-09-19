package text.message.sms.messaging.util

import android.os.SystemClock
import android.util.Log

/**
 * Minimal navigation-latency breadcrumb for the Home -> Chat path, added for the navigation-speed
 * pass -- not read by any UI decision, purely a Logcat line (tag "NavPerf") so a "tap a
 * conversation -> Chat's first frame" duration can be read off a real device without attaching a
 * profiler. [markConversationClicked] is called from
 * [text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen] right where the
 * tap happens; [logChatFirstFrame] is called once from [text.message.sms.messaging.ui.screens.chat
 * .ChatScreen] after its very first composition.
 */
internal object NavPerfTracer {
    private const val TAG = "NavPerf"

    @Volatile
    private var conversationClickAtMillis: Long = 0L

    private val loggedThreadIds = mutableSetOf<Long>()

    fun markConversationClicked() {
        conversationClickAtMillis = SystemClock.elapsedRealtime()
    }

    /** Logs once per [threadId] per process -- re-entering an already-open Chat (e.g. rotating,
     * or a recomposition) never logs a second, meaningless "elapsed" against a stale click. */
    fun logChatFirstFrame(threadId: Long) {
        val clickAtMillis = conversationClickAtMillis
        if (clickAtMillis == 0L || !loggedThreadIds.add(threadId)) return
        val elapsedMillis = SystemClock.elapsedRealtime() - clickAtMillis
        Log.d(TAG, "Home tap -> Chat first frame (thread=$threadId): ${elapsedMillis}ms")
    }

    private const val PERF_TAG = "ChatOpenPerf"

    /** [mode] is [text.message.sms.messaging.ui.screens.chat.ChatMode]'s name, and [messageCount]
     * is `messages.size` -- both read at Chat's very first composition, so a logcat filter on
     * "ChatOpenPerf" shows exactly what the user's first frame looked like: whether the mode was
     * already known (never `PERSONAL` by default -- see `ChatMode`'s doc comment) and whether the
     * message list was already non-empty before any second emission could arrive. */
    fun logChatOpenDetails(threadId: Long, mode: String, messageCount: Int) {
        Log.d(PERF_TAG, "thread=$threadId firstFrameMode=$mode firstFrameMessageCount=$messageCount")
    }

    /** Called from a delayed check after Chat's first frame -- logs only if the message list's
     * size actually differs from what the first frame showed, i.e. a real second emission (a
     * priority sync import, a page widen) changed what's on screen after the fact. */
    fun logChatMessagesChangedAfterFirstFrame(threadId: Long, firstFrameCount: Int, laterCount: Int) {
        if (firstFrameCount == laterCount) return
        Log.d(PERF_TAG, "thread=$threadId messages changed after first frame: $firstFrameCount -> $laterCount")
    }

    private const val BLINK_TAG = "ChatListBlink"

    /** [ChatScreen]'s very first composition for this thread -- the start of the ~120ms window
     * the list-blink bug measured empty. */
    fun logChatListBlinkScreenComposed(threadId: Long) {
        val elapsedMillis = SystemClock.elapsedRealtime()
        Log.d(BLINK_TAG, "thread=$threadId screenComposed at=${elapsedMillis}ms")
    }

    /** [ChatViewModel.chatMessagesState] emitted `Loaded` -- the first real Room page for this
     * thread arrived and was grouped into [itemCount] items in that same emission, so
     * [ChatMessageList] is now eligible to compose from those exact items (never a stale/empty
     * list from a separately-timed flow -- see [ChatViewModel.chatMessagesState]'s doc comment). */
    fun logChatListBlinkFirstPageArrived(threadId: Long, itemCount: Int) {
        val elapsedMillis = SystemClock.elapsedRealtime()
        Log.d(BLINK_TAG, "thread=$threadId firstPageArrived at=${elapsedMillis}ms itemCount=$itemCount")
    }

    /** [ChatMessageList]'s [androidx.compose.foundation.lazy.LazyColumn] laid out for the first
     * time -- [firstVisibleItemIndex] and [lastVisibleItemKey] should already reflect the newest
     * message, since the fix bakes the initial scroll position into the list state instead of
     * scrolling to it after the fact. [itemCount] should never be 0 here for a thread whose first
     * page had messages -- see [logChatListBlinkFirstPageArrived]'s itemCount for what the loaded
     * state actually carried. */
    fun logChatListBlinkFirstLayout(threadId: Long, firstVisibleItemIndex: Int, lastVisibleItemKey: Any?, itemCount: Int) {
        val elapsedMillis = SystemClock.elapsedRealtime()
        Log.d(
            BLINK_TAG,
            "thread=$threadId firstLayout at=${elapsedMillis}ms firstVisibleItemIndex=$firstVisibleItemIndex " +
                "lastVisibleItemKey=$lastVisibleItemKey itemCount=$itemCount",
        )
    }

    /** A scroll happened on [listState] after [logChatListBlinkFirstLayout] already fired for this
     * thread -- [source] identifies which mechanism triggered it ("newMessage" or "messageSent"
     * are both expected/legitimate; anything else during the fix's manual checklist is a bug). */
    fun logChatListBlinkScrollAfterFirstLayout(threadId: Long, source: String) {
        val elapsedMillis = SystemClock.elapsedRealtime()
        Log.d(BLINK_TAG, "thread=$threadId scrollAfterFirstLayout at=${elapsedMillis}ms source=$source")
    }
}
