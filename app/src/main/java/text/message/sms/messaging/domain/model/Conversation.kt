package text.message.sms.messaging.domain.model

/** A message thread, keyed by the system provider's thread id. */
data class Conversation(
    val id: Long,
    val threadId: Long,
    val recipients: List<Recipient>,
    val snippet: String,
    val lastMessageAtMillis: Long,
    val unreadCount: Int,
    val isArchived: Boolean,
    val isPinned: Boolean,
    val isBlocked: Boolean,
    val isMuted: Boolean,
    val draft: String?,
    /** 0-based SIM slot remembered for this thread when the "Ask every time" send preference is
     * active -- see `text.message.sms.messaging.domain.usecase.ResolveSendSubscription`. Null
     * until the user picks a SIM and chooses to remember it, and unused entirely outside "Ask"
     * mode or on a single-SIM device. */
    val subscriptionSlot: Int? = null,
) {
    val isGroup: Boolean get() = recipients.size > 1

    val hasUnread: Boolean get() = unreadCount > 0

    /** Comma-separated recipient names, suitable for a list row title. */
    val title: String
        get() = recipients.joinToString(separator = ", ") { it.displayName }
}
