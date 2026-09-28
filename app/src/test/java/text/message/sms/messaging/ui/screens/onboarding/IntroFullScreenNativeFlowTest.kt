package text.message.sms.messaging.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeLoad.FAILED
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeLoad.LOADED
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeLoad.LOADING

/** [IntroFullScreenNativeFlow] on the test scheduler's virtual clock, with a String standing in
 * for the ad -- no real sleeps, no ads SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
class IntroFullScreenNativeFlowTest {

    private val maxWait = 3_000L
    private val load = MutableStateFlow(LOADING)
    private var ad: String? = null
    private var overlaysUp = 0
    private var overlaysDown = 0
    private val logs = mutableListOf<String>()

    private fun TestScope.flow(savedState: SavedStateHandle = SavedStateHandle()) =
        IntroFullScreenNativeFlow(
            scope = backgroundScope,
            savedStateHandle = savedState,
            load = load,
            loadedAd = { ad },
            consent = { AdConsentState.Allowed },
            enabled = true,
            maxWaitMillis = maxWait,
            onOverlayUp = { overlaysUp++ },
            onOverlayDown = { overlaysDown++ },
            log = { logs += it },
        )

    private fun finishLoad() {
        ad = "the ad"
        load.value = LOADED
    }

    @Test
    fun alreadyLoaded_showsAtOnce() = runTest {
        finishLoad()
        val flow = flow()

        assertTrue(flow.onFirstSlideNext())
        assertEquals(FullScreenNativeOverlay.Showing("the ad"), flow.overlay.value)
        assertFalse(logs.any { "waiting" in it })
    }

    @Test
    fun stillLoading_waitsWithShimmer_thenShowsWhenItLoadsWithinTheWait() = runTest {
        val flow = flow()

        assertTrue(flow.onFirstSlideNext())
        assertEquals(FullScreenNativeOverlay.Waiting, flow.overlay.value)
        assertTrue("full-screen native waiting (still loading)" in logs)

        advanceTimeBy(400)
        finishLoad()
        runCurrent()

        assertEquals(FullScreenNativeOverlay.Showing("the ad"), flow.overlay.value)
        assertFalse(flow.advancePending.value)
        assertTrue("full-screen native loaded during wait" in logs)

        advanceTimeBy(10 * maxWait)
        assertEquals("the wait is over once it loaded", FullScreenNativeOverlay.Showing("the ad"), flow.overlay.value)
        assertEquals(1, overlaysUp)
    }

    @Test
    fun loadsAfterTheWait_skipsToSlide2_andNeverShows() = runTest {
        val flow = flow()
        flow.onFirstSlideNext()

        advanceTimeBy(maxWait - 1)
        runCurrent()
        assertFalse(flow.advancePending.value)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(flow.advancePending.value)
        assertTrue("full-screen native wait timed out, going to slide 2" in logs)

        flow.onAdvanced()
        assertNull(flow.overlay.value)
        assertEquals(1, overlaysDown)

        finishLoad()
        runCurrent()
        assertNull("a late load mustn't bring the overlay back", flow.overlay.value)
        assertFalse("never shown again this visit", flow.onFirstSlideNext())
        assertNull(flow.overlay.value)
    }

    @Test
    fun failsDuringTheWait_skipsToSlide2Early() = runTest {
        val flow = flow()
        flow.onFirstSlideNext()

        advanceTimeBy(500)
        load.value = FAILED
        runCurrent()

        assertTrue(flow.advancePending.value)
        assertEquals("shimmer stays until the pager has moved", FullScreenNativeOverlay.Waiting, flow.overlay.value)
        assertTrue("full-screen native failed during wait, going to slide 2" in logs)

        flow.onAdvanced()
        assertNull(flow.overlay.value)
        assertFalse(flow.onFirstSlideNext())
    }

    @Test
    fun nextTappedTwiceQuickly_oneOverlayOnly() = runTest {
        val flow = flow()

        assertTrue(flow.onFirstSlideNext())
        advanceTimeBy(50)
        assertTrue(flow.onFirstSlideNext())

        assertEquals(1, overlaysUp)
        assertEquals(1, logs.count { it == "full-screen native waiting (still loading)" })

        finishLoad()
        runCurrent()
        assertEquals(FullScreenNativeOverlay.Showing("the ad"), flow.overlay.value)
        assertEquals(1, logs.count { it == "full-screen native loaded during wait" })
    }

    @Test
    fun closedAfterShowing_advances_andNeverShowsTwice() = runTest {
        finishLoad()
        val flow = flow()
        flow.onFirstSlideNext()

        flow.onClosed()
        assertTrue(flow.advancePending.value)
        flow.onAdvanced()

        assertNull(flow.overlay.value)
        assertEquals(1, overlaysUp)
        assertEquals(1, overlaysDown)
        assertFalse(flow.onFirstSlideNext())
    }

    @Test
    fun closeIgnoredWhileWaiting() = runTest {
        val flow = flow()
        flow.onFirstSlideNext()

        flow.onClosed()
        assertFalse(flow.advancePending.value)
        assertEquals(FullScreenNativeOverlay.Waiting, flow.overlay.value)
    }

    /** The owner (the ViewModel) is cleared mid-wait: nothing fires afterwards. */
    @Test
    fun waitCancelledWithItsScope() = runTest {
        val scope = TestScope(testScheduler)
        val flow = IntroFullScreenNativeFlow(
            scope = scope,
            savedStateHandle = SavedStateHandle(),
            load = load,
            loadedAd = { ad },
            consent = { AdConsentState.Allowed },
            enabled = true,
            maxWaitMillis = maxWait,
        )
        flow.onFirstSlideNext()
        advanceTimeBy(1_000)
        scope.cancel()

        advanceTimeBy(10 * maxWait)
        runCurrent()
        assertFalse(flow.advancePending.value)
    }

    /** A process death mid-wait: the restored flow lands on slide 2 and never shows. */
    @Test
    fun processDeathWhileWaiting_landsOnSlide2_neverShows() = runTest {
        val savedState = SavedStateHandle()
        flow(savedState).onFirstSlideNext()

        finishLoad()
        val restored = flow(savedState)
        assertTrue(restored.advancePending.value)
        assertTrue(restored.decided)
        restored.onAdvanced()
        assertFalse(restored.onFirstSlideNext())
    }
}
