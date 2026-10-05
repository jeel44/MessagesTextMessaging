package text.message.sms.messaging.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import text.message.sms.messaging.data.local.datastore.OnboardingStep
import javax.inject.Inject

/**
 * The one place onboarding's progress is written: each onboarding screen's "done" callback in
 * [text.message.sms.messaging.ui.navigation.MessagingNavHost] goes through [advanceTo], so every
 * step stores the next one before navigating there, and a process death on any screen resumes at
 * that screen (see [OnboardingPreferences.currentStep]). The screens themselves never write it.
 *
 * Scoped to the Activity, not a back-stack entry, so the write isn't cancelled by the very
 * navigation it precedes.
 */
@HiltViewModel
internal class OnboardingProgressViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {

    private var advancing: Job? = null

    /** Stores [next], then calls [navigate]. Ignored while a previous advance is still writing, so
     * a double-tap can't navigate twice. */
    fun advanceTo(next: OnboardingStep, navigate: () -> Unit) {
        if (advancing?.isActive == true) return
        advancing = viewModelScope.launch {
            onboardingPreferences.setCurrentStep(next)
            Analytics.onboardingStep(next)
            navigate()
        }
    }
}
