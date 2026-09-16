package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.data.mapper.toEntity
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [MessageRepository]. Writes land in the local cache first so the UI updates
 * immediately, then in the system provider, which the app owns as default SMS handler.
 */
@Singleton
class LocalMessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val smsProviderGateway: SmsProviderGateway,
) : MessageRepository {

    override fun observeThread(threadId: Long): Flow<List<Message>> =
        messageDao.observeThread(threadId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findById(id: Long): Message? =
        messageDao.findById(id)?.toDomain()

    override suspend fun findByProviderId(providerId: Long): Message? =
        messageDao.findByProviderId(providerId, MessageChannel.SMS)?.toDomain()

    override suspend fun insertOutgoing(
        threadId: Long,
        address: String,
        body: String,
        subscriptionId: Int,
        attachmentUris: List<String>,
    ): Message {
        val now = System.currentTimeMillis()
        val draft = Message(
            id = 0L,
            threadId = threadId,
            providerId = 0L,
            channel = if (attachmentUris.isEmpty()) MessageChannel.SMS else MessageChannel.MMS,
            folder = MessageFolder.OUTBOX,
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

        return draft.copy(id = localId, providerId = providerId)
    }

    override suspend fun insertIncoming(message: Message): Message {
        val localId = messageDao.insert(message.toEntity())
        val providerId = message.providerId.takeIf { it != 0L }
            ?: smsProviderGateway.insert(message.copy(id = localId))
        messageDao.setProviderId(localId, providerId)
        refreshConversationCounters(message.threadId, message.body, message.receivedAtMillis)

        return message.copy(id = localId, providerId = providerId)
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

    override suspend fun delete(messageIds: Collection<Long>) {
        messageDao.delete(messageIds)
    }

    override suspend fun deleteOlderThan(timestampMillis: Long) {
        messageDao.deleteOlderThan(timestampMillis)
    }

    override fun search(query: String): Flow<List<Message>> =
        messageDao.search(query).map { rows -> rows.map { it.toDomain() } }

    private suspend fun refreshConversationCounters(
        threadId: Long,
        snippet: String,
        timestampMillis: Long,
    ) {
        val existing = conversationDao.findByThreadId(threadId)?.conversation ?: return
        conversationDao.upsert(
            existing.copy(
                snippet = snippet,
                lastMessageAtMillis = timestampMillis,
                unreadCount = messageDao.countUnread(threadId),
            ),
        )
    }
}
