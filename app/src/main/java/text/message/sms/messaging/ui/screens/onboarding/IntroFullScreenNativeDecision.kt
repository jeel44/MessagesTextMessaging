package text.message.sms.messaging.ui.screens.onboarding

import text.message.sms.messaging.ads.AdConsentState

/** Where Intro's full-screen native load stands when slide 1's Next is tapped --
 * [text.message.sms.messaging.ads.NativeAdState]
 * without the ad itself, so [decideIntroFullScreenNative] stays a pure, JVM-testable function. */
internal enum class FullScreenNativeLoad { LOADING, LOADED, FAILED }

/** Why slide 1's Next went straight to slide 2 -- logged by [IntroFullScreenNativeFlow]. */
internal enum class FullScreenNativeSkipReason(val logText: String) {
    DISABLED("disabled in IntroAdConfig"),
    ALREADY_SHOWN("already shown this visit"),
    ALREADY_SKIPPED("already skipped once this visit"),
    NO_CONSENT("no consent"),
    FAILED("failed to load"),
}

internal sealed interface FullScreenNativeDecision {
    data object Show : FullScreenNativeDecision

    /** Still loading: put the overlay up with its shimmer and wait for the load, up to
     * [text.message.sms.messaging.config.IntroAdConfig.FULLSCREEN_NATIVE_MAX_WAIT_MILLIS]. */
    data object Wait : FullScreenNativeDecision

    data class Skip(val reason: FullScreenNativeSkipReason) : FullScreenNativeDecision
}

/**
 * Slide 1's Next: show the full-screen native, wait for it (still loading), or skip straight to
 * slide 2 (failed, no consent). One decision per Intro visit -- once it has been shown, or skipped
 * for any reason (a wait that timed out or failed included), every later Next on slide 1 skips
 * too, so going back to slide 1 never brings up an ad the first pass didn't.
 */
internal fun decideIntroFullScreenNative(
    enabled: Boolean,
    alreadyShown: Boolean,
    alreadySkipped: Boolean,
    consent: AdConsentState,
    load: FullScreenNativeLoad,
): FullScreenNativeDecision {
    val reason = when {
        !enabled -> FullScreenNativeSkipReason.DISABLED
        alreadyShown -> FullScreenNativeSkipReason.ALREADY_SHOWN
        alreadySkipped -> FullScreenNativeSkipReason.ALREADY_SKIPPED
        consent != AdConsentState.Allowed -> FullScreenNativeSkipReason.NO_CONSENT
        load == FullScreenNativeLoad.LOADING -> return FullScreenNativeDecision.Wait
        load == FullScreenNativeLoad.FAILED -> FullScreenNativeSkipReason.FAILED
        else -> return FullScreenNativeDecision.Show
    }
    return FullScreenNativeDecision.Skip(reason)
}
