package text.message.sms.messaging.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import text.message.sms.messaging.data.local.db.dao.ScheduledMessageDao
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.MessageTransmitter

/**
 * Fires a message whose scheduled time has arrived.
 *
 * WorkManager, not `AlarmManager`, drives this: a `OneTimeWorkRequest` survives process death
 * and a reboot on its own (WorkManager persists it to its own database and re-arms itself,
 * `RearmScheduledMessageAlarms`/[rearmScheduledSends][text.message.sms.messaging.domain.usecase.RearmScheduledMessageAlarms]
 * is a consistency check, not the primary mechanism), tolerates Doze by piggy-backing on the
 * same system job-scheduling WorkManager already uses for everything else, and retries
 * automatically on failure. The alternative -- `AlarmManager.setExactAndAllowWhileIdle` -- would
 * need `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, a permission users have to separately grant in
 * system settings on API 31+, for a feature where "within about a minute of the requested time"
 * is an entirely acceptable trade for never showing that permission prompt.
 */
@HiltWorker
class ScheduledSendWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageRepository: MessageRepository,
    private val transmitter: MessageTransmitter,
    private val scheduledMessageDao: ScheduledMessageDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getLong(KEY_MESSAGE_ID, 0L)
        if (messageId == 0L) return Result.failure()

        val message = messageRepository.findById(messageId)
            ?: return Result.failure() // the message (or its schedule) was cancelled/deleted

        return try {
            transmitter.transmit(message)
            scheduledMessageDao.delete(messageId)
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_MESSAGE_ID: String = "message_id"
        private const val MAX_ATTEMPTS = 3
    }
}
