package text.message.sms.messaging.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NUMBER = "+917990096382"
private const val OTHER_NUMBER = "+916351000085"

/** [CallEndShownGate]: a true duplicate of one call end is dropped; every other call gets its screen. */
class CallEndShownGateTest {

    private var now = 50_000L
    private val gate = CallEndShownGate(elapsedRealtime = { now })

    @Test
    fun firstClaim_wins() {
        assertTrue(gate.tryClaim(NUMBER))
    }

    @Test
    fun sameNumberWithinTheWindow_isADuplicate() {
        assertTrue(gate.tryClaim(NUMBER))
        now += CALL_END_DUPLICATE_WINDOW_MILLIS - 1
        assertFalse(gate.tryClaim(NUMBER))
    }

    @Test
    fun sameNumberOnceTheWindowHasPassed_wins() {
        assertTrue(gate.tryClaim(NUMBER))
        now += CALL_END_DUPLICATE_WINDOW_MILLIS
        assertTrue(gate.tryClaim(NUMBER))
    }

    @Test
    fun missedCallTenSecondsAfterThePreviousCall_wins() {
        // The Android 16 case the old 15s window dropped.
        assertTrue(gate.tryClaim(NUMBER))
        now += 10_000
        assertTrue(gate.tryClaim(NUMBER))
    }

    @Test
    fun differentNumberWithinTheWindow_wins() {
        assertTrue(gate.tryClaim(NUMBER))
        now += 500
        assertTrue(gate.tryClaim(OTHER_NUMBER))
    }

    @Test
    fun differentNumberResetsTheWindow_forTheFirstNumber() {
        assertTrue(gate.tryClaim(NUMBER))
        now += 500
        assertTrue(gate.tryClaim(OTHER_NUMBER))
        now += 500
        assertTrue(gate.tryClaim(NUMBER))
    }

    @Test
    fun withheldNumbers_dedupeWithEachOther_butNotWithARealNumber() {
        assertTrue(gate.tryClaim(null))
        now += 500
        assertFalse(gate.tryClaim(null))
        assertTrue(gate.tryClaim(NUMBER))
    }

    @Test
    fun aLosingClaim_doesNotExtendTheWindow() {
        assertTrue(gate.tryClaim(NUMBER))
        now += 1_500
        assertFalse(gate.tryClaim(NUMBER))
        now += CALL_END_DUPLICATE_WINDOW_MILLIS - 1_500
        assertTrue(gate.tryClaim(NUMBER))
    }

    @Test
    fun clockNearZero_firstClaimStillWins() {
        now = 0L
        assertTrue(gate.tryClaim(NUMBER))
    }
}
