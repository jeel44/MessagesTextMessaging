package text.message.sms.messaging.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [applyCallEndFlag] with the call-end flag passed in explicitly, so these don't depend on
 * the Remote Config value in CallEndFeatureFlag. Covers both uses: the step Splash resumes at, and the
 * step Welcome moves on to (always [OnboardingStep.CALL_SCREENING] going in).
 */
class ApplyCallEndFlagTest {

    @Test
    fun flagOn_everyStepIsUnchanged() {
        OnboardingStep.entries.forEach { step ->
            assertEquals(step, applyCallEndFlag(step, callEndEnabled = true))
        }
    }

    @Test
    fun flagOff_savedCallScreeningResumesAtSetDefaultSms() {
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyCallEndFlag(OnboardingStep.CALL_SCREENING, callEndEnabled = false))
    }

    @Test
    fun flagOff_everyOtherStepIsUnchanged() {
        OnboardingStep.entries.filter { it != OnboardingStep.CALL_SCREENING }.forEach { step ->
            assertEquals(step, applyCallEndFlag(step, callEndEnabled = false))
        }
    }

    @Test
    fun flagOff_nothingResolvesToCallScreening() {
        OnboardingStep.entries.forEach { step ->
            assertNotEquals(OnboardingStep.CALL_SCREENING, applyCallEndFlag(step, callEndEnabled = false))
        }
    }

    @Test
    fun welcomeNextStep_followsTheFlag() {
        assertEquals(OnboardingStep.CALL_SCREENING, applyCallEndFlag(OnboardingStep.CALL_SCREENING, callEndEnabled = true))
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyCallEndFlag(OnboardingStep.CALL_SCREENING, callEndEnabled = false))
    }

    /** The full resume path: the legacy stored `OVERLAY` resolved by [resolveOnboardingStep] (to
     * its replacement), then the flag applied, as SplashViewModel does. */
    @Test
    fun storedLegacyOverlay_resolvedThenFlagged() {
        val stored = resolveOnboardingStep("OVERLAY", legacyOnboardingComplete = false, legacyIntroPending = false)
        assertEquals(OnboardingStep.CALL_SCREENING, applyCallEndFlag(stored, callEndEnabled = true))
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyCallEndFlag(stored, callEndEnabled = false))
    }
}
