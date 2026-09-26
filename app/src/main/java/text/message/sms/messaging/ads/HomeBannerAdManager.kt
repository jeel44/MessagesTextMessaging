package text.message.sms.messaging.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig

/**
 * The refresh policy for Home's bottom banner ([AdUnitIds.HOME_BANNER]). Owned by
 * [text.message.sms.messaging.ui.screens.conversationlist.ConversationListViewModel] and run on
 * its [scope], so it and its timer outlive Home's composition (in-app navigation, rotation).
 * Three independent request triggers:
 *
 * - **Home became visible** ([onHomeStarted]) -- Home's back-stack entry reaching STARTED, which
 *   covers a return from the background, from the ad's own click-through (browser, Play Store),
 *   and from another in-app screen alike. ON_START rather than ON_RESUME, so a dialog-style
 *   Activity that only pauses Home (permission prompt, default-SMS role request) doesn't count.
 *   The first start is Home's first appearance, and loads the first ad.
 * - **Every [REFRESH_INTERVAL_MILLIS]** from that first start -- never paused or reset, by
 *   visibility or by the other triggers. A tick that lands while Home isn't visible is skipped:
 *   the next [onHomeStarted] refreshes anyway, and a banner nobody can see must not be requested.
 * - **Retry [RETRY_DELAY_MILLIS] after a failed request**, again after each further failure --
 *   skipped the same way if Home isn't visible when it comes due.
 *
 * Every request also needs [AdConsentManager] to allow ads; one that doesn't is skipped, and the
 * first load goes out as soon as consent turns Allowed while Home is visible. A request while one
 * is still in flight is dropped ([BannerAdLoader.reload]), so near-simultaneous triggers cost one
 * request. All calls on the main thread.
 *
 * The [BannerAdLoader] itself is rebuilt by [attach] whenever the screen's Activity context or
 * the adaptive size changes (rotation, language change) -- an [android.view.View] must not
 * outlive its Activity, and a new size needs a new [com.google.android.gms.ads.AdView] anyway.
 */
internal class HomeBannerAdManager(
    private val scope: CoroutineScope,
    private val adConsentManager: AdConsentManager,
) {

    private val _loader = MutableStateFlow<BannerAdLoader?>(null)

    /** The current slot's loader -- null until the screen first [attach]es. */
    val loader: StateFlow<BannerAdLoader?> = _loader.asStateFlow()

    private var loaderContext: Context? = null
    private var loaderSize: AdSize? = null

    private var visible = false
    private var timerJob: Job? = null
    private var retryJob: Job? = null

    init {
        scope.launch {
            adConsentManager.state.collect {
                if (it == AdConsentState.Allowed && _loader.value?.isStarted == false) request("consent allowed")
            }
        }
    }

    /** From the screen, on every composition with its current Activity [context] and adaptive
     * [adSize]: a no-op unless either changed, in which case the loader is rebuilt. */
    fun attach(context: Context, adSize: AdSize) {
        if (context === loaderContext && adSize == loaderSize) return
        loaderContext = context
        loaderSize = adSize
        lateinit var newLoader: BannerAdLoader
        newLoader = BannerAdLoader(
            context,
            AdUnitIds.HOME_BANNER,
            adSize,
            onRequestFinished = { loaded -> onRequestFinished(newLoader, loaded) },
        )
        val oldLoader = _loader.value
        _loader.value = newLoader
        oldLoader?.destroy()
        retryJob?.cancel()
        debugLog("slot attached at ${adSize.width}x${adSize.height}dp${if (oldLoader != null) " (rebuilt)" else ""}")
        request("slot attached")
    }

    /** Home's back-stack entry reached STARTED -- see the class doc. */
    fun onHomeStarted() {
        visible = true
        if (timerJob == null) {
            timerJob = scope.launch {
                while (true) {
                    delay(REFRESH_INTERVAL_MILLIS)
                    request("${REFRESH_INTERVAL_MILLIS / 1000}s timer")
                }
            }
            request("Home first shown")
        } else {
            request("Home visible again")
        }
    }

    /** Home's back-stack entry dropped below STARTED, or its screen left composition. */
    fun onHomeStopped() {
        visible = false
    }

    fun destroy() {
        timerJob?.cancel()
        retryJob?.cancel()
        _loader.value?.destroy()
        _loader.value = null
    }

    private fun request(reason: String) {
        val loader = _loader.value
        val skipReason = when {
            loader == null -> "no slot attached yet"
            !visible -> "Home not visible"
            adConsentManager.state.value != AdConsentState.Allowed -> "consent=${adConsentManager.state.value}"
            !loader.reload() -> "a request is already in flight"
            else -> null
        }
        if (skipReason != null) {
            debugLog("$reason: skipped ($skipReason)")
            return
        }
        retryJob?.cancel()
        retryJob = null
        debugLog("$reason: requesting")
    }

    private fun onRequestFinished(loader: BannerAdLoader, loaded: Boolean) {
        if (loader !== _loader.value) return
        debugLog(if (loaded) "loaded" else "failed, retrying in ${RETRY_DELAY_MILLIS / 1000}s")
        retryJob?.cancel()
        retryJob = if (loaded) {
            null
        } else {
            scope.launch {
                delay(RETRY_DELAY_MILLIS)
                retryJob = null
                request("retry")
            }
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "HomeBannerAdManager"
        const val REFRESH_INTERVAL_MILLIS = 30_000L
        const val RETRY_DELAY_MILLIS = 30_000L
    }
}
