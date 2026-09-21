package text.message.sms.messaging.domain.model

import androidx.compose.runtime.Immutable
import text.message.sms.messaging.util.firstLetterOrDigitOrNull

/** An entry resolved from the system contacts provider. */
@Immutable
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
    val numbers: List<String> = emptyList(),
    val isStarred: Boolean = false,
) {
    /** Up to two letters/digits used when no contact photo is available. */
    val initials: String
        get() = displayName
            .split(' ')
            .filter { it.isNotBlank() }
            .mapNotNull { it.firstLetterOrDigitOrNull() }
            .take(2)
            .joinToString(separator = "")
}
