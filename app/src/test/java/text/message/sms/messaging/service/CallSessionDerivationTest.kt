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
 * screen shows -- direction, outcome, number and duration -- and refusing a row too old to be the
 * call that just ended. The `CallLog.Calls.*_TYPE` constants are compile-time ints, so this runs on the
 * plain JVM with no Android runtime.
 */
class CallSessionDerivationTest {

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
            ENDED_AT,
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
            ENDED_AT,
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
            ENDED_AT,
        )

        assertEquals(CallDirection.OUTGOING, session?.direction)
        assertEquals(0L, session?.durationMillis)
    }

    @Test
    fun missedCall_isIncomingMissedWithZeroDuration() {
        val session = deriveCallSession(
            entry(CallLog.Calls.MISSED_TYPE, startedAgoMillis = 20_000, durationSeconds = 0),
            ENDED_AT,
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
            ENDED_AT,
        )

        assertEquals(CallDirection.INCOMING, session?.direction)
        assertEquals(CallOutcome.REJECTED, session?.outcome)
        assertEquals(0L, session?.durationMillis)
    }

    @Test
    fun withheldNumber_becomesNull() {
        val empty = deriveCallSession(
            entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 40_000, durationSeconds = 30, number = ""),
            ENDED_AT,
        )
        val missing = deriveCallSession(
            entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 40_000, durationSeconds = 30, number = null),
            ENDED_AT,
        )

        assertNull(empty?.phoneNumber)
        assertNull(missing?.phoneNumber)
        assertEquals(CallOutcome.ANSWERED, empty?.outcome)
    }

    @Test
    fun negativeDurationFromTheProvider_isTreatedAsZero() {
        val session = deriveCallSession(
            entry(CallLog.Calls.OUTGOING_TYPE, startedAgoMillis = 10_000, durationSeconds = -1),
            ENDED_AT,
        )

        assertEquals(0L, session?.durationMillis)
    }

    @Test
    fun noRow_derivesNothing() {
        assertNull(deriveCallSession(null, ENDED_AT))
    }

    @Test
    fun rowTypesTheScreenHasNothingToSayAbout_deriveNothing() {
        val otherTypes = listOf(
            CallLog.Calls.VOICEMAIL_TYPE,
            CallLog.Calls.BLOCKED_TYPE,
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE,
            99,
        )

        for (type in otherTypes) {
            assertNull(
                "type=$type",
                deriveCallSession(entry(type, startedAgoMillis = 30_500, durationSeconds = 20), ENDED_AT),
            )
        }
    }

    // --- Is the row recent enough to be the call that just ended? ---

    @Test
    fun rowOfACallThatEndedMinutesAgo_isRejected() {
        // The latest row is a 30s call that ended 5 minutes ago.
        val stale = entry(CallLog.Calls.INCOMING_TYPE, startedAgoMillis = 330_000, durationSeconds = 30)

        assertNull(deriveCallSession(stale, ENDED_AT))
    }

    @Test
    fun longCallThatJustEnded_isDerivedFromTheRow() {
        // 45 minutes of talk after 20s of ringing.
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
            deriveCallSession(row, ENDED_AT),
        )
    }

    @Test
    fun missedCallStillRingingAMinuteAgo_isAccepted() {
        val row = entry(CallLog.Calls.MISSED_TYPE, startedAgoMillis = 60_000, durationSeconds = 0)

        assertEquals(CallOutcome.MISSED, deriveCallSession(row, ENDED_AT)?.outcome)
    }

    @Test
    fun rowThatEndedAnHourAgo_isRejected() {
        val row = entry(CallLog.Calls.OUTGOING_TYPE, startedAgoMillis = 3_660_000, durationSeconds = 60)

        assertNull(deriveCallSession(row, ENDED_AT))
    }

    @Test
    fun maxAgeBoundary() {
        val justInside = entry(
            CallLog.Calls.MISSED_TYPE,
            startedAgoMillis = CALL_LOG_ROW_MAX_AGE_MILLIS,
            durationSeconds = 0,
        )
        val justOutside = entry(
            CallLog.Calls.MISSED_TYPE,
            startedAgoMillis = CALL_LOG_ROW_MAX_AGE_MILLIS + 1,
            durationSeconds = 0,
        )

        assertEquals(CallOutcome.MISSED, deriveCallSession(justInside, ENDED_AT)?.outcome)
        assertNull(deriveCallSession(justOutside, ENDED_AT))
    }
}
