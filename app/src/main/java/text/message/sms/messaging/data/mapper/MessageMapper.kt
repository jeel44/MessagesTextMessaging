package text.message.sms.messaging.data.mapper

import text.message.sms.messaging.data.local.db.entity.MessageEntity
import text.message.sms.messaging.data.local.db.entity.MessageWithAttachments
import text.message.sms.messaging.domain.model.Message

fun MessageWithAttachments.toDomain(): Message = message.toDomain(
    attachments = attachments.map { it.toDomain() },
)

fun MessageEntity.toDomain(
    attachments: List<text.message.sms.messaging.domain.model.Attachment> = emptyList(),
): Message = Message(
    id = id,
    threadId = threadId,
    providerId = providerId,
    channel = channel,
    folder = folder,
    deliveryState = deliveryState,
    address = address,
    body = body,
    subject = subject,
    sentAtMillis = sentAtMillis,
    receivedAtMillis = receivedAtMillis,
    isRead = isRead,
    isSeen = isSeen,
    subscriptionId = subscriptionId,
    errorCode = errorCode,
    attachments = attachments,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    threadId = threadId,
    providerId = providerId,
    channel = channel,
    folder = folder,
    deliveryState = deliveryState,
    address = address,
    body = body,
    subject = subject,
    sentAtMillis = sentAtMillis,
    receivedAtMillis = receivedAtMillis,
    isRead = isRead,
    isSeen = isSeen,
    subscriptionId = subscriptionId,
    errorCode = errorCode,
)
