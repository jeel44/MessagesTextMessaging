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
import text.message.sms.messaging.domain.repository.ConversationCounterUpdate

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
            database = database,
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

    /** [ConversationDao.observeInbox]'s `ORDER BY ... last_message_at DESC` clause, exercised
     * with rows inserted in a deliberately scrambled order so the assertion can't pass by
     * accident of insertion order. */
    @Test
    fun observeInbox_ordersByMostRecentMessageFirst() = runBlocking {
        database.conversationDao().upsertAll(
            listOf(
                ConversationEntity(threadId = 1L, lastMessageAtMillis = 5_000L),
                ConversationEntity(threadId = 2L, lastMessageAtMillis = 9_000L),
                ConversationEntity(threadId = 3L, lastMessageAtMillis = 1_000L),
                ConversationEntity(threadId = 4L, lastMessageAtMillis = 7_000L),
            ),
        )

        val inbox = database.conversationDao().observeInbox().first()

        assertEquals(listOf(2L, 4L, 1L, 3L), inbox.map { it.conversation.threadId })
    }

    /** [ConversationDao.observeInbox]'s `ORDER BY is_pinned DESC, pinned_at DESC, last_message_at
     * DESC` -- pinned threads sort by when they were pinned (newest pin first), never by their own
     * `last_message_at`, and unpinned threads still fall back to `last_message_at DESC` among
     * themselves. Rows inserted in a deliberately scrambled order so the assertion can't pass by
     * accident. */
    @Test
    fun observeInbox_pinnedThreadsSortByPinTimeAheadOfUnpinnedByMessageTime() = runBlocking {
        database.conversationDao().upsertAll(
            listOf(
                ConversationEntity(threadId = 1L, lastMessageAtMillis = 9_000L, isPinned = false),
                ConversationEntity(threadId = 2L, lastMessageAtMillis = 1_000L, isPinned = true, pinnedAtMillis = 5_000L),
                ConversationEntity(threadId = 3L, lastMessageAtMillis = 2_000L, isPinned = true, pinnedAtMillis = 8_000L),
                ConversationEntity(threadId = 4L, lastMessageAtMillis = 6_000L, isPinned = false),
            ),
        )

        val inbox = database.conversationDao().observeInbox().first()

        // 3 pinned most recently, then 2 (also pinned, but earlier), then the two unpinned threads
        // by their own last_message_at -- 1 (9_000) before 4 (6_000).
        assertEquals(listOf(3L, 2L, 1L, 4L), inbox.map { it.conversation.threadId })
    }

    /** [LocalConversationRepository.setPinned] sets `pinned_at` to "now" on pin and resets it to 0
     * on unpin -- the DAO write itself ([ConversationDao.setPinned]) is a plain, deterministic
     * column set, so the "now" has to come from the repository layer this test exercises. */
    @Test
    fun setPinned_setsPinnedAtOnPinAndClearsItOnUnpin() = runBlocking {
        database.conversationDao().upsert(ConversationEntity(threadId = existingThreadId, lastMessageAtMillis = 1L))

        repository.setPinned(listOf(existingThreadId), pinned = true)
        val pinned = database.conversationDao().findByThreadId(existingThreadId)?.conversation
        assertTrue(pinned?.isPinned == true)
        assertTrue((pinned?.pinnedAtMillis ?: 0L) > 0L)

        repository.setPinned(listOf(existingThreadId), pinned = false)
        val unpinned = database.conversationDao().findByThreadId(existingThreadId)?.conversation
        assertTrue(unpinned?.isPinned == false)
        assertEquals(0L, unpinned?.pinnedAtMillis)
    }

    /** [TelephonySyncRepository.flushConversationUpdates] (via [refreshCountersBatch]) must never
     * reset a thread's pin/block state back to its defaults -- it only ever updates
     * snippet/last-message-time/unread-count, `.copy()`-ing off the row already in the database
     * rather than constructing a fresh [ConversationEntity]. Regression coverage for the same bug
     * class [resolveThreadId_doesNotDisturbExistingConversation] guards for `resolveThreadId`. */
    @Test
    fun refreshCountersBatch_preservesPinnedAndBlockedState() = runBlocking {
        database.conversationDao().upsert(
            ConversationEntity(
                threadId = existingThreadId,
                lastMessageAtMillis = 1_000L,
                isPinned = true,
                pinnedAtMillis = 5_000L,
                isBlocked = true,
            ),
        )

        repository.refreshCountersBatch(
            mapOf(existingThreadId to ConversationCounterUpdate(snippet = "new message", lastMessageAtMillis = 2_000L)),
        )

        val conversation = database.conversationDao().findByThreadId(existingThreadId)?.conversation
        assertTrue(conversation?.isPinned == true)
        assertEquals(5_000L, conversation?.pinnedAtMillis)
        assertTrue(conversation?.isBlocked == true)
        assertEquals("new message", conversation?.snippet)
    }

    /** [ConversationDao.getBlockedConversations] backs the eventual Blocked list screen (Part 3) --
     * excluded from [ConversationDao.observeInbox] but still queryable on its own. */
    @Test
    fun getBlockedConversations_returnsOnlyBlockedThreads() = runBlocking {
        database.conversationDao().upsertAll(
            listOf(
                ConversationEntity(threadId = 1L, lastMessageAtMillis = 1_000L, isBlocked = true),
                ConversationEntity(threadId = 2L, lastMessageAtMillis = 2_000L, isBlocked = false),
            ),
        )

        val blocked = database.conversationDao().getBlockedConversations().first()

        assertEquals(listOf(1L), blocked.map { it.conversation.threadId })
        val inbox = database.conversationDao().observeInbox().first()
        assertTrue(inbox.none { it.conversation.threadId == 1L })
    }

    /**
     * Regression test for the bug [LocalConversationRepository.refreshCountersBatch] exists to
     * fix: [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll] used to
     * upsert each thread's counters with its own separate write, so a catch-up sync touching
     * several threads produced one live Flow emission per thread instead of one for the whole
     * pass -- and, if a stalled-watermark re-inclusion re-processed an older message for a thread
     * after its true latest message had already landed, an unconditional overwrite could even
     * clobber a newer timestamp with an older one, moving that thread backward in the sort.
     * [refreshCountersBatch] applies every thread's update in a single transaction and never lets
     * an older timestamp regress an existing one, so the inbox settles directly into the correct
     * order with no intermediate scrambled state.
     */
    @Test
    fun refreshCountersBatch_settlesInboxIntoCorrectOrderInOnePass() = runBlocking {
        // Pre-existing state, as if an earlier sync pass had already run.
        database.conversationDao().upsertAll(
            listOf(
                ConversationEntity(threadId = 10L, snippet = "old", lastMessageAtMillis = 2_000L),
                ConversationEntity(threadId = 20L, snippet = "old", lastMessageAtMillis = 8_000L),
                ConversationEntity(threadId = 30L, snippet = "old", lastMessageAtMillis = 4_000L),
            ),
        )

        // One simulated sync batch: thread 10 gets a genuinely newer message, thread 20 only has
        // a stale re-included message older than what it already has (must be ignored), and
        // thread 30 gets a new message that overtakes thread 20 at the top.
        repository.refreshCountersBatch(
            mapOf(
                10L to ConversationCounterUpdate(snippet = "newer for 10", lastMessageAtMillis = 6_000L),
                20L to ConversationCounterUpdate(snippet = "stale replay", lastMessageAtMillis = 1_000L),
                30L to ConversationCounterUpdate(snippet = "newest overall", lastMessageAtMillis = 9_000L),
            ),
        )

        val inbox = database.conversationDao().observeInbox().first()

        assertEquals(listOf(30L, 20L, 10L), inbox.map { it.conversation.threadId })
        // Thread 20's snippet/timestamp must be untouched by the older, re-included message.
        val threadTwenty = inbox.first { it.conversation.threadId == 20L }.conversation
        assertEquals("old", threadTwenty.snippet)
        assertEquals(8_000L, threadTwenty.lastMessageAtMillis)
    }
}
