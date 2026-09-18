package text.message.sms.messaging.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.RecipientEntity
import text.message.sms.messaging.data.local.provider.TelephonyThreadResolver

/**
 * Regression coverage for resolving a thread id without disturbing an existing conversation --
 * see [LocalConversationRepository.resolveThreadId].
 */
@RunWith(AndroidJUnit4::class)
class LocalConversationRepositoryTest {

    private lateinit var database: MessagingDatabase
    private lateinit var repository: LocalConversationRepository

    private val existingThreadId = 42L
    private val contactAddress = "+15551234567"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MessagingDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val fakeThreadResolver = object : TelephonyThreadResolver(context) {
            override fun resolve(addresses: Set<String>): Long = existingThreadId
        }

        repository = LocalConversationRepository(
            conversationDao = database.conversationDao(),
            contactDao = database.contactDao(),
            threadResolver = fakeThreadResolver,
            messageDao = database.messageDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * New Message resolves/creates a thread id the moment a contact is tapped, before any
     * message is sent (see [text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen]).
     * That must never disturb an existing conversation for the same thread -- previously it did,
     * because the placeholder row was unconditionally [androidx.room.Upsert]'d with a freshly
     * constructed [ConversationEntity]'s blank defaults, wiping the real conversation's
     * snippet/timestamp/unread-count/pinned/draft state and dropping it out of the inbox query
     * (which requires `last_message_at > 0`).
     */
    @Test
    fun resolveThreadId_doesNotDisturbExistingConversation() = runBlocking {
        database.conversationDao().upsert(
            ConversationEntity(
                threadId = existingThreadId,
                snippet = "See you tomorrow!",
                lastMessageAtMillis = 1_700_000_000_000L,
                unreadCount = 3,
                isPinned = true,
                draft = "unsent draft",
            ),
        )
        database.conversationDao().insertRecipients(
            listOf(RecipientEntity(threadId = existingThreadId, address = contactAddress)),
        )

        // Simulates: New Message -> tap contact -> back out without sending.
        val resolved = repository.resolveThreadId(setOf(contactAddress))
        assertEquals(existingThreadId, resolved)

        val conversation = database.conversationDao().findByThreadId(existingThreadId)?.conversation
        assertEquals("See you tomorrow!", conversation?.snippet)
        assertEquals(1_700_000_000_000L, conversation?.lastMessageAtMillis)
        assertEquals(3, conversation?.unreadCount)
        assertTrue(conversation?.isPinned == true)
        assertEquals("unsent draft", conversation?.draft)

        val inbox = database.conversationDao().observeInbox().first()
        assertTrue(inbox.any { it.conversation.threadId == existingThreadId })
    }

    @Test
    fun resolveThreadId_createsPlaceholderForBrandNewThread() = runBlocking {
        val resolved = repository.resolveThreadId(setOf(contactAddress))
        assertEquals(existingThreadId, resolved)

        val conversation = database.conversationDao().findByThreadId(existingThreadId)?.conversation
        assertEquals("", conversation?.snippet)
        assertEquals(0L, conversation?.lastMessageAtMillis)

        // A brand-new, message-less thread must not appear in the inbox yet.
        val inbox = database.conversationDao().observeInbox().first()
        assertTrue(inbox.none { it.conversation.threadId == existingThreadId })
    }
}
