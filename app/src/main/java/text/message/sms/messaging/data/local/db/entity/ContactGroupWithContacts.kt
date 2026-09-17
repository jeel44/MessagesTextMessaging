package text.message.sms.messaging.data.local.db.entity

import androidx.room.Embedded

/**
 * A group joined with every contact that belongs to it. Populated by
 * [text.message.sms.messaging.data.local.db.dao.ContactDao.observeGroupsWithContacts], not
 * Room's `@Relation`/`@Junction`: those require the junction's far side to match the target
 * entity's primary key, but [ContactGroupMemberEntity] carries a contact's `lookup_key` (the
 * stable, provider-assigned join key -- see its class doc), not [ContactEntity.id]. The DAO
 * query does that join directly instead.
 */
data class ContactGroupWithContacts(
    @Embedded
    val group: ContactGroupEntity,

    val contacts: List<ContactEntity>,
)
