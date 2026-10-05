package text.message.sms.messaging.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import text.message.sms.messaging.ads.AdConsentState

class CollectionDecisionTest {

    @Test
    fun `nothing is decided while consent is pending`() {
        assertNull(decideCollection(AdConsentState.Pending, gdprApplies = 0, purposeConsents = null))
        assertNull(decideCollection(AdConsentState.Pending, gdprApplies = 1, purposeConsents = "1111111111"))
    }

    @Test
    fun `outside GDPR everything is granted`() {
        assertEquals(CollectionDecision.GRANTED_ALL, decideCollection(AdConsentState.Allowed, 0, null))
        assertEquals(CollectionDecision.GRANTED_ALL, decideCollection(AdConsentState.Allowed, null, null))
    }

    @Test
    fun `EEA user who consented to everything`() {
        assertEquals(CollectionDecision.GRANTED_ALL, decideCollection(AdConsentState.Allowed, 1, "1111111111"))
    }

    @Test
    fun `EEA user who declined -- ads still allowed (limited), analytics off`() {
        val decision = decideCollection(AdConsentState.Allowed, 1, "0000000000")
        assertEquals(CollectionDecision.DENIED_ALL, decision)
    }

    @Test
    fun `EEA consent not given yet means off`() {
        assertEquals(CollectionDecision.DENIED_ALL, decideCollection(AdConsentState.Unavailable, 1, null))
    }

    @Test
    fun `EEA partial consent maps TCF purposes`() {
        // Purpose 1 and 7 granted, 3/4 not: storage and ad user data, no personalization.
        assertEquals(
            CollectionDecision(analyticsStorage = true, adStorage = true, adUserData = true, adPersonalization = false),
            decideCollection(AdConsentState.Allowed, 1, "1000001000"),
        )
    }

    @Test
    fun `failed consent check with unknown region stays off`() {
        assertEquals(CollectionDecision.DENIED_ALL, decideCollection(AdConsentState.Unavailable, null, null))
    }
}
