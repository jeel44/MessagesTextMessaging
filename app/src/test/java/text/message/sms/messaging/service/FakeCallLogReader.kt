package text.message.sms.messaging.service

/** A scripted call log: [rowsByQuery] is what each successive query returns, the last one
 * repeating forever. */
internal class FakeCallLogReader(
    private val readable: Boolean = true,
    private val rowsByQuery: List<CallLogEntry?> = listOf(null),
) : CallLogReader {
    var queries = 0
        private set

    override fun canRead(): Boolean = readable

    override suspend fun latestEntry(): CallLogEntry? =
        rowsByQuery[queries.coerceAtMost(rowsByQuery.lastIndex)].also { queries++ }
}
