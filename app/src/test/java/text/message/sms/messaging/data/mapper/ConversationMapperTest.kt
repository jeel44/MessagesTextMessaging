package text.message.sms.messaging.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.ConversationWithRecipients
import text.message.sms.messaging.data.local.db.entity.RecipientEntity
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.util.PhoneNumbers

class ConversationMapperTest {

    /**
     * The exact bug this covers: a contact saved in Contacts *without* a country code
     * ("5550101234") next to a recipient row whose address came from a source that *does* include
     * one ("+15550101234", e.g. the system Telephony provider) -- or the reverse. Both addresses
     * reach the same handset, but a lookup keyed by [PhoneNumbers.normalize] alone would treat
     * them as unrelated strings and never resolve a name. See
     * [text.message.sms.messaging.data.repository.LocalConversationRepository.contactsByComparableSuffix].
     */
    @Test
    fun `toDomain resolves a contact whose saved number omits the country code a recipient address includes`() {
        val contact = Contact(
            id = 1L,
            lookupKey = "lookup-1",
            displayName = "Jordan Test",
            photoUri = null,
            numbers = listOf("5550101234"),
        )
        val contactsByComparableSuffix = mapOf(PhoneNumbers.comparableSuffix("5550101234") to contact)

        val conversation = ConversationWithRecipients(
            conversation = ConversationEntity(threadId = 42L),
            recipients = listOf(
                RecipientEntity(threadId = 42L, address = "+15550101234"),
            ),
        )

        val domain = conversation.toDomain(contactsByComparableSuffix)

        assertEquals("Jordan Test", domain.title)
        assertEquals(contact, domain.recipients.single().contact)
    }

    /** Same bug, the other direction: contact saved *with* a country code, recipient address
     * without one -- e.g. a brand-new outgoing thread created by picking that contact from
     * Contacts, whose own saved number happens to carry the "+1" the app's own Telephony-synced
     * threads don't always have. */
    @Test
    fun `toDomain resolves a contact whose saved number includes a country code a recipient address omits`() {
        val contact = Contact(
            id = 2L,
            lookupKey = "lookup-2",
            displayName = "Riley Test",
            photoUri = null,
            numbers = listOf("+1 555-010-1234"),
        )
        val contactsByComparableSuffix = mapOf(PhoneNumbers.comparableSuffix("+1 555-010-1234") to contact)

        val conversation = ConversationWithRecipients(
            conversation = ConversationEntity(threadId = 43L),
            recipients = listOf(
                RecipientEntity(threadId = 43L, address = "5550101234"),
            ),
        )

        val domain = conversation.toDomain(contactsByComparableSuffix)

        assertEquals("Riley Test", domain.title)
    }

    @Test
    fun `toDomain falls back to the raw address when no contact matches`() {
        val conversation = ConversationWithRecipients(
            conversation = ConversationEntity(threadId = 44L),
            recipients = listOf(
                RecipientEntity(threadId = 44L, address = "5559998888"),
            ),
        )

        val domain = conversation.toDomain(emptyMap())

        assertEquals("5559998888", domain.title)
        assertNull(domain.recipients.single().contact)
    }
}
