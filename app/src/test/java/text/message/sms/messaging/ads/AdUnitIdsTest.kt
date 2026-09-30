package text.message.sms.messaging.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which ID each [AdPlacement] requests under both states of the test-ads switch. Drives
 * [resolveAdUnitId] with the flag as an argument rather than reading [AdUnitIds.USE_TEST_ADS], so
 * both states are covered whatever `useTestAds` is set to in `app/build.gradle.kts`.
 */
class AdUnitIdsTest {

    /** `ca-app-pub-<16-digit publisher>/<10-digit unit>`. */
    private val adUnitIdFormat = Regex("""ca-app-pub-\d{16}/\d{10}""")
    private val googleTestPublisher = "ca-app-pub-3940256099942544/"

    @Test
    fun testAdsOn_everyPlacementRequestsAGoogleTestUnit() {
        for (placement in AdPlacement.entries) {
            var safeguardFired = false

            val id = resolveAdUnitId(placement.testId, placement.realId, useTestAds = true) {
                safeguardFired = true
            }

            assertTrue("$placement: $id", id.startsWith(googleTestPublisher) && adUnitIdFormat.matches(id))
            assertFalse("$placement: safeguard fired for a test ID", safeguardFired)
        }
    }

    @Test
    fun testAdsOff_everyPlacementRequestsItsRealUnitOrTripsTheSafeguard() {
        for (placement in AdPlacement.entries) {
            var safeguardFired = false

            val id = resolveAdUnitId(placement.testId, placement.realId, useTestAds = false) {
                safeguardFired = true
            }

            assertEquals(placement.realId, id)
            if (placement.realId.startsWith(AD_ID_PLACEHOLDER_PREFIX)) {
                assertTrue("$placement: placeholder requested without the safeguard", safeguardFired)
            } else {
                assertTrue("$placement: malformed real ID $id", adUnitIdFormat.matches(id))
                assertFalse("$placement: real ID is a Google test unit", id.startsWith(googleTestPublisher))
                assertFalse("$placement: safeguard fired for a real ID", safeguardFired)
            }
        }
    }

    @Test
    fun testAdsOff_aFilledInRealIdIsRequestedWithoutTheSafeguard() {
        var safeguardFired = false

        val id = resolveAdUnitId(
            testId = "ca-app-pub-3940256099942544/2247696110",
            realId = "ca-app-pub-1234567890123456/1234567890",
            useTestAds = false,
        ) { safeguardFired = true }

        assertEquals("ca-app-pub-1234567890123456/1234567890", id)
        assertFalse(safeguardFired)
    }

    @Test
    fun anUnfilledRealIdIsNamedAfterItsPlacement() {
        for (placement in AdPlacement.entries) {
            if (placement.realId.startsWith(AD_ID_PLACEHOLDER_PREFIX)) {
                assertEquals("REPLACE_ME_${placement.name}", placement.realId)
            }
        }
    }
}
