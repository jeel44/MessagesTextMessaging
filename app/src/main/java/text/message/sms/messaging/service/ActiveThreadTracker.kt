package text.message.sms.messaging.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which thread's [text.message.sms.messaging.ui.screens.chat.ChatScreen] is currently
 * resumed on screen, so [text.message.sms.messaging.domain.repository.IncomingMessageNotifier]
 * can skip posting a notification for a thread the user is already looking at. Set/cleared from
 * [text.message.sms.messaging.ui.screens.chat.ChatViewModel]'s screen-resume/pause hooks, which
 * are driven by Compose's `LifecycleResumeEffect` -- Activity-level resume/pause, not the
 * ViewModel's own (differently timed) creation/clearing.
 */
@Singleton
class ActiveThreadTracker @Inject constructor() {

    @Volatile
    private var activeThreadId: Long? = null

    fun setActive(threadId: Long) {
        activeThreadId = threadId
    }

    /** No-ops if [threadId] isn't the currently active one -- a stale pause callback (e.g. from a
     * screen instance already superseded by a newer one for the same thread) can never clear a
     * different, still-active thread. */
    fun clear(threadId: Long) {
        if (activeThreadId == threadId) activeThreadId = null
    }

    fun isActive(threadId: Long): Boolean = activeThreadId == threadId
}
