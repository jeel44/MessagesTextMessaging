package text.message.sms.messaging.data.local.provider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import text.message.sms.messaging.data.receiver.MessageDeliveredReceiver
import text.message.sms.messaging.data.receiver.MessageSentReceiver
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.repository.MessageTransmitter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands outgoing messages to the platform radio.
 *
 * Only the plain-text SMS path is wired up in this scaffold; MMS assembly and scheduled sending
 * arrive with the send pipeline.
 */
@Singleton
class TelephonyMessageTransmitter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val defaultSmsManager: SmsManager,
) : MessageTransmitter {

    override suspend fun transmit(message: Message) {
        require(message.channel == MessageChannel.SMS) {
            "MMS sending is not wired up yet: message ${message.id}"
        }

        val address = requireNotNull(message.address) { "Message ${message.id} has no recipient" }
        val smsManager = smsManagerFor(message.subscriptionId)
        val parts = smsManager.divideMessage(message.body)

        smsManager.sendMultipartTextMessage(
            address,
            null,
            parts,
            ArrayList(parts.indices.map { index -> sentIntent(message.id, index) }),
            ArrayList(parts.indices.map { index -> deliveredIntent(message.id, index) }),
        )
    }

    override suspend fun cancelPending(messageId: Long) {
        TODO("Send-delay cancellation arrives with the send pipeline")
    }

    override suspend fun schedule(
        addresses: Set<String>,
        body: String,
        sendAtMillis: Long,
        subscriptionId: Int,
    ) {
        TODO("Scheduled sending arrives with the send pipeline")
    }

    override suspend fun dispatchDue(nowMillis: Long) {
        TODO("Scheduled sending arrives with the send pipeline")
    }

    override suspend fun rearmAlarms() {
        TODO("Scheduled sending arrives with the send pipeline")
    }

    /** Android 12 moved SIM selection onto the instance; older releases use the static form. */
    private fun smsManagerFor(subscriptionId: Int): SmsManager = when {
        subscriptionId < 0 -> defaultSmsManager

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            defaultSmsManager.createForSubscriptionId(subscriptionId)

        else -> @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
    }

    private fun sentIntent(messageId: Long, partIndex: Int): PendingIntent =
        broadcast(MessageSentReceiver::class.java, messageId, partIndex)

    private fun deliveredIntent(messageId: Long, partIndex: Int): PendingIntent =
        broadcast(MessageDeliveredReceiver::class.java, messageId, partIndex)

    private fun broadcast(
        receiver: Class<*>,
        messageId: Long,
        partIndex: Int,
    ): PendingIntent {
        val intent = Intent(context, receiver).putExtra(EXTRA_MESSAGE_ID, messageId)
        return PendingIntent.getBroadcast(
            context,
            // Request codes must be distinct per part, or the parts overwrite each other.
            (messageId * PART_CODE_STRIDE + partIndex).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_MESSAGE_ID: String = "text.message.sms.messaging.extra.MESSAGE_ID"
        private const val PART_CODE_STRIDE = 100L
    }
}
