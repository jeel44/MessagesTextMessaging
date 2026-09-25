package text.message.sms.messaging.ui.screens.onboarding

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ads.AdUnitIds
import text.message.sms.messaging.ads.InterstitialAdLoader
import text.message.sms.messaging.ads.NativeAdLoader
import text.message.sms.messaging.ads.NativeAdState
import text.message.sms.messaging.data.local.datastore.OnboardingPreferences
import text.message.sms.messaging.domain.usecase.SyncMessages
import javax.inject.Inject

/**
 * Backs [LanguageScreen], reached both from onboarding (a one-time step) and from Settings (to
 * change the language later) -- see [LanguageScreen]'s doc for how it tells the two apart.
 *
 * Also owns the moment the post-onboarding sync starts: the instant this ViewModel is created
 * (i.e. the instant the screen is first composed), on [viewModelScope] so the sync keeps running
 * even if the user continues on to Home before it finishes. Reached from Settings, this sync is
 * already long done and [SyncMessages] is idempotent, so it's harmless there too.
 *
 * Onboarding only, it also owns the screen's ads (see [startOnboardingAds]): a native ad
 * ([AdUnitIds.LANGUAGE_NATIVE]) refreshed once on the first language tap of the visit, and an
 * interstitial ([AdUnitIds.LANGUAGE_INTERSTITIAL]) shown on Continue. Reached from Settings,
 * [startOnboardingAds] is never called, so neither loader ever makes a request there.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val syncMessages: SyncMessages,
    private val onboardingPreferences: OnboardingPreferences,
    private val adConsentManager: AdConsentManager,
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext context: Context,
) : ViewModel() {

    private val nativeAdLoader = NativeAdLoader(context, AdUnitIds.LANGUAGE_NATIVE)
    internal val nativeAdState: StateFlow<NativeAdState> = nativeAdLoader.state

    private val interstitialLoader = InterstitialAdLoader(context, AdUnitIds.LANGUAGE_INTERSTITIAL)

    private var onboardingAdsStarted = false
    private var continuePressed = false

    // Seeded from AppCompatDelegate's own state, not always LanguageOptions.first() -- reopening
    // this screen from Settings after a language was already picked must show *that* language
    // selected, not silently reset the radio group back to "System Default" every time.
    private val _selectedLanguage = MutableStateFlow(currentLanguageOption())
    internal val selectedLanguage: StateFlow<LanguageOption> = _selectedLanguage.asStateFlow()

    init {
        // Silent and non-blocking by design: no progress UI on this screen for it, and Continue
        // never waits on it -- see LanguageScreen/MessagingNavHost.
        viewModelScope.launch { syncMessages() }
    }

    internal fun selectLanguage(language: LanguageOption) {
        _selectedLanguage.value = language
        AppCompatDelegate.setApplicationLocales(
            language.languageTag
                ?.let(LocaleListCompat::forLanguageTags)
                ?: LocaleListCompat.getEmptyLocaleList(),
        )
        viewModelScope.launch { onboardingPreferences.setLanguageTag(language.languageTag) }
        refreshNativeAdOnFirstSelection()
    }

    /** Onboarding only (the screen calls this only when it has a Continue step). Idempotent. Waits
     * for consent to resolve, then loads the native ad and preloads the Continue interstitial, or
     * settles both on unavailable. */
    internal fun startOnboardingAds() {
        if (onboardingAdsStarted) return
        onboardingAdsStarted = true
        viewModelScope.launch {
            when (adConsentManager.state.first { it != AdConsentState.Pending }) {
                AdConsentState.Allowed -> {
                    nativeAdLoader.start()
                    interstitialLoader.start()
                }
                else -> {
                    nativeAdLoader.markUnavailable()
                    interstitialLoader.markUnavailable()
                }
            }
        }
    }

    /** The first language tap of this visit -- any row, including the already-selected one --
     * refreshes the native ad once; later taps never do. The flag lives in [savedStateHandle], so
     * rotation and process death mid-screen keep it set (a restored screen loads a fresh ad, which
     * isn't a refresh); only a fresh entry (a new ViewModel) starts it false. Consumed even if the
     * refresh is skipped (ad still loading or failed -- see [NativeAdLoader.refresh]). */
    private fun refreshNativeAdOnFirstSelection() {
        if (!onboardingAdsStarted) return
        if (savedStateHandle.get<Boolean>(KEY_FIRST_SELECTION_REFRESH_DONE) == true) return
        savedStateHandle[KEY_FIRST_SELECTION_REFRESH_DONE] = true
        nativeAdLoader.refresh()
    }

    /** Continue: marks onboarding complete first (so it sticks even if the process dies while the
     * interstitial is up), then shows the preloaded interstitial and calls [onAdvance] once it's
     * dismissed (or fails to show) -- or immediately if it isn't ready. Never waits on an ad. Taps
     * after the first are ignored, so a quick double-tap can't navigate under an opening ad. */
    internal fun onContinueClicked(activity: Activity?, onAdvance: () -> Unit) {
        if (continuePressed) return
        continuePressed = true
        completeOnboarding()
        val shown = activity != null && interstitialLoader.showIfReady(activity, onFinished = onAdvance)
        if (!shown) onAdvance()
    }

    fun completeOnboarding() {
        viewModelScope.launch { onboardingPreferences.setOnboardingComplete() }
    }

    override fun onCleared() {
        nativeAdLoader.destroy()
        interstitialLoader.destroy()
    }

    private companion object {
        const val KEY_FIRST_SELECTION_REFRESH_DONE = "language_first_selection_refresh_done"
    }
}
