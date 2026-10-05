package text.message.sms.messaging.analytics

import text.message.sms.messaging.ads.AdConsentState

/**
 * What Firebase may collect, as Google consent-mode signals. [collectionEnabled] (Analytics
 * collection and Crashlytics reporting) follows [analyticsStorage].
 */
internal data class CollectionDecision(
    val analyticsStorage: Boolean,
    val adStorage: Boolean,
    val adUserData: Boolean,
    val adPersonalization: Boolean,
) {
    val collectionEnabled: Boolean get() = analyticsStorage

    companion object {
        val GRANTED_ALL = CollectionDecision(true, true, true, true)
        val DENIED_ALL = CollectionDecision(false, false, false, false)
    }
}

/**
 * Maps [AdConsentManager][text.message.sms.messaging.ads.AdConsentManager]'s result plus the
 * IAB TCF v2 values UMP stores ([gdprApplies] = `IABTCF_gdprApplies`, [purposeConsents] =
 * `IABTCF_PurposeConsents`) to a [CollectionDecision], or null while consent isn't known yet
 * ([AdConsentState.Pending]) -- collection then stays as it is (off on a first run).
 *
 * - GDPR applies (EEA/UK): the user's TCF purpose choices -- Purpose 1 (store/access information
 *   on a device) gates analytics_storage and ad_storage, Purposes 1+7 ad_user_data, Purposes 3+4
 *   ad_personalization. Consent declined, or not given yet, means all denied.
 * - GDPR doesn't apply: everything granted.
 * - Unknown (no TCF value): granted if UMP said ads may be requested (no consent needed); denied
 *   if the consent check failed with nothing from an earlier session -- the region is unknown, so
 *   it's treated as one that needs consent.
 */
internal fun decideCollection(
    consent: AdConsentState,
    gdprApplies: Int?,
    purposeConsents: String?,
): CollectionDecision? {
    if (consent == AdConsentState.Pending) return null
    return when {
        gdprApplies == 1 -> {
            val purposes = purposeConsents.orEmpty()
            fun granted(purpose: Int) = purposes.getOrNull(purpose - 1) == '1'
            CollectionDecision(
                analyticsStorage = granted(1),
                adStorage = granted(1),
                adUserData = granted(1) && granted(7),
                adPersonalization = granted(3) && granted(4),
            )
        }
        gdprApplies == 0 -> CollectionDecision.GRANTED_ALL
        consent == AdConsentState.Allowed -> CollectionDecision.GRANTED_ALL
        else -> CollectionDecision.DENIED_ALL
    }
}
