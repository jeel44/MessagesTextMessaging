package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.model.ScheduledMessage

/**
 * The `scheduled_messages` rows and the scheduling-only writes on their messages. Registering the
 * WorkManager job stays with [MessageTransmitter.schedule].
 */
interface ScheduledMessageRepository {

    /** Every scheduled message, all threads, soonest first. */
    fun observeAll(): Flow<List<ScheduledMessage>>

    /** messageId -> send time for [threadId]'s scheduled messages, for the thread's bubbles. */
    fun observeSendTimes(threadId: Long): Flow<Map<Long, Long>>

    suspend fun find(messageId: Long): ScheduledMessage?

    /** How many are still waiting to send ([MessageFolder.QUEUED]) -- see
     * [text.message.sms.messaging.domain.model.SchedulingRules.MAX_PENDING]. */
    suspend fun countPending(): Int

    /**
     * Atomically moves [messageId] from one of [from] to [MessageFolder.OUTBOX] (state SENDING),
     * stamping it with [nowMillis] as its send time so it sorts where it really went out. Returns
     * the claimed message, or null if it wasn't in [from] -- someone else already claimed it, or it
     * was cancelled -- in which case nothing changed and the caller must not send.
     */
    suspend fun claim(messageId: Long, from: Set<MessageFolder>, nowMillis: Long): Message?

    /** Moves [messageId] to [MessageFolder.FAILED] (state FAILED). Its schedule row stays. */
    suspend fun markFailed(messageId: Long, errorCode: Int = 0)

    /** Replaces the text of a message that is still [MessageFolder.QUEUED], in the local cache
     * and the system provider. Returns false (and changes nothing) once it has been claimed. */
    suspend fun updateBody(messageId: Long, body: String): Boolean

    /** Drops the schedule row only (the message itself stays). */
    suspend fun deleteSchedule(messageId: Long)
}
