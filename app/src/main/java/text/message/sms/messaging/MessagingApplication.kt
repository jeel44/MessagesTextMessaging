package text.message.sms.messaging

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.StrictMode
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
import text.message.sms.messaging.util.ColdStartTracer
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

    // TEMPORARY (cold-start investigation, see ColdStartTracer): super.attachBaseContext is where
    // the Hilt-generated base class (Hilt_MessagingApplication) actually builds the Dagger
    // component graph and performs field injection into this instance -- timing around it in
    // isolation is the only way to separate "Hilt/DI setup" from everything below in onCreate.
    override fun attachBaseContext(base: Context?) {
        ColdStartTracer.mark("Application.attachBaseContext:start")
        super.attachBaseContext(base)
        ColdStartTracer.mark("Application.attachBaseContext:end (Hilt component + field injection done)")
    }

    override fun onCreate() {
        super.onCreate()
        ColdStartTracer.mark("Application.onCreate:start")

        // Debug-only regression trip-wire for future main-thread/leak issues -- penaltyLog (never
        // penaltyDeath) so a violation shows up in Logcat without crashing the app. Deliberately
        // placed before the runBlocking calls below: those are expected, already-known-about
        // synchronous DataStore reads, not something this batch fixes -- StrictMode logging them
        // is expected and informs a future cleanup pass, not a bug in this one.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build(),
            )
        }
        ColdStartTracer.mark("Application.onCreate:afterStrictMode")

        // Synchronous and, after the first successful run, a no-op forever (see
        // migrateDefaultThemeModeIfNeeded) -- must complete before any Activity.onCreate ever
        // reads the stored theme, which finishing here, before the rest of this method, guarantees
        // (Application.onCreate always completes before the first Activity is created).
        runBlocking {
            themePreferences.migrateDefaultThemeModeIfNeeded(
                isExistingUser = onboardingPreferences.isOnboardingComplete.first(),
            )
        }
        ColdStartTracer.mark("Application.onCreate:afterThemeMigrationRunBlocking")

        notificationChannels.register()
        ColdStartTracer.mark("Application.onCreate:afterNotificationChannels")

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
        ColdStartTracer.mark("Application.onCreate:afterContactObserverRegisterAndSyncLaunch")

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
        ColdStartTracer.mark("Application.onCreate:afterProviderObserverRegister")
        Log.d(TAG, "defaultSms=$isDefault observerRegistered=$hasReadSms")

        if (!isDefault) {
            ColdStartTracer.mark("Application.onCreate:end (early return, not default SMS app)")
            return // writes below need the default-SMS role
        }

        // syncMessages/rearmScheduledMessageAlarms (which is where WorkManager -- lazily
        // initialized via this app's own Configuration.Provider above, since the manifest removes
        // WorkManager's default androidx.startup initializer -- first gets touched) both run
        // inside this launch, on applicationScope's IO dispatcher: the launch{} call below returns
        // immediately, so their real cost lands after this method (and first frame) regardless.
        applicationScope.launch {
            syncMessages()
            rearmScheduledMessageAlarms()
        }
        ColdStartTracer.mark("Application.onCreate:end")
    }

    private companion object {
        const val TAG = "MessagingApplication"
    }
}
