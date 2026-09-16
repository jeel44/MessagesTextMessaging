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

    @Upsert
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<Long>)

    @Query("DELETE FROM attachments")
    suspend fun clear()
}
