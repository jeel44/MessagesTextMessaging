package text.message.sms.messaging.data.local.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import text.message.sms.messaging.data.local.provider.mms.MmsFieldCode
import text.message.sms.messaging.data.local.provider.mms.MmsPart
import text.message.sms.messaging.data.local.provider.mms.MmsRetrieved
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.IncomingMessageSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read and write helpers for `Telephony.Mms`, `Telephony.Mms.Addr` and `Telephony.Mms.Part`.
 *
 * This app is responsible for its own PDU decode/encode (`data.local.provider.mms`) and for the
 * carrier transport (`MmsTransportGateway`, via `SmsManager`); this class is the third piece --
 * mirroring a decoded message into the system provider so it exists there the same way an SMS
 * does, and reading it back the same way [SmsProviderGateway] reads SMS.
 */
@Singleton
class MmsProviderGateway @Inject constructor(
    private val contentResolver: ContentResolver,
    private val attachmentStorage: MmsAttachmentStorage,
) : IncomingMessageSource {

    override suspend fun readMessage(providerUri: String): Message? = withContext(Dispatchers.IO) {
        val uri = providerUri.toUri()
        val providerId = uri.lastPathSegment?.toLongOrNull() ?: return@withContext null
        val header = readHeader(uri) ?: return@withContext null
        val address = readAddress(providerId, MmsFieldCode.FROM)
        val attachments = readParts(providerId)

        header.copy(address = address, attachments = attachments)
    }

    private fun readHeader(uri: Uri): Message? {
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
                sentAtMillis = (if (sentSeconds > 0) sentSeconds else receivedSeconds) * MILLIS_PER_SECOND,
                receivedAtMillis = receivedSeconds * MILLIS_PER_SECOND,
                isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1,
                isSeen = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.SEEN)) == 1,
                // Same dual-SIM gap as Telephony.Sms.SUBSCRIPTION_ID (see SmsProviderGateway):
                // absent entirely from the mms table on some OEM providers, so this falls back to
                // the app's "unknown subscription" sentinel instead of throwing.
                subscriptionId = cursor.getIntOrDefault(
                    Telephony.Mms.SUBSCRIPTION_ID,
                    default = UNKNOWN_SUBSCRIPTION_ID,
                ),
                errorCode = 0,
            )
        }
    }

    private fun readAddress(providerId: Long, addressType: Int): String? {
        val uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "$providerId/addr")
        val projection = arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE)

        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
            val typeColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getInt(typeColumn) == addressType) return@use cursor.getString(addressColumn)
            }
            null
        }
    }

    private fun readAllAddresses(providerId: Long, addressType: Int): List<String> {
        val uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "$providerId/addr")
        val projection = arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE)

        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
            val typeColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
            val result = mutableListOf<String>()
            while (cursor.moveToNext()) {
                if (cursor.getInt(typeColumn) == addressType) result += cursor.getString(addressColumn)
            }
            result
        }.orEmpty()
    }

    private suspend fun readParts(providerId: Long): List<Attachment> {
        val uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "$providerId/part")
        val projection = arrayOf(
            Telephony.Mms.Part._ID,
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part.NAME,
            Telephony.Mms.Part.FILENAME,
            Telephony.Mms.Part.TEXT,
        )

        val rows = contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
            val nameColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.NAME)
            // FILENAME is already only a fallback for a null NAME value below -- some OEM parts
            // tables don't define the column at all, so resolving it must fall back the same way
            // rather than throwing and losing every part in this message over an optional column.
            val fileNameColumn = cursor.getColumnIndex(Telephony.Mms.Part.FILENAME)
            val textColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)

            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PartRow(
                            partId = cursor.getLong(idColumn),
                            contentType = cursor.getString(typeColumn) ?: "application/octet-stream",
                            name = cursor.getString(nameColumn)
                                ?: fileNameColumn.takeIf { it >= 0 }?.let { cursor.getString(it) },
                            text = cursor.getString(textColumn),
                        ),
                    )
                }
            }
        }.orEmpty()

        return rows
            // SMIL layout parts describe how to present the others; nothing in this app's UI
            // reads them, so they are dropped rather than surfaced as a confusing attachment.
            .filterNot { it.contentType == "application/smil" }
            .mapIndexed { index, row -> row.toAttachment(providerId, index) }
    }

    private suspend fun PartRow.toAttachment(providerId: Long, index: Int): Attachment {
        if (contentType.startsWith("text/") && text != null) {
            return Attachment(
                id = 0L,
                messageId = 0L,
                mimeType = contentType,
                fileName = name,
                contentUri = null,
                byteSize = text.length.toLong(),
                text = text,
            )
        }

        // Telephony.Mms.Part.CONTENT_URI is API 29+; the URI shape it points at ("content://
        // mms/part/<id>") has been stable since long before that constant existed, so it is
        // built directly to keep this working back to minSdk 26.
        val partUri = Uri.withAppendedPath(MMS_PART_CONTENT_URI, partId.toString())
        val bytes = contentResolver.openInputStream(partUri)?.use { it.readBytes() } ?: ByteArray(0)
        val part = MmsPart(contentType = contentType, name = name, contentId = null, data = bytes)
        val file = attachmentStorage.savePart(providerId, index, part)

        return Attachment(
            id = 0L,
            messageId = 0L,
            mimeType = contentType,
            fileName = name,
            contentUri = attachmentStorage.contentUriFor(file).toString(),
            byteSize = bytes.size.toLong(),
            text = null,
        )
    }

    private data class PartRow(val partId: Long, val contentType: String, val name: String?, val text: String?)

    private fun Cursor.getIntOrDefault(columnName: String, default: Int): Int {
        val index = getColumnIndex(columnName)
        return if (index >= 0) getInt(index) else default
    }

    /** Writes a downloaded [retrieved] message into the system provider and returns its
     * `content://mms/<id>` Uri, the same shape an incoming SMS gets from [SmsProviderGateway]. */
    fun insertRetrieved(retrieved: MmsRetrieved, threadId: Long, subscriptionId: Int): Uri {
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, retrieved.dateEpochSeconds)
            put(Telephony.Mms.DATE_SENT, retrieved.dateEpochSeconds)
            put(Telephony.Mms.MESSAGE_BOX, MessageFolder.INBOX.providerValue)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.SUBJECT, retrieved.subject)
            put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
            put(Telephony.Mms.TRANSACTION_ID, retrieved.transactionId)
            put(Telephony.Mms.MESSAGE_ID, retrieved.messageId)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
        }

        val mmsUri = contentResolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return Uri.EMPTY
        val providerId = mmsUri.lastPathSegment?.toLongOrNull() ?: return mmsUri

        retrieved.from?.let { insertAddr(providerId, it, MmsFieldCode.FROM) }
        retrieved.to.forEach { insertAddr(providerId, it, MmsFieldCode.TO) }
        retrieved.cc.forEach { insertAddr(providerId, it, MmsFieldCode.CC) }
        retrieved.parts.forEachIndexed { index, part -> insertPart(providerId, index, part) }

        return mmsUri
    }

    /** Writes a not-yet-sent outgoing MMS into the system provider (OUTBOX) so it exists there
     * the same way a queued SMS does, and returns its provider row id. */
    fun insertOutgoingDraft(
        threadId: Long,
        addresses: List<String>,
        subject: String?,
        parts: List<MmsPart>,
        subscriptionId: Int,
    ): Long {
        val nowSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, nowSeconds)
            put(Telephony.Mms.MESSAGE_BOX, MessageFolder.OUTBOX.providerValue)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.SUBJECT, subject)
            put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
        }

        val mmsUri = contentResolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return 0L
        val providerId = mmsUri.lastPathSegment?.toLongOrNull() ?: return 0L

        addresses.forEach { insertAddr(providerId, it, MmsFieldCode.TO) }
        parts.forEachIndexed { index, part -> insertPart(providerId, index, part) }

        return providerId
    }

    fun updateMessageBox(providerId: Long, folder: MessageFolder) {
        val values = ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, folder.providerValue) }
        contentResolver.update(
            Telephony.Mms.CONTENT_URI,
            values,
            "${Telephony.Mms._ID} = ?",
            arrayOf(providerId.toString()),
        )
    }

    /** Provider ids of every MMS newer than [sinceDateSeconds] (the MMS provider's native
     * unit), oldest first -- the shape
     * [text.message.sms.messaging.data.repository.TelephonySyncRepository] needs for an
     * incremental sync. Each id is re-read through [readMessage] to reuse its full
     * header/address/part decode rather than duplicating it here. */
    fun queryIdsSince(sinceDateSeconds: Long): List<Long> {
        val projection = arrayOf(Telephony.Mms._ID)
        return contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            projection,
            "${Telephony.Mms.DATE} > ?",
            arrayOf(sinceDateSeconds.toString()),
            "${Telephony.Mms.DATE} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            buildList {
                while (cursor.moveToNext()) {
                    // Mirrors SmsProviderGateway.querySince(): one bad row's cursor read must not
                    // abort every id already collected from this cursor.
                    try {
                        add(cursor.getLong(idColumn))
                    } catch (error: Exception) {
                        Log.w(TAG, "Skipping malformed MMS id cursor row", error)
                    }
                }
            }
        }.orEmpty()
    }

    /** All recipient addresses recorded against [providerId], excluding the sender. Used when
     * building the recipient set for a conversation thread from a resynced MMS row. */
    fun readRecipients(providerId: Long): List<String> =
        readAllAddresses(providerId, MmsFieldCode.TO) + readAllAddresses(providerId, MmsFieldCode.CC)

    private fun insertAddr(providerId: Long, address: String, type: Int) {
        val uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "$providerId/addr")
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, CHARSET_UTF_8)
        }
        contentResolver.insert(uri, values)
    }

    private fun insertPart(providerId: Long, sequence: Int, part: MmsPart) {
        val uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "$providerId/part")
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.SEQ, sequence)
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            part.name?.let { put(Telephony.Mms.Part.NAME, it) }
            if (part.isText) {
                put(Telephony.Mms.Part.TEXT, String(part.data, Charsets.UTF_8))
            }
        }

        val partUri = contentResolver.insert(uri, values) ?: return
        if (!part.isText) {
            contentResolver.openOutputStream(partUri)?.use { it.write(part.data) }
        }
    }

    private companion object {
        const val TAG = "MmsProviderGateway"

        const val MILLIS_PER_SECOND = 1_000L

        // IANA MIBenum for UTF-8, the value Telephony.Mms.Addr.CHARSET expects.
        const val CHARSET_UTF_8 = 106

        val MMS_PART_CONTENT_URI: Uri = "content://mms/part".toUri()

        /** Matches the "use whichever SIM the platform considers default" sentinel used
         * throughout this app, e.g. [text.message.sms.messaging.domain.usecase.SendMessage.DEFAULT_SUBSCRIPTION_ID]. */
        const val UNKNOWN_SUBSCRIPTION_ID = -1
    }
}
