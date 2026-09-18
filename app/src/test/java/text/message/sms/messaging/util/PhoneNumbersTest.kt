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
}
