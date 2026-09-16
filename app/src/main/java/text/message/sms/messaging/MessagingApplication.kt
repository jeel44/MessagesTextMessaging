package text.message.sms.messaging

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.provider.ProviderChangeObserver
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.RearmScheduledMessageAlarms
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.service.NotificationChannels
import javax.inject.Inject

/** Process entry point, Hilt root, and WorkManager's on-demand [Configuration.Provider]. */
@HiltAndroidApp
class MessagingApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationChannels: NotificationChannels

    @Inject
    lateinit var defaultSmsAppGuard: DefaultSmsAppGuard

    @Inject
    lateinit var providerChangeObserver: ProviderChangeObserver

    @Inject
    lateinit var syncMessages: SyncMessages

    @Inject
    lateinit var rearmScheduledMessageAlarms: RearmScheduledMessageAlarms

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notificationChannels.register()

        if (!defaultSmsAppGuard.isDefault) return // reads/writes below need the default-SMS role

        providerChangeObserver.register()
        applicationScope.launch {
            syncMessages()
            rearmScheduledMessageAlarms()
        }
    }
}
