package text.message.sms.messaging.data.repository

import android.content.ContentResolver
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.db.entity.AttachmentEntity
import text.message.sms.messaging.data.local.db.entity.MessageWithAttachments
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.data.local.provider.mms.MmsPart
import text.message.sms.messaging.domain.model.BackupResult
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.BackupRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.SyncRepository
import text.message.sms.messaging.service.DefaultSmsAppGuard
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams the full SMS/MMS history to/from a single JSON file using [JsonWriter]/[JsonReader] --
 * no third-party JSON library, and no full-document tree held in memory, since a real message
 * history plus inline attachment bytes can be large. Each message entry carries its own thread's
 * participant addresses (see [ConversationDao.getAllWithRecipients]) rather than the local
 * `thread_id`, since a restore -- possibly onto a different device or after a reinstall -- cannot
 * assume the system provider will hand back the same thread id it exported.
 */
@Singleton
class LocalBackupRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val smsProviderGateway: SmsProviderGateway,
    private val mmsProviderGateway: MmsProviderGateway,
    private val conversationRepository: ConversationRepository,
    private val syncRepository: SyncRepository,
    private val defaultSmsAppGuard: DefaultSmsAppGuard,
) : BackupRepository {

    override suspend fun export(destinationUri: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val addressesByThread = conversationDao.getAllWithRecipients()
                .associate { it.conversation.threadId to it.recipients.map { recipient -> recipient.address } }
            val rows = messageDao.getAllForBackup()

            val opened = contentResolver.openOutputStream(destinationUri.toUri())?.use { output ->
                JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                    writer.beginObject()
                    writer.name(FIELD_VERSION).value(BACKUP_VERSION)
                    writer.name(FIELD_CREATED_AT).value(System.currentTimeMillis())
                    writer.name(FIELD_MESSAGES)
                    writer.beginArray()
                    rows.forEach { row ->
                        writer.writeMessage(row, addressesByThread[row.message.threadId].orEmpty())
                    }
                    writer.endArray()
                    writer.endObject()
                }
            } != null

            if (!opened) return@withContext BackupResult.Failure.DestinationUnavailable

            BackupResult.Success(rows.size)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Backup export failed", error)
            BackupResult.Failure.Error(error.describe())
        }
    }

    override suspend fun import(sourceUri: String): BackupResult = withContext(Dispatchers.IO) {
        if (!defaultSmsAppGuard.isDefault || !defaultSmsAppGuard.hasCoreSmsPermissions) {
            return@withContext BackupResult.Failure.NotDefaultSmsApp
        }

        try {
            var restoredCount = 0
            val opened = contentResolver.openInputStream(sourceUri.toUri())?.use { input ->
                JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == FIELD_MESSAGES) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                restoreMessage(reader)
                                restoredCount++
                            }
                            reader.endArray()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            } != null

            if (!opened) return@withContext BackupResult.Failure.DestinationUnavailable

            // Restored rows carry their original, possibly old, timestamps -- an ordinary
            // incremental syncAll would only look past the current watermark and silently skip
            // every one of them. See resetSyncWatermark's doc.
            syncRepository.resetSyncWatermark()
            syncRepository.syncAll()

            BackupResult.Success(restoredCount)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Backup import failed", error)
            BackupResult.Failure.Error(error.describe())
        }
    }

    private fun JsonWriter.writeMessage(row: MessageWithAttachments, addresses: List<String>) {
        val message = row.message
        beginObject()
        name(FIELD_CHANNEL).value(message.channel.name)
        name(FIELD_FOLDER).value(message.folder.name)
        name(FIELD_ADDRESS).nullableValue(message.address)
        name(FIELD_ADDRESSES)
        beginArray()
        addresses.forEach { value(it) }
        endArray()
        name(FIELD_BODY).value(message.body)
        name(FIELD_SUBJECT).nullableValue(message.subject)
        name(FIELD_SENT_AT).value(message.sentAtMillis)
        name(FIELD_RECEIVED_AT).value(message.receivedAtMillis)
        name(FIELD_IS_READ).value(message.isRead)
        name(FIELD_IS_SEEN).value(message.isSeen)
        name(FIELD_SUBSCRIPTION_ID).value(message.subscriptionId)
        name(FIELD_ERROR_CODE).value(message.errorCode)
        name(FIELD_ATTACHMENTS)
        beginArray()
        row.attachments.forEach { writeAttachment(it) }
        endArray()
        endObject()
    }

    private fun JsonWriter.writeAttachment(attachment: AttachmentEntity) {
        beginObject()
        name(FIELD_MIME_TYPE).value(attachment.mimeType)
        name(FIELD_FILE_NAME).nullableValue(attachment.fileName)
        name(FIELD_TEXT).nullableValue(attachment.text)
        name(FIELD_DATA).nullableValue(attachment.contentUri?.let(::readAttachmentBase64))
        endObject()
    }

    private fun JsonWriter.nullableValue(text: String?): JsonWriter =
        if (text != null) value(text) else nullValue()

    /** Missing/unreadable source data (the file behind [contentUri] was cleared, e.g. by
     * [text.message.sms.messaging.data.local.provider.MmsAttachmentStorage]'s own storage) must
     * not abort the whole export -- the attachment is written with a null `data` instead, same as
     * every other per-row skip elsewhere in this codebase's provider gateways. */
    private fun readAttachmentBase64(contentUri: String): String? =
        try {
            contentResolver.openInputStream(contentUri.toUri())?.use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Skipping unreadable attachment at $contentUri", error)
            null
        }

    private suspend fun restoreMessage(reader: JsonReader) {
        var channel = MessageChannel.SMS
        var folder = MessageFolder.INBOX
        var address: String? = null
        var addresses: List<String> = emptyList()
        var body = ""
        var subject: String? = null
        var sentAt = 0L
        var receivedAt = 0L
        var isRead = false
        var isSeen = false
        var subscriptionId = UNKNOWN_SUBSCRIPTION_ID
        var errorCode = 0
        var attachments: List<BackupAttachment> = emptyList()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                FIELD_CHANNEL -> channel = runCatching { MessageChannel.valueOf(reader.nextString()) }
                    .getOrDefault(MessageChannel.SMS)
                FIELD_FOLDER -> folder = runCatching { MessageFolder.valueOf(reader.nextString()) }
                    .getOrDefault(MessageFolder.INBOX)
                FIELD_ADDRESS -> address = reader.nextStringOrNull()
                FIELD_ADDRESSES -> addresses = reader.readStringArray()
                FIELD_BODY -> body = reader.nextString()
                FIELD_SUBJECT -> subject = reader.nextStringOrNull()
                FIELD_SENT_AT -> sentAt = reader.nextLong()
                FIELD_RECEIVED_AT -> receivedAt = reader.nextLong()
                FIELD_IS_READ -> isRead = reader.nextBoolean()
                FIELD_IS_SEEN -> isSeen = reader.nextBoolean()
                FIELD_SUBSCRIPTION_ID -> subscriptionId = reader.nextInt()
                FIELD_ERROR_CODE -> errorCode = reader.nextInt()
                FIELD_ATTACHMENTS -> attachments = reader.readAttachments()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        // No participant survives means there is nothing to resolve a thread from -- mirrors
        // TelephonySyncRepository.syncSms/syncMms's own "no address, cannot resolve a thread"
        // skip rather than inserting a row with nothing to attach it to.
        val threadAddresses = addresses.ifEmpty { listOfNotNull(address) }
        if (threadAddresses.isEmpty()) return

        val threadId = conversationRepository.resolveThreadId(threadAddresses.toSet())

        when (channel) {
            MessageChannel.SMS -> {
                smsProviderGateway.insert(
                    Message(
                        id = 0L,
                        threadId = threadId,
                        providerId = 0L,
                        channel = MessageChannel.SMS,
                        folder = folder,
                        deliveryState = DeliveryState.NONE,
                        address = address ?: threadAddresses.first(),
                        body = body,
                        subject = null,
                        sentAtMillis = sentAt,
                        receivedAtMillis = receivedAt,
                        isRead = isRead,
                        isSeen = isSeen,
                        subscriptionId = subscriptionId,
                        errorCode = errorCode,
                    ),
                )
            }

            MessageChannel.MMS -> {
                mmsProviderGateway.insertForRestore(
                    threadId = threadId,
                    folder = folder,
                    fromAddress = address,
                    toAddresses = threadAddresses.filterNot { it == address },
                    subject = subject,
                    dateEpochSeconds = receivedAt / MILLIS_PER_SECOND,
                    isRead = isRead,
                    isSeen = isSeen,
                    subscriptionId = subscriptionId,
                    parts = attachments.map { it.toMmsPart() },
                )
            }
        }
    }

    private fun BackupAttachment.toMmsPart(): MmsPart = MmsPart(
        contentType = mimeType,
        name = fileName,
        contentId = null,
        data = when {
            text != null -> text.toByteArray(Charsets.UTF_8)
            data != null -> Base64.decode(data, Base64.NO_WRAP)
            else -> ByteArray(0)
        },
    )

    private data class BackupAttachment(
        val mimeType: String,
        val fileName: String?,
        val text: String?,
        val data: String?,
    )

    private fun JsonReader.readAttachments(): List<BackupAttachment> {
        val result = mutableListOf<BackupAttachment>()
        beginArray()
        while (hasNext()) {
            var mimeType = "application/octet-stream"
            var fileName: String? = null
            var text: String? = null
            var data: String? = null
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    FIELD_MIME_TYPE -> mimeType = nextString()
                    FIELD_FILE_NAME -> fileName = nextStringOrNull()
                    FIELD_TEXT -> text = nextStringOrNull()
                    FIELD_DATA -> data = nextStringOrNull()
                    else -> skipValue()
                }
            }
            endObject()
            result += BackupAttachment(mimeType, fileName, text, data)
        }
        endArray()
        return result
    }

    private fun JsonReader.readStringArray(): List<String> {
        val result = mutableListOf<String>()
        beginArray()
        while (hasNext()) result += nextString()
        endArray()
        return result
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextString()
        }

    /** The real exception, not a generic string -- matches
     * [TelephonySyncRepository.describe]'s own reasoning: this is what surfaces in the failure
     * toast, so it must be diagnosable from that alone. */
    private fun Throwable?.describe(): String =
        this?.let { "${it::class.simpleName}: ${it.message}" } ?: "unknown error"

    private companion object {
        const val TAG = "LocalBackupRepository"
        const val BACKUP_VERSION = 1
        const val MILLIS_PER_SECOND = 1_000L
        const val UNKNOWN_SUBSCRIPTION_ID = -1

        const val FIELD_VERSION = "version"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_MESSAGES = "messages"
        const val FIELD_CHANNEL = "channel"
        const val FIELD_FOLDER = "folder"
        const val FIELD_ADDRESS = "address"
        const val FIELD_ADDRESSES = "addresses"
        const val FIELD_BODY = "body"
        const val FIELD_SUBJECT = "subject"
        const val FIELD_SENT_AT = "sentAt"
        const val FIELD_RECEIVED_AT = "receivedAt"
        const val FIELD_IS_READ = "isRead"
        const val FIELD_IS_SEEN = "isSeen"
        const val FIELD_SUBSCRIPTION_ID = "subscriptionId"
        const val FIELD_ERROR_CODE = "errorCode"
        const val FIELD_ATTACHMENTS = "attachments"
        const val FIELD_MIME_TYPE = "mimeType"
        const val FIELD_FILE_NAME = "fileName"
        const val FIELD_TEXT = "text"
        const val FIELD_DATA = "data"
    }
}
