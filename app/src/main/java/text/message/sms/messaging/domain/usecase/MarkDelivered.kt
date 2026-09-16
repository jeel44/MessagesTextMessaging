package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Records a delivery report confirming the recipient handset received the message. */
class MarkDelivered @Inject constructor(
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(messageId: Long) {
        messageRepository.setDeliveryState(messageId, DeliveryState.DELIVERED)
    }
}
