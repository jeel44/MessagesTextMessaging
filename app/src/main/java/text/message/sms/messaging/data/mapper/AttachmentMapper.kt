package text.message.sms.messaging.data.mapper

import text.message.sms.messaging.data.local.db.entity.AttachmentEntity
import text.message.sms.messaging.domain.model.Attachment

fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = id,
    messageId = messageId,
    mimeType = mimeType,
    fileName = fileName,
    contentUri = contentUri,
    byteSize = byteSize,
    text = text,
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    messageId = messageId,
    mimeType = mimeType,
    fileName = fileName,
    contentUri = contentUri,
    byteSize = byteSize,
    text = text,
)
