package text.message.sms.messaging.service

import android.annotation.SuppressLint
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
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
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

/** How often, while a call is in progress, [CallEndTriggerService] asks the platform whether
 * there's still actually a call -- the fail-safe for the call's IDLE broadcast never reaching us
 * (dropped, or the process was killed and restarted mid-call), which would otherwise leave the
 * service, and its notification, alive indefinitely. */
private const val IN_CALL_WATCHDOG_INTERVAL_MILLIS = 30_000L

/**
 * Short-lived foreground service started by [PhoneStateReceiver] for the span of a single call --
 * started at RINGING/OFFHOOK, launches [CallEndActivity] at IDLE (immediately if the device is
 * locked, after [LAUNCH_DELAY_UNLOCKED_MILLIS] if it's unlocked -- see [handleCallEnded]), then
 * stops itself ~1s after that launch. Every start command ends in exactly one of: a call in
 * progress (stay alive, guarded by [inCallWatchdog]), a completed call (launch, then stop -- even
 * if the launch throws), or a no-op (stop within [STOP_DELAY_MILLIS] unless a launch is pending);
 * none of them can leave the service running with no call active. Deliberately does NOT run continuously between calls: an earlier
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

    /** Most recent [onStartCommand] id. Stopping via [stopSelf] with this id (not the no-arg form)
     * means a start command already queued behind a pending stop keeps the service alive to handle
     * it, instead of it being destroyed before that command's `startForeground` -- which the
     * platform punishes with a crash for any `startForegroundService` start. */
    private var lastStartId = 0
    private val stopRunnable = Runnable {
        if (BuildConfig.DEBUG) Log.d(TAG, "stopSelf(startId=$lastStartId)")
        stopSelf(lastStartId)
    }

    private val inCallWatchdog = object : Runnable {
        override fun run() {
            if (isPlatformInCall() == false) {
                Log.w(TAG, "Call state stuck in-progress but platform reports no call -- stopping")
                stopRunnable.run()
            } else {
                stopHandler.postDelayed(this, IN_CALL_WATCHDOG_INTERVAL_MILLIS)
            }
        }
    }

    /** Non-null exactly while a delayed launch (see [LAUNCH_DELAY_UNLOCKED_MILLIS]) is pending --
     * distinct from [stopRunnable] so [cancelPendingWork] can cancel either or both independently
     * (a new call starting can race either kind of pending callback left by the previous one). */
    private var launchRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForegroundCompat()

        val rawState = intent?.getStringExtra(EXTRA_RAW_STATE)
        if (BuildConfig.DEBUG) Log.d(TAG, "onStartCommand startId=$startId rawState=$rawState")

        serviceScope.launch {
            val session = try {
                callStateMonitor.onPhoneStateChanged(rawState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "onPhoneStateChanged(rawState=$rawState) failed", e)
                null
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "onPhoneStateChanged(rawState=$rawState) returned $session")
            when {
                session != null -> handleCallEnded(session)

                callStateMonitor.isCallInProgress -> {
                    // A call is still in progress (RINGING/OFFHOOK) -- cancel any pending
                    // launch/stop left by a previous, already-completed call so this new one isn't
                    // cut short, and stay alive until its IDLE (or the watchdog says it's gone).
                    cancelPendingWork()
                    stopHandler.postDelayed(inCallWatchdog, IN_CALL_WATCHDOG_INTERVAL_MILLIS)
                }

                // Nothing happened: a duplicate/redelivered broadcast, or one CallStateMonitor
                // deliberately ignored (its post-call cooldown). Must NOT cancel pending work --
                // that pending stop may be the only thing that will ever end this service -- and
                // if nothing is pending (no launch about to schedule its own stop), schedule one.
                launchRunnable == null -> scheduleStop()
            }
        }

        return START_NOT_STICKY
    }

    /** (Re)schedules [stopRunnable] [STOP_DELAY_MILLIS] from now -- never more than one pending. */
    private fun scheduleStop() {
        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.postDelayed(stopRunnable, STOP_DELAY_MILLIS)
    }

    private fun handleCallEnded(session: CallSession) {
        cancelPendingWork()

        val canDrawOverlays = Settings.canDrawOverlays(this)
        if (BuildConfig.DEBUG) Log.d(TAG, "canDrawOverlays=$canDrawOverlays")
        if (!canDrawOverlays) {
            scheduleStop()
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
        try {
            BackgroundActivityLaunchOverlay.withTransientOverlay(this) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Calling CallEndActivity.start() for $session")
                CallEndActivity.start(this, session)
            }
        } catch (e: Exception) {
            // Launch failed (BAL-blocked, ActivityNotFound, WindowManager errors...) -- the stop
            // below must still be scheduled either way, or the service never ends.
            Log.e(TAG, "CallEndActivity launch failed", e)
        } finally {
            scheduleStop()
        }
    }

    private fun cancelPendingWork() {
        launchRunnable?.let { stopHandler.removeCallbacks(it) }
        launchRunnable = null
        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.removeCallbacks(inCallWatchdog)
    }

    /** Device-wide (all SIMs), unlike `TelephonyManager.getCallStateForSubscription`. Null if it
     * can't be determined (READ_PHONE_STATE revoked) -- the watchdog then leaves the service alone. */
    @SuppressLint("MissingPermission")
    private fun isPlatformInCall(): Boolean? = try {
        getSystemService(TelecomManager::class.java)?.isInCall
    } catch (e: SecurityException) {
        null
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
