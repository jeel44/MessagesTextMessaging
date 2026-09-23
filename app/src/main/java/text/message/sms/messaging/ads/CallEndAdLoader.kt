package text.message.sms.messaging.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The call-end screen's ad slot state -- [Loading] until [CallEndAdLoader] settles on exactly one
 * of [NativeLoaded], [BannerLoaded], or [Failed] (both formats failed to load; the slot then
 * collapses entirely, see CallEndAdContent.kt's `CallEndAdSlot`). */
internal sealed interface CallEndAdState {
    data object Loading : CallEndAdState
    data class NativeLoaded(val nativeAd: NativeAd) : CallEndAdState
    data class BannerLoaded(val adView: AdView) : CallEndAdState
    data object Failed : CallEndAdState
}

/**
 * Loads exactly one ad for the call-end screen's ad slot: tries a native ad first
 * ([AdUnitIds.CALL_END_NATIVE]); if it fails to load, falls back to a fixed 300x250 banner
 * ([AdUnitIds.CALL_END_BANNER]) in the same slot; if the banner also fails, [state] settles on
 * [CallEndAdState.Failed]. Pure AdMob -- no mediation adapters.
 *
 * One loader instance per screen, owned by [text.message.sms.messaging.ui.screens.callend
 * .CallEndViewModel]: [start] is called once from its `init` (load once when the screen appears),
 * [destroy] from its `onCleared` -- both [NativeAd] and [AdView] hold native/WebView resources
 * that leak without an explicit destroy call once the screen is gone.
 */
internal class CallEndAdLoader(private val context: Context) {

    private val _state = MutableStateFlow<CallEndAdState>(CallEndAdState.Loading)
    val state: StateFlow<CallEndAdState> = _state.asStateFlow()

    private var bannerAdView: AdView? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        loadNative()
    }

    private fun loadNative() {
        val adLoader = AdLoader.Builder(context, AdUnitIds.CALL_END_NATIVE)
            .forNativeAd { nativeAd -> _state.value = CallEndAdState.NativeLoaded(nativeAd) }
            .withAdListener(
                object : AdListener() {
                    // Native failed -- fall back to the banner rather than settling on Failed
                    // directly; only a banner failure too collapses the slot.
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        loadBanner()
                    }
                },
            )
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun loadBanner() {
        val adView = AdView(context)
        adView.adUnitId = AdUnitIds.CALL_END_BANNER
        adView.setAdSize(AdSize.MEDIUM_RECTANGLE)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                _state.value = CallEndAdState.BannerLoaded(adView)
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                _state.value = CallEndAdState.Failed
                adView.destroy()
            }
        }
        bannerAdView = adView
        adView.loadAd(AdRequest.Builder().build())
    }

    fun destroy() {
        (_state.value as? CallEndAdState.NativeLoaded)?.nativeAd?.destroy()
        bannerAdView?.destroy()
        bannerAdView = null
    }
}
