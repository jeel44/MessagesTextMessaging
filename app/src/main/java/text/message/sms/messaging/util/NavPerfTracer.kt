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
}
