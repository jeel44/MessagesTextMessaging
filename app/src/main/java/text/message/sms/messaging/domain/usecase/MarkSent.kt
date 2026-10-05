package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import javax.inject.Inject

/** Records that the carrier accepted an outgoing message. A scheduled message's schedule row is
 * dropped here, not when it's handed to the radio, so a send that fails still has one (see
 * [MarkSendFailed]). Its timestamp was already moved to the real send time when it was claimed
 * (see [ScheduledMessageRepository.claim]). */
class MarkSent @Inject constructor(
    private val messageRepository: MessageRepository,
    private val scheduledMessageRepository: ScheduledMessageRepository,
) : UseCase {

    suspend operator fun invoke(messageId: Long) {
        messageRepository.setDeliveryState(messageId, DeliveryState.SENT)
        scheduledMessageRepository.deleteSchedule(messageId)
    }
}
