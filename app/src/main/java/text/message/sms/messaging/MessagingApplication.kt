package text.message.sms.messaging

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import text.message.sms.messaging.service.NotificationChannels
import javax.inject.Inject

/** Process entry point and Hilt root. */
@HiltAndroidApp
class MessagingApplication : Application() {

    @Inject
    lateinit var notificationChannels: NotificationChannels

    override fun onCreate() {
        super.onCreate()
        notificationChannels.register()
    }
}
