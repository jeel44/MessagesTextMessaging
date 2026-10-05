package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.model.ScheduledMessage
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import text.message.sms.messaging.domain.repository.ScheduledSendNotifier
import text.message.sms.messaging.domain.repository.SendEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A [Clock] tests can move. */
internal class MutableClock(var nowMillis: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    override fun millis(): Long = nowMillis
}

/** In-memory messages -- only what the scheduling use cases touch. */
internal class FakeMessageRepository : MessageRepository {
    val messages = linkedMapOf<Long, Message>()
    private var nextId = 1L

    override fun observeThread(threadId: Long): Flow<List<Message>> =
        MutableStateFlow(messages.values.filter { it.threadId == threadId })

    override suspend fun findById(id: Long): Message? = messages[id]

    override suspend fun findByProviderId(providerId: Long, channel: MessageChannel): Message? = notUsed()

    override suspend fun insertOutgoing(
        threadId: Long,
        address: String,
        body: String,
        subscriptionId: Int,
        attachmentUris: List<String>,
        folder: MessageFolder,
    ): Message {
        val message = testMessage(id = nextId++, threadId = threadId, body = body, folder = folder)
            .copy(address = address, subscriptionId = subscriptionId)
        messages[message.id] = message
        return message
    }

    override suspend fun insertIncoming(message: Message, notifyConversation: Boolean): Message? = notUsed()

    override suspend fun setDeliveryState(messageId: Long, state: DeliveryState, errorCode: Int) {
        messages[messageId]?.let { messages[messageId] = it.copy(deliveryState = state, errorCode = errorCode) }
    }

    override suspend fun setRead(threadIds: Collection<Long>, read: Boolean): Unit = notUsed()
    override suspend fun setSeen(threadIds: Collection<Long>): Unit = notUsed()
    override suspend fun countForThread(threadId: Long): Int = messages.values.count { it.threadId == threadId }
    override suspend fun findLatestForThread(threadId: Long): Message? = notUsed()

    override suspend fun delete(messageIds: Collection<Long>) {
        messageIds.forEach { messages.remove(it) }
    }

    override suspend fun deleteOlderThan(timestampMillis: Long): Unit = notUsed()
    override fun search(query: String): Flow<List<Message>> = notUsed()
    override suspend fun hasAnyMessages(): Boolean = messages.isNotEmpty()

    private fun notUsed(): Nothing = throw AssertionError("Not needed by the scheduling tests")
}

/** In-memory schedule rows over [messages], mirroring the Room implementation's rules. */
internal class FakeScheduledMessageRepository(private val messages: FakeMessageRepository) : ScheduledMessageRepository {
    /** messageId -> send time. */
    val rows = linkedMapOf<Long, Long>()
    var claimCalls = 0

    override fun observeAll(): Flow<List<ScheduledMessage>> =
        MutableStateFlow(rows.mapNotNull { (id, at) -> messages.messages[id]?.let { ScheduledMessage(it, at) } })

    override fun observeSendTimes(threadId: Long): Flow<Map<Long, Long>> =
        MutableStateFlow(rows.filterKeys { messages.messages[it]?.threadId == threadId })

    override suspend fun find(messageId: Long): ScheduledMessage? {
        val at = rows[messageId] ?: return null
        return messages.messages[messageId]?.let { ScheduledMessage(it, at) }
    }

    override suspend fun countPending(): Int = rows.keys.count { messages.messages[it]?.folder == MessageFolder.QUEUED }

    override suspend fun claim(messageId: Long, from: Set<MessageFolder>, nowMillis: Long): Message? {
        claimCalls++
        val message = messages.messages[messageId] ?: return null
        if (message.folder !in from) return null
        val claimed = message.copy(
            folder = MessageFolder.OUTBOX,
            deliveryState = DeliveryState.SENDING,
            sentAtMillis = nowMillis,
            receivedAtMillis = nowMillis,
        )
        messages.messages[messageId] = claimed
        return claimed
    }

    override suspend fun markFailed(messageId: Long, errorCode: Int) {
        messages.messages[messageId]?.let {
            messages.messages[messageId] = it.copy(folder = MessageFolder.FAILED, deliveryState = DeliveryState.FAILED, errorCode = errorCode)
        }
    }

    override suspend fun updateBody(messageId: Long, body: String): Boolean {
        val message = messages.messages[messageId] ?: return false
        if (message.folder != MessageFolder.QUEUED) return false
        messages.messages[messageId] = message.copy(body = body)
        return true
    }

    override suspend fun deleteSchedule(messageId: Long) {
        rows.remove(messageId)
    }
}

/** Records every call; [transmit] throws while [failTransmit] is set. */
internal class FakeTransmitter(private val scheduled: FakeScheduledMessageRepository) : MessageTransmitter {
    val transmitted = mutableListOf<Long>()
    val scheduleCalls = mutableListOf<Pair<Long, Long>>()
    val cancelled = mutableListOf<Long>()
    var failTransmit = false

    override suspend fun transmit(message: Message) {
        if (failTransmit) throw IllegalStateException("radio unavailable")
        transmitted += message.id
    }

    override suspend fun cancelPending(messageId: Long) {
        cancelled += messageId
        scheduled.rows.remove(messageId)
    }

    override suspend fun schedule(message: Message, sendAtMillis: Long) {
        scheduleCalls += message.id to sendAtMillis
        scheduled.rows[message.id] = sendAtMillis
    }

    override suspend fun rearmAlarms() = Unit
}

internal class FakeSendEnvironment(
    var isDefault: Boolean = true,
    var subscriptionIds: List<Int> = listOf(SIM_A),
) : SendEnvironment {
    override fun isDefaultSmsApp(): Boolean = isDefault
    override suspend fun activeSubscriptionIds(): List<Int> = subscriptionIds
}

internal class FakeScheduledNotifier : ScheduledSendNotifier {
    val failed = mutableListOf<Long>()
    val missed = mutableListOf<Long>()
    override fun notifyFailed(messageId: Long, threadId: Long) {
        failed += messageId
    }

    override fun notifyMissed(messageId: Long, threadId: Long) {
        missed += messageId
    }
}

internal const val SIM_A = 11
internal const val SIM_B = 22

internal fun testMessage(
    id: Long,
    threadId: Long = 1L,
    body: String = "hello",
    folder: MessageFolder = MessageFolder.QUEUED,
    subscriptionId: Int = SIM_A,
) = Message(
    id = id,
    threadId = threadId,
    providerId = 0L,
    channel = MessageChannel.SMS,
    folder = folder,
    deliveryState = DeliveryState.PENDING,
    address = "+15550100",
    body = body,
    subject = null,
    sentAtMillis = 0L,
    receivedAtMillis = 0L,
    isRead = true,
    isSeen = true,
    subscriptionId = subscriptionId,
    errorCode = 0,
)

/** Every scheduling fake wired together, with "now" at [NOW]. */
internal class SchedulingHarness {
    val clock = MutableClock(NOW)
    val messages = FakeMessageRepository()
    val scheduled = FakeScheduledMessageRepository(messages)
    val transmitter = FakeTransmitter(scheduled)
    val environment = FakeSendEnvironment()
    val notifier = FakeScheduledNotifier()

    fun sendScheduled() = SendScheduledMessage(scheduled, transmitter, environment, notifier, clock)

    /** A message already scheduled for [sendAt]. */
    fun seedScheduled(
        id: Long,
        sendAt: Long,
        folder: MessageFolder = MessageFolder.QUEUED,
        subscriptionId: Int = SIM_A,
    ): Message {
        val message = testMessage(id = id, folder = folder, subscriptionId = subscriptionId)
        messages.messages[id] = message
        scheduled.rows[id] = sendAt
        return message
    }

    companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
