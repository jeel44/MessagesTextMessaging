package text.message.sms.messaging.domain.model

/** A real, user-created contacts group ("Family", "Work") and its members. */
data class ContactGroup(
    val id: Long,
    val title: String,
    val contacts: List<Contact>,
)
