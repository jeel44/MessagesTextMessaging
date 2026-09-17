package text.message.sms.messaging.data.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.RearmScheduledMessageAlarms
import text.message.sms.messaging.domain.usecase.SyncContacts
import javax.inject.Inject

/**
 * WorkManager's own persisted jobs already reschedule themselves across a reboot -- that is one
 * of the reasons `TelephonyMessageTransmitter.schedule` uses WorkManager rather than
 * `AlarmManager` in the first place, see the note on `ScheduledSendWorker`. The alarm re-arm
 * here exists as a consistency check on top of that, not the primary mechanism: it re-enqueues
 * any scheduled send whose WorkManager job did not, for whatever reason, survive.
 *
 * The contacts re-sync alongside it is here for a different reason: a synced account (Google,
 * a work profile) can add, edit or remove contacts while the device was off, and
 * [text.message.sms.messaging.data.local.provider.ContactChangeObserver] only sees changes
 * that happen while the process is alive to observe them.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rearmScheduledMessageAlarms: RearmScheduledMessageAlarms

    @Inject
    lateinit var syncContacts: SyncContacts

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                rearmScheduledMessageAlarms()
                if (hasContactsPermission) syncContacts()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
