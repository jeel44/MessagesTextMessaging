package text.message.sms.messaging.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import text.message.sms.messaging.BuildConfig
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns Firebase Analytics and Crashlytics on or off to match consent. Both start off (manifest
 * meta-data) with every consent-mode signal denied.
 *
 * - Debug builds: never on. Analytics is deactivated by the manifest; Crashlytics is switched off
 *   here and any queued reports deleted. [Analytics] events only go to Logcat.
 * - Release builds: once [AdConsentManager.state] has left Pending, and again whenever UMP rewrites
 *   its TCF values (the privacy options form in Settings), [decideCollection] picks the consent
 *   signals and whether Analytics collects and Crashlytics reports. Reading the stored TCF values
 *   rather than [AdConsentManager.state] alone matters: declining consent still ends in
 *   [text.message.sms.messaging.ads.AdConsentState.Allowed] (limited ads), which says nothing about
 *   analytics.
 *
 * Crashes from before the decision are held on the device by Crashlytics: sent once reporting is
 * enabled, deleted when it's denied.
 */
@Singleton
internal class FirebaseCollectionController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adConsentManager: AdConsentManager,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val firebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    /** From Application.onCreate. Returns immediately; Firebase work runs on [scope]. */
    fun start() {
        if (BuildConfig.DEBUG) {
            Analytics.install { name, params -> Log.d(TAG, "event (debug build, not sent): $name $params") }
            scope.launch {
                FirebaseCrashlytics.getInstance().run {
                    setCrashlyticsCollectionEnabled(false)
                    deleteUnsentReports()
                }
            }
            return
        }
        Analytics.install { name, params -> firebaseAnalytics.logEvent(name, params.toBundle()) }
        scope.launch {
            // UMP stores the IAB TCF values in the app's default SharedPreferences.
            val tcf = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            combine(adConsentManager.state, tcf.tcfChanges()) { consent, _ ->
                decideCollection(consent, tcf.intOrNull(KEY_GDPR_APPLIES), tcf.stringOrNull(KEY_PURPOSE_CONSENTS))
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect(::apply)
        }
    }

    private fun apply(decision: CollectionDecision) {
        firebaseAnalytics.setConsent(
            mapOf(
                ConsentType.ANALYTICS_STORAGE to decision.analyticsStorage.toStatus(),
                ConsentType.AD_STORAGE to decision.adStorage.toStatus(),
                ConsentType.AD_USER_DATA to decision.adUserData.toStatus(),
                ConsentType.AD_PERSONALIZATION to decision.adPersonalization.toStatus(),
            ),
        )
        firebaseAnalytics.setAnalyticsCollectionEnabled(decision.collectionEnabled)
        FirebaseCrashlytics.getInstance().run {
            setCrashlyticsCollectionEnabled(decision.collectionEnabled)
            if (!decision.collectionEnabled) deleteUnsentReports()
        }
    }

    /** Emits once now, then on every change to an `IABTCF_` key (or a clear). */
    private fun SharedPreferences.tcfChanges(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(TCF_KEY_PREFIX)) trySend(Unit)
        }
        registerOnSharedPreferenceChangeListener(listener)
        send(Unit)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun SharedPreferences.intOrNull(key: String): Int? = when (val value = all[key]) {
        is Int -> value
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun SharedPreferences.stringOrNull(key: String): String? = all[key] as? String

    private fun Boolean.toStatus() = if (this) ConsentStatus.GRANTED else ConsentStatus.DENIED

    private fun Map<String, Any>.toBundle() = Bundle().apply {
        for ((key, value) in this@toBundle) {
            when (value) {
                is Long -> putLong(key, value)
                is String -> putString(key, value)
            }
        }
    }

    private companion object {
        const val TAG = "FirebaseCollection"
        const val TCF_KEY_PREFIX = "IABTCF_"
        const val KEY_GDPR_APPLIES = "IABTCF_gdprApplies"
        const val KEY_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
    }
}
