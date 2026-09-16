package text.message.sms.messaging.data.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.provider.TelephonyMessageTransmitter
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.MarkDelivered
import text.message.sms.messaging.domain.usecase.MarkDeliveryFailed
import javax.inject.Inject

/** Receives the carrier delivery report for a sent message, when the carrier sends one. */
@AndroidEntryPoint
class MessageDeliveredReceiver : BroadcastReceiver() {

    @Inject
    lateinit var markDelivered: MarkDelivered

    @Inject
    lateinit var markDeliveryFailed: MarkDeliveryFailed

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(TelephonyMessageTransmitter.EXTRA_MESSAGE_ID, 0L)
        if (messageId == 0L) return

        val delivered = resultCode == Activity.RESULT_OK
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                if (delivered) markDelivered(messageId) else markDeliveryFailed(messageId, resultCode)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
