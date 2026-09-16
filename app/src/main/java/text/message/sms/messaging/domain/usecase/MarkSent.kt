package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Records that the carrier accepted an outgoing message. */
class MarkSent @Inject constructor(
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(messageId: Long) {
        messageRepository.setDeliveryState(messageId, DeliveryState.SENT)
    }
}
