package text.message.sms.messaging.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.data.local.provider.MmsAttachmentStorage
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.data.local.provider.TelephonyThreadResolver
import text.message.sms.messaging.data.repository.LocalBlockedNumberRepository
import text.message.sms.messaging.data.repository.LocalConversationRepository
import text.message.sms.messaging.data.repository.LocalMessageRepository
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.IncomingMessageSource
import text.message.sms.messaging.domain.repository.MessageRepository

/**
 * Non-blocking-path counterpart to [IncomingMessageBlockingTest]: regression coverage for the
 * ordinary "sender isn't blocked" shape of [ReceiveMms] (storage, conversation creation, the
 * notifier firing exactly once, dedupe) plus the transition where a sender gets blocked after a
 * conversation already has non-blocked history, and the pinned-conversation ordering guarantee.
 *
 * Only [ReceiveMms] is exercised directly, never [ReceiveSms] -- see [IncomingMessageBlockingTest]'s
 * own doc: a live SMS always carries `providerId == 0` and [LocalMessageRepository.insertIncoming]
 * falls through to a real [SmsProviderGateway.insert] write against the system Telephony provider
 * for that case, which would land real rows in whatever app currently holds the default-SMS-app
 * role on the test device -- not something this suite can safely trigger. [ReceiveMms] shares the
 * exact same [BlockedSenderGate]/[MessageRepository.insertIncoming]/notifier wiring -- the only
 * difference is where its message's provider id comes from -- so it stands in for both.
 */
@RunWith(AndroidJUnit4::class)
class IncomingMessageStorageTest {

    private lateinit var database: MessagingDatabase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var blockedNumberRepository: BlockedNumberRepository
    private lateinit var blockedSenderGate: BlockedSenderGate
    private lateinit var notifier: RecordingNotifier

    private val threadId = 42L
    private val address = "12345"

    private val otherThreadId = 43L
    private val otherAddress = "67890"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MessagingDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val fakeThreadResolver = object : TelephonyThreadResolver(context) {
            override fun resolve(addresses: Set<String>): Long =
                if (addresses.contains(otherAddress)) otherThreadId else threadId
        }
        conversationRepository = LocalConversationRepository(
            database = database,
            conversationDao = database.conversationDao(),
            contactDao = database.contactDao(),
            threadResolver = fakeThreadResolver,
            messageDao = database.messageDao(),
        )
        messageRepository = LocalMessageRepository(
            database = database,
            messageDao = database.messageDao(),
            conversationDao = database.conversationDao(),
            attachmentDao = database.attachmentDao(),
            smsProviderGateway = SmsProviderGateway(context.contentResolver),
            mmsProviderGateway = MmsProviderGateway(context.contentResolver, MmsAttachmentStorage(context)),
            attachmentStorage = MmsAttachmentStorage(context),
            contentResolver = context.contentResolver,
        )
        blockedNumberRepository = LocalBlockedNumberRepository(database.blockedNumberDao())
        blockedSenderGate = BlockedSenderGate(blockedNumberRepository, conversationRepository)
        notifier = RecordingNotifier()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun receiveMms_nonBlockedSender_storesMessageCreatesConversationAndNotifiesOnce() = runBlocking {
        conversationRepository.resolveThreadId(setOf(address))
        val source = QueuedIncomingMessageSource(mmsMessage(providerId = 501L, threadId = threadId, address = address))
        val receiveMms = receiveMmsWith(source)

        val inserted = receiveMms("content://mms/501")

        assertNotNull(inserted)
        assertEquals(listOf(inserted), notifier.notifiedMessages)

        val conversation = database.conversationDao().findByThreadId(threadId)?.conversation
        assertFalse("a non-blocked sender's conversation must not be marked blocked", conversation?.isBlocked ?: true)

        val inbox = database.conversationDao().observeInbox().first()
        assertTrue("the conversation must appear in the main list", inbox.any { it.conversation.threadId == threadId })
    }

    @Test
    fun receiveMms_duplicateMessage_secondInsertIgnoredAndNotifierCalledOnce() = runBlocking {
        conversationRepository.resolveThreadId(setOf(address))
        val providerId = 777L
        val firstSource = QueuedIncomingMessageSource(mmsMessage(providerId = providerId, threadId = threadId, address = address))
        val firstReceiveMms = receiveMmsWith(firstSource)
        val firstResult = firstReceiveMms("content://mms/777")

        // Same provider id and channel -- the unique index LocalMessageRepository.insertIncoming
        // dedupes on -- standing in for a re-delivered WAP push notifying about the same row twice.
        val secondSource = QueuedIncomingMessageSource(mmsMessage(providerId = providerId, threadId = threadId, address = address))
        val secondReceiveMms = receiveMmsWith(secondSource)
        val secondResult = secondReceiveMms("content://mms/777")

        assertNotNull(firstResult)
        assertNull("a duplicate provider row must be ignored, not stored again", secondResult)

        val stored = database.messageDao().observeThread(threadId).first()
        assertEquals("only one row must exist for the duplicated provider id", 1, stored.size)
        assertEquals("the notifier must only fire for the original insert", 1, notifier.notifiedMessages.size)
    }

    @Test
    fun receiveMms_senderBlockedAfterConversationAlreadyExists_flipsBlockedThenUnblockRestoresBoth() = runBlocking {
        conversationRepository.resolveThreadId(setOf(address))
        val firstSource = QueuedIncomingMessageSource(mmsMessage(providerId = 901L, threadId = threadId, address = address))
        val firstReceiveMms = receiveMmsWith(firstSource)
        val firstResult = firstReceiveMms("content://mms/901")

        assertNotNull("the pre-block message must be stored normally", firstResult)
        assertEquals(1, notifier.notifiedMessages.size)
        val beforeBlock = database.conversationDao().findByThreadId(threadId)?.conversation
        assertFalse(beforeBlock?.isBlocked ?: true)

        blockedNumberRepository.block(setOf(address), BlockReason.MANUAL)
        val secondSource = QueuedIncomingMessageSource(mmsMessage(providerId = 902L, threadId = threadId, address = address))
        val secondReceiveMms = receiveMmsWith(secondSource)
        val secondResult = secondReceiveMms("content://mms/902")

        assertNotNull("a newly-blocked sender's message must still be stored", secondResult)
        assertEquals("the notifier must not fire for the now-blocked message", 1, notifier.notifiedMessages.size)
        val afterBlock = database.conversationDao().findByThreadId(threadId)?.conversation
        assertTrue("the conversation must flip to blocked", afterBlock?.isBlocked == true)
        val inboxWhileBlocked = database.conversationDao().observeInbox().first()
        assertTrue(
            "a blocked conversation must not appear in the main list",
            inboxWhileBlocked.none { it.conversation.threadId == threadId },
        )

        val outcome = MarkUnblocked(conversationRepository, blockedNumberRepository)(listOf(threadId))

        assertEquals(setOf(threadId), outcome.unblockedThreadIds)
        val inboxAfterUnblock = database.conversationDao().observeInbox().first()
        assertTrue(
            "unblocking must restore the conversation to the main list",
            inboxAfterUnblock.any { it.conversation.threadId == threadId },
        )
        val messages = messageRepository.observeThread(threadId).first()
        assertEquals("both the pre- and post-block messages must still be there", 2, messages.size)
    }

    @Test
    fun receiveMms_pinnedConversation_nonBlockedSenderMessage_keepsPinStateAndOrdering() = runBlocking {
        conversationRepository.resolveThreadId(setOf(address))
        conversationRepository.resolveThreadId(setOf(otherAddress))

        // Give both threads inbox-eligible history (observeInbox requires last_message_at > 0)
        // before pinning, mirroring a conversation that already existed when it got pinned.
        receiveMmsWith(QueuedIncomingMessageSource(mmsMessage(providerId = 1001L, threadId = threadId, address = address)))(
            "content://mms/1001",
        )
        receiveMmsWith(QueuedIncomingMessageSource(mmsMessage(providerId = 1002L, threadId = otherThreadId, address = otherAddress)))(
            "content://mms/1002",
        )

        conversationRepository.setPinned(listOf(threadId), pinned = true)
        val pinnedAtBefore = database.conversationDao().findByThreadId(threadId)?.conversation?.pinnedAtMillis

        // Bumps the other (unpinned) thread strictly ahead in last_message_at, so the ordering
        // assertion below only passes if is_pinned genuinely outranks recency, not by accident.
        receiveMmsWith(
            QueuedIncomingMessageSource(mmsMessage(providerId = 1003L, threadId = otherThreadId, address = otherAddress, receivedAtMillis = 9_999_999L)),
        )("content://mms/1003")

        val newMessageSource = QueuedIncomingMessageSource(
            mmsMessage(providerId = 1004L, threadId = threadId, address = address, receivedAtMillis = 5_000L),
        )
        receiveMmsWith(newMessageSource)("content://mms/1004")

        val conversation = database.conversationDao().findByThreadId(threadId)?.conversation
        assertTrue("the conversation must remain pinned", conversation?.isPinned == true)
        assertEquals("pinned_at must be untouched by an ordinary incoming message", pinnedAtBefore, conversation?.pinnedAtMillis)

        val inbox = database.conversationDao().observeInbox().first()
        assertEquals(
            "the pinned conversation must still sort first despite the other thread being more recent",
            threadId,
            inbox.first().conversation.threadId,
        )
    }

    private fun receiveMmsWith(source: IncomingMessageSource): ReceiveMms = ReceiveMms(
        database = database,
        incomingMessageSource = source,
        messageRepository = messageRepository,
        blockedSenderGate = blockedSenderGate,
        incomingMessageNotifier = notifier,
    )

    private fun mmsMessage(
        providerId: Long,
        threadId: Long,
        address: String,
        body: String = "hello",
        receivedAtMillis: Long = 1_000L,
    ) = Message(
        id = 0L,
        threadId = threadId,
        providerId = providerId,
        channel = MessageChannel.MMS,
        folder = MessageFolder.INBOX,
        deliveryState = DeliveryState.NONE,
        address = address,
        body = body,
        subject = null,
        sentAtMillis = receivedAtMillis,
        receivedAtMillis = receivedAtMillis,
        isRead = false,
        isSeen = false,
        subscriptionId = -1,
        errorCode = 0,
    )

    private class QueuedIncomingMessageSource(private val message: Message? = null) : IncomingMessageSource {
        override suspend fun readMessage(providerUri: String): Message? = message
    }
}
