package text.message.sms.messaging.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import text.message.sms.messaging.domain.usecase.ScheduledSendTrigger
import text.message.sms.messaging.domain.usecase.SendScheduledMessage

/**
 * Fires a message whose scheduled time has arrived.
 *
 * WorkManager, not `AlarmManager`, drives this: a `OneTimeWorkRequest` survives process death
 * and a reboot on its own (WorkManager persists it to its own database and re-arms itself,
 * `RearmScheduledMessageAlarms`/[rearmScheduledSends][text.message.sms.messaging.domain.usecase.RearmScheduledMessageAlarms]
 * is a consistency check, not the primary mechanism), and tolerates Doze by piggy-backing on the
 * same system job-scheduling WorkManager already uses for everything else. The alternative --
 * `AlarmManager.setExactAndAllowWhileIdle` -- would need `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`,
 * a permission users have to separately grant in system settings on API 31+, for a feature where
 * "at about the requested time" is an acceptable trade for never showing that permission prompt.
 *
 * All the logic is in [SendScheduledMessage], which claims the message before sending it -- so a
 * re-run of this job (after process death, say) finds it already claimed and does nothing. That is
 * also why this never returns `retry()`: a failure is final (FAILED + notification), and a retry
 * could only ever find the message already claimed.
 */
@HiltWorker
class ScheduledSendWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sendScheduledMessage: SendScheduledMessage,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getLong(KEY_MESSAGE_ID, 0L)
        if (messageId == 0L) return Result.failure()
        sendScheduledMessage(messageId, ScheduledSendTrigger.WORKER)
        return Result.success()
    }

    companion object {
        const val KEY_MESSAGE_ID: String = "message_id"
    }
}
