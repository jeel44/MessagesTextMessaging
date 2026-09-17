package text.message.sms.messaging.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.data.local.db.entity.ContactEntity
import text.message.sms.messaging.data.local.db.entity.ContactGroupEntity
import text.message.sms.messaging.data.local.db.entity.ContactGroupMemberEntity
import text.message.sms.messaging.data.local.db.entity.ContactGroupWithContacts

@Dao
interface ContactGroupDao {

    @Upsert
    suspend fun upsertGroups(groups: List<ContactGroupEntity>)

    @Upsert
    suspend fun upsertMembers(members: List<ContactGroupMemberEntity>)

    @Query("DELETE FROM contact_groups")
    suspend fun clearGroups()

    @Query("DELETE FROM contact_group_members")
    suspend fun clearMembers()

    @Transaction
    suspend fun replaceAll(groups: List<ContactGroupEntity>, members: List<ContactGroupMemberEntity>) {
        // Members are foreign-keyed to groups with ON DELETE CASCADE, so clearing groups first
        // would already drop every member row -- clearMembers() here is belt-and-suspenders for
        // the case a future change relaxes that cascade, and costs nothing since the table is
        // about to be fully repopulated either way.
        clearGroups()
        clearMembers()
        upsertGroups(groups)
        upsertMembers(members)
    }

    /**
     * Every group, each joined with its member contacts. The join goes through
     * [ContactGroupMemberEntity.contactLookupKey] into [ContactEntity.lookupKey] rather than a
     * Room `@Relation`, since a junction's far side must target the related entity's primary
     * key and a contact's lookup key is not [ContactEntity.id] -- see
     * [ContactGroupWithContacts]'s class doc.
     */
    @Query(
        """
        SELECT g.id AS group_id, g.title AS group_title, c.* FROM contact_groups g
        INNER JOIN contact_group_members m ON m.group_id = g.id
        INNER JOIN contacts c ON c.lookup_key = m.contact_lookup_key
        ORDER BY g.title ASC
        """
    )
    fun observeGroupRows(): Flow<List<ContactGroupRow>>
}

/**
 * One (group, member) pair as Room hands it back; [ContactGroupWithContacts] is assembled from
 * these by grouping on [groupId] -- see the repository layer. `group_id`/`group_title` are
 * explicitly aliased in the query above so they can't collide with [ContactEntity]'s own
 * embedded `id`/other columns, which Room otherwise maps by bare column name.
 */
data class ContactGroupRow(
    @androidx.room.ColumnInfo(name = "group_id")
    val groupId: Long,

    @androidx.room.ColumnInfo(name = "group_title")
    val title: String,

    @androidx.room.Embedded
    val contact: ContactEntity,
)
