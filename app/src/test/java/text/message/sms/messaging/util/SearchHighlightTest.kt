package text.message.sms.messaging.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHighlightTest {

    @Test
    fun `matchRanges finds a single case-insensitive occurrence`() {
        assertEquals(listOf(3 until 7), SearchHighlight.matchRanges("Hi Jeel, how are you?", "Jeel"))
    }

    @Test
    fun `matchRanges finds every non-overlapping occurrence`() {
        assertEquals(
            listOf(0 until 3, 8 until 11),
            SearchHighlight.matchRanges("cat and cat", "cat"),
        )
    }

    @Test
    fun `matchRanges returns nothing for a blank query`() {
        assertTrue(SearchHighlight.matchRanges("some text", "").isEmpty())
    }

    @Test
    fun `matchRanges returns nothing when the query is not present`() {
        assertTrue(SearchHighlight.matchRanges("some text", "xyz").isEmpty())
    }
}
