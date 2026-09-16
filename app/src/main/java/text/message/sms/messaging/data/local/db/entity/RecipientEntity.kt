package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One participant address within a thread; a group thread has several. */
@Entity(
    tableName = "recipients",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["thread_id"],
            childColumns = ["thread_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["thread_id"]),
        Index(value = ["address"]),
    ],
)
data class RecipientEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "address")
    val address: String,

    /** Links to [ContactEntity.lookupKey] when the address resolves to a saved contact. */
    @ColumnInfo(name = "contact_lookup_key")
    val contactLookupKey: String? = null,
)
