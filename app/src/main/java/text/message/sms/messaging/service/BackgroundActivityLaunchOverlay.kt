package text.message.sms.messaging.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import text.message.sms.messaging.BuildConfig

private const val TAG = "BackgroundActivityLaunchOverlay"

/**
 * Android 12+'s background-activity-launch (BAL) restrictions can block even a legitimate launch
 * from a running foreground service unless the caller currently holds a BAL exemption. Holding
 * [android.permission.SYSTEM_ALERT_WINDOW] grants one (`BAL_ALLOW_SAW_PERMISSION`), but isn't
 * reliably fresh/timely in the OS's eyes on every OEM/version combination -- attaching a
 * transient, invisible 1x1 [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] window right
 * before the launch grants a `BAL_ALLOW_VISIBLE_WINDOW`-class exemption independent of that.
 *
 * Used by [PhoneStateReceiver] (bracketing the call that starts [CallEndTriggerService]) and by
 * [CallEndTriggerService] itself (bracketing the call that launches
 * [text.message.sms.messaging.ui.screens.callend.CallEndActivity]).
 */
object BackgroundActivityLaunchOverlay {

    /**
     * Runs [block] with the transient overlay attached for its duration. If
     * [Settings.canDrawOverlays] is false, or attaching the window otherwise fails, [block] still
     * runs -- this is a best-effort BAL exemption, not a precondition for the launch it wraps.
     */
    fun withTransientOverlay(context: Context, block: () -> Unit) {
        if (!Settings.canDrawOverlays(context)) {
            block()
            return
        }
        val windowManager = context.getSystemService(WindowManager::class.java)
        val view = View(context)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        val attached = try {
            windowManager.addView(view, params)
            true
        } catch (e: RuntimeException) {
            // WindowManager.addView throws BadTokenException/InvalidDisplayException as
            // RuntimeExceptions on OEM/version combinations that reject this window for reasons
            // unrelated to whether the launch itself is legitimate.
            false
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Transient overlay attached=$attached")
        try {
            block()
        } finally {
            if (attached) windowManager.removeView(view)
        }
    }
}
