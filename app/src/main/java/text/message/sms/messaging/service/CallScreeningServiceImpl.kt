package text.message.sms.messaging.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.PhoneAccount
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.domain.usecase.BlockedSenderGate
import javax.inject.Inject

private const val TAG = "CallScreeningService"

/** How long [CallScreeningServiceImpl] waits on the block list before letting the call ring.
 * Telecom's own limit is a few seconds longer; a call is never held up past this. */
internal const val CALL_SCREENING_TIMEOUT_MILLIS = 2_000L

/**
 * The [CallScreeningService] behind this app's `ROLE_CALL_SCREENING` request: the role can only be
 * offered to, and held by, an app that declares one. Telecom binds it (guarded by
 * `BIND_SCREENING_SERVICE` in the manifest) for each call it screens. Holding the role is also
 * what makes Telecom launch [text.message.sms.messaging.ui.screens.callend.PostCallActivity]
 * after each call, which opens the call-end screen.
 *
 * Rejects an incoming call whose number [BlockedSenderGate] says is blocked -- the same block list
 * and matching rule as SMS/MMS -- with no notification, but still logged (as blocked) in the call
 * log. Everything else is allowed: outgoing calls, calls with no number, and any call whose check
 * fails or takes longer than [CALL_SCREENING_TIMEOUT_MILLIS]. Telecom waits on [respondToCall]
 * before going on with the call, so every path answers.
 */
@AndroidEntryPoint
class CallScreeningServiceImpl : CallScreeningService() {

    @Inject
    lateinit var blockedSenderGate: BlockedSenderGate

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        if (!isIncoming(callDetails)) {
            respond(callDetails, reject = false)
            return
        }
        val number = callDetails.handle
            ?.takeIf { it.scheme == PhoneAccount.SCHEME_TEL }
            ?.schemeSpecificPart
        scope.launch {
            val reject = isBlockedCaller(number, CALL_SCREENING_TIMEOUT_MILLIS, blockedSenderGate::isBlocked)
            if (BuildConfig.DEBUG) Log.d(TAG, "incoming call screened, reject=$reject")
            respond(callDetails, reject)
            if (reject) Analytics.callBlocked()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun respond(callDetails: Call.Details, reject: Boolean) {
        val response = if (reject) {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(false)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        try {
            respondToCall(callDetails, response)
        } catch (e: Exception) {
            Log.w(TAG, "respondToCall failed", e)
        }
    }

    /** Before API 29 Telecom only ever screened incoming calls, and has no direction to read. */
    private fun isIncoming(callDetails: Call.Details): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
}

/**
 * Whether [number] is blocked according to [isBlocked], failing open: false for no number, and
 * false when [isBlocked] throws or hasn't answered within [timeoutMillis] -- a call or a call-end
 * screen should never be lost to a slow or broken lookup.
 */
internal suspend fun isBlockedCaller(
    number: String?,
    timeoutMillis: Long,
    isBlocked: suspend (String) -> Boolean,
): Boolean {
    if (number.isNullOrBlank()) return false
    return try {
        withTimeoutOrNull(timeoutMillis) { isBlocked(number) } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
