package text.message.sms.messaging.data.mapper

import text.message.sms.messaging.data.local.db.entity.ContactWithNumbers
import text.message.sms.messaging.domain.model.Contact

fun ContactWithNumbers.toDomain(): Contact = Contact(
    id = contact.id,
    lookupKey = contact.lookupKey,
    displayName = contact.displayName,
    photoUri = contact.photoUri,
    numbers = numbers.map { it.address },
    isStarred = contact.isStarred,
)
