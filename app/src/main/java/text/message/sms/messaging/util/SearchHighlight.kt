package text.message.sms.messaging.util

/** Case-insensitive substring matching for highlighting search results. */
object SearchHighlight {

    /**
     * Every non-overlapping occurrence of [query] within [text], as start-inclusive/end-exclusive
     * ranges, matched case-insensitively. Returns an empty list when [query] is blank so callers
     * can render plain text without a special case.
     */
    fun matchRanges(text: String, query: String): List<IntRange> {
        if (query.isBlank()) return emptyList()

        val ranges = mutableListOf<IntRange>()
        var fromIndex = 0
        while (fromIndex <= text.length) {
            val start = text.indexOf(query, startIndex = fromIndex, ignoreCase = true)
            if (start < 0) break
            val end = start + query.length
            ranges += start until end
            fromIndex = end
        }
        return ranges
    }
}
