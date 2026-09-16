package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.MessageEntity
import text.message.sms.messaging.data.local.db.entity.MessageWithAttachments
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.MessageChannel

@Dao
interface MessageDao {

    @Transaction
    @Query("SELECT * FROM messages WHERE thread_id = :threadId ORDER BY received_at ASC")
    fun observeThread(threadId: Long): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun findById(id: Long): MessageWithAttachments?

    @Transaction
    @Query("SELECT * FROM messages WHERE provider_id = :providerId AND channel = :channel")
    suspend fun findByProviderId(providerId: Long, channel: MessageChannel): MessageWithAttachments?

    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE body LIKE '%' || :query || '%'
        ORDER BY received_at DESC
        LIMIT 500
        """
    )
    fun search(query: String): Flow<List<MessageWithAttachments>>

    @Query("SELECT COUNT(*) FROM messages WHERE thread_id = :threadId AND is_read = 0")
    suspend fun countUnread(threadId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET delivery_state = :state, error_code = :errorCode WHERE id = :id")
    suspend fun setDeliveryState(id: Long, state: DeliveryState, errorCode: Int)

    @Query("UPDATE messages SET provider_id = :providerId WHERE id = :id")
    suspend fun setProviderId(id: Long, providerId: Long)

    @Query("UPDATE messages SET is_read = :read, is_seen = 1 WHERE thread_id IN (:threadIds)")
    suspend fun setRead(threadIds: Collection<Long>, read: Boolean)

    @Query("UPDATE messages SET is_seen = 1 WHERE thread_id IN (:threadIds)")
    suspend fun setSeen(threadIds: Collection<Long>)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<Long>)

    @Query("DELETE FROM messages WHERE received_at < :timestampMillis")
    suspend fun deleteOlderThan(timestampMillis: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}
