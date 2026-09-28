package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.config.IntroAdConfig
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ads.AdUnitIds
import text.message.sms.messaging.ads.FullScreenAdGate
import text.message.sms.messaging.ads.InterstitialAdLoader
import text.message.sms.messaging.ads.NativeAdLoader
import text.message.sms.messaging.ads.NativeAdState
import javax.inject.Inject

/**
 * Backs [IntroScreen], onboarding's second step (after Language's Apply, before Welcome).
 *
 * Ads, all started by [startAds] once consent settles -- never preloaded from Splash or Language:
 * - One native ad ([AdUnitIds.INTRO_NATIVE]) for the shared slot under slides 2 and 3 (slide 1
 *   shows the slot's reserved space empty). Requested at most twice per visit: the initial load,
 *   plus one refresh on first reaching slide 3 -- only if slide 2 was on screen with a loaded ad,
 *   so the refresh actually replaces an ad the user has already seen (see [onPageSettled]).
 * - A full-screen native ([AdUnitIds.INTRO_NATIVE_FULLSCREEN], if
 *   [IntroAdConfig.FULLSCREEN_NATIVE_ENABLED]) shown over slide 1 on its Next, then slide 2 once
 *   it's closed ([onFirstSlideNext], [IntroFullScreenNativeFlow]). Requested once per visit, right
 *   after the slot's native. Still loading at Next: its overlay comes up shimmering and waits up
 *   to [IntroAdConfig.FULLSCREEN_NATIVE_MAX_WAIT_MILLIS] for it, then goes to slide 2 if it
 *   hasn't come. Failed or no consent: Next goes straight to slide 2.
 * - An interstitial ([AdUnitIds.INTRO_INTERSTITIAL]) shown on the last slide's "Get started"
 *   ([onFinishClicked]).
 *
 * The next step (Welcome) is only stored once that interstitial is gone (or wasn't shown), so until
 * then Splash resumes here after a process death (see [OnboardingProgressViewModel]).
 *
 * The full-screen native survives rotation (this ViewModel holds it). After a process death while
 * it was showing, the ad is gone: the restored screen moves on to slide 2 and never shows it again
 * ([fullScreenAdvancePending]).
 */
@HiltViewModel
internal class IntroViewModel @Inject constructor(
    private val adConsentManager: AdConsentManager,
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext context: Context,
    private val fullScreenAdGate: FullScreenAdGate,
) : ViewModel() {

    private val nativeAdLoader = NativeAdLoader(context, AdUnitIds.INTRO_NATIVE)
    val nativeAdState: StateFlow<NativeAdState> = nativeAdLoader.state

    private val fullScreenNativeLoader = NativeAdLoader(context, AdUnitIds.INTRO_NATIVE_FULLSCREEN)

    private var fullScreenInGate = false
    private var fullScreenDisplayed = false

    private val fullScreenNative = IntroFullScreenNativeFlow(
        scope = viewModelScope,
        savedStateHandle = savedStateHandle,
        load = fullScreenNativeLoader.state
            .map { it.toFullScreenLoad() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, fullScreenNativeLoader.state.value.toFullScreenLoad()),
        loadedAd = { (fullScreenNativeLoader.state.value as? NativeAdState.Loaded)?.nativeAd },
        consent = { adConsentManager.state.value },
        enabled = IntroAdConfig.FULLSCREEN_NATIVE_ENABLED,
        maxWaitMillis = IntroAdConfig.FULLSCREEN_NATIVE_MAX_WAIT_MILLIS,
        onOverlayUp = {
            if (!fullScreenInGate) {
                fullScreenInGate = true
                fullScreenAdGate.inAppAdShowing()
            }
        },
        onOverlayDown = ::clearFullScreenGate,
        log = ::debugLog,
    )

    /** Non-null while the full-screen native's overlay is up over slide 1: waiting for the load
     * (shimmer) or showing the ad. */
    val fullScreenNativeOverlay: StateFlow<FullScreenNativeOverlay<NativeAd>?> = fullScreenNative.overlay

    /** The full-screen native is done (closed, the wait gave up, or lost to a process death): the
     * screen should move the pager to slide 2, then call [onFullScreenNativeAdvanced]. */
    val fullScreenAdvancePending: StateFlow<Boolean> = fullScreenNative.advancePending

    private val interstitialLoader =
        InterstitialAdLoader(context, AdUnitIds.INTRO_INTERSTITIAL, fullScreenAdGate)

    private var adsStarted = false
    private var finishPressed = false
    private var settledPage = 0

    init {
        viewModelScope.launch {
            fullScreenNativeLoader.state.collect { state ->
                when (state) {
                    NativeAdState.Loading -> Unit
                    is NativeAdState.Loaded -> debugLog("full-screen native preload loaded")
                    NativeAdState.Failed -> debugLog("full-screen native preload failed or unavailable")
                }
            }
        }
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

    /** Idempotent. Waits for consent to resolve, then loads the native ad, preloads the
     * full-screen native and the finish interstitial, or settles all three on unavailable. The
     * slot's native goes out first: the full-screen one is only ever needed before it's shown. */
    fun startAds() {
        if (adsStarted) return
        adsStarted = true
        viewModelScope.launch {
            val consent = adConsentManager.state.first { it != AdConsentState.Pending }
            when (consent) {
                AdConsentState.Allowed -> {
                    debugLog("consent Allowed, loading native + preloading interstitial")
                    nativeAdLoader.start()
                    startFullScreenNative()
                    interstitialLoader.start()
                }
                else -> {
                    debugLog("consent $consent, no intro ads")
                    nativeAdLoader.markUnavailable()
                    fullScreenNativeLoader.markUnavailable()
                    interstitialLoader.markUnavailable()
                }
            }
        }
    }

    private fun startFullScreenNative() {
        when {
            !IntroAdConfig.FULLSCREEN_NATIVE_ENABLED -> {
                debugLog("full-screen native disabled, not preloading")
                fullScreenNativeLoader.markUnavailable()
            }
            fullScreenNative.decided -> {
                debugLog("full-screen native already shown or skipped this visit, not preloading")
                fullScreenNativeLoader.markUnavailable()
            }
            else -> {
                debugLog("full-screen native preload start")
                fullScreenNativeLoader.start()
            }
        }
    }

    /** Slide 1's Next -- see [IntroFullScreenNativeFlow.onFirstSlideNext]. */
    fun onFirstSlideNext(): Boolean = fullScreenNative.onFirstSlideNext()

    /** The full-screen native's assets are rendered and visible -- its close timer starts now. */
    fun onFullScreenNativeDisplayed() {
        debugLog(if (fullScreenDisplayed) "full-screen native shown again (recreated, e.g. rotation)" else "full-screen native shown")
        fullScreenDisplayed = true
    }

    fun onFullScreenNativeCloseRevealed() {
        debugLog("full-screen native close button revealed")
    }

    /** Close button, or back once it's up. The ad stays on screen until the pager has moved to
     * slide 2 under it ([onFullScreenNativeAdvanced]), so slide 1 never flashes in between. */
    fun onFullScreenNativeClosed() = fullScreenNative.onClosed()

    /** The screen has moved the pager to slide 2 after [fullScreenAdvancePending]. */
    fun onFullScreenNativeAdvanced() = fullScreenNative.onAdvanced()

    private fun clearFullScreenGate() {
        if (!fullScreenInGate) return
        fullScreenInGate = false
        fullScreenAdGate.inAppAdSettled()
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
     * dismissed, fails to show, or straight away if it wasn't ready -- calls [onDone] (which
     * stores the next onboarding step, see [OnboardingProgressViewModel]). Taps after the first
     * are ignored, so a quick double-tap can't show twice or navigate under an opening ad.
     */
    fun onFinishClicked(activity: Activity?, onDone: () -> Unit) {
        if (finishPressed) return
        finishPressed = true
        interstitialLoader.commitToShow()
        val finish = { onDone() }
        val shown = activity != null && interstitialLoader.showIfReady(activity, onFinished = {
            debugLog("interstitial finished, leaving Intro")
            finish()
        })
        if (shown) {
            debugLog("interstitial showing")
        } else {
            debugLog("interstitial missed (${interstitialLoader.state.value}), leaving Intro")
            finish()
        }
    }

    override fun onCleared() {
        nativeAdLoader.destroy()
        fullScreenNativeLoader.destroy()
        clearFullScreenGate()
        interstitialLoader.destroy()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "IntroViewModel"
        const val KEY_SLIDE2_AD_SEEN = "intro_slide2_ad_seen"
        const val KEY_REFRESH_DONE = "intro_native_refresh_done"

        fun NativeAdState.toFullScreenLoad(): FullScreenNativeLoad = when (this) {
            NativeAdState.Loading -> FullScreenNativeLoad.LOADING
            is NativeAdState.Loaded -> FullScreenNativeLoad.LOADED
            NativeAdState.Failed -> FullScreenNativeLoad.FAILED
        }
    }
}
