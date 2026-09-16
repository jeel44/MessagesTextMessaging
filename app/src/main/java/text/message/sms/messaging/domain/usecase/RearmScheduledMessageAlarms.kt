package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageTransmitter
import javax.inject.Inject

/** Re-registers scheduling alarms after a reboot, since alarms do not survive one. */
class RearmScheduledMessageAlarms @Inject constructor(
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke() {
        transmitter.rearmAlarms()
    }
}
