package text.message.sms.messaging.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import text.message.sms.messaging.BuildConfig
import kotlin.coroutines.resume

private const val TAG = "BackgroundActivityLaunchOverlay"

/** Upper bound on waiting for the overlay window to become visible before launching anyway. Hit
 * when the window never draws at all (e.g. some devices while the screen is off). */
private const val OVERLAY_VISIBLE_TIMEOUT_MILLIS = 500L

/** Gap left between the overlay's first frame being committed and the launch. The app can observe
 * its own frame, but not the moment WindowManager marks the window's surface as shown, which
 * follows on WindowManager's next layout pass -- there is no callback for that, so this is a
 * deliberate few-frames allowance rather than a confirmed signal. */
private const val OVERLAY_SETTLE_MILLIS = 50L

/**
 * Android 10+'s background-activity-launch (BAL) restrictions drop an activity start from a
 * background process unless the caller holds an exemption. Holding
 * [android.permission.SYSTEM_ALERT_WINDOW] grants one (`BAL_ALLOW_SAW_PERMISSION`), but isn't
 * reliably fresh/timely in the OS's eyes on every OEM/version combination -- having a visible
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] window of this app's on screen at the
 * moment of the launch grants a visible-window exemption independent of that.
 *
 * "Visible" is the operative word: `WindowManager.addView` only registers the window, its surface
 * isn't shown until its first frame has been drawn, which happens asynchronously on a later
 * main-thread pass. Launching straight after `addView` (what this used to do) therefore ran
 * before the window could count. [withVisibleOverlay] waits for the first frame instead.
 *
 * Used by [CallEndLauncher], around the launch of
 * [text.message.sms.messaging.ui.screens.callend.CallEndActivity].
 */
object BackgroundActivityLaunchOverlay {

    /**
     * Attaches a transient, invisible 1x1 overlay window, waits until its first frame is on
     * screen (bounded by [OVERLAY_VISIBLE_TIMEOUT_MILLIS]), runs [block], then removes it.
     *
     * Best-effort, not a precondition: if [Settings.canDrawOverlays] is false, attaching the
     * window fails, or it never draws in time, [block] still runs. Callers that must not launch
     * without the permission check it themselves (see [CallEndLauncher.canLaunch]).
     */
    suspend fun withVisibleOverlay(context: Context, block: () -> Unit) {
        if (!Settings.canDrawOverlays(context)) {
            block()
            return
        }
        // Window operations belong to the thread that added the view, and its first frame is
        // produced by that thread's looper -- so this must not block it while waiting.
        withContext(Dispatchers.Main.immediate) {
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
            try {
                val visible = attached && withTimeoutOrNull(OVERLAY_VISIBLE_TIMEOUT_MILLIS) {
                    view.awaitFirstFrame()
                    delay(OVERLAY_SETTLE_MILLIS)
                } != null
                if (BuildConfig.DEBUG) Log.d(TAG, "Transient overlay attached=$attached visible=$visible")
                block()
            } finally {
                if (attached) {
                    try {
                        windowManager.removeView(view)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Removing the transient overlay failed", e)
                    }
                }
            }
        }
    }

    /** Suspends until this view's window has drawn a frame. Registered through the view's
     * [ViewTreeObserver] before it's attached, which the platform merges into the window's own
     * observer on attach. */
    private suspend fun View.awaitFirstFrame() = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Fires once the frame has actually been committed, then unregisters itself.
            viewTreeObserver.registerFrameCommitCallback {
                if (continuation.isActive) continuation.resume(Unit)
            }
        } else {
            // No commit callback before API 29: onDraw fires as the first draw starts, and a
            // posted message runs once that draw pass has finished. The listener can't be
            // removed from inside onDraw (the platform throws), hence removing it in the post.
            val listener = object : ViewTreeObserver.OnDrawListener {
                private var fired = false
                override fun onDraw() {
                    if (fired) return
                    fired = true
                    val listener = this
                    post {
                        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnDrawListener(listener)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            viewTreeObserver.addOnDrawListener(listener)
        }
    }
}
