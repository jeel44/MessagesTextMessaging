package text.message.sms.messaging

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.provider.ContactChangeObserver
import text.message.sms.messaging.data.local.provider.ProviderChangeObserver
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.RearmScheduledMessageAlarms
import text.message.sms.messaging.domain.usecase.SyncContacts
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
    lateinit var contactChangeObserver: ContactChangeObserver

    @Inject
    lateinit var syncMessages: SyncMessages

    @Inject
    lateinit var syncContacts: SyncContacts

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

        // READ_CONTACTS is a separate runtime permission from the default-SMS-app role SMS/MMS
        // sync below depends on, so it gets its own check rather than being folded into
        // defaultSmsAppGuard -- a device can hold the role but still have contacts permission
        // denied, or vice versa during onboarding.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            contactChangeObserver.register()
            applicationScope.launch { syncContacts() }
        }

        if (!defaultSmsAppGuard.isDefault) return // reads/writes below need the default-SMS role

        providerChangeObserver.register()
        applicationScope.launch {
            syncMessages()
            rearmScheduledMessageAlarms()
        }
    }
}
