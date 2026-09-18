package text.message.sms.messaging.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup")

/** When the last successful [text.message.sms.messaging.domain.repository.BackupRepository]
 * export ran, for Settings' "Last backup" summary. */
@Singleton
class BackupPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    val lastBackupAtMillis: Flow<Long?> =
        context.backupDataStore.data.map { it[Keys.LAST_BACKUP_AT] }

    suspend fun setLastBackupAtMillis(timestampMillis: Long) {
        context.backupDataStore.edit { it[Keys.LAST_BACKUP_AT] = timestampMillis }
    }
}
