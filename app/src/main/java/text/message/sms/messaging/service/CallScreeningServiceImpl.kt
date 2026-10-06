package text.message.sms.messaging.service

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * The [CallScreeningService] behind this app's `ROLE_CALL_SCREENING` request: the role can only be
 * offered to, and held by, an app that declares one. Telecom binds it (guarded by
 * `BIND_SCREENING_SERVICE` in the manifest) for each call it screens. Holding the role is also
 * what makes Telecom launch [text.message.sms.messaging.ui.screens.callend.PostCallActivity]
 * after each call, which opens the call-end screen.
 *
 * A pass-through for now -- every call is allowed, with no blocking, silencing or call-log
 * suppression. Telecom waits on [respondToCall] before going on with the call, so it's answered
 * straight away.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
