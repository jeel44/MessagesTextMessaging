package text.message.sms.messaging.domain.repository

import text.message.sms.messaging.domain.model.Message

/**
 * Port for handing outgoing messages to the platform radio. Implemented in the data layer so
 * the use cases never touch `SmsManager` directly.
 */
interface MessageTransmitter {

    /** Sends [message] now, splitting it into parts if the body exceeds a single SMS. */
    suspend fun transmit(message: Message)

    /** Drops a message that is still inside its send-delay window. */
    suspend fun cancelPending(messageId: Long)

    /** Stores a message to be sent at [sendAtMillis] and registers the alarm for it. */
    suspend fun schedule(
        addresses: Set<String>,
        body: String,
        sendAtMillis: Long,
        subscriptionId: Int,
    )

    /** Sends every scheduled message whose time has passed. */
    suspend fun dispatchDue(nowMillis: Long)

    /** Re-registers scheduling alarms, which the platform drops on reboot. */
    suspend fun rearmAlarms()
}
