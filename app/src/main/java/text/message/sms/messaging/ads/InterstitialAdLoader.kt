package text.message.sms.messaging.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import text.message.sms.messaging.BuildConfig

/** A single interstitial's state -- [Idle] until [InterstitialAdLoader.start], then [Loading]
 * until it settles on [Ready] or [Failed]; [Shown] once [InterstitialAdLoader.showIfReady] has
 * used it up (each loaded ad is shown at most once). */
internal sealed interface InterstitialAdState {
    data object Idle : InterstitialAdState
    data object Loading : InterstitialAdState
    data class Ready(val ad: InterstitialAd) : InterstitialAdState
    data object Failed : InterstitialAdState
    data object Shown : InterstitialAdState
}

/**
 * Loads one full-screen interstitial for [adUnitId] ahead of time, so it can be shown the instant
 * its placement's transition point is reached -- the placement-agnostic interstitial loading for
 * every interstitial slot (first one: SetDefaultSms -> Language). Pure AdMob -- no mediation.
 *
 * Consent-agnostic like [BannerAdLoader]: the owner waits on [AdConsentManager.state] before
 * calling [start], or calls [markUnavailable] instead. Never reloads on its own.
 *
 * [showIfReady] never blocks the caller's flow: it either shows a [InterstitialAdState.Ready] ad
 * and later calls `onFinished` exactly once (dismissed, or failed to show), or returns false
 * immediately so the caller moves on without waiting. [destroy] drops the ad and callbacks once
 * the owner is gone -- an [InterstitialAd] has no destroy of its own, but a held callback would
 * otherwise keep the showing Activity reachable.
 */
internal class InterstitialAdLoader(
    private val context: Context,
    private val adUnitId: String,
) {

    private val _state = MutableStateFlow<InterstitialAdState>(InterstitialAdState.Idle)
    val state: StateFlow<InterstitialAdState> = _state.asStateFlow()

    private var destroyed = false

    fun start() {
        if (_state.value != InterstitialAdState.Idle) return
        _state.value = InterstitialAdState.Loading
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (destroyed) return
                    debugLog("loaded")
                    _state.value = InterstitialAdState.Ready(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    if (destroyed) return
                    debugLog("failed to load: ${adError.code} ${adError.message}")
                    _state.value = InterstitialAdState.Failed
                }
            },
        )
    }

    /** Consent doesn't allow ad requests (see [AdConsentManager]) -- settle straight on
     * [InterstitialAdState.Failed] without any request. No-op once [start] has run. */
    fun markUnavailable() {
        if (_state.value != InterstitialAdState.Idle) return
        _state.value = InterstitialAdState.Failed
    }

    /** Shows the ad if one is [InterstitialAdState.Ready] and returns true -- `onFinished` then
     * fires exactly once, when the ad is dismissed or fails to show. Otherwise returns false and
     * never calls `onFinished`: the caller advances on its own, immediately. */
    fun showIfReady(activity: Activity, onFinished: () -> Unit): Boolean {
        val ad = (_state.value as? InterstitialAdState.Ready)?.ad ?: run {
            debugLog("not ready (${_state.value}), skipping")
            return false
        }
        _state.value = InterstitialAdState.Shown
        var finished = false
        val finishOnce = {
            if (!finished) {
                finished = true
                ad.fullScreenContentCallback = null
                onFinished()
            }
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
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
        ad.show(activity)
        return true
    }

    fun destroy() {
        destroyed = true
        (_state.value as? InterstitialAdState.Ready)?.ad?.fullScreenContentCallback = null
        if (_state.value is InterstitialAdState.Ready) _state.value = InterstitialAdState.Failed
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$adUnitId: $message")
    }

    private companion object {
        const val TAG = "InterstitialAdLoader"
    }
}
