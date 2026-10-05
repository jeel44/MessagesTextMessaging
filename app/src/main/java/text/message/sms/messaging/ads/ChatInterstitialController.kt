package text.message.sms.messaging.ads

/** One chat interstitial placement's single ad slot, as [ChatInterstitialController] sees it.
 * [H] is whatever an ad is shown over (an Activity in the app, anything in tests). */
internal interface ChatInterstitialSlot<H> {
    val isReady: Boolean

    /** Requests an ad unless one is already loading, loaded or on screen. A used or failed ad is
     * replaced by a fresh request. */
    fun preload()

    /** See [InterstitialAdLoader.commitToShow]. */
    fun commitToShow()

    /** See [InterstitialAdLoader.showIfReady]: true = shown, and `onFinished` fires exactly once
     * on dismiss or show failure; false = not shown, `onFinished` never fires. */
    fun showIfReady(host: H, onFinished: () -> Unit): Boolean
}

/** The app-wide conditions every chat interstitial (and every preload) checks. */
internal interface ChatInterstitialConditions<H> {
    val onboardingComplete: Boolean
    val consentAllowed: Boolean

    /** [FullScreenAdGate.isFullScreenAdBusy]. */
    val fullScreenAdBusy: Boolean

    /** [host] is the resumed, on-screen Activity -- false while the call-end screen (or anything
     * else) covers it, or the app is in the background. */
    fun isInForeground(host: H): Boolean
}

/** Why a due chat interstitial wasn't shown -- logged only. */
internal enum class ChatAdSkipReason(val label: String) {
    ONBOARDING("onboarding incomplete"),
    NO_CONSENT("no consent"),
    GATE_BUSY("gate busy (another full-screen ad showing or about to show)"),
    NOT_IN_FOREGROUND("app not in foreground (call-end screen up?)"),
    MIN_GAP("inside MIN_GAP_MILLIS"),
    NOT_READY("not ready"),
    NOT_VISIBLE("previous screen never became visible"),
}

/**
 * The chat interstitials' cycle -- pure logic, no Android, so it's unit-tested directly; the app
 * owns its one instance through [ChatInterstitialManager] (app-scoped: survives rotation and
 * navigation, resets on cold start).
 *
 * A *visit* starts when a chat is opened from a counted [ChatEntrySource] ([onChatOpenRequested])
 * and takes the next entry of [cycle]; the position advances at that moment, whether or not the
 * visit's ad ends up showing -- a skipped ad (not ready, gate busy, ...) is never retried, so the
 * pattern stays predictable.
 *
 * - [ChatVisitAd.ENTER_AD]: the enter interstitial shows first and the chat opens when it's
 *   dismissed or fails to show; if it can't show, the chat opens immediately.
 * - [ChatVisitAd.EXIT_AD]: the chat opens and is *armed*. Leaving it -- its back-stack entry gone
 *   and the screen it was opened from back on top ([onBackStackChanged]) -- shows the exit
 *   interstitial once that screen is visible ([onLeaveVisible]). Anything that keeps the chat on
 *   the back stack (Home, rotation, Conversation info) isn't leaving; anything that replaces it
 *   with another screen drops the exit ad.
 *
 * Only the placement the cycle needs next is preloaded ([preloadNext]), one ad at a time.
 * Main thread only.
 */
internal class ChatInterstitialController<H>(
    private val enterSlot: ChatInterstitialSlot<H>,
    private val exitSlot: ChatInterstitialSlot<H>,
    private val conditions: ChatInterstitialConditions<H>,
    private val clock: () -> Long,
    cycle: List<ChatVisitAd> = ChatInterstitialConfig.CYCLE,
    private val enabled: Boolean = ChatInterstitialConfig.ENABLED,
    private val minGapMillis: Long = ChatInterstitialConfig.MIN_GAP_MILLIS,
    private val countedSources: Set<ChatEntrySource> = ChatInterstitialConfig.COUNTED_SOURCES,
    private val log: (String) -> Unit = {},
) {
    private val cycle: List<ChatVisitAd> = cycle.toList().also { require(it.isNotEmpty()) { "empty cycle" } }

    private class ArmedExit(val visitNumber: Int, val chatKey: Any, val originKey: Any)

    private var visitsStarted = 0L
    private var lastShownAtMillis: Long? = null
    private var armedExit: ArmedExit? = null
    private var pendingLeaveVisit: Int? = null
    private var enterShowing = false

    /** The 1-based cycle position the next counted visit takes. */
    val nextVisitNumber: Int get() = (visitsStarted % cycle.size).toInt() + 1

    /**
     * A chat was tapped on a screen whose back-stack entry is [originKey]. [openChat] navigates to
     * it and returns the new chat's back-stack entry key; it's called exactly once -- right away,
     * or after the enter interstitial.
     */
    fun onChatOpenRequested(source: ChatEntrySource, host: H?, originKey: Any, openChat: () -> Any?) {
        if (!enabled) {
            openChat()
            return
        }
        if (source !in countedSources) {
            log("source=${source.name.lowercase()}: skipped (not counted source), cycle stays at visit $nextVisitNumber")
            openChat()
            return
        }
        // The enter ad covers the inbox, so a second tap can't really land -- but never count it.
        if (enterShowing) return

        val visitNumber = nextVisitNumber
        val due = cycle[visitNumber - 1]
        visitsStarted++
        log("visit $visitNumber: ${dueLabel(due)}")
        // A leave that never got its visible check (its screen went away mid-wait) is stale now.
        pendingLeaveVisit = null
        armedExit = null
        when (due) {
            ChatVisitAd.NONE -> openChat()
            ChatVisitAd.EXIT_AD -> {
                val chatKey = openChat()
                if (chatKey != null) armedExit = ArmedExit(visitNumber, chatKey, originKey)
            }
            ChatVisitAd.ENTER_AD -> showEnterThenOpen(visitNumber, host, openChat)
        }
    }

    /**
     * Every back-stack change, as its entries' keys (bottom to top). Returns true when the armed
     * chat was just left back to the screen it was opened from: the caller then waits for that
     * screen to be visible and calls [onLeaveVisible] exactly once. An empty stack (a NavController
     * being rebuilt, e.g. on rotation) is ignored.
     */
    fun onBackStackChanged(entryKeys: List<Any>): Boolean {
        val armed = armedExit ?: return false
        if (entryKeys.isEmpty() || armed.chatKey in entryKeys) return false
        armedExit = null
        if (entryKeys.last() != armed.originKey) {
            log("visit ${armed.visitNumber}: chat replaced by another screen, not a leave -- exit ad dropped")
            return false
        }
        pendingLeaveVisit = armed.visitNumber
        return true
    }

    /** After [onBackStackChanged] returned true: [visible] = the previous screen finished its
     * transition and is on top. Shows the exit ad over [host] if everything allows it. */
    fun onLeaveVisible(host: H?, visible: Boolean) {
        val visitNumber = pendingLeaveVisit ?: return
        pendingLeaveVisit = null
        val skip = if (!visible) ChatAdSkipReason.NOT_VISIBLE else skipReason(host, exitSlot)
        if (skip != null || host == null) {
            logSkip(visitNumber, ChatVisitAd.EXIT_AD, skip ?: ChatAdSkipReason.NOT_IN_FOREGROUND)
            preloadNext()
            return
        }
        val shown = exitSlot.showIfReady(host) {
            log("visit $visitNumber: exit ad finished")
            preloadNext()
        }
        if (shown) {
            lastShownAtMillis = clock()
            log("visit $visitNumber: shown ${placementName(ChatVisitAd.EXIT_AD)}")
        } else {
            logSkip(visitNumber, ChatVisitAd.EXIT_AD, ChatAdSkipReason.NOT_READY)
            preloadNext()
        }
    }

    /** Requests the one placement the cycle needs next, if it isn't loaded or loading already.
     * Called while the inbox is visible and after every show or miss. */
    fun preloadNext() {
        if (!enabled || !conditions.onboardingComplete || !conditions.consentAllowed) return
        val next = nextAdDue() ?: return
        slotFor(next).preload()
    }

    private fun showEnterThenOpen(visitNumber: Int, host: H?, openChat: () -> Any?) {
        var opened = false
        val openOnce = {
            if (!opened) {
                opened = true
                openChat()
            }
        }
        val skip = skipReason(host, enterSlot)
        if (skip != null || host == null) {
            logSkip(visitNumber, ChatVisitAd.ENTER_AD, skip ?: ChatAdSkipReason.NOT_IN_FOREGROUND)
            openOnce()
            preloadNext()
            return
        }
        // Nothing else full-screen (a warm-resume App Open) may slip in before the chat opens.
        enterSlot.commitToShow()
        enterShowing = true
        val shown = enterSlot.showIfReady(host) {
            enterShowing = false
            log("visit $visitNumber: enter ad finished, opening chat")
            openOnce()
            preloadNext()
        }
        if (shown) {
            lastShownAtMillis = clock()
            log("visit $visitNumber: shown ${placementName(ChatVisitAd.ENTER_AD)}")
        } else {
            enterShowing = false
            logSkip(visitNumber, ChatVisitAd.ENTER_AD, ChatAdSkipReason.NOT_READY)
            openOnce()
            preloadNext()
        }
    }

    private fun skipReason(host: H?, slot: ChatInterstitialSlot<H>): ChatAdSkipReason? {
        val lastShown = lastShownAtMillis
        return when {
            !conditions.onboardingComplete -> ChatAdSkipReason.ONBOARDING
            !conditions.consentAllowed -> ChatAdSkipReason.NO_CONSENT
            conditions.fullScreenAdBusy -> ChatAdSkipReason.GATE_BUSY
            host == null || !conditions.isInForeground(host) -> ChatAdSkipReason.NOT_IN_FOREGROUND
            minGapMillis > 0 && lastShown != null && clock() - lastShown < minGapMillis -> ChatAdSkipReason.MIN_GAP
            !slot.isReady -> ChatAdSkipReason.NOT_READY
            else -> null
        }
    }

    /** The armed (or just-left) visit's exit ad, else the first ad from the next visit on. */
    private fun nextAdDue(): ChatVisitAd? {
        if (armedExit != null || pendingLeaveVisit != null) return ChatVisitAd.EXIT_AD
        for (offset in cycle.indices) {
            val ad = cycle[((visitsStarted + offset) % cycle.size).toInt()]
            if (ad != ChatVisitAd.NONE) return ad
        }
        return null
    }

    private fun slotFor(ad: ChatVisitAd): ChatInterstitialSlot<H> = when (ad) {
        ChatVisitAd.ENTER_AD -> enterSlot
        ChatVisitAd.EXIT_AD -> exitSlot
        ChatVisitAd.NONE -> error("no slot for NONE")
    }

    private fun dueLabel(ad: ChatVisitAd): String =
        if (ad == ChatVisitAd.NONE) "no ad due" else "due ${placementName(ad)}"

    private fun placementName(ad: ChatVisitAd): String = ad.placement?.name?.lowercase().orEmpty()

    private fun logSkip(visitNumber: Int, ad: ChatVisitAd, reason: ChatAdSkipReason) {
        log("visit $visitNumber: skipped ${placementName(ad)} (${reason.label})")
    }
}
