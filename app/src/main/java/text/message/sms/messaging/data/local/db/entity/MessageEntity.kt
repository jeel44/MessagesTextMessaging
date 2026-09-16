package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder

/** A single SMS or MMS entry mirrored from the system Telephony provider. */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["thread_id"],
            childColumns = ["thread_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["thread_id", "received_at"]),
        Index(value = ["provider_id", "channel"], unique = true),
        Index(value = ["is_read"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    /** Row id inside the system provider; 0 until the message has been written there. */
    @ColumnInfo(name = "provider_id")
    val providerId: Long = 0L,

    @ColumnInfo(name = "channel")
    val channel: MessageChannel,

    @ColumnInfo(name = "folder")
    val folder: MessageFolder,

    @ColumnInfo(name = "delivery_state")
    val deliveryState: DeliveryState = DeliveryState.NONE,

    @ColumnInfo(name = "address")
    val address: String? = null,

    @ColumnInfo(name = "body")
    val body: String = "",

    @ColumnInfo(name = "subject")
    val subject: String? = null,

    @ColumnInfo(name = "sent_at")
    val sentAtMillis: Long = 0L,

    @ColumnInfo(name = "received_at")
    val receivedAtMillis: Long = 0L,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    @ColumnInfo(name = "is_seen")
    val isSeen: Boolean = false,

    /** SIM subscription the message travelled on; -1 means the platform default. */
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: Int = -1,

    @ColumnInfo(name = "error_code")
    val errorCode: Int = 0,
)
