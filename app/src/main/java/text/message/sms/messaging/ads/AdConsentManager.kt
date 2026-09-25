package text.message.sms.messaging.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import text.message.sms.messaging.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Whether ad requests may go out -- [Pending] until UMP has resolved consent for this session,
 * then [Allowed] ([ConsentInformation.canRequestAds] is true) or [Unavailable] (it isn't: consent
 * is still required, or the consent check/form failed and nothing from a previous session allows
 * ads either). Every ad slot treats [Unavailable] exactly like a failed load and collapses. */
enum class AdConsentState { Pending, Allowed, Unavailable }

/**
 * The single gate in front of every ad request, wrapping Google's User Messaging Platform (UMP)
 * for GDPR/UK and US-state consent. Nothing may call [MobileAds.initialize] or load an ad until
 * [state] is [AdConsentState.Allowed] -- [BannerAdLoader]/[CallEndAdLoader] themselves don't know
 * about consent; their owners ([text.message.sms.messaging.ui.components.ads.BannerAdWithShimmer],
 * [text.message.sms.messaging.ui.screens.callend.CallEndViewModel]) wait on [state] before calling
 * `start()`.
 *
 * Starts as [AdConsentState.Allowed] when [ConsentInformation.canRequestAds] is already true from
 * a previous session (Google's documented pattern: returning users get ads immediately while the
 * consent info refreshes in the background), otherwise [AdConsentState.Pending].
 *
 * - [gatherConsent] (MainActivity, every launch): refreshes consent info, then shows the consent
 *   form only if UMP says it's required.
 * - [refreshConsentInfo] (CallEndActivity): refreshes only, never shows the form -- a GDPR dialog
 *   over a just-ended call is the wrong place for it, and every user reaching call-end has already
 *   been through onboarding in MainActivity.
 *
 * Declining consent still ends in [AdConsentState.Allowed]: [ConsentInformation.canRequestAds] is
 * true once the form has been completed either way, and the Ads SDK reads the stored TCF string
 * itself to serve limited/non-personalized ads -- there is no separate code path for that here.
 */
@Singleton
class AdConsentManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    private val _state = MutableStateFlow(
        if (consentInformation.canRequestAds()) AdConsentState.Allowed else AdConsentState.Pending,
    )
    val state: StateFlow<AdConsentState> = _state.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(isPrivacyOptionsRequired())

    /** Whether Settings must offer the "Privacy options" entry point ([showPrivacyOptionsForm]) --
     * UMP requires one for users in regions where consent can be changed later (EEA/UK). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val mobileAdsInitialized = AtomicBoolean(false)

    /** Called from Application.onCreate in place of an unconditional [MobileAds.initialize]: only
     * initializes the SDK when consent from a previous session already allows ads. Otherwise
     * initialization waits for [gatherConsent]/[refreshConsentInfo] to settle on Allowed. */
    fun initializeAdsIfAllowed() {
        if (_state.value == AdConsentState.Allowed) initializeMobileAds()
    }

    fun gatherConsent(activity: Activity) {
        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters(),
            {
                debugLog("consent info updated: status=${consentInformation.consentStatus}")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) logError("consent form", formError)
                    settle()
                }
            },
            { requestError ->
                logError("consent info update", requestError)
                settle()
            },
        )
    }

    fun refreshConsentInfo(activity: Activity) {
        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters(),
            {
                debugLog("consent info refreshed (no form): status=${consentInformation.consentStatus}")
                settle()
            },
            { requestError ->
                logError("consent info refresh", requestError)
                settle()
            },
        )
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) logError("privacy options form", formError)
            settle()
        }
    }

    private fun settle() {
        _privacyOptionsRequired.value = isPrivacyOptionsRequired()
        val canRequestAds = consentInformation.canRequestAds()
        // Initialize before publishing Allowed, so no slot can start a request ahead of it.
        if (canRequestAds) initializeMobileAds()
        _state.value = if (canRequestAds) AdConsentState.Allowed else AdConsentState.Unavailable
        debugLog(
            "settled: state=${_state.value} privacyOptionsRequired=${_privacyOptionsRequired.value}",
        )
    }

    private fun initializeMobileAds() {
        if (!mobileAdsInitialized.compareAndSet(false, true)) return
        // Fire-and-forget: the SDK's own init work runs on its own background thread.
        MobileAds.initialize(context)
        debugLog("MobileAds.initialize called")
    }

    private fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private fun requestParameters(): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        debugSettings()?.let { builder.setConsentDebugSettings(it) }
        return builder.build()
    }

    /** Debug builds only, from local.properties via BuildConfig (see app/build.gradle.kts) --
     * both fields are always blank in release, so this returns null there. */
    private fun debugSettings(): ConsentDebugSettings? {
        if (!BuildConfig.DEBUG) return null
        val geography = when (BuildConfig.UMP_DEBUG_GEOGRAPHY.uppercase()) {
            "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            "REGULATED_US_STATE" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
            "OTHER" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
            else -> null
        }
        val hashedId = BuildConfig.UMP_TEST_DEVICE_HASHED_ID
        if (geography == null && hashedId.isBlank()) return null
        debugLog("debug settings: geography=${BuildConfig.UMP_DEBUG_GEOGRAPHY} testDevice=${hashedId.isNotBlank()}")
        return ConsentDebugSettings.Builder(context)
            .apply {
                if (geography != null) setDebugGeography(geography)
                if (hashedId.isNotBlank()) addTestDeviceHashedId(hashedId)
            }
            .build()
    }

    private fun logError(step: String, error: FormError) {
        if (BuildConfig.DEBUG) Log.w(TAG, "$step failed: ${error.errorCode} ${error.message}")
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AdConsentManager"
    }
}

/** For Compose ad slots with no ViewModel to inject [AdConsentManager] into (see
 * [text.message.sms.messaging.ui.components.ads.BannerAdWithShimmer]). */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AdConsentEntryPoint {
    fun adConsentManager(): AdConsentManager
}
