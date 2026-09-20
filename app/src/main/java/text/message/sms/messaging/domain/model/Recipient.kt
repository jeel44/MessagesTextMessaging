package text.message.sms.messaging.domain.model

import androidx.compose.runtime.Immutable

/** A phone number taking part in a conversation, optionally resolved to a contact. */
@Immutable
data class Recipient(
    val id: Long,
    val address: String,
    val contact: Contact?,
) {
    /** Contact name when known, otherwise the raw address. */
    val displayName: String get() = contact?.displayName ?: address
}
