package text.message.sms.messaging.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.MainActivity
import text.message.sms.messaging.R
import text.message.sms.messaging.domain.model.CallSession
import javax.inject.Inject

private const val TAG = "CallEndTriggerService"
private const val NOTIFICATION_ID_FOREGROUND = 9001
private const val NOTIFICATION_TAG_CALL_END = "call_end"

/**
 * Foreground service whose only job is to keep [CallStateMonitor] alive and, on each
 * [CallSession] it emits, post a full-screen-intent notification that launches
 * [text.message.sms.messaging.ui.screens.callend.CallEndScreen].
 *
 * Started from [text.message.sms.messaging.MessagingApplication.onCreate] (only if
 * READ_PHONE_STATE is granted -- see [start]), so it's already running by the time a call comes
 * in, independent of whether the app's UI is in the foreground. [android.permission
 * .FOREGROUND_SERVICE_SPECIAL_USE] + `foregroundServiceType="specialUse"` (manifest, with a
 * `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justification) are what let this run as an FGS on API 34+ --
 * NOT the "phoneCall" type, which the platform restricts to apps holding MANAGE_OWN_CALLS or the
 * system dialer role; this service only watches call-state transitions, it never places or
 * manages a call, so it doesn't qualify for that type and starting it as one is rejected with a
 * SecurityException. The `startForeground` call below only passes a type on API 29+, where the
 * typed overload exists.
 *
 * `USE_FULL_SCREEN_INTENT` is "special access" on API 34+ and is not expected to be auto-granted
 * to a messaging app (only default-dialer/CALL-category apps get that automatically) -- see
 * Settings' Call alerts explanation for the manual opt-in path. If it isn't granted, the
 * notification this posts still appears, just as a normal heads-up/tray notification instead of a
 * full-screen launch; nothing here crashes or behaves differently either way.
 */
@AndroidEntryPoint
class CallEndTriggerService : Service() {

    @Inject
    lateinit var callStateMonitor: CallStateMonitor

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        // Guards against re-subscribing to callSessions() on every onStartCommand -- the service
        // can be restarted (START_STICKY) or re-started redundantly (MessagingApplication calls
        // start() once per process start) without ever tearing this instance down first.
        if (collectJob == null) {
            collectJob = serviceScope.launch {
                callStateMonitor.callSessions().collect { session ->
                    if (BuildConfig.DEBUG) Log.d(TAG, "CallSession: $session")
                    try {
                        postCallEndNotification(session)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "postCallEndNotification threw", e)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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

    private fun postCallEndNotification(session: CallSession) {
        // A silent notify() no-op (POST_NOTIFICATIONS revoked on API 33+, or the channel disabled
        // by the user) would otherwise look identical to this method never being called at all.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "areNotificationsEnabled=${notificationManager.areNotificationsEnabled()}")
        }
        val pendingIntent = callEndPendingIntent(session)
        val notification = NotificationCompat.Builder(this, NotificationChannels.CALL_END)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(getString(R.string.call_end_notification_title))
            .setContentText(session.phoneNumber ?: getString(R.string.call_end_unknown_caller))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        notificationManager.notify(NOTIFICATION_TAG_CALL_END, notificationId(session), notification)
    }

    /** [MainActivity] reads these back the same way it already reads [MainActivity.EXTRA_THREAD_ID]
     * -- plain primitive extras, no Parcelable, decoded into a [CallSession] by
     * `Intent.callSessionExtra()`. */
    private fun callEndPendingIntent(session: CallSession): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_IS_CALL_END, true)
            .putExtra(MainActivity.EXTRA_CALL_PHONE_NUMBER, session.phoneNumber)
            .putExtra(MainActivity.EXTRA_CALL_DIRECTION, session.direction.name)
            .putExtra(MainActivity.EXTRA_CALL_OUTCOME, session.outcome.name)
            .putExtra(MainActivity.EXTRA_CALL_STARTED_AT, session.startedAt)
            .putExtra(MainActivity.EXTRA_CALL_ENDED_AT, session.endedAt)
            .putExtra(MainActivity.EXTRA_CALL_DURATION_MILLIS, session.durationMillis)
        return PendingIntent.getActivity(
            this,
            notificationId(session),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** [CallSession.endedAt] is unique per call (two calls can't end at the same millisecond),
     * so it doubles as both the notification id and the PendingIntent request code -- no separate
     * counter needed, and no risk of one call's notification/intent aliasing another's. */
    private fun notificationId(session: CallSession): Int = session.endedAt.hashCode()

    companion object {
        /** Starts (or re-starts, harmlessly -- see [onStartCommand]) the service. Called from
         * [text.message.sms.messaging.MessagingApplication.onCreate], gated on READ_PHONE_STATE
         * being granted -- there's nothing for this service to do without it, and starting a
         * foreground service that immediately fails to register its telephony callback would just
         * be a permanent, pointless notification. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CallEndTriggerService::class.java))
        }
    }
}
