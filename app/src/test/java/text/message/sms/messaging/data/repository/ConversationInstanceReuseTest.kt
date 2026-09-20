package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import text.message.sms.messaging.domain.model.Conversation

/**
 * [reuseUnchangedInstances] exists so [LocalConversationRepository.withContacts]'s per-emission
 * remap of every row (Room invalidates its query on any write to `conversations` or `contacts`,
 * table-wide, not per-row) doesn't hand Compose a brand-new [Conversation] instance for a row
 * whose content didn't actually change -- see that function's own doc comment. Structural
 * equality (via [Conversation]'s `@Immutable` data class `equals`) is what lets this tell "same
 * content, different instance" apart from "actually changed".
 */
class ConversationInstanceReuseTest {

    private fun conversation(threadId: Long, snippet: String) = Conversation(
        id = threadId,
        threadId = threadId,
        recipients = emptyList(),
        snippet = snippet,
        lastMessageAtMillis = 1_700_000_000_000L,
        unreadCount = 0,
        isArchived = false,
        isPinned = false,
        isBlocked = false,
        isMuted = false,
        draft = null,
    )

    @Test
    fun identicalEmission_reusesThePreviousInstance() = runTest {
        val first = listOf(conversation(threadId = 1L, snippet = "Hi"))
        // A distinct object, same content -- exactly what a fresh Room remap produces for an
        // unchanged row.
        val second = listOf(conversation(threadId = 1L, snippet = "Hi"))
        assertNotSame(first[0], second[0])

        val emissions = flowOf(first, second).reuseUnchangedInstances().toList()

        assertEquals(2, emissions.size)
        assertSame(first[0], emissions[0][0])
        assertSame(first[0], emissions[1][0])
    }

    @Test
    fun identicalEmission_isFilteredOutByDistinctUntilChanged() = runTest {
        val first = listOf(conversation(threadId = 1L, snippet = "Hi"))
        val second = listOf(conversation(threadId = 1L, snippet = "Hi"))

        val emissions = flowOf(first, second)
            .reuseUnchangedInstances()
            .distinctUntilChanged()
            .toList()

        assertEquals(1, emissions.size)
        assertSame(first[0], emissions[0][0])
    }

    @Test
    fun changedRow_onlyThatRowBecomesANewInstance() = runTest {
        val unchanged = conversation(threadId = 1L, snippet = "Hi")
        val changedBefore = conversation(threadId = 2L, snippet = "Old")
        val changedAfter = conversation(threadId = 2L, snippet = "New")

        val first = listOf(unchanged, changedBefore)
        // Fresh instances for both rows, as a real remap would produce -- only row 2's content
        // actually differs.
        val second = listOf(conversation(threadId = 1L, snippet = "Hi"), changedAfter)

        val emissions = flowOf(first, second).reuseUnchangedInstances().toList()

        assertEquals(2, emissions.size)
        val secondEmission = emissions[1]
        assertSame(unchanged, secondEmission[0])
        assertSame(changedAfter, secondEmission[1])
        assertNotSame(changedBefore, secondEmission[1])
    }

    @Test
    fun removedThread_isDroppedFromTheCache() = runTest {
        val kept = conversation(threadId = 1L, snippet = "Hi")
        val removed = conversation(threadId = 2L, snippet = "Bye")
        val first = listOf(kept, removed)
        // Thread 2 is gone; thread 1 reappears with a new but equal instance and thread 3 is
        // genuinely new -- if the cache still held thread 2's entry, this wouldn't be affected
        // either way, but a thread 2 reappearing later (not exercised here) must not be handed a
        // stale instance from before it was removed.
        val second = listOf(conversation(threadId = 1L, snippet = "Hi"))

        val emissions = flowOf(first, second).reuseUnchangedInstances().toList()

        assertEquals(2, emissions.size)
        assertEquals(1, emissions[1].size)
        assertSame(kept, emissions[1][0])
    }
}
