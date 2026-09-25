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
import com.google.android.gms.ads.AdActivity
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
 * The app-wide App Open ad ([AdUnitIds.APP_OPEN]), following Google's documented pattern: one
 * ad preloaded ahead of time, thrown away and reloaded once it's older than 4 hours, shown on an
 * app-level foreground transition observed through [ProcessLifecycleOwner] -- never on a plain
 * Activity resume (ProcessLifecycleOwner's own ~700ms stop delay already absorbs rotations and
 * Activity-to-Activity hops). Registered once from `MessagingApplication.onCreate` ([register]).
 *
 * Two show paths, both decided at ProcessLifecycleOwner ON_START:
 * - **Launch** -- the foreground trip created a fresh [MainActivity] (no saved state), so Splash
 *   is about to run: the decision is handed to Splash ([consumeLaunchEligibility]), which holds its
 *   own branding briefly and shows the ad over itself ([awaitAdForLaunch], [showIfReady]). Covers
 *   true cold starts and relaunches after the user backed out.
 * - **Warm resume** -- an existing [MainActivity] came back: shown here directly.
 *
 * A trip is eligible only if it's the process's first foreground, or the app spent at least
 * [MIN_BACKGROUND_MILLIS] in the background, and the trip wasn't one the app started itself
 * ([markSelfInitiatedNavigation]: camera, pickers, dialer, links...) or a deep-link return
 * ([suppressNextForegroundAd]: notification/call-end hand-offs). A warm resume additionally needs
 * onboarding complete, [MainActivity] on top (never over CallEndActivity), and no ad
 * ([AdActivity]) on screen -- this manager's own or any other placement's.
 *
 * Loads only after [AdConsentManager] allows it, and only in a process that has created a
 * [MainActivity] -- a process the OS started for an incoming SMS or a call-end screen never
 * requests one. A failed load isn't retried in a loop; the next foreground or launch tries again.
 * All state is touched on the main thread only.
 */
@Singleton
class AppOpenAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adConsentManager: AdConsentManager,
    private val onboardingPreferences: OnboardingPreferences,
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
    private var liveAdActivities = 0
    private var mainActivityCreated = false

    /** Null until the process's first ON_STOP -- so a null at ON_START means first foreground. */
    private var backgroundedAtMillis: Long? = null
    private var freshMainActivityThisTrip = false
    private var selfNavigationArmed = false
    private var selfInitiatedTrip = false
    private var suppressNextForeground = false
    private var launchAdEligible = false

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { onboardingPreferences.isOnboardingComplete.collect { onboardingComplete = it } }
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

    /** Splash's cold-start wait: true once an unexpired ad is ready, false as soon as consent
     * rules ads out or the load fails. Never times out on its own -- the caller bounds it. */
    suspend fun awaitAdForLaunch(): Boolean {
        if (adConsentManager.state.first { it != AdConsentState.Pending } != AdConsentState.Allowed) {
            return false
        }
        maybeLoad()
        val settled = loadState.first { it !is LoadState.Loading }
        return settled is LoadState.Ready && !settled.isExpired()
    }

    /** Shows the ready ad over [activity] and returns true -- [onFinished] then fires exactly once,
     * on dismiss or show failure. Otherwise returns false, never calls [onFinished], and kicks off
     * a load for next time. */
    fun showIfReady(activity: Activity, onFinished: () -> Unit): Boolean {
        if (isShowingAd) return false
        val ready = loadState.value as? LoadState.Ready
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
        backgroundedAtMillis = null
        selfInitiatedTrip = false
        suppressNextForeground = false

        val eligible = when {
            backgroundedAt == null -> true
            selfInitiated -> false
            else -> SystemClock.elapsedRealtime() - backgroundedAt >= MIN_BACKGROUND_MILLIS
        }
        debugLog(
            "foreground: firstForeground=${backgroundedAt == null} selfInitiated=$selfInitiated " +
                "eligible=$eligible freshMainActivity=$freshMainActivityThisTrip",
        )

        if (freshMainActivityThisTrip) {
            // Splash is about to run and owns this launch.
            launchAdEligible = eligible
            return
        }
        // First foreground without a Splash (a process-death restore, or CallEndActivity): no ad.
        if (backgroundedAt == null || !eligible) {
            maybeLoad()
            return
        }
        // Posted so every Activity coming back in this trip has started first -- the checks below
        // then see the real top Activity, including an AdActivity over MainActivity.
        mainHandler.post(::showOnWarmResume)
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAtMillis = SystemClock.elapsedRealtime()
        selfInitiatedTrip = selfNavigationArmed
        selfNavigationArmed = false
        suppressNextForeground = false
        freshMainActivityThisTrip = false
        launchAdEligible = false
    }

    private fun showOnWarmResume() {
        val activity = topActivity
        val skipReason = when {
            !onboardingComplete -> "onboarding incomplete"
            liveAdActivities > 0 -> "another ad is on screen"
            activity !is MainActivity -> "top activity is ${activity?.javaClass?.simpleName}"
            else -> null
        }
        if (skipReason != null || activity == null) {
            debugLog("warm resume skipped: $skipReason")
            return
        }
        showIfReady(activity, onFinished = {})
    }

    private fun maybeLoad() {
        if (!mainActivityCreated || adConsentManager.state.value != AdConsentState.Allowed) return
        when (val state = loadState.value) {
            LoadState.Loading -> return
            is LoadState.Ready -> if (!state.isExpired()) return
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

    private fun LoadState.Ready.isExpired(): Boolean =
        SystemClock.elapsedRealtime() - loadedAtMillis >= AD_EXPIRY_MILLIS

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is AdActivity) liveAdActivities++
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
        if (activity is AdActivity) liveAdActivities--
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
        const val MIN_BACKGROUND_MILLIS = 30_000L
        const val AD_EXPIRY_MILLIS = 4 * 60 * 60 * 1000L
    }
}
