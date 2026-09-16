package text.message.sms.messaging.domain.repository

import text.message.sms.messaging.domain.model.Message

/**
 * Port for reading a message the platform has just written into the Telephony provider,
 * such as the MMS the system materialises after a WAP push.
 */
interface IncomingMessageSource {

    /** Reads the provider row at [providerUri], or `null` if it has gone away. */
    suspend fun readMessage(providerUri: String): Message?
}
