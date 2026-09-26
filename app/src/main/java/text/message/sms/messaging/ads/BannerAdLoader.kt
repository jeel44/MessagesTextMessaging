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

/** The latest request's progress ([BannerAdLoader.requestStatus]) -- unlike [BannerAdState],
 * which never leaves [BannerAdState.Loaded] once an ad has shown, this follows every request. */
internal enum class BannerRequestStatus { Idle, InFlight, Loaded, Failed }

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
 * Only [reload] can make it fire again.
 *
 * [reload] is for owners that run their own refresh policy (Home's [HomeBannerAdManager]): it
 * requests a fresh ad into the same [AdView], so the current creative stays up until its
 * replacement arrives -- or, after a failed first load collapsed the slot, into a new one.
 * [onRequestFinished] reports every request's outcome, refreshes included, so such an owner can
 * schedule its own retry, and [requestStatus] lets its UI follow each request.
 */
internal class BannerAdLoader(
    private val context: Context,
    private val adUnitId: String,
    private val adSize: AdSize,
    private val onSettled: (BannerAdState) -> Unit = {},
    private val onRequestFinished: (loaded: Boolean) -> Unit = {},
) {

    private val _state = MutableStateFlow<BannerAdState>(BannerAdState.Loading)
    val state: StateFlow<BannerAdState> = _state.asStateFlow()

    private val _requestStatus = MutableStateFlow(BannerRequestStatus.Idle)

    /** [BannerRequestStatus.InFlight] from each request until its ad arrives or fails -- the
     * first load and every [reload] alike. */
    val requestStatus: StateFlow<BannerRequestStatus> = _requestStatus.asStateFlow()

    private var adView: AdView? = null
    private var started = false
    private var destroyed = false

    /** Whether the first request has gone out ([start], or a [reload] standing in for it). */
    val isStarted: Boolean
        get() = started

    fun start() {
        if (started) return
        started = true
        request()
    }

    /** Requests a fresh ad -- the first load if [start] hasn't run yet. Returns whether a request
     * actually went out: not once destroyed, nor while one is still in flight. */
    fun reload(): Boolean {
        if (destroyed || _requestStatus.value == BannerRequestStatus.InFlight) return false
        if (!started) {
            start()
        } else {
            request()
        }
        return true
    }

    private fun request() {
        val view = adView ?: createAdView().also { adView = it }
        _requestStatus.value = BannerRequestStatus.InFlight
        view.loadAd(AdRequest.Builder().build())
    }

    private fun createAdView(): AdView {
        val view = AdView(context)
        view.adUnitId = adUnitId
        view.setAdSize(adSize)
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                _requestStatus.value = BannerRequestStatus.Loaded
                onRequestFinished(true)
                // Also fires on every auto-refresh of an already-shown banner -- same view, so
                // settling again is a no-op for the state flow.
                if (_state.value is BannerAdState.Loaded) return
                settle(BannerAdState.Loaded(view))
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                _requestStatus.value = BannerRequestStatus.Failed
                onRequestFinished(false)
                // A failed *refresh* keeps showing the previous creative -- only a failed initial
                // load collapses the slot.
                if (_state.value is BannerAdState.Loaded) return
                view.destroy()
                adView = null
                settle(BannerAdState.Failed)
            }
        }
        return view
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
        destroyed = true
        adView?.destroy()
        adView = null
    }
}
