package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.MessageTransmitter
import javax.inject.Inject

/**
 * Persists a message in [MessageFolder.QUEUED] -- the same folder the system SMS provider uses
 * for a message waiting to go out -- then registers it to send at a future time.
 */
class ScheduleMessage @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke(params: Params): Message {
        val threadId = params.threadId ?: conversationRepository.resolveThreadId(params.addresses)

        val message = messageRepository.insertOutgoing(
            threadId = threadId,
            address = params.addresses.first(),
            body = params.body,
            subscriptionId = params.subscriptionId,
            attachmentUris = params.attachmentUris,
            folder = MessageFolder.QUEUED,
        )

        transmitter.schedule(message, params.sendAtMillis)
        return message
    }

    data class Params(
        val addresses: Set<String>,
        val body: String,
        val sendAtMillis: Long,
        val threadId: Long? = null,
        val subscriptionId: Int = SendMessage.DEFAULT_SUBSCRIPTION_ID,
        val attachmentUris: List<String> = emptyList(),
    )
}
