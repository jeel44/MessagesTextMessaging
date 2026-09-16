package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.ConversationWithRecipients
import text.message.sms.messaging.data.local.db.entity.RecipientEntity

@Dao
interface ConversationDao {

    @Transaction
    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 0 AND is_blocked = 0
        ORDER BY is_pinned DESC, last_message_at DESC
        """
    )
    fun observeInbox(): Flow<List<ConversationWithRecipients>>

    @Transaction
    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 1 AND is_blocked = 0
        ORDER BY last_message_at DESC
        """
    )
    fun observeArchived(): Flow<List<ConversationWithRecipients>>

    @Transaction
    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun observeByThreadId(threadId: Long): Flow<ConversationWithRecipients?>

    @Transaction
    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    suspend fun findByThreadId(threadId: Long): ConversationWithRecipients?

    @Transaction
    @Query(
        """
        SELECT DISTINCT c.* FROM conversations c
        INNER JOIN recipients r ON r.thread_id = c.thread_id
        WHERE c.snippet LIKE '%' || :query || '%' OR r.address LIKE '%' || :query || '%'
        ORDER BY c.last_message_at DESC
        """
    )
    fun search(query: String): Flow<List<ConversationWithRecipients>>

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipients(recipients: List<RecipientEntity>)

    @Query("UPDATE conversations SET is_archived = :archived WHERE thread_id IN (:threadIds)")
    suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean)

    @Query("UPDATE conversations SET is_pinned = :pinned WHERE thread_id IN (:threadIds)")
    suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean)

    @Query("UPDATE conversations SET is_muted = :muted WHERE thread_id IN (:threadIds)")
    suspend fun setMuted(threadIds: Collection<Long>, muted: Boolean)

    @Query("UPDATE conversations SET is_blocked = :blocked WHERE thread_id IN (:threadIds)")
    suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean)

    @Query("UPDATE conversations SET draft = :draft WHERE thread_id = :threadId")
    suspend fun setDraft(threadId: Long, draft: String?)

    @Query("UPDATE conversations SET unread_count = :count WHERE thread_id = :threadId")
    suspend fun setUnreadCount(threadId: Long, count: Int)

    @Query("DELETE FROM conversations WHERE thread_id IN (:threadIds)")
    suspend fun delete(threadIds: Collection<Long>)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}
