package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** A message row plus its `scheduled_messages.send_at` -- query results only, not a table. */
data class ScheduledMessageRow(
    @Embedded val message: MessageEntity,
    @ColumnInfo(name = "scheduled_send_at") val sendAtMillis: Long,
)

/** One scheduled message's id and send time -- query results only, not a table. */
data class ScheduledSendTime(
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "send_at") val sendAtMillis: Long,
)
