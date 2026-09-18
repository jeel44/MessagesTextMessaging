package text.message.sms.messaging.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.db.entity.ConversationWithRecipients
import text.message.sms.messaging.data.local.db.entity.RecipientEntity
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.util.PhoneNumbers

/**
 * The real, verified trigger for "conversation list shows a number instead of the saved name",
 * for an ordinary person-to-person conversation (never a promotional/bank alphanumeric sender id
 * -- those never match a contact regardless, so they aren't this bug):
 *
 * A [RecipientEntity.address] is copied verbatim from a [Contact]'s own saved number only the one
 * time a brand-new thread is created by picking that contact in `NewMessageScreen` -- both sides
 * of the lookup would use the identical string then, so that path alone could never mismatch.
 * Every other recipient row -- the address on any thread that has ever *received* a message, i.e.
 * nearly every real two-way personal conversation -- comes from
 * `TelephonySyncRepository`/`SmsProviderGateway`/`MmsProviderGateway` reading the system
 * Telephony provider's own `ADDRESS` column, a format entirely under the carrier/OEM telephony
 * stack's control and never reconciled against Contacts afterward. It routinely disagrees with
 * Contacts on whether a country code is present for the exact same handset -- these tests
 * reproduce that pairing directly against the real [ConversationWithRecipients.toDomain] mapping
 * function, the same way [text.message.sms.messaging.data.repository.LocalConversationRepository.contactsByComparableSuffix]
 * feeds it.
 */
class ConversationMapperTest {

    @Test
    fun `toDomain resolves a contact saved without a country code against a Telephony-reported incoming address that includes one`() {
        // Contact saved by typing a plain local 10-digit number, the common case when the user
        // adds a contact themselves rather than importing from an account.
        val contact = Contact(
            id = 1L,
            lookupKey = "lookup-1",
            displayName = "Priya Test",
            photoUri = null,
            numbers = listOf("9876543210"),
        )
        val contactsByComparableSuffix = mapOf(PhoneNumbers.comparableSuffix("9876543210") to contact)

        // The recipient row as TelephonySyncRepository would have written it after Priya's first
        // incoming message -- Telephony.Sms.ADDRESS reported the sender with a country code, no
        // leading '+', which is a real, commonly observed carrier delivery-report format.
        val conversation = ConversationWithRecipients(
            conversation = ConversationEntity(threadId = 42L),
            recipients = listOf(
                RecipientEntity(threadId = 42L, address = "919876543210"),
            ),
        )

        // Prerequisite for this test to actually prove anything: confirm the two addresses truly
        // diverge under the old exact-match key, so a pass below is because comparableSuffix
        // reconciles them, not because they happened to already match.
        assertNotEquals(
            PhoneNumbers.normalize("9876543210"),
            PhoneNumbers.normalize("919876543210"),
        )

        val domain = conversation.toDomain(contactsByComparableSuffix)

        assertEquals("Priya Test", domain.title)
        assertEquals(contact, domain.recipients.single().contact)
    }

    @Test
    fun `toDomain resolves a contact saved with a country code against a Telephony-reported address that omits it`() {
        // Contact synced in from an account that stores international format.
        val contact = Contact(
            id = 2L,
            lookupKey = "lookup-2",
            displayName = "Arjun Test",
            photoUri = null,
            numbers = listOf("+91 98765 43210"),
        )
        val contactsByComparableSuffix = mapOf(PhoneNumbers.comparableSuffix("+91 98765 43210") to contact)

        val conversation = ConversationWithRecipients(
            conversation = ConversationEntity(threadId = 43L),
            recipients = listOf(
                RecipientEntity(threadId = 43L, address = "9876543210"),
            ),
        )

        assertNotEquals(
            PhoneNumbers.normalize("+91 98765 43210"),
            PhoneNumbers.normalize("9876543210"),
        )

        val domain = conversation.toDomain(contactsByComparableSuffix)

        assertEquals("Arjun Test", domain.title)
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

    @Test
    fun `toDomain never matches an alphanumeric promotional sender id to a contact`() {
        // A short alphanumeric sender id (e.g. a bank/OTP sender) is never a phone number, so it
        // legitimately can never match a contact under any normalization scheme -- this is not an
        // instance of the bug, just confirming the fix doesn't overreach into matching unrelated
        // things.
        val contact = Contact(
            id = 3L,
            lookupKey = "lookup-3",
            displayName = "Priya Test",
            photoUri = null,
            numbers = listOf("9876543210"),
        )
        val contactsByComparableSuffix = mapOf(PhoneNumbers.comparableSuffix("9876543210") to contact)

        val conversation = ConversationWithRecipients(
            conversation = ConversationEntity(threadId = 45L),
            recipients = listOf(
                RecipientEntity(threadId = 45L, address = "JD-SBIBNK-S"),
            ),
        )

        val domain = conversation.toDomain(contactsByComparableSuffix)

        assertEquals("JD-SBIBNK-S", domain.title)
        assertNull(domain.recipients.single().contact)
    }
}
