package text.message.sms.messaging.data.local.provider

import android.content.ContentResolver
import android.provider.ContactsContract
import text.message.sms.messaging.data.local.db.entity.ContactEntity
import text.message.sms.messaging.data.local.db.entity.ContactGroupEntity
import text.message.sms.messaging.data.local.db.entity.ContactGroupMemberEntity
import text.message.sms.messaging.data.local.db.entity.ContactNumberEntity
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the system contacts provider into the shape the local cache stores. */
@Singleton
class ContactProviderGateway @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    /** Reads every contact that has at least one phone number. */
    fun readContacts(): ContactSnapshot {
        val contacts = mutableMapOf<Long, ContactEntity>()
        val numbers = mutableListOf<ContactNumberEntity>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        )

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            )
            val lookupColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            )
            val nameColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            )
            val photoColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            )
            val starredColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.STARRED,
            )
            val numberColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            val superPrimaryColumn = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            )

            // Keyed by contact id, so duplicate numbers are only ever collapsed against other
            // numbers belonging to the *same* contact -- never across different contacts.
            val rawNumbersByContact = mutableMapOf<Long, MutableList<RawNumber>>()

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idColumn)
                contacts.getOrPut(contactId) {
                    ContactEntity(
                        id = contactId,
                        lookupKey = cursor.getString(lookupColumn).orEmpty(),
                        displayName = cursor.getString(nameColumn).orEmpty(),
                        photoUri = cursor.getString(photoColumn),
                        isStarred = cursor.getInt(starredColumn) == 1,
                    )
                }

                val address = cursor.getString(numberColumn) ?: continue
                rawNumbersByContact.getOrPut(contactId) { mutableListOf() } += RawNumber(
                    address = address,
                    isSuperPrimary = cursor.getInt(superPrimaryColumn) == 1,
                )
            }

            rawNumbersByContact.forEach { (contactId, rawNumbers) ->
                numbers += dedupeNumbers(contactId, rawNumbers)
            }
        }

        return ContactSnapshot(contacts.values.toList(), numbers)
    }

    /**
     * Reads real, user-created contact groups ("Family", "Work") and which contacts belong to
     * each. Excludes auto-add groups (every contacts-sync-adapter tends to create one of these
     * per account, invisible to the user), deleted rows still pending provider cleanup, the
     * built-in favorites group (already covered by [ContactEntity.isStarred]), and rows with no
     * title at all.
     */
    fun readGroups(): GroupSnapshot {
        val groups = mutableListOf<ContactGroupEntity>()

        val groupProjection = arrayOf(
            ContactsContract.Groups._ID,
            ContactsContract.Groups.TITLE,
        )
        val groupSelection = "${ContactsContract.Groups.AUTO_ADD}=0 " +
            "AND ${ContactsContract.Groups.DELETED}=0 " +
            "AND ${ContactsContract.Groups.FAVORITES}=0 " +
            "AND ${ContactsContract.Groups.TITLE} IS NOT NULL"

        contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            groupProjection,
            groupSelection,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)

            while (cursor.moveToNext()) {
                groups += ContactGroupEntity(
                    id = cursor.getLong(idColumn),
                    title = cursor.getString(titleColumn).orEmpty(),
                )
            }
        }

        val members = mutableListOf<ContactGroupMemberEntity>()

        val memberProjection = arrayOf(
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DATA1,
        )
        val memberSelection = "${ContactsContract.Data.MIMETYPE}=?"
        val memberSelectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
        )

        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            memberProjection,
            memberSelection,
            memberSelectionArgs,
            null,
        )?.use { cursor ->
            val lookupColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.LOOKUP_KEY)
            // DATA1 holds the group row id for a GroupMembership row -- there's no dedicated
            // constant for it, same as QKSMS's own reader for this table.
            val groupIdColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)

            while (cursor.moveToNext()) {
                val lookupKey = cursor.getString(lookupColumn) ?: continue
                members += ContactGroupMemberEntity(
                    groupId = cursor.getLong(groupIdColumn),
                    contactLookupKey = lookupKey,
                )
            }
        }

        // A membership row can point at a group that got filtered out above (an auto-add group,
        // say) -- drop those here rather than let them become orphaned rows the foreign key
        // would reject anyway.
        val validGroupIds = groups.mapTo(mutableSetOf()) { it.id }
        return GroupSnapshot(groups, members.filter { it.groupId in validGroupIds })
    }

    /**
     * Collapses numbers that reach the same handset into one row per contact. Some providers
     * (WhatsApp is the common culprit) inject a near-duplicate [ContactsContract] row for a
     * number that's already there -- same digits, different formatting or a differing country
     * code prefix -- which without this would show up as separate, redundant numbers on one
     * contact. When a duplicate group contains a row marked super-primary (the contact app's
     * "default number for this contact"), that formatting wins; otherwise the first one seen is
     * kept.
     */
    private fun dedupeNumbers(contactId: Long, rawNumbers: List<RawNumber>): List<ContactNumberEntity> {
        val kept = mutableListOf<RawNumber>()

        rawNumbers.forEach { candidate ->
            val duplicateIndex = kept.indexOfFirst { PhoneNumbers.areEquivalent(it.address, candidate.address) }
            when {
                duplicateIndex == -1 -> kept += candidate
                candidate.isSuperPrimary && !kept[duplicateIndex].isSuperPrimary -> kept[duplicateIndex] = candidate
                // Otherwise the existing entry is kept as-is: either it's already
                // super-primary, or neither row is and the first one seen wins.
            }
        }

        return kept.map { raw ->
            ContactNumberEntity(
                contactId = contactId,
                address = raw.address,
                normalizedAddress = PhoneNumbers.normalize(raw.address),
            )
        }
    }

    private data class RawNumber(val address: String, val isSuperPrimary: Boolean)

    /** Contacts and their numbers, read in one pass over the provider. */
    data class ContactSnapshot(
        val contacts: List<ContactEntity>,
        val numbers: List<ContactNumberEntity>,
    )

    /** Groups and their membership rows, read in one pass over the provider. */
    data class GroupSnapshot(
        val groups: List<ContactGroupEntity>,
        val members: List<ContactGroupMemberEntity>,
    )
}
