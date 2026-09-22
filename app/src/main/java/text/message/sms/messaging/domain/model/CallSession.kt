package text.message.sms.messaging.domain.model

import androidx.compose.runtime.Immutable

enum class CallDirection { INCOMING, OUTGOING }

/**
 * [MISSED] and [REJECTED] can't always be told apart from [android.telephony.TelephonyManager]
 * call-state alone -- both look like RINGING -> IDLE with no intervening OFFHOOK, whether the
 * user actively declined or simply never answered. [CallStateMonitor][text.message.sms.messaging
 * .service.CallStateMonitor] always reports that transition as [MISSED]; [REJECTED] is reserved
 * for a future signal (e.g. a declined-call broadcast) that can tell the two apart.
 */
enum class CallOutcome { ANSWERED, MISSED, REJECTED }

/**
 * A single completed phone call, built by [text.message.sms.messaging.service.CallStateMonitor]
 * from raw telephony call-state transitions.
 *
 * @param phoneNumber The other party's number, resolved from [android.provider.CallLog.Calls]
 * after the call ends. Null when READ_CALL_LOG isn't granted, or nothing was resolvable (e.g. a
 * private/unknown caller) -- callers must degrade gracefully rather than assume a number.
 * @param startedAt Epoch millis when the call actually started -- for [CallDirection.INCOMING]
 * this is when it was answered (not when it started ringing); for [CallDirection.OUTGOING] this
 * is dial time.
 * @param endedAt Epoch millis when the call ended (or, for a [CallOutcome.MISSED] call, when it
 * stopped ringing).
 * @param durationMillis Talk duration, computed from [startedAt]/[endedAt] -- never read from
 * [android.provider.CallLog], which may not reflect the call yet at the moment this is built. Near
 * zero for a [CallOutcome.MISSED] call, since it was never answered.
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
