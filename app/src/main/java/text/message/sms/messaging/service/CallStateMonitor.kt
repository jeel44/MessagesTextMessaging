package text.message.sms.messaging.service

import android.telephony.TelephonyManager
import android.util.Log
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallStateMonitor"

/** Minimum gap after a call completes before a fresh IDLE->RINGING/OFFHOOK transition is
 * trusted as the start of a genuinely new call -- see [CallStateMonitor.onCallState]'s cooldown
 * check for why this exists. Real back-to-back calls (hang up, immediately dial again) taking
 * under 2s between them are effectively never going to happen in practice, so this trades away
 * that theoretical case for rejecting the OEM noise it was added for. */
private const val CALL_START_COOLDOWN_MILLIS = 2_000L

/**
 * "A call just ended", as far as one `PHONE_STATE` IDLE broadcast can tell -- what
 * [CallStateMonitor] hands [CallEndLauncher], which then asks the call log for the call's real
 * details (see [CallSessionResolver]).
 *
 * @param endedAt Epoch millis of the IDLE broadcast.
 * @param observedStartAt Epoch millis of the first RINGING/OFFHOOK broadcast this process saw for
 * the call, or null if it saw none: the process was killed mid-call and cold-started by this very
 * IDLE, so nothing about the call survives in memory.
 * @param observed What the in-memory state machine made of the call (always without a phone
 * number) -- only a fallback for when the call log has no usable row. Null exactly when
 * [observedStartAt] is.
 */
data class CallEndSignal(
    val endedAt: Long,
    val observedStartAt: Long?,
    val observed: CallSession?,
)

/**
 * Turns [android.intent.action.PHONE_STATE] broadcast extras into a [CallEndSignal] per completed
 * call. Driven by [PhoneStateReceiver] via [onPhoneStateChanged] -- one call per broadcast, not a
 * live listener kept registered for the process's whole lifetime (the earliest design: a
 * [android.telephony.TelephonyCallback]/[android.telephony.PhoneStateListener] registered inside
 * a persistent foreground service). That service's process was observed getting frozen by the OS
 * within seconds of the app being backgrounded -- even while the foreground service was still
 * running -- so a call ending while frozen was silently never detected at all. Routing through the
 * exempted `PHONE_STATE` implicit broadcast instead means nothing needs to stay alive between
 * calls: the OS cold-starts the process fresh for each one.
 *
 * Purely in-memory, and deliberately not the source of truth for a call's details: nothing keeps
 * this process alive between a call's RINGING/OFFHOOK and its IDLE (there's no foreground service
 * any more), so it can be killed mid-call and lose everything here. Being [Singleton] lets
 * [lastState]/[direction]/[startedAt] persist across the broadcasts of a single call when the
 * process does survive, which is only used (a) to tell [CallSessionResolver] when the call
 * started, so it can recognise the call's own call-log row, and (b) as the fallback session when
 * the call log has nothing usable. When the process didn't survive, the IDLE arrives with no call
 * tracked ("orphan IDLE") and the call log alone decides whether a call really just ended.
 *
 * State machine:
 * - IDLE -> RINGING: an incoming call starts ringing.
 * - RINGING -> OFFHOOK: that incoming call was answered.
 * - RINGING -> IDLE (no OFFHOOK in between): it was missed.
 * - IDLE -> OFFHOOK (no preceding RINGING): an outgoing call starts.
 * - OFFHOOK -> IDLE: whichever call was in progress just ended.
 * - IDLE -> IDLE: an orphan IDLE, see above.
 *
 * Only ever driven from [PhoneStateReceiver.onReceive] (main thread); [isCallInProgress] is the
 * one member read from elsewhere, hence [lastState] being volatile. [log] is a parameter so unit
 * tests run without android.util.Log.
 */
@Singleton
class CallStateMonitor internal constructor(
    private val log: (message: String) -> Unit,
) {

    @Inject
    constructor() : this({ message -> if (BuildConfig.DEBUG) Log.d(TAG, message) })

    @Volatile
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var direction: CallDirection? = null
    private var firstSeenAt = 0L
    private var startedAt = 0L
    private var ringingStartedAt = 0L
    private var lastCallEndedAt = 0L
    private var lastOrphanIdleAt = 0L

    /** True from a call's RINGING/OFFHOOK until the IDLE that completes it. [CallEndLauncher]
     * uses this to drop a pending launch when a new call has started in the meantime. */
    val isCallInProgress: Boolean
        get() = lastState != TelephonyManager.CALL_STATE_IDLE

    /**
     * [rawState] is `PHONE_STATE`'s [TelephonyManager.EXTRA_STATE] extra as-is -- one of
     * [TelephonyManager.EXTRA_STATE_IDLE]/[TelephonyManager.EXTRA_STATE_RINGING]/
     * [TelephonyManager.EXTRA_STATE_OFFHOOK], or null/anything else, which is treated as IDLE.
     * Returns a [CallEndSignal] only for a broadcast that may have completed a call; every other
     * one, including a redelivered repeat of a RINGING/OFFHOOK state (the platform can do this,
     * e.g. a second RINGING broadcast on a multi-SIM device), returns null.
     */
    fun onPhoneStateChanged(rawState: String?): CallEndSignal? =
        onCallState(toCallState(rawState), System.currentTimeMillis())

    private fun toCallState(rawState: String?): Int = when (rawState) {
        TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
        TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
        else -> TelephonyManager.CALL_STATE_IDLE
    }

    /** [onPhoneStateChanged] on an already-decoded `TelephonyManager.CALL_STATE_*` and an explicit
     * clock -- the form unit tests drive. */
    internal fun onCallState(state: Int, now: Long): CallEndSignal? {
        if (state == lastState) {
            return if (state == TelephonyManager.CALL_STATE_IDLE) onOrphanIdle(now) else null
        }

        // Observed on-device (Samsung One UI, real call): a spurious extra state broadcast
        // landing within ~1s of a call's real IDLE, shaped exactly like a new call starting
        // (IDLE->OFFHOOK), which then produced a second, bogus completed call about a second
        // later. Root cause on the OEM side isn't confirmed, but treating anything shaped like a
        // fresh call start within CALL_START_COOLDOWN_MILLIS of the last real call end as noise
        // -- ignored entirely, no state mutated -- reproducibly suppresses it. (The IDLE that
        // follows it is then an orphan IDLE inside onOrphanIdle's window, so it's dropped too.)
        if (lastState == TelephonyManager.CALL_STATE_IDLE &&
            now - lastCallEndedAt < CALL_START_COOLDOWN_MILLIS
        ) {
            log("Ignoring state=$state, ${now - lastCallEndedAt}ms after previous call end (cooldown)")
            return null
        }

        val previousState = lastState
        lastState = state

        return when {
            previousState == TelephonyManager.CALL_STATE_IDLE && state == TelephonyManager.CALL_STATE_RINGING -> {
                direction = CallDirection.INCOMING
                firstSeenAt = now
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
                callEnded(
                    now,
                    CallSession(
                        phoneNumber = null,
                        direction = CallDirection.INCOMING,
                        startedAt = ringingStartedAt,
                        endedAt = now,
                        durationMillis = 0L,
                        outcome = CallOutcome.MISSED,
                    ),
                )
            }

            previousState == TelephonyManager.CALL_STATE_IDLE && state == TelephonyManager.CALL_STATE_OFFHOOK -> {
                direction = CallDirection.OUTGOING
                firstSeenAt = now
                startedAt = now
                null
            }

            previousState == TelephonyManager.CALL_STATE_OFFHOOK && state == TelephonyManager.CALL_STATE_IDLE -> {
                val endedDirection = direction
                direction = null
                endedDirection?.let {
                    callEnded(
                        now,
                        CallSession(
                            phoneNumber = null,
                            direction = it,
                            startedAt = startedAt,
                            endedAt = now,
                            durationMillis = (now - startedAt).coerceAtLeast(0L),
                            outcome = CallOutcome.ANSWERED,
                        ),
                    )
                }
            }

            else -> null
        }
    }

    private fun callEnded(now: Long, observed: CallSession): CallEndSignal {
        lastCallEndedAt = now
        return CallEndSignal(endedAt = now, observedStartAt = firstSeenAt, observed = observed)
    }

    /**
     * An IDLE broadcast with no call tracked. Either noise (boot, SIM/radio changes, the second of
     * two per-SIM IDLE broadcasts for one call) or the real end of a call whose start this process
     * never saw because it was killed mid-call -- indistinguishable here, so it's passed on with
     * nothing observed and [CallSessionResolver] lets the call log decide.
     *
     * Dropped outright within [UNOBSERVED_CALL_END_WINDOW_MILLIS] of a call end this process
     * already reported: that is the same window the resolver accepts an unobserved call's row in,
     * so without this the duplicate IDLE for a call would find that call's row and show its screen
     * a second time.
     */
    private fun onOrphanIdle(now: Long): CallEndSignal? {
        if (now - maxOf(lastCallEndedAt, lastOrphanIdleAt) < UNOBSERVED_CALL_END_WINDOW_MILLIS) return null
        lastOrphanIdleAt = now
        return CallEndSignal(endedAt = now, observedStartAt = null, observed = null)
    }
}
