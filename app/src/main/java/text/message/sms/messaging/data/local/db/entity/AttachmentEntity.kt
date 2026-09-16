package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One MMS part belonging to a [MessageEntity]. */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["message_id"])],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "file_name")
    val fileName: String? = null,

    @ColumnInfo(name = "content_uri")
    val contentUri: String? = null,

    @ColumnInfo(name = "byte_size")
    val byteSize: Long = 0L,

    /** Inline text for text parts, which carry no separate file. */
    @ColumnInfo(name = "text")
    val text: String? = null,
)
