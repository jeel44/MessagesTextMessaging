package text.message.sms.messaging.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.SyncMessages
import javax.inject.Inject

/**
 * Entry point for inbound MMS. The WAP push carries only a notification; the message body and
 * its parts have to be downloaded before they can be read back out of the provider.
 */
@AndroidEntryPoint
class MmsWapPushReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncMessages: SyncMessages

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                // Downloading the PDU and decoding its parts arrives with the receive pipeline.
                syncMessages()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
