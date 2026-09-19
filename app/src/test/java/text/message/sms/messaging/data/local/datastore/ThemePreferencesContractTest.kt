package text.message.sms.messaging.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Pins the first-install-defaults-to-light behavior: [ThemePreferences.migrateDefaultThemeModeIfNeeded]
 * must default a fresh install to [ThemeMode.LIGHT] while leaving an existing user's stored (or
 * previously-implicit [ThemeMode.SYSTEM]) choice alone, and the choice made by
 * [ThemePreferences.setThemeMode] must persist across later reads.
 *
 * Like [OnboardingPreferencesContractTest], this does not construct [ThemePreferences] itself --
 * it needs a real Android [android.content.Context], which a plain JVM unit test can't supply --
 * so it exercises the same `"theme_mode"` key against an isolated, throwaway DataStore file, while
 * calling [ThemePreferences.resolveMigratedMode] and [ThemePreferences.parseStoredMode] directly
 * since those are pure functions with no [android.content.Context] dependency.
 */
class ThemePreferencesContractTest {

    private lateinit var file: File
    private lateinit var dataStoreScope: CoroutineScope
    private val modeKey = stringPreferencesKey("theme_mode")

    @Before
    fun setUp() {
        // Mirrors a genuinely fresh install: the file must not exist yet, only its path.
        file = File.createTempFile("theme_contract_test", ".preferences_pb").also { it.delete() }
        dataStoreScope = CoroutineScope(Job())
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        file.delete()
    }

    private fun newDataStore() = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }

    private suspend fun migrate(dataStore: DataStore<Preferences>, isExistingUser: Boolean) {
        dataStore.edit { prefs ->
            if (prefs[modeKey] == null) {
                prefs[modeKey] = ThemePreferences.resolveMigratedMode(isExistingUser).name
            }
        }
    }

    private suspend fun readMode(dataStore: DataStore<Preferences>) =
        dataStore.data.map { ThemePreferences.parseStoredMode(it[modeKey]) }.first()

    @Test
    fun parseStoredMode_nothingStored_resolvesToLight() {
        assertEquals(ThemeMode.LIGHT, ThemePreferences.parseStoredMode(null))
    }

    @Test
    fun parseStoredMode_unparsableValue_resolvesToLight() {
        assertEquals(ThemeMode.LIGHT, ThemePreferences.parseStoredMode("not_a_real_mode"))
    }

    @Test
    fun parseStoredMode_validValue_roundTrips() {
        assertEquals(ThemeMode.DARK, ThemePreferences.parseStoredMode("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.parseStoredMode("SYSTEM"))
    }

    @Test
    fun resolveMigratedMode_existingUser_isSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.resolveMigratedMode(isExistingUser = true))
    }

    @Test
    fun resolveMigratedMode_freshInstall_isLight() {
        assertEquals(ThemeMode.LIGHT, ThemePreferences.resolveMigratedMode(isExistingUser = false))
    }

    @Test
    fun freshInstall_migration_defaultsToLight() = runBlocking {
        val dataStore = newDataStore()

        migrate(dataStore, isExistingUser = false)

        assertEquals(ThemeMode.LIGHT, readMode(dataStore))
    }

    @Test
    fun existingUser_neverChoseATheme_migrationKeepsSystemBehavior() = runBlocking {
        val dataStore = newDataStore()

        // Simulates a device updating into this feature: onboarding already completed on a
        // previous version, and the theme picker was never opened, so theme_mode is unset.
        migrate(dataStore, isExistingUser = true)

        assertEquals(ThemeMode.SYSTEM, readMode(dataStore))
    }

    @Test
    fun existingUser_storedDark_migrationLeavesItUnchanged() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit { it[modeKey] = ThemeMode.DARK.name }

        migrate(dataStore, isExistingUser = true)

        assertEquals(ThemeMode.DARK, readMode(dataStore))
    }

    @Test
    fun migration_neverReRunsOnceAnExplicitValueIsStored() = runBlocking {
        val dataStore = newDataStore()

        migrate(dataStore, isExistingUser = false) // fresh install -> LIGHT stored explicitly
        assertEquals(ThemeMode.LIGHT, readMode(dataStore))

        // A later launch calling migrate() again (e.g. as an existing user now, post-onboarding)
        // must not override the value already decided and stored.
        migrate(dataStore, isExistingUser = true)
        assertEquals(ThemeMode.LIGHT, readMode(dataStore))
    }

    @Test
    fun userSelectsATheme_itPersistsAcrossReads() = runBlocking {
        val dataStore = newDataStore()

        dataStore.edit { it[modeKey] = ThemeMode.DARK.name }
        assertEquals(ThemeMode.DARK, readMode(dataStore))

        // A fresh Flow read (simulating a later launch/recomposition) still sees the same choice.
        assertEquals(ThemeMode.DARK, readMode(dataStore))
    }
}
