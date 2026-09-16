package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder

/** Read and write access to individual SMS/MMS entries. */
interface MessageRepository {

    fun observeThread(threadId: Long): Flow<List<Message>>

    suspend fun findById(id: Long): Message?

    /** [channel] is required because the same provider row id space is not shared between the
     * SMS and MMS tables -- SMS row 12 and MMS row 12 are unrelated messages. */
    suspend fun findByProviderId(providerId: Long, channel: MessageChannel): Message?

    /**
     * Persists an outgoing message locally (including [attachmentUris], resolved to real
     * [text.message.sms.messaging.domain.model.Attachment] rows) and returns the stored row.
     *
     * [folder] defaults to [MessageFolder.OUTBOX] -- ready to hand to the radio now. Scheduled
     * sends pass [MessageFolder.QUEUED] instead, the same folder the system SMS provider itself
     * uses for a message waiting to go out, so the message is visibly "queued" rather than
     * "sending" until its scheduled time arrives.
     */
    suspend fun insertOutgoing(
        threadId: Long,
        address: String,
        body: String,
        subscriptionId: Int,
        attachmentUris: List<String> = emptyList(),
        folder: MessageFolder = MessageFolder.OUTBOX,
    ): Message

    /** Persists an inbound message, including any [Message.attachments] it already carries
     * (remapped onto the row's real id), and returns the stored row. */
    suspend fun insertIncoming(message: Message): Message

    suspend fun setDeliveryState(messageId: Long, state: DeliveryState, errorCode: Int = 0)

    suspend fun setRead(threadIds: Collection<Long>, read: Boolean)

    suspend fun setSeen(threadIds: Collection<Long>)

    suspend fun delete(messageIds: Collection<Long>)

    suspend fun deleteOlderThan(timestampMillis: Long)

    fun search(query: String): Flow<List<Message>>
}
