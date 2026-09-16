package text.message.sms.messaging.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.RearmScheduledMessageAlarms
import javax.inject.Inject

/**
 * WorkManager's own persisted jobs already reschedule themselves across a reboot -- that is one
 * of the reasons `TelephonyMessageTransmitter.schedule` uses WorkManager rather than
 * `AlarmManager` in the first place, see the note on `ScheduledSendWorker`. This receiver exists
 * as a consistency check on top of that, not the primary mechanism: it re-enqueues any scheduled
 * send whose WorkManager job did not, for whatever reason, survive.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rearmScheduledMessageAlarms: RearmScheduledMessageAlarms

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                rearmScheduledMessageAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
