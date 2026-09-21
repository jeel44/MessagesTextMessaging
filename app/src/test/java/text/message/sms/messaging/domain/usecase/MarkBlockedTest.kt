package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.repository.BlockedNumberRepository

/**
 * A group thread must never be blocked -- [BlockedNumberRepository] blocks by a single normalized
 * address, and every participant's address in a group MMS would otherwise get blocked too,
 * silently taking out their unrelated 1:1 threads along with it. See [MarkBlocked]'s doc.
 */
class MarkBlockedTest {

    @Test
    fun invoke_nonGroupThread_blocksItsAddressAndHidesIt() = runTest {
        val conversationRepository = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, recipientCount = 1)),
        )
        val blockedNumberRepository = RecordingBlockedNumberRepository()
        val markBlocked = MarkBlocked(conversationRepository, blockedNumberRepository)

        val outcome = markBlocked(listOf(1L))

        assertEquals(setOf(1L), outcome.blockedThreadIds)
        assertTrue(outcome.skippedGroupThreadIds.isEmpty())
        assertEquals(listOf(setOf("5550001") to BlockReason.MANUAL), blockedNumberRepository.blockCalls)
        assertEquals(listOf(listOf(1L) as Collection<Long> to true), conversationRepository.setBlockedCalls)
    }

    @Test
    fun invoke_groupThread_isSkippedAndNeverBlocked() = runTest {
        val conversationRepository = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, recipientCount = 3)),
        )
        val blockedNumberRepository = RecordingBlockedNumberRepository()
        val markBlocked = MarkBlocked(conversationRepository, blockedNumberRepository)

        val outcome = markBlocked(listOf(1L))

        assertTrue(outcome.blockedThreadIds.isEmpty())
        assertEquals(setOf(1L), outcome.skippedGroupThreadIds)
        assertTrue(blockedNumberRepository.blockCalls.isEmpty())
        assertTrue(conversationRepository.setBlockedCalls.isEmpty())
    }

    @Test
    fun invoke_mixedSelection_blocksOnlyTheNonGroupThreadsAndReportsTheSkippedOne() = runTest {
        val conversationRepository = FakeConversationRepository(
            seed = listOf(
                testConversation(threadId = 1L, recipientCount = 1),
                testConversation(threadId = 2L, recipientCount = 3),
            ),
        )
        val blockedNumberRepository = RecordingBlockedNumberRepository()
        val markBlocked = MarkBlocked(conversationRepository, blockedNumberRepository)

        val outcome = markBlocked(listOf(1L, 2L))

        assertEquals(setOf(1L), outcome.blockedThreadIds)
        assertEquals(setOf(2L), outcome.skippedGroupThreadIds)
        assertEquals(listOf(listOf(1L) as Collection<Long> to true), conversationRepository.setBlockedCalls)
    }
}

internal class RecordingBlockedNumberRepository : BlockedNumberRepository {
    val blockCalls = mutableListOf<Pair<Collection<String>, BlockReason>>()
    val unblockCalls = mutableListOf<Collection<String>>()

    override fun observeAll(): Flow<List<BlockedNumber>> = MutableStateFlow(emptyList())
    override suspend fun isBlocked(address: String): Boolean = false

    override suspend fun block(addresses: Collection<String>, reason: BlockReason) {
        blockCalls += addresses to reason
    }

    override suspend fun unblock(addresses: Collection<String>) {
        unblockCalls += addresses
    }
}
