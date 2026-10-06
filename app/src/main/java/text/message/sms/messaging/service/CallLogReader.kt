package text.message.sms.messaging.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession

private const val TAG = "CallLogReader"

/** How recently a call-log row must have ended to count as the call that just ended. A row's
 * date + duration falls short of the real end by the call's ring/setup time (DATE is when the call
 * was created), which this has to cover. */
internal const val CALL_LOG_ROW_MAX_AGE_MILLIS = 120_000L

/** How often [PostCallSessionResolver] re-reads the call log while waiting for the just-ended
 * call's row, whose write can land after `POST_CALL`. */
internal const val POLL_INTERVAL_MILLIS = 250L

/** The columns of a [CallLog.Calls] row that [deriveCallSession] needs. */
internal data class CallLogEntry(
    val number: String?,
    val type: Int,
    val dateMillis: Long,
    val durationSeconds: Long,
)

/** The call log, as far as [PostCallSessionResolver] needs it -- an interface so unit tests can
 * feed it rows without a `ContentResolver`. */
internal interface CallLogReader {
    /** False when READ_CALL_LOG isn't granted; [latestEntry] is then never worth calling. */
    fun canRead(): Boolean

    /** The most recent row, or null when there is none or it couldn't be read. Never throws. */
    suspend fun latestEntry(): CallLogEntry?
}

/**
 * Builds the [CallSession] for a call that ended at [endedAt] from a call-log row, or returns null
 * when [entry] isn't usable for it: there's no row, it ended more than
 * [CALL_LOG_ROW_MAX_AGE_MILLIS] before [endedAt], or it's of a type the call-end screen has nothing
 * to say about (voicemail, blocked, answered on another device...). Whether the row is this call's
 * and not an earlier one's is [matchPostCallRow]'s job; this only maps it.
 *
 * Direction, outcome, number and duration all come from the row. [CallLog.Calls.DURATION] is talk
 * time in seconds, so an unanswered outgoing call is 0 -- not the time spent ringing out.
 */
internal fun deriveCallSession(entry: CallLogEntry?, endedAt: Long): CallSession? {
    if (entry == null) return null
    val durationMillis = entry.durationSeconds.coerceAtLeast(0L) * 1_000L
    if (entry.dateMillis + durationMillis < endedAt - CALL_LOG_ROW_MAX_AGE_MILLIS) return null

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
            answered && direction == CallDirection.INCOMING -> endedAt - durationMillis
            else -> entry.dateMillis.coerceAtMost(endedAt)
        },
        endedAt = endedAt,
        durationMillis = if (answered) durationMillis else 0L,
        outcome = outcome,
    )
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
