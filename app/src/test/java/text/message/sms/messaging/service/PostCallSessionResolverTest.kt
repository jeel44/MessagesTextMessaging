package text.message.sms.messaging.service

import android.provider.CallLog
import android.telecom.DisconnectCause
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession

private const val T = 1_791_287_000_000L
private const val NUMBER = "+917990096382"
private const val WAIT = 1_500L

/**
 * [PostCallSessionResolver]/[matchPostCallRow]: `POST_CALL` arrives before Telecom writes the
 * call's own call-log row, so the latest row is often the previous call's -- which must never be
 * taken for this one. Replays the Android 16 back-to-back sequence (LOCAL, then MISSED, then
 * REMOTE, all from one number) that showed each call with the previous call's row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostCallSessionResolverTest {

    // Call A: outgoing, answered, 7s, hung up locally. POST_CALL at T.
    private val callA = PostCallInfo(NUMBER, DisconnectCause.LOCAL, endedAt = T)
    private val rowA = CallLogEntry(NUMBER, CallLog.Calls.OUTGOING_TYPE, dateMillis = T - 10_000, durationSeconds = 7)

    // Call B: missed, rang from T+15s. POST_CALL at T+40s.
    private val callB = PostCallInfo(NUMBER, DisconnectCause.MISSED, endedAt = T + 40_000)
    private val rowB = CallLogEntry(NUMBER, CallLog.Calls.MISSED_TYPE, dateMillis = T + 15_000, durationSeconds = 0)

    // Call C: incoming, answered, 25s, remote hung up. POST_CALL at T+80s.
    private val callC = PostCallInfo(NUMBER, DisconnectCause.REMOTE, endedAt = T + 80_000)
    private val rowC = CallLogEntry(NUMBER, CallLog.Calls.INCOMING_TYPE, dateMillis = T + 50_000, durationSeconds = 25)

    private fun fallbackFor(call: PostCallInfo, outcome: CallOutcome) = CallSession(
        phoneNumber = call.number,
        direction = CallDirection.INCOMING,
        startedAt = call.endedAt,
        endedAt = call.endedAt,
        durationMillis = 0,
        outcome = outcome,
    )

    private fun resolver(reader: CallLogReader, history: PostCallHistory) =
        PostCallSessionResolver(reader, history, pollTimeoutMillis = WAIT, pollIntervalMillis = 250)

    @Test
    fun backToBackCalls_eachGetTheirOwnRow_evenWhenItIsWrittenLate() = runTest {
        val history = PostCallHistory()

        val a = resolver(FakeCallLogReader(rowsByQuery = listOf(rowA)), history)
            .resolve(callA, fallbackFor(callA, CallOutcome.ANSWERED))
        assertEquals(CallOutcome.ANSWERED, a?.outcome)
        assertEquals(CallDirection.OUTGOING, a?.direction)
        assertEquals(7_000L, a?.durationMillis)

        // The log still shows call A's row for the first two polls.
        val b = resolver(FakeCallLogReader(rowsByQuery = listOf(rowA, rowA, rowB)), history)
            .resolve(callB, fallbackFor(callB, CallOutcome.MISSED))
        assertEquals(CallOutcome.MISSED, b?.outcome)
        assertEquals(0L, b?.durationMillis)

        val c = resolver(FakeCallLogReader(rowsByQuery = listOf(rowB, rowC)), history)
            .resolve(callC, fallbackFor(callC, CallOutcome.ANSWERED))
        assertEquals(CallOutcome.ANSWERED, c?.outcome)
        assertEquals(CallDirection.INCOMING, c?.direction)
        assertEquals(25_000L, c?.durationMillis)
    }

    @Test
    fun rowNeverWritten_usesTheExtras_neverThePreviousCallsRow() = runTest {
        val history = PostCallHistory()
        resolver(FakeCallLogReader(rowsByQuery = listOf(rowA)), history).resolve(callA, null)
        val fallback = fallbackFor(callB, CallOutcome.MISSED)
        val reader = FakeCallLogReader(rowsByQuery = listOf(rowA))

        val session = resolver(reader, history).resolve(callB, fallback)

        assertEquals(fallback, session)
        assertEquals(WAIT, currentTime)
    }

    @Test
    fun callLogNotReadable_usesTheExtras_andStillRecordsTheCallEnd() = runTest {
        val history = PostCallHistory()
        val fallback = fallbackFor(callB, CallOutcome.MISSED)

        val session = resolver(FakeCallLogReader(readable = false), history).resolve(callB, fallback)

        assertEquals(fallback, session)
        assertEquals(callB.endedAt, history.lastCallEndedAt)
    }

    @Test
    fun match_acceptsThisCallsOwnRow() {
        assertNotNull(matchPostCallRow(rowB, callB, previousCallEndedAt = T, lastUsedRowDateMillis = rowA.dateMillis))
    }

    @Test
    fun match_rejectsAnotherCallersRow() {
        val other = rowB.copy(number = "+916351000085")
        assertNull(matchPostCallRow(other, callB, previousCallEndedAt = null, lastUsedRowDateMillis = null))
    }

    @Test
    fun match_rejectsARowWhoseTypeContradictsTheCause() {
        // The reported bug: a MISSED call shown with the previous call's answered row, and back.
        assertNull(matchPostCallRow(rowA, callB, previousCallEndedAt = null, lastUsedRowDateMillis = null))
        assertNull(matchPostCallRow(rowB, callC, previousCallEndedAt = null, lastUsedRowDateMillis = null))
    }

    @Test
    fun match_rejectsARowAlreadyUsedForAnEarlierCall() {
        // Same type as the stale row, so only the history can tell them apart.
        val callA2 = callA.copy(endedAt = T + 40_000)
        assertNull(matchPostCallRow(rowA, callA2, previousCallEndedAt = null, lastUsedRowDateMillis = rowA.dateMillis))
    }

    @Test
    fun match_rejectsARowThatEndedBeforeThePreviousCallDid() {
        val callA2 = callA.copy(endedAt = T + 40_000)
        assertNull(matchPostCallRow(rowA, callA2, previousCallEndedAt = T, lastUsedRowDateMillis = null))
    }

    @Test
    fun match_rejectsARowEndingWellAfterPostCall() {
        val future = rowB.copy(dateMillis = callB.endedAt + 10_000)
        assertNull(matchPostCallRow(future, callB, previousCallEndedAt = null, lastUsedRowDateMillis = null))
    }

    @Test
    fun match_aMissedCallsRowStartsLongBeforeItsEnd_andIsStillAccepted() {
        // DATE is when it started ringing: 45s before POST_CALL.
        val longRing = rowB.copy(dateMillis = callB.endedAt - 45_000)
        assertNotNull(matchPostCallRow(longRing, callB, previousCallEndedAt = T - 60_000, lastUsedRowDateMillis = null))
    }

    @Test
    fun sameCaller_matchesAcrossFormatsAndPrefixes() {
        assertTrue(sameCaller("+917990096382", "+917990096382"))
        assertTrue(sameCaller("+91 79900 96382", "07990096382"))
        assertTrue(sameCaller("7990096382", "+917990096382"))
        assertTrue(sameCaller("(555) 123-4567", "+15551234567"))
    }

    @Test
    fun sameCaller_rejectsDifferentNumbers() {
        assertFalse(sameCaller("+917990096382", "+916351000085"))
        assertFalse(sameCaller("12345", "912345"))
        assertTrue(sameCaller("12345", "12345"))
    }

    @Test
    fun sameCaller_withheldMatchesOnlyWithheld() {
        assertTrue(sameCaller("", null))
        assertTrue(sameCaller(null, null))
        assertTrue(sameCaller("-2", null))
        assertFalse(sameCaller("+917990096382", null))
        assertFalse(sameCaller("", "+917990096382"))
        assertFalse(sameCaller("-1", "+917990096382"))
    }

    @Test
    fun rowTypeFitsCause_followsTheDisconnectCause() {
        assertTrue(rowTypeFitsCause(CallLog.Calls.MISSED_TYPE, DisconnectCause.MISSED))
        assertFalse(rowTypeFitsCause(CallLog.Calls.INCOMING_TYPE, DisconnectCause.MISSED))
        assertTrue(rowTypeFitsCause(CallLog.Calls.INCOMING_TYPE, DisconnectCause.REMOTE))
        assertFalse(rowTypeFitsCause(CallLog.Calls.MISSED_TYPE, DisconnectCause.REMOTE))
        assertTrue(rowTypeFitsCause(CallLog.Calls.REJECTED_TYPE, DisconnectCause.REJECTED))
        assertTrue(rowTypeFitsCause(CallLog.Calls.OUTGOING_TYPE, DisconnectCause.BUSY))
        assertFalse(rowTypeFitsCause(CallLog.Calls.INCOMING_TYPE, DisconnectCause.CANCELED))
        assertTrue(rowTypeFitsCause(CallLog.Calls.MISSED_TYPE, null))
    }
}
