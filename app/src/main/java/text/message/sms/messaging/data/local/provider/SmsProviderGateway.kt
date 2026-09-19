package text.message.sms.messaging.data.local.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
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
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    // One malformed row (e.g. a required column genuinely missing on some OEM
                    // provider) must not abort every other row already walked in this cursor --
                    // skip and keep going rather than losing the whole batch to one bad row.
                    try {
                        add(cursor.toMessage())
                    } catch (error: Exception) {
                        Log.w(TAG, "Skipping malformed SMS cursor row", error)
                    }
                }
            }
        }.orEmpty()
    }

    /** The newest [limit] SMS rows of [threadId], regardless of the sync watermark -- used to
     * prioritize a single thread's recent history (e.g. opening a Chat screen before the
     * background sync has reached that thread) ahead of the normal incremental walk. */
    fun queryThreadRecent(threadId: Long, limit: Int): List<Message> {
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
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    try {
                        add(cursor.toMessage())
                    } catch (error: Exception) {
                        Log.w(TAG, "Skipping malformed SMS cursor row", error)
                    }
                }
            }
        }.orEmpty()
    }

    private fun Cursor.toMessage(): Message {
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
            // SUBSCRIPTION_ID (dual-SIM) and ERROR_CODE are both known to be absent from the sms
            // table entirely on some OEM/single-SIM Telephony providers, unlike the columns above
            // -- getColumnIndexOrThrow would abort this whole row (and, without the per-row catch
            // above, every row after it) over a column that is genuinely optional. Missing either
            // falls back to this app's own "unknown"/"no error" sentinels, matching the defaults
            // already used elsewhere (e.g. SendMessage.DEFAULT_SUBSCRIPTION_ID, MmsProviderGateway
            // hardcoding errorCode = 0), rather than throwing.
            subscriptionId = getIntOrDefault(Telephony.Sms.SUBSCRIPTION_ID, default = UNKNOWN_SUBSCRIPTION_ID),
            errorCode = getIntOrDefault(Telephony.Sms.ERROR_CODE, default = NO_ERROR_CODE),
        )
    }

    private fun Cursor.getIntOrDefault(columnName: String, default: Int): Int {
        val index = getColumnIndex(columnName)
        return if (index >= 0) getInt(index) else default
    }

    private companion object {
        const val TAG = "SmsProviderGateway"

        /** Matches the "use whichever SIM the platform considers default" sentinel used
         * throughout this app, e.g. [text.message.sms.messaging.domain.usecase.SendMessage.DEFAULT_SUBSCRIPTION_ID]. */
        const val UNKNOWN_SUBSCRIPTION_ID = -1

        const val NO_ERROR_CODE = 0
    }
}
