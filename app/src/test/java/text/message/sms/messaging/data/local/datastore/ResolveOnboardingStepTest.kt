package text.message.sms.messaging.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

/** [resolveOnboardingStep]: a stored step wins; otherwise the legacy flags decide. */
class ResolveOnboardingStepTest {

    @Test
    fun freshInstall_startsAtLanguage() {
        assertEquals(OnboardingStep.LANGUAGE, resolve(storedStep = null))
    }

    @Test
    fun legacyCompletedInstall_isDone() {
        assertEquals(OnboardingStep.DONE, resolve(storedStep = null, complete = true))
    }

    @Test
    fun legacyIntroPending_resumesAtIntro() {
        assertEquals(OnboardingStep.INTRO, resolve(storedStep = null, introPending = true))
    }

    @Test
    fun legacyCompleteWinsOverIntroPending() {
        assertEquals(OnboardingStep.DONE, resolve(storedStep = null, complete = true, introPending = true))
    }

    @Test
    fun storedStep_winsOverLegacyFlags() {
        OnboardingStep.entries.forEach { step ->
            assertEquals(step, resolve(storedStep = step.name, complete = true, introPending = true))
        }
    }

    @Test
    fun legacyOverlayStep_resumesOnTheCallScreeningStepThatReplacedIt() {
        assertEquals(OnboardingStep.CALL_SCREENING, resolve(storedStep = "OVERLAY"))
        assertEquals(OnboardingStep.CALL_SCREENING, resolve(storedStep = "OVERLAY", complete = true))
    }

    @Test
    fun unknownStoredStep_fallsBackToLegacyFlags() {
        assertEquals(OnboardingStep.LANGUAGE, resolve(storedStep = "FROM_A_NEWER_BUILD"))
        assertEquals(OnboardingStep.DONE, resolve(storedStep = "FROM_A_NEWER_BUILD", complete = true))
    }

    private fun resolve(storedStep: String?, complete: Boolean = false, introPending: Boolean = false) =
        resolveOnboardingStep(storedStep, legacyOnboardingComplete = complete, legacyIntroPending = introPending)
}
