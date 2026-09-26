package text.message.sms.messaging.ads

/**
 * AdMob ad unit IDs, one per placement -- separate units per slot so each reports its own
 * performance in the AdMob dashboard, even where two slots share a format.
 *
 * TEST IDS ONLY -- Google's official sample ad units
 * (https://developers.google.com/admob/android/test-ads), safe to build and run with but never to
 * be shipped alongside real ad traffic. Swap these for the app's real ad unit IDs in a separate,
 * config-only commit once they're available -- never mixed into the same commit as a behavior
 * change.
 */
internal object AdUnitIds {
    /** Call-end screen's primary slot (see [CallEndAdLoader]). */
    const val CALL_END_NATIVE = "ca-app-pub-3940256099942544/2247696110"

    /** Call-end screen's fixed 300x250 banner fallback (see [CallEndAdLoader]). */
    const val CALL_END_BANNER = "ca-app-pub-3940256099942544/6300978111"

    /** Welcome (first onboarding) screen's anchored adaptive banner. Google's adaptive-banner test
     * unit, not the fixed-size one above. */
    const val WELCOME_BANNER = "ca-app-pub-3940256099942544/9214589741"

    /** SetDefaultSms (onboarding) screen's anchored adaptive banner -- same adaptive-banner test
     * unit as [WELCOME_BANNER], but its own constant so each gets its own real unit later. */
    const val SET_DEFAULT_SMS_BANNER = "ca-app-pub-3940256099942544/9214589741"

    /** Interstitial shown once the default-SMS role and core permissions are granted, before
     * advancing to Language (see [InterstitialAdLoader]). Google's interstitial test unit. */
    const val SET_DEFAULT_SMS_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    /** Language screen's native ad (onboarding and Settings), below Apply (see [NativeAdLoader]). Same native
     * test unit as [CALL_END_NATIVE], but its own constant so each gets its own real unit later. */
    const val LANGUAGE_NATIVE = "ca-app-pub-3940256099942544/2247696110"

    /** Splash screen's compact native ad, shown only on launches that also hold for the App Open
     * ad (see [text.message.sms.messaging.ui.screens.onboarding.SplashViewModel]). Same native test
     * unit as [CALL_END_NATIVE], but its own constant so it gets its own real unit later. */
    const val SPLASH_NATIVE = "ca-app-pub-3940256099942544/2247696110"

    /** Interstitial shown on the Language screen's Apply (onboarding and Settings). */
    const val LANGUAGE_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    /** App-wide App Open ad -- cold start over Splash, and warm resumes (see [AppOpenAdManager]).
     * Google's App Open test unit. */
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}
