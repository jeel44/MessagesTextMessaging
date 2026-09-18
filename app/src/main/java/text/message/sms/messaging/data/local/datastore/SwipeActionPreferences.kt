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

private val Context.swipeActionDataStore: DataStore<Preferences> by preferencesDataStore(name = "swipe_actions")

/** What a conversation-list swipe does, matching QKSMS's configurable swipe actions. */
enum class SwipeAction { NONE, ARCHIVE, DELETE, TOGGLE_READ, CALL }

/** The action bound to each swipe direction. Archive-right/delete-left are just the defaults --
 * both are user-configurable from Settings' "Swipe actions" row. */
data class SwipeActionPreference(
    val startToEnd: SwipeAction = SwipeAction.ARCHIVE,
    val endToStart: SwipeAction = SwipeAction.DELETE,
)

/** Persists the Settings "Swipe actions" row's choice; read back by the conversation list to
 * decide what each swipe direction does. */
@Singleton
class SwipeActionPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val START_TO_END = stringPreferencesKey("swipe_start_to_end")
        val END_TO_START = stringPreferencesKey("swipe_end_to_start")
    }

    val swipeActionPreference: Flow<SwipeActionPreference> = context.swipeActionDataStore.data.map { prefs ->
        SwipeActionPreference(
            startToEnd = prefs[Keys.START_TO_END]?.toSwipeActionOrDefault(SwipeAction.ARCHIVE) ?: SwipeAction.ARCHIVE,
            endToStart = prefs[Keys.END_TO_START]?.toSwipeActionOrDefault(SwipeAction.DELETE) ?: SwipeAction.DELETE,
        )
    }

    suspend fun setStartToEnd(action: SwipeAction) {
        context.swipeActionDataStore.edit { it[Keys.START_TO_END] = action.name }
    }

    suspend fun setEndToStart(action: SwipeAction) {
        context.swipeActionDataStore.edit { it[Keys.END_TO_START] = action.name }
    }

    private fun String.toSwipeActionOrDefault(default: SwipeAction): SwipeAction =
        runCatching { SwipeAction.valueOf(this) }.getOrDefault(default)
}
