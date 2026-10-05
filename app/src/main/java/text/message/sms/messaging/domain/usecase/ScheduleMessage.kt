package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.model.SchedulingRules
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import text.message.sms.messaging.domain.repository.SendEnvironment
import java.time.Clock
import javax.inject.Inject

/** Why [ScheduleMessage]/[EditScheduledMessage] refused. */
enum class ScheduleRejection {
    /** More than one recipient -- v1 schedules one-to-one SMS only. */
    MULTIPLE_RECIPIENTS,

    /** The thread is a group conversation. */
    GROUP_THREAD,

    /** Attachments present -- v1 is text only. */
    ATTACHMENTS,

    EMPTY_TEXT,

    /** In the past, or less than [SchedulingRules.MIN_LEAD_MILLIS] ahead. */
    TOO_SOON,

    /** [SchedulingRules.MAX_PENDING] messages are already waiting. */
    LIMIT_REACHED,

    /** Edit only: the message has already been sent, claimed, cancelled or failed. */
    NOT_PENDING,
}

sealed interface ScheduleResult {
    data class Scheduled(val message: Message) : ScheduleResult
    data class Rejected(val reason: ScheduleRejection) : ScheduleResult
}

/**
 * Persists a text-only, one-to-one message in [MessageFolder.QUEUED] -- the same folder the system
 * SMS provider uses for a message waiting to go out -- then registers it to send at
 * [Params.sendAtMillis]. Nothing is written when a rule in [ScheduleRejection] fails.
 *
 * [Params.subscriptionId] comes from [ResolveSendSubscription] at schedule time. On a single-SIM
 * device that's [SendMessage.DEFAULT_SUBSCRIPTION_ID]; it's pinned here to the one active SIM's
 * real id, so a SIM swapped in before the send time is detected rather than silently used (see
 * [SendScheduledMessage]).
 */
class ScheduleMessage @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val transmitter: MessageTransmitter,
    private val sendEnvironment: SendEnvironment,
    private val clock: Clock,
) : UseCase {

    suspend operator fun invoke(params: Params): ScheduleResult {
        val rejection = when {
            params.addresses.size != 1 -> ScheduleRejection.MULTIPLE_RECIPIENTS
            params.attachmentUris.isNotEmpty() -> ScheduleRejection.ATTACHMENTS
            params.body.isBlank() -> ScheduleRejection.EMPTY_TEXT
            !isFarEnoughAhead(params.sendAtMillis, clock) -> ScheduleRejection.TOO_SOON
            params.threadId != null &&
                conversationRepository.findByThreadId(params.threadId)?.isGroup == true -> ScheduleRejection.GROUP_THREAD
            scheduledMessageRepository.countPending() >= SchedulingRules.MAX_PENDING -> ScheduleRejection.LIMIT_REACHED
            else -> null
        }
        if (rejection != null) return ScheduleResult.Rejected(rejection)

        val threadId = params.threadId ?: conversationRepository.resolveThreadId(params.addresses)
        val message = messageRepository.insertOutgoing(
            threadId = threadId,
            address = params.addresses.single(),
            body = params.body,
            subscriptionId = pinnedSubscriptionId(params.subscriptionId),
            folder = MessageFolder.QUEUED,
        )

        transmitter.schedule(message, params.sendAtMillis)
        Analytics.messageScheduled()
        return ScheduleResult.Scheduled(message)
    }

    private suspend fun pinnedSubscriptionId(subscriptionId: Int): Int {
        if (subscriptionId != SendMessage.DEFAULT_SUBSCRIPTION_ID) return subscriptionId
        return sendEnvironment.activeSubscriptionIds().singleOrNull() ?: subscriptionId
    }

    data class Params(
        val addresses: Set<String>,
        val body: String,
        val sendAtMillis: Long,
        val threadId: Long? = null,
        val subscriptionId: Int = SendMessage.DEFAULT_SUBSCRIPTION_ID,
        val attachmentUris: List<String> = emptyList(),
    )
}

/** Whether [sendAtMillis] is at least [SchedulingRules.MIN_LEAD_MILLIS] after [clock]'s now. */
internal fun isFarEnoughAhead(sendAtMillis: Long, clock: Clock): Boolean =
    sendAtMillis - clock.millis() >= SchedulingRules.MIN_LEAD_MILLIS
