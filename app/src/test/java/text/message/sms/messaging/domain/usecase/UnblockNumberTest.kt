package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber

/** [UnblockNumber] starts from a number (the Blocked list's row), not a thread -- so beyond
 * dropping the number off the block list, it must find and restore that number's blocked
 * threads itself, and leave everyone else's alone. [ReblockNumber] must undo exactly that. */
class UnblockNumberTest {

    private fun number(address: String, reason: BlockReason = BlockReason.MANUAL) =
        BlockedNumber(id = 1L, address = address, reason = reason, blockedAtMillis = 0L)

    @Test
    fun unblock_removesNumberAndRestoresItsThreadToTheInbox() = runTest {
        // testConversation's single recipient is "5550001".
        val conversations = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, isBlocked = true)),
        )
        val blocked = RecordingBlockedNumberRepository()

        val outcome = UnblockNumber(conversations, blocked)(number("5550001"))

        assertEquals(listOf(listOf("5550001")), blocked.unblockCalls.map { it.toList() })
        assertEquals(setOf(1L), outcome.restoredThreadIds)
        assertFalse(conversations.findByThreadId(1L)!!.isBlocked)
        assertTrue(conversations.observeInbox().first().any { it.threadId == 1L && !it.isBlocked })
    }

    @Test
    fun unblock_matchesTheThreadDespiteFormatting() = runTest {
        val conversations = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, isBlocked = true)),
        )

        val outcome = UnblockNumber(conversations, RecordingBlockedNumberRepository())(number("555-0001"))

        assertEquals(setOf(1L), outcome.restoredThreadIds)
    }

    @Test
    fun unblock_leavesGroupThreadsWithThatNumberAlone() = runTest {
        val conversations = FakeConversationRepository(
            seed = listOf(
                testConversation(threadId = 1L, isBlocked = true),
                // A group that includes 5550001 -- flagged here only to prove it's skipped.
                testConversation(threadId = 2L, recipientCount = 2, isBlocked = true),
            ),
        )

        val outcome = UnblockNumber(conversations, RecordingBlockedNumberRepository())(number("5550001"))

        assertEquals(setOf(1L), outcome.restoredThreadIds)
        assertTrue(conversations.findByThreadId(2L)!!.isBlocked)
    }

    @Test
    fun unblock_leavesOtherNumbersThreadsAlone() = runTest {
        val conversations = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, isBlocked = true)),
        )

        val outcome = UnblockNumber(conversations, RecordingBlockedNumberRepository())(number("5559999"))

        assertTrue(outcome.restoredThreadIds.isEmpty())
        assertTrue(conversations.setBlockedCalls.isEmpty())
        assertTrue(conversations.findByThreadId(1L)!!.isBlocked)
    }

    @Test
    fun unblock_withNoThreadStillRemovesTheNumber() = runTest {
        val blocked = RecordingBlockedNumberRepository()
        val conversations = FakeConversationRepository()

        val outcome = UnblockNumber(conversations, blocked)(number("5550001"))

        assertEquals(1, blocked.unblockCalls.size)
        assertTrue(outcome.restoredThreadIds.isEmpty())
        assertTrue(conversations.setBlockedCalls.isEmpty())
    }

    @Test
    fun reblock_restoresNumberWithItsReasonAndHidesTheThreadAgain() = runTest {
        val conversations = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L, isBlocked = true)),
        )
        val blocked = RecordingBlockedNumberRepository()
        val outcome = UnblockNumber(conversations, blocked)(number("5550001", BlockReason.SPAM))

        ReblockNumber(conversations, blocked)(outcome)

        assertEquals(listOf(listOf("5550001") to BlockReason.SPAM), blocked.blockCalls.map { it.first.toList() to it.second })
        assertTrue(conversations.findByThreadId(1L)!!.isBlocked)
    }
}
