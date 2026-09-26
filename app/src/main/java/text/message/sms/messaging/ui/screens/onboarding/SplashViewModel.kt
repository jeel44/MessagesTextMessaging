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
    /** Reading the onboarding flags; the system splash still covers this screen. */
    data object Deciding : SplashStep

    /** This screen's own branding and compact native ad are up while consent, then the native
     * ad, then the App Open ad resolve. */
    data object Holding : SplashStep

    /** An App Open ad is ready and the minimum branding time has passed -- show it. */
    data object ShowAd : SplashStep

    /** Leave Splash: to the inbox if [onboardingComplete], otherwise to Welcome. */
    data class Done(val onboardingComplete: Boolean) : SplashStep
}

/**
 * Backs [SplashScreen]: decides where the app goes (inbox or Welcome), and which ads the launch
 * shows on the way (see [AppOpenAdManager] for the app-wide App Open rules).
 *
 * - Deep-link launches (notification tap, call-end hand-off) and launches [AppOpenAdManager]
 *   didn't find eligible go straight to [SplashStep.Done], exactly as before -- no hold, no ads.
 * - Every other launch -- new users included -- enters [SplashStep.Holding], which shows the ads
 *   disclosure and a compact native ad ([AdUnitIds.SPLASH_NATIVE], [nativeAdState]), then runs:
 *   1. **Consent** -- up to [CONSENT_TIMEOUT_MILLIS] for UMP to settle. Time the consent form is
 *      on screen isn't counted (see [HoldClock]): it's modal, and leaving Splash wouldn't close it.
 *      A timeout skips this launch's ads; consent carries on, and any form lands over Welcome.
 *   2. **Native** -- up to [NATIVE_TIMEOUT_MILLIS] for it to load or fail. A timeout collapses the
 *      slot for good (a late ad never pops in).
 *   3. **App Open** -- only once a launch on this install has already got past Splash
 *      ([OnboardingPreferences.hasCompletedFirstLaunch]), per Google's guidance not to show one on
 *      a user's very first open. Up to [APP_OPEN_WAIT_MILLIS] more for it; its load started when
 *      consent allowed it, so this is usually short. Ready: [SplashStep.ShowAd] over the native
 *      ad, then [SplashStep.Done] once dismissed. Not ready: [SplashStep.Done] -- a late ad is kept
 *      for a later warm resume, never shown over the next screen.
 *
 *   All three draw on one [MAX_TOTAL_HOLD_MILLIS] budget (see [phaseTimeout]), and the hold lasts
 *   at least [MIN_HOLD_MILLIS] so the branding is seen before any full-screen ad.
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

    /** True from the start of a hold until Splash is left -- never flips back, so the layout
     * doesn't shift on the frame Splash navigates away. */
    val adSectionVisible: StateFlow<Boolean> = _adSectionVisible.asStateFlow()

    private val nativeAdLoader = NativeAdLoader(context, AdUnitIds.SPLASH_NATIVE)
    private val nativeTimedOut = MutableStateFlow(false)

    /** The compact slot's state -- [NativeAdState.Failed] (collapsed) for good once the native
     * phase times out or the consent phase gives up, whatever the loader does afterwards. */
    val nativeAdState: StateFlow<NativeAdState> =
        combine(nativeAdLoader.state, nativeTimedOut) { state, timedOut ->
            if (timedOut) NativeAdState.Failed else state
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NativeAdState.Loading)

    private var started = false
    private var adShowAttempted = false
    private var onboardingComplete = false
    private var firstLaunch = false

    /** Idempotent -- only the first call's [isDeepLinkLaunch] counts. */
    fun start(isDeepLinkLaunch: Boolean) {
        if (started) return
        started = true
        viewModelScope.launch {
            onboardingComplete = onboardingPreferences.isOnboardingComplete.first()
            // Completed onboarding implies an earlier launch, even from before this flag existed.
            firstLaunch = !onboardingComplete && !onboardingPreferences.hasCompletedFirstLaunch.first()
            // Always consumed, so a stale eligibility can never leak into a later Splash.
            val adEligible = appOpenAdManager.consumeLaunchEligibility()
            val skipReason = when {
                isDeepLinkLaunch -> "deep-link launch"
                !adEligible -> "not eligible at foreground (see AppOpenAdManager foreground line)"
                else -> null
            }
            if (skipReason != null) {
                debugLog("launch ads skipped: $skipReason")
                finish(SplashStep.Done(onboardingComplete))
                return@launch
            }
            debugLog(
                "launch ads eligible: onboardingComplete=$onboardingComplete firstLaunch=$firstLaunch, " +
                    "holding up to ${MAX_TOTAL_HOLD_MILLIS}ms (consent form time excluded)",
            )
            val clock = HoldClock()
            _adSectionVisible.value = true
            _step.value = SplashStep.Holding

            val consent = awaitConsent(clock)
            if (consent == null) {
                nativeTimedOut.value = true
                nativeAdLoader.markUnavailable()
                debugLog("consent timed out after ${clock.elapsed()}ms (limit ${CONSENT_TIMEOUT_MILLIS}ms), skipping ads")
                finish(SplashStep.Done(onboardingComplete))
                return@launch
            }
            debugLog("consent $consent after ${clock.elapsed()}ms (form time excluded: ${clock.excludedMillis}ms)")

            val nativeResult = withTimeoutOrNull(phaseTimeout(clock, NATIVE_TIMEOUT_MILLIS)) { awaitNativeAd() }
            if (nativeResult == null) {
                nativeTimedOut.value = true
                nativeAdLoader.markUnavailable()
            }
            debugLog("native ${nativeOutcome(nativeResult)} at ${clock.elapsed()}ms (phase limit ${NATIVE_TIMEOUT_MILLIS}ms)")

            val showAppOpen = awaitAppOpen(clock)

            val remaining = MIN_HOLD_MILLIS - clock.elapsed()
            if (remaining > 0) delay(remaining)
            finish(if (showAppOpen) SplashStep.ShowAd else SplashStep.Done(onboardingComplete))
        }
    }

    /** At most once per Splash; moves on to [SplashStep.Done] once the ad is gone, or right away
     * if it can't show. */
    fun showAd(activity: Activity?) {
        if (adShowAttempted) return
        adShowAttempted = true
        val done = { _step.value = SplashStep.Done(onboardingComplete) }
        val shown = activity != null && appOpenAdManager.showIfReady(activity, onFinished = done)
        if (!shown) done()
    }

    /** Moves to [next] -- on a first launch, only after recording that this install has now had
     * one, so the next launch sees it even if this process dies mid-ad or mid-onboarding. */
    private suspend fun finish(next: SplashStep) {
        if (firstLaunch) onboardingPreferences.setFirstLaunchCompleted()
        _step.value = next
    }

    /**
     * The settled consent state, or null if the network part took longer than
     * [CONSENT_TIMEOUT_MILLIS]. Once UMP has decided a form is needed
     * ([AdConsentManager.consentFormShowing]), waits for the user with no timeout, and that time
     * is excluded from [clock].
     */
    private suspend fun awaitConsent(clock: HoldClock): AdConsentState? {
        val beforeForm = withTimeoutOrNull(phaseTimeout(clock, CONSENT_TIMEOUT_MILLIS)) {
            combine(adConsentManager.state, adConsentManager.consentFormShowing) { state, formShowing ->
                state to formShowing
            }.first { (state, formShowing) -> state != AdConsentState.Pending || formShowing }
        } ?: return null
        if (beforeForm.first != AdConsentState.Pending) return beforeForm.first

        debugLog("consent form showing at ${clock.elapsed()}ms, waiting for the user (no timeout)")
        val formStart = SystemClock.elapsedRealtime()
        val settled = adConsentManager.state.first { it != AdConsentState.Pending }
        clock.exclude(SystemClock.elapsedRealtime() - formStart)
        return settled
    }

    /** Waits for consent, then for the native ad's first load to settle ([NativeAdState.Loaded]
     * or [NativeAdState.Failed]). Consent is already settled by the time this runs. */
    private suspend fun awaitNativeAd(): NativeAdState {
        if (adConsentManager.state.first { it != AdConsentState.Pending } == AdConsentState.Allowed) {
            nativeAdLoader.start()
        } else {
            nativeAdLoader.markUnavailable()
        }
        return nativeAdLoader.state.first { it !is NativeAdState.Loading }
    }

    /** Whether an App Open ad is ready to show within its phase's share of the budget. */
    private suspend fun awaitAppOpen(clock: HoldClock): Boolean {
        val phaseStart = clock.elapsed()
        val timeout = phaseTimeout(clock, APP_OPEN_WAIT_MILLIS)
        val result = withTimeoutOrNull(timeout) { appOpenAdManager.awaitAdForLaunch() }
        val waited = clock.elapsed() - phaseStart
        debugLog(
            when (result) {
                null -> "App Open timed out after ${waited}ms (limit ${timeout}ms)"
                true -> "App Open ready after ${waited}ms, showing"
                false -> "App Open unavailable after ${waited}ms (consent not Allowed or load failed/expired)"
            } + " -- total hold ${clock.elapsed()}ms",
        )
        return result == true
    }

    /** The one place the hold's total cap is enforced: a phase gets [phaseLimitMillis], or
     * whatever is left of [MAX_TOTAL_HOLD_MILLIS] on [clock] if that's less. */
    private fun phaseTimeout(clock: HoldClock, phaseLimitMillis: Long): Long =
        minOf(phaseLimitMillis, MAX_TOTAL_HOLD_MILLIS - clock.elapsed()).coerceAtLeast(0L)

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

    /** Time since the hold started, minus time the consent form was on screen -- what every
     * limit here is measured against. */
    private class HoldClock {
        private val start = SystemClock.elapsedRealtime()
        var excludedMillis = 0L
            private set

        fun exclude(millis: Long) {
            excludedMillis += millis
        }

        fun elapsed(): Long = SystemClock.elapsedRealtime() - start - excludedMillis
    }

    private companion object {
        const val TAG = "SplashViewModel"
        const val MIN_HOLD_MILLIS = 1_000L
        const val CONSENT_TIMEOUT_MILLIS = 3_000L
        const val NATIVE_TIMEOUT_MILLIS = 2_500L
        const val FIRST_LAUNCH_NATIVE_TIMEOUT_MILLIS = 4_000L
        const val APP_OPEN_WAIT_MILLIS = 2_500L
        const val MAX_TOTAL_HOLD_MILLIS = 6_000L
    }
}
