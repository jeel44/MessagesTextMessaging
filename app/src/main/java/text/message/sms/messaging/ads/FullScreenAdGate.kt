package text.message.sms.messaging.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdActivity
import text.message.sms.messaging.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide "is a full-screen ad busy" signal, so [AppOpenAdManager]'s warm-resume App Open never
 * lands on top of, or races, an interstitial. Busy ([isFullScreenAdBusy]) while either:
 *
 * - an [AdActivity] is alive -- any full-screen AdMob ad on screen (every interstitial, and App
 *   Open itself), tracked here with no per-placement wiring; or
 * - an interstitial is pending -- [InterstitialAdLoader] marks itself from the moment its
 *   placement commits to showing ([InterstitialAdLoader.commitToShow], or
 *   [InterstitialAdLoader.showIfReady] itself) until the ad is dismissed, fails to show, isn't
 *   shown after all, or the loader is destroyed. Covers a placement's async gap between its
 *   trigger and the show call (Language's Apply), including one that ends up calling show while
 *   the app is in the background.
 * - an in-app full-screen ad is on screen -- one drawn inside MainActivity rather than in an
 *   [AdActivity] (Intro's full-screen native), marked by its owner via [inAppAdShowing] /
 *   [inAppAdSettled].
 *
 * Registered by [AppOpenAdManager.register]. All state is touched on the main thread only.
 */
@Singleton
class FullScreenAdGate @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var registered = false
    private var pendingInterstitials = 0
    private var liveAdActivities = 0
    private var inAppAdsShowing = 0

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
    }

    fun isFullScreenAdBusy(): Boolean = pendingInterstitials > 0 || liveAdActivities > 0 || inAppAdsShowing > 0

    /** An in-app full-screen ad is now on screen -- its owner pairs every call with exactly one
     * [inAppAdSettled]. */
    internal fun inAppAdShowing() {
        inAppAdsShowing++
        debugLog("in-app full-screen ad showing ($inAppAdsShowing showing)")
    }

    internal fun inAppAdSettled() {
        inAppAdsShowing = (inAppAdsShowing - 1).coerceAtLeast(0)
        debugLog("in-app full-screen ad settled ($inAppAdsShowing showing)")
    }

    /** Called by [InterstitialAdLoader] only -- each loader pairs every call with exactly one
     * [interstitialSettled]. */
    internal fun interstitialPending() {
        pendingInterstitials++
        debugLog("interstitial pending ($pendingInterstitials pending)")
    }

    internal fun interstitialSettled() {
        pendingInterstitials = (pendingInterstitials - 1).coerceAtLeast(0)
        debugLog("interstitial settled ($pendingInterstitials pending)")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is AdActivity) liveAdActivities++
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is AdActivity) liveAdActivities = (liveAdActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "FullScreenAdGate"
    }
}
