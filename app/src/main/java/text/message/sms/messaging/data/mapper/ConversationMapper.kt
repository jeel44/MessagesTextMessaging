package text.message.sms.messaging.data.mapper

import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.ConversationWithRecipients
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Recipient
import text.message.sms.messaging.util.PhoneNumbers

/**
 * @param contactsByNormalizedAddress resolved contacts keyed by normalized phone number, so
 * recipient rows can show a name instead of a bare number. Recipients with no match fall back to
 * their address.
 */
fun ConversationWithRecipients.toDomain(
    contactsByNormalizedAddress: Map<String, Contact> = emptyMap(),
): Conversation = Conversation(
    id = conversation.threadId,
    threadId = conversation.threadId,
    recipients = recipients.map { recipient ->
        Recipient(
            id = recipient.id,
            address = recipient.address,
            contact = contactsByNormalizedAddress[PhoneNumbers.normalize(recipient.address)],
        )
    },
    snippet = conversation.snippet,
    lastMessageAtMillis = conversation.lastMessageAtMillis,
    unreadCount = conversation.unreadCount,
    isArchived = conversation.isArchived,
    isPinned = conversation.isPinned,
    isBlocked = conversation.isBlocked,
    isMuted = conversation.isMuted,
    draft = conversation.draft,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    threadId = threadId,
    snippet = snippet,
    lastMessageAtMillis = lastMessageAtMillis,
    unreadCount = unreadCount,
    isArchived = isArchived,
    isPinned = isPinned,
    isBlocked = isBlocked,
    isMuted = isMuted,
    draft = draft,
)
