package text.message.sms.messaging.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One contact's membership in one [ContactGroupEntity]. Joined on [contactLookupKey] rather
 * than a contact row id -- that's what the provider's own group-membership rows carry, and a
 * contact's numeric id is not guaranteed stable across a contacts sync the way its lookup key
 * is.
 */
@Entity(
    tableName = "contact_group_members",
    primaryKeys = ["group_id", "contact_lookup_key"],
    foreignKeys = [
        ForeignKey(
            entity = ContactGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["contact_lookup_key"]),
    ],
)
data class ContactGroupMemberEntity(
    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "contact_lookup_key")
    val contactLookupKey: String,
)
