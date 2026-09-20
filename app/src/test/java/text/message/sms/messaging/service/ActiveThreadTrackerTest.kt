package text.message.sms.messaging.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ActiveThreadTracker] is the exact mechanism [DefaultIncomingMessageNotifier.notify] checks
 * to suppress a notification for the thread currently on screen -- `notify` itself can't be
 * unit-tested directly (it touches `Context`/`NotificationManagerCompat`, both real Android
 * framework classes this module's plain-JVM tests have no way to construct or fake without a
 * mocking library), so this pins down the suppression decision it delegates to instead.
 */
class ActiveThreadTrackerTest {

    @Test
    fun isActive_falseBeforeAnyThreadSetActive() {
        val tracker = ActiveThreadTracker()

        assertFalse(tracker.isActive(threadId = 1L))
    }

    @Test
    fun isActive_trueForTheThreadJustSetActive() {
        val tracker = ActiveThreadTracker()

        tracker.setActive(threadId = 1L)

        assertTrue(tracker.isActive(threadId = 1L))
        assertFalse(tracker.isActive(threadId = 2L))
    }

    @Test
    fun clear_removesTheActiveThread() {
        val tracker = ActiveThreadTracker()
        tracker.setActive(threadId = 1L)

        tracker.clear(threadId = 1L)

        assertFalse(tracker.isActive(threadId = 1L))
    }

    @Test
    fun clear_withADifferentThreadId_leavesTheActiveThreadUntouched() {
        val tracker = ActiveThreadTracker()
        tracker.setActive(threadId = 1L)

        // A stale pause callback from a screen instance already superseded by a newer one for
        // the same thread (e.g. a fast back-then-forward navigation) must never clear a
        // different, still-active thread -- see ActiveThreadTracker.clear's doc.
        tracker.clear(threadId = 2L)

        assertTrue(tracker.isActive(threadId = 1L))
    }
}
