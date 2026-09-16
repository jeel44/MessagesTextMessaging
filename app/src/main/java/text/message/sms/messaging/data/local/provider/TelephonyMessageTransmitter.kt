package text.message.sms.messaging.data.local.provider

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import text.message.sms.messaging.data.local.db.dao.ScheduledMessageDao
import text.message.sms.messaging.data.local.db.entity.ScheduledMessageEntity
import text.message.sms.messaging.data.local.provider.mms.MmsPart
import text.message.sms.messaging.data.local.provider.mms.MmsPduEncoder
import text.message.sms.messaging.data.local.provider.mms.MmsSendRequest
import text.message.sms.messaging.data.receiver.MessageDeliveredReceiver
import text.message.sms.messaging.data.receiver.MessageSentReceiver
import text.message.sms.messaging.data.receiver.MmsSendResultReceiver
import text.message.sms.messaging.data.work.ScheduledSendWorker
import text.message.sms.messaging.domain.model.Attachment
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.repository.MessageRepository
import text.message.sms.messaging.domain.repository.MessageTransmitter
import javax.inject.Inject
import javax.inject.Singleton

/** Hands outgoing messages to the platform radio, and schedules delayed ones through
 * WorkManager -- see [ScheduledSendWorker] for why WorkManager rather than `AlarmManager`. */
@Singleton
class TelephonyMessageTransmitter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val defaultSmsManager: SmsManager,
    private val mmsTransportGateway: MmsTransportGateway,
    private val contentResolver: ContentResolver,
    private val workManager: WorkManager,
    private val scheduledMessageDao: ScheduledMessageDao,
    private val messageRepository: MessageRepository,
) : MessageTransmitter {

    override suspend fun transmit(message: Message) {
        when (message.channel) {
            MessageChannel.SMS -> transmitSms(message)
            MessageChannel.MMS -> transmitMms(message)
        }
    }

    private fun transmitSms(message: Message) {
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

    private suspend fun transmitMms(message: Message) {
        val address = requireNotNull(message.address) { "Message ${message.id} has no recipient" }
        val parts = buildList {
            if (message.body.isNotBlank()) {
                add(MmsPart("text/plain", name = null, contentId = null, data = message.body.toByteArray()))
            }
            message.attachments.forEach { add(it.toMmsPart(contentResolver)) }
        }

        val pdu = MmsPduEncoder.encodeSendRequest(MmsSendRequest(listOf(address), null, parts))
        val sentIntent = broadcast(MmsSendResultReceiver::class.java, message.id, 0)
        mmsTransportGateway.send(message.id, pdu, message.subscriptionId, sentIntent)
    }

    override suspend fun cancelPending(messageId: Long) {
        workManager.cancelUniqueWork(workNameFor(messageId))
        scheduledMessageDao.delete(messageId)
    }

    override suspend fun schedule(message: Message, sendAtMillis: Long) {
        enqueue(message.id, sendAtMillis)
        scheduledMessageDao.upsert(
            ScheduledMessageEntity(
                messageId = message.id,
                sendAtMillis = sendAtMillis,
                workName = workNameFor(message.id),
            ),
        )
    }

    /** Safety net for anything WorkManager's own persistence didn't end up dispatching --
     * normal operation never reaches here, see the class doc on [ScheduledSendWorker]. */
    override suspend fun dispatchDue(nowMillis: Long) {
        scheduledMessageDao.findDue(nowMillis).forEach { scheduled ->
            workManager.cancelUniqueWork(scheduled.workName)
            val message = messageRepository.findById(scheduled.messageId)
            if (message != null) transmit(message)
            scheduledMessageDao.delete(scheduled.messageId)
        }
    }

    override suspend fun rearmAlarms() {
        scheduledMessageDao.findAll().forEach { scheduled ->
            val hasLiveWork = workManager.getWorkInfosForUniqueWorkFlow(scheduled.workName)
                .first()
                .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            if (!hasLiveWork) enqueue(scheduled.messageId, scheduled.sendAtMillis)
        }
    }

    private fun enqueue(messageId: Long, sendAtMillis: Long) {
        val delay = (sendAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ScheduledSendWorker.KEY_MESSAGE_ID to messageId))
            .build()
        workManager.enqueueUniqueWork(workNameFor(messageId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workNameFor(messageId: Long): String = "scheduled_send_$messageId"

    private fun Attachment.toMmsPart(resolver: ContentResolver): MmsPart {
        val data = text?.toByteArray()
            ?: contentUri?.let { uri -> resolver.openInputStream(uri.toUri())?.use { it.readBytes() } }
            ?: ByteArray(0)
        return MmsPart(mimeType, fileName, contentId = null, data = data)
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

    private fun broadcast(receiver: Class<*>, messageId: Long, partIndex: Int): PendingIntent {
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
