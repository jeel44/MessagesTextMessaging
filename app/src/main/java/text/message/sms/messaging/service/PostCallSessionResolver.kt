package text.message.sms.messaging.service

import android.provider.CallLog
import android.telecom.DisconnectCause
import kotlinx.coroutines.delay
import text.message.sms.messaging.domain.model.CallSession
import javax.inject.Inject
import javax.inject.Singleton

/** How far after `POST_CALL` arrived a row may still claim to have ended -- clock slack between
 * Telecom writing the row and this process reading the clock. */
private const val ROW_END_FUTURE_SLACK_MILLIS = 5_000L

/** Fewest digits two numbers need for a suffix match ([sameCaller]); shorter ones (short codes)
 * must match exactly. */
private const val MIN_SUFFIX_MATCH_DIGITS = 7

/** Most trailing digits a suffix match compares: a national number without its country code or
 * trunk prefix. */
private const val MAX_SUFFIX_MATCH_DIGITS = 10

/** What a `POST_CALL` intent's already-validated extras say about the call that just ended. */
internal data class PostCallInfo(
    /** The decoded `tel:` handle, or null when it was missing or invalid (withheld callers). */
    val number: String?,
    /** A known [DisconnectCause] code, or null. */
    val disconnectCause: Int?,
    /** Epoch millis `POST_CALL` arrived. */
    val endedAt: Long,
)

/**
 * What the previous `POST_CALL`s in this process already used -- so a call-log row that belongs to
 * an earlier call can't be taken for the call that just ended. In memory only; a fresh process has
 * nothing here and relies on [matchPostCallRow]'s other checks. Main thread only.
 */
@Singleton
class PostCallHistory @Inject constructor() {
    internal var lastCallEndedAt: Long? = null
    internal var lastUsedRowDateMillis: Long? = null
}

/**
 * The [CallSession] for [call] from [entry], or null if [entry] can't be this call's own row:
 * - a different caller than the handle ([sameCaller]);
 * - a row type that contradicts the disconnect cause ([rowTypeFitsCause]), e.g. a MISSED row for a
 *   call Telecom says the remote party hung up;
 * - the row already used for an earlier call, or one that ended before the previous call did;
 * - a row ending implausibly far after `POST_CALL`, or (via [deriveCallSession]) too long before it.
 *
 * Row end is DATE + DURATION. DATE is when the call was created, so for a missed call (DURATION 0)
 * that's when it started ringing. That's why there's no "ended within a few seconds" check: the
 * previous-call bound is what rejects a stale row.
 */
internal fun matchPostCallRow(
    entry: CallLogEntry?,
    call: PostCallInfo,
    previousCallEndedAt: Long?,
    lastUsedRowDateMillis: Long?,
): CallSession? {
    if (entry == null) return null
    if (!sameCaller(entry.number, call.number)) return null
    if (!rowTypeFitsCause(entry.type, call.disconnectCause)) return null
    if (lastUsedRowDateMillis != null && entry.dateMillis <= lastUsedRowDateMillis) return null
    val rowEnd = entry.dateMillis + entry.durationSeconds.coerceAtLeast(0L) * 1_000L
    if (rowEnd > call.endedAt + ROW_END_FUTURE_SLACK_MILLIS) return null
    if (previousCallEndedAt != null && rowEnd < previousCallEndedAt) return null
    return deriveCallSession(entry, CallEndSignal(endedAt = call.endedAt, observedStartAt = null, observed = null))
}

/**
 * Whether a call-log row's number is the `POST_CALL` handle's. Compared on digits only, and on the
 * last [MAX_SUFFIX_MATCH_DIGITS] when both have at least [MIN_SUFFIX_MATCH_DIGITS] -- the log can
 * store `+917990096382` for a handle of `07990096382`. A withheld caller (no handle) matches only a
 * withheld row: blank, or the legacy negative presentation markers (`-1`, `-2`...).
 */
internal fun sameCaller(rowNumber: String?, handleNumber: String?): Boolean {
    val rowWithheld = rowNumber.isNullOrBlank() || rowNumber.trim().startsWith("-")
    if (handleNumber == null) return rowWithheld
    if (rowWithheld) return false
    val row = rowNumber.orEmpty().filter { it.isDigit() }
    val handle = handleNumber.filter { it.isDigit() }
    if (row.isEmpty() || handle.isEmpty()) return false
    if (row == handle) return true
    val shorter = minOf(row.length, handle.length)
    if (shorter < MIN_SUFFIX_MATCH_DIGITS) return false
    val compared = minOf(shorter, MAX_SUFFIX_MATCH_DIGITS)
    return row.takeLast(compared) == handle.takeLast(compared)
}

/** Whether a row of call-log [type] can belong to a call that ended with [disconnectCause]. An
 * unknown or absent cause rules nothing out. */
internal fun rowTypeFitsCause(type: Int, disconnectCause: Int?): Boolean = when (disconnectCause) {
    DisconnectCause.MISSED -> type == CallLog.Calls.MISSED_TYPE
    DisconnectCause.REJECTED -> type == CallLog.Calls.REJECTED_TYPE || type == CallLog.Calls.MISSED_TYPE
    DisconnectCause.REMOTE -> type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE
    // The user hung up -- or, on some OEMs, declined while it rang.
    DisconnectCause.LOCAL ->
        type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE ||
            type == CallLog.Calls.REJECTED_TYPE
    DisconnectCause.CANCELED, DisconnectCause.BUSY -> type == CallLog.Calls.OUTGOING_TYPE
    else -> true
}

/**
 * Resolves the session for one `POST_CALL`: polls the call log for this call's own row
 * ([matchPostCallRow]) for up to [pollTimeoutMillis], and otherwise returns [fallback] -- built from
 * the extras, with its outcome from the disconnect cause -- never a row that failed the match.
 * Records the call's end, and the row it used, in [history] for the next call.
 */
internal class PostCallSessionResolver(
    private val reader: CallLogReader,
    private val history: PostCallHistory,
    private val pollTimeoutMillis: Long,
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
    private val log: (String) -> Unit = {},
) {

    suspend fun resolve(call: PostCallInfo, fallback: CallSession?): CallSession? {
        val previousCallEndedAt = history.lastCallEndedAt
        history.lastCallEndedAt = call.endedAt
        if (!reader.canRead()) {
            log("call log not readable, using the extras")
            return fallback
        }
        var waited = 0L
        while (true) {
            val entry = reader.latestEntry()
            val session = matchPostCallRow(entry, call, previousCallEndedAt, history.lastUsedRowDateMillis)
            if (session != null && entry != null) {
                history.lastUsedRowDateMillis = entry.dateMillis
                log("matched call-log row after ${waited}ms: $entry")
                return session
            }
            if (waited >= pollTimeoutMillis) {
                log("no matching call-log row in ${pollTimeoutMillis}ms (latest: $entry), using the extras")
                return fallback
            }
            delay(pollIntervalMillis)
            waited += pollIntervalMillis
        }
    }
}
