package text.message.sms.messaging.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeDecision.Show
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeDecision.Skip
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeSkipReason.ALREADY_SHOWN
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeSkipReason.ALREADY_SKIPPED
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeSkipReason.DISABLED
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeSkipReason.FAILED
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeSkipReason.LOADING
import text.message.sms.messaging.ui.screens.onboarding.FullScreenNativeSkipReason.NO_CONSENT

class IntroFullScreenNativeDecisionTest {

    private fun decide(
        enabled: Boolean = true,
        alreadyShown: Boolean = false,
        alreadySkipped: Boolean = false,
        consent: AdConsentState = AdConsentState.Allowed,
        load: FullScreenNativeLoad = FullScreenNativeLoad.LOADED,
    ) = decideIntroFullScreenNative(enabled, alreadyShown, alreadySkipped, consent, load)

    @Test
    fun loadedWithConsent_shows() = assertEquals(Show, decide())

    @Test
    fun stillLoading_skipsWithoutWaiting() = assertEquals(Skip(LOADING), decide(load = FullScreenNativeLoad.LOADING))

    @Test
    fun failed_skips() = assertEquals(Skip(FAILED), decide(load = FullScreenNativeLoad.FAILED))

    @Test
    fun consentPendingOrUnavailable_skips() {
        assertEquals(Skip(NO_CONSENT), decide(consent = AdConsentState.Pending, load = FullScreenNativeLoad.LOADING))
        assertEquals(Skip(NO_CONSENT), decide(consent = AdConsentState.Unavailable, load = FullScreenNativeLoad.FAILED))
    }

    @Test
    fun disabled_skipsEvenWhenLoaded() = assertEquals(Skip(DISABLED), decide(enabled = false))

    @Test
    fun alreadyShown_neverShowsTwice() = assertEquals(Skip(ALREADY_SHOWN), decide(alreadyShown = true))

    /** Going back to slide 1 after a skip doesn't bring up an ad that has loaded since. */
    @Test
    fun alreadySkipped_staysSkippedOnceLoaded() = assertEquals(Skip(ALREADY_SKIPPED), decide(alreadySkipped = true))
}
