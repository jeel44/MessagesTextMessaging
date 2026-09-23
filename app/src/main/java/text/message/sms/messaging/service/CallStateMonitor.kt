package text.message.sms.messaging.service

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallStateMonitor"

// TEMPORARY (real-call diagnostic): human-readable TelephonyManager.CALL_STATE_* names.
private fun stateName(state: Int): String = when (state) {
    TelephonyManager.CALL_STATE_IDLE -> "IDLE"
    TelephonyManager.CALL_STATE_RINGING -> "RINGING"
    TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
    else -> "UNKNOWN($state)"
}

/**
 * Turns raw [TelephonyManager] call-state transitions into a [CallSession] per completed call.
 *
 * State machine (see [CallSessions.onStateChanged]):
 * - IDLE -> RINGING: an incoming call starts ringing.
 * - RINGING -> OFFHOOK: that incoming call was answered.
 * - RINGING -> IDLE (no OFFHOOK in between): it was missed -- see [CallOutcome]'s doc for why this
 *   can't be distinguished from an active decline.
 * - IDLE -> OFFHOOK (no preceding RINGING): an outgoing call starts.
 * - OFFHOOK -> IDLE: whichever call was in progress just ended.
 *
 * On API 31+ this registers a [TelephonyCallback.CallStateListener]; below that (down to this
 * app's minSdk 26) it falls back to the deprecated [PhoneStateListener], the only API available.
 * Neither delivers `NEW_OUTGOING_CALL`-style broadcasts, which are deprecated and unreliable on
 * modern Android -- outgoing calls are detected purely from the IDLE->OFFHOOK transition instead.
 *
 * Both registration paths are pinned to [ContextCompat.getMainExecutor] (`TelephonyManager.listen`
 * otherwise delivers on whatever thread happens to call it), so every callback lands on the same
 * thread and the state kept inside [callSessions] never needs external synchronization -- the
 * same reasoning [ActiveThreadTracker]'s `@Volatile` field exists for, just enforced by thread
 * confinement here instead of a volatile field, since the mutable state below is local to the
 * flow's builder lambda, not a class property.
 *
 * The whole [callbackFlow] block is pinned to [Dispatchers.Main] via `flowOn` below for a second,
 * separate reason on top of that: the legacy [PhoneStateListener]'s no-arg constructor builds its
 * own internal `Handler` from `Looper.myLooper()`, which is only non-null on a thread that has
 * called `Looper.prepare()` -- the main thread, or a dedicated `HandlerThread`. [callSessions] has
 * no `flowOn` of its own to fall back on, so without this it runs on whatever dispatcher its
 * collector happens to use ([CallEndTriggerService] collects on `Dispatchers.Default`), and
 * constructing that listener there crashes immediately, every time, on any API < 31 device (this
 * app's minSdk 26 floor). The API 31+ [TelephonyCallback] branch was never affected by this --
 * its constructor touches no Looper/Handler, and delivery is routed through the explicit
 * [ContextCompat.getMainExecutor] argument to `registerTelephonyCallback` regardless of which
 * thread calls it -- but pinning the whole block is simpler than special-casing just one branch,
 * and costs nothing extra there.
 */
@Singleton
class CallStateMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telephonyManager: TelephonyManager,
) {

    /** Emits once per completed call, including a missed one (see [CallOutcome.MISSED]). Never
     * throws for a revoked runtime permission -- [SecurityException] from either registration path
     * is caught and logged, leaving the flow simply silent instead of crashing the collector. */
    fun callSessions(): Flow<CallSession> = callbackFlow {
        val sessions = CallSessionBuilder(context)

        val emit: (CallSession) -> Unit = { session -> trySend(session) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    sessions.onStateChanged(state, emit)
                }
            }
            try {
                telephonyManager.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_PHONE_STATE not granted, call state cannot be monitored", e)
            }
            awaitClose {
                try {
                    telephonyManager.unregisterTelephonyCallback(callback)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Failed to unregister TelephonyCallback", e)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    sessions.onStateChanged(state, emit)
                }
            }
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_PHONE_STATE not granted, call state cannot be monitored", e)
            }
            awaitClose {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }.flowOn(Dispatchers.Main)
}

/**
 * The actual state machine, split out of [CallStateMonitor.callSessions] so it can run on either
 * registration path unchanged. Not thread-safe on its own -- relies on every [onStateChanged] call
 * landing on the same thread, guaranteed by [CallStateMonitor] pinning both registration paths to
 * the main executor.
 */
private class CallSessionBuilder(private val context: Context) {

    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var direction: CallDirection? = null
    private var startedAt = 0L
    private var ringingStartedAt = 0L

    fun onStateChanged(state: Int, emit: (CallSession) -> Unit) {
        // Debounce: the platform can redeliver the same state (e.g. a second RINGING callback for
        // a multi-SIM device), which must never be mistaken for a real transition.
        if (state == lastState) return

        val now = System.currentTimeMillis()
        when {
            lastState == TelephonyManager.CALL_STATE_IDLE && state == TelephonyManager.CALL_STATE_RINGING -> {
                direction = CallDirection.INCOMING
                ringingStartedAt = now
            }

            lastState == TelephonyManager.CALL_STATE_RINGING && state == TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Answered -- talk time starts now, not when it started ringing.
                startedAt = now
            }

            lastState == TelephonyManager.CALL_STATE_RINGING && state == TelephonyManager.CALL_STATE_IDLE -> {
                emit(
                    CallSession(
                        phoneNumber = mostRecentCallLogNumber(),
                        direction = CallDirection.INCOMING,
                        startedAt = ringingStartedAt,
                        endedAt = now,
                        durationMillis = 0L,
                        outcome = CallOutcome.MISSED,
                    ),
                )
                direction = null
            }

            lastState == TelephonyManager.CALL_STATE_IDLE && state == TelephonyManager.CALL_STATE_OFFHOOK -> {
                direction = CallDirection.OUTGOING
                startedAt = now
            }

            lastState == TelephonyManager.CALL_STATE_OFFHOOK && state == TelephonyManager.CALL_STATE_IDLE -> {
                val endedDirection = direction
                if (endedDirection != null) {
                    emit(
                        CallSession(
                            phoneNumber = mostRecentCallLogNumber(),
                            direction = endedDirection,
                            startedAt = startedAt,
                            endedAt = now,
                            durationMillis = (now - startedAt).coerceAtLeast(0L),
                            outcome = CallOutcome.ANSWERED,
                        ),
                    )
                }
                direction = null
            }
        }
        lastState = state
    }

    /**
     * Best-effort number lookup. `TelephonyCallback`/`PhoneStateListener` no longer hand back
     * `EXTRA_INCOMING_NUMBER`-equivalent data on modern Android (privacy hardening), so the only
     * source left is the call log's own most recent row -- read right after the call ends. This
     * can race the provider's own write on some OEM builds (a known limitation, not fixable from
     * here); a miss just leaves [CallSession.phoneNumber] null, which callers must already handle.
     */
    private fun mostRecentCallLogNumber(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CALL_LOG revoked at runtime", e)
            null
        }
    }
}
