package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached mirror of a row in `ContactsContract.Groups` -- a real, user-created group ("Family",
 * "Work"), used to label and pick group-MMS recipients. Auto-add, deleted, favorites and
 * untitled provider rows are filtered out before this table is populated, since none of those
 * are groups a person would recognize or pick from.
 */
@Entity(tableName = "contact_groups")
data class ContactGroupEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "title")
    val title: String,
)
