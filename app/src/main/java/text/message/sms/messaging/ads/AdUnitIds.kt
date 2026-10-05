package text.message.sms.messaging.ads

import android.util.Log
import text.message.sms.messaging.BuildConfig

/**
 * AdMob ad unit IDs, one per placement -- separate units per slot so each reports its own
 * performance in the AdMob dashboard, even where two slots share a format. The only place an ad
 * unit ID may come from: every loader reads its ID through one of the properties below.
 *
 * TEST vs REAL IDS: THE SWITCH IS NOT IN THIS FILE. It is `val useTestAds` near the top of
 * `app/build.gradle.kts` -- the manifest's AdMob App ID has to switch together with these, and the
 * manifest can only be driven from Gradle. [USE_TEST_ADS] just mirrors it.
 *
 * Each placement holds both IDs (see [AdPlacement]): Google's official sample unit for its format
 * (https://developers.google.com/admob/android/test-ads) and this app's real unit. A real ID that
 * hasn't been created in the AdMob console yet is a `REPLACE_ME_<PLACEMENT>` placeholder; asking
 * for one while [USE_TEST_ADS] is false throws in debug builds and logs an error in release, where
 * the load then fails like any other no-fill. The real AdMob App ID lives next to the switch in
 * `app/build.gradle.kts`.
 */
internal object AdUnitIds {
    /** true = Google's test IDs (safe: no real revenue, no policy risk). false = this app's real
     * IDs -- only for a release build meant to be published. Set by `useTestAds` in
     * `app/build.gradle.kts`, never here. */
    const val USE_TEST_ADS: Boolean = BuildConfig.USE_TEST_ADS

    /** Call-end screen's primary slot (see [CallEndAdLoader]). */
    val CALL_END_NATIVE: String get() = AdPlacement.CALL_END_NATIVE.adUnitId()

    /** Call-end screen's fixed 300x250 banner fallback (see [CallEndAdLoader]). */
    val CALL_END_BANNER: String get() = AdPlacement.CALL_END_BANNER.adUnitId()

    /** Welcome (first onboarding) screen's anchored adaptive banner. */
    val WELCOME_BANNER: String get() = AdPlacement.WELCOME_BANNER.adUnitId()

    /** SetDefaultSms (onboarding) screen's anchored adaptive banner. */
    val SET_DEFAULT_SMS_BANNER: String get() = AdPlacement.SET_DEFAULT_SMS_BANNER.adUnitId()

    /** Home (conversation list) screen's bottom anchored adaptive banner (see
     * [HomeBannerAdManager]). */
    val HOME_BANNER: String get() = AdPlacement.HOME_BANNER.adUnitId()

    /** Interstitial shown once the default-SMS role and core permissions are granted, before
     * advancing to Language (see [InterstitialAdLoader]). */
    val SET_DEFAULT_SMS_INTERSTITIAL: String get() = AdPlacement.SET_DEFAULT_SMS_INTERSTITIAL.adUnitId()

    /** Language screen's native ad (onboarding and Settings), below Apply (see [NativeAdLoader]). */
    val LANGUAGE_NATIVE: String get() = AdPlacement.LANGUAGE_NATIVE.adUnitId()

    /** Splash screen's compact native ad, shown only on launches that also hold for the App Open
     * ad (see [text.message.sms.messaging.ui.screens.onboarding.SplashViewModel]). */
    val SPLASH_NATIVE: String get() = AdPlacement.SPLASH_NATIVE.adUnitId()

    /** Chat screen's compact native ad, under a non-personal thread's Learn More row (see
     * [text.message.sms.messaging.ui.screens.chat.ChatViewModel]). */
    val CHAT_NATIVE: String get() = AdPlacement.CHAT_NATIVE.adUnitId()

    /** Interstitial shown on the Language screen's Apply -- Settings only; onboarding's Apply
     * moves on to Intro without one (see [INTRO_INTERSTITIAL]). */
    val LANGUAGE_INTERSTITIAL: String get() = AdPlacement.LANGUAGE_INTERSTITIAL.adUnitId()

    /** Intro (onboarding, after Language) screen's native ad, shared by its 2nd and 3rd slides (see
     * [text.message.sms.messaging.ui.screens.onboarding.IntroViewModel]). */
    val INTRO_NATIVE: String get() = AdPlacement.INTRO_NATIVE.adUnitId()

    /** Intro's full-screen native ad, shown on slide 1's Next (see
     * [text.message.sms.messaging.ui.screens.onboarding.IntroViewModel]). Its test unit is Google's
     * native *video* one, so the full-screen media slot is exercised with video as well as images. */
    val INTRO_NATIVE_FULLSCREEN: String get() = AdPlacement.INTRO_NATIVE_FULLSCREEN.adUnitId()

    /** Interstitial shown on Intro's last "Get started", before onboarding completes. */
    val INTRO_INTERSTITIAL: String get() = AdPlacement.INTRO_INTERSTITIAL.adUnitId()

    /** Interstitial shown before a chat opens from the inbox, on the visits
     * [ChatInterstitialConfig.CYCLE] marks [ChatVisitAd.ENTER_AD] (see [ChatInterstitialManager]). */
    val CHAT_ENTER_INTERSTITIAL: String get() = AdPlacement.CHAT_ENTER_INTERSTITIAL.adUnitId()

    /** Interstitial shown over the inbox after leaving a chat, on the visits
     * [ChatInterstitialConfig.CYCLE] marks [ChatVisitAd.EXIT_AD] (see [ChatInterstitialManager]). */
    val CHAT_EXIT_INTERSTITIAL: String get() = AdPlacement.CHAT_EXIT_INTERSTITIAL.adUnitId()

    /** App Open ad -- shown only over Splash, as the last step of a launch (see [AppOpenAdManager]). */
    val APP_OPEN: String get() = AdPlacement.APP_OPEN.adUnitId()

    private const val TAG = "AdUnitIds"

    private fun AdPlacement.adUnitId(): String =
        resolveAdUnitId(testId, realId, USE_TEST_ADS) {
            val message = "$name has no real ad unit ID yet ($realId) but useTestAds is false -- " +
                "fill it in in AdUnitIds.kt, or set useTestAds = true in app/build.gradle.kts"
            if (BuildConfig.DEBUG) error(message)
            Log.e(TAG, message)
        }
}

/** Google's sample ad units, one per format -- what every placement of that format requests while
 * [AdUnitIds.USE_TEST_ADS] is true. */
private object GoogleTestAdUnits {
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val NATIVE_VIDEO = "ca-app-pub-3940256099942544/1044960115"
    const val BANNER_FIXED_SIZE = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_ADAPTIVE = "ca-app-pub-3940256099942544/9214589741"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}

/**
 * Every ad placement's two IDs. [realId] is the unit created for that placement in the AdMob
 * console (`ca-app-pub-<publisher>/<unit>`) -- replace each `REPLACE_ME_<PLACEMENT>` with it; the
 * placements are documented on their [AdUnitIds] properties.
 */
internal enum class AdPlacement(val testId: String, val realId: String) {
    CALL_END_NATIVE(GoogleTestAdUnits.NATIVE, "REPLACE_ME_CALL_END_NATIVE"),
    CALL_END_BANNER(GoogleTestAdUnits.BANNER_FIXED_SIZE, "REPLACE_ME_CALL_END_BANNER"),
    WELCOME_BANNER(GoogleTestAdUnits.BANNER_ADAPTIVE, "REPLACE_ME_WELCOME_BANNER"),
    SET_DEFAULT_SMS_BANNER(GoogleTestAdUnits.BANNER_ADAPTIVE, "REPLACE_ME_SET_DEFAULT_SMS_BANNER"),
    HOME_BANNER(GoogleTestAdUnits.BANNER_ADAPTIVE, "REPLACE_ME_HOME_BANNER"),
    SET_DEFAULT_SMS_INTERSTITIAL(GoogleTestAdUnits.INTERSTITIAL, "REPLACE_ME_SET_DEFAULT_SMS_INTERSTITIAL"),
    LANGUAGE_NATIVE(GoogleTestAdUnits.NATIVE, "REPLACE_ME_LANGUAGE_NATIVE"),
    SPLASH_NATIVE(GoogleTestAdUnits.NATIVE, "REPLACE_ME_SPLASH_NATIVE"),
    CHAT_NATIVE(GoogleTestAdUnits.NATIVE, "REPLACE_ME_CHAT_NATIVE"),
    LANGUAGE_INTERSTITIAL(GoogleTestAdUnits.INTERSTITIAL, "REPLACE_ME_LANGUAGE_INTERSTITIAL"),
    INTRO_NATIVE(GoogleTestAdUnits.NATIVE, "REPLACE_ME_INTRO_NATIVE"),
    INTRO_NATIVE_FULLSCREEN(GoogleTestAdUnits.NATIVE_VIDEO, "REPLACE_ME_INTRO_NATIVE_FULLSCREEN"),
    INTRO_INTERSTITIAL(GoogleTestAdUnits.INTERSTITIAL, "REPLACE_ME_INTRO_INTERSTITIAL"),
    APP_OPEN(GoogleTestAdUnits.APP_OPEN, "REPLACE_ME_APP_OPEN"),
    CHAT_ENTER_INTERSTITIAL(GoogleTestAdUnits.INTERSTITIAL, "REPLACE_ME_CHAT_ENTER_INTERSTITIAL"),
    CHAT_EXIT_INTERSTITIAL(GoogleTestAdUnits.INTERSTITIAL, "REPLACE_ME_CHAT_EXIT_INTERSTITIAL"),
}

/** The prefix of a real ID that hasn't been filled in yet. */
internal const val AD_ID_PLACEHOLDER_PREFIX = "REPLACE_ME"

/**
 * Which of a placement's two IDs to request: [testId] while [useTestAds], else [realId].
 * [onPlaceholderRealId] runs first when [realId] is what's wanted but is still a placeholder; if
 * it returns, [realId] is handed back as-is and the request for it simply fails. A pure function
 * of its arguments, so tests can drive both flag states without touching the real switch.
 */
internal fun resolveAdUnitId(
    testId: String,
    realId: String,
    useTestAds: Boolean,
    onPlaceholderRealId: () -> Unit,
): String {
    if (useTestAds) return testId
    if (realId.startsWith(AD_ID_PLACEHOLDER_PREFIX)) onPlaceholderRealId()
    return realId
}
