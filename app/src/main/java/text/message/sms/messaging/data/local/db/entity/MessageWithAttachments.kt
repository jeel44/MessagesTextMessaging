package text.message.sms.messaging.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A message joined with its MMS parts. SMS rows simply carry an empty list. */
data class MessageWithAttachments(
    @Embedded
    val message: MessageEntity,

    @Relation(parentColumn = "id", entityColumn = "message_id")
    val attachments: List<AttachmentEntity>,
)
