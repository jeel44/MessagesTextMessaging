package text.message.sms.messaging.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import text.message.sms.messaging.ads.AdConsentState

/** What covers slide 1 after its Next. */
internal sealed interface FullScreenNativeOverlay<out A> {
    /** Still loading: the shimmer layout only -- no close button, back ignored -- for at most
     * the flow's max wait. */
    data object Waiting : FullScreenNativeOverlay<Nothing>

    data class Showing<out A>(val ad: A) : FullScreenNativeOverlay<A>
}

/**
 * Slide 1's full-screen native, from its Next to landing on slide 2: [decideIntroFullScreenNative],
 * then either the ad at once, a wait of up to [maxWaitMillis] on [scope] for a load still in
 * flight, or straight on. Generic over the ad type [A] so it runs on the JVM with a stand-in ad;
 * [IntroViewModel] passes its `viewModelScope` (the wait survives rotation, and is cancelled with
 * the ViewModel) and [com.google.android.gms.ads.nativead.NativeAd].
 *
 * Outcomes live in [savedStateHandle]: shown, skipped (a wait that timed out or failed included),
 * and whether the overlay is up -- a process death with it up lands on slide 2 with nothing shown.
 * [onOverlayUp]/[onOverlayDown] pair exactly once per overlay (the Waiting -> Showing hand-over
 * is one overlay).
 */
internal class IntroFullScreenNativeFlow<A : Any>(
    private val scope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val load: StateFlow<FullScreenNativeLoad>,
    private val loadedAd: () -> A?,
    private val consent: () -> AdConsentState,
    private val enabled: Boolean,
    private val maxWaitMillis: Long,
    private val onOverlayUp: () -> Unit = {},
    private val onOverlayDown: () -> Unit = {},
    private val log: (String) -> Unit = {},
) {

    private val _overlay = MutableStateFlow<FullScreenNativeOverlay<A>?>(null)

    /** Non-null while the overlay covers slide 1. */
    val overlay: StateFlow<FullScreenNativeOverlay<A>?> = _overlay.asStateFlow()

    private val _advancePending = MutableStateFlow(false)

    /** The overlay is done (closed, the wait gave up, or lost to a process death): the screen
     * should move the pager to slide 2 under it, then call [onAdvanced]. */
    val advancePending: StateFlow<Boolean> = _advancePending.asStateFlow()

    private val shown: Boolean get() = savedStateHandle.get<Boolean>(KEY_SHOWN) == true
    private val skipped: Boolean get() = savedStateHandle.get<Boolean>(KEY_SKIPPED) == true

    /** Already shown or skipped this visit -- nothing left to preload for. */
    val decided: Boolean get() = shown || skipped

    init {
        if (savedStateHandle.get<Boolean>(KEY_OVERLAY_UP) == true) {
            // Only a process death gets here -- rotation keeps the owner, and this with it.
            log("full-screen native closed: process restored while it was up, moving on to slide 2")
            savedStateHandle[KEY_OVERLAY_UP] = false
            if (!shown) savedStateHandle[KEY_SKIPPED] = true
            _advancePending.value = true
        }
    }

    /** Slide 1's Next. Returns true if the overlay is now up (showing, or waiting) -- the screen
     * then stays on slide 1 under it until [advancePending] -- or false (logging why) if the
     * screen should go straight to slide 2. A tap while the overlay is already up changes
     * nothing. */
    fun onFirstSlideNext(): Boolean {
        if (_overlay.value != null || _advancePending.value) return true
        val decision = decideIntroFullScreenNative(
            enabled = enabled,
            alreadyShown = shown,
            alreadySkipped = skipped,
            consent = consent(),
            load = load.value,
        )
        return when (decision) {
            FullScreenNativeDecision.Show -> {
                val ad = loadedAd()
                if (ad == null) {
                    skip("no ad")
                    false
                } else {
                    log("full-screen native opening on slide 1's Next")
                    markOverlayUp()
                    show(ad)
                    true
                }
            }
            FullScreenNativeDecision.Wait -> {
                log("full-screen native waiting (still loading)")
                markOverlayUp()
                _overlay.value = FullScreenNativeOverlay.Waiting
                awaitLoad()
                true
            }
            is FullScreenNativeDecision.Skip -> {
                skip(decision.reason.logText)
                false
            }
        }
    }

    private fun awaitLoad() {
        scope.launch {
            val settled = withTimeoutOrNull(maxWaitMillis) {
                load.first { it != FullScreenNativeLoad.LOADING }
            }
            val ad = if (settled == FullScreenNativeLoad.LOADED) loadedAd() else null
            when {
                ad != null -> {
                    log("full-screen native loaded during wait")
                    show(ad)
                }
                settled == null -> endWithoutAd("full-screen native wait timed out, going to slide 2")
                else -> endWithoutAd("full-screen native failed during wait, going to slide 2")
            }
        }
    }

    /** The close button, or back once it's up. */
    fun onClosed() {
        if (_overlay.value !is FullScreenNativeOverlay.Showing || _advancePending.value) return
        log("full-screen native closed, going to slide 2")
        savedStateHandle[KEY_OVERLAY_UP] = false
        _advancePending.value = true
    }

    /** The screen has moved the pager to slide 2 after [advancePending]: the overlay goes. */
    fun onAdvanced() {
        _advancePending.value = false
        if (_overlay.value == null) return
        _overlay.value = null
        onOverlayDown()
    }

    private fun markOverlayUp() {
        savedStateHandle[KEY_OVERLAY_UP] = true
        onOverlayUp()
    }

    private fun show(ad: A) {
        savedStateHandle[KEY_SHOWN] = true
        _overlay.value = FullScreenNativeOverlay.Showing(ad)
    }

    private fun skip(reason: String) {
        log("full-screen native skipped ($reason), going to slide 2")
        if (!shown) savedStateHandle[KEY_SKIPPED] = true
    }

    /** The wait gave up: the shimmer stays until the pager is on slide 2 under it. */
    private fun endWithoutAd(message: String) {
        log(message)
        savedStateHandle[KEY_SKIPPED] = true
        savedStateHandle[KEY_OVERLAY_UP] = false
        _advancePending.value = true
    }

    private companion object {
        const val KEY_SHOWN = "intro_fullscreen_native_shown"
        const val KEY_SKIPPED = "intro_fullscreen_native_skipped"

        /** Same key the pre-wait version used for "showing", so a restored state carries over. */
        const val KEY_OVERLAY_UP = "intro_fullscreen_native_showing"
    }
}
