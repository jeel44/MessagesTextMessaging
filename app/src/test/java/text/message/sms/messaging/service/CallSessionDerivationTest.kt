package text.message.sms.messaging.service

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession

private const val ENDED_AT = 1_700_000_600_000L

/**
 * [deriveCallSession]: turning the call log's most recent row into the [CallSession] the call-end
 * screen shows -- direction, outcome, number and duration -- and refusing a row that belongs to an
 * earlier call. The `CallLog.Calls.*_TYPE` constants are compile-time ints, so this runs on the
 * plain JVM with no Android runtime.
 */
class CallSessionDerivationTest {

    /** A call this process watched from its first broadcast, [startedAgoMillis] before the end. */
    private fun observedSignal(startedAgoMillis: Long) = CallEndSignal(
        endedAt = ENDED_AT,
        observedStartAt = ENDED_AT - startedAgoMillis,
        observed = null,
    )

    /** A call this process saw nothing of but its final IDLE (killed mid-call). */
    private val unobservedSignal = CallEndSignal(endedAt = ENDED_AT, observedStartAt = null, observed = null)

    private fun entry(
        type: Int,
        startedAgoMillis: Long,
        durationSeconds: Long,
        number: String? = "+15551234567",
    ) = CallLogEntry(
        number = number,
        type = type,
        dateMillis = ENDED_AT - startedAgoMillis,
        durationSeconds = durationSeconds,
    )

    @Test
    fun answeredIncomingCall_takesDirectionDurationAndNumberFromTheRow() {
        // Rang for 8s, then 95s of talk.
        val session = deriveCallSession(
            entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 103_000, durationSeconds = 95),
            observedSignal(startedAgoMillis = 102_500),
        )

        assertEquals(
            CallSession(
                phoneNumber = "+15551234567",
                direction = CallDirection.INCOMING,
                // When it was answered, not when it started ringing.
                startedAt = ENDED_AT - 95_000,
                endedAt = ENDED_AT,
                durationMillis = 95_000,
                outcome = CallOutcome.ANSWERED,
            ),
            session,
        )
    }

    @Test
    fun outgoingCall_startsAtDialTimeAndReportsTalkTimeOnly() {
        // Dialled 70s ago, the other side picked up after 10s.
        val session = deriveCallSession(
            entry(CallLog.Calls.OUTGOING_TYPE, startedAgoMillis = 70_000, durationSeconds = 60),
            observedSignal(startedAgoMillis = 69_800),
        )

        assertEquals(
            CallSession(
                phoneNumber = "+15551234567",
                direction = CallDirection.OUTGOING,
                startedAt = ENDED_AT - 70_000,
                endedAt = ENDED_AT,
                durationMillis = 60_000,
                outcome = CallOutcome.ANSWERED,
            ),
            session,
        )
    }

    @Test
    fun outgoingCallNobodyAnswered_hasZeroDuration() {
        val session = deriveCallSession(
            entry(CallLog.Calls.OUTGOING_TYPE, startedAgoMillis = 25_000, durationSeconds = 0),
            observedSignal(startedAgoMillis = 24_800),
        )

        assertEquals(CallDirection.OUTGOING, session?.direction)
        assertEquals(0L, session?.durationMillis)
    }

    @Test
    fun missedCall_isIncomingMissedWithZeroDuration() {
        val session = deriveCallSession(
            entry(CallLog.Calls.MISSED_TYPE, startedAgoMillis = 20_000, durationSeconds = 0),
            observedSignal(startedAgoMillis = 19_500),
        )

        assertEquals(
            CallSession(
                phoneNumber = "+15551234567",
                direction = CallDirection.INCOMING,
                startedAt = ENDED_AT - 20_000,
                endedAt = ENDED_AT,
                durationMillis = 0,
                outcome = CallOutcome.MISSED,
            ),
            session,
        )
    }

    @Test
    fun rejectedCall_isIncomingRejected() {
        val session = deriveCallSession(
            entry(CallLog.Calls.REJECTED_TYPE, startedAgoMillis = 6_000, durationSeconds = 0),
            observedSignal(startedAgoMillis = 5_500),
        )

        assertEquals(CallDirection.INCOMING, session?.direction)
        assertEquals(CallOutcome.REJECTED, session?.outcome)
        assertEquals(0L, session?.durationMillis)
    }

    @Test
    fun withheldNumber_becomesNull() {
        val empty = deriveCallSession(
            entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 40_000, durationSeconds = 30, number = ""),
            observedSignal(startedAgoMillis = 39_000),
        )
        val missing = deriveCallSession(
            entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 40_000, durationSeconds = 30, number = null),
            observedSignal(startedAgoMillis = 39_000),
        )

        assertNull(empty?.phoneNumber)
        assertNull(missing?.phoneNumber)
        assertEquals(CallOutcome.ANSWERED, empty?.outcome)
    }

    @Test
    fun negativeDurationFromTheProvider_isTreatedAsZero() {
        val session = deriveCallSession(
            entry(CallLog.Calls.OUTGOING_TYPE, startedAgoMillis = 10_000, durationSeconds = -1),
            observedSignal(startedAgoMillis = 9_800),
        )

        assertEquals(0L, session?.durationMillis)
    }

    @Test
    fun noRow_derivesNothing() {
        assertNull(deriveCallSession(null, observedSignal(startedAgoMillis = 30_000)))
        assertNull(deriveCallSession(null, unobservedSignal))
    }

    @Test
    fun rowTypesTheScreenHasNothingToSayAbout_deriveNothing() {
        val signal = observedSignal(startedAgoMillis = 30_000)
        val otherTypes = listOf(
            CallLog.Calls.VOICEMAIL_TYPE,
            CallLog.Calls.BLOCKED_TYPE,
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE,
            99,
        )

        for (type in otherTypes) {
            assertNull(
                "type=$type",
                deriveCallSession(entry(type, startedAgoMillis = 30_500, durationSeconds = 20), signal),
            )
        }
    }

    // --- Is the latest row this call's row, or an earlier call's? ---

    @Test
    fun observedCall_rowOfAnEarlierCall_isRejected() {
        // This call started 60s ago; the latest row is a 30s call that ended 5 minutes ago,
        // i.e. this call's own row hasn't been written yet.
        val stale = entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 330_000, durationSeconds = 30)

        assertNull(deriveCallSession(stale, observedSignal(startedAgoMillis = 60_000)))
    }

    @Test
    fun observedCall_unansweredRowDatedJustBeforeTheFirstBroadcast_isAccepted() {
        // The row is dated at call creation, a little before the RINGING broadcast was seen.
        val row = entry(
            CallLog.Calls.MISSED_TYPE,
            startedAgoMillis = 20_000 + CALL_START_SLACK_MILLIS - 1,
            durationSeconds = 0,
        )

        assertEquals(CallOutcome.MISSED, deriveCallSession(row, observedSignal(startedAgoMillis = 20_000))?.outcome)
    }

    @Test
    fun observedCall_rowEndingBeforeTheSlack_isRejected() {
        val row = entry(
            CallLog.Calls.MISSED_TYPE,
            startedAgoMillis = 20_000 + CALL_START_SLACK_MILLIS + 1,
            durationSeconds = 0,
        )

        assertNull(deriveCallSession(row, observedSignal(startedAgoMillis = 20_000)))
    }

    @Test
    fun observedCall_processRestartedAfterRinging_stillMatchesTheLongCallsRow() {
        // Process was killed while ringing and only saw OFFHOOK (15s after the row's date):
        // the row still ends long after that, so it's this call's.
        val row = entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 615_000, durationSeconds = 600)

        assertEquals(
            CallDirection.INCOMING,
            deriveCallSession(row, observedSignal(startedAgoMillis = 600_000))?.direction,
        )
    }

    @Test
    fun unobservedCall_longCallThatJustEnded_isDerivedFromTheRowAlone() {
        // 45 minutes of talk after 20s of ringing; the process saw none of it.
        val row = entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 2_720_000, durationSeconds = 2_700)

        assertEquals(
            CallSession(
                phoneNumber = "+15551234567",
                direction = CallDirection.INCOMING,
                startedAt = ENDED_AT - 2_700_000,
                endedAt = ENDED_AT,
                durationMillis = 2_700_000,
                outcome = CallOutcome.ANSWERED,
            ),
            deriveCallSession(row, unobservedSignal),
        )
    }

    @Test
    fun unobservedCall_missedCallStillRingingAMinuteAgo_isAccepted() {
        val row = entry(CallLog.Calls.MISSED_TYPE, startedAgoMillis = 60_000, durationSeconds = 0)

        assertEquals(CallOutcome.MISSED, deriveCallSession(row, unobservedSignal)?.outcome)
    }

    @Test
    fun unobservedCall_rowThatEndedLongAgo_isRejected() {
        // A spurious IDLE (boot, SIM change): the latest row is a call from an hour ago.
        val row = entry(CallLog.Calls.OUTGOING_TYPE, startedAgoMillis = 3_660_000, durationSeconds = 60)

        assertNull(deriveCallSession(row, unobservedSignal))
    }

    @Test
    fun unobservedCall_windowBoundary() {
        val justInside = entry(
            CallLog.Calls.MISSED_TYPE,
            startedAgoMillis = UNOBSERVED_CALL_END_WINDOW_MILLIS,
            durationSeconds = 0,
        )
        val justOutside = entry(
            CallLog.Calls.MISSED_TYPE,
            startedAgoMillis = UNOBSERVED_CALL_END_WINDOW_MILLIS + 1,
            durationSeconds = 0,
        )

        assertEquals(CallOutcome.MISSED, deriveCallSession(justInside, unobservedSignal)?.outcome)
        assertNull(deriveCallSession(justOutside, unobservedSignal))
    }
}
