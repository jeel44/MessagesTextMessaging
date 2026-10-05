package text.message.sms.messaging.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import text.message.sms.messaging.MainActivity
import text.message.sms.messaging.R
import text.message.sms.messaging.data.local.telephony.SimRepository
import text.message.sms.messaging.domain.repository.ScheduledSendNotifier
import text.message.sms.messaging.domain.repository.SendEnvironment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScheduledSendNotifier] on the [NotificationChannels.SEND_FAILURES] channel: one notification
 * per message (a later one for the same message replaces it), opening its conversation, where the
 * bubble offers Send now / Cancel. Deliberately generic -- no message text or recipient in it.
 */
@Singleton
class ScheduledSendNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
) : ScheduledSendNotifier {

    override fun notifyFailed(messageId: Long, threadId: Long) =
        post(messageId, threadId, R.string.scheduled_notification_failed_title, R.string.scheduled_notification_failed_text)

    override fun notifyMissed(messageId: Long, threadId: Long) =
        post(messageId, threadId, R.string.scheduled_notification_missed_title, R.string.scheduled_notification_missed_text)

    private fun post(messageId: Long, threadId: Long, @StringRes title: Int, @StringRes text: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openChat = PendingIntent.getActivity(
            context,
            notificationId(messageId),
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_THREAD_ID, threadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.SEND_FAILURES)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
            .setContentIntent(openChat)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        // Tagged, so these ids can never collide with the incoming-message notifications'.
        notificationManager.notify(NOTIFICATION_TAG, notificationId(messageId), notification)
    }

    private fun notificationId(messageId: Long): Int = messageId.hashCode()

    private companion object {
        const val NOTIFICATION_TAG = "scheduled_send"
    }
}

/** [SendEnvironment] read from the platform at the moment of sending. */
@Singleton
class DeviceSendEnvironment @Inject constructor(
    private val defaultSmsAppGuard: DefaultSmsAppGuard,
    private val simRepository: SimRepository,
) : SendEnvironment {

    override fun isDefaultSmsApp(): Boolean = defaultSmsAppGuard.isDefault

    override suspend fun activeSubscriptionIds(): List<Int> =
        simRepository.currentActiveSims().map { it.subscriptionId }
}
