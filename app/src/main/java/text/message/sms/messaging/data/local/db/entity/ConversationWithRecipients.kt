package text.message.sms.messaging.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A conversation joined with its participants, which is how the UI always needs it. */
data class ConversationWithRecipients(
    @Embedded
    val conversation: ConversationEntity,

    @Relation(parentColumn = "thread_id", entityColumn = "thread_id")
    val recipients: List<RecipientEntity>,
)
