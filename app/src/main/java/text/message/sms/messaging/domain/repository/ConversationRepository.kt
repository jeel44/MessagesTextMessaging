package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.Conversation

/** Read and write access to message threads. */
interface ConversationRepository {

    fun observeInbox(): Flow<List<Conversation>>

    fun observeArchived(): Flow<List<Conversation>>

    fun observeConversation(threadId: Long): Flow<Conversation?>

    suspend fun findByThreadId(threadId: Long): Conversation?

    /** Returns the thread id for [addresses], creating the thread if it does not exist yet. */
    suspend fun resolveThreadId(addresses: Set<String>): Long

    suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean)

    suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean)

    suspend fun setMuted(threadIds: Collection<Long>, muted: Boolean)

    suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean)

    suspend fun saveDraft(threadId: Long, draft: String?)

    /** Upserts every thread in [updates]' snippet, last-message time, and unread count inside a
     * single database transaction -- used by [text.message.sms.messaging.data.repository
     * .TelephonySyncRepository.syncAll], which tracks one [ConversationCounterUpdate] per thread
     * across the whole sync batch. A single transaction means [observeInbox]'s live Flow emits
     * its final, fully-settled, correctly-ordered state exactly once per sync pass -- previously
     * each thread was upserted in its own separate write, so a large catch-up sync produced one
     * Flow emission per thread, and the inbox visibly re-sorted itself thread-by-thread instead
     * of jumping straight to the final order. */
    suspend fun refreshCountersBatch(updates: Map<Long, ConversationCounterUpdate>)

    suspend fun delete(threadIds: Collection<Long>)

    fun search(query: String): Flow<List<Conversation>>
}

data class ConversationCounterUpdate(val snippet: String, val lastMessageAtMillis: Long)
