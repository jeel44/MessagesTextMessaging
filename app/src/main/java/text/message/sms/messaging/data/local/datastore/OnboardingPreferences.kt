package text.message.sms.messaging.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

/**
 * Onboarding progress: whether the flow has been completed (so Splash can skip straight to the
 * inbox on future launches) and the language the user picked (so a future in-app language switch
 * in Settings has something to read, alongside what [androidx.appcompat.app.AppCompatDelegate]
 * already persists for itself).
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
    }

    val isOnboardingComplete: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[Keys.ONBOARDING_COMPLETE] == true }

    suspend fun setOnboardingComplete() {
        context.onboardingDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = true }
    }

    /** [languageTag] is a BCP-47 tag, or `null` for "System Default". */
    suspend fun setLanguageTag(languageTag: String?) {
        context.onboardingDataStore.edit { prefs ->
            if (languageTag == null) {
                prefs.remove(Keys.LANGUAGE_TAG)
            } else {
                prefs[Keys.LANGUAGE_TAG] = languageTag
            }
        }
    }
}
