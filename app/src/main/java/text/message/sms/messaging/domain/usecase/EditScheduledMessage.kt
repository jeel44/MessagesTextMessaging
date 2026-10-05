package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.ScheduledStatus
import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Changes a still-pending scheduled message's text and time in place: the same message row and
 * schedule row are updated, then its job is replaced. Never deletes and recreates the message, so
 * its id (and so its place in the thread and any open UI) stays the same.
 */
class EditScheduledMessage @Inject constructor(
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val transmitter: MessageTransmitter,
    private val clock: Clock,
) : UseCase {

    suspend operator fun invoke(messageId: Long, body: String, sendAtMillis: Long): ScheduleResult {
        val scheduled = scheduledMessageRepository.find(messageId)
        val rejection = when {
            scheduled == null || scheduled.status != ScheduledStatus.PENDING -> ScheduleRejection.NOT_PENDING
            body.isBlank() -> ScheduleRejection.EMPTY_TEXT
            !isFarEnoughAhead(sendAtMillis, clock) -> ScheduleRejection.TOO_SOON
            else -> null
        }
        if (rejection != null || scheduled == null) {
            return ScheduleResult.Rejected(rejection ?: ScheduleRejection.NOT_PENDING)
        }

        // Conditional on the message still being QUEUED, so an edit racing the send can't change
        // text that has already gone to the radio.
        if (!scheduledMessageRepository.updateBody(messageId, body)) {
            return ScheduleResult.Rejected(ScheduleRejection.NOT_PENDING)
        }
        val updated = scheduled.message.copy(body = body)
        transmitter.schedule(updated, sendAtMillis)
        return ScheduleResult.Scheduled(updated)
    }
}
