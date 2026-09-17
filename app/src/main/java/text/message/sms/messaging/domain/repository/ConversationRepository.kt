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

    /** Upserts [threadId]'s snippet, last-message time, and unread count together in one write --
     * used by a caller doing a batched update across many messages at once (see
     * [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll]) so the
     * conversation list's live Flow emits its final state once per batch instead of once per
     * message. */
    suspend fun refreshCounters(threadId: Long, snippet: String, lastMessageAtMillis: Long)

    suspend fun delete(threadIds: Collection<Long>)

    fun search(query: String): Flow<List<Conversation>>
}
