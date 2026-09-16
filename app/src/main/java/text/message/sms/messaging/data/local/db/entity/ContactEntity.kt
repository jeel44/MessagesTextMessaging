package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached mirror of a row in the system contacts provider. */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["lookup_key"], unique = true)],
)
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "lookup_key")
    val lookupKey: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,

    @ColumnInfo(name = "is_starred")
    val isStarred: Boolean = false,
)
