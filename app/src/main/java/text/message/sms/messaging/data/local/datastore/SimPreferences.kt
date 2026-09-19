package text.message.sms.messaging.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.simPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "sim_preferences")

/** Which SIM a dual-SIM device sends from -- the Settings "SIM for sending messages" row's
 * choice. [SLOT_0]/[SLOT_1] are keyed by physical slot index (stable across a SIM swap), not a
 * subscription id (which is not). Meaningless, and never surfaced, on a single-SIM device. */
enum class SimSendPreference { SLOT_0, SLOT_1, ASK }

/** Persists the Settings "SIM for sending messages" row's choice; read back by
 * `text.message.sms.messaging.domain.usecase.ResolveSendSubscription` at send time. */
@Singleton
class SimPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val SEND_PREFERENCE = stringPreferencesKey("sim_send_preference")
    }

    /** Defaults to "ask every time" -- the only choice that makes sense before the user has ever
     * expressed one, since silently always-picking a slot could send from the wrong SIM. */
    val sendPreference: Flow<SimSendPreference> = context.simPreferencesDataStore.data.map { prefs ->
        prefs[Keys.SEND_PREFERENCE]?.toSimSendPreferenceOrDefault(SimSendPreference.ASK) ?: SimSendPreference.ASK
    }

    suspend fun setSendPreference(preference: SimSendPreference) {
        context.simPreferencesDataStore.edit { it[Keys.SEND_PREFERENCE] = preference.name }
    }

    private fun String.toSimSendPreferenceOrDefault(default: SimSendPreference): SimSendPreference =
        runCatching { SimSendPreference.valueOf(this) }.getOrDefault(default)
}
