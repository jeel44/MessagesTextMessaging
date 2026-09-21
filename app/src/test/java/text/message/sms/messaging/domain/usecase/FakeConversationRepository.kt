package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Recipient
import text.message.sms.messaging.domain.repository.ConversationCounterUpdate
import text.message.sms.messaging.domain.repository.ConversationRepository

/**
 * Minimal in-memory [ConversationRepository] fake shared by this package's plain-JVM unit tests --
 * backs just enough (a threadId-keyed [Conversation] map, seeded via the constructor) for use cases
 * that read/write archive/pin/block flags without needing a real Room database. Every write this
 * package's tests care about is both applied to the in-memory map (so a use case's follow-up read
 * sees its own effect) and recorded (so a test can assert exactly what was called).
 */
internal class FakeConversationRepository(
    seed: List<Conversation> = emptyList(),
) : ConversationRepository {

    private val conversations = seed.associateBy { it.threadId }.toMutableMap()

    val setBlockedCalls = mutableListOf<Pair<Collection<Long>, Boolean>>()
    val setPinnedCalls = mutableListOf<Pair<Collection<Long>, Boolean>>()

    override fun observeInbox(): Flow<List<Conversation>> = MutableStateFlow(conversations.values.toList())

    override fun observeArchived(): Flow<List<Conversation>> = MutableStateFlow(emptyList())

    override fun getBlockedConversations(): Flow<List<Conversation>> =
        MutableStateFlow(conversations.values.filter { it.isBlocked })

    override fun observeConversation(threadId: Long): Flow<Conversation?> = MutableStateFlow(conversations[threadId])

    override suspend fun findByThreadId(threadId: Long): Conversation? = conversations[threadId]

    override suspend fun resolveThreadId(addresses: Set<String>): Long = notUsed()

    override suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean) {
        threadIds.forEach { id -> conversations[id]?.let { conversations[id] = it.copy(isArchived = archived) } }
    }

    override suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean) {
        setPinnedCalls += threadIds to pinned
        threadIds.forEach { id -> conversations[id]?.let { conversations[id] = it.copy(isPinned = pinned) } }
    }

    override suspend fun setMuted(threadIds: Collection<Long>, muted: Boolean) {
        threadIds.forEach { id -> conversations[id]?.let { conversations[id] = it.copy(isMuted = muted) } }
    }

    override suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean) {
        setBlockedCalls += threadIds to blocked
        threadIds.forEach { id -> conversations[id]?.let { conversations[id] = it.copy(isBlocked = blocked) } }
    }

    override suspend fun saveDraft(threadId: Long, draft: String?): Unit = notUsed()

    override suspend fun setSubscriptionSlot(threadId: Long, slot: Int?): Unit = notUsed()

    override suspend fun refreshCountersBatch(updates: Map<Long, ConversationCounterUpdate>): Unit = notUsed()

    override suspend fun setLastMessage(threadId: Long, snippet: String, timestampMillis: Long): Unit = notUsed()

    override suspend fun delete(threadIds: Collection<Long>): Unit = notUsed()

    override fun search(query: String): Flow<List<Conversation>> = notUsed()

    private fun notUsed(): Nothing = throw AssertionError("Not needed by this test's scenario")
}

/** A [ConversationRepository] whose every member fails the test loudly if the code under test
 * ever reaches it -- used where the scenario should short-circuit before touching the repository
 * at all. */
internal fun neverCalledConversationRepository(): ConversationRepository = FakeConversationRepository()
    .let { fake ->
        object : ConversationRepository by fake {
            override suspend fun findByThreadId(threadId: Long): Conversation? = fail()
            override suspend fun setBlocked(threadIds: Collection<Long>, blocked: Boolean) = fail<Unit>()
            override suspend fun setPinned(threadIds: Collection<Long>, pinned: Boolean) = fail<Unit>()
            override suspend fun setArchived(threadIds: Collection<Long>, archived: Boolean) = fail<Unit>()
        }
    }

private fun <T> fail(): T = throw AssertionError("Should not be reached by this test's scenario")

/** Builds a one-participant (non-group) [Conversation] for tests that don't care about most
 * fields -- [threadId] and [isBlocked]/[isGroup] are the ones that vary per scenario. */
internal fun testConversation(
    threadId: Long,
    recipientCount: Int = 1,
    isBlocked: Boolean = false,
    isPinned: Boolean = false,
): Conversation = Conversation(
    id = threadId,
    threadId = threadId,
    recipients = (1..recipientCount).map { index ->
        Recipient(id = index.toLong(), address = "555000$index", contact = null)
    },
    snippet = "",
    lastMessageAtMillis = 0L,
    unreadCount = 0,
    isArchived = false,
    isPinned = isPinned,
    isBlocked = isBlocked,
    isMuted = false,
    draft = null,
)
