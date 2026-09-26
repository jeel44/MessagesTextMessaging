package text.message.sms.messaging.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app-wide App Open ad ([AdUnitIds.APP_OPEN]): one ad slot, shared by two show paths, both
 * decided at [ProcessLifecycleOwner] ON_START. Registered once from `MessagingApplication.onCreate`
 * ([register]).
 *
 * - **Launch** -- the foreground trip created a fresh [MainActivity] (no saved state), so Splash is
 *   about to run: the trip's eligibility is handed to Splash ([consumeLaunchEligibility]), which
 *   waits for the ad ([awaitAdForLaunch]) and shows it over itself ([showIfReady]). Covers true
 *   cold starts and relaunches after the user backed out.
 * - **Warm resume** -- an existing [MainActivity] came back, on whatever screen: shown here
 *   directly ([showOnWarmResume]). Skipped -- not queued; the next warm resume tries again --
 *   while onboarding is incomplete, while a full-screen ad is on screen or an interstitial is
 *   about to show ([FullScreenAdGate]), or when [MainActivity] isn't on top (never over
 *   CallEndActivity). A first foreground without Splash (a process-death restore, or a
 *   CallEndActivity process) never shows one.
 *
 * Neither path has a minimum time in the background, and neither fires for a trip the app
 * started itself ([markSelfInitiatedNavigation]: camera, pickers, dialer, links...) or a
 * deep-link return ([suppressNextForegroundAd]: notification/call-end hand-offs).
 *
 * Loads only after [AdConsentManager] allows it, and only while something may show the ad: an
 * eligible launch whose Splash is still waiting (until [endLaunch] or the app goes to the
 * background), or -- for warm resume -- once this process has created a [MainActivity] and
 * onboarding is complete ([warmResumeLoadWanted]). So a process the OS started for an incoming SMS
 * or a call-end screen never requests one. Reloads after every show, and after every show attempt
 * that found no usable ad; a loaded ad is kept up to 4 hours. A failed load isn't retried in a
 * loop; the next foreground or show attempt tries again. All state is touched on the main thread
 * only.
 */
@Singleton
class AppOpenAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adConsentManager: AdConsentManager,
    private val onboardingPreferences: OnboardingPreferences,
    private val fullScreenAdGate: FullScreenAdGate,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private sealed interface LoadState {
        data object Idle : LoadState
        data object Loading : LoadState
        data class Ready(val ad: AppOpenAd, val loadedAtMillis: Long) : LoadState
        data object Failed : LoadState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadState = MutableStateFlow<LoadState>(LoadState.Idle)

    private var registered = false
    private var isShowingAd = false
    private var onboardingComplete = false

    private var topActivity: Activity? = null
    private var mainActivityCreated = false

    /** Null until the process's first ON_STOP -- so a null at ON_START means first foreground. */
    private var backgroundedAtMillis: Long? = null
    private var freshMainActivityThisTrip = false
    private var selfNavigationArmed = false
    private var selfInitiatedTrip = false
    private var suppressNextForeground = false
    private var launchAdEligible = false

    /** An eligible launch's Splash may still want an ad -- one of the two reasons [maybeLoad]
     * requests one (the other: [warmResumeLoadWanted]). Unlike [launchAdEligible], not reset when
     * Splash reads its eligibility: Splash reads that before consent settles, and the load has to
     * start the moment consent allows it. */
    private var launchLoadWanted = false

    /** Warm resume may want an ad: this process has a [MainActivity] (so it isn't an SMS- or
     * call-end-only process) and onboarding is done (warm resume never shows before that). */
    private val warmResumeLoadWanted: Boolean
        get() = mainActivityCreated && onboardingComplete

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
        fullScreenAdGate.register(application)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            onboardingPreferences.isOnboardingComplete.collect {
                onboardingComplete = it
                if (it) maybeLoad()
            }
        }
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

    /** Splash is done waiting for this launch's App Open ad -- it showed one, won't show one
     * (deep link, consent timeout), or gave up waiting. Drops only Splash's own reason to load;
     * loading for warm resume carries on ([warmResumeLoadWanted]). An ad already loaded, or
     * still loading, is kept. */
    fun endLaunch() {
        if (launchLoadWanted) debugLog("launch ended, Splash no longer waiting")
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
     * on dismiss or show failure. Otherwise returns false and never calls [onFinished]. Either
     * way, a replacement is requested ([maybeLoad]) if anything may still show one. */
    fun showIfReady(activity: Activity, onFinished: () -> Unit): Boolean {
        if (isShowingAd) return false
        val ready = loadState.value as? LoadState.Ready
        if (ready != null) {
            debugLog("show attempt: ad age=${formatMillis(ready.ageMillis())} (expires at ${formatMillis(AD_EXPIRY_MILLIS)})")
        }
        if (ready == null || ready.isExpired()) {
            debugLog(if (ready == null) "not ready (${loadState.value}), skipping" else "expired, discarding")
            if (ready != null) loadState.value = LoadState.Idle
            maybeLoad()
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
                maybeLoad()
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

        if (freshMainActivityThisTrip) {
            // Splash is about to run and owns this launch.
            val eligible = backgroundedFor == null || !selfInitiated
            val reason = when {
                backgroundedFor == null -> "first foreground of process"
                selfInitiatedReason != null -> selfInitiatedReason
                else -> "back after ${formatMillis(backgroundedFor)}"
            }
            logForeground("launch", backgroundedFor, eligible, reason)
            launchAdEligible = eligible
            launchLoadWanted = eligible
            maybeLoad()
            return
        }

        // Warm resume.
        val eligible = backgroundedFor != null && !selfInitiated
        val reason = when {
            backgroundedFor == null -> "first foreground without Splash (process-death restore or CallEndActivity)"
            selfInitiatedReason != null -> selfInitiatedReason
            else -> "back after ${formatMillis(backgroundedFor)}"
        }
        logForeground("warm resume", backgroundedFor, eligible, reason)
        if (!eligible) {
            maybeLoad()
            return
        }
        // Posted so every Activity coming back in this trip has started first -- the checks in
        // showOnWarmResume then see the real top Activity, including an AdActivity over
        // MainActivity.
        mainHandler.post(::showOnWarmResume)
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

    /** The warm-resume show -- see the class doc for when it's skipped. A skipped resume is
     * dropped, not queued. */
    private fun showOnWarmResume() {
        val activity = topActivity
        val skipReason = when {
            !onboardingComplete -> "onboarding incomplete"
            fullScreenAdGate.isFullScreenAdBusy() -> "a full-screen ad is showing or about to show"
            activity !is MainActivity -> "top activity is ${activity?.javaClass?.simpleName}"
            else -> null
        }
        if (skipReason != null || activity == null) {
            debugLog("warm resume skipped: $skipReason")
            maybeLoad()
            return
        }
        showIfReady(activity, onFinished = {})
    }

    private fun maybeLoad() {
        if (!launchLoadWanted && !warmResumeLoadWanted) {
            debugLog(
                "load skipped: no Splash launch waiting, no warm resume possible yet " +
                    "(mainActivityCreated=$mainActivityCreated onboardingComplete=$onboardingComplete)",
            )
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

    private fun logForeground(path: String, backgroundedFor: Long?, eligible: Boolean, reason: String) {
        debugLog(
            "foreground ($path): backgroundedFor=${backgroundedFor?.let(::formatMillis) ?: "n/a"} " +
                "eligible=$eligible ($reason) consent=${adConsentManager.state.value} ad=${loadStateSummary()}",
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
        if (activity is MainActivity) {
            if (savedInstanceState == null) freshMainActivityThisTrip = true
            mainActivityCreated = true
            maybeLoad()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        topActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        topActivity = activity
        selfNavigationArmed = false
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (topActivity === activity) topActivity = null
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AppOpenAdManager"
        const val AD_EXPIRY_MILLIS = 4 * 60 * 60 * 1000L
    }
}
