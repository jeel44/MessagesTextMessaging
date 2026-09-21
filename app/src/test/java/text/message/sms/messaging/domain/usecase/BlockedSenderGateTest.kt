package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.repository.BlockedNumberRepository

/**
 * [BlockedSenderGate] is the one place [ReceiveSms]/[ReceiveMms] (and
 * [text.message.sms.messaging.data.repository.TelephonySyncRepository]'s three historical-backfill
 * call sites) decide whether an inbound message's sender is blocked -- see its own doc for why a
 * blocked sender's message is stored, not dropped. Their end-to-end behavior (touching
 * `MessagingDatabase.withTransaction`) needs a real Room database and is covered by this class's
 * `androidTest` counterpart instead; this only exercises [BlockedSenderGate] in isolation.
 */
class BlockedSenderGateTest {

    @Test
    fun isBlocked_nullAddress_isFalseWithoutConsultingTheRepository() = runTest {
        val gate = BlockedSenderGate(
            blockedNumberRepository = object : BlockedNumberRepository {
                override fun observeAll(): Flow<List<BlockedNumber>> = MutableStateFlow(emptyList())
                override suspend fun isBlocked(address: String): Boolean =
                    throw AssertionError("must not be called for a null address")
                override suspend fun block(addresses: Collection<String>, reason: BlockReason) = Unit
                override suspend fun unblock(addresses: Collection<String>) = Unit
            },
            conversationRepository = neverCalledConversationRepository(),
        )

        assertFalse(gate.isBlocked(null))
    }

    @Test
    fun isBlocked_delegatesToBlockedNumberRepository() = runTest {
        val gate = BlockedSenderGate(
            blockedNumberRepository = fixedResultBlockedNumberRepository(blocked = true),
            conversationRepository = neverCalledConversationRepository(),
        )

        assertTrue(gate.isBlocked("12345"))
    }

    @Test
    fun markThreadsBlocked_emptyCollection_neverTouchesConversationRepository() = runTest {
        val gate = BlockedSenderGate(
            blockedNumberRepository = fixedResultBlockedNumberRepository(blocked = true),
            conversationRepository = neverCalledConversationRepository(),
        )

        // Must not throw -- neverCalledConversationRepository() fails the test if setBlocked is
        // actually invoked, which an empty collection must never trigger.
        gate.markThreadsBlocked(emptyList())
    }

    @Test
    fun markThreadsBlocked_nonEmptyCollection_setsBlockedTrueForEveryThread() = runTest {
        val conversationRepository = FakeConversationRepository(
            seed = listOf(testConversation(threadId = 1L), testConversation(threadId = 2L)),
        )
        val gate = BlockedSenderGate(
            blockedNumberRepository = fixedResultBlockedNumberRepository(blocked = true),
            conversationRepository = conversationRepository,
        )

        gate.markThreadsBlocked(listOf(1L, 2L))

        assertEquals(listOf(listOf(1L, 2L) as Collection<Long> to true), conversationRepository.setBlockedCalls)
    }
}

private fun fixedResultBlockedNumberRepository(blocked: Boolean): BlockedNumberRepository =
    object : BlockedNumberRepository {
        override fun observeAll(): Flow<List<BlockedNumber>> = MutableStateFlow(emptyList())
        override suspend fun isBlocked(address: String): Boolean = blocked
        override suspend fun block(addresses: Collection<String>, reason: BlockReason) = Unit
        override suspend fun unblock(addresses: Collection<String>) = Unit
    }
