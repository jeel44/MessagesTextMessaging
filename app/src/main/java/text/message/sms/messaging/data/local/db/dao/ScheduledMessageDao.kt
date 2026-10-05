package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.ScheduledMessageEntity
import text.message.sms.messaging.data.local.db.entity.ScheduledMessageRow
import text.message.sms.messaging.data.local.db.entity.ScheduledSendTime

/** "Pending" below means the message is still in the QUEUED folder -- a failed or missed one
 * keeps its schedule row (see `MarkSent`) but is never re-armed or counted against the limit. */
@Dao
interface ScheduledMessageDao {

    @Query("SELECT * FROM scheduled_messages WHERE message_id = :messageId")
    suspend fun find(messageId: Long): ScheduledMessageEntity?

    @Query(
        """
        SELECT s.* FROM scheduled_messages s
        JOIN messages m ON m.id = s.message_id
        WHERE m.folder = 'QUEUED'
        """
    )
    suspend fun findAllPending(): List<ScheduledMessageEntity>

    @Query(
        """
        SELECT COUNT(*) FROM scheduled_messages s
        JOIN messages m ON m.id = s.message_id
        WHERE m.folder = 'QUEUED'
        """
    )
    suspend fun countPending(): Int

    /** Every scheduled message joined with its message row, soonest first. */
    @Query(
        """
        SELECT m.*, s.send_at AS scheduled_send_at FROM messages m
        JOIN scheduled_messages s ON s.message_id = m.id
        ORDER BY s.send_at ASC
        """
    )
    fun observeAllWithMessages(): Flow<List<ScheduledMessageRow>>

    @Query(
        """
        SELECT m.*, s.send_at AS scheduled_send_at FROM messages m
        JOIN scheduled_messages s ON s.message_id = m.id
        WHERE m.id = :messageId
        """
    )
    suspend fun findWithMessage(messageId: Long): ScheduledMessageRow?

    /** The send times the thread's bubbles show. */
    @Query(
        """
        SELECT s.message_id, s.send_at FROM scheduled_messages s
        JOIN messages m ON m.id = s.message_id
        WHERE m.thread_id = :threadId
        """
    )
    fun observeSendTimesForThread(threadId: Long): Flow<List<ScheduledSendTime>>

    @Upsert
    suspend fun upsert(entity: ScheduledMessageEntity)

    @Query("DELETE FROM scheduled_messages WHERE message_id = :messageId")
    suspend fun delete(messageId: Long)
}
