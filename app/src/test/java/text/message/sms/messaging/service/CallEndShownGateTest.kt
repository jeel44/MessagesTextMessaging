package text.message.sms.messaging.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CallEndShownGate]: one call end, two launch paths, one call-end screen. */
class CallEndShownGateTest {

    private var now = 50_000L
    private val gate = CallEndShownGate(elapsedRealtime = { now })

    @Test
    fun firstClaim_wins() {
        assertTrue(gate.tryClaim())
    }

    @Test
    fun secondClaimForTheSameCallEnd_loses() {
        assertTrue(gate.tryClaim())
        now += 3_000
        assertFalse(gate.tryClaim())
    }

    @Test
    fun claimJustInsideTheWindow_loses() {
        assertTrue(gate.tryClaim())
        now += CALL_END_DEDUPE_WINDOW_MILLIS - 1
        assertFalse(gate.tryClaim())
    }

    @Test
    fun claimOnceTheWindowHasPassed_wins() {
        assertTrue(gate.tryClaim())
        now += CALL_END_DEDUPE_WINDOW_MILLIS
        assertTrue(gate.tryClaim())
    }

    @Test
    fun aLosingClaim_doesNotExtendTheWindow() {
        assertTrue(gate.tryClaim())
        now += 10_000
        assertFalse(gate.tryClaim())
        now += CALL_END_DEDUPE_WINDOW_MILLIS - 10_000
        assertTrue(gate.tryClaim())
    }

    @Test
    fun clockNearZero_firstClaimStillWins() {
        now = 0L
        assertTrue(gate.tryClaim())
    }
}
