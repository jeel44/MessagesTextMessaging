package text.message.sms.messaging.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.ReceiveSms
import javax.inject.Inject

/**
 * Entry point for inbound SMS. Only the default SMS app receives `SMS_DELIVER`, and it is
 * responsible for writing the message to the provider -- the platform will not do it.
 */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject
    lateinit var receiveSms: ReceiveSms

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A long SMS arrives as several PDUs that must be stitched back into one body.
        val head = messages.first()
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val address = head.displayOriginatingAddress ?: return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                receiveSms(
                    ReceiveSms.Params(
                        address = address,
                        body = body,
                        sentAtMillis = head.timestampMillis,
                        receivedAtMillis = System.currentTimeMillis(),
                        subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1),
                    ),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_SUBSCRIPTION = "subscription"
    }
}
