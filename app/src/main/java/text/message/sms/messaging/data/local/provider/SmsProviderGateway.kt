package text.message.sms.messaging.data.local.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.Telephony
import text.message.sms.messaging.domain.model.Message
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
}
