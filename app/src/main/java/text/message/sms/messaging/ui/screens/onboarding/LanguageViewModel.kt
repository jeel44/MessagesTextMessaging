package text.message.sms.messaging.ui.screens.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val syncMessages: SyncMessages,
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {

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
    }

    fun completeOnboarding() {
        viewModelScope.launch { onboardingPreferences.setOnboardingComplete() }
    }
}
