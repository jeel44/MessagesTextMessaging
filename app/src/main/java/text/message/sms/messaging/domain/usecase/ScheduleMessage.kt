package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.repository.MessageTransmitter
import javax.inject.Inject

/** Registers an alarm so a composed message is sent at a future time. */
class ScheduleMessage @Inject constructor(
    private val transmitter: MessageTransmitter,
) : UseCase {

    suspend operator fun invoke(params: Params) {
        transmitter.schedule(
            addresses = params.addresses,
            body = params.body,
            sendAtMillis = params.sendAtMillis,
            subscriptionId = params.subscriptionId,
        )
    }

    data class Params(
        val addresses: Set<String>,
        val body: String,
        val sendAtMillis: Long,
        val subscriptionId: Int = SendMessage.DEFAULT_SUBSCRIPTION_ID,
    )
}
