package text.message.sms.messaging.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.db.dao.ScheduledMessageDao
import text.message.sms.messaging.data.local.db.entity.ScheduledMessageRow
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.model.ScheduledMessage
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [ScheduledMessageRepository]. Like [LocalMessageRepository], every write lands in
 * the local cache first, then in the system SMS provider (scheduling is SMS-only). */
@Singleton
class LocalScheduledMessageRepository @Inject constructor(
    private val scheduledMessageDao: ScheduledMessageDao,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val smsProviderGateway: SmsProviderGateway,
) : ScheduledMessageRepository {

    override fun observeAll(): Flow<List<ScheduledMessage>> =
        scheduledMessageDao.observeAllWithMessages().map { rows -> rows.map { it.toDomain() } }

    override fun observeSendTimes(threadId: Long): Flow<Map<Long, Long>> =
        scheduledMessageDao.observeSendTimesForThread(threadId)
            .map { rows -> rows.associate { it.messageId to it.sendAtMillis } }

    override suspend fun find(messageId: Long): ScheduledMessage? =
        scheduledMessageDao.findWithMessage(messageId)?.toDomain()

    override suspend fun countPending(): Int = scheduledMessageDao.countPending()

    override suspend fun claim(messageId: Long, from: Set<MessageFolder>, nowMillis: Long): Message? =
        withContext(Dispatchers.IO) {
            val claimed = messageDao.claimScheduled(messageId, from.map { it.name }, nowMillis)
            if (claimed == 0) return@withContext null
            val message = messageDao.findById(messageId)?.toDomain() ?: return@withContext null
            if (message.providerId != 0L) {
                smsProviderGateway.updateScheduled(message.providerId, folder = MessageFolder.OUTBOX, dateMillis = nowMillis)
            }
            moveConversationTo(message, nowMillis)
            message
        }

    override suspend fun markFailed(messageId: Long, errorCode: Int): Unit = withContext(Dispatchers.IO) {
        messageDao.markScheduledFailed(messageId, errorCode)
        val providerId = messageDao.findById(messageId)?.message?.providerId ?: 0L
        if (providerId != 0L) smsProviderGateway.updateScheduled(providerId, folder = MessageFolder.FAILED)
    }

    override suspend fun updateBody(messageId: Long, body: String): Boolean = withContext(Dispatchers.IO) {
        if (messageDao.updateQueuedBody(messageId, body) == 0) return@withContext false
        val providerId = messageDao.findById(messageId)?.message?.providerId ?: 0L
        if (providerId != 0L) smsProviderGateway.updateScheduled(providerId, body = body)
        true
    }

    override suspend fun deleteSchedule(messageId: Long) {
        scheduledMessageDao.delete(messageId)
    }

    /** A claimed message is now the thread's newest -- move the conversation's snippet/time to it,
     * so the inbox sorts by when it really went out. */
    private suspend fun moveConversationTo(message: Message, nowMillis: Long) {
        val existing = conversationDao.findByThreadId(message.threadId)?.conversation ?: return
        if (nowMillis < existing.lastMessageAtMillis) return
        conversationDao.upsert(existing.copy(snippet = message.body, lastMessageAtMillis = nowMillis))
    }

    private fun ScheduledMessageRow.toDomain() = ScheduledMessage(message.toDomain(), sendAtMillis)
}
