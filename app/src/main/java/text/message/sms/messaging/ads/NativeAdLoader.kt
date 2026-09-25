package text.message.sms.messaging.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import text.message.sms.messaging.BuildConfig

/** A single native slot's state -- [Loading] until the first load settles on [Loaded] or [Failed]
 * (the slot then collapses entirely). A [NativeAdLoader.refresh] never moves it back to [Loading]
 * or on to [Failed]: the current ad stays up until its replacement arrives. */
internal sealed interface NativeAdState {
    data object Loading : NativeAdState
    data class Loaded(val nativeAd: NativeAd) : NativeAdState
    data object Failed : NativeAdState
}

/**
 * Loads one native ad for [adUnitId], with an optional in-place [refresh] -- the placement-agnostic
 * native loading for slots with no banner fallback (first one: the onboarding Language screen).
 * [CallEndAdLoader] keeps its own native-then-banner logic. Pure AdMob -- no mediation.
 *
 * Consent-agnostic like [BannerAdLoader]: the owner waits on [AdConsentManager.state] before
 * calling [start], or calls [markUnavailable] instead.
 *
 * [refresh] only acts while an ad is [NativeAdState.Loaded] and no refresh is already in flight:
 * the current ad stays on screen, the new one replaces it when it arrives, and a failed refresh
 * keeps the current one. It deliberately doesn't retry a failed first load or pile onto one still
 * loading, so a slot never appears/expands under the user mid-interaction.
 *
 * [destroy] must be called once the owner is gone -- [NativeAd] holds native resources that leak
 * without it.
 */
internal class NativeAdLoader(
    private val context: Context,
    private val adUnitId: String,
) {

    private val _state = MutableStateFlow<NativeAdState>(NativeAdState.Loading)
    val state: StateFlow<NativeAdState> = _state.asStateFlow()

    private var started = false
    private var refreshing = false
    private var destroyed = false

    /** The ad a refresh replaced -- still bound to the on-screen view until the next frame
     * rebinds it, so it's destroyed on the next swap or in [destroy] rather than immediately. */
    private var replacedAd: NativeAd? = null

    fun start() {
        if (started) return
        started = true
        request()
    }

    /** No-op once [start] has run. */
    fun markUnavailable() {
        if (started) return
        started = true
        _state.value = NativeAdState.Failed
    }

    /** Returns whether a refresh request actually went out (see the class doc for when it
     * doesn't). */
    fun refresh(): Boolean {
        if (destroyed || refreshing || _state.value !is NativeAdState.Loaded) {
            debugLog("refresh skipped (state=${_state.value}, refreshing=$refreshing)")
            return false
        }
        refreshing = true
        debugLog("refreshing")
        request()
        return true
    }

    private fun request() {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                if (destroyed) {
                    nativeAd.destroy()
                    return@forNativeAd
                }
                debugLog(if (refreshing) "refreshed" else "loaded")
                refreshing = false
                replacedAd?.destroy()
                replacedAd = (_state.value as? NativeAdState.Loaded)?.nativeAd
                _state.value = NativeAdState.Loaded(nativeAd)
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        if (destroyed) return
                        debugLog("failed to load (refresh=$refreshing): ${adError.code} ${adError.message}")
                        refreshing = false
                        // A failed refresh keeps the current ad; only a failed first load collapses.
                        if (_state.value !is NativeAdState.Loaded) _state.value = NativeAdState.Failed
                    }
                },
            )
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun destroy() {
        destroyed = true
        replacedAd?.destroy()
        replacedAd = null
        (_state.value as? NativeAdState.Loaded)?.nativeAd?.destroy()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$adUnitId: $message")
    }

    private companion object {
        const val TAG = "NativeAdLoader"
    }
}
