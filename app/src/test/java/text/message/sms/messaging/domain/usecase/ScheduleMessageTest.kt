package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.model.SchedulingRules
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.HOUR
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.MINUTE
import text.message.sms.messaging.domain.usecase.SchedulingHarness.Companion.NOW

class ScheduleMessageTest {

    private val harness = SchedulingHarness()
    private val conversations = FakeConversationRepository(
        seed = listOf(
            testConversation(threadId = 1L, recipientCount = 1),
            testConversation(threadId = 2L, recipientCount = 3),
        ),
    )
    private val scheduleMessage = ScheduleMessage(
        conversations,
        harness.messages,
        harness.scheduled,
        harness.transmitter,
        harness.environment,
        harness.clock,
    )

    private fun params(
        sendAt: Long = NOW + HOUR,
        threadId: Long = 1L,
        addresses: Set<String> = setOf("+15550100"),
        attachments: List<String> = emptyList(),
        subscriptionId: Int = SIM_A,
    ) = ScheduleMessage.Params(
        addresses = addresses,
        body = "see you at 9",
        sendAtMillis = sendAt,
        threadId = threadId,
        subscriptionId = subscriptionId,
        attachmentUris = attachments,
    )

    private fun assertRejected(expected: ScheduleRejection, result: ScheduleResult) {
        assertEquals(ScheduleResult.Rejected(expected), result)
        assertTrue("nothing may be written on a rejection", harness.messages.messages.isEmpty())
        assertTrue(harness.transmitter.scheduleCalls.isEmpty())
    }

    @Test
    fun `schedules a queued message and registers it at the requested time`() = runTest {
        val result = scheduleMessage(params(sendAt = NOW + HOUR))

        val message = (result as ScheduleResult.Scheduled).message
        assertEquals(MessageFolder.QUEUED, harness.messages.messages.getValue(message.id).folder)
        assertEquals(listOf(message.id to NOW + HOUR), harness.transmitter.scheduleCalls)
    }

    @Test
    fun `a past time is rejected`() = runTest {
        assertRejected(ScheduleRejection.TOO_SOON, scheduleMessage(params(sendAt = NOW - MINUTE)))
    }

    @Test
    fun `less than one minute ahead is rejected, exactly one minute is accepted`() = runTest {
        assertRejected(ScheduleRejection.TOO_SOON, scheduleMessage(params(sendAt = NOW + MINUTE - 1)))
        assertTrue(scheduleMessage(params(sendAt = NOW + MINUTE)) is ScheduleResult.Scheduled)
    }

    @Test
    fun `the 51st pending message is rejected`() = runTest {
        repeat(SchedulingRules.MAX_PENDING) { harness.seedScheduled(id = 1_000L + it, sendAt = NOW + HOUR) }
        val before = harness.messages.messages.size

        val result = scheduleMessage(params())

        assertEquals(ScheduleResult.Rejected(ScheduleRejection.LIMIT_REACHED), result)
        assertEquals(before, harness.messages.messages.size)
    }

    @Test
    fun `failed or missed messages don't count toward the limit`() = runTest {
        repeat(SchedulingRules.MAX_PENDING) {
            harness.seedScheduled(id = 1_000L + it, sendAt = NOW - HOUR, folder = MessageFolder.FAILED)
        }
        assertTrue(scheduleMessage(params()) is ScheduleResult.Scheduled)
    }

    @Test
    fun `a group thread is rejected`() = runTest {
        assertRejected(ScheduleRejection.GROUP_THREAD, scheduleMessage(params(threadId = 2L)))
    }

    @Test
    fun `several addresses are rejected rather than using the first`() = runTest {
        assertRejected(
            ScheduleRejection.MULTIPLE_RECIPIENTS,
            scheduleMessage(params(addresses = setOf("+15550100", "+15550101"))),
        )
    }

    @Test
    fun `attachments are rejected`() = runTest {
        assertRejected(ScheduleRejection.ATTACHMENTS, scheduleMessage(params(attachments = listOf("content://x/1"))))
    }

    @Test
    fun `on a single-SIM device the default subscription is pinned to that SIM`() = runTest {
        harness.environment.subscriptionIds = listOf(SIM_B)

        val result = scheduleMessage(params(subscriptionId = SendMessage.DEFAULT_SUBSCRIPTION_ID))

        assertEquals(SIM_B, (result as ScheduleResult.Scheduled).message.subscriptionId)
    }
}
