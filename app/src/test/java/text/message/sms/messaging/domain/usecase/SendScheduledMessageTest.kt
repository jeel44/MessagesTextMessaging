package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.HOUR
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.MINUTE
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.NOW

class SendScheduledMessageTest {

    private val harness = SchedulingHarness()
    private val send = harness.sendScheduled()

    private fun folderOf(id: Long) = harness.messages.messages.getValue(id).folder

    @Test
    fun `on time - claimed, then transmitted once`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW)

        assertEquals(ScheduledSendOutcome.TRANSMITTED, send(1L, ScheduledSendTrigger.WORKER))

        assertEquals(listOf(1L), harness.transmitter.transmitted)
        assertEquals(MessageFolder.OUTBOX, folderOf(1L))
        assertEquals("send time is the real one", NOW, harness.messages.messages.getValue(1L).receivedAtMillis)
        assertTrue("row stays until MarkSent", 1L in harness.scheduled.rows)
    }

    @Test
    fun `a second run of the same job does nothing - claimed before transmit`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW)

        send(1L, ScheduledSendTrigger.WORKER)
        val second = send(1L, ScheduledSendTrigger.WORKER)

        assertEquals(ScheduledSendOutcome.ALREADY_HANDLED, second)
        assertEquals(listOf(1L), harness.transmitter.transmitted)
    }

    @Test
    fun `a run after process death mid-send finds it claimed and does not send`() = runTest {
        // Claimed by a run that died before transmit returned.
        harness.seedScheduled(id = 1L, sendAt = NOW, folder = MessageFolder.OUTBOX)

        assertEquals(ScheduledSendOutcome.ALREADY_HANDLED, send(1L, ScheduledSendTrigger.WORKER))
        assertTrue(harness.transmitter.transmitted.isEmpty())
    }

    @Test
    fun `more than an hour late - missed, FAILED, notified, never sent`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW - HOUR - 1)

        assertEquals(ScheduledSendOutcome.MISSED, send(1L, ScheduledSendTrigger.WORKER))

        assertTrue(harness.transmitter.transmitted.isEmpty())
        assertEquals(MessageFolder.FAILED, folderOf(1L))
        assertEquals(listOf(1L), harness.notifier.missed)
        assertTrue(harness.notifier.failed.isEmpty())
    }

    @Test
    fun `exactly an hour late still sends`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW - HOUR)

        assertEquals(ScheduledSendOutcome.TRANSMITTED, send(1L, ScheduledSendTrigger.WORKER))
    }

    @Test
    fun `its SIM is gone - FAILED and notified, never sent on another SIM`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW, subscriptionId = SIM_A)
        harness.environment.subscriptionIds = listOf(SIM_B)

        assertEquals(ScheduledSendOutcome.FAILED, send(1L, ScheduledSendTrigger.WORKER))

        assertTrue(harness.transmitter.transmitted.isEmpty())
        assertEquals(MessageFolder.FAILED, folderOf(1L))
        assertEquals(listOf(1L), harness.notifier.failed)
    }

    @Test
    fun `not the default SMS app - FAILED and notified`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW)
        harness.environment.isDefault = false

        assertEquals(ScheduledSendOutcome.FAILED, send(1L, ScheduledSendTrigger.WORKER))
        assertEquals(listOf(1L), harness.notifier.failed)
    }

    @Test
    fun `transmitter throws - FAILED and notified, not retried`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW)
        harness.transmitter.failTransmit = true

        assertEquals(ScheduledSendOutcome.FAILED, send(1L, ScheduledSendTrigger.WORKER))
        harness.transmitter.failTransmit = false
        assertEquals(ScheduledSendOutcome.ALREADY_HANDLED, send(1L, ScheduledSendTrigger.WORKER))

        assertEquals(MessageFolder.FAILED, folderOf(1L))
        assertEquals(listOf(1L), harness.notifier.failed)
        assertTrue(harness.transmitter.transmitted.isEmpty())
    }

    @Test
    fun `the job never re-sends a failed message, Send now does`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW - 2 * HOUR, folder = MessageFolder.FAILED)

        assertEquals(ScheduledSendOutcome.ALREADY_HANDLED, send(1L, ScheduledSendTrigger.WORKER))
        assertEquals(ScheduledSendOutcome.TRANSMITTED, send(1L, ScheduledSendTrigger.SEND_NOW))
        assertEquals(listOf(1L), harness.transmitter.transmitted)
    }

    @Test
    fun `Send now before the time sends it immediately, and the later job does nothing`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW + HOUR)

        assertEquals(ScheduledSendOutcome.TRANSMITTED, send(1L, ScheduledSendTrigger.SEND_NOW))
        harness.clock.nowMillis = NOW + HOUR
        assertEquals(ScheduledSendOutcome.ALREADY_HANDLED, send(1L, ScheduledSendTrigger.WORKER))
        assertEquals(listOf(1L), harness.transmitter.transmitted)
    }

    @Test
    fun `a cancelled message is not sent`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW + MINUTE)
        CancelQueuedMessage(harness.messages, harness.transmitter)(1L)

        assertEquals(ScheduledSendOutcome.ALREADY_HANDLED, send(1L, ScheduledSendTrigger.WORKER))
        assertTrue(harness.transmitter.transmitted.isEmpty())
    }

    @Test
    fun `cancel drops the job, the row and the message`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW + HOUR)

        CancelQueuedMessage(harness.messages, harness.transmitter)(1L)

        assertEquals(listOf(1L), harness.transmitter.cancelled)
        assertFalse(1L in harness.scheduled.rows)
        assertNull(harness.messages.messages[1L])
    }

    @Test
    fun `sent broadcast drops the schedule row`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW)
        send(1L, ScheduledSendTrigger.WORKER)

        MarkSent(harness.messages, harness.scheduled)(1L)

        assertFalse(1L in harness.scheduled.rows)
    }

    @Test
    fun `failed broadcast marks FAILED and notifies once per message, not per part`() = runTest {
        harness.seedScheduled(id = 1L, sendAt = NOW)
        send(1L, ScheduledSendTrigger.WORKER)
        val markSendFailed = MarkSendFailed(harness.messages, harness.scheduled, harness.notifier)

        markSendFailed(1L, errorCode = 4)
        markSendFailed(1L, errorCode = 4)

        assertEquals(MessageFolder.FAILED, folderOf(1L))
        assertEquals(listOf(1L), harness.notifier.failed)
        assertTrue("row kept so the user can Send now / Cancel", 1L in harness.scheduled.rows)
    }

    @Test
    fun `failed broadcast for an ordinary message only updates its state`() = runTest {
        harness.messages.messages[5L] = testMessage(id = 5L, folder = MessageFolder.OUTBOX)

        MarkSendFailed(harness.messages, harness.scheduled, harness.notifier)(5L, errorCode = 1)

        assertEquals(MessageFolder.OUTBOX, folderOf(5L))
        assertTrue(harness.notifier.failed.isEmpty())
    }
}
