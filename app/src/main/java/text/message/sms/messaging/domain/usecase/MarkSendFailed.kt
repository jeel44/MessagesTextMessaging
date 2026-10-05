package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.ScheduledStatus
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import text.message.sms.messaging.domain.repository.ScheduledSendNotifier
import javax.inject.Inject

/** Records that an outgoing message could not be handed to the carrier. A scheduled message
 * (one with a schedule row) also moves to the FAILED folder and gets a notification -- the user
 * may not be in the app when it was due. */
class MarkSendFailed @Inject constructor(
    private val messageRepository: MessageRepository,
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val notifier: ScheduledSendNotifier,
) : UseCase {

    suspend operator fun invoke(messageId: Long, errorCode: Int) {
        val scheduled = scheduledMessageRepository.find(messageId)
        if (scheduled == null) {
            messageRepository.setDeliveryState(messageId, DeliveryState.FAILED, errorCode)
            return
        }
        // One sent broadcast per SMS part: only the first failure notifies.
        if (scheduled.status == ScheduledStatus.FAILED) return
        scheduledMessageRepository.markFailed(messageId, errorCode)
        notifier.notifyFailed(messageId, scheduled.message.threadId)
        Analytics.scheduledFailed()
    }
}
