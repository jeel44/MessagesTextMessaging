package text.message.sms.messaging.service

import android.telephony.TelephonyManager.CALL_STATE_IDLE
import android.telephony.TelephonyManager.CALL_STATE_OFFHOOK
import android.telephony.TelephonyManager.CALL_STATE_RINGING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession

private const val T0 = 1_700_000_000_000L

/**
 * [CallStateMonitor.onCallState]: which `PHONE_STATE` broadcasts count as a call having ended,
 * and what the monitor can say about that call from memory. The `CALL_STATE_*` constants are
 * compile-time ints, so this runs on the plain JVM.
 */
class CallStateMonitorTest {

    private val monitor = CallStateMonitor(log = {})

    @Test
    fun answeredIncomingCall_signalsOnIdle_withTalkTimeFromAnswerToEnd() {
        assertNull(monitor.onCallState(CALL_STATE_RINGING, T0))
        assertTrue(monitor.isCallInProgress)
        assertNull(monitor.onCallState(CALL_STATE_OFFHOOK, T0 + 8_000))

        val signal = monitor.onCallState(CALL_STATE_IDLE, T0 + 68_000)

        assertEquals(
            CallEndSignal(
                endedAt = T0 + 68_000,
                observedStartAt = T0,
                observed = CallSession(
                    phoneNumber = null,
                    direction = CallDirection.INCOMING,
                    startedAt = T0 + 8_000,
                    endedAt = T0 + 68_000,
                    durationMillis = 60_000,
                    outcome = CallOutcome.ANSWERED,
                ),
            ),
            signal,
        )
        assertFalse(monitor.isCallInProgress)
    }

    @Test
    fun missedCall_signalsOnIdle() {
        monitor.onCallState(CALL_STATE_RINGING, T0)

        val signal = monitor.onCallState(CALL_STATE_IDLE, T0 + 20_000)

        assertEquals(T0, signal?.observedStartAt)
        assertEquals(CallDirection.INCOMING, signal?.observed?.direction)
        assertEquals(CallOutcome.MISSED, signal?.observed?.outcome)
        assertEquals(0L, signal?.observed?.durationMillis)
    }

    @Test
    fun outgoingCall_signalsOnIdle() {
        assertNull(monitor.onCallState(CALL_STATE_OFFHOOK, T0))

        val signal = monitor.onCallState(CALL_STATE_IDLE, T0 + 45_000)

        assertEquals(T0, signal?.observedStartAt)
        assertEquals(CallDirection.OUTGOING, signal?.observed?.direction)
        assertEquals(45_000L, signal?.observed?.durationMillis)
    }

    @Test
    fun repeatedRingingOrOffhookBroadcasts_areIgnored() {
        monitor.onCallState(CALL_STATE_RINGING, T0)
        assertNull(monitor.onCallState(CALL_STATE_RINGING, T0 + 100))
        monitor.onCallState(CALL_STATE_OFFHOOK, T0 + 5_000)
        assertNull(monitor.onCallState(CALL_STATE_OFFHOOK, T0 + 5_100))

        // The repeats didn't disturb the timestamps taken from the first of each.
        val signal = monitor.onCallState(CALL_STATE_IDLE, T0 + 35_000)
        assertEquals(T0, signal?.observedStartAt)
        assertEquals(30_000L, signal?.observed?.durationMillis)
    }

    @Test
    fun duplicateIdleForTheSameCall_doesNotSignalTwice() {
        monitor.onCallState(CALL_STATE_OFFHOOK, T0)
        assertNotNull(monitor.onCallState(CALL_STATE_IDLE, T0 + 30_000))

        // The second SIM's IDLE ~300ms later, and another well inside the window.
        assertNull(monitor.onCallState(CALL_STATE_IDLE, T0 + 30_300))
        assertNull(monitor.onCallState(CALL_STATE_IDLE, T0 + 30_000 + UNOBSERVED_CALL_END_WINDOW_MILLIS - 1))
    }

    @Test
    fun idleWithNoCallTracked_signalsWithNothingObserved() {
        // Fresh process, cold-started by the IDLE of a call it never saw begin.
        val signal = monitor.onCallState(CALL_STATE_IDLE, T0)

        assertEquals(CallEndSignal(endedAt = T0, observedStartAt = null, observed = null), signal)
    }

    @Test
    fun repeatedOrphanIdles_signalOncePerWindow() {
        assertNotNull(monitor.onCallState(CALL_STATE_IDLE, T0))
        assertNull(monitor.onCallState(CALL_STATE_IDLE, T0 + 300))
        assertNull(monitor.onCallState(CALL_STATE_IDLE, T0 + UNOBSERVED_CALL_END_WINDOW_MILLIS - 1))

        assertNotNull(monitor.onCallState(CALL_STATE_IDLE, T0 + UNOBSERVED_CALL_END_WINDOW_MILLIS))
    }

    @Test
    fun orphanIdle_doesNotSwallowACallThatStartsRightAfterIt() {
        monitor.onCallState(CALL_STATE_IDLE, T0)

        // Unlike a real call end, an orphan IDLE starts no cooldown.
        assertNull(monitor.onCallState(CALL_STATE_RINGING, T0 + 500))
        assertTrue(monitor.isCallInProgress)
        assertNotNull(monitor.onCallState(CALL_STATE_IDLE, T0 + 10_000))
    }

    @Test
    fun spuriousCallStartRightAfterACallEnds_isIgnoredAlongWithItsIdle() {
        monitor.onCallState(CALL_STATE_OFFHOOK, T0)
        assertNotNull(monitor.onCallState(CALL_STATE_IDLE, T0 + 30_000))

        // OEM noise: an IDLE->OFFHOOK within the cooldown, then its IDLE a second later.
        assertNull(monitor.onCallState(CALL_STATE_OFFHOOK, T0 + 30_800))
        assertFalse(monitor.isCallInProgress)
        assertNull(monitor.onCallState(CALL_STATE_IDLE, T0 + 31_800))
    }

    @Test
    fun realCallAfterTheCooldown_isTrackedNormally() {
        monitor.onCallState(CALL_STATE_OFFHOOK, T0)
        monitor.onCallState(CALL_STATE_IDLE, T0 + 30_000)

        assertNull(monitor.onCallState(CALL_STATE_RINGING, T0 + 40_000))
        val signal = monitor.onCallState(CALL_STATE_IDLE, T0 + 55_000)

        assertEquals(T0 + 40_000, signal?.observedStartAt)
        assertEquals(CallOutcome.MISSED, signal?.observed?.outcome)
    }
}
