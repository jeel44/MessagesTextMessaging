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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * Selection is two-phase: tapping a row only highlights it ([selectLanguage]); nothing is applied
 * until [onApplyClicked]. Every visit starts with nothing highlighted -- from Settings too, where
 * the active language is deliberately neither pre-selected nor marked.
 *
 * Both entry points get the same ads (see [startAds]): a native ad ([AdUnitIds.LANGUAGE_NATIVE])
 * requested at most twice per visit -- the initial load, plus one refresh on the first language
 * tap -- and an interstitial ([AdUnitIds.LANGUAGE_INTERSTITIAL]) shown on every Apply.
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

    private var adsStarted = false
    private var applyPressed = false

    /** The highlighted row, or null until the first tap. Kept in [savedStateHandle] (by id) so
     * rotation and process death mid-screen keep the highlight. */
    internal val selectedLanguage: StateFlow<LanguageOption?> = savedStateHandle
        .getStateFlow<String?>(KEY_SELECTED_LANGUAGE_ID, null)
        .map { id -> LanguageOptions.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Silent and non-blocking by design: no progress UI on this screen for it, and Apply
        // never waits on it -- see LanguageScreen/MessagingNavHost.
        viewModelScope.launch { syncMessages() }
    }

    /** Highlight only -- nothing is applied until [onApplyClicked]. */
    internal fun selectLanguage(language: LanguageOption) {
        savedStateHandle[KEY_SELECTED_LANGUAGE_ID] = language.id
        refreshNativeAdOnFirstSelection()
    }

    /** Idempotent. Waits for consent to resolve, then loads the native ad and preloads the Apply
     * interstitial, or settles both on unavailable. */
    internal fun startAds() {
        if (adsStarted) return
        adsStarted = true
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

    /** The native ad's second and last request: the first language tap of this visit refreshes
     * it once; later taps never do (the initial load in [startAds] is the first request). The
     * flag lives in [savedStateHandle], so rotation and process death mid-screen keep it set (a
     * restored screen loads a fresh ad, which isn't a refresh); only a fresh entry (a new
     * ViewModel) starts it false. Consumed even if the refresh is skipped (ad still loading or
     * failed -- see [NativeAdLoader.refresh]). */
    private fun refreshNativeAdOnFirstSelection() {
        if (!adsStarted) return
        if (savedStateHandle.get<Boolean>(KEY_FIRST_SELECTION_REFRESH_DONE) == true) return
        savedStateHandle[KEY_FIRST_SELECTION_REFRESH_DONE] = true
        nativeAdLoader.refresh()
    }

    /**
     * Apply, in this order:
     * 1. Persist the tag to [OnboardingPreferences] (and, from onboarding, mark onboarding
     *    complete, so it sticks even if the process dies while the ad is up). MainActivity
     *    localizes its Compose tree off that tag, so the app's own UI switches language right
     *    away, with no configuration change.
     * 2. Show the preloaded interstitial, if ready.
     * 3. Once it's dismissed (or fails to show, or wasn't ready), call
     *    [AppCompatDelegate.setApplicationLocales], then [onDone].
     *
     * [AppCompatDelegate.setApplicationLocales] is held until the ad is gone on purpose: on API
     * 33+ it goes through the platform LocaleManager, which pushes a configuration change to every
     * Activity in the app -- the ad's own included -- and doing that while the interstitial is
     * opening or up risks leaving it stuck on screen.
     *
     * No-op until a row is selected; taps after the first are ignored, so a quick double-tap
     * can't apply twice or navigate under an opening ad.
     */
    internal fun onApplyClicked(activity: Activity?, isOnboarding: Boolean, onDone: () -> Unit) {
        val language = selectedLanguage.value ?: return
        if (applyPressed) return
        applyPressed = true
        viewModelScope.launch {
            onboardingPreferences.setLanguageTag(language.languageTag)
            if (isOnboarding) onboardingPreferences.setOnboardingComplete()
            val finish = {
                AppCompatDelegate.setApplicationLocales(
                    language.languageTag
                        ?.let(LocaleListCompat::forLanguageTags)
                        ?: LocaleListCompat.getEmptyLocaleList(),
                )
                onDone()
            }
            val shown = activity != null && interstitialLoader.showIfReady(activity, onFinished = finish)
            if (!shown) finish()
        }
    }

    override fun onCleared() {
        nativeAdLoader.destroy()
        interstitialLoader.destroy()
    }

    private companion object {
        const val KEY_SELECTED_LANGUAGE_ID = "language_selected_id"
        const val KEY_FIRST_SELECTION_REFRESH_DONE = "language_first_selection_refresh_done"
    }
}
