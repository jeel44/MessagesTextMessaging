package text.message.sms.messaging.ads

/**
 * AdMob ad unit IDs for the call-end screen's ad slot (see [CallEndAdLoader]).
 *
 * TEST IDS ONLY -- Google's official sample ad units
 * (https://developers.google.com/admob/android/test-ads), safe to build and run with but never to
 * be shipped alongside real ad traffic. Swap these for the app's real ad unit IDs in a separate,
 * config-only commit once they're available -- never mixed into the same commit as a behavior
 * change.
 */
internal object AdUnitIds {
    const val CALL_END_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val CALL_END_BANNER = "ca-app-pub-3940256099942544/6300978111"
}
