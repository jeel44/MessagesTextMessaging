package text.message.sms.messaging.domain.model

/** A conversation matched by a full-text query, with the number of matching messages. */
data class SearchResult(
    val conversation: Conversation,
    val matchCount: Int,
    val topMatch: Message?,
)
