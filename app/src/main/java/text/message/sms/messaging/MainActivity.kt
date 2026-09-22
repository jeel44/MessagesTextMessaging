package text.message.sms.messaging

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
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
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession
import text.message.sms.messaging.domain.usecase.SyncMessages
import text.message.sms.messaging.service.DefaultSmsAppGuard
import text.message.sms.messaging.ui.navigation.MessagingDestination
import text.message.sms.messaging.ui.navigation.MessagingNavHost
import text.message.sms.messaging.ui.theme.AppTheme
import text.message.sms.messaging.util.ColdStartTracer
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

    /** Non-null exactly when an [IncomingMessageNotifier][text.message.sms.messaging.domain
     * .repository.IncomingMessageNotifier] notification (or any other [EXTRA_THREAD_ID]-carrying intent) was
     * just tapped -- read once by the `LaunchedEffect` inside [setContent] below, which navigates
     * to that thread and resets this back to null. A plain Activity field (not `SavedStateHandle`
     * or a ViewModel) since it is only ever a same-process, single-use handoff into the Compose
     * tree, exactly like [text.message.sms.messaging.util.ChatOpenHint]. */
    private var pendingThreadId by mutableStateOf<Long?>(null)

    /** Non-null exactly when [CallEndTriggerService][text.message.sms.messaging.service
     * .CallEndTriggerService]'s full-screen-intent notification (or, from Settings' Debug
     * section, a synthetic demo session) was just tapped/launched -- the same same-process,
     * single-use handoff as [pendingThreadId] above, read once by [setContent]'s own
     * `LaunchedEffect` below, which navigates to [MessagingDestination.CallEnd] and resets this to
     * null. Also drives [applyCallEndWindowFlags]/[clearCallEndWindowFlags], so the app can show
     * over the lock screen for exactly this launch and no other. */
    private var pendingCallSession by mutableStateOf<CallSession?>(null)

    /** True until navigation has moved off [MessagingDestination.Splash] -- see the
     * `setKeepOnScreenCondition` call below. Starts true so the system splash installed by
     * [installSplashScreen] (see `Theme.App.Starting` in themes.xml) stays up across the whole
     * gap between process start and [text.message.sms.messaging.ui.screens.onboarding.SplashScreen]
     * resolving the onboarding flag and navigating away, instead of a blank frame or a second,
     * separately-timed Compose splash ever being visible. */
    private var keepSystemSplashOnScreen by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        ColdStartTracer.mark("MainActivity.onCreate:start")
        // Must run before super.onCreate() -- this is what lets the OS keep showing the themed
        // splash (icon + background from Theme.App.Starting) instead of a blank window while the
        // rest of onCreate/setContent below runs.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        ColdStartTracer.mark("MainActivity.onCreate:afterSuper")
        splashScreen.setKeepOnScreenCondition { keepSystemSplashOnScreen }
        // Seeded here, before the onCreate-following onResume ever runs, so that first onResume
        // never mistakes an already-granted role for a fresh grant and fires a redundant sync.
        wasDefaultSmsApp = defaultSmsAppGuard.isDefault
        pendingThreadId = intent.threadIdExtra()
        pendingCallSession = intent.callSessionExtra()
        if (pendingCallSession != null) applyCallEndWindowFlags()

        enableEdgeToEdge()
        // MessagingApplication.onCreate has already migrated/persisted an explicit theme mode by
        // this point, so this is a fast read of already-resident DataStore state -- done
        // synchronously so the very first composed frame (including SplashScreen, both wrapped in
        // AppTheme below) already reflects it, instead of momentarily showing
        // collectAsStateWithLifecycle's own initialValue default before the flow's first real
        // emission lands (a visible light/dark flash on the app's first frame otherwise).
        ColdStartTracer.mark("MainActivity.onCreate:beforeThemeRunBlocking")
        val initialThemePreference = runBlocking { themePreferences.themePreference.first() }
        ColdStartTracer.mark("MainActivity.onCreate:afterThemeRunBlocking")
        // First (and only, for this investigation) OnPreDrawListener on the root view: fires just
        // before the window's first real draw pass, the same signal `adb shell am start -W`'s
        // TotalTime is itself based on -- see ColdStartTracer's doc comment.
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
            }
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
                    // Created here (rather than left to MessagingNavHost's own default) so the
                    // deep-link effect below can drive it directly, on top of whatever the graph
                    // is already showing.
                    val navController = rememberNavController()

                    // Dismisses the system splash (see keepSystemSplashOnScreen/installSplashScreen
                    // above) the moment the graph moves off Splash -- whichever of
                    // onOnboardingComplete/onOnboardingIncomplete it took. Runs once per
                    // composition, not once per back-stack change: by the time this has fired the
                    // splash is gone for good, and Splash is never navigated back to.
                    LaunchedEffect(navController) {
                        navController.currentBackStackEntryFlow
                            .first { it.destination.route != MessagingDestination.Splash.route }
                        keepSystemSplashOnScreen = false
                    }

                    // Waits for the graph to actually reach ConversationList before pushing Chat
                    // on top -- at a cold start the graph begins at Splash and only gets there
                    // asynchronously (it awaits onboarding's own DataStore read), and navigating
                    // to Chat any earlier would race Splash's own popUpTo navigation. In practice
                    // this never actually waits long: every entry point that can set
                    // pendingThreadId (a notification tap) only exists once a message has already
                    // been received, which itself requires onboarding to be long complete.
                    LaunchedEffect(pendingThreadId) {
                        val threadId = pendingThreadId ?: return@LaunchedEffect
                        navController.currentBackStackEntryFlow
                            .first { it.destination.route == MessagingDestination.ConversationList.route }
                        navController.navigate(MessagingDestination.Chat.routeFor(threadId))
                        pendingThreadId = null
                    }

                    // Same shape as the pendingThreadId effect above, but waits only for the graph
                    // to move off Splash (not specifically to ConversationList) before navigating --
                    // a call can end while the user is anywhere in the app, not only once Home is
                    // showing, so gating on ConversationList specifically would silently never fire
                    // for that far more common case. Still avoids racing Splash's own popUpTo
                    // navigation, the same problem the ConversationList wait above guards against.
                    LaunchedEffect(pendingCallSession) {
                        val session = pendingCallSession ?: return@LaunchedEffect
                        navController.currentBackStackEntryFlow
                            .first { it.destination.route != MessagingDestination.Splash.route }
                        navController.navigate(MessagingDestination.CallEnd.routeFor(session))
                        pendingCallSession = null
                        clearCallEndWindowFlags()
                    }

                    MessagingNavHost(navController = navController)
                }
            }
        }
        ColdStartTracer.mark("MainActivity.onCreate:end (afterSetContent)")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.threadIdExtra()?.let { pendingThreadId = it }
        intent.callSessionExtra()?.let {
            pendingCallSession = it
            applyCallEndWindowFlags()
        }
    }

    private fun Intent.threadIdExtra(): Long? =
        getLongExtra(EXTRA_THREAD_ID, -1L).takeIf { it != -1L }

    /** Decodes the primitive extras [text.message.sms.messaging.service.CallEndTriggerService]
     * puts on its full-screen-intent's [Intent] back into a [CallSession] -- same "plain extras,
     * no Parcelable" approach as [threadIdExtra], just spread across more of them. */
    private fun Intent.callSessionExtra(): CallSession? {
        if (!getBooleanExtra(EXTRA_IS_CALL_END, false)) return null
        return CallSession(
            phoneNumber = getStringExtra(EXTRA_CALL_PHONE_NUMBER),
            direction = getStringExtra(EXTRA_CALL_DIRECTION)
                ?.let { runCatching { CallDirection.valueOf(it) }.getOrNull() }
                ?: CallDirection.INCOMING,
            outcome = getStringExtra(EXTRA_CALL_OUTCOME)
                ?.let { runCatching { CallOutcome.valueOf(it) }.getOrNull() }
                ?: CallOutcome.MISSED,
            startedAt = getLongExtra(EXTRA_CALL_STARTED_AT, 0L),
            endedAt = getLongExtra(EXTRA_CALL_ENDED_AT, 0L),
            durationMillis = getLongExtra(EXTRA_CALL_DURATION_MILLIS, 0L),
        )
    }

    /** Lets this window show over the lock screen and turn the screen on -- API 27+ has a proper
     * per-Activity API for this; [minSdk] (26) needs the older window-flag form instead. Called
     * only for a call-end launch (never unconditionally -- a normal app open must never show over
     * the lock screen), and undone by [clearCallEndWindowFlags] once that launch is consumed. */
    private fun applyCallEndWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun clearCallEndWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
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

    companion object {
        /** Carries the thread an [IncomingMessageNotifier][text.message.sms.messaging.domain
         * .repository.IncomingMessageNotifier] notification was tapped for -- read by [threadIdExtra] in
         * both [onCreate] (cold start) and [onNewIntent] (already running). */
        const val EXTRA_THREAD_ID: String = "extra_thread_id"

        /** Set by [text.message.sms.messaging.service.CallEndTriggerService]'s full-screen-intent
         * notification -- true marks the [Intent] as a call-end launch at all, distinguishing it
         * from a plain app-icon/other launch that happens to carry none of the extras below (all
         * of which would otherwise decode to a valid-looking, but wrong, default [CallSession]).
         * See [callSessionExtra]. */
        const val EXTRA_IS_CALL_END: String = "extra_is_call_end"
        const val EXTRA_CALL_PHONE_NUMBER: String = "extra_call_phone_number"
        const val EXTRA_CALL_DIRECTION: String = "extra_call_direction"
        const val EXTRA_CALL_OUTCOME: String = "extra_call_outcome"
        const val EXTRA_CALL_STARTED_AT: String = "extra_call_started_at"
        const val EXTRA_CALL_ENDED_AT: String = "extra_call_ended_at"
        const val EXTRA_CALL_DURATION_MILLIS: String = "extra_call_duration_millis"
    }
}
