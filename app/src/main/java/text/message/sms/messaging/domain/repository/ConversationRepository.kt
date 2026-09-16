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

    suspend fun delete(threadIds: Collection<Long>)

    fun search(query: String): Flow<List<Conversation>>
}
