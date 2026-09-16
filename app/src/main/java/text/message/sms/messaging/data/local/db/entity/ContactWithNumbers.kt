package text.message.sms.messaging.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A contact joined with every phone number it owns. */
data class ContactWithNumbers(
    @Embedded
    val contact: ContactEntity,

    @Relation(parentColumn = "id", entityColumn = "contact_id")
    val numbers: List<ContactNumberEntity>,
)
