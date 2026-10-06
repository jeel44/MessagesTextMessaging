package text.message.sms.messaging.ui.screens.callend

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.config.OverlayFeatureFlag
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession
import text.message.sms.messaging.service.CallEndShownGate
import text.message.sms.messaging.service.ContentResolverCallLogReader
import text.message.sms.messaging.service.PostCallHistory
import text.message.sms.messaging.service.PostCallInfo
import text.message.sms.messaging.service.PostCallSessionResolver
import javax.inject.Inject

private const val TAG = "PostCallActivity"

/** How long this (translucent, touch-swallowing) Activity waits on the call log for the call's
 * row before settling for [postCallFallbackSession]. Shorter than the receiver path's 3s: this
 * window sits on top of whatever the user is doing. */
internal const val POST_CALL_CALL_LOG_WAIT_MILLIS = 1_500L

/** Longest number [validPostCallNumber] accepts -- E.164 is 15 digits; this leaves room for a
 * leading +, separators and a short extension. */
private const val MAX_NUMBER_LENGTH = 32

private val ALLOWED_NUMBER_CHARS = Regex("[0-9+*#()\\-., ;pPwW]+")

/**
 * The system's `POST_CALL` hook: once this app holds `ROLE_CALL_SCREENING` (see
 * [text.message.sms.messaging.service.CallScreeningServiceImpl]), Telecom itself (uid 1000)
 * launches this Activity after each call ends. That launch needs no "draw over other apps" and no
 * background-activity-launch exemption -- the system is the one starting it -- and since this
 * Activity is then in the foreground, it can open [CallEndActivity] directly.
 *
 * No UI of its own: a translucent window (not `Theme.NoDisplay`, which must finish before
 * `onResume` and so can't wait on the call log) that resolves the session, opens
 * [CallEndActivity] and finishes. The session comes from this call's own call-log row, matched
 * against the extras' handle and disconnect cause ([PostCallSessionResolver]) -- `POST_CALL` can
 * arrive before Telecom writes that row, and the latest row is then the previous call's. Capped at
 * [POST_CALL_CALL_LOG_WAIT_MILLIS]; with no matching row by then, the extras alone build the session.
 *
 * Shown over the lock screen (it's transparent, and gone within that cap): a call that ended while
 * locked still has to open [CallEndActivity], and this window being visible is what lets it.
 *
 * [PhoneStateReceiver][text.message.sms.messaging.service.PhoneStateReceiver] still runs for users
 * with "draw over other apps"; [CallEndShownGate] makes sure one call end opens the screen once.
 *
 * Exported, so the extras are untrusted: every one is type- and range-checked, and anything
 * malformed is ignored. Any app can send `POST_CALL` here, though, and there's no reliable way to
 * tell Telecom's launch from another app's before API 34 (and even then only if the sender opts
 * in to sharing its identity). With READ_CALL_LOG granted a spoofed intent opens the screen for
 * the real most-recent call at worst; without it, the screen can show a number of the sender's
 * choosing. That screen is this app's own and only offers read-only actions or hands off to
 * [text.message.sms.messaging.MainActivity], so the exposure is a misleading screen, nothing more.
 */
@AndroidEntryPoint
class PostCallActivity : ComponentActivity() {

    @Inject
    lateinit var callEndShownGate: CallEndShownGate

    @Inject
    lateinit var postCallHistory: PostCallHistory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        // Recreated (config change) mid-wait: the first instance's work went with it, and running
        // it again this late would only open the screen after the user has moved on.
        if (savedInstanceState != null) {
            log("recreated, finishing without launching")
            finish()
            return
        }
        if (BuildConfig.DEBUG) logAllExtras(intent)

        if (!OverlayFeatureFlag.isEnabled()) {
            log("overlay/call-end feature flag is off, finishing")
            finish()
            return
        }

        val call = readPostCallInfo(intent, endedAt = System.currentTimeMillis())
        val fallback = call?.let { postCallFallbackSession(it.number, it.disconnectCause, it.endedAt) }
        log("call from extras: $call, fallback session: $fallback")

        lifecycleScope.launch {
            val session = if (call == null) {
                null
            } else {
                PostCallSessionResolver(
                    reader = ContentResolverCallLogReader(applicationContext),
                    history = postCallHistory,
                    pollTimeoutMillis = POST_CALL_CALL_LOG_WAIT_MILLIS,
                    log = ::log,
                ).resolve(call, fallback)
            }
            when {
                session == null -> log("no session from the call log or the extras, finishing")
                !callEndShownGate.tryClaim(session.phoneNumber) -> log("call-end screen already shown for this call, finishing")
                else -> try {
                    log("starting CallEndActivity for $session")
                    CallEndActivity.start(this@PostCallActivity, session)
                } catch (e: Exception) {
                    Log.e(TAG, "CallEndActivity launch failed", e)
                }
            }
            finish()
        }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

/** Debug only: every extra exactly as delivered, one line per key -- to see what each OEM's
 * Telecom actually sends with `POST_CALL`. */
@Suppress("DEPRECATION")
private fun logAllExtras(intent: Intent) {
    Log.d(TAG, "action=${intent.action} data=${intent.data} flags=0x${Integer.toHexString(intent.flags)}")
    try {
        val extras = intent.extras
        if (extras == null || extras.isEmpty) {
            Log.d(TAG, "no extras")
            return
        }
        for (key in extras.keySet()) {
            val value = extras.get(key)
            Log.d(TAG, "extra $key = $value (${value?.javaClass?.name})")
        }
    } catch (e: Exception) {
        Log.d(TAG, "extras unreadable", e)
    }
}

/** Reads and validates the `POST_CALL` extras. Never throws: an extras bundle a sender stuffed
 * with an unparcelable value just yields null, and no screen. */
@Suppress("DEPRECATION")
private fun readPostCallInfo(intent: Intent, endedAt: Long): PostCallInfo? = try {
    val handle = IntentCompat.getParcelableExtra(intent, TelecomManager.EXTRA_HANDLE, Uri::class.java)
    val extras = intent.extras
    val disconnectCause = extras?.get(TelecomManager.EXTRA_DISCONNECT_CAUSE) as? Int
    // A coarse bucket (DURATION_VERY_SHORT..DURATION_LONG), not a length: logged, not used.
    val durationBucket = (extras?.get(TelecomManager.EXTRA_CALL_DURATION) as? Int)
        ?.takeIf { it in TelecomManager.DURATION_VERY_SHORT..TelecomManager.DURATION_LONG }
    if (BuildConfig.DEBUG) Log.d(TAG, "validated: handle=$handle cause=$disconnectCause durationBucket=$durationBucket")
    PostCallInfo(
        number = validPostCallNumber(handle?.scheme, handle?.schemeSpecificPart),
        disconnectCause = disconnectCause,
        endedAt = endedAt,
    )
} catch (e: Exception) {
    Log.w(TAG, "POST_CALL extras unreadable", e)
    null
}

/** The number in a `tel:` handle, or null if it isn't one or doesn't look like a phone number. */
internal fun validPostCallNumber(scheme: String?, schemeSpecificPart: String?): String? {
    if (scheme != "tel") return null
    val number = schemeSpecificPart?.trim() ?: return null
    if (number.isEmpty() || number.length > MAX_NUMBER_LENGTH) return null
    if (!ALLOWED_NUMBER_CHARS.matches(number) || number.none { it.isDigit() }) return null
    return number
}

/**
 * The session to show when the call log has nothing usable, built from already-validated `POST_CALL`
 * extras, or null when they say nothing (no number and no recognised cause) or describe a call
 * the screen has nothing to say about -- the same calls [text.message.sms.messaging.service
 * .deriveCallSession] skips. The extras carry no direction or talk time, and the screen shows
 * neither, so an answered call is reported as incoming with zero duration.
 */
internal fun postCallFallbackSession(number: String?, disconnectCause: Int?, endedAt: Long): CallSession? {
    val (direction, outcome) = when (disconnectCause) {
        DisconnectCause.MISSED -> CallDirection.INCOMING to CallOutcome.MISSED
        DisconnectCause.REJECTED -> CallDirection.INCOMING to CallOutcome.REJECTED
        // Outgoing calls that never connected.
        DisconnectCause.CANCELED, DisconnectCause.BUSY -> CallDirection.OUTGOING to CallOutcome.ANSWERED
        DisconnectCause.UNKNOWN, DisconnectCause.ERROR, DisconnectCause.LOCAL, DisconnectCause.REMOTE,
        DisconnectCause.RESTRICTED, DisconnectCause.OTHER,
        -> CallDirection.INCOMING to CallOutcome.ANSWERED
        // No cause: only worth a screen if there's at least a number.
        null -> if (number != null) CallDirection.INCOMING to CallOutcome.ANSWERED else return null
        // Answered elsewhere, pulled to another device, or a value Telecom never sends.
        else -> return null
    }
    return CallSession(
        phoneNumber = number,
        direction = direction,
        startedAt = endedAt,
        endedAt = endedAt,
        durationMillis = 0L,
        outcome = outcome,
    )
}
