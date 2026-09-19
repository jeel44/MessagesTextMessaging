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

    /** The most recent [limit] messages of a thread, newest first -- see [observeThread] for the
     * unbounded version this backs. Callers reverse the result to display oldest-first; ordering
     * DESC here (rather than nesting an ASC subquery limited from the tail) keeps this a plain
     * indexed range scan over `messages(thread_id, received_at)`. */
    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE thread_id = :threadId
        ORDER BY received_at DESC
        LIMIT :limit
        """
    )
    fun observeThreadPageDesc(threadId: Long, limit: Int): Flow<List<MessageWithAttachments>>

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

    /** Every message, oldest-thread-first then oldest-message-first, joined with its attachments
     * -- the full-history read [text.message.sms.messaging.data.repository.LocalBackupRepository]
     * streams out to a backup file. */
    @Transaction
    @Query("SELECT * FROM messages ORDER BY thread_id ASC, received_at ASC")
    suspend fun getAllForBackup(): List<MessageWithAttachments>

    @Query("SELECT COUNT(*) FROM messages WHERE thread_id = :threadId AND is_read = 0")
    suspend fun countUnread(threadId: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE thread_id = :threadId")
    suspend fun countForThread(threadId: Long): Int

    /** The single newest message left in [threadId] -- used after a selection delete to recompute
     * the conversation's snippet/last-message time, since deleting rows out from under it can
     * leave [text.message.sms.messaging.data.local.db.entity.ConversationEntity] pointing at a
     * message that no longer exists. */
    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE thread_id = :threadId
        ORDER BY received_at DESC
        LIMIT 1
        """
    )
    suspend fun findLatestForThread(threadId: Long): MessageWithAttachments?

    /** `EXISTS` rather than `COUNT(*)` so this can short-circuit on the very first row instead
     * of scanning the whole table -- used only to distinguish "empty" from "not empty", never a
     * real count. */
    @Query("SELECT EXISTS(SELECT 1 FROM messages LIMIT 1)")
    suspend fun hasAnyMessages(): Boolean

    /** Which of [providerIds] are already cached, one query for the whole batch -- lets a bulk
     * sync skip already-synced rows without a per-row [findByProviderId] round trip. See
     * [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll]. */
    @Query("SELECT provider_id FROM messages WHERE channel = :channel AND provider_id IN (:providerIds)")
    suspend fun findExistingProviderIds(providerIds: Collection<Long>, channel: MessageChannel): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
