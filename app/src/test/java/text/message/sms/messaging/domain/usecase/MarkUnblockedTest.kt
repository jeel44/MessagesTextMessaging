package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Defensive symmetry with [MarkBlockedTest] -- see [MarkUnblocked]'s doc for why a group thread
 * is guarded here too, even though the UI never offers Unblock for one in practice. */
class MarkUnblockedTest {

    @Test
    fun invoke_nonGroupThread_unblocksItsAddressAndRestoresIt() = runTest {
        val conversationRepository = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, recipientCount = 1, isBlocked = true)),
        )
        val blockedNumberRepository = RecordingBlockedNumberRepository()
        val markUnblocked = MarkUnblocked(conversationRepository, blockedNumberRepository)

        val outcome = markUnblocked(listOf(1L))

        assertEquals(setOf(1L), outcome.unblockedThreadIds)
        assertTrue(outcome.skippedGroupThreadIds.isEmpty())
        assertEquals(listOf(setOf("5550001")), blockedNumberRepository.unblockCalls)
        assertEquals(listOf(listOf(1L) as Collection<Long> to false), conversationRepository.setBlockedCalls)
    }

    @Test
    fun invoke_groupThread_isSkippedAndNeverUnblocked() = runTest {
        val conversationRepository = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, recipientCount = 3, isBlocked = true)),
        )
        val blockedNumberRepository = RecordingBlockedNumberRepository()
        val markUnblocked = MarkUnblocked(conversationRepository, blockedNumberRepository)

        val outcome = markUnblocked(listOf(1L))

        assertTrue(outcome.unblockedThreadIds.isEmpty())
        assertEquals(setOf(1L), outcome.skippedGroupThreadIds)
        assertTrue(blockedNumberRepository.unblockCalls.isEmpty())
        assertTrue(conversationRepository.setBlockedCalls.isEmpty())
    }
}
