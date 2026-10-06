package text.message.sms.messaging.config

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import text.message.sms.messaging.BuildConfig

private const val TAG = "OverlayFeatureFlag"

/**
 * The overlay-permission + call-end feature flag's Firebase Remote Config parameter (boolean).
 *
 * - true: onboarding asks for the call-screening role (CallScreeningRoleScreen) and the call-end
 *   screen opens after each call.
 * - false: call-end disabled -- onboarding skips CallScreeningRole (Welcome goes straight to
 *   SetDefaultSms), and PostCallActivity/PhoneStateReceiver/CallEndLauncher/CallEndActivity never
 *   show the call-end screen.
 */
internal const val OVERLAY_AND_CALL_END_KEY = "overlay_and_call_end_enabled"

/** What the flag is until Remote Config has activated a value: a first launch, a fetch that never
 * succeeded, or Remote Config not being readable at all. Also registered as its in-app default. */
internal const val OVERLAY_AND_CALL_END_DEFAULT = true

private const val RELEASE_MIN_FETCH_INTERVAL_SECONDS = 12 * 60 * 60L

/** Where the flag comes from -- Remote Config in the app, a fake in OverlayFlagSessionTest. */
internal interface OverlayFlagSource {
    /** The value activated by an earlier fetch, or null when there is none. Local only, never
     * waits on the network. */
    fun activatedValue(): Boolean?

    /** Starts a fetch whose result [activatedValue] returns from the next session on. */
    fun fetchForNextSession()
}

/**
 * The flag for one process: [enabled] is read from [source] once and kept, so onboarding and an
 * in-progress call can't switch paths on a fetch that lands mid-session. A source that has no
 * value, or throws, resolves to [OVERLAY_AND_CALL_END_DEFAULT]. [log] is a parameter so unit tests
 * run without android.util.Log.
 */
internal class OverlayFlagSession(
    private val source: OverlayFlagSource,
    private val log: (message: String, error: Exception?) -> Unit = { _, _ -> },
) {

    val enabled: Boolean by lazy {
        val activated = try {
            source.activatedValue()
        } catch (e: Exception) {
            log("Remote Config unreadable, using the default", e)
            null
        }
        val resolved = activated ?: OVERLAY_AND_CALL_END_DEFAULT
        log("enabled=$resolved for this session (activated=$activated)", null)
        resolved
    }

    /** Resolves [enabled] before fetching, so this session's value is always the one activated
     * before it, however late the first [enabled] read comes. */
    fun snapshotThenFetch() {
        enabled
        try {
            source.fetchForNextSession()
        } catch (e: Exception) {
            log("Remote Config fetch not started", e)
        }
    }
}

private object RemoteConfigOverlayFlagSource : OverlayFlagSource {

    // Only a fetched-and-activated value counts. The in-app default is applied asynchronously, so
    // a first launch can read before it's in place -- and would get Remote Config's static false.
    override fun activatedValue(): Boolean? {
        val value = FirebaseRemoteConfig.getInstance().getValue(OVERLAY_AND_CALL_END_KEY)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) value.asBoolean() else null
    }

    override fun fetchForNextSession() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else RELEASE_MIN_FETCH_INTERVAL_SECONDS)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)
            .continueWithTask {
                remoteConfig.setDefaultsAsync(mapOf(OVERLAY_AND_CALL_END_KEY to OVERLAY_AND_CALL_END_DEFAULT))
            }
            .continueWithTask { remoteConfig.fetchAndActivate() }
            .addOnCompleteListener { task ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "fetchAndActivate success=${task.isSuccessful} newlyActivated=${task.result.takeIf { task.isSuccessful }}", task.exception)
                }
            }
    }
}

/**
 * The single read every call site goes through. A plain object rather than a Hilt singleton, so
 * the manifest receiver, the call-end launcher, CallEndActivity's static launcher and Compose all
 * read it the same way, with nothing to inject.
 *
 * Firebase Remote Config is the only source. FirebaseApp is initialized by Firebase's own
 * ContentProvider before [text.message.sms.messaging.MessagingApplication.onCreate], so it's ready
 * before any receiver or service can read the flag; if it ever isn't, the read falls back to
 * [OVERLAY_AND_CALL_END_DEFAULT] for the session rather than throwing.
 */
object OverlayFeatureFlag {
    private val session = OverlayFlagSession(RemoteConfigOverlayFlagSource) { message, error ->
        if (BuildConfig.DEBUG) Log.d(TAG, message, error)
    }

    /** This session's value: what Remote Config activated in an earlier session, else the default.
     * Reads local storage on the first call only, never the network. */
    fun isEnabled(): Boolean = session.enabled

    /** Called once per process from MessagingApplication, off the main thread. */
    fun snapshotThenFetch() = session.snapshotThenFetch()
}
