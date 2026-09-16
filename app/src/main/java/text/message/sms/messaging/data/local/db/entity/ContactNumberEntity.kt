package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One phone number belonging to a [ContactEntity]. */
@Entity(
    tableName = "contact_numbers",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["contact_id"]),
        Index(value = ["normalized_address"]),
    ],
)
data class ContactNumberEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "contact_id")
    val contactId: Long,

    @ColumnInfo(name = "address")
    val address: String,

    /** Digits-only form used for matching, since carriers vary the formatting. */
    @ColumnInfo(name = "normalized_address")
    val normalizedAddress: String,
)
