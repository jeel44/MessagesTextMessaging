package text.message.sms.messaging.ui.screens.conversationlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import text.message.sms.messaging.domain.model.Conversation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.data.local.datastore.SwipeActionPreferences
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.MessageEntity
import text.message.sms.messaging.data.local.db.entity.RecipientEntity
import text.message.sms.messaging.data.local.provider.ProviderChangeObserver
import text.message.sms.messaging.data.local.provider.TelephonyThreadResolver
import text.message.sms.messaging.data.repository.LocalConversationRepository
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.ContactGroup
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.SyncProgress
import text.message.sms.messaging.domain.repository.SyncRepository
import text.message.sms.messaging.domain.usecase.DeleteConversation
import text.message.sms.messaging.domain.usecase.MarkArchived
import text.message.sms.messaging.domain.usecase.MarkBlocked
import text.message.sms.messaging.domain.usecase.MarkPinned
import text.message.sms.messaging.domain.usecase.MarkRead
import text.message.sms.messaging.domain.usecase.MarkUnarchived
import text.message.sms.messaging.domain.usecase.MarkUnblocked
import text.message.sms.messaging.domain.usecase.MarkUnpinned
import text.message.sms.messaging.domain.usecase.MarkUnread
import text.message.sms.messaging.domain.usecase.SyncContacts
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard

/**
 * Regression coverage for the deferred swipe-to-delete flow: [ConversationListViewModel.requestDelete]
 * hides a thread client-side immediately, and only [ConversationListViewModel.confirmPendingDelete]
 * (undo window expired) actually calls [DeleteConversation] -- [ConversationListViewModel.cancelPendingDelete]
 * (Undo tapped in time) must just un-hide it, since nothing was ever written yet.
 */
@RunWith(AndroidJUnit4::class)
class ConversationListViewModelTest {

    private lateinit var database: MessagingDatabase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var viewModel: ConversationListViewModel
    private val viewModelStore = ViewModelStore()

    private val threadId = 7L
    private val address = "+15559876543"

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

        val syncRepository = object : SyncRepository {
            override fun observeProgress(): Flow<SyncProgress> = MutableStateFlow(SyncProgress.Idle)
            override suspend fun syncAll() = Unit
            override suspend fun syncMessage(providerUri: String) = Unit
            override suspend fun resetSyncWatermark() = Unit
            override suspend fun lastSyncAtMillis(): Long? = null
        }
        val syncMessages = SyncMessages(syncRepository)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Only MarkRead/MarkUnread need a MessageRepository, and neither is exercised by the
        // delete/undo flow this test covers -- a stub is enough to satisfy the constructor.
        val messageRepository = object : MessageRepository {
            override fun observeThread(threadId: Long): Flow<List<Message>> = MutableStateFlow(emptyList())
            override suspend fun findById(id: Long): Message? = null
            override suspend fun findByProviderId(providerId: Long, channel: MessageChannel): Message? = null
            override suspend fun insertOutgoing(
                threadId: Long,
                address: String,
                body: String,
                subscriptionId: Int,
                attachmentUris: List<String>,
                folder: MessageFolder,
            ): Message = throw UnsupportedOperationException("not used by this test")
            override suspend fun insertIncoming(message: Message, notifyConversation: Boolean): Message =
                throw UnsupportedOperationException("not used by this test")
            override suspend fun setDeliveryState(messageId: Long, state: DeliveryState, errorCode: Int) = Unit
            override suspend fun setRead(threadIds: Collection<Long>, read: Boolean) = Unit
            override suspend fun setSeen(threadIds: Collection<Long>) = Unit
            override suspend fun countForThread(threadId: Long): Int = 0
            override suspend fun findLatestForThread(threadId: Long): Message? = null
            override suspend fun delete(messageIds: Collection<Long>) = Unit
            override suspend fun deleteOlderThan(timestampMillis: Long) = Unit
            override fun search(query: String): Flow<List<Message>> = MutableStateFlow(emptyList())
            override suspend fun hasAnyMessages(): Boolean = false
        }

        // Only MarkRead/MarkUnread need a MessageRepository; SyncContacts is likewise never
        // exercised by the delete/undo flow this test covers, so a stub ContactRepository is
        // enough to satisfy the constructor.
        val contactRepository = object : ContactRepository {
            override fun observeAll(): Flow<List<Contact>> = MutableStateFlow(emptyList())
            override suspend fun findByAddress(address: String): Contact? = null
            override fun search(query: String): Flow<List<Contact>> = MutableStateFlow(emptyList())
            override fun observeGroups(): Flow<List<ContactGroup>> = MutableStateFlow(emptyList())
            override suspend fun refreshFromProvider() = Unit
        }

        // Only MarkBlocked/MarkUnblocked need a BlockedNumberRepository, and neither is exercised
        // by the delete/undo flow this test covers -- a stub is enough to satisfy the constructor.
        val blockedNumberRepository = object : BlockedNumberRepository {
            override fun observeAll(): Flow<List<BlockedNumber>> = MutableStateFlow(emptyList())
            override suspend fun isBlocked(address: String): Boolean = false
            override suspend fun block(addresses: Collection<String>, reason: BlockReason) = Unit
            override suspend fun unblock(addresses: Collection<String>) = Unit
        }

        val builtViewModel = ConversationListViewModel(
            conversationRepository = conversationRepository,
            syncRepository = syncRepository,
            defaultSmsAppGuard = DefaultSmsAppGuard(context),
            syncMessages = syncMessages,
            syncContacts = SyncContacts(contactRepository),
            context = context,
            providerChangeObserver = ProviderChangeObserver(context, applicationScope, syncMessages),
            swipeActionPreferences = SwipeActionPreferences(context),
            markArchivedUseCase = MarkArchived(conversationRepository),
            markUnarchivedUseCase = MarkUnarchived(conversationRepository),
            deleteConversationUseCase = DeleteConversation(conversationRepository, messageRepository),
            markReadUseCase = MarkRead(messageRepository),
            markUnreadUseCase = MarkUnread(messageRepository),
            markPinnedUseCase = MarkPinned(conversationRepository),
            markUnpinnedUseCase = MarkUnpinned(conversationRepository),
            markBlockedUseCase = MarkBlocked(conversationRepository, blockedNumberRepository),
            markUnblockedUseCase = MarkUnblocked(conversationRepository, blockedNumberRepository),
        )
        // Routed through a real ViewModelStore (rather than just using builtViewModel directly)
        // purely so tearDown can call the ordinary, public ViewModelStore.clear() -- ViewModel's
        // own clear() isn't public API -- to cancel viewModelScope before the next test closes a
        // different in-memory database out from under any coroutine this one left running.
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = builtViewModel as T
        }
        viewModel = ViewModelProvider(viewModelStore, factory)[ConversationListViewModel::class.java]
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        database.close()
    }

    private suspend fun seedConversationWithHistory() {
        database.conversationDao().upsert(
            ConversationEntity(
                threadId = threadId,
                snippet = "Sounds good, see you then",
                lastMessageAtMillis = 1_700_000_000_000L,
                unreadCount = 0,
                isPinned = true,
            ),
        )
        database.conversationDao().insertRecipients(
            listOf(RecipientEntity(threadId = threadId, address = address)),
        )
        // Distinct providerId per row -- messages' unique index is on (provider_id, channel), and
        // the default providerId of 0 would collide the two inserts down to just one row.
        database.messageDao().insert(
            MessageEntity(
                threadId = threadId,
                providerId = 101L,
                channel = MessageChannel.SMS,
                folder = MessageFolder.INBOX,
                address = address,
                body = "Hey, are we still on for Friday?",
                receivedAtMillis = 1_699_999_000_000L,
                isRead = true,
            ),
        )
        database.messageDao().insert(
            MessageEntity(
                threadId = threadId,
                providerId = 102L,
                channel = MessageChannel.SMS,
                folder = MessageFolder.SENT,
                address = address,
                body = "Sounds good, see you then",
                sentAtMillis = 1_700_000_000_000L,
                receivedAtMillis = 1_700_000_000_000L,
                isRead = true,
            ),
        )
    }

    /** Waits (rather than racing [kotlinx.coroutines.flow.StateFlow]'s current value against the
     * [androidx.lifecycle.ViewModel]'s own `WhileSubscribed` sharing coroutine) until [threadId]
     * appears in [ConversationListViewModel.conversations], and returns it. */
    private suspend fun awaitVisible(threadId: Long): Conversation =
        withTimeout(5_000) {
            viewModel.conversations
                .map { list -> list.find { it.threadId == threadId } }
                .filterNotNull()
                .first()
        }

    private suspend fun awaitHidden(threadId: Long) {
        withTimeout(5_000) {
            viewModel.conversations.first { list -> list.none { it.threadId == threadId } }
        }
    }

    /** [ConversationListViewModel.confirmPendingDelete] fires the real delete on the ViewModel's
     * own coroutine scope and returns immediately, so callers must poll rather than check the
     * repository right away. */
    private suspend fun awaitActuallyDeleted(threadId: Long) {
        withTimeout(5_000) {
            while (conversationRepository.findByThreadId(threadId) != null) {
                delay(20)
            }
        }
    }

    /** Swipe -> Undo tapped inside the window: nothing was ever actually deleted, so the
     * conversation and its full message history must come back exactly as they were. */
    @Test
    fun swipeDelete_thenUndo_conversationAndHistoryFullyIntact() = runBlocking {
        seedConversationWithHistory()

        val conversation = awaitVisible(threadId)

        viewModel.requestDelete(conversation)
        awaitHidden(threadId)

        viewModel.cancelPendingDelete(threadId)

        val restored = awaitVisible(threadId)
        assertEquals(conversation.snippet, restored.snippet)
        assertEquals(conversation.lastMessageAtMillis, restored.lastMessageAtMillis)
        assertEquals(conversation.isPinned, restored.isPinned)
        assertEquals(conversation.recipients.map { it.address }, restored.recipients.map { it.address })

        val history = database.messageDao().observeThread(threadId).first()
        assertEquals(2, history.size)
        assertEquals("Hey, are we still on for Friday?", history[0].message.body)
        assertEquals("Sounds good, see you then", history[1].message.body)
    }

    /** Swipe -> undo window expires untapped: the deferred delete must actually happen, cascading
     * to the thread's message rows -- fixing the undo path must not break this. */
    @Test
    fun swipeDelete_windowExpires_conversationActuallyDeleted() = runBlocking {
        seedConversationWithHistory()

        val conversation = awaitVisible(threadId)
        viewModel.requestDelete(conversation)
        awaitHidden(threadId)

        viewModel.confirmPendingDelete(threadId)
        awaitActuallyDeleted(threadId)

        assertNull(conversationRepository.findByThreadId(threadId))
        val history = database.messageDao().observeThread(threadId).first()
        assertTrue(history.isEmpty())
    }
}
