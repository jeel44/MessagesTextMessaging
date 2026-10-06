package text.message.sms.messaging.ui.screens.permissions

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import text.message.sms.messaging.analytics.CallScreeningSource
import text.message.sms.messaging.service.CallScreeningRoleTracker
import javax.inject.Inject

/** Backs [CallScreeningRoleScreen]: reports its outcomes to [CallScreeningRoleTracker], which
 * remembers that the role was granted and logs `call_screening_result` with source onboarding. */
@HiltViewModel
class CallScreeningRoleViewModel @Inject constructor(
    private val tracker: CallScreeningRoleTracker,
) : ViewModel() {

    internal fun onRoleAlreadyHeld() = tracker.onRoleSeenHeld()

    internal fun onRoleRequestResult() = tracker.onRoleRequestResult(CallScreeningSource.ONBOARDING)

    internal fun onNotNow() = tracker.onOnboardingSkipped()
}
