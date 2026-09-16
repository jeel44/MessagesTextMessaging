package text.message.sms.messaging.data.local.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.Telephony
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read and write helpers for `Telephony.Sms`.
 *
 * Only the default SMS app may write here, so every call assumes that role has been granted;
 * see `service.DefaultSmsAppGuard`.
 */
@Singleton
class SmsProviderGateway @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    /** Inserts [message] into the system SMS table and returns the new provider row id. */
    fun insert(message: Message): Long {
        val values = ContentValues().apply {
            put(Telephony.Sms.THREAD_ID, message.threadId)
            put(Telephony.Sms.ADDRESS, message.address)
            put(Telephony.Sms.BODY, message.body)
            put(Telephony.Sms.DATE, message.receivedAtMillis)
            put(Telephony.Sms.DATE_SENT, message.sentAtMillis)
            put(Telephony.Sms.READ, if (message.isRead) 1 else 0)
            put(Telephony.Sms.SEEN, if (message.isSeen) 1 else 0)
            put(Telephony.Sms.TYPE, message.folder.providerValue)
            put(Telephony.Sms.SUBSCRIPTION_ID, message.subscriptionId)
        }

        val uri = contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull() ?: 0L
    }

    /** Moves an existing provider row between folders, e.g. outbox to sent. */
    fun updateFolder(providerId: Long, folder: MessageFolder) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, folder.providerValue)
        }
        contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms._ID} = ?",
            arrayOf(providerId.toString()),
        )
    }

    fun delete(providerId: Long) {
        contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms._ID} = ?",
            arrayOf(providerId.toString()),
        )
    }

    /** Every SMS newer than [sinceDateMillis], oldest first -- the shape
     * [text.message.sms.messaging.data.repository.TelephonySyncRepository] needs for an
     * incremental sync. */
    fun querySince(sinceDateMillis: Long): List<Message> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.TYPE,
            Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.ERROR_CODE,
        )

        return contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(sinceDateMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toMessage()) } }.orEmpty()
    }

    private fun android.database.Cursor.toMessage(): Message {
        val dateSent = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE_SENT))
        val dateReceived = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE))
        return Message(
            id = 0L,
            threadId = getLong(getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
            providerId = getLong(getColumnIndexOrThrow(Telephony.Sms._ID)),
            channel = MessageChannel.SMS,
            folder = MessageFolder.fromProviderValue(getInt(getColumnIndexOrThrow(Telephony.Sms.TYPE))),
            deliveryState = DeliveryState.NONE,
            address = getString(getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
            body = getString(getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty(),
            subject = null,
            sentAtMillis = if (dateSent > 0) dateSent else dateReceived,
            receivedAtMillis = dateReceived,
            isRead = getInt(getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
            isSeen = getInt(getColumnIndexOrThrow(Telephony.Sms.SEEN)) == 1,
            subscriptionId = getInt(getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)),
            errorCode = getInt(getColumnIndexOrThrow(Telephony.Sms.ERROR_CODE)),
        )
    }
}
