package text.message.sms.messaging.util

import text.message.sms.messaging.domain.model.Conversation

/**
 * Carries the [Conversation] a list screen (Home, Archived) already has in memory for the row the
 * user just tapped, so [text.message.sms.messaging.ui.screens.chat.ChatViewModel] can know the
 * personal/non-personal mode from its very first frame instead of defaulting to one and flipping
 * once its own `observeConversation` query resolves -- see [ChatViewModel]'s `init` and
 * [text.message.sms.messaging.ui.screens.chat.ChatScreen]'s mode handling.
 *
 * Plain in-memory object (same pattern as [NavPerfTracer]), not a `SavedStateHandle` nav argument
 * -- [Conversation] isn't a primitive nav-safe type, and this hint is only ever a same-process,
 * single-use optimization for the common "tap a row" path. Any entry point that doesn't go through
 * one of those rows (deep link, notification, search, forward, a brand-new thread) simply finds
 * nothing here and falls back to [ChatViewModel]'s own load, exactly as before this existed.
 */
internal object ChatOpenHint {
    @Volatile
    private var pending: Conversation? = null

    fun prime(conversation: Conversation) {
        pending = conversation
    }

    /** Consumes the hint if it matches [threadId] -- single-use and thread-id-checked so a stale
     * hint from a different row can never leak into the wrong chat (e.g. priming for one row,
     * then a different one ending up navigated to first). */
    fun consume(threadId: Long): Conversation? {
        val hint = pending
        pending = null
        return hint?.takeIf { it.threadId == threadId }
    }
}
