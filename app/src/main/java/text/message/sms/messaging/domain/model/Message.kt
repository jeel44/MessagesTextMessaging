package text.message.sms.messaging.domain.model

import androidx.compose.runtime.Immutable

/** A single SMS or MMS entry belonging to a [Conversation]. */
@Immutable
data class Message(
    val id: Long,
    val threadId: Long,
    val providerId: Long,
    val channel: MessageChannel,
    val folder: MessageFolder,
    val deliveryState: DeliveryState,
    val address: String?,
    val body: String,
    val subject: String?,
    val sentAtMillis: Long,
    val receivedAtMillis: Long,
    val isRead: Boolean,
    val isSeen: Boolean,
    val subscriptionId: Int,
    val errorCode: Int,
    val attachments: List<Attachment> = emptyList(),
) {
    val isOutgoing: Boolean
        get() = folder in OUTGOING_FOLDERS

    val isIncoming: Boolean
        get() = !isOutgoing

    private companion object {
        val OUTGOING_FOLDERS = setOf(
            MessageFolder.SENT,
            MessageFolder.OUTBOX,
            MessageFolder.DRAFT,
            MessageFolder.FAILED,
            MessageFolder.QUEUED,
        )
    }
}
