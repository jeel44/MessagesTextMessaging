package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ads.AdUnitIds
import text.message.sms.messaging.ads.AppOpenAdManager
import text.message.sms.messaging.ads.NativeAdLoader
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import javax.inject.Inject

/** Where [SplashScreen] is in its hand-off -- see [SplashViewModel]. */
internal sealed interface SplashStep {
    /** Reading the onboarding flag; the system splash still covers this screen. */
    data object Deciding : SplashStep

    /** Returning-user launch: this screen's own branding and compact native ad are up while the
     * native ad, then the App Open ad, resolve. */
    data object Holding : SplashStep

    /** An App Open ad is ready and the minimum branding time has passed -- show it. */
    data object ShowAd : SplashStep

    data class Done(val onboardingComplete: Boolean) : SplashStep
}

/**
 * Backs [SplashScreen]: decides where the app goes, and whether a returning user's launch shows
 * ads on the way (see [AppOpenAdManager] for the app-wide App Open rules).
 *
 * - New users (onboarding incomplete), deep-link launches (notification tap, call-end hand-off)
 *   and launches [AppOpenAdManager] didn't find eligible go straight to [SplashStep.Done],
 *   exactly as before -- no hold, no ads.
 * - Eligible launches enter [SplashStep.Holding], which shows the ads disclosure and a compact
 *   native ad ([AdUnitIds.SPLASH_NATIVE], [nativeAdState]), then run two phases in sequence:
 *   1. **Native** -- up to [NATIVE_TIMEOUT_MILLIS] for it to load or fail. A timeout collapses the
 *      slot for good (a late ad never pops in).
 *   2. **App Open** -- up to [APP_OPEN_WAIT_MILLIS] more for it. Its load started back in
 *      MainActivity.onCreate, so this is usually short. Ready: [SplashStep.ShowAd] over the
 *      native ad, then [SplashStep.Done] once dismissed. Not ready: [SplashStep.Done] -- a late
 *      ad is kept for a later warm resume, never shown over the inbox.
 *
 *   Both phases draw on one [MAX_TOTAL_HOLD_MILLIS] budget from the start of the hold (see
 *   [phaseTimeout]), and the hold always lasts at least [MIN_HOLD_MILLIS] so the branding is
 *   seen before any full-screen ad.
 *
 * Lives on the Splash back-stack entry, so a rotation mid-hold or mid-ad never re-decides, reloads
 * the native ad, or shows a second App Open ad.
 */
@HiltViewModel
internal class SplashViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val appOpenAdManager: AppOpenAdManager,
    private val adConsentManager: AdConsentManager,
    @param:ApplicationContext context: Context,
) : ViewModel() {

    private val _step = MutableStateFlow<SplashStep>(SplashStep.Deciding)
    val step: StateFlow<SplashStep> = _step.asStateFlow()

    private val _adSectionVisible = MutableStateFlow(false)

    /** True from the start of an eligible hold until Splash is left -- never flips back, so the
     * layout doesn't shift on the frame Splash navigates away. */
    val adSectionVisible: StateFlow<Boolean> = _adSectionVisible.asStateFlow()

    private val nativeAdLoader = NativeAdLoader(context, AdUnitIds.SPLASH_NATIVE)
    private val nativeTimedOut = MutableStateFlow(false)

    /** The compact slot's state -- [NativeAdState.Failed] (collapsed) for good once the native
     * phase times out, whatever the loader does afterwards. */
    val nativeAdState: StateFlow<NativeAdState> =
        combine(nativeAdLoader.state, nativeTimedOut) { state, timedOut ->
            if (timedOut) NativeAdState.Failed else state
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NativeAdState.Loading)

    private var started = false
    private var adShowAttempted = false

    /** Idempotent -- only the first call's [isDeepLinkLaunch] counts. */
    fun start(isDeepLinkLaunch: Boolean) {
        if (started) return
        started = true
        viewModelScope.launch {
            val onboardingComplete = onboardingPreferences.isOnboardingComplete.first()
            // Always consumed, so a stale eligibility can never leak into a later Splash.
            val adEligible = appOpenAdManager.consumeLaunchEligibility()
            val skipReason = when {
                !onboardingComplete -> "onboarding incomplete"
                isDeepLinkLaunch -> "deep-link launch"
                !adEligible -> "not eligible at foreground (see AppOpenAdManager foreground line)"
                else -> null
            }
            if (skipReason != null) {
                debugLog("launch ads skipped: $skipReason")
                _step.value = SplashStep.Done(onboardingComplete)
                return@launch
            }
            debugLog("launch ads eligible: holding up to ${MAX_TOTAL_HOLD_MILLIS}ms")
            val holdStart = SystemClock.elapsedRealtime()
            _adSectionVisible.value = true
            _step.value = SplashStep.Holding

            val nativeResult = withTimeoutOrNull(phaseTimeout(holdStart, NATIVE_TIMEOUT_MILLIS)) { awaitNativeAd() }
            if (nativeResult == null) {
                nativeTimedOut.value = true
                nativeAdLoader.markUnavailable()
            }
            debugLog(
                "native ${nativeOutcome(nativeResult)} after ${elapsedSince(holdStart)}ms " +
                    "(limit ${NATIVE_TIMEOUT_MILLIS}ms), trying App Open",
            )

            val appOpenStart = SystemClock.elapsedRealtime()
            val appOpenTimeout = phaseTimeout(holdStart, APP_OPEN_WAIT_MILLIS)
            val appOpenResult = withTimeoutOrNull(appOpenTimeout) { appOpenAdManager.awaitAdForLaunch() }
            val appOpenWaited = elapsedSince(appOpenStart)
            debugLog(
                when (appOpenResult) {
                    null -> "App Open timed out after ${appOpenWaited}ms (limit ${appOpenTimeout}ms), going to inbox"
                    true -> "App Open ready after ${appOpenWaited}ms, showing"
                    false -> "App Open unavailable after ${appOpenWaited}ms (consent not Allowed or load failed/expired)"
                } + " -- total hold ${elapsedSince(holdStart)}ms",
            )

            val remaining = MIN_HOLD_MILLIS - elapsedSince(holdStart)
            if (remaining > 0) delay(remaining)
            _step.value = if (appOpenResult == true) SplashStep.ShowAd else SplashStep.Done(onboardingComplete = true)
        }
    }

    /** At most once per Splash; moves on to [SplashStep.Done] once the ad is gone, or right away
     * if it can't show. */
    fun showAd(activity: Activity?) {
        if (adShowAttempted) return
        adShowAttempted = true
        val done = { _step.value = SplashStep.Done(onboardingComplete = true) }
        val shown = activity != null && appOpenAdManager.showIfReady(activity, onFinished = done)
        if (!shown) done()
    }

    /** Waits for consent, then for the native ad's first load to settle ([NativeAdState.Loaded]
     * or [NativeAdState.Failed]). */
    private suspend fun awaitNativeAd(): NativeAdState {
        if (adConsentManager.state.first { it != AdConsentState.Pending } == AdConsentState.Allowed) {
            nativeAdLoader.start()
        } else {
            nativeAdLoader.markUnavailable()
        }
        return nativeAdLoader.state.first { it !is NativeAdState.Loading }
    }

    /** The one place the hold's total cap is enforced: a phase gets [phaseLimitMillis], or
     * whatever is left of [MAX_TOTAL_HOLD_MILLIS] since [holdStart] if that's less. */
    private fun phaseTimeout(holdStart: Long, phaseLimitMillis: Long): Long =
        minOf(phaseLimitMillis, MAX_TOTAL_HOLD_MILLIS - elapsedSince(holdStart)).coerceAtLeast(0L)

    private fun elapsedSince(start: Long): Long = SystemClock.elapsedRealtime() - start

    private fun nativeOutcome(result: NativeAdState?): String = when (result) {
        null -> "timed out"
        is NativeAdState.Loaded -> "loaded"
        else -> "failed"
    }

    override fun onCleared() {
        nativeAdLoader.destroy()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "SplashViewModel"
        const val MIN_HOLD_MILLIS = 1_000L
        const val NATIVE_TIMEOUT_MILLIS = 2_500L
        const val APP_OPEN_WAIT_MILLIS = 2_500L
        const val MAX_TOTAL_HOLD_MILLIS = 5_000L
    }
}
