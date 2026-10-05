package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.MessageTransmitter
import javax.inject.Inject

/** Cancels a scheduled message (pending, failed or missed): drops its job and schedule row, then
 * deletes the message itself. */
class CancelQueuedMessage @Inject constructor(
    private val messageRepository: MessageRepository,
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke(messageId: Long) {
        transmitter.cancelPending(messageId)
        messageRepository.delete(listOf(messageId))
        Analytics.scheduledCancelled()
    }
}
