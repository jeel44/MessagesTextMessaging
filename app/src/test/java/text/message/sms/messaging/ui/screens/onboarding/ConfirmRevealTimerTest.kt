package text.message.sms.messaging.ui.screens.onboarding

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ConfirmRevealTimer] on the test scheduler's virtual clock -- no real sleeps. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmRevealTimerTest {

    private fun TestScope.timer(initiallyVisible: Boolean = false) =
        ConfirmRevealTimer(backgroundScope, initiallyVisible = initiallyVisible)

    @Test
    fun hiddenUntilFirstSelection() = runTest {
        val timer = timer()
        advanceTimeBy(10 * CONFIRM_REVEAL_DELAY_MILLIS)
        assertFalse(timer.visible.value)
    }

    @Test
    fun appearsTwoSecondsAfterSelection() = runTest {
        val timer = timer()
        timer.onSelection()

        advanceTimeBy(CONFIRM_REVEAL_DELAY_MILLIS - 1)
        runCurrent()
        assertFalse(timer.visible.value)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(timer.visible.value)
    }

    @Test
    fun laterSelectionRestartsTheDelay() = runTest {
        val timer = timer()
        timer.onSelection()
        advanceTimeBy(1_500)
        timer.onSelection()

        // 2s after the first tap, but only 0.5s after the second.
        advanceTimeBy(500)
        runCurrent()
        assertFalse(timer.visible.value)

        advanceTimeBy(CONFIRM_REVEAL_DELAY_MILLIS - 500 - 1)
        runCurrent()
        assertFalse(timer.visible.value)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(timer.visible.value)
    }

    @Test
    fun staysVisibleAfterAnotherSelection() = runTest {
        val timer = timer()
        timer.onSelection()
        advanceTimeBy(CONFIRM_REVEAL_DELAY_MILLIS)
        runCurrent()
        assertTrue(timer.visible.value)

        timer.onSelection()
        runCurrent()
        assertTrue(timer.visible.value)
        advanceTimeBy(CONFIRM_REVEAL_DELAY_MILLIS)
        assertTrue(timer.visible.value)
    }

    @Test
    fun restoredVisibleStaysVisible() = runTest {
        val timer = timer(initiallyVisible = true)
        assertTrue(timer.visible.value)
        timer.onSelection()
        assertTrue(timer.visible.value)
    }

    @Test
    fun neverFiresOnceItsScopeIsCancelled() = runTest {
        val scope = TestScope(testScheduler)
        val timer = ConfirmRevealTimer(scope)
        timer.onSelection()
        advanceTimeBy(1_000)
        scope.cancel()

        advanceTimeBy(10 * CONFIRM_REVEAL_DELAY_MILLIS)
        runCurrent()
        assertFalse(timer.visible.value)
    }
}
