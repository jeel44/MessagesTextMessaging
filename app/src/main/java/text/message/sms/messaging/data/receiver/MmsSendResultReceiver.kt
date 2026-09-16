package text.message.sms.messaging.data.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.TelephonyMessageTransmitter
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.usecase.MarkSendFailed
import text.message.sms.messaging.domain.usecase.MarkSent
import javax.inject.Inject

/** Receives the result of `SmsManager.sendMultimediaMessage`, the MMS equivalent of
 * [MessageSentReceiver]. MMS delivery/read reports are not implemented -- most carriers do not
 * reliably send them, so this app treats "the MMSC accepted it" as the terminal success state,
 * the same simplification most mainstream messaging apps make. */
@AndroidEntryPoint
class MmsSendResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var markSent: MarkSent

    @Inject
    lateinit var markSendFailed: MarkSendFailed

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    lateinit var mmsProviderGateway: MmsProviderGateway

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
                val message = messageRepository.findById(messageId)
                if (succeeded) {
                    markSent(messageId)
                    message?.let { mmsProviderGateway.updateMessageBox(it.providerId, MessageFolder.SENT) }
                } else {
                    markSendFailed(messageId, resultCode)
                    message?.let { mmsProviderGateway.updateMessageBox(it.providerId, MessageFolder.FAILED) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
