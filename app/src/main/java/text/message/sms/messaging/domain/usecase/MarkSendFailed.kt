package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Records that an outgoing message could not be handed to the carrier. */
class MarkSendFailed @Inject constructor(
    private val messageRepository: MessageRepository,
) : UseCase {

    suspend operator fun invoke(messageId: Long, errorCode: Int) {
        messageRepository.setDeliveryState(messageId, DeliveryState.FAILED, errorCode)
    }
}
