package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.HOUR
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.NOW

class EditScheduledMessageTest {

    private val harness = SchedulingHarness()
    private val edit = EditScheduledMessage(harness.scheduled, harness.transmitter, harness.clock)

    @Test
    fun `edits text and time in place - same message, same row, job replaced`() = runTest {
        harness.seedScheduled(id = 7L, sendAt = NOW + HOUR)

        val result = edit(7L, "new text", NOW + 3 * HOUR)

        assertTrue(result is ScheduleResult.Scheduled)
        assertEquals(setOf(7L), harness.messages.messages.keys)
        assertEquals("new text", harness.messages.messages.getValue(7L).body)
        assertEquals(NOW + 3 * HOUR, harness.scheduled.rows.getValue(7L))
        assertEquals(listOf(7L to NOW + 3 * HOUR), harness.transmitter.scheduleCalls)
        assertTrue("never cancelled/recreated", harness.transmitter.cancelled.isEmpty())
    }

    @Test
    fun `a message already claimed for sending can't be edited`() = runTest {
        harness.seedScheduled(id = 7L, sendAt = NOW, folder = MessageFolder.OUTBOX)

        assertEquals(ScheduleResult.Rejected(ScheduleRejection.NOT_PENDING), edit(7L, "late edit", NOW + HOUR))
        assertEquals("hello", harness.messages.messages.getValue(7L).body)
        assertTrue(harness.transmitter.scheduleCalls.isEmpty())
    }

    @Test
    fun `an edit to a past time is rejected and changes nothing`() = runTest {
        harness.seedScheduled(id = 7L, sendAt = NOW + HOUR)

        assertEquals(ScheduleResult.Rejected(ScheduleRejection.TOO_SOON), edit(7L, "x", NOW - 1))
        assertEquals(NOW + HOUR, harness.scheduled.rows.getValue(7L))
        assertEquals("hello", harness.messages.messages.getValue(7L).body)
    }
}
