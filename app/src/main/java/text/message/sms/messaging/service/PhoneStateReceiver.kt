package text.message.sms.messaging.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import text.message.sms.messaging.BuildConfig
import javax.inject.Inject

private const val TAG = "PhoneStateReceiver"

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
 * Does no state-machine or launch work itself: a manifest receiver's [onReceive] has a short
 * execution budget (the platform ANRs it otherwise), so this just decodes the extra and hands off
 * to [CallEndTriggerService], which has no such tight budget.
 *
 * IDLE broadcasts while no call is being tracked (see [CallStateMonitor.isIdleNoOp]) are dropped
 * here rather than forwarded: every forward is a `startForegroundService`, which posts the FGS
 * notification, and there's nothing for the service to do for them.
 */
@AndroidEntryPoint
class PhoneStateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var callStateMonitor: CallStateMonitor

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val rawState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (BuildConfig.DEBUG) Log.d(TAG, "onReceive rawState=$rawState")
        if (callStateMonitor.isIdleNoOp(rawState)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Ignoring IDLE with no call in progress")
            return
        }
        BackgroundActivityLaunchOverlay.withTransientOverlay(context) {
            CallEndTriggerService.onPhoneStateChanged(context, rawState)
        }
    }
}
