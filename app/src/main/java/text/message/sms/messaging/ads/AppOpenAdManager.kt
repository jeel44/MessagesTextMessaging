package text.message.sms.messaging.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app-wide App Open ad ([AdUnitIds.APP_OPEN]). Shown **only by Splash**, as the last step of
 * its launch sequence -- never on a warm resume (switching away and back to an existing screen),
 * whatever screen is on top. Registered once from `MessagingApplication.onCreate` ([register]).
 *
 * A launch is a foreground trip, observed through [ProcessLifecycleOwner] ON_START, that created a
 * fresh [MainActivity] (no saved state) -- so Splash is about to run. Covers true cold starts and
 * relaunches after the user backed out; a process-death restore (saved state) returns to its old
 * screen and never counts. The trip's eligibility is handed to Splash
 * ([consumeLaunchEligibility]), which waits for the ad ([awaitAdForLaunch]) and shows it over
 * itself ([showIfReady]). A trip is eligible only if it's the process's first foreground, or the
 * app spent at least [MIN_BACKGROUND_MILLIS] in the background, and the trip wasn't one the app
 * started itself ([markSelfInitiatedNavigation]: camera, pickers, dialer, links...) or a
 * deep-link return ([suppressNextForegroundAd]: notification/call-end hand-offs).
 *
 * Loads only while an eligible launch still wants an ad -- from its ON_START until Splash says
 * it's done ([endLaunch]) or the app goes to the background -- and only after [AdConsentManager]
 * allows it, so no request goes out for an ad that could never be shown. An ad that arrives after
 * Splash gave up is kept (up to 4 hours) for the next launch in the same process. A failed load
 * isn't retried; the next launch tries again. All state is touched on the main thread only.
 */
@Singleton
class AppOpenAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adConsentManager: AdConsentManager,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private sealed interface LoadState {
        data object Idle : LoadState
        data object Loading : LoadState
        data class Ready(val ad: AppOpenAd, val loadedAtMillis: Long) : LoadState
        data object Failed : LoadState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loadState = MutableStateFlow<LoadState>(LoadState.Idle)

    private var registered = false
    private var isShowingAd = false

    /** Null until the process's first ON_STOP -- so a null at ON_START means first foreground. */
    private var backgroundedAtMillis: Long? = null
    private var freshMainActivityThisTrip = false
    private var selfNavigationArmed = false
    private var selfInitiatedTrip = false
    private var suppressNextForeground = false
    private var launchAdEligible = false

    /** An eligible launch's Splash may still want an ad -- the only time [maybeLoad] requests one.
     * Unlike [launchAdEligible], not reset when Splash reads its eligibility: Splash reads that
     * before consent settles, and the load has to start the moment consent allows it. */
    private var launchLoadWanted = false

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            adConsentManager.state.collect { if (it == AdConsentState.Allowed) maybeLoad() }
        }
    }

    /** MainActivity is about to launch another app's Activity (camera, picker, dialer, link...):
     * the foreground trip back from it must not show an ad. Dropped if one of this app's
     * Activities resumes before the app actually goes to the background (a dialog-style target,
     * or a launch that never left the app). */
    fun markSelfInitiatedNavigation() {
        selfNavigationArmed = true
    }

    /** MainActivity received a deep-link intent (notification tap, call-end hand-off) while in
     * the background: the user is headed somewhere specific, so the coming foreground shows no
     * ad. Cleared at the next ON_STOP if no foreground transition consumed it. */
    fun suppressNextForegroundAd() {
        suppressNextForeground = true
    }

    /** Splash only, once per Splash: whether the launch that created it may show an App Open ad
     * (see the class doc), then resets. Waits for ON_START so the decision has been made. */
    suspend fun consumeLaunchEligibility(): Boolean {
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
        return launchAdEligible.also { launchAdEligible = false }
    }

    /** Splash is done with this launch's App Open ad -- it showed one, won't show one (deep
     * link, consent timeout), or gave up waiting: no further loads until the next launch. An ad
     * already loaded, or still loading, is kept. */
    fun endLaunch() {
        if (launchLoadWanted) debugLog("launch ended, no further loads")
        launchLoadWanted = false
    }

    /** Splash's cold-start wait: true once an unexpired ad is ready, false as soon as consent
     * rules ads out or the load fails. Never times out on its own -- the caller bounds it. */
    suspend fun awaitAdForLaunch(): Boolean {
        val consent = adConsentManager.state.first { it != AdConsentState.Pending }
        if (consent != AdConsentState.Allowed) {
            debugLog("launch wait: consent=$consent, no ad")
            return false
        }
        maybeLoad()
        val settled = loadState.first { it !is LoadState.Loading }
        return settled is LoadState.Ready && !settled.isExpired()
    }

    /** Shows the ready ad over [activity] and returns true -- [onFinished] then fires exactly once,
     * on dismiss or show failure. Otherwise returns false and never calls [onFinished]. Never
     * loads a replacement: the next ad is requested by the next launch that wants one. */
    fun showIfReady(activity: Activity, onFinished: () -> Unit): Boolean {
        if (isShowingAd) return false
        val ready = loadState.value as? LoadState.Ready
        if (ready != null) {
            debugLog("show attempt: ad age=${formatMillis(ready.ageMillis())} (expires at ${formatMillis(AD_EXPIRY_MILLIS)})")
        }
        if (ready == null || ready.isExpired()) {
            debugLog(if (ready == null) "not ready (${loadState.value}), skipping" else "expired, discarding")
            if (ready != null) loadState.value = LoadState.Idle
            return false
        }
        isShowingAd = true
        loadState.value = LoadState.Idle
        var finished = false
        val finishOnce = {
            if (!finished) {
                finished = true
                isShowingAd = false
                ready.ad.fullScreenContentCallback = null
                onFinished()
            }
        }
        ready.ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                debugLog("dismissed")
                finishOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                debugLog("failed to show: ${adError.code} ${adError.message}")
                finishOnce()
            }
        }
        debugLog("showing")
        ready.ad.show(activity)
        return true
    }

    override fun onStart(owner: LifecycleOwner) {
        val backgroundedAt = backgroundedAtMillis
        val selfInitiated = selfInitiatedTrip || suppressNextForeground
        val selfInitiatedReason = when {
            selfInitiatedTrip -> "self-initiated navigation"
            suppressNextForeground -> "deep-link return"
            else -> null
        }
        backgroundedAtMillis = null
        selfInitiatedTrip = false
        suppressNextForeground = false

        val backgroundedFor = backgroundedAt?.let { SystemClock.elapsedRealtime() - it }
        val eligible = when {
            backgroundedFor == null -> true
            selfInitiated -> false
            else -> backgroundedFor >= MIN_BACKGROUND_MILLIS
        }
        val reason = when {
            backgroundedFor == null -> "first foreground of process"
            selfInitiatedReason != null -> selfInitiatedReason
            eligible -> "background ${formatMillis(backgroundedFor)} >= ${formatMillis(MIN_BACKGROUND_MILLIS)}"
            else -> "background ${formatMillis(backgroundedFor)} < ${formatMillis(MIN_BACKGROUND_MILLIS)}"
        }
        debugLog(
            "foreground: firstForeground=${backgroundedAt == null} selfInitiated=$selfInitiated " +
                "backgroundedFor=${backgroundedFor?.let(::formatMillis) ?: "n/a"} " +
                "eligible=$eligible ($reason) freshMainActivity=$freshMainActivityThisTrip " +
                "consent=${adConsentManager.state.value} ad=${loadStateSummary()}",
        )

        // Anything else -- a warm resume to an existing screen, a process-death restore, a
        // CallEndActivity process -- never shows an App Open ad, so never loads one either.
        if (!freshMainActivityThisTrip) return
        // Splash is about to run and owns this launch.
        launchAdEligible = eligible
        launchLoadWanted = eligible
        maybeLoad()
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAtMillis = SystemClock.elapsedRealtime()
        selfInitiatedTrip = selfNavigationArmed
        selfNavigationArmed = false
        suppressNextForeground = false
        freshMainActivityThisTrip = false
        launchAdEligible = false
        launchLoadWanted = false
    }

    private fun maybeLoad() {
        if (!launchLoadWanted) {
            debugLog("load skipped: no Splash launch waiting for an ad")
            return
        }
        val consent = adConsentManager.state.value
        if (consent != AdConsentState.Allowed) {
            debugLog("load skipped: consent=$consent (needs Allowed)")
            return
        }
        when (val state = loadState.value) {
            LoadState.Loading -> return
            is LoadState.Ready -> {
                if (!state.isExpired()) return
                debugLog("cached ad expired (age=${formatMillis(state.ageMillis())}), reloading")
            }
            LoadState.Idle, LoadState.Failed -> Unit
        }
        loadState.value = LoadState.Loading
        debugLog("loading")
        AppOpenAd.load(
            context,
            AdUnitIds.APP_OPEN,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    debugLog("loaded")
                    loadState.value = LoadState.Ready(ad, SystemClock.elapsedRealtime())
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    debugLog("failed to load: ${adError.code} ${adError.message}")
                    loadState.value = LoadState.Failed
                }
            },
        )
    }

    private fun LoadState.Ready.ageMillis(): Long = SystemClock.elapsedRealtime() - loadedAtMillis

    private fun LoadState.Ready.isExpired(): Boolean = ageMillis() >= AD_EXPIRY_MILLIS

    private fun loadStateSummary(): String = when (val state = loadState.value) {
        is LoadState.Ready -> "Ready(age=${formatMillis(state.ageMillis())}, expired=${state.isExpired()})"
        else -> state.toString()
    }

    private fun formatMillis(millis: Long): String = when {
        millis < 60_000L -> "%.1fs".format(millis / 1000.0)
        millis < 3_600_000L -> "%dm%02ds".format(millis / 60_000L, millis % 60_000L / 1000L)
        else -> "%dh%02dm".format(millis / 3_600_000L, millis % 3_600_000L / 60_000L)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is MainActivity && savedInstanceState == null) freshMainActivityThisTrip = true
    }

    override fun onActivityResumed(activity: Activity) {
        selfNavigationArmed = false
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AppOpenAdManager"
        const val MIN_BACKGROUND_MILLIS = 30_000L
        const val AD_EXPIRY_MILLIS = 4 * 60 * 60 * 1000L
    }
}
