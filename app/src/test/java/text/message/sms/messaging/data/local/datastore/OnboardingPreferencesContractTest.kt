package text.message.sms.messaging.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Pins the exact DataStore contract [OnboardingPreferences.isOnboardingComplete] depends on: a
 * `booleanPreferencesKey` that was never written reads back `null`, which
 * `it[KEY] == true` must map to `false` -- i.e. a brand-new install (no onboarding datastore file
 * on disk yet) must never be treated as "onboarding already completed".
 *
 * This deliberately does not construct [OnboardingPreferences] itself: that class resolves its
 * DataStore from a process-wide singleton keyed to the real app [android.content.Context] (see
 * `Context.onboardingDataStore`), so touching it from a test risks either reading stale state
 * some other test already wrote in the same process, or -- if ever run against a real device
 * instead of the JVM -- writing to the actual on-device onboarding flag. Using an isolated,
 * throwaway file with the identical key/logic instead verifies the same contract with no such
 * risk, at the cost of not exercising `OnboardingPreferences`'s own code directly.
 */
class OnboardingPreferencesContractTest {

    private lateinit var file: File
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setUp() {
        // Mirrors a genuinely fresh install: the file must not exist yet, only its path.
        file = File.createTempFile("onboarding_contract_test", ".preferences_pb").also { it.delete() }
        // A scope of its own, not the test's runBlocking scope -- DataStore's internal actor
        // coroutine runs on whatever scope it's given and never completes on its own, so handing
        // it runBlocking's own scope would make runBlocking wait on it forever.
        dataStoreScope = CoroutineScope(Job())
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        file.delete()
    }

    @Test
    fun onboardingComplete_defaultsToFalse_thenTrueOnlyAfterRealCompletion() = runBlocking {
        val key = booleanPreferencesKey("onboarding_complete")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }

        val beforeCompletion = dataStore.data.map { it[key] == true }.first()
        assertFalse("a never-written flag must read as incomplete, not complete", beforeCompletion)

        dataStore.edit { it[key] = true }

        val afterCompletion = dataStore.data.map { it[key] == true }.first()
        assertTrue(afterCompletion)
    }
}
