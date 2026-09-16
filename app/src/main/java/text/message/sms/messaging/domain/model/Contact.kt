package text.message.sms.messaging.domain.model

/** An entry resolved from the system contacts provider. */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
    val numbers: List<String> = emptyList(),
    val isStarred: Boolean = false,
) {
    /** Up to two letters used when no contact photo is available. */
    val initials: String
        get() = displayName
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString(separator = "")
}
