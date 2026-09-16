package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message

/** Read and write access to individual SMS/MMS entries. */
interface MessageRepository {

    fun observeThread(threadId: Long): Flow<List<Message>>

    suspend fun findById(id: Long): Message?

    suspend fun findByProviderId(providerId: Long): Message?

    /** Persists an outgoing message locally and returns the stored row. */
    suspend fun insertOutgoing(
        threadId: Long,
        address: String,
        body: String,
        subscriptionId: Int,
        attachmentUris: List<String> = emptyList(),
    ): Message

    /** Persists an inbound message and returns the stored row. */
    suspend fun insertIncoming(message: Message): Message

    suspend fun setDeliveryState(messageId: Long, state: DeliveryState, errorCode: Int = 0)

    suspend fun setRead(threadIds: Collection<Long>, read: Boolean)

    suspend fun setSeen(threadIds: Collection<Long>)

    suspend fun delete(messageIds: Collection<Long>)

    suspend fun deleteOlderThan(timestampMillis: Long)

    fun search(query: String): Flow<List<Message>>
}
