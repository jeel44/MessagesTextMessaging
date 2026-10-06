package text.message.sms.messaging.analytics

import text.message.sms.messaging.ads.AdPlacement
import text.message.sms.messaging.data.local.datastore.OnboardingStep

/**
 * The only way anything in the app reaches Firebase Analytics. Event names and parameter keys are
 * a closed set ([AnalyticsEvent], [AnalyticsParam]), and every parameter value is either a short
 * lowercase identifier (checked against [IDENTIFIER_VALUE]) or a small number -- so message text,
 * phone numbers, contact names and addresses can't be passed, by accident or otherwise. No user ID
 * is ever set.
 *
 * Callers use the typed functions below. Until [install] runs (and always in unit tests) events
 * go nowhere; whether they then leave the device is decided by consent, see
 * [FirebaseCollectionController].
 */
internal object Analytics {

    @Volatile
    private var sink: AnalyticsSink? = null

    fun install(sink: AnalyticsSink?) {
        this.sink = sink
    }

    /** An onboarding step was reached (stored as the current step, about to be shown). */
    fun onboardingStep(step: OnboardingStep) =
        log(AnalyticsEvent.ONBOARDING_STEP, AnalyticsParam.STEP to step.name.lowercase())

    /** [languageId] is a [text.message.sms.messaging.ui.screens.onboarding.LanguageOption] id:
     * a language tag such as "en", or "system". */
    fun languageSelected(languageId: String) =
        log(AnalyticsEvent.LANGUAGE_SELECTED, AnalyticsParam.LANGUAGE to languageId)

    fun introSlideViewed(index: Int) =
        log(AnalyticsEvent.INTRO_SLIDE_VIEWED, AnalyticsParam.INDEX to index.toLong())

    fun defaultSmsResult(granted: Boolean) =
        log(AnalyticsEvent.DEFAULT_SMS_RESULT, AnalyticsParam.RESULT to if (granted) "granted" else "denied")

    /** How a call-screening role request from [source] ended -- see [CallScreeningResult]. */
    fun callScreeningResult(result: CallScreeningResult, source: CallScreeningSource) = log(
        AnalyticsEvent.CALL_SCREENING_RESULT,
        AnalyticsParam.RESULT to result.name.lowercase(),
        AnalyticsParam.SOURCE to source.name.lowercase(),
    )

    /** The call-screening role, granted once, was found held by another app. Once per loss. */
    fun callScreeningLost() = log(AnalyticsEvent.CALL_SCREENING_LOST)

    fun drawerItemOpened(item: DrawerItem) =
        log(AnalyticsEvent.DRAWER_ITEM_OPENED, AnalyticsParam.ITEM to item.name.lowercase())

    fun numberBlocked() = log(AnalyticsEvent.NUMBER_BLOCKED)

    fun numberUnblocked() = log(AnalyticsEvent.NUMBER_UNBLOCKED)

    fun conversationArchived() = log(AnalyticsEvent.CONVERSATION_ARCHIVED)

    fun messageScheduled() = log(AnalyticsEvent.MESSAGE_SCHEDULED)

    fun scheduledCancelled() = log(AnalyticsEvent.SCHEDULED_CANCELLED)

    fun scheduledSendNow() = log(AnalyticsEvent.SCHEDULED_SEND_NOW)

    /** A scheduled message failed or was missed. */
    fun scheduledFailed() = log(AnalyticsEvent.SCHEDULED_FAILED)

    /** An ad recorded an impression. Placement and format only -- never anything from the ad. */
    fun adShown(placement: AdPlacement, format: AdFormat) = logAd(AnalyticsEvent.AD_SHOWN, placement, format)

    /** An ad failed to load or to show. */
    fun adFailed(placement: AdPlacement, format: AdFormat) = logAd(AnalyticsEvent.AD_FAILED, placement, format)

    private fun logAd(event: AnalyticsEvent, placement: AdPlacement, format: AdFormat) = log(
        event,
        AnalyticsParam.PLACEMENT to placement.name.lowercase(),
        AnalyticsParam.FORMAT to format.name.lowercase(),
    )

    /** Hands [event] to the installed sink if [params] pass [validatedParams]; returns whether
     * they did. A rejected event is dropped whole, never sent with the bad parameter stripped. */
    fun log(event: AnalyticsEvent, vararg params: Pair<AnalyticsParam, Any>): Boolean {
        val validated = validatedParams(event, params.toList()) ?: return false
        sink?.log(event.eventName, validated)
        return true
    }
}

/** Where validated events go -- Firebase in the app, a fake in tests. */
internal fun interface AnalyticsSink {
    fun log(eventName: String, params: Map<String, Any>)
}

/** Every event the app may log, with the parameters each one may carry. */
internal enum class AnalyticsEvent(val eventName: String, val allowedParams: Set<AnalyticsParam>) {
    ONBOARDING_STEP("onboarding_step", setOf(AnalyticsParam.STEP)),
    LANGUAGE_SELECTED("language_selected", setOf(AnalyticsParam.LANGUAGE)),
    INTRO_SLIDE_VIEWED("intro_slide_viewed", setOf(AnalyticsParam.INDEX)),
    DEFAULT_SMS_RESULT("default_sms_result", setOf(AnalyticsParam.RESULT)),
    CALL_SCREENING_RESULT("call_screening_result", setOf(AnalyticsParam.RESULT, AnalyticsParam.SOURCE)),
    CALL_SCREENING_LOST("call_screening_lost", emptySet()),
    DRAWER_ITEM_OPENED("drawer_item_opened", setOf(AnalyticsParam.ITEM)),
    NUMBER_BLOCKED("number_blocked", emptySet()),
    NUMBER_UNBLOCKED("number_unblocked", emptySet()),
    CONVERSATION_ARCHIVED("conversation_archived", emptySet()),
    MESSAGE_SCHEDULED("message_scheduled", emptySet()),
    SCHEDULED_CANCELLED("scheduled_cancelled", emptySet()),
    SCHEDULED_SEND_NOW("scheduled_send_now", emptySet()),
    SCHEDULED_FAILED("scheduled_failed", emptySet()),
    AD_SHOWN("ad_shown", setOf(AnalyticsParam.PLACEMENT, AnalyticsParam.FORMAT)),
    AD_FAILED("ad_failed", setOf(AnalyticsParam.PLACEMENT, AnalyticsParam.FORMAT)),
}

/** Every parameter key the app may send. [numeric] keys take a [Long] in [NUMERIC_VALUE_RANGE];
 * the rest take a [String] matching [IDENTIFIER_VALUE]. */
internal enum class AnalyticsParam(val key: String, val numeric: Boolean = false) {
    STEP("step"),
    LANGUAGE("language"),
    INDEX("index", numeric = true),
    RESULT("result"),
    SOURCE("source"),
    ITEM("item"),
    PLACEMENT("placement"),
    FORMAT("format"),
}

/** [GRANTED]/[DECLINED]: the role request dialog came back with the role held, or not.
 * [SKIPPED]: onboarding's "Not now" or the inbox banner's close, without opening the dialog. An
 * onboarding step skipped automatically (role already held, or unavailable) logs nothing. */
internal enum class CallScreeningResult { GRANTED, DECLINED, SKIPPED }

/** Where a [CallScreeningResult] came from: onboarding's role screen, the Settings row, or the
 * inbox's re-ask banner. */
internal enum class CallScreeningSource { ONBOARDING, SETTINGS, BANNER }

internal enum class DrawerItem { ARCHIVED, BLOCKED, SCHEDULED, LANGUAGE }

internal enum class AdFormat { NATIVE, BANNER, INTERSTITIAL, APP_OPEN }

/** A lowercase identifier: starts with a letter, so no phone number fits; no spaces, capitals or
 * punctuation beyond '_' and '-', so no message text or name fits. */
internal val IDENTIFIER_VALUE = Regex("^[a-z][a-z0-9_-]{0,35}$")

internal val NUMERIC_VALUE_RANGE = 0L..99L

/** [params] keyed by their Firebase names, or null if any of them isn't allowed on [event], is
 * repeated, or has a value of the wrong type or shape. */
internal fun validatedParams(
    event: AnalyticsEvent,
    params: List<Pair<AnalyticsParam, Any>>,
): Map<String, Any>? {
    val result = LinkedHashMap<String, Any>()
    for ((param, value) in params) {
        if (param !in event.allowedParams || param.key in result) return null
        val valid = if (param.numeric) {
            value is Long && value in NUMERIC_VALUE_RANGE
        } else {
            value is String && IDENTIFIER_VALUE.matches(value)
        }
        if (!valid) return null
        result[param.key] = value
    }
    return result
}
