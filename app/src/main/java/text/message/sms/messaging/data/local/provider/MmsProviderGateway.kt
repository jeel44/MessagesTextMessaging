package text.message.sms.messaging.data.local.provider

import android.content.ContentResolver
import androidx.core.net.toUri
import android.provider.Telephony
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.IncomingMessageSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read helpers for `Telephony.Mms`. The platform materialises an inbound MMS and its parts
 * after a WAP push, so this reads the resulting row rather than parsing PDUs itself.
 *
 * Part decoding lands with the receiving pipeline; for now the header row is enough to prove
 * the wiring.
 */
@Singleton
class MmsProviderGateway @Inject constructor(
    private val contentResolver: ContentResolver,
) : IncomingMessageSource {

    override suspend fun readMessage(providerUri: String): Message? {
        val uri = providerUri.toUri()
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.SUBJECT,
            Telephony.Mms.DATE,
            Telephony.Mms.DATE_SENT,
            Telephony.Mms.READ,
            Telephony.Mms.SEEN,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.SUBSCRIPTION_ID,
        )

        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null

            // Telephony stores MMS timestamps in seconds, unlike SMS which uses milliseconds.
            val sentSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE_SENT))
            val receivedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE))

            Message(
                id = 0L,
                threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)),
                providerId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID)),
                channel = MessageChannel.MMS,
                folder = MessageFolder.fromProviderValue(
                    cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)),
                ),
                deliveryState = DeliveryState.NONE,
                address = null,
                body = "",
                subject = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)),
                sentAtMillis = sentSeconds * MILLIS_PER_SECOND,
                receivedAtMillis = receivedSeconds * MILLIS_PER_SECOND,
                isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1,
                isSeen = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.SEEN)) == 1,
                subscriptionId = cursor.getInt(
                    cursor.getColumnIndexOrThrow(Telephony.Mms.SUBSCRIPTION_ID),
                ),
                errorCode = 0,
            )
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
