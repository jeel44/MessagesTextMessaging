package text.message.sms.messaging

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.app.LocaleManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import text.message.sms.messaging.data.local.datastore.ThemeMode
import text.message.sms.messaging.data.local.datastore.ThemePreferences
import text.message.sms.messaging.data.local.provider.ProviderChangeObserver
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.ui.navigation.MessagingDestination
import text.message.sms.messaging.ui.navigation.MessagingNavHost
import text.message.sms.messaging.ui.theme.AppTheme
import text.message.sms.messaging.util.ColdStartTracer
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import java.util.Locale
import javax.inject.Inject

private class LocalizedContext(
    activity: Context,
    configuration: Configuration,
) : ContextWrapper(activity) {

    private val localizedContext: Context by lazy {
        activity.createConfigurationContext(configuration)
    }

    override fun getResources(): Resources {
        return localizedContext.resources
    }

    override fun getAssets(): AssetManager {
        return localizedContext.assets
    }
}

/**
 * The app's only activity; every screen is a Compose destination inside [MessagingNavHost].
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
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var adConsentManager: AdConsentManager

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var wasDefaultSmsApp = false

    private var pendingThreadId by mutableStateOf<Long?>(null)
    private var pendingOpenContacts by mutableStateOf(false)
    private var pendingComingSoonFeature by mutableStateOf<String?>(null)
    private var keepSystemSplashOnScreen by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        ColdStartTracer.mark("MainActivity.onCreate:start")
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        ColdStartTracer.mark("MainActivity.onCreate:afterSuper")
        splashScreen.setKeepOnScreenCondition { keepSystemSplashOnScreen }
        wasDefaultSmsApp = defaultSmsAppGuard.isDefault
        pendingThreadId = intent.threadIdExtra()
        pendingOpenContacts = intent.getBooleanExtra(EXTRA_OPEN_CONTACTS, false)
        pendingComingSoonFeature = intent.getStringExtra(EXTRA_COMING_SOON_FEATURE)

        // Every launch, as UMP recommends. Deliberately not held behind the splash: the check is a
        // network round trip, so on a first launch the form (if required) appears over Welcome a
        // moment after it renders -- Welcome's banner slot keeps shimmering until this settles.
        adConsentManager.gatherConsent(this)

        enableEdgeToEdge()
        ColdStartTracer.mark("MainActivity.onCreate:beforeThemeRunBlocking")
        val initialThemePreference = runBlocking { themePreferences.themePreference.first() }
        val initialLanguageTag = runBlocking { onboardingPreferences.languageTag.first() }
        ColdStartTracer.mark("MainActivity.onCreate:afterThemeRunBlocking")

        window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                ColdStartTracer.mark("MainActivity:firstFrame(OnPreDraw)")
                return true
            }
        })
        ColdStartTracer.mark("MainActivity.onCreate:beforeSetContent")
        setContent {
            remember {
                ColdStartTracer.mark("MainActivity:firstComposition(setContent root)")
                true
            }
            val themePreference by themePreferences.themePreference
                .collectAsStateWithLifecycle(initialValue = initialThemePreference)

            val languageTag by onboardingPreferences.languageTag
                .collectAsStateWithLifecycle(initialValue = initialLanguageTag)

            val darkTheme = when (themePreference.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val currentContext = LocalContext.current
            val currentConfig = LocalConfiguration.current

            val localizedContextAndConfig = remember(languageTag, currentContext, currentConfig) {
                val tag = languageTag
                // System Default reads the device's own locale, not Locale.getDefault(): the
                // Language screen persists the tag before calling setApplicationLocales (held
                // until its interstitial closes), so an earlier per-app override is still the
                // process default at that point and would keep the old language on screen.
                val locale = if (tag.isNullOrBlank() || tag == "system") {
                    LocaleManagerCompat.getSystemLocales(currentContext).get(0) ?: Locale.getDefault()
                } else {
                    Locale.forLanguageTag(tag)
                }
                val config = Configuration(currentConfig).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
                val localizedContext = LocalizedContext(currentContext, config)
                localizedContext to config
            }

            CompositionLocalProvider(
                LocalContext provides localizedContextAndConfig.first,
                LocalConfiguration provides localizedContextAndConfig.second,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalOnBackPressedDispatcherOwner provides this@MainActivity,
            ) {
                AppTheme(darkTheme = darkTheme, accentColor = themePreference.accentColor) {
                    SideEffect {
                        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                        insetsController.isAppearanceLightStatusBars = !darkTheme
                        insetsController.isAppearanceLightNavigationBars = !darkTheme
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true },
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val navController = rememberNavController()

                        LaunchedEffect(navController) {
                            navController.currentBackStackEntryFlow
                                .first { it.destination.route != MessagingDestination.Splash.route }
                            keepSystemSplashOnScreen = false
                        }

                        LaunchedEffect(pendingThreadId) {
                            val threadId = pendingThreadId ?: return@LaunchedEffect
                            navController.currentBackStackEntryFlow
                                .first { it.destination.route == MessagingDestination.ConversationList.route }
                            navController.navigate(MessagingDestination.Chat.routeFor(threadId))
                            pendingThreadId = null
                        }

                        LaunchedEffect(pendingOpenContacts) {
                            if (!pendingOpenContacts) return@LaunchedEffect
                            navController.currentBackStackEntryFlow
                                .first { it.destination.route == MessagingDestination.ConversationList.route }
                            navController.navigate(MessagingDestination.ContactsList.route)
                            pendingOpenContacts = false
                        }

                        LaunchedEffect(pendingComingSoonFeature) {
                            val featureTitle = pendingComingSoonFeature ?: return@LaunchedEffect
                            navController.currentBackStackEntryFlow
                                .first { it.destination.route == MessagingDestination.ConversationList.route }
                            navController.navigate(MessagingDestination.ComingSoon.routeFor(featureTitle))
                            pendingComingSoonFeature = null
                        }

                        MessagingNavHost(navController = navController)
                    }
                }
            }
        }
        ColdStartTracer.mark("MainActivity.onCreate:end (afterSetContent)")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.threadIdExtra()?.let { pendingThreadId = it }
        if (intent.getBooleanExtra(EXTRA_OPEN_CONTACTS, false)) pendingOpenContacts = true
        intent.getStringExtra(EXTRA_COMING_SOON_FEATURE)?.let { pendingComingSoonFeature = it }
    }

    private fun Intent.threadIdExtra(): Long? =
        getLongExtra(EXTRA_THREAD_ID, -1L).takeIf { it != -1L }

    override fun onResume() {
        super.onResume()
        val isDefaultNow = defaultSmsAppGuard.isDefault
        if (isDefaultNow && !wasDefaultSmsApp) {
            providerChangeObserver.register()
            applicationScope.launch { syncMessages() }
        }
        wasDefaultSmsApp = isDefaultNow
    }

    companion object {
        const val EXTRA_THREAD_ID: String = "extra_thread_id"
        const val EXTRA_OPEN_CONTACTS: String = "extra_open_contacts"
        const val EXTRA_COMING_SOON_FEATURE: String = "extra_coming_soon_feature"
    }
}
