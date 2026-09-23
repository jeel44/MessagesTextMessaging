package text.message.sms.messaging.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.CallSession
import text.message.sms.messaging.ui.screens.callend.CallEndActivity
import javax.inject.Inject

private const val TAG = "CallEndTriggerService"
private const val NOTIFICATION_ID_FOREGROUND = 9001
private const val EXTRA_RAW_STATE = "extra_raw_state"
private const val STOP_DELAY_MILLIS = 1_000L

/** How long to wait before launching [CallEndActivity] when the device is unlocked -- long
 * enough for the in-call UI/dialer to finish tearing itself down first, so the call-end screen
 * doesn't visually collide with it. Skipped entirely when the device is locked (see
 * [handleCallEnded]): there's no competing UI to wait out, and delaying only means a longer
 * stretch showing the bare lock screen instead. */
private const val LAUNCH_DELAY_UNLOCKED_MILLIS = 1_000L

/**
 * Short-lived foreground service started by [PhoneStateReceiver] for the span of a single call --
 * started at RINGING/OFFHOOK, launches [CallEndActivity] at IDLE (immediately if the device is
 * locked, after [LAUNCH_DELAY_UNLOCKED_MILLIS] if it's unlocked -- see [handleCallEnded]), then
 * stops itself ~1s after that launch. Deliberately does NOT run continuously between calls: an earlier
 * design kept [CallStateMonitor]'s listener registered inside a persistent foreground service, and
 * that service's process was observed getting frozen by the OS within seconds of the app being
 * backgrounded -- even while the foreground service was still running -- so a call ending while
 * frozen was silently never detected. Nothing here runs, or needs to run, while no call is active.
 *
 * [android.permission.FOREGROUND_SERVICE_PHONE_CALL] + `foregroundServiceType="phoneCall"`
 * (manifest) are what let this run as an FGS on API 34+ for this use case --
 * [android.permission.MANAGE_OWN_CALLS] (an install-time permission, no dialer role needed) is
 * what makes the "phoneCall" type available to this app despite it never actually placing or
 * managing a call itself, only watching `PHONE_STATE` broadcasts to know when one ended.
 *
 * Every step of the success path is logged under [BuildConfig.DEBUG] -- this class and
 * [PhoneStateReceiver] previously had none, which made a real on-device failure (call detected
 * fine, but no evidence either way of whether [CallEndActivity.start] was ever actually reached)
 * impossible to diagnose from logs alone. Don't remove this logging without a replacement.
 */
@AndroidEntryPoint
class CallEndTriggerService : Service() {

    @Inject
    lateinit var callStateMonitor: CallStateMonitor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }

    /** Non-null exactly while a delayed launch (see [LAUNCH_DELAY_UNLOCKED_MILLIS]) is pending --
     * distinct from [stopRunnable] so [cancelPendingWork] can cancel either or both independently
     * (a new call starting can race either kind of pending callback left by the previous one). */
    private var launchRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        val rawState = intent?.getStringExtra(EXTRA_RAW_STATE)
        if (BuildConfig.DEBUG) Log.d(TAG, "onStartCommand rawState=$rawState")

        serviceScope.launch {
            val session = callStateMonitor.onPhoneStateChanged(rawState)
            if (BuildConfig.DEBUG) Log.d(TAG, "onPhoneStateChanged(rawState=$rawState) returned $session")
            if (session != null) {
                handleCallEnded(session)
            } else {
                // A call is still in progress (RINGING/OFFHOOK) -- cancel any pending launch/stop
                // left by a previous, already-completed call so this new one isn't cut short.
                cancelPendingWork()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleCallEnded(session: CallSession) {
        cancelPendingWork()

        val canDrawOverlays = Settings.canDrawOverlays(this)
        if (BuildConfig.DEBUG) Log.d(TAG, "canDrawOverlays=$canDrawOverlays")
        if (!canDrawOverlays) {
            stopHandler.postDelayed(stopRunnable, STOP_DELAY_MILLIS)
            return
        }

        // Locked: launch immediately, there's no competing in-call/dialer UI to wait out and a
        // delay would only mean longer showing the bare lock screen. Unlocked: wait, see
        // LAUNCH_DELAY_UNLOCKED_MILLIS's own doc comment.
        val isLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (BuildConfig.DEBUG) Log.d(TAG, "isLocked=$isLocked")
        if (isLocked) {
            launchThenScheduleStop(session)
        } else {
            val runnable = Runnable { launchThenScheduleStop(session) }
            launchRunnable = runnable
            stopHandler.postDelayed(runnable, LAUNCH_DELAY_UNLOCKED_MILLIS)
        }
    }

    private fun launchThenScheduleStop(session: CallSession) {
        launchRunnable = null
        BackgroundActivityLaunchOverlay.withTransientOverlay(this) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Calling CallEndActivity.start() for $session")
            CallEndActivity.start(this, session)
        }
        stopHandler.postDelayed(stopRunnable, STOP_DELAY_MILLIS)
    }

    private fun cancelPendingWork() {
        launchRunnable?.let { stopHandler.removeCallbacks(it) }
        launchRunnable = null
        stopHandler.removeCallbacks(stopRunnable)
    }

    override fun onDestroy() {
        cancelPendingWork()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    /** Minimal, silent -- the platform requires some notification for any foreground service,
     * but there is nothing here worth surfacing to the user (see [NotificationChannels.CALL_MONITOR]). */
    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CALL_MONITOR)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(getString(R.string.call_monitor_notification_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        /** Called only from [PhoneStateReceiver], once per `PHONE_STATE` broadcast -- starts (or
         * re-uses, if already running for this same call) the service and passes the raw state
         * straight through to [CallStateMonitor.onPhoneStateChanged]. */
        fun onPhoneStateChanged(context: Context, rawState: String?) {
            val intent = Intent(context, CallEndTriggerService::class.java)
                .putExtra(EXTRA_RAW_STATE, rawState)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
