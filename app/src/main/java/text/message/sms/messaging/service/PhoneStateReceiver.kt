package text.message.sms.messaging.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.config.OverlayFeatureFlag
import text.message.sms.messaging.di.ApplicationScope
import javax.inject.Inject

private const val TAG = "PhoneStateReceiver"

/** Hard cap on the `goAsync()` work for one call end. The platform ANRs a receiver that holds its
 * broadcast for ~10s; [CallEndLauncher.onCallEnded]'s own worst case is about 4s (3s of call-log
 * polling, which the 1s unlocked launch delay overlaps, plus 0.5s waiting on the overlay window),
 * so this only ever fires if something underneath it hangs -- a stuck call-log provider query,
 * say -- and then gives the broadcast back with time to spare rather than launching late. */
private const val ASYNC_BUDGET_MILLIS = 8_000L

/**
 * Manifest-registered receiver for [TelephonyManager.ACTION_PHONE_STATE_CHANGED]
 * (`android.intent.action.PHONE_STATE`) -- one of the implicit broadcasts still exempt from the
 * API 26+ restriction on manifest-declared receivers (same category as this app's own
 * `BOOT_COMPLETED` receiver), so the OS cold-starts this app's process and dispatches straight
 * here whenever a call's state changes, with nothing needing to already be running beforehand
 * (see [text.message.sms.messaging.MessagingApplication.onCreate], which bootstraps no
 * call-monitoring component at all).
 *
 * Receiving this broadcast only requires holding [android.permission.READ_PHONE_STATE] (already
 * declared) -- no `android:permission` attribute needed on the manifest `<receiver>` tag. A
 * "Permission Denial ... requires READ_PRIVILEGED_PHONE_STATE" logged against this receiver by
 * `BroadcastQueue` is expected noise, not a failure: the platform additionally attempts delivery
 * of a privileged variant of this broadcast (carrying the incoming number) that no third-party
 * app can receive, logging a denial for it regardless of whether the plain variant -- state only,
 * all this receiver needs -- was delivered successfully.
 *
 * This receiver is the whole trigger: RINGING/OFFHOOK broadcasts only update
 * [CallStateMonitor]'s in-memory state and return; the IDLE that ends a call runs
 * [CallEndLauncher.onCallEnded] inside a `goAsync()` window, capped at [ASYNC_BUDGET_MILLIS].
 * No service is started at any point, so nothing is posted to the notification shade and nothing
 * of this app's is kept alive during the call.
 */
@AndroidEntryPoint
class PhoneStateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var callStateMonitor: CallStateMonitor

    @Inject
    lateinit var callEndLauncher: CallEndLauncher

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        // Call-end disabled: no call tracking and no call-end screen.
        if (!OverlayFeatureFlag.isEnabled()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Ignoring: overlay/call-end feature flag is off")
            return
        }
        val rawState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val signal = callStateMonitor.onPhoneStateChanged(rawState)
        if (BuildConfig.DEBUG) Log.d(TAG, "onReceive rawState=$rawState -> $signal")
        if (signal == null) return
        // Without "draw over other apps" the launch can't happen; don't hold the broadcast for it.
        if (!callEndLauncher.canLaunch()) return

        val pendingResult = goAsync()
        val work = applicationScope.launch { callEndLauncher.onCallEnded(signal) }
        // Joined from a second coroutine rather than wrapping the work itself in the timeout: a
        // timeout around the work would still wait for a blocking call inside it to return.
        applicationScope.launch {
            try {
                if (withTimeoutOrNull(ASYNC_BUDGET_MILLIS) { work.join() } == null) {
                    Log.w(TAG, "Call-end work exceeded ${ASYNC_BUDGET_MILLIS}ms, abandoning it")
                    work.cancel()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
