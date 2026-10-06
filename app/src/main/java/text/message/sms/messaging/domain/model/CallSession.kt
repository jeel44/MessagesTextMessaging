package text.message.sms.messaging.domain.model

import androidx.compose.runtime.Immutable

enum class CallDirection { INCOMING, OUTGOING }

/**
 * [MISSED] and [REJECTED] come from the call log's `MISSED_TYPE`/`REJECTED_TYPE`, or, when the
 * call log has no usable row, from `POST_CALL`'s disconnect cause (`DisconnectCause.MISSED` /
 * `REJECTED`).
 */
enum class CallOutcome { ANSWERED, MISSED, REJECTED }

/**
 * A single completed phone call. Normally built from the call's [android.provider.CallLog.Calls]
 * row once it has ended (see [text.message.sms.messaging.service.PostCallSessionResolver]); when
 * the call log has no usable row, from `POST_CALL`'s own extras instead (handle and disconnect
 * cause -- no direction or talk time; see
 * [text.message.sms.messaging.ui.screens.callend.postCallFallbackSession]).
 *
 * @param phoneNumber The other party's number, from the call log. Null when READ_CALL_LOG isn't
 * granted, or nothing was resolvable (e.g. a private/unknown caller) -- callers must degrade
 * gracefully rather than assume a number.
 * @param startedAt Epoch millis when the call actually started -- for an answered
 * [CallDirection.INCOMING] call this is when it was answered (not when it started ringing); for
 * [CallDirection.OUTGOING] this is dial time.
 * @param endedAt Epoch millis when the call ended (or, for a [CallOutcome.MISSED] call, when it
 * stopped ringing).
 * @param durationMillis Talk duration: the call log's own figure (whole seconds; zero for an
 * outgoing call nobody answered), or computed from [startedAt]/[endedAt] in the fallback case.
 * Zero for a [CallOutcome.MISSED] or [CallOutcome.REJECTED] call, since it was never answered.
 */
@Immutable
data class CallSession(
    val phoneNumber: String?,
    val direction: CallDirection,
    val startedAt: Long,
    val endedAt: Long,
    val durationMillis: Long,
    val outcome: CallOutcome,
)
