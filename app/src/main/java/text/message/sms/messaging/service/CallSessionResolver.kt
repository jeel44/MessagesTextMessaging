package text.message.sms.messaging.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession

private const val TAG = "CallSessionResolver"

/** How far before the first RINGING/OFFHOOK broadcast this process saw a call-log row may still
 * end and count as that call's own row. A call's row is dated at the call's creation, which
 * precedes its first `PHONE_STATE` broadcast (by up to a few seconds when a call-screening app
 * holds the call before it rings), so an unanswered call's row "ends" slightly before the
 * broadcast. The cost of the slack: a previous call that ended within it, whose successor's row
 * isn't written yet, is mistaken for the successor. */
internal const val CALL_START_SLACK_MILLIS = 5_000L

/** With no call start observed (process killed mid-call), how recently a call-log row must have
 * ended to count as the call that just ended. A row's date + duration falls short of the real end
 * by the call's ring/setup time, which this has to cover. Also how long [CallStateMonitor] drops
 * further orphan IDLE broadcasts after a call end, so the same row can't be shown twice. */
internal const val UNOBSERVED_CALL_END_WINDOW_MILLIS = 120_000L

/** The call log's write can land after the IDLE broadcast, so the row is polled for. Bounded well
 * inside the receiver's `goAsync()` budget (see [PhoneStateReceiver]). */
internal const val POLL_INTERVAL_MILLIS = 250L
private const val POLL_TIMEOUT_MILLIS = 3_000L

/** The columns of a [CallLog.Calls] row that [deriveCallSession] needs. */
internal data class CallLogEntry(
    val number: String?,
    val type: Int,
    val dateMillis: Long,
    val durationSeconds: Long,
)

/** The call log, as far as [CallSessionResolver] needs it -- an interface so unit tests can feed
 * it rows without a `ContentResolver`. */
internal interface CallLogReader {
    /** False when READ_CALL_LOG isn't granted; [latestEntry] is then never worth calling. */
    fun canRead(): Boolean

    /** The most recent row, or null when there is none or it couldn't be read. Never throws. */
    suspend fun latestEntry(): CallLogEntry?
}

/**
 * Builds the [CallSession] for the call [signal] reports from the call log's most recent row, or
 * returns null when [entry] isn't usable for it: there's no row, the row belongs to an earlier
 * call (the just-ended call's own row isn't written yet), or it's of a type the call-end screen
 * has nothing to say about (voicemail, blocked, answered on another device...).
 *
 * Direction, outcome, number and duration all come from the row. [CallLog.Calls.DURATION] is talk
 * time in seconds, so an unanswered outgoing call is 0 -- not the time spent ringing out.
 */
internal fun deriveCallSession(entry: CallLogEntry?, signal: CallEndSignal): CallSession? {
    if (entry == null) return null
    val durationMillis = entry.durationSeconds.coerceAtLeast(0L) * 1_000L

    val earliestPlausibleEnd = signal.observedStartAt?.minus(CALL_START_SLACK_MILLIS)
        ?: (signal.endedAt - UNOBSERVED_CALL_END_WINDOW_MILLIS)
    if (entry.dateMillis + durationMillis < earliestPlausibleEnd) return null

    val (direction, outcome) = when (entry.type) {
        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING to CallOutcome.ANSWERED
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING to CallOutcome.ANSWERED
        CallLog.Calls.MISSED_TYPE -> CallDirection.INCOMING to CallOutcome.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallDirection.INCOMING to CallOutcome.REJECTED
        else -> return null
    }
    val answered = outcome == CallOutcome.ANSWERED
    return CallSession(
        // Withheld/unknown callers are logged with an empty number.
        phoneNumber = entry.number?.takeIf { it.isNotBlank() },
        direction = direction,
        startedAt = when {
            // Answered incoming: when it was answered, per CallSession.startedAt's contract.
            answered && direction == CallDirection.INCOMING -> signal.endedAt - durationMillis
            else -> entry.dateMillis.coerceAtMost(signal.endedAt)
        },
        endedAt = signal.endedAt,
        durationMillis = if (answered) durationMillis else 0L,
        outcome = outcome,
    )
}

/**
 * Resolves a [CallEndSignal] into the [CallSession] to show, preferring the call log over whatever
 * [CallStateMonitor] observed in memory: the log survives this process being killed mid-call, and
 * it's the only source of the phone number.
 */
internal class CallSessionResolver(
    private val reader: CallLogReader,
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
    private val pollTimeoutMillis: Long = POLL_TIMEOUT_MILLIS,
) {

    /**
     * Polls the call log for the just-ended call's row for up to [pollTimeoutMillis]. Falls back
     * to [CallEndSignal.observed] (no number) when no usable row turns up or the log isn't
     * readable -- which is null, meaning "no call to show", when nothing was observed either.
     */
    suspend fun resolve(signal: CallEndSignal): CallSession? {
        if (reader.canRead()) {
            var waited = 0L
            while (true) {
                deriveCallSession(reader.latestEntry(), signal)?.let { return it }
                if (waited >= pollTimeoutMillis) break
                delay(pollIntervalMillis)
                waited += pollIntervalMillis
            }
        }
        return signal.observed
    }
}

/** [CallLogReader] over the real [CallLog.Calls] provider. */
internal class ContentResolverCallLogReader(private val context: Context) : CallLogReader {

    override fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun latestEntry(): CallLogEntry? = withContext(Dispatchers.IO) {
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1")
            .build()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                CallLogEntry(
                    number = cursor.getString(0),
                    type = cursor.getInt(1),
                    dateMillis = cursor.getLong(2),
                    durationSeconds = cursor.getLong(3),
                )
            }
        } catch (e: Exception) {
            // READ_CALL_LOG revoked between canRead() and here, or an OEM provider rejecting the
            // query -- either way there's no row to offer, and the caller has a fallback.
            Log.w(TAG, "Call log query failed", e)
            null
        }
    }
}
