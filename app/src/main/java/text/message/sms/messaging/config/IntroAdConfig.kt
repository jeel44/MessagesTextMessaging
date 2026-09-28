package text.message.sms.messaging.config

/**
 * Intro's full-screen native ad (shown on slide 1's Next, see
 * [text.message.sms.messaging.ui.screens.onboarding.IntroViewModel]).
 *
 * Hardcoded for now: edit and rebuild. No Remote Config.
 */
internal object IntroAdConfig {
    /** false: the full-screen native is never requested, and slide 1's Next always goes straight
     * to slide 2. */
    const val FULLSCREEN_NATIVE_ENABLED = true

    /** How long the full-screen native is on screen (assets rendered, shimmer gone) before its
     * close button appears. Back is blocked until then. */
    const val CLOSE_BUTTON_DELAY_MILLIS = 2000L

    /** Slide 1's Next while the full-screen native is still loading: how long its overlay waits,
     * shimmering, for the load before giving up and going to slide 2. The overlay has no close
     * button while it waits, so this is also the longest the user can be held there. */
    const val FULLSCREEN_NATIVE_MAX_WAIT_MILLIS = 3000L
}
