package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.Conversation

/** Read and write access to message threads. */
interface ConversationRepository {

    fun observeInbox(): Flow<List<Conversation>>

    fun observeArchived(): Flow<List<Conversation>>

    /** Every blocked thread -- see [text.message.sms.messaging.data.local.db.dao.ConversationDao
     * .getBlockedConversations]. Backs the eventual Blocked list screen. */
    fun getBlockedConversations(): Flow<List<Conversation>>

    fun observeConversation(threadId: Long): Flow<Conversation?>

    suspend fun findByThreadId(threadId: Long): Conversation?

    /** Returns the thread id for [addresses], creating the thread if it does not exist yet. */
    suspend fun resolveThreadId(addresses: Set<String>): Long

    suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean)

    suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean)

    suspend fun setMuted(threadIds: Collection<Long>, muted: Boolean)

    suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean)

    suspend fun saveDraft(threadId: Long, draft: String?)

    /** Remembers (or clears, when [slot] is null) which SIM slot sends this thread's messages --
     * see [text.message.sms.messaging.domain.model.Conversation.subscriptionSlot]. */
    suspend fun setSubscriptionSlot(threadId: Long, slot: Int?)

    /** Upserts every thread in [updates]' snippet, last-message time, and unread count inside a
     * single database transaction -- used by [text.message.sms.messaging.data.repository
     * .TelephonySyncRepository.syncAll], which tracks one [ConversationCounterUpdate] per thread
     * across the whole sync batch. A single transaction means [observeInbox]'s live Flow emits
     * its final, fully-settled, correctly-ordered state exactly once per sync pass -- previously
     * each thread was upserted in its own separate write, so a large catch-up sync produced one
     * Flow emission per thread, and the inbox visibly re-sorted itself thread-by-thread instead
     * of jumping straight to the final order. */
    suspend fun refreshCountersBatch(updates: Map<Long, ConversationCounterUpdate>)

    /** Unconditionally sets [threadId]'s snippet/last-message time -- unlike
     * [refreshCountersBatch], which only ever moves those forward, this can also move them
     * *backward*. Used after deleting one or more messages from a thread that still has others
     * left, where the previous last message is simply gone. */
    suspend fun setLastMessage(threadId: Long, snippet: String, timestampMillis: Long)

    suspend fun delete(threadIds: Collection<Long>)

    fun search(query: String): Flow<List<Conversation>>
}

data class ConversationCounterUpdate(val snippet: String, val lastMessageAtMillis: Long)
