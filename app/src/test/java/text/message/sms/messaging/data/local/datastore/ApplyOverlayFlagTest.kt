package text.message.sms.messaging.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [applyOverlayFlag] with the overlay/call-end flag passed in explicitly, so these don't depend on
 * the hardcoded value in OverlayFeatureFlag. Covers both uses: the step Splash resumes at, and the
 * step Welcome moves on to (always [OnboardingStep.OVERLAY] going in).
 */
class ApplyOverlayFlagTest {

    @Test
    fun flagOn_everyStepIsUnchanged() {
        OnboardingStep.entries.forEach { step ->
            assertEquals(step, applyOverlayFlag(step, overlayEnabled = true))
        }
    }

    @Test
    fun flagOff_savedOverlayResumesAtSetDefaultSms() {
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyOverlayFlag(OnboardingStep.OVERLAY, overlayEnabled = false))
    }

    @Test
    fun flagOff_everyOtherStepIsUnchanged() {
        OnboardingStep.entries.filter { it != OnboardingStep.OVERLAY }.forEach { step ->
            assertEquals(step, applyOverlayFlag(step, overlayEnabled = false))
        }
    }

    @Test
    fun flagOff_nothingResolvesToOverlay() {
        OnboardingStep.entries.forEach { step ->
            assertNotEquals(OnboardingStep.OVERLAY, applyOverlayFlag(step, overlayEnabled = false))
        }
    }

    @Test
    fun welcomeNextStep_followsTheFlag() {
        assertEquals(OnboardingStep.OVERLAY, applyOverlayFlag(OnboardingStep.OVERLAY, overlayEnabled = true))
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyOverlayFlag(OnboardingStep.OVERLAY, overlayEnabled = false))
    }

    /** The full resume path: a legacy/stored value resolved by [resolveOnboardingStep], then the
     * flag applied, as SplashViewModel does. */
    @Test
    fun storedOverlay_resolvedThenFlagged() {
        val stored = resolveOnboardingStep("OVERLAY", legacyOnboardingComplete = false, legacyIntroPending = false)
        assertEquals(OnboardingStep.OVERLAY, applyOverlayFlag(stored, overlayEnabled = true))
        assertEquals(OnboardingStep.SET_DEFAULT_SMS, applyOverlayFlag(stored, overlayEnabled = false))
    }
}
