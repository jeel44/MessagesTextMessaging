package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageTransmitter
import javax.inject.Inject

/** Sends every scheduled message whose time has come; run from the scheduling alarm. */
class DispatchScheduledMessages @Inject constructor(
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke(nowMillis: Long) {
        transmitter.dispatchDue(nowMillis)
    }
}
