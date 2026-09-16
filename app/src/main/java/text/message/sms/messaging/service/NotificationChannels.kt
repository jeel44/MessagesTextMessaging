package text.message.sms.messaging.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Registers the notification channels the app posts to. Registering twice is a no-op. */
@Singleton
class NotificationChannels @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
) {

    fun register() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                INCOMING_MESSAGES,
                context.getString(text.message.sms.messaging.R.string.channel_incoming_messages),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                SEND_FAILURES,
                context.getString(text.message.sms.messaging.R.string.channel_send_failures),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        const val INCOMING_MESSAGES: String = "incoming_messages"
        const val SEND_FAILURES: String = "send_failures"
    }
}
