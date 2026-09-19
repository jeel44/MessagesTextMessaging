package text.message.sms.messaging.data.mapper

import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.ConversationWithRecipients
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Recipient
import text.message.sms.messaging.util.PhoneNumbers

/**
 * @param contactsByComparableSuffix resolved contacts keyed by [PhoneNumbers.comparableSuffix]
 * (not [PhoneNumbers.normalize] -- see
 * [text.message.sms.messaging.data.repository.LocalConversationRepository.contactsByComparableSuffix]
 * for why an exact-normalized-string key would miss a real match), so recipient rows can show a
 * name instead of a bare number. Recipients with no match fall back to their address.
 */
fun ConversationWithRecipients.toDomain(
    contactsByComparableSuffix: Map<String, Contact> = emptyMap(),
): Conversation = Conversation(
    id = conversation.threadId,
    threadId = conversation.threadId,
    recipients = recipients.map { recipient ->
        Recipient(
            id = recipient.id,
            address = recipient.address,
            contact = contactsByComparableSuffix[PhoneNumbers.comparableSuffix(recipient.address)],
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
    subscriptionSlot = conversation.subscriptionSlot,
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
    subscriptionSlot = subscriptionSlot,
)
