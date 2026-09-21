package text.message.sms.messaging.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.domain.repository.IncomingMessageSource
import text.message.sms.messaging.domain.repository.MessageRepository

/**
 * End-to-end coverage for [ReceiveMms] against a real (in-memory) Room database -- the scenario
 * [BlockedSenderGateTest] can't reach at the plain-JVM level, since every path through
 * [ReceiveMms]/[ReceiveSms] now touches `MessagingDatabase.withTransaction`. Exercises [ReceiveMms]
 * rather than [ReceiveSms] specifically because a live SMS always has `providerId == 0` and falls
 * through to [SmsProviderGateway.insert] -- a real Telephony-provider write this test can't safely
 * make without the test device holding the default-SMS-app role. [ReceiveMms]'s message always
 * already carries a real provider id (the platform already wrote it before the WAP push fires), so
 * [text.message.sms.messaging.data.repository.LocalMessageRepository.insertIncoming] never reaches
 * that branch -- both use cases share the exact same [BlockedSenderGate]-driven shape, already unit
 * tested in isolation, so this one instrumented path stands in for both.
 */
@RunWith(AndroidJUnit4::class)
class IncomingMessageBlockingTest {

    private lateinit var database: MessagingDatabase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var blockedNumberRepository: BlockedNumberRepository
    private lateinit var blockedSenderGate: BlockedSenderGate
    private lateinit var notifier: RecordingNotifier

    private val threadId = 42L
    private val blockedAddress = "12345"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MessagingDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val fakeThreadResolver = object : TelephonyThreadResolver(context) {
            override fun resolve(addresses: Set<String>): Long = threadId
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
    fun receiveMms_blockedSender_storesMessageMarksConversationBlockedAndNeverNotifies() = runBlocking {
        blockedNumberRepository.block(setOf(blockedAddress), BlockReason.MANUAL)
        // Mirrors MmsDownloadResultReceiver's own call, which always resolves (creating if
        // absent) the thread before ever invoking ReceiveMms -- see its doc comment. ReceiveMms
        // itself never does this; it trusts the row already exists.
        conversationRepository.resolveThreadId(setOf(blockedAddress))
        val receiveMms = ReceiveMms(
            database = database,
            incomingMessageSource = fixedIncomingMessageSource(),
            messageRepository = messageRepository,
            blockedSenderGate = blockedSenderGate,
            incomingMessageNotifier = notifier,
        )

        val inserted = receiveMms("content://mms/999")

        assertNotNull("a blocked sender's message must still be stored", inserted)
        assertTrue(notifier.notifiedMessages.isEmpty())

        val conversation = database.conversationDao().findByThreadId(threadId)?.conversation
        assertTrue("the resolved conversation must be marked blocked", conversation?.isBlocked == true)

        val inbox = database.conversationDao().observeInbox().first()
        assertTrue("a blocked conversation must not appear in the main list", inbox.none { it.conversation.threadId == threadId })
    }

    @Test
    fun receiveMms_blockedSender_thenUnblocked_restoresConversationAndItsStoredMessage() = runBlocking {
        blockedNumberRepository.block(setOf(blockedAddress), BlockReason.MANUAL)
        conversationRepository.resolveThreadId(setOf(blockedAddress))
        val receiveMms = ReceiveMms(
            database = database,
            incomingMessageSource = fixedIncomingMessageSource(),
            messageRepository = messageRepository,
            blockedSenderGate = blockedSenderGate,
            incomingMessageNotifier = notifier,
        )
        receiveMms("content://mms/999")

        val markUnblocked = MarkUnblocked(conversationRepository, blockedNumberRepository)
        val outcome = markUnblocked(listOf(threadId))

        assertEquals(setOf(threadId), outcome.unblockedThreadIds)
        val inbox = database.conversationDao().observeInbox().first()
        assertTrue("unblocking must restore the conversation to the main list", inbox.any { it.conversation.threadId == threadId })
        val messages = messageRepository.observeThread(threadId).first()
        assertEquals(1, messages.size)
    }

    private fun fixedIncomingMessageSource(): IncomingMessageSource = object : IncomingMessageSource {
        override suspend fun readMessage(providerUri: String): Message = Message(
            id = 0L,
            threadId = threadId,
            providerId = 999L,
            channel = MessageChannel.MMS,
            folder = MessageFolder.INBOX,
            deliveryState = DeliveryState.NONE,
            address = blockedAddress,
            body = "hello",
            subject = null,
            sentAtMillis = 1_000L,
            receivedAtMillis = 1_000L,
            isRead = false,
            isSeen = false,
            subscriptionId = -1,
            errorCode = 0,
        )
    }
}

internal class RecordingNotifier : IncomingMessageNotifier {
    val notifiedMessages = mutableListOf<Message>()
    override suspend fun notify(message: Message) {
        notifiedMessages += message
    }
    override fun cancel(threadId: Long) = Unit
}
