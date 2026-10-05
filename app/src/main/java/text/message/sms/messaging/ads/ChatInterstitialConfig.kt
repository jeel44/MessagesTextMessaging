package text.message.sms.messaging.ads

/** What one chat visit (opening a chat from the inbox, then leaving it) shows -- see
 * [ChatInterstitialConfig.CYCLE]. Each ad kind has its own [placement], so the enter and exit ads
 * never share an ad unit or a loaded ad. */
internal enum class ChatVisitAd(val placement: AdPlacement?) {
    /** No ad on this visit. */
    NONE(null),

    /** Interstitial before the chat opens ([AdPlacement.CHAT_ENTER_INTERSTITIAL]). */
    ENTER_AD(AdPlacement.CHAT_ENTER_INTERSTITIAL),

    /** Interstitial over the inbox once the chat is left ([AdPlacement.CHAT_EXIT_INTERSTITIAL]). */
    EXIT_AD(AdPlacement.CHAT_EXIT_INTERSTITIAL),
}

/** Where a chat was opened from. Only [ChatInterstitialConfig.COUNTED_SOURCES] start a visit;
 * chats opened anywhere else (and notification / call-end hand-offs, which don't come through
 * [ChatInterstitialController] at all) never advance the cycle or show a chat interstitial. */
internal enum class ChatEntrySource { INBOX, SEARCH, ARCHIVED }

/**
 * The chat interstitials' pacing -- hardcoded, no remote config. See [ChatInterstitialController]
 * for how it's applied.
 */
internal object ChatInterstitialConfig {
    /** Master switch: false = no chat interstitial is ever requested or shown. */
    const val ENABLED: Boolean = true

    /** Minimum time between two shown chat interstitials (enter or exit). A due ad inside the gap
     * is skipped and the cycle still advances. 0 = no gap. */
    const val MIN_GAP_MILLIS: Long = 0L

    /** One entry per chat visit, in order; after the last, the cycle starts over. The first
     * counted visit after a cold start is entry 0. Edit this line to change the pattern. */
    val CYCLE: List<ChatVisitAd> = listOf(ChatVisitAd.EXIT_AD, ChatVisitAd.NONE, ChatVisitAd.NONE, ChatVisitAd.ENTER_AD)

    /** Sources whose chat opens count as a visit. Search and Archived open chats too, but aren't
     * the inbox list, so they don't count. */
    val COUNTED_SOURCES: Set<ChatEntrySource> = setOf(ChatEntrySource.INBOX)
}
