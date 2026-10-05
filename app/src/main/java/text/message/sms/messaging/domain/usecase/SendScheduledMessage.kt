package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.CancellationException
import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.model.SchedulingRules
import text.message.sms.messaging.domain.repository.MessageTransmitter
import text.message.sms.messaging.domain.repository.ScheduledMessageRepository
import text.message.sms.messaging.domain.repository.ScheduledSendNotifier
import text.message.sms.messaging.domain.repository.SendEnvironment
import java.time.Clock
import javax.inject.Inject

enum class ScheduledSendTrigger {
    /** The message's own WorkManager job -- only ever sends a still-QUEUED message. */
    WORKER,

    /** The user's Send now -- also re-sends one that failed or was missed. */
    SEND_NOW,
}

enum class ScheduledSendOutcome {
    /** Handed to the radio; [MarkSent]/[MarkSendFailed] take it from here. */
    TRANSMITTED,

    /** Nothing to do: already claimed (a re-run after process death), sent, or cancelled. */
    ALREADY_HANDLED,

    /** More than [SchedulingRules.OVERDUE_LIMIT_MILLIS] late: not sent, marked FAILED. */
    MISSED,

    /** Couldn't send (not the default SMS app, its SIM is gone, or the radio call threw):
     * marked FAILED. */
    FAILED,
}

/**
 * Sends one scheduled message, at most once. The message is claimed first -- an atomic
 * QUEUED -> OUTBOX move -- and only the caller that wins the claim goes on, so a job re-run after
 * process death, or a Send now racing the job, can never send it twice.
 *
 * Every way it can't go out ends in [MessageFolder.FAILED] plus a notification, and is never
 * retried automatically -- in particular it never falls back to a SIM other than the one chosen
 * when it was scheduled.
 */
class SendScheduledMessage @Inject constructor(
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val transmitter: MessageTransmitter,
    private val sendEnvironment: SendEnvironment,
    private val notifier: ScheduledSendNotifier,
    private val clock: Clock,
) : UseCase {

    suspend operator fun invoke(messageId: Long, trigger: ScheduledSendTrigger): ScheduledSendOutcome {
        val scheduled = scheduledMessageRepository.find(messageId) ?: return ScheduledSendOutcome.ALREADY_HANDLED
        val claimableFrom = when (trigger) {
            ScheduledSendTrigger.WORKER -> setOf(MessageFolder.QUEUED)
            ScheduledSendTrigger.SEND_NOW -> setOf(MessageFolder.QUEUED, MessageFolder.FAILED)
        }
        val now = clock.millis()
        val message = scheduledMessageRepository.claim(messageId, claimableFrom, now)
            ?: return ScheduledSendOutcome.ALREADY_HANDLED

        if (trigger == ScheduledSendTrigger.WORKER && now - scheduled.sendAtMillis > SchedulingRules.OVERDUE_LIMIT_MILLIS) {
            scheduledMessageRepository.markFailed(messageId)
            notifier.notifyMissed(messageId, message.threadId)
            Analytics.scheduledFailed()
            return ScheduledSendOutcome.MISSED
        }
        if (trigger == ScheduledSendTrigger.SEND_NOW) Analytics.scheduledSendNow()

        if (!sendEnvironment.isDefaultSmsApp() || !isSimStillActive(message)) return fail(message)

        return try {
            transmitter.transmit(message)
            ScheduledSendOutcome.TRANSMITTED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(message)
        }
    }

    /** [SendMessage.DEFAULT_SUBSCRIPTION_ID] means "the platform default" (no SIM was known when
     * it was scheduled) -- the radio itself reports a failure if there's no SIM at all. */
    private suspend fun isSimStillActive(message: Message): Boolean =
        message.subscriptionId == SendMessage.DEFAULT_SUBSCRIPTION_ID ||
            message.subscriptionId in sendEnvironment.activeSubscriptionIds()

    private suspend fun fail(message: Message): ScheduledSendOutcome {
        scheduledMessageRepository.markFailed(message.id)
        notifier.notifyFailed(message.id, message.threadId)
        Analytics.scheduledFailed()
        return ScheduledSendOutcome.FAILED
    }
}
