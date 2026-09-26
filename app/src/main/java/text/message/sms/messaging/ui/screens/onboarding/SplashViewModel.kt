package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.ads.AppOpenAdManager
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import javax.inject.Inject

/** Where [SplashScreen] is in its hand-off -- see [SplashViewModel]. */
internal sealed interface SplashStep {
    /** Reading the onboarding flag; the system splash still covers this screen. */
    data object Deciding : SplashStep

    /** Returning-user launch: this screen's own branding is up while the App Open ad loads. */
    data object Holding : SplashStep

    /** An App Open ad is ready and the minimum branding time has passed -- show it. */
    data object ShowAd : SplashStep

    data class Done(val onboardingComplete: Boolean) : SplashStep
}

/**
 * Backs [SplashScreen]: decides where the app goes, and whether a returning user's launch shows
 * an App Open ad on the way (see [AppOpenAdManager] for the app-wide rules).
 *
 * - New users (onboarding incomplete) and deep-link launches (notification tap, call-end hand-off)
 *   go straight to [SplashStep.Done], exactly as before -- no hold, no ad.
 * - Eligible returning-user launches hold [SplashStep.Holding] for at least [MIN_HOLD_MILLIS]
 *   (so the app's own branding is seen before any ad) and at most [MAX_HOLD_MILLIS] while the ad
 *   loads. Ready in time: [SplashStep.ShowAd], then [SplashStep.Done] once it's dismissed. Not
 *   ready: [SplashStep.Done] -- a late ad is kept for a later warm resume, never shown over the
 *   inbox.
 *
 * Lives on the Splash back-stack entry, so a rotation mid-hold or mid-ad never re-decides or
 * shows a second ad.
 */
@HiltViewModel
internal class SplashViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val appOpenAdManager: AppOpenAdManager,
) : ViewModel() {

    private val _step = MutableStateFlow<SplashStep>(SplashStep.Deciding)
    val step: StateFlow<SplashStep> = _step.asStateFlow()

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
                debugLog("launch ad skipped: $skipReason")
                _step.value = SplashStep.Done(onboardingComplete)
                return@launch
            }
            debugLog("launch ad eligible: holding up to ${MAX_HOLD_MILLIS}ms for the ad")
            _step.value = SplashStep.Holding
            val holdStart = SystemClock.elapsedRealtime()
            val waitResult = withTimeoutOrNull(MAX_HOLD_MILLIS) { appOpenAdManager.awaitAdForLaunch() }
            val waited = SystemClock.elapsedRealtime() - holdStart
            debugLog(
                when (waitResult) {
                    null -> "launch ad timed out after ${waited}ms (limit ${MAX_HOLD_MILLIS}ms), going to inbox"
                    true -> "launch ad ready after ${waited}ms, showing"
                    false -> "launch ad unavailable after ${waited}ms (consent not Allowed or load failed/expired)"
                },
            )
            val adReady = waitResult == true
            val remaining = MIN_HOLD_MILLIS - waited
            if (remaining > 0) delay(remaining)
            _step.value = if (adReady) SplashStep.ShowAd else SplashStep.Done(onboardingComplete = true)
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

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "SplashViewModel"
        const val MIN_HOLD_MILLIS = 1_000L
        const val MAX_HOLD_MILLIS = 3_500L
    }
}
