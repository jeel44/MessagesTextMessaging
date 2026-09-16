package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Re-queues a message that previously failed to send. */
class RetrySendMessage @Inject constructor(
    private val messageRepository: MessageRepository,
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke(messageId: Long) {
        val message = messageRepository.findById(messageId) ?: return
        messageRepository.setDeliveryState(messageId, DeliveryState.PENDING)
        transmitter.transmit(message)
    }
}
