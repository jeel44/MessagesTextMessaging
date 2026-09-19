package text.message.sms.messaging.data.repository

import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.data.local.db.dao.AttachmentDao
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.db.entity.AttachmentEntity
import text.message.sms.messaging.data.local.provider.MmsAttachmentStorage
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.data.local.provider.mms.MmsPart
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.data.mapper.toEntity
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [MessageRepository]. Writes land in the local cache first so the UI updates
 * immediately, then in the system provider, which the app owns as default SMS handler -- except
 * for a new outgoing MMS, where the provider row has to exist before its parts can be attached,
 * so that direction runs provider-first (see [insertOutgoing]).
 */
@Singleton
class LocalMessageRepository @Inject constructor(
    private val database: MessagingDatabase,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val attachmentDao: AttachmentDao,
    private val smsProviderGateway: SmsProviderGateway,
    private val mmsProviderGateway: MmsProviderGateway,
    private val attachmentStorage: MmsAttachmentStorage,
    private val contentResolver: ContentResolver,
) : MessageRepository {

    override fun observeThread(threadId: Long): Flow<List<Message>> =
        messageDao.observeThread(threadId).map { rows -> rows.map { it.toDomain() } }

    override fun observeThreadPage(threadId: Long, limit: Int): Flow<List<Message>> =
        messageDao.observeThreadPageDesc(threadId, limit)
            .map { rows -> rows.asReversed().map { it.toDomain() } }

    override suspend fun findById(id: Long): Message? =
        messageDao.findById(id)?.toDomain()

    override suspend fun findByProviderId(providerId: Long, channel: MessageChannel): Message? =
        messageDao.findByProviderId(providerId, channel)?.toDomain()

    override suspend fun insertOutgoing(
        threadId: Long,
        address: String,
        body: String,
        subscriptionId: Int,
        attachmentUris: List<String>,
        folder: MessageFolder,
    ): Message = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        if (attachmentUris.isEmpty()) {
            val draft = Message(
                id = 0L,
                threadId = threadId,
                providerId = 0L,
                channel = MessageChannel.SMS,
                folder = folder,
                deliveryState = DeliveryState.PENDING,
                address = address,
                body = body,
                subject = null,
                sentAtMillis = now,
                receivedAtMillis = now,
                isRead = true,
                isSeen = true,
                subscriptionId = subscriptionId,
                errorCode = 0,
            )

            val localId = messageDao.insert(draft.toEntity())
            val providerId = smsProviderGateway.insert(draft.copy(id = localId))
            messageDao.setProviderId(localId, providerId)
            refreshConversationCounters(threadId, body, now)

            return@withContext draft.copy(id = localId, providerId = providerId)
        }

        // MMS: the system provider needs a parent row to attach parts to, so it is written
        // first and the local cache mirrors the result, the reverse of the SMS order above.
        val parts = attachmentUris.map { readPart(it) }
        val providerId = mmsProviderGateway.insertOutgoingDraft(
            threadId = threadId,
            addresses = listOf(address),
            subject = null,
            parts = parts,
            subscriptionId = subscriptionId,
        )

        val draft = Message(
            id = 0L,
            threadId = threadId,
            providerId = providerId,
            channel = MessageChannel.MMS,
            folder = folder,
            deliveryState = DeliveryState.PENDING,
            address = address,
            body = body,
            subject = null,
            sentAtMillis = now,
            receivedAtMillis = now,
            isRead = true,
            isSeen = true,
            subscriptionId = subscriptionId,
            errorCode = 0,
        )

        val localId = messageDao.insert(draft.toEntity())
        val attachments = persistPartsLocally(providerId, localId, parts)
        refreshConversationCounters(threadId, body.ifBlank { parts.firstOrNull()?.name.orEmpty() }, now)

        draft.copy(id = localId, attachments = attachments)
    }

    override suspend fun insertIncoming(message: Message, notifyConversation: Boolean): Message = withContext(Dispatchers.IO) {
        database.withTransaction {
            var localId = messageDao.insert(message.toEntity())
            if (localId == -1L) {
                localId = messageDao.findByProviderId(message.providerId, message.channel)?.message?.id ?: localId
            }
            val providerId = message.providerId.takeIf { it != 0L }
                ?: smsProviderGateway.insert(message.copy(id = localId))
            messageDao.setProviderId(localId, providerId)

            val attachments = if (message.attachments.isEmpty()) {
                emptyList()
            } else {
                attachmentDao.upsertAll(
                    message.attachments.map { it.copy(messageId = localId).toEntity() },
                )
                attachmentDao.findForMessage(localId).map { it.toDomain() }
            }

            if (notifyConversation) {
                refreshConversationCounters(message.threadId, message.body, message.receivedAtMillis)
            }
            message.copy(id = localId, providerId = providerId, attachments = attachments)
        }
    }

    /**
     * See [MessageRepository.insertIncomingBatch]. Every entry in [messages] must already carry
     * a non-zero [Message.providerId] -- unlike [insertIncoming], this never falls back to
     * writing a provider row, since a sync-sourced message always already has one.
     */
    override suspend fun insertIncomingBatch(messages: List<Message>): List<Message> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext emptyList()
        database.withTransaction {
            messages.map { message ->
                var localId = messageDao.insert(message.toEntity())
                if (localId == -1L) {
                    localId = messageDao.findByProviderId(message.providerId, message.channel)?.message?.id ?: localId
                }

                val attachments = if (message.attachments.isEmpty()) {
                    emptyList()
                } else {
                    attachmentDao.upsertAll(
                        message.attachments.map { it.copy(messageId = localId).toEntity() },
                    )
                    attachmentDao.findForMessage(localId).map { it.toDomain() }
                }

                message.copy(id = localId, attachments = attachments)
            }
        }
    }

    override suspend fun findExistingProviderIds(providerIds: Collection<Long>, channel: MessageChannel): Set<Long> =
        withContext(Dispatchers.IO) {
            if (providerIds.isEmpty()) emptySet() else messageDao.findExistingProviderIds(providerIds, channel).toSet()
        }

    override suspend fun setDeliveryState(messageId: Long, state: DeliveryState, errorCode: Int) {
        messageDao.setDeliveryState(messageId, state, errorCode)
    }

    override suspend fun setRead(threadIds: Collection<Long>, read: Boolean) {
        messageDao.setRead(threadIds, read)
        threadIds.forEach { threadId ->
            conversationDao.setUnreadCount(threadId, messageDao.countUnread(threadId))
        }
    }

    override suspend fun setSeen(threadIds: Collection<Long>) {
        messageDao.setSeen(threadIds)
    }

    override suspend fun countForThread(threadId: Long): Int = messageDao.countForThread(threadId)

    override suspend fun findIdsForThreads(threadIds: Collection<Long>): List<Long> =
        if (threadIds.isEmpty()) emptyList() else messageDao.findIdsForThreads(threadIds)

    override suspend fun findLatestForThread(threadId: Long): Message? =
        messageDao.findLatestForThread(threadId)?.toDomain()

    override suspend fun delete(messageIds: Collection<Long>): Unit = withContext(Dispatchers.IO) {
        if (messageIds.isEmpty()) return@withContext
        messageIds.forEach { id ->
            val row = messageDao.findById(id) ?: return@forEach
            val providerId = row.message.providerId
            if (providerId != 0L) {
                when (row.message.channel) {
                    MessageChannel.SMS -> smsProviderGateway.delete(providerId)
                    MessageChannel.MMS -> mmsProviderGateway.delete(providerId)
                }
            }
            if (row.message.channel == MessageChannel.MMS) {
                attachmentStorage.deleteMessageDir(providerId)
            }
        }
        messageDao.delete(messageIds)
    }

    override suspend fun deleteOlderThan(timestampMillis: Long) {
        messageDao.deleteOlderThan(timestampMillis)
    }

    override fun search(query: String): Flow<List<Message>> =
        messageDao.search(query).map { rows -> rows.map { it.toDomain() } }

    override suspend fun hasAnyMessages(): Boolean = messageDao.hasAnyMessages()

    private suspend fun persistPartsLocally(
        providerId: Long,
        localMessageId: Long,
        parts: List<MmsPart>,
    ): List<Attachment> {
        val entities = parts.mapIndexed { index, part ->
            if (part.isText) {
                AttachmentEntity(
                    messageId = localMessageId,
                    mimeType = part.contentType,
                    fileName = part.name,
                    contentUri = null,
                    byteSize = part.data.size.toLong(),
                    text = String(part.data, Charsets.UTF_8),
                )
            } else {
                val file = attachmentStorage.savePart(providerId, index, part)
                AttachmentEntity(
                    messageId = localMessageId,
                    mimeType = part.contentType,
                    fileName = part.name,
                    contentUri = attachmentStorage.contentUriFor(file).toString(),
                    byteSize = part.data.size.toLong(),
                    text = null,
                )
            }
        }

        attachmentDao.upsertAll(entities)
        return attachmentDao.findForMessage(localMessageId).map { it.toDomain() }
    }

    private fun readPart(uriString: String): MmsPart {
        val uri = uriString.toUri()
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        var displayName: String? = null

        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) displayName = cursor.getString(column)
        }

        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        return MmsPart(contentType = mimeType, name = displayName, contentId = null, data = bytes)
    }

    private suspend fun refreshConversationCounters(
        threadId: Long,
        snippet: String,
        timestampMillis: Long,
    ) {
        val existing = conversationDao.findByThreadId(threadId)?.conversation ?: return
        val isNewer = timestampMillis >= existing.lastMessageAtMillis
        conversationDao.upsert(
            existing.copy(
                snippet = if (isNewer) snippet else existing.snippet,
                lastMessageAtMillis = if (isNewer) timestampMillis else existing.lastMessageAtMillis,
                unreadCount = messageDao.countUnread(threadId),
            ),
        )
    }
}
