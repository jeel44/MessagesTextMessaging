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

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "search")

/**
 * Recent search terms shown on [text.message.sms.messaging.ui.screens.search.SearchScreen] when
 * its query field is empty. Stored as a single delimited string (most recent first) rather than
 * [androidx.datastore.preferences.core.stringSetPreferencesKey] because a `Set` has no ordering,
 * and recency order is the entire point of this list.
 */
@Singleton
class SearchPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }

    val recentSearches: Flow<List<String>> =
        context.searchDataStore.data.map { it[Keys.RECENT_SEARCHES]?.toTermList() ?: emptyList() }

    /** Records [term] as the most recent search, evicting any earlier occurrence of the same
     * term (case-insensitively) and capping the list at [MAX_RECENT_SEARCHES]. */
    suspend fun addSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return

        context.searchDataStore.edit { prefs ->
            val existing = prefs[Keys.RECENT_SEARCHES]?.toTermList() ?: emptyList()
            val updated = (listOf(trimmed) + existing.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            prefs[Keys.RECENT_SEARCHES] = updated.joinToString(SEPARATOR)
        }
    }

    private fun String.toTermList(): List<String> = split(SEPARATOR).filter { it.isNotBlank() }

    private companion object {
        /** Unit separator: never appears in typed search text, so it is a safe delimiter. */
        const val SEPARATOR = "␟"
        const val MAX_RECENT_SEARCHES = 10
    }
}
