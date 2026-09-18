package text.message.sms.messaging.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `normalize strips formatting but keeps a leading plus`() {
        assertEquals("+15550101234", PhoneNumbers.normalize("+1 (555) 010-1234"))
        assertEquals("5550101234", PhoneNumbers.normalize("(555) 010-1234"))
    }

    @Test
    fun `areEquivalent matches numbers that differ only in formatting or country code prefix`() {
        assertTrue(PhoneNumbers.areEquivalent("+1 555 010 1234", "5550101234"))
        assertTrue(PhoneNumbers.areEquivalent("555-010-1234", "(555) 010-1234"))
    }

    @Test
    fun `areEquivalent rejects genuinely different numbers`() {
        assertFalse(PhoneNumbers.areEquivalent("5550101234", "5550109999"))
    }

    @Test
    fun `areEquivalent rejects two blank addresses rather than matching them`() {
        assertFalse(PhoneNumbers.areEquivalent("", ""))
    }

    @Test
    fun `isShortCode accepts typical carrier short codes and rejects real numbers`() {
        assertTrue(PhoneNumbers.isShortCode("22333"))
        assertFalse(PhoneNumbers.isShortCode("5550101234"))
    }

    @Test
    fun `comparableSuffix matches formatting and country-code prefix differences that normalize alone would not`() {
        // The exact pairing that motivates comparableSuffix as a *lookup key*: normalize() keeps
        // country code presence/absence as a literal difference, so these two would land on
        // different map keys even though areEquivalent (and comparableSuffix) treat them as the
        // same handset -- see LocalConversationRepository.contactsByComparableSuffix's doc for the
        // real-world bug this was.
        assertEquals(
            PhoneNumbers.comparableSuffix("+1 555 010 1234"),
            PhoneNumbers.comparableSuffix("5550101234"),
        )
        assertTrue(
            PhoneNumbers.normalize("+1 555 010 1234") != PhoneNumbers.normalize("5550101234"),
        )
    }

    @Test
    fun `comparableSuffix of a digit-free string is empty`() {
        // A promotional/bank alphanumeric sender id has no digits at all, so it comparableSuffix-
        // es to "" -- callers building a suffix-keyed lookup map must skip empty suffixes (see
        // LocalConversationRepository.contactsByComparableSuffix's guard) or two unrelated
        // digit-free strings would collide on the same "" key.
        assertEquals("", PhoneNumbers.comparableSuffix("JD-SBIBNK-S"))
        assertEquals("", PhoneNumbers.comparableSuffix(""))
    }

    @Test
    fun `looksLikePhoneNumber accepts real numbers and short codes in common formats`() {
        assertTrue(PhoneNumbers.looksLikePhoneNumber("+917990096382"))
        assertTrue(PhoneNumbers.looksLikePhoneNumber("+1 (555) 010-1234"))
        assertTrue(PhoneNumbers.looksLikePhoneNumber("5550101234"))
        assertTrue(PhoneNumbers.looksLikePhoneNumber("22333"))
    }

    @Test
    fun `looksLikePhoneNumber rejects an RCS business agent address`() {
        // The exact address shape from the "2 participants" bug: a real RCS Business Messaging
        // sender is not a phone number and must never be treated like one.
        assertFalse(PhoneNumbers.looksLikePhoneNumber("angel_one_iishg0wq_agent@rbm.goog"))
    }

    @Test
    fun `looksLikePhoneNumber rejects alphanumeric sender ids and blank input`() {
        assertFalse(PhoneNumbers.looksLikePhoneNumber("JD-SBIBNK-S"))
        assertFalse(PhoneNumbers.looksLikePhoneNumber(""))
        assertFalse(PhoneNumbers.looksLikePhoneNumber("   "))
    }

    @Test
    fun `realParticipantsOnly leaves a normal 1-to-1 SMS or MMS thread as a single participant`() {
        val participants = PhoneNumbers.realParticipantsOnly(setOf("+15550101234"))

        assertEquals(setOf("+15550101234"), participants)
        assertFalse("a single real number must never read as a group", participants.size > 1)
    }

    @Test
    fun `realParticipantsOnly keeps every real number in a genuine group MMS`() {
        val realNumbers = setOf("+15550101234", "+15550109999", "+15550105555")

        val participants = PhoneNumbers.realParticipantsOnly(realNumbers)

        assertEquals(realNumbers, participants)
        assertTrue("three real numbers must still read as a group", participants.size > 1)
    }

    @Test
    fun `realParticipantsOnly drops the RCS business address so the thread stays 1-to-1`() {
        // The reported bug: a real number plus an RBM business agent address were both counted
        // as participants, producing "2 participants" and a group icon for what is really a
        // single-sender business conversation.
        val rawAddresses = setOf("+917990096382", "angel_one_iishg0wq_agent@rbm.goog")

        val participants = PhoneNumbers.realParticipantsOnly(rawAddresses)

        assertEquals(setOf("+917990096382"), participants)
        assertFalse("an RCS business address must not flip the thread to a group", participants.size > 1)
    }

    @Test
    fun `realParticipantsOnly falls back to the raw set when nothing looks like a phone number`() {
        // Resolving some thread beats silently dropping the message this participant set came
        // from -- see PhoneNumbers.realParticipantsOnly's doc.
        val rawAddresses = setOf("angel_one_iishg0wq_agent@rbm.goog", "another_agent@rbm.goog")

        assertEquals(rawAddresses, PhoneNumbers.realParticipantsOnly(rawAddresses))
    }
}
