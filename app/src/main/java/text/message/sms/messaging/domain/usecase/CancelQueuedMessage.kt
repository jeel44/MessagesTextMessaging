package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Cancels a message that is still waiting out its send delay. */
class CancelQueuedMessage @Inject constructor(
    private val messageRepository: MessageRepository,
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke(messageId: Long) {
        transmitter.cancelPending(messageId)
        messageRepository.delete(listOf(messageId))
    }
}
