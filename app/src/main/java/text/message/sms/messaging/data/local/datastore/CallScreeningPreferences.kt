package text.message.sms.messaging.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.callScreeningDataStore: DataStore<Preferences> by preferencesDataStore(name = "call_screening")

/** What [CallScreeningStore] remembers about `ROLE_CALL_SCREENING` across sessions. */
data class CallScreeningState(
    /** The role has been seen held at least once on this install. */
    val grantedOnce: Boolean = false,
    /** Times the inbox's "turn it back on" banner was dismissed (or its request declined). */
    val reaskDismissCount: Int = 0,
    /** Wall-clock millis of the last such dismissal, or null if never. */
    val reaskLastDismissedAtMillis: Long? = null,
    /** The current loss of the role has already been reported to analytics. */
    val lossReported: Boolean = false,
)

/** [CallScreeningState]'s storage -- an interface so the re-ask logic can be unit-tested
 * without DataStore. */
interface CallScreeningStore {
    suspend fun read(): CallScreeningState

    /** The role is held now: it counts as granted once, and any loss is over. */
    suspend fun markRoleSeenHeld()

    suspend fun markLossReported()

    suspend fun recordReaskDismissal(atMillis: Long)
}

/**
 * [CallScreeningStore] over its own DataStore. Wiped on reinstall with the rest of this app's
 * storage (allowBackup=false), so a reinstall starts from "never granted".
 */
@Singleton
class CallScreeningPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CallScreeningStore {

    private object Keys {
        val GRANTED_ONCE = booleanPreferencesKey("granted_once")
        val REASK_DISMISS_COUNT = intPreferencesKey("reask_dismiss_count")
        val REASK_LAST_DISMISSED_AT = longPreferencesKey("reask_last_dismissed_at")
        val LOSS_REPORTED = booleanPreferencesKey("loss_reported")
    }

    override suspend fun read(): CallScreeningState {
        val prefs = context.callScreeningDataStore.data.first()
        return CallScreeningState(
            grantedOnce = prefs[Keys.GRANTED_ONCE] == true,
            reaskDismissCount = prefs[Keys.REASK_DISMISS_COUNT] ?: 0,
            reaskLastDismissedAtMillis = prefs[Keys.REASK_LAST_DISMISSED_AT],
            lossReported = prefs[Keys.LOSS_REPORTED] == true,
        )
    }

    override suspend fun markRoleSeenHeld() {
        context.callScreeningDataStore.edit {
            it[Keys.GRANTED_ONCE] = true
            it.remove(Keys.LOSS_REPORTED)
        }
    }

    override suspend fun markLossReported() {
        context.callScreeningDataStore.edit { it[Keys.LOSS_REPORTED] = true }
    }

    override suspend fun recordReaskDismissal(atMillis: Long) {
        context.callScreeningDataStore.edit {
            it[Keys.REASK_DISMISS_COUNT] = (it[Keys.REASK_DISMISS_COUNT] ?: 0) + 1
            it[Keys.REASK_LAST_DISMISSED_AT] = atMillis
        }
    }
}
