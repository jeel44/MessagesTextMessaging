package text.message.sms.messaging.service

import android.provider.CallLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession

private const val ENDED_AT = 1_700_000_600_000L

/** A scripted call log: [rowsByQuery] is what each successive query returns, the last one
 * repeating forever. */
internal class FakeCallLogReader(
    private val readable: Boolean = true,
    private val rowsByQuery: List<CallLogEntry?> = listOf(null),
) : CallLogReader {
    var queries = 0
        private set

    override fun canRead(): Boolean = readable

    override suspend fun latestEntry(): CallLogEntry? =
        rowsByQuery[queries.coerceAtMost(rowsByQuery.lastIndex)].also { queries++ }
}

/**
 * [CallSessionResolver.resolve]: waiting for the call log to catch up with the call that just
 * ended, and what it settles for when it doesn't. `delay` runs on [runTest]'s virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallSessionResolverTest {

    private val observed = CallSession(
        phoneNumber = null,
        direction = CallDirection.OUTGOING,
        startedAt = ENDED_AT - 60_000,
        endedAt = ENDED_AT,
        durationMillis = 60_000,
        outcome = CallOutcome.ANSWERED,
    )
    private val observedSignal = CallEndSignal(
        endedAt = ENDED_AT,
        observedStartAt = ENDED_AT - 60_000,
        observed = observed,
    )
    private val unobservedSignal = CallEndSignal(endedAt = ENDED_AT, observedStartAt = null, observed = null)

    private val thisCallsRow = CallLogEntry(
        number = "+15551234567",
        type = CallLog.Calls.OUTGOING_TYPE,
        dateMillis = ENDED_AT - 60_200,
        durationSeconds = 50,
    )
    private val earlierCallsRow = CallLogEntry(
        number = "+15559999999",
        type = CallLog.Calls.INCOMING_TYPE,
        dateMillis = ENDED_AT - 3_600_000,
        durationSeconds = 10,
    )

    @Test
    fun rowAlreadyWritten_resolvesOnTheFirstQueryWithoutWaiting() = runTest {
        val reader = FakeCallLogReader(rowsByQuery = listOf(thisCallsRow))

        val session = CallSessionResolver(reader).resolve(observedSignal)

        assertEquals("+15551234567", session?.phoneNumber)
        assertEquals(50_000L, session?.durationMillis)
        assertEquals(1, reader.queries)
        assertEquals(0L, currentTime)
    }

    @Test
    fun rowWrittenLate_isPickedUpByPollingPastTheEarlierCallsRow() = runTest {
        val reader = FakeCallLogReader(rowsByQuery = listOf(earlierCallsRow, earlierCallsRow, thisCallsRow))

        val session = CallSessionResolver(reader, pollIntervalMillis = 250, pollTimeoutMillis = 3_000)
            .resolve(observedSignal)

        assertEquals("+15551234567", session?.phoneNumber)
        assertEquals(CallDirection.OUTGOING, session?.direction)
        assertEquals(3, reader.queries)
        assertEquals(500L, currentTime)
    }

    @Test
    fun rowNeverWritten_fallsBackToWhatWasObserved_afterTheTimeout() = runTest {
        val reader = FakeCallLogReader(rowsByQuery = listOf(earlierCallsRow))

        val session = CallSessionResolver(reader, pollIntervalMillis = 250, pollTimeoutMillis = 3_000)
            .resolve(observedSignal)

        assertEquals(observed, session)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun callLogNotReadable_fallsBackImmediatelyWithoutQuerying() = runTest {
        val reader = FakeCallLogReader(readable = false, rowsByQuery = listOf(thisCallsRow))

        val session = CallSessionResolver(reader).resolve(observedSignal)

        assertEquals(observed, session)
        assertEquals(0, reader.queries)
        assertEquals(0L, currentTime)
    }

    @Test
    fun unobservedCall_withAFreshRow_resolvesFromTheRowAlone() = runTest {
        val reader = FakeCallLogReader(rowsByQuery = listOf(null, thisCallsRow))

        val session = CallSessionResolver(reader).resolve(unobservedSignal)

        assertEquals("+15551234567", session?.phoneNumber)
        assertEquals(CallDirection.OUTGOING, session?.direction)
    }

    @Test
    fun unobservedCall_withNoFreshRow_resolvesToNothing() = runTest {
        // A spurious IDLE: nothing observed, and the log's latest row is an old call.
        val reader = FakeCallLogReader(rowsByQuery = listOf(earlierCallsRow))

        assertNull(CallSessionResolver(reader).resolve(unobservedSignal))
    }

    @Test
    fun unobservedCall_withTheCallLogNotReadable_resolvesToNothing() = runTest {
        val reader = FakeCallLogReader(readable = false)

        assertNull(CallSessionResolver(reader).resolve(unobservedSignal))
        assertEquals(0, reader.queries)
    }
}
