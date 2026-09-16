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
import text.message.sms.messaging.domain.usecase.MarkSendFailed
import text.message.sms.messaging.domain.usecase.MarkSent
import javax.inject.Inject

/** Receives the result broadcast the radio fires once it has accepted (or rejected) a part. */
@AndroidEntryPoint
class MessageSentReceiver : BroadcastReceiver() {

    @Inject
    lateinit var markSent: MarkSent

    @Inject
    lateinit var markSendFailed: MarkSendFailed

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(TelephonyMessageTransmitter.EXTRA_MESSAGE_ID, 0L)
        if (messageId == 0L) return

        val succeeded = resultCode == Activity.RESULT_OK
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                if (succeeded) markSent(messageId) else markSendFailed(messageId, resultCode)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
