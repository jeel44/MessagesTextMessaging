package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Persists an outgoing message locally, then hands it to the radio. */
class SendMessage @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke(params: Params): Message {
        val threadId = params.threadId
            ?: conversationRepository.resolveThreadId(params.addresses)

        val message = messageRepository.insertOutgoing(
            threadId = threadId,
            address = params.addresses.first(),
            body = params.body,
            subscriptionId = params.subscriptionId,
            attachmentUris = params.attachmentUris,
        )

        transmitter.transmit(message)
        conversationRepository.saveDraft(threadId, draft = null)
        return message
    }

    data class Params(
        val addresses: Set<String>,
        val body: String,
        val threadId: Long? = null,
        val subscriptionId: Int = DEFAULT_SUBSCRIPTION_ID,
        val attachmentUris: List<String> = emptyList(),
    )

    companion object {
        /** Sentinel meaning "use whichever SIM the platform considers default". */
        const val DEFAULT_SUBSCRIPTION_ID: Int = -1
    }
}
