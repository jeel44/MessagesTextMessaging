package text.message.sms.messaging.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallStateMonitor"

/** Minimum gap after a call completes before a fresh IDLE->RINGING/OFFHOOK transition is
 * trusted as the start of a genuinely new call -- see [onPhoneStateChanged]'s cooldown check for
 * why this exists. Real back-to-back calls (hang up, immediately dial again) taking under 2s
 * between them are effectively never going to happen in practice, so this trades away that
 * theoretical case for rejecting the OEM noise it was added for. */
private const val CALL_START_COOLDOWN_MILLIS = 2_000L

/**
 * Turns [android.intent.action.PHONE_STATE] broadcast extras into a [CallSession] per completed
 * call. Driven by [PhoneStateReceiver] via [onPhoneStateChanged] -- one call per broadcast, not a
 * live listener kept registered for the process's whole lifetime (the earlier design: a
 * [android.telephony.TelephonyCallback]/[android.telephony.PhoneStateListener] registered inside
 * a persistent foreground service). That service's process was observed getting frozen by the OS
 * within seconds of the app being backgrounded -- even while the foreground service was still
 * running -- so a call ending while frozen was silently never detected at all. Routing through the
 * exempted `PHONE_STATE` implicit broadcast instead means nothing needs to stay alive between
 * calls: the OS cold-starts the process fresh for each one.
 *
 * Being [Singleton] is what lets [lastState]/[direction]/[startedAt] persist across the several
 * broadcasts (RINGING -> OFFHOOK -> IDLE) that make up a single call -- correct as long as this
 * process survives that short span, same as it did before. If the OS kills the process *between*
 * two broadcasts of the same call (a real but accepted risk, same tradeoff the reference design
 * this was ported from accepts), the next broadcast simply starts over from
 * [TelephonyManager.CALL_STATE_IDLE], misreading that one transition -- it does not crash, and it
 * has no effect on any other call.
 *
 * State machine:
 * - IDLE -> RINGING: an incoming call starts ringing.
 * - RINGING -> OFFHOOK: that incoming call was answered.
 * - RINGING -> IDLE (no OFFHOOK in between): it was missed -- see [CallOutcome]'s doc for why this
 *   can't be distinguished from an active decline.
 * - IDLE -> OFFHOOK (no preceding RINGING): an outgoing call starts.
 * - OFFHOOK -> IDLE: whichever call was in progress just ended -- the only transition
 *   [onPhoneStateChanged] returns non-null for.
 */
@Singleton
class CallStateMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var direction: CallDirection? = null
    private var startedAt = 0L
    private var ringingStartedAt = 0L
    private var lastCallEndedAt = 0L

    /**
     * [rawState] is `PHONE_STATE`'s [TelephonyManager.EXTRA_STATE] extra as-is -- one of
     * [TelephonyManager.EXTRA_STATE_IDLE]/[TelephonyManager.EXTRA_STATE_RINGING]/
     * [TelephonyManager.EXTRA_STATE_OFFHOOK], or null/anything else, which is treated as IDLE.
     * Returns a [CallSession] only on the transition that completes a call (RINGING->IDLE or
     * OFFHOOK->IDLE); every other transition, including a redelivered repeat of the current state
     * (the platform can do this, e.g. a second RINGING broadcast on a multi-SIM device), returns
     * null.
     *
     * Suspends only because [mostRecentCallLogNumber] does (a real `ContentResolver` query, moved
     * off whatever thread calls this -- see its own doc comment); the state-machine logic itself
     * is synchronous and this returns as soon as its caller's dispatcher lets it run.
     */
    /** True from a call's RINGING/OFFHOOK until the IDLE that completes it. [CallEndTriggerService]
     * uses this to tell "call still in progress, stay alive" apart from every other null result of
     * [onPhoneStateChanged] (duplicate/ignored broadcasts), which must not keep it alive. */
    val isCallInProgress: Boolean
        get() = lastState != TelephonyManager.CALL_STATE_IDLE

    /** True if [rawState] would be a pure no-op for [onPhoneStateChanged]: an IDLE broadcast while
     * no call is being tracked (boot, SIM/radio changes, per-SIM duplicates, or the process having
     * been killed mid-call so the preceding RINGING/OFFHOOK was lost). [PhoneStateReceiver] skips
     * starting [CallEndTriggerService] entirely for these -- starting it posts the FGS notification. */
    fun isIdleNoOp(rawState: String?): Boolean =
        toCallState(rawState) == TelephonyManager.CALL_STATE_IDLE && !isCallInProgress

    private fun toCallState(rawState: String?): Int = when (rawState) {
        TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
        TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
        else -> TelephonyManager.CALL_STATE_IDLE
    }

    suspend fun onPhoneStateChanged(rawState: String?): CallSession? {
        val state = toCallState(rawState)
        if (state == lastState) return null

        val now = System.currentTimeMillis()

        // Observed on-device (Samsung One UI, real call): a spurious extra state broadcast
        // landing within ~1s of a call's real IDLE, shaped exactly like a new call starting
        // (IDLE->OFFHOOK), which then produced a second, bogus completed CallSession about a
        // second later. Root cause on the OEM side isn't confirmed, but treating anything
        // shaped like a fresh call start within CALL_START_COOLDOWN_MILLIS of the last real
        // call end as noise -- ignored entirely, no state mutated -- reproducibly suppresses it.
        if (lastState == TelephonyManager.CALL_STATE_IDLE &&
            state != TelephonyManager.CALL_STATE_IDLE &&
            now - lastCallEndedAt < CALL_START_COOLDOWN_MILLIS
        ) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Ignoring state=$state, ${now - lastCallEndedAt}ms after previous call end (cooldown)")
            }
            return null
        }

        // Commit the new state (and, for a completing transition, lastCallEndedAt) BEFORE the
        // suspending call-log lookup below: a duplicate IDLE broadcast handled while that lookup
        // is in flight must see the call as already ended, not complete it a second time.
        val previousState = lastState
        lastState = state
        val completesCall = state == TelephonyManager.CALL_STATE_IDLE &&
            (previousState == TelephonyManager.CALL_STATE_RINGING ||
                (previousState == TelephonyManager.CALL_STATE_OFFHOOK && direction != null))
        if (completesCall) lastCallEndedAt = now

        return when {
            previousState == TelephonyManager.CALL_STATE_IDLE && state == TelephonyManager.CALL_STATE_RINGING -> {
                direction = CallDirection.INCOMING
                ringingStartedAt = now
                null
            }

            previousState == TelephonyManager.CALL_STATE_RINGING && state == TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Answered -- talk time starts now, not when it started ringing.
                startedAt = now
                null
            }

            previousState == TelephonyManager.CALL_STATE_RINGING && state == TelephonyManager.CALL_STATE_IDLE -> {
                direction = null
                CallSession(
                    phoneNumber = mostRecentCallLogNumber(),
                    direction = CallDirection.INCOMING,
                    startedAt = ringingStartedAt,
                    endedAt = now,
                    durationMillis = 0L,
                    outcome = CallOutcome.MISSED,
                )
            }

            previousState == TelephonyManager.CALL_STATE_IDLE && state == TelephonyManager.CALL_STATE_OFFHOOK -> {
                direction = CallDirection.OUTGOING
                startedAt = now
                null
            }

            previousState == TelephonyManager.CALL_STATE_OFFHOOK && state == TelephonyManager.CALL_STATE_IDLE -> {
                val endedDirection = direction
                direction = null
                endedDirection?.let {
                    CallSession(
                        phoneNumber = mostRecentCallLogNumber(),
                        direction = it,
                        startedAt = startedAt,
                        endedAt = now,
                        durationMillis = (now - startedAt).coerceAtLeast(0L),
                        outcome = CallOutcome.ANSWERED,
                    )
                }
            }

            else -> null
        }
    }

    /**
     * Best-effort number lookup. `PHONE_STATE`'s `EXTRA_INCOMING_NUMBER` extra no longer carries
     * real data on modern Android (privacy hardening), so the only source left is the call log's
     * own most recent row -- read right after the call ends. This can race the provider's own
     * write on some OEM builds (a known limitation, not fixable from here); a miss just leaves
     * [CallSession.phoneNumber] null, which callers must already handle.
     *
     * `withContext(Dispatchers.IO)`: [onPhoneStateChanged] is called from [CallEndTriggerService]
     * on its main-thread coroutine scope, and this is a real synchronous `ContentResolver` query
     * -- without this it triggers `StrictMode`'s `DiskReadViolation` on the main thread.
     */
    private suspend fun mostRecentCallLogNumber(): String? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }
        try {
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
