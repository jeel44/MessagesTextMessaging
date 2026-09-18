package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.AttachmentEntity

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE message_id = :messageId ORDER BY id ASC")
    fun observeForMessage(messageId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE message_id = :messageId ORDER BY id ASC")
    suspend fun findForMessage(messageId: Long): List<AttachmentEntity>

    /** Every image/video part ever sent or received in [threadId], newest first -- the shared
     * media grid on the conversation info screen. */
    @Query(
        """
        SELECT a.* FROM attachments a
        INNER JOIN messages m ON m.id = a.message_id
        WHERE m.thread_id = :threadId AND (a.mime_type LIKE 'image/%' OR a.mime_type LIKE 'video/%')
        ORDER BY m.received_at DESC
        """
    )
    fun observeMediaForThread(threadId: Long): Flow<List<AttachmentEntity>>

    @Upsert
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<Long>)

    @Query("DELETE FROM attachments")
    suspend fun clear()
}
