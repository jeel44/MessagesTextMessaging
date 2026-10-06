package text.message.sms.messaging.service

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.config.OverlayFeatureFlag
import text.message.sms.messaging.domain.model.CallSession
import text.message.sms.messaging.ui.screens.callend.CallEndActivity
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallEndLauncher"

/** How long after the IDLE broadcast to launch [CallEndActivity] when the device is unlocked --
 * long enough for the in-call UI/dialer to finish tearing itself down first, so the call-end
 * screen doesn't visually collide with it. Time spent waiting on the call log counts towards it.
 * Skipped entirely when the device is locked: there's no competing UI to wait out, and delaying
 * only means a longer stretch showing the bare lock screen instead. */
internal const val LAUNCH_DELAY_UNLOCKED_MILLIS = 1_000L

/**
 * Everything between "[CallStateMonitor] says a call ended" and [CallEndActivity] being on
 * screen: resolve the call's details from the call log ([CallSessionResolver]), wait out the
 * dialer if the device is unlocked, then launch. Run by [PhoneStateReceiver] inside its
 * `goAsync()` window -- there is no service involved, foreground or otherwise, and so no
 * notification and nothing alive during the call itself.
 *
 * (An earlier design ran this from a `phoneCall`-type foreground service that lived for the span
 * of each call, which needed FOREGROUND_SERVICE_PHONE_CALL + MANAGE_OWN_CALLS purely to keep
 * [CallStateMonitor]'s in-memory state alive until IDLE. Reading the call log at IDLE instead
 * made that state, and the service, unnecessary.)
 *
 * Two gates, both "skip quietly": [OverlayFeatureFlag] off, or "draw over other apps" not
 * granted. The latter is a hard requirement rather than a nicety -- that permission is this
 * launch's exemption from the platform's background-activity-launch restrictions (see
 * [BackgroundActivityLaunchOverlay]); without it the platform would just drop the launch.
 *
 * Call-screening role holders also get the screen from the system's `POST_CALL` launch of
 * [text.message.sms.messaging.ui.screens.callend.PostCallActivity]; [CallEndShownGate], checked
 * right before the launch, keeps a user with both from seeing it twice for one call.
 *
 * Every step of the success path is logged under [BuildConfig.DEBUG] -- an earlier version of
 * this path had none, which made a real on-device failure (call detected fine, but no evidence
 * either way of whether [CallEndActivity.start] was ever actually reached) impossible to diagnose
 * from logs alone. Don't remove this logging without a replacement.
 *
 * The primary constructor takes every platform touchpoint as a function so plain-JVM unit tests
 * can drive it; Hilt uses the secondary one.
 */
@Singleton
class CallEndLauncher internal constructor(
    private val resolver: CallSessionResolver,
    private val isFlagEnabled: () -> Boolean,
    private val canDrawOverlays: () -> Boolean,
    private val isDeviceLocked: () -> Boolean,
    private val isCallInProgress: () -> Boolean,
    private val tryClaimLaunch: () -> Boolean,
    private val elapsedRealtime: () -> Long,
    private val launch: suspend (CallSession) -> Unit,
    private val log: (message: String, error: Throwable?) -> Unit,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        callStateMonitor: CallStateMonitor,
        callEndShownGate: CallEndShownGate,
    ) : this(
        resolver = CallSessionResolver(ContentResolverCallLogReader(context)),
        isFlagEnabled = OverlayFeatureFlag::isEnabled,
        canDrawOverlays = { Settings.canDrawOverlays(context) },
        isDeviceLocked = { context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true },
        isCallInProgress = { callStateMonitor.isCallInProgress },
        tryClaimLaunch = callEndShownGate::tryClaim,
        elapsedRealtime = SystemClock::elapsedRealtime,
        launch = { session ->
            BackgroundActivityLaunchOverlay.withVisibleOverlay(context) {
                CallEndActivity.start(context, session)
            }
        },
        log = { message, error ->
            if (error != null) Log.e(TAG, message, error) else if (BuildConfig.DEBUG) Log.d(TAG, message)
        },
    )

    /** False when a call ending must not lead to a launch at all -- [PhoneStateReceiver] checks
     * this before spending a `goAsync()` on [onCallEnded], which checks it again itself. */
    fun canLaunch(): Boolean {
        if (!isFlagEnabled()) {
            log("overlay/call-end feature flag is off, not launching", null)
            return false
        }
        if (!canDrawOverlays()) {
            log("draw-over-other-apps not granted, not launching", null)
            return false
        }
        return true
    }

    /** Never throws (other than for cancellation): a failed launch is logged and dropped. */
    suspend fun onCallEnded(signal: CallEndSignal) {
        if (!canLaunch()) return

        val idleAt = elapsedRealtime()
        val session = resolver.resolve(signal)
        log("resolved $session in ${elapsedRealtime() - idleAt}ms for $signal", null)
        if (session == null) return

        val isLocked = isDeviceLocked()
        log("isLocked=$isLocked", null)
        if (!isLocked) {
            val remaining = LAUNCH_DELAY_UNLOCKED_MILLIS - (elapsedRealtime() - idleAt)
            if (remaining > 0) delay(remaining)
        }

        // A new call started while this one's launch was pending -- don't open over its UI.
        if (isCallInProgress()) {
            log("another call is in progress, not launching", null)
            return
        }

        // PostCallActivity already opened it for this call end.
        if (!tryClaimLaunch()) {
            log("call-end screen already shown for this call, not launching", null)
            return
        }

        try {
            log("Calling CallEndActivity.start() for $session", null)
            launch(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // BAL-blocked, ActivityNotFound, WindowManager errors...
            log("CallEndActivity launch failed", e)
        }
    }
}
