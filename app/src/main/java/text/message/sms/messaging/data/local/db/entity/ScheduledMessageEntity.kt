package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A message queued to send at a future time. Kept as its own row (rather than folded into
 * [MessageEntity]) because it needs to survive both process death and a device reboot until
 * WorkManager's own persistence takes over -- see `TelephonyMessageTransmitter.schedule`.
 *
 * Any attachment content is copied into app-private storage at schedule time (see
 * `MmsAttachmentStorage`) rather than keeping the original `content://` URIs, since a picker
 * grant on those can be revoked long before a delayed send actually runs.
 */
@Entity(
    tableName = "scheduled_messages",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScheduledMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "send_at")
    val sendAtMillis: Long,

    /** WorkManager's unique work name for this send; stable and derived from [messageId], kept
     * here so it doesn't need to be recomputed by more than one call site. */
    @ColumnInfo(name = "work_name")
    val workName: String,

    @ColumnInfo(name = "created_at")
    val createdAtMillis: Long = System.currentTimeMillis(),
)
