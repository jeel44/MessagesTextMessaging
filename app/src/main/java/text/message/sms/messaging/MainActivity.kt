package text.message.sms.messaging

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import text.message.sms.messaging.data.local.datastore.ThemeMode
import text.message.sms.messaging.data.local.datastore.ThemePreference
import text.message.sms.messaging.data.local.datastore.ThemePreferences
import text.message.sms.messaging.data.local.provider.ProviderChangeObserver
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.ui.navigation.MessagingNavHost
import text.message.sms.messaging.ui.theme.AppTheme
import javax.inject.Inject

/**
 * The app's only activity; every screen is a Compose destination inside [MessagingNavHost].
 *
 * Also the catch-all for the default-SMS-app role changing while this app was not driving the
 * change itself -- e.g. the user backgrounds the app, flips the default SMS app in system
 * Settings, then returns. [onResume] re-checks [DefaultSmsAppGuard.isDefault] against the value
 * last observed and, only on a false-to-true transition, starts a catch-up sync and registers
 * [ProviderChangeObserver] (a no-op if [MessagingApplication.onCreate] already registered it at
 * launch -- the observer guards its own double-registration). In-app grants (onboarding's
 * [text.message.sms.messaging.ui.screens.onboarding.SetDefaultSmsScreen], Home's own empty-state
 * prompt) also trigger their own immediate sync and registration, so this is a safety net for the
 * outside-the-app path, not the only path -- both are safe to run together since
 * [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll] is idempotent and
 * [ProviderChangeObserver.register] is a no-op once already registered.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var defaultSmsAppGuard: DefaultSmsAppGuard

    @Inject
    lateinit var syncMessages: SyncMessages

    @Inject
    lateinit var providerChangeObserver: ProviderChangeObserver

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var wasDefaultSmsApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seeded here, before the onCreate-following onResume ever runs, so that first onResume
        // never mistakes an already-granted role for a fresh grant and fires a redundant sync.
        wasDefaultSmsApp = defaultSmsAppGuard.isDefault
        enableEdgeToEdge()
        // MessagingApplication.onCreate has already migrated/persisted an explicit theme mode by
        // this point, so this is a fast read of already-resident DataStore state -- done
        // synchronously so the very first composed frame (including SplashScreen, both wrapped in
        // AppTheme below) already reflects it, instead of momentarily showing
        // collectAsStateWithLifecycle's own initialValue default before the flow's first real
        // emission lands (a visible light/dark flash on the app's first frame otherwise).
        val initialThemePreference = runBlocking { themePreferences.themePreference.first() }
        setContent {
            // Live over ThemePreferences.themePreference, not a one-shot read -- a change made in
            // Settings' theme picker recomposes this the moment DataStore commits it, so the
            // whole app recolors immediately rather than only on the next cold start.
            val themePreference by themePreferences.themePreference
                .collectAsStateWithLifecycle(initialValue = initialThemePreference)

            val darkTheme = when (themePreference.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            AppTheme(darkTheme = darkTheme, accentColor = themePreference.accentColor) {
                // enableEdgeToEdge()'s own default style is fixed at onCreate and never reacts to
                // an in-app theme override (ThemeMode.LIGHT/DARK against a differing system mode),
                // so the status/navigation bar icon color is set explicitly here instead, recomputed
                // whenever darkTheme itself changes -- dark icons over the light background, light
                // icons once the app is actually in dark theme.
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
                Surface(
                    // testTagsAsResourceId turns every Modifier.testTag below into a real
                    // resource-id UiAutomator can query -- the baseline profile generator (see
                    // the :baselineprofile module) drives this app as a black box and has no
                    // other reliable, localization-proof way to find a specific element.
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MessagingNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isDefaultNow = defaultSmsAppGuard.isDefault
        if (isDefaultNow && !wasDefaultSmsApp) {
            providerChangeObserver.register()
            applicationScope.launch { syncMessages() }
        }
        wasDefaultSmsApp = isDefaultNow
    }
}
