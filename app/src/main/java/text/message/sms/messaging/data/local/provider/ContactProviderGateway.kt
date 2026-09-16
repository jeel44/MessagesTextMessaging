package text.message.sms.messaging.data.local.provider

import android.content.ContentResolver
import android.provider.ContactsContract
import text.message.sms.messaging.data.local.db.entity.ContactEntity
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
                numbers += ContactNumberEntity(
                    contactId = contactId,
                    address = address,
                    normalizedAddress = PhoneNumbers.normalize(address),
                )
            }
        }

        return ContactSnapshot(contacts.values.toList(), numbers)
    }

    /** Contacts and their numbers, read in one pass over the provider. */
    data class ContactSnapshot(
        val contacts: List<ContactEntity>,
        val numbers: List<ContactNumberEntity>,
    )
}
