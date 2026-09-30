package text.message.sms.messaging.service

import android.provider.CallLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession

private const val ENDED_AT = 1_700_000_600_000L

/**
 * [CallEndLauncher]'s decisions -- whether a call ending leads to a launch at all, with what, and
 * when -- with the real launch (`CallEndActivity.start` inside the overlay window) replaced by a
 * recording fake. In particular: "draw over other apps" missing means no launch and no crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallEndLauncherTest {

    private val observed = CallSession(
        phoneNumber = null,
        direction = CallDirection.INCOMING,
        startedAt = ENDED_AT - 30_000,
        endedAt = ENDED_AT,
        durationMillis = 30_000,
        outcome = CallOutcome.ANSWERED,
    )
    private val signal = CallEndSignal(endedAt = ENDED_AT, observedStartAt = ENDED_AT - 40_000, observed = observed)
    private val row = CallLogEntry(
        number = "+15551234567",
        type = CallLog.Calls.INCOMING_TYPE,
        dateMillis = ENDED_AT - 40_500,
        durationSeconds = 30,
    )

    private class Launches {
        val sessions = mutableListOf<CallSession>()
        val atMillis = mutableListOf<Long>()
    }

    private fun TestScope.launcher(
        launches: Launches,
        reader: CallLogReader = FakeCallLogReader(rowsByQuery = listOf(row)),
        flagEnabled: Boolean = true,
        overlayGranted: Boolean = true,
        locked: Boolean = false,
        callInProgress: () -> Boolean = { false },
        launchFailure: Exception? = null,
        errors: MutableList<Throwable> = mutableListOf(),
    ) = CallEndLauncher(
        resolver = CallSessionResolver(reader),
        isFlagEnabled = { flagEnabled },
        canDrawOverlays = { overlayGranted },
        isDeviceLocked = { locked },
        isCallInProgress = callInProgress,
        elapsedRealtime = { currentTime },
        launch = { session ->
            launchFailure?.let { throw it }
            launches.sessions += session
            launches.atMillis += currentTime
        },
        log = { _, error -> error?.let { errors += it } },
    )

    @Test
    fun overlayPermissionMissing_doesNotLaunch_doesNotThrow_andNeverTouchesTheCallLog() = runTest {
        val launches = Launches()
        val reader = FakeCallLogReader(rowsByQuery = listOf(row))
        val launcher = launcher(launches, reader = reader, overlayGranted = false)

        assertFalse(launcher.canLaunch())
        launcher.onCallEnded(signal)

        assertTrue(launches.sessions.isEmpty())
        assertEquals(0, reader.queries)
    }

    @Test
    fun overlayPermissionGranted_launchesWithTheCallLogsSession() = runTest {
        val launches = Launches()
        val launcher = launcher(launches)

        assertTrue(launcher.canLaunch())
        launcher.onCallEnded(signal)

        assertEquals(1, launches.sessions.size)
        assertEquals("+15551234567", launches.sessions.single().phoneNumber)
        assertEquals(30_000L, launches.sessions.single().durationMillis)
    }

    @Test
    fun featureFlagOff_doesNotLaunch_evenWithTheOverlayPermission() = runTest {
        val launches = Launches()
        val reader = FakeCallLogReader(rowsByQuery = listOf(row))
        val launcher = launcher(launches, reader = reader, flagEnabled = false)

        assertFalse(launcher.canLaunch())
        launcher.onCallEnded(signal)

        assertTrue(launches.sessions.isEmpty())
        assertEquals(0, reader.queries)
    }

    @Test
    fun unlockedDevice_waitsOutTheDialerBeforeLaunching() = runTest {
        val launches = Launches()

        launcher(launches, locked = false).onCallEnded(signal)

        assertEquals(listOf(LAUNCH_DELAY_UNLOCKED_MILLIS), launches.atMillis)
    }

    @Test
    fun unlockedDevice_timeSpentWaitingOnTheCallLogCountsTowardsTheDelay() = runTest {
        val launches = Launches()
        // Row shows up on the 4th query, 750ms in: only the remaining 250ms is waited.
        val reader = FakeCallLogReader(rowsByQuery = listOf(null, null, null, row))

        launcher(launches, reader = reader, locked = false).onCallEnded(signal)

        assertEquals(listOf(LAUNCH_DELAY_UNLOCKED_MILLIS), launches.atMillis)
    }

    @Test
    fun lockedDevice_launchesImmediately() = runTest {
        val launches = Launches()

        launcher(launches, locked = true).onCallEnded(signal)

        assertEquals(listOf(0L), launches.atMillis)
    }

    @Test
    fun callLogHasNothingUsable_launchesWithWhatWasObserved() = runTest {
        val launches = Launches()

        launcher(launches, reader = FakeCallLogReader(readable = false)).onCallEnded(signal)

        assertEquals(listOf(observed), launches.sessions)
    }

    @Test
    fun nothingObservedAndNothingInTheCallLog_doesNotLaunch() = runTest {
        val launches = Launches()
        val orphanIdle = CallEndSignal(endedAt = ENDED_AT, observedStartAt = null, observed = null)

        launcher(launches, reader = FakeCallLogReader(rowsByQuery = listOf(null))).onCallEnded(orphanIdle)

        assertTrue(launches.sessions.isEmpty())
    }

    @Test
    fun newCallStartedWhileTheLaunchWasPending_doesNotLaunch() = runTest {
        val launches = Launches()
        // A new call's RINGING lands during the unlocked launch delay.
        val launcher = launcher(launches, locked = false, callInProgress = { currentTime >= 500 })

        launcher.onCallEnded(signal)

        assertTrue(launches.sessions.isEmpty())
    }

    @Test
    fun launchThrowing_isLoggedAndSwallowed() = runTest {
        val launches = Launches()
        val errors = mutableListOf<Throwable>()
        val failure = IllegalStateException("BAL blocked")

        launcher(launches, launchFailure = failure, errors = errors).onCallEnded(signal)

        assertTrue(launches.sessions.isEmpty())
        assertEquals(listOf<Throwable>(failure), errors)
    }
}
