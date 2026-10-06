package text.message.sms.messaging.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.analytics.CallScreeningResult
import text.message.sms.messaging.analytics.CallScreeningSource
import text.message.sms.messaging.data.local.datastore.CallScreeningState
import text.message.sms.messaging.data.local.datastore.CallScreeningStore

private const val NOW = 1_791_300_000_000L
private const val DAY = 24 * 60 * 60 * 1_000L

/**
 * [shouldShowCallScreeningReask] and [CallScreeningRoleTracker]: the inbox's "caller info after
 * calls is off" banner for a user who had the call-screening role and lost it to another app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallScreeningRoleTrackerTest {

    private val lost = CallScreeningState(grantedOnce = true)

    private fun show(
        state: CallScreeningState = lost,
        callEndEnabled: Boolean = true,
        roleAvailable: Boolean = true,
        roleHeld: Boolean = false,
        isDefaultSmsApp: Boolean = true,
        nowMillis: Long = NOW,
    ) = shouldShowCallScreeningReask(callEndEnabled, roleAvailable, roleHeld, isDefaultSmsApp, state, nowMillis)

    // --- shouldShowCallScreeningReask ---

    @Test
    fun grantedOnceAndNoLongerHeld_shows() {
        assertTrue(show())
    }

    @Test
    fun neverGranted_neverShows() {
        // "Not now" in onboarding: the role was never held.
        assertFalse(show(state = CallScreeningState(grantedOnce = false)))
    }

    @Test
    fun stillHeld_doesNotShow() {
        assertFalse(show(roleHeld = true))
    }

    @Test
    fun callEndFlagOff_doesNotShow() {
        assertFalse(show(callEndEnabled = false))
    }

    @Test
    fun roleUnavailable_doesNotShow() {
        // Below API 29, or a device without the role.
        assertFalse(show(roleAvailable = false))
    }

    @Test
    fun notTheDefaultSmsApp_doesNotShow() {
        assertFalse(show(isDefaultSmsApp = false))
    }

    @Test
    fun dismissedWithinSevenDays_doesNotShow_thenShowsAgain() {
        val dismissed = lost.copy(reaskDismissCount = 1, reaskLastDismissedAtMillis = NOW)
        assertFalse(show(state = dismissed, nowMillis = NOW + REASK_SNOOZE_MILLIS - 1))
        assertTrue(show(state = dismissed, nowMillis = NOW + REASK_SNOOZE_MILLIS))
        assertEquals(7 * DAY, REASK_SNOOZE_MILLIS)
    }

    @Test
    fun threeDismissals_neverShowsAgain() {
        val twice = lost.copy(reaskDismissCount = 2, reaskLastDismissedAtMillis = NOW - 30 * DAY)
        val thrice = lost.copy(reaskDismissCount = 3, reaskLastDismissedAtMillis = NOW - 365 * DAY)
        assertTrue(show(state = twice))
        assertFalse(show(state = thrice))
    }

    // --- CallScreeningRoleTracker ---

    private class FakeStore(var state: CallScreeningState = CallScreeningState()) : CallScreeningStore {
        override suspend fun read() = state
        override suspend fun markRoleSeenHeld() {
            state = state.copy(grantedOnce = true, lossReported = false)
        }
        override suspend fun markLossReported() {
            state = state.copy(lossReported = true)
        }
        override suspend fun recordReaskDismissal(atMillis: Long) {
            state = state.copy(reaskDismissCount = state.reaskDismissCount + 1, reaskLastDismissedAtMillis = atMillis)
        }
    }

    private class Logged {
        var lost = 0
        val results = mutableListOf<Pair<CallScreeningResult, CallScreeningSource>>()
    }

    private fun TestScope.tracker(
        store: FakeStore,
        logged: Logged = Logged(),
        held: () -> Boolean = { false },
        isDefaultSmsApp: Boolean = true,
    ) = CallScreeningRoleTracker(
        store = store,
        isCallEndEnabled = { true },
        isRoleAvailable = { true },
        isRoleHeld = held,
        isDefaultSmsApp = { isDefaultSmsApp },
        nowMillis = { NOW },
        scope = this,
        logLost = { logged.lost++ },
        logResult = { result, source -> logged.results += result to source },
    )

    @Test
    fun roleLost_showsTheBanner_andReportsTheLossOnce() = runTest {
        val store = FakeStore(lost)
        val logged = Logged()
        val tracker = tracker(store, logged)

        tracker.checkOnce()
        advanceUntilIdle()

        assertTrue(tracker.showReaskBanner.value)
        assertEquals(1, logged.lost)
        assertTrue(store.state.lossReported)

        // A later session (new tracker, same storage) shows it again but doesn't re-report.
        val nextSession = tracker(store, logged)
        nextSession.checkOnce()
        advanceUntilIdle()
        assertTrue(nextSession.showReaskBanner.value)
        assertEquals(1, logged.lost)
    }

    @Test
    fun checkOnce_isOncePerSession() = runTest {
        val store = FakeStore(lost)
        var heldChecks = 0
        val tracker = tracker(store, held = { heldChecks++; false })

        tracker.checkOnce()
        tracker.checkOnce()
        advanceUntilIdle()
        tracker.checkOnce()
        advanceUntilIdle()

        assertEquals(1, heldChecks)
    }

    @Test
    fun roleHeldOnInboxOpen_marksGrantedOnce_andClearsAPastLoss() = runTest {
        val store = FakeStore(CallScreeningState(grantedOnce = false, lossReported = true))
        val tracker = tracker(store, held = { true })

        tracker.checkOnce()
        advanceUntilIdle()

        assertFalse(tracker.showReaskBanner.value)
        assertTrue(store.state.grantedOnce)
        assertFalse(store.state.lossReported)
    }

    @Test
    fun lostWhileNotTheDefaultSmsApp_reportsButDoesNotShow() = runTest {
        val logged = Logged()
        val tracker = tracker(FakeStore(lost), logged, isDefaultSmsApp = false)

        tracker.checkOnce()
        advanceUntilIdle()

        assertFalse(tracker.showReaskBanner.value)
        assertEquals(1, logged.lost)
    }

    @Test
    fun close_hidesTheBanner_recordsADismissal_andLogsSkipped() = runTest {
        val store = FakeStore(lost)
        val logged = Logged()
        val tracker = tracker(store, logged)
        tracker.checkOnce()
        advanceUntilIdle()

        tracker.onReaskDismissed()
        advanceUntilIdle()

        assertFalse(tracker.showReaskBanner.value)
        assertEquals(1, store.state.reaskDismissCount)
        assertEquals(NOW, store.state.reaskLastDismissedAtMillis)
        assertEquals(listOf(CallScreeningResult.SKIPPED to CallScreeningSource.BANNER), logged.results)
    }

    @Test
    fun turnOnThenDeclined_countsAsADismissal() = runTest {
        val store = FakeStore(lost)
        val logged = Logged()
        val tracker = tracker(store, logged, held = { false })
        tracker.checkOnce()
        advanceUntilIdle()

        tracker.onRoleRequestResult(CallScreeningSource.BANNER)
        advanceUntilIdle()

        assertFalse(tracker.showReaskBanner.value)
        assertEquals(1, store.state.reaskDismissCount)
        assertEquals(listOf(CallScreeningResult.DECLINED to CallScreeningSource.BANNER), logged.results)
    }

    @Test
    fun turnOnThenGranted_hidesTheBanner_withoutADismissal() = runTest {
        val store = FakeStore(lost.copy(lossReported = true))
        val logged = Logged()
        var held = false
        val tracker = tracker(store, logged, held = { held })
        tracker.checkOnce()
        advanceUntilIdle()

        held = true
        tracker.onRoleRequestResult(CallScreeningSource.BANNER)
        advanceUntilIdle()

        assertFalse(tracker.showReaskBanner.value)
        assertEquals(0, store.state.reaskDismissCount)
        assertFalse(store.state.lossReported)
        assertEquals(listOf(CallScreeningResult.GRANTED to CallScreeningSource.BANNER), logged.results)
    }

    @Test
    fun roleRegainedElsewhere_hidesTheBannerOnResume() = runTest {
        val store = FakeStore(lost)
        var held = false
        val tracker = tracker(store, held = { held })
        tracker.checkOnce()
        advanceUntilIdle()
        assertTrue(tracker.showReaskBanner.value)

        held = true
        tracker.onInboxResumed()
        advanceUntilIdle()

        assertFalse(tracker.showReaskBanner.value)
        assertFalse(store.state.lossReported)
    }

    @Test
    fun resume_neverShowsTheBannerMidSession() = runTest {
        val store = FakeStore(CallScreeningState(grantedOnce = true))
        var held = true
        val tracker = tracker(store, held = { held })
        tracker.checkOnce()
        advanceUntilIdle()

        held = false
        tracker.onInboxResumed()
        advanceUntilIdle()

        assertFalse(tracker.showReaskBanner.value)
    }

    @Test
    fun settingsAndOnboardingResults_logTheirSource() = runTest {
        val logged = Logged()
        val tracker = tracker(FakeStore(), logged, held = { true })

        tracker.onRoleRequestResult(CallScreeningSource.SETTINGS)
        tracker.onRoleRequestResult(CallScreeningSource.ONBOARDING)
        tracker.onOnboardingSkipped()
        advanceUntilIdle()

        assertEquals(
            listOf(
                CallScreeningResult.GRANTED to CallScreeningSource.SETTINGS,
                CallScreeningResult.GRANTED to CallScreeningSource.ONBOARDING,
                CallScreeningResult.SKIPPED to CallScreeningSource.ONBOARDING,
            ),
            logged.results,
        )
    }
}
