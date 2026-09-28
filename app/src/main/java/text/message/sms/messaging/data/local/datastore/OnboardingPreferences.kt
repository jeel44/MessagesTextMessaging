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
 * Onboarding's steps, in order. [OnboardingPreferences.currentStep] is the one the user is on (or
 * [DONE]) -- Splash resumes there after a process death, and each step stores the next one as it
 * completes.
 */
enum class OnboardingStep { LANGUAGE, INTRO, WELCOME, OVERLAY, SET_DEFAULT_SMS, DONE }

/**
 * [OnboardingPreferences.currentStep]'s resolution, pure so it can be tested on its own. A
 * stored step wins; otherwise (an install from before the step key existed, or a value this build
 * doesn't know) it falls back on the older flags: `onboarding_complete` means [OnboardingStep.DONE],
 * the unreleased `intro_pending` means [OnboardingStep.INTRO], and neither means a fresh start at
 * [OnboardingStep.LANGUAGE]. Nothing is migrated on disk -- the next step write replaces it all.
 */
internal fun resolveOnboardingStep(
    storedStep: String?,
    legacyOnboardingComplete: Boolean,
    legacyIntroPending: Boolean,
): OnboardingStep =
    storedStep?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
        ?: when {
            legacyOnboardingComplete -> OnboardingStep.DONE
            legacyIntroPending -> OnboardingStep.INTRO
            else -> OnboardingStep.LANGUAGE
        }

/**
 * Onboarding progress: which step the user is on ([currentStep] -- so Splash can resume there, or
 * skip straight to the inbox once it's [OnboardingStep.DONE]), whether any launch has got past
 * Splash yet ([hasCompletedFirstLaunch]), and the language the user picked (so a future in-app
 * language switch in Settings has something to read, alongside what
 * [androidx.appcompat.app.AppCompatDelegate] already persists for itself).
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val CURRENT_ONBOARDING_STEP = stringPreferencesKey("current_onboarding_step")

        /** Still written alongside [OnboardingStep.DONE], so a downgrade to a build from before
         * [CURRENT_ONBOARDING_STEP] doesn't restart onboarding. */
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

        /** Read-only: only an unreleased build wrote it (see [resolveOnboardingStep]); cleared by
         * the first step write. */
        val LEGACY_INTRO_PENDING = booleanPreferencesKey("intro_pending")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
    }

    val currentStep: Flow<OnboardingStep> =
        context.onboardingDataStore.data.map {
            resolveOnboardingStep(
                storedStep = it[Keys.CURRENT_ONBOARDING_STEP],
                legacyOnboardingComplete = it[Keys.ONBOARDING_COMPLETE] == true,
                legacyIntroPending = it[Keys.LEGACY_INTRO_PENDING] == true,
            )
        }

    /** [currentStep] is [OnboardingStep.DONE]. */
    val isOnboardingComplete: Flow<Boolean> = currentStep.map { it == OnboardingStep.DONE }

    /** Whether a launch on this install has already got past Splash -- Splash withholds the App
     * Open ad until this is true (see SplashViewModel). Wiped with the rest of this store on
     * reinstall (allowBackup=false), so a reinstall is a first launch again. */
    val hasCompletedFirstLaunch: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[Keys.FIRST_LAUNCH_COMPLETED] == true }

    val languageTag: Flow<String?> =
        context.onboardingDataStore.data.map { it[Keys.LANGUAGE_TAG] }

    /** A step was completed and [step] is next ([OnboardingStep.DONE] once the last one is). */
    suspend fun setCurrentStep(step: OnboardingStep) {
        context.onboardingDataStore.edit {
            it[Keys.CURRENT_ONBOARDING_STEP] = step.name
            if (step == OnboardingStep.DONE) it[Keys.ONBOARDING_COMPLETE] = true
            it.remove(Keys.LEGACY_INTRO_PENDING)
        }
    }

    suspend fun setFirstLaunchCompleted() {
        context.onboardingDataStore.edit { it[Keys.FIRST_LAUNCH_COMPLETED] = true }
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
