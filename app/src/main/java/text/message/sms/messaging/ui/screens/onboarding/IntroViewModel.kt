package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ads.AdUnitIds
import text.message.sms.messaging.ads.FullScreenAdGate
import text.message.sms.messaging.ads.InterstitialAdLoader
import text.message.sms.messaging.ads.NativeAdLoader
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import javax.inject.Inject

/**
 * Backs [IntroScreen], onboarding's last step (after Language's Apply, before the inbox).
 *
 * Ads, both started by [startAds] once consent settles -- never preloaded from Splash or Language:
 * - One native ad ([AdUnitIds.INTRO_NATIVE]) for the shared slot under slides 2 and 3 (slide 1
 *   shows the slot's reserved space empty). Requested at most twice per visit: the initial load,
 *   plus one refresh on first reaching slide 3 -- only if slide 2 was on screen with a loaded ad,
 *   so the refresh actually replaces an ad the user has already seen (see [onPageSettled]).
 * - An interstitial ([AdUnitIds.INTRO_INTERSTITIAL]) shown on the last slide's "Get started"
 *   ([onFinishClicked]).
 *
 * Onboarding is only marked complete once that interstitial is gone (or wasn't shown), so until
 * then Splash resumes here after a process death (the `intro_pending` flag Language set) and a
 * warm-resume App Open stays off (see [text.message.sms.messaging.ads.AppOpenAdManager]).
 */
@HiltViewModel
internal class IntroViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val adConsentManager: AdConsentManager,
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext context: Context,
    fullScreenAdGate: FullScreenAdGate,
) : ViewModel() {

    private val nativeAdLoader = NativeAdLoader(context, AdUnitIds.INTRO_NATIVE)
    val nativeAdState: StateFlow<NativeAdState> = nativeAdLoader.state

    private val interstitialLoader =
        InterstitialAdLoader(context, AdUnitIds.INTRO_INTERSTITIAL, fullScreenAdGate)

    private var adsStarted = false
    private var finishPressed = false
    private var settledPage = 0

    init {
        viewModelScope.launch {
            nativeAdLoader.state.collect { state ->
                when (state) {
                    NativeAdState.Loading -> Unit
                    is NativeAdState.Loaded -> {
                        debugLog("native loaded (on slide ${settledPage + 1})")
                        markSlide2AdSeenIfShowing()
                    }
                    NativeAdState.Failed -> debugLog("native failed or unavailable, slot stays empty")
                }
            }
        }
    }

    /** Idempotent. Waits for consent to resolve, then loads the native ad and preloads the
     * finish interstitial, or settles both on unavailable. */
    fun startAds() {
        if (adsStarted) return
        adsStarted = true
        viewModelScope.launch {
            val consent = adConsentManager.state.first { it != AdConsentState.Pending }
            when (consent) {
                AdConsentState.Allowed -> {
                    debugLog("consent Allowed, loading native + preloading interstitial")
                    nativeAdLoader.start()
                    interstitialLoader.start()
                }
                else -> {
                    debugLog("consent $consent, no intro ads")
                    nativeAdLoader.markUnavailable()
                    interstitialLoader.markUnavailable()
                }
            }
        }
    }

    /** The pager came to rest on [page] (0-based). Records that slide 2 showed a loaded ad, and
     * on first reaching slide 3 after that, refreshes the native ad -- its one and only refresh.
     * Both flags live in [savedStateHandle], like Language's, so rotation or process death
     * mid-screen doesn't re-arm the refresh. */
    fun onPageSettled(page: Int) {
        settledPage = page
        markSlide2AdSeenIfShowing()
        if (page != IntroPageCount - 1) return
        if (savedStateHandle.get<Boolean>(KEY_REFRESH_DONE) == true) return
        if (savedStateHandle.get<Boolean>(KEY_SLIDE2_AD_SEEN) != true) {
            debugLog("slide 3 reached, no refresh (slide 2 never showed a loaded ad)")
            return
        }
        savedStateHandle[KEY_REFRESH_DONE] = true
        val requested = nativeAdLoader.refresh()
        debugLog(if (requested) "slide 3 reached, native refresh requested" else "slide 3 reached, native refresh skipped")
    }

    private fun markSlide2AdSeenIfShowing() {
        if (settledPage == 1 && nativeAdLoader.state.value is NativeAdState.Loaded) {
            savedStateHandle[KEY_SLIDE2_AD_SEEN] = true
        }
    }

    /**
     * The last slide's "Get started": shows the preloaded interstitial if ready, then -- once it's
     * dismissed, fails to show, or straight away if it wasn't ready -- marks onboarding complete
     * (clearing `intro_pending`) and calls [onDone]. Taps after the first are ignored, so a quick
     * double-tap can't show twice or navigate under an opening ad.
     */
    fun onFinishClicked(activity: Activity?, onDone: () -> Unit) {
        if (finishPressed) return
        finishPressed = true
        interstitialLoader.commitToShow()
        val finish = {
            viewModelScope.launch {
                onboardingPreferences.completeIntro()
                onDone()
            }
            Unit
        }
        val shown = activity != null && interstitialLoader.showIfReady(activity, onFinished = {
            debugLog("interstitial finished, completing onboarding")
            finish()
        })
        if (shown) {
            debugLog("interstitial showing")
        } else {
            debugLog("interstitial missed (${interstitialLoader.state.value}), completing onboarding")
            finish()
        }
    }

    override fun onCleared() {
        nativeAdLoader.destroy()
        interstitialLoader.destroy()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "IntroViewModel"
        const val KEY_SLIDE2_AD_SEEN = "intro_slide2_ad_seen"
        const val KEY_REFRESH_DONE = "intro_native_refresh_done"
    }
}
