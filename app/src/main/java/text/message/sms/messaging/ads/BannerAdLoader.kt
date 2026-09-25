package text.message.sms.messaging.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A single banner slot's state -- [Loading] until [BannerAdLoader] settles on [Loaded] or
 * [Failed] (the slot then collapses entirely). */
internal sealed interface BannerAdState {
    data object Loading : BannerAdState
    data class Loaded(val adView: AdView) : BannerAdState
    data object Failed : BannerAdState
}

/**
 * Loads one banner [AdView] for [adUnitId] at [adSize] -- the placement-agnostic banner loading
 * shared by every banner slot: call-end's banner fallback ([CallEndAdLoader]) and the Compose
 * [text.message.sms.messaging.ui.components.ads.BannerAdWithShimmer] slot. Pure AdMob -- no
 * mediation adapters.
 *
 * [start] is idempotent (load once per instance); [destroy] must be called once the owning screen
 * is gone -- an [AdView] holds a WebView that leaks without it. [onSettled] fires once, on the main
 * thread, with the final [BannerAdState.Loaded] or [BannerAdState.Failed] -- for owners that fold
 * this into a wider state of their own (as [CallEndAdLoader] does) instead of collecting [state].
 */
internal class BannerAdLoader(
    private val context: Context,
    private val adUnitId: String,
    private val adSize: AdSize,
    private val onSettled: (BannerAdState) -> Unit = {},
) {

    private val _state = MutableStateFlow<BannerAdState>(BannerAdState.Loading)
    val state: StateFlow<BannerAdState> = _state.asStateFlow()

    private var adView: AdView? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val view = AdView(context)
        view.adUnitId = adUnitId
        view.setAdSize(adSize)
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Also fires on every auto-refresh of an already-shown banner -- same view, so
                // settling again is a no-op for the state flow.
                if (_state.value is BannerAdState.Loaded) return
                settle(BannerAdState.Loaded(view))
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                // A failed *refresh* keeps showing the previous creative -- only a failed initial
                // load collapses the slot.
                if (_state.value is BannerAdState.Loaded) return
                view.destroy()
                adView = null
                settle(BannerAdState.Failed)
            }
        }
        adView = view
        view.loadAd(AdRequest.Builder().build())
    }

    private fun settle(newState: BannerAdState) {
        _state.value = newState
        onSettled(newState)
    }

    fun pause() {
        adView?.pause()
    }

    fun resume() {
        adView?.resume()
    }

    fun destroy() {
        adView?.destroy()
        adView = null
    }
}
