package text.message.sms.messaging.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the chat interstitials: the one [ChatInterstitialController] (the cycle) and one ad slot
 * per placement -- [AdUnitIds.CHAT_ENTER_INTERSTITIAL] and [AdUnitIds.CHAT_EXIT_INTERSTITIAL],
 * each with its own [InterstitialAdLoader], replaced after every show, miss or failure. App-scoped,
 * so the cycle position survives rotation and navigation and resets only on a cold start.
 *
 * Gated like every other full-screen ad: nothing loads or shows before onboarding is complete or
 * while [AdConsentManager] doesn't allow ads, and nothing shows while [FullScreenAdGate] is busy or
 * MainActivity isn't the resumed Activity (call-end screen on top, app in the background). The
 * loaders report to [FullScreenAdGate] from commit/show to dismiss, so a warm-resume App Open
 * never lands on top of, or right after, a chat interstitial.
 *
 * Driven from MessagingNavHost: chat taps ([openChat]), back-stack changes
 * ([ChatInterstitialController.onBackStackChanged] / [ChatInterstitialController.onLeaveVisible])
 * and the inbox's visibility ([onInboxVisible] / [onInboxHidden]). Main thread only.
 */
@Singleton
class ChatInterstitialManager @Inject constructor(
    @param:ApplicationContext context: Context,
    adConsentManager: AdConsentManager,
    onboardingPreferences: OnboardingPreferences,
    fullScreenAdGate: FullScreenAdGate,
    clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var onboardingComplete = false
    private var inboxVisible = false

    private val conditions = object : ChatInterstitialConditions<Activity> {
        override val onboardingComplete: Boolean get() = this@ChatInterstitialManager.onboardingComplete
        override val consentAllowed: Boolean get() = adConsentManager.state.value == AdConsentState.Allowed
        override val fullScreenAdBusy: Boolean get() = fullScreenAdGate.isFullScreenAdBusy()
        override fun isInForeground(host: Activity): Boolean =
            (host as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
    }

    internal val controller = ChatInterstitialController(
        enterSlot = loaderSlot(context, fullScreenAdGate, ChatVisitAd.ENTER_AD),
        exitSlot = loaderSlot(context, fullScreenAdGate, ChatVisitAd.EXIT_AD),
        conditions = conditions,
        clock = clock::millis,
        log = { if (BuildConfig.DEBUG) Log.d(TAG, it) },
    )

    init {
        scope.launch {
            onboardingPreferences.isOnboardingComplete.collect {
                onboardingComplete = it
                if (it && inboxVisible) controller.preloadNext()
            }
        }
        scope.launch {
            adConsentManager.state.collect {
                if (it == AdConsentState.Allowed && inboxVisible) controller.preloadNext()
            }
        }
    }

    /** See [ChatInterstitialController.onChatOpenRequested]. */
    internal fun openChat(source: ChatEntrySource, activity: Activity?, originKey: Any, open: () -> Any?) =
        controller.onChatOpenRequested(source, activity, originKey, open)

    /** The inbox is on screen: preload the placement the cycle needs next. */
    fun onInboxVisible() {
        inboxVisible = true
        controller.preloadNext()
    }

    fun onInboxHidden() {
        inboxVisible = false
    }

    private companion object {
        const val TAG = "ChatInterstitial"
    }
}

/** [ad]'s own slot -- its own placement, ad unit ID and loader, never shared with the other. */
private fun loaderSlot(context: Context, gate: FullScreenAdGate, ad: ChatVisitAd): ChatInterstitialSlot<Activity> {
    val placement = checkNotNull(ad.placement)
    return InterstitialLoaderSlot {
        InterstitialAdLoader(context, chatInterstitialAdUnitId(placement), gate, placement)
    }
}

/** The ad unit ID for a chat interstitial [placement] -- read through [AdUnitIds] at each load. */
internal fun chatInterstitialAdUnitId(placement: AdPlacement): String = when (placement) {
    AdPlacement.CHAT_ENTER_INTERSTITIAL -> AdUnitIds.CHAT_ENTER_INTERSTITIAL
    AdPlacement.CHAT_EXIT_INTERSTITIAL -> AdUnitIds.CHAT_EXIT_INTERSTITIAL
    else -> error("$placement is not a chat interstitial")
}

/** One [InterstitialAdLoader] at a time; a used or failed one is replaced on the next [preload]
 * (an [InterstitialAdLoader] never reloads itself). */
private class InterstitialLoaderSlot(
    private val newLoader: () -> InterstitialAdLoader,
) : ChatInterstitialSlot<Activity> {

    private var loader: InterstitialAdLoader? = null
    private var showing = false

    override val isReady: Boolean get() = loader?.state?.value is InterstitialAdState.Ready

    override fun preload() {
        if (showing) return
        val state = loader?.state?.value
        if (state is InterstitialAdState.Loading || state is InterstitialAdState.Ready) return
        loader?.destroy()
        loader = newLoader().also { it.start() }
    }

    override fun commitToShow() {
        loader?.commitToShow()
    }

    override fun showIfReady(host: Activity, onFinished: () -> Unit): Boolean {
        val current = loader ?: return false
        showing = true
        val shown = current.showIfReady(host, onFinished = {
            showing = false
            onFinished()
        })
        if (!shown) showing = false
        return shown
    }
}
