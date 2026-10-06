package text.message.sms.messaging.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [applyOverlayFlag] with the overlay/call-end flag passed in explicitly, so these don't depend on
 * the Remote Config value in OverlayFeatureFlag. Covers both uses: the step Splash resumes at, and the
 * step Welcome moves on to (always [OnboardingStep.CALL_SCREENING] going in).
 */
class ApplyOverlayFlagTest {

    @Test
    fun flagOn_everyStepIsUnchanged() {
        OnboardingStep.entries.forEach { step ->
            assertEquals(step, applyOverlayFlag(step, overlayEnabled = true))
        }
    }

    @Test
    fun flagOff_savedCallScreeningResumesAtSetDefaultSms() {
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyOverlayFlag(OnboardingStep.CALL_SCREENING, overlayEnabled = false))
    }

    @Test
    fun flagOff_everyOtherStepIsUnchanged() {
        OnboardingStep.entries.filter { it != OnboardingStep.CALL_SCREENING }.forEach { step ->
            assertEquals(step, applyOverlayFlag(step, overlayEnabled = false))
        }
    }

    @Test
    fun flagOff_nothingResolvesToCallScreening() {
        OnboardingStep.entries.forEach { step ->
            assertNotEquals(OnboardingStep.CALL_SCREENING, applyOverlayFlag(step, overlayEnabled = false))
        }
    }

    @Test
    fun welcomeNextStep_followsTheFlag() {
        assertEquals(OnboardingStep.CALL_SCREENING, applyOverlayFlag(OnboardingStep.CALL_SCREENING, overlayEnabled = true))
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyOverlayFlag(OnboardingStep.CALL_SCREENING, overlayEnabled = false))
    }

    /** The full resume path: the legacy stored `OVERLAY` resolved by [resolveOnboardingStep] (to
     * its replacement), then the flag applied, as SplashViewModel does. */
    @Test
    fun storedLegacyOverlay_resolvedThenFlagged() {
        val stored = resolveOnboardingStep("OVERLAY", legacyOnboardingComplete = false, legacyIntroPending = false)
        assertEquals(OnboardingStep.CALL_SCREENING, applyOverlayFlag(stored, overlayEnabled = true))
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyOverlayFlag(stored, overlayEnabled = false))
    }
}
