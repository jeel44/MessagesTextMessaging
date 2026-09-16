package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import text.message.sms.messaging.data.local.db.entity.ScheduledMessageEntity

@Dao
interface ScheduledMessageDao {

    @Query("SELECT * FROM scheduled_messages WHERE message_id = :messageId")
    suspend fun find(messageId: Long): ScheduledMessageEntity?

    @Query("SELECT * FROM scheduled_messages")
    suspend fun findAll(): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE send_at <= :nowMillis")
    suspend fun findDue(nowMillis: Long): List<ScheduledMessageEntity>

    @Upsert
    suspend fun upsert(entity: ScheduledMessageEntity)

    @Query("DELETE FROM scheduled_messages WHERE message_id = :messageId")
    suspend fun delete(messageId: Long)
}
