package text.message.sms.messaging.data.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.MmsTransportGateway
import text.message.sms.messaging.data.local.provider.mms.MmsPduDecoder
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.usecase.ReceiveMms
import javax.inject.Inject

/**
 * Completes the receive started by [MmsWapPushReceiver]: decodes the downloaded M-Retrieve.conf,
 * mirrors it into the system provider, and hands the resulting Uri to [ReceiveMms] -- the same
 * use case path an already-materialised provider row would go through.
 *
 * Thread resolution happens here rather than at the notification stage: `Content-Location`'s
 * From address is frequently masked or absent until the real message is retrieved, while the
 * downloaded PDU always carries the full sender/recipient set.
 */
@AndroidEntryPoint
class MmsDownloadResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var mmsTransportGateway: MmsTransportGateway

    @Inject
    lateinit var mmsProviderGateway: MmsProviderGateway

    @Inject
    lateinit var conversationRepository: ConversationRepository

    @Inject
    lateinit var receiveMms: ReceiveMms

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID) ?: return
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
        val succeeded = resultCode == Activity.RESULT_OK

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                if (succeeded) handleDownloaded(transactionId, subscriptionId)
            } finally {
                mmsTransportGateway.cleanup(mmsTransportGateway.downloadedFile(transactionId))
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleDownloaded(transactionId: String, subscriptionId: Int) {
        val file = mmsTransportGateway.downloadedFile(transactionId)
        if (!file.exists()) return

        val retrieved = MmsPduDecoder.decodeRetrieveConf(file.readBytes()) ?: return
        val participants = (listOfNotNull(retrieved.from) + retrieved.to + retrieved.cc).toSet()
        if (participants.isEmpty()) return

        val threadId = conversationRepository.resolveThreadId(participants)
        val providerUri = mmsProviderGateway.insertRetrieved(retrieved, threadId, subscriptionId)
        if (providerUri.toString().isNotEmpty()) receiveMms(providerUri.toString())
    }

    companion object {
        const val EXTRA_TRANSACTION_ID: String = "text.message.sms.messaging.extra.TRANSACTION_ID"
        const val EXTRA_SUBSCRIPTION_ID: String = "text.message.sms.messaging.extra.SUBSCRIPTION_ID"
    }
}
