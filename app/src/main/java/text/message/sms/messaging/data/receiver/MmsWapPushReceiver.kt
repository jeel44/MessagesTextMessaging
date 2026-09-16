package text.message.sms.messaging.data.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.provider.MmsTransportGateway
import text.message.sms.messaging.data.local.provider.mms.MmsPduDecoder
import text.message.sms.messaging.di.ApplicationScope
import javax.inject.Inject

/**
 * Entry point for inbound MMS. A WAP push carries only a notification -- sender, subject, and
 * where to fetch the real message from (`Content-Location`, an MMSC URL) -- so this decodes
 * just that much, then asks [MmsTransportGateway] to download the rest over the carrier
 * connection. [MmsDownloadResultReceiver] picks up once that download completes.
 */
@AndroidEntryPoint
class MmsWapPushReceiver : BroadcastReceiver() {

    @Inject
    lateinit var mmsTransportGateway: MmsTransportGateway

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val data = intent.getByteArrayExtra(EXTRA_PUSH_DATA) ?: return
        val notification = MmsPduDecoder.decodeNotification(data) ?: return
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1)

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val downloadedIntent = buildDownloadedIntent(context, notification.transactionId, subscriptionId)
                mmsTransportGateway.download(
                    contentLocation = notification.contentLocation,
                    transactionId = notification.transactionId,
                    subscriptionId = subscriptionId,
                    downloadedIntent = downloadedIntent,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildDownloadedIntent(
        context: Context,
        transactionId: String,
        subscriptionId: Int,
    ): PendingIntent {
        val downloadIntent = Intent(context, MmsDownloadResultReceiver::class.java)
            .putExtra(MmsDownloadResultReceiver.EXTRA_TRANSACTION_ID, transactionId)
            .putExtra(MmsDownloadResultReceiver.EXTRA_SUBSCRIPTION_ID, subscriptionId)

        return PendingIntent.getBroadcast(
            context,
            transactionId.hashCode(),
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val EXTRA_PUSH_DATA = "data"
        const val EXTRA_SUBSCRIPTION = "subscription"
    }
}
