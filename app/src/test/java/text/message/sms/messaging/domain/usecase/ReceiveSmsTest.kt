package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.data.local.db.dao.AttachmentDao
import text.message.sms.messaging.data.local.db.dao.BlockedNumberDao
import text.message.sms.messaging.data.local.db.dao.ContactDao
import text.message.sms.messaging.data.local.db.dao.ContactGroupDao
import text.message.sms.messaging.data.local.db.dao.ConversationDao
import text.message.sms.messaging.data.local.db.dao.MessageDao
import text.message.sms.messaging.data.local.db.dao.ScheduledMessageDao
import text.message.sms.messaging.data.local.db.dao.SyncStateDao
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationCounterUpdate
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.domain.repository.MessageRepository

/**
 * Regression coverage for the rule added alongside [IncomingMessageNotifier]: a blocked sender's
 * message must never reach storage or trigger a notification -- [ReceiveSms] already dropped it
 * before storage existed for notifications to trigger from, so this only pins down that the new
 * notifier call sits on the same side of that check, not before it.
 *
 * The success and duplicate-insert paths run inside `MessagingDatabase.withTransaction`, which
 * needs a real (Robolectric or instrumented) Room database to exercise -- there is no such
 * dependency in this module's plain-JVM `test` source set, so they are not covered here.
 */
class ReceiveSmsTest {

    @Test
    fun invoke_blockedSender_returnsNullAndNeverNotifies() = runTest {
        val notifier = RecordingIncomingMessageNotifier()
        val blockedNumberRepository = object : BlockedNumberRepository {
            override fun observeAll(): Flow<List<BlockedNumber>> = MutableStateFlow(emptyList())
            override suspend fun isBlocked(address: String): Boolean = true
            override suspend fun block(addresses: Collection<String>, reason: BlockReason) = Unit
            override suspend fun unblock(addresses: Collection<String>) = Unit
        }
        val receiveSms = ReceiveSms(
            database = neverUsedDatabase(),
            conversationRepository = neverCalledConversationRepository(),
            messageRepository = neverCalledMessageRepository(),
            blockedNumberRepository = blockedNumberRepository,
            incomingMessageNotifier = notifier,
        )

        val result = receiveSms(
            ReceiveSms.Params(
                address = "12345",
                body = "hello",
                sentAtMillis = 1_000L,
                receivedAtMillis = 1_000L,
                subscriptionId = -1,
            ),
        )

        assertNull(result)
        assertTrue(notifier.notifiedMessages.isEmpty())
    }
}

internal class RecordingIncomingMessageNotifier : IncomingMessageNotifier {
    val notifiedMessages = mutableListOf<Message>()
    val cancelledThreadIds = mutableListOf<Long>()

    override suspend fun notify(message: Message) {
        notifiedMessages += message
    }

    override fun cancel(threadId: Long) {
        cancelledThreadIds += threadId
    }
}

/** A [MessageRepository] whose every member fails the test loudly if the code under test ever
 * reaches it -- used where the scenario should short-circuit before touching storage at all. */
internal fun neverCalledMessageRepository(): MessageRepository = object : MessageRepository {
    override fun observeThread(threadId: Long): Flow<List<Message>> = fail()
    override suspend fun findById(id: Long): Message? = fail()
    override suspend fun findByProviderId(providerId: Long, channel: MessageChannel): Message? = fail()
    override suspend fun insertOutgoing(
        threadId: Long,
        address: String,
        body: String,
        subscriptionId: Int,
        attachmentUris: List<String>,
        folder: text.message.sms.messaging.domain.model.MessageFolder,
    ): Message = fail()
    override suspend fun insertIncoming(message: Message, notifyConversation: Boolean): Message? = fail()
    override suspend fun setDeliveryState(messageId: Long, state: text.message.sms.messaging.domain.model.DeliveryState, errorCode: Int) = fail<Unit>()
    override suspend fun setRead(threadIds: Collection<Long>, read: Boolean) = fail<Unit>()
    override suspend fun setSeen(threadIds: Collection<Long>) = fail<Unit>()
    override suspend fun countForThread(threadId: Long): Int = fail()
    override suspend fun findLatestForThread(threadId: Long): Message? = fail()
    override suspend fun delete(messageIds: Collection<Long>) = fail<Unit>()
    override suspend fun deleteOlderThan(timestampMillis: Long) = fail<Unit>()
    override fun search(query: String): Flow<List<Message>> = fail()
    override suspend fun hasAnyMessages(): Boolean = fail()
}

internal fun neverCalledConversationRepository(): ConversationRepository = object : ConversationRepository {
    override fun observeInbox(): Flow<List<Conversation>> = fail()
    override fun observeArchived(): Flow<List<Conversation>> = fail()
    override fun observeConversation(threadId: Long): Flow<Conversation?> = fail()
    override suspend fun findByThreadId(threadId: Long): Conversation? = fail()
    override suspend fun resolveThreadId(addresses: Set<String>): Long = fail()
    override suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean) = fail<Unit>()
    override suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean) = fail<Unit>()
    override suspend fun setMuted(threadIds: Collection<Long>, muted: Boolean) = fail<Unit>()
    override suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean) = fail<Unit>()
    override suspend fun saveDraft(threadId: Long, draft: String?) = fail<Unit>()
    override suspend fun setSubscriptionSlot(threadId: Long, slot: Int?) = fail<Unit>()
    override suspend fun refreshCountersBatch(updates: Map<Long, ConversationCounterUpdate>) = fail<Unit>()
    override suspend fun setLastMessage(threadId: Long, snippet: String, timestampMillis: Long) = fail<Unit>()
    override suspend fun delete(threadIds: Collection<Long>) = fail<Unit>()
    override fun search(query: String): Flow<List<Conversation>> = fail()
}

/** A [MessagingDatabase] that only exists to satisfy [ReceiveSms]'s constructor -- every member
 * fails the test if actually touched, which a blocked-sender call never does (it returns before
 * `withTransaction` is ever called). */
internal fun neverUsedDatabase(): MessagingDatabase = object : MessagingDatabase() {
    override fun conversationDao(): ConversationDao = fail()
    override fun messageDao(): MessageDao = fail()
    override fun attachmentDao(): AttachmentDao = fail()
    override fun contactDao(): ContactDao = fail()
    override fun contactGroupDao(): ContactGroupDao = fail()
    override fun blockedNumberDao(): BlockedNumberDao = fail()
    override fun syncStateDao(): SyncStateDao = fail()
    override fun scheduledMessageDao(): ScheduledMessageDao = fail()
    override fun clearAllTables(): Unit = fail()
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker = fail()
}

private fun <T> fail(): T = throw AssertionError("Should not be reached by this test's scenario")
