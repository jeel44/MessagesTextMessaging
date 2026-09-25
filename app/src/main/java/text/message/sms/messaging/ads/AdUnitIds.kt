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
}
