package text.message.sms.messaging.ui.screens.callend

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import text.message.sms.messaging.MainActivity
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.data.local.datastore.ThemeMode
import text.message.sms.messaging.data.local.datastore.ThemePreferences
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession
import text.message.sms.messaging.ui.navigation.MessagingDestination
import text.message.sms.messaging.ui.theme.AppTheme
import javax.inject.Inject

private const val EXTRA_PHONE_NUMBER = "extra_call_phone_number"
private const val EXTRA_CALL_DIRECTION = "extra_call_direction"
private const val EXTRA_CALL_OUTCOME = "extra_call_outcome"
private const val EXTRA_CALL_STARTED_AT = "extra_call_started_at"
private const val EXTRA_CALL_ENDED_AT = "extra_call_ended_at"
private const val EXTRA_CALL_DURATION_MILLIS = "extra_call_duration_millis"

/**
 * Full-screen call-end popup, launched directly by [text.message.sms.messaging.service
 * .CallEndTriggerService] the instant a call ends (see [start]) -- no notification involved.
 *
 * Lives in its own task (`launchMode="singleTask"` plus a dedicated `taskAffinity` and
 * `excludeFromRecents` in the manifest) so back/close returns to whatever the user was doing
 * before the call, never into [MainActivity]'s own task. [CallEndScreen] itself still needs a
 * single-destination [NavHost] here (rather than being called directly) purely so its
 * [CallEndViewModel] keeps reading the [CallSession] off `SavedStateHandle` route args, exactly
 * as [text.message.sms.messaging.ui.screens.chat.ChatViewModel] does for its thread id.
 *
 * Tapping a conversation, "View Contacts", "Messages" or a coming-soon item hands off to
 * [MainActivity] (a different task) and finishes this Activity, rather than navigating in place --
 * see [MainActivity.EXTRA_OPEN_CONTACTS]/[MainActivity.EXTRA_COMING_SOON_FEATURE].
 */
@AndroidEntryPoint
class CallEndActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var adConsentManager: AdConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // API 27+ has a proper per-Activity API for showing over the lock screen; minSdk (26)
        // needs the older window-flag form instead. Unlike MainActivity (which toggles this on
        // only for the duration of a pending call-end launch, since it serves many other
        // purposes), this Activity exists for nothing else, so it's simply left on for its whole
        // lifetime.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        // Often the process's first activity (the phone-state broadcast cold-starts straight into
        // it), so this is what resolves a Pending consent state for CallEndViewModel's ad slot.
        // Refresh only -- the consent form itself is only ever shown from MainActivity.
        adConsentManager.refreshConsentInfo(this)

        val session = intent.toCallSession()
        enableEdgeToEdge()
        // Same "synchronous first read, then live collect" approach as MainActivity.onCreate, so
        // this screen matches the user's chosen theme from its very first frame.
        val initialThemePreference = runBlocking { themePreferences.themePreference.first() }

        setContent {
            val themePreference by themePreferences.themePreference
                .collectAsStateWithLifecycle(initialValue = initialThemePreference)

            val darkTheme = when (themePreference.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            AppTheme(darkTheme = darkTheme, accentColor = themePreference.accentColor) {
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = MessagingDestination.CallEnd.routeFor(session),
                    ) {
                        composable(
                            route = MessagingDestination.CallEnd.route,
                            arguments = listOf(
                                navArgument(MessagingDestination.ARG_PHONE_NUMBER) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                                navArgument(MessagingDestination.ARG_CALL_DIRECTION) {
                                    type = NavType.StringType
                                    defaultValue = CallDirection.INCOMING.name
                                },
                                navArgument(MessagingDestination.ARG_CALL_OUTCOME) {
                                    type = NavType.StringType
                                    defaultValue = CallOutcome.MISSED.name
                                },
                                navArgument(MessagingDestination.ARG_CALL_STARTED_AT) {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                                navArgument(MessagingDestination.ARG_CALL_ENDED_AT) {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                                navArgument(MessagingDestination.ARG_CALL_DURATION_MILLIS) {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                },
                            ),
                        ) {
                            CallEndScreen(
                                onConversationClick = { threadId ->
                                    handOffToMainActivity { putExtra(MainActivity.EXTRA_THREAD_ID, threadId) }
                                },
                                onViewContactsClick = {
                                    handOffToMainActivity { putExtra(MainActivity.EXTRA_OPEN_CONTACTS, true) }
                                },
                                onMessagesClick = {
                                    handOffToMainActivity {}
                                },
                                onComingSoonClick = { featureTitle ->
                                    handOffToMainActivity { putExtra(MainActivity.EXTRA_COMING_SOON_FEATURE, featureTitle) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** Launches [MainActivity] in its own task (reusing/clearing-to an existing instance, same as
     * the old full-screen-intent notification's PendingIntent did) and finishes this Activity --
     * so a conversation/contacts/messages/coming-soon tap here lands the user back in the app's
     * normal task, not stuck inside this one's isolated `taskAffinity`. */
    private inline fun handOffToMainActivity(putExtras: Intent.() -> Unit) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply(putExtras)
        startActivity(intent)
        finish()
    }

    companion object {
        /** Starts the call-end screen directly, bypassing any notification. Called only when
         * [android.provider.Settings.canDrawOverlays] is true (see
         * [text.message.sms.messaging.service.CallEndTriggerService]) -- holding that permission
         * is also what exempts this from the platform's background-activity-launch restrictions
         * when started from the service's context.
         *
         * `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS`/`FLAG_ACTIVITY_SINGLE_TOP` are deliberately not
         * added here -- this Activity's manifest entry already declares `excludeFromRecents=true`
         * and `launchMode=singleTask` (a strictly stronger guarantee than singleTop: at most one
         * instance across the whole task, not just at the top of the stack), so the equivalent
         * Intent flags would be redundant. `FLAG_ACTIVITY_NO_USER_ACTION` (this launch isn't a
         * user gesture, so it shouldn't reset idle/screensaver timers as if it were) and
         * `FLAG_ACTIVITY_NO_ANIMATION` (an incoming-call-style instant appearance, not a normal
         * windowed transition) have no manifest equivalent, so both are set explicitly. */
        fun start(context: Context, session: CallSession) {
            val intent = Intent(context, CallEndActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                .putExtra(EXTRA_PHONE_NUMBER, session.phoneNumber)
                .putExtra(EXTRA_CALL_DIRECTION, session.direction.name)
                .putExtra(EXTRA_CALL_OUTCOME, session.outcome.name)
                .putExtra(EXTRA_CALL_STARTED_AT, session.startedAt)
                .putExtra(EXTRA_CALL_ENDED_AT, session.endedAt)
                .putExtra(EXTRA_CALL_DURATION_MILLIS, session.durationMillis)
            context.startActivity(intent)
        }
    }
}

/** Decodes the primitive extras [CallEndActivity.start] puts on its launch [Intent] back into a
 * [CallSession] -- same "plain extras, no Parcelable" approach [MainActivity] already used for
 * this before the notification/Activity split. */
private fun Intent.toCallSession(): CallSession = CallSession(
    phoneNumber = getStringExtra(EXTRA_PHONE_NUMBER),
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
