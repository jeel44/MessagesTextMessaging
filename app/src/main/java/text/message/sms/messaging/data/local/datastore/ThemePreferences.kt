package text.message.sms.messaging.data.local.datastore

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme")

/** Light/dark/system mode -- the tri-state choice
 * [text.message.sms.messaging.ui.theme.AppTheme]'s plain `darkTheme: Boolean` parameter can't
 * represent on its own; [SYSTEM] resolves to [androidx.compose.foundation.isSystemInDarkTheme] at
 * the call site rather than being persisted as a resolved boolean, so it keeps tracking the
 * system setting even if that changes after the user picked it. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The user's whole theme choice: [mode] plus an optional [accentColor] override. A `null`
 * [accentColor] keeps the app's default coloring, the hand-authored blue scheme -- see
 * [text.message.sms.messaging.ui.theme.AppTheme]. */
data class ThemePreference(val mode: ThemeMode = ThemeMode.LIGHT, val accentColor: Color? = null)

/** Persists the Settings theme picker's choice; read back by `MainActivity` to feed
 * [text.message.sms.messaging.ui.theme.AppTheme] so a change applies live across the whole app,
 * not just the Settings screen. */
@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR_ARGB = intPreferencesKey("accent_color_argb")
    }

    val themePreference: Flow<ThemePreference> = context.themeDataStore.data.map { prefs ->
        ThemePreference(
            mode = parseStoredMode(prefs[Keys.MODE]),
            accentColor = prefs[Keys.ACCENT_COLOR_ARGB]?.let { Color(it) },
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[Keys.MODE] = mode.name }
    }

    /** `null` resets to the app's default coloring. */
    suspend fun setAccentColor(color: Color?) {
        context.themeDataStore.edit { prefs ->
            if (color == null) {
                prefs.remove(Keys.ACCENT_COLOR_ARGB)
            } else {
                prefs[Keys.ACCENT_COLOR_ARGB] = color.toArgb()
            }
        }
    }

    /**
     * One-time default-theme decision for a device that has never had an explicit [ThemeMode]
     * stored -- called from [text.message.sms.messaging.MessagingApplication.onCreate], before any
     * screen ever reads [themePreference], so the first frame is never mid-decision.
     *
     * [isExistingUser] tells apart a fresh install (nothing stored yet -> takes the new
     * [ThemeMode.LIGHT] default) from a device updating into this default's introduction (already
     * had onboarding completed on a previous version -> its old system-following behavior is
     * persisted explicitly instead of being silently switched to light). Once [Keys.MODE] is
     * written this way, this is a no-op on every later launch -- the decision only ever runs once.
     */
    suspend fun migrateDefaultThemeModeIfNeeded(isExistingUser: Boolean) {
        context.themeDataStore.edit { prefs ->
            if (prefs[Keys.MODE] == null) {
                prefs[Keys.MODE] = resolveMigratedMode(isExistingUser).name
            }
        }
    }

    companion object {
        /** No stored value (or an unparsable one, which should not happen) resolves to
         * [ThemeMode.LIGHT] -- the app's default absent any real choice. Factored out so it's
         * unit-testable without a real [Context]/DataStore. */
        internal fun parseStoredMode(stored: String?): ThemeMode =
            stored?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() } ?: ThemeMode.LIGHT

        /** The [migrateDefaultThemeModeIfNeeded] decision, factored out for unit testing. */
        internal fun resolveMigratedMode(isExistingUser: Boolean): ThemeMode =
            if (isExistingUser) ThemeMode.SYSTEM else ThemeMode.LIGHT
    }
}
