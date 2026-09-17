package text.message.sms.messaging.data.mapper

import text.message.sms.messaging.data.local.db.dao.ContactGroupRow
import text.message.sms.messaging.data.local.db.entity.ContactEntity
import text.message.sms.messaging.data.local.db.entity.ContactWithNumbers
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.ContactGroup

fun ContactWithNumbers.toDomain(): Contact = Contact(
    id = contact.id,
    lookupKey = contact.lookupKey,
    displayName = contact.displayName,
    photoUri = contact.photoUri,
    numbers = numbers.map { it.address },
    isStarred = contact.isStarred,
)

/** A group member's own numbers aren't loaded by [text.message.sms.messaging.data.local.db.dao.ContactGroupDao.observeGroupRows]
 * (it joins only the `contacts` table, not `contact_numbers`) -- a group listing shows a
 * contact's name and photo, not their number, so that join was left out rather than paid for on
 * every group query. */
fun ContactEntity.toDomain(): Contact = Contact(
    id = id,
    lookupKey = lookupKey,
    displayName = displayName,
    photoUri = photoUri,
    numbers = emptyList(),
    isStarred = isStarred,
)

/** Groups the flat (group, member) rows [text.message.sms.messaging.data.local.db.dao.ContactGroupDao.observeGroupRows]
 * returns back into one [ContactGroup] per distinct group id, preserving the title's `ORDER BY`
 * from that query. */
fun List<ContactGroupRow>.toDomainGroups(): List<ContactGroup> =
    groupBy { it.groupId }
        .map { (groupId, rows) ->
            ContactGroup(
                id = groupId,
                title = rows.first().title,
                contacts = rows.map { it.contact.toDomain() },
            )
        }
        .sortedBy { it.title }
