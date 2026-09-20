package text.message.sms.messaging

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import text.message.sms.messaging.data.local.datastore.ThemePreferences
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
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()

        // Synchronous and, after the first successful run, a no-op forever (see
        // migrateDefaultThemeModeIfNeeded) -- must complete before any Activity.onCreate ever
        // reads the stored theme, which finishing here, before the rest of this method, guarantees
        // (Application.onCreate always completes before the first Activity is created).
        runBlocking {
            themePreferences.migrateDefaultThemeModeIfNeeded(
                isExistingUser = onboardingPreferences.isOnboardingComplete.first(),
            )
        }

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

        // The observer only needs READ_SMS (it just watches content://sms/content://mms), unlike
        // the sync/alarm work below, which needs the default-SMS role to actually write anything
        // -- registering it whenever READ_SMS is granted means a message another app or the
        // platform writes into the provider still reaches this app's Room cache even before (or
        // without) this app ever holding the role. [ProviderChangeObserver.register] no-ops if
        // already registered, so this can never double-register alongside the false->true
        // transition path in [MainActivity.onResume]/[text.message.sms.messaging.ui.screens
        // .conversationlist.ConversationListViewModel.refreshDefaultSmsAppStatus].
        val isDefault = defaultSmsAppGuard.isDefault
        val hasReadSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (hasReadSms) {
            providerChangeObserver.register()
        }
        Log.d(TAG, "defaultSms=$isDefault observerRegistered=$hasReadSms")

        if (!isDefault) return // writes below need the default-SMS role

        applicationScope.launch {
            syncMessages()
            rearmScheduledMessageAlarms()
        }
    }

    private companion object {
        const val TAG = "MessagingApplication"
    }
}
