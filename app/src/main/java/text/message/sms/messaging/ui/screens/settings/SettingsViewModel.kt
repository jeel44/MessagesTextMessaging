package text.message.sms.messaging.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.datastore.BackupPreferences
import text.message.sms.messaging.data.local.datastore.SwipeAction
import text.message.sms.messaging.data.local.datastore.SwipeActionPreference
import text.message.sms.messaging.data.local.datastore.SwipeActionPreferences
import text.message.sms.messaging.data.local.datastore.ThemeMode
import text.message.sms.messaging.data.local.datastore.ThemePreference
import text.message.sms.messaging.data.local.datastore.ThemePreferences
import text.message.sms.messaging.domain.model.BackupResult
import text.message.sms.messaging.domain.usecase.ExportBackup
import text.message.sms.messaging.domain.usecase.ImportBackup
import javax.inject.Inject

/** Whether a backup export/import is in flight, so [SettingsScreen] can show progress and the
 * two rows can't be double-launched while one is already running. */
internal enum class BackupOperation { IDLE, EXPORTING, IMPORTING }

/** A one-shot backup outcome to surface as a toast; consumed via
 * [SettingsViewModel.consumeBackupEvent] so recomposition (e.g. a config change) never re-shows a
 * stale result. */
internal sealed interface BackupEvent {
    data class ExportSucceeded(val messageCount: Int) : BackupEvent
    data class ImportSucceeded(val messageCount: Int) : BackupEvent
    data object NotDefaultSmsApp : BackupEvent
    data object DestinationUnavailable : BackupEvent
    data class Error(val message: String) : BackupEvent
}

/** Backs the Backup & Sync rows on [SettingsScreen]: kicks off [ExportBackup]/[ImportBackup]
 * against a Uri the screen already obtained from the system file picker, and tracks
 * [BackupPreferences.lastBackupAtMillis] for the "Backup now" row's summary. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportBackupUseCase: ExportBackup,
    private val importBackupUseCase: ImportBackup,
    private val backupPreferences: BackupPreferences,
    private val themePreferences: ThemePreferences,
    private val swipeActionPreferences: SwipeActionPreferences,
) : ViewModel() {

    private val _operation = MutableStateFlow(BackupOperation.IDLE)
    internal val operation: StateFlow<BackupOperation> = _operation.asStateFlow()

    private val _event = MutableStateFlow<BackupEvent?>(null)
    internal val event: StateFlow<BackupEvent?> = _event.asStateFlow()

    internal val lastBackupAtMillis: StateFlow<Long?> = backupPreferences.lastBackupAtMillis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    internal val themePreference: StateFlow<ThemePreference> = themePreferences.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference())

    internal fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    internal fun setAccentColor(color: Color?) {
        viewModelScope.launch { themePreferences.setAccentColor(color) }
    }

    internal val swipeActionPreference: StateFlow<SwipeActionPreference> = swipeActionPreferences.swipeActionPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeActionPreference())

    internal fun setSwipeStartToEnd(action: SwipeAction) {
        viewModelScope.launch { swipeActionPreferences.setStartToEnd(action) }
    }

    internal fun setSwipeEndToStart(action: SwipeAction) {
        viewModelScope.launch { swipeActionPreferences.setEndToStart(action) }
    }

    internal fun exportBackup(destinationUri: String) {
        if (_operation.value != BackupOperation.IDLE) return
        _operation.value = BackupOperation.EXPORTING
        viewModelScope.launch {
            when (val result = exportBackupUseCase(destinationUri)) {
                is BackupResult.Success -> {
                    backupPreferences.setLastBackupAtMillis(System.currentTimeMillis())
                    _event.value = BackupEvent.ExportSucceeded(result.messageCount)
                }
                is BackupResult.Failure -> _event.value = result.toEvent()
            }
            _operation.value = BackupOperation.IDLE
        }
    }

    internal fun importBackup(sourceUri: String) {
        if (_operation.value != BackupOperation.IDLE) return
        _operation.value = BackupOperation.IMPORTING
        viewModelScope.launch {
            when (val result = importBackupUseCase(sourceUri)) {
                is BackupResult.Success -> _event.value = BackupEvent.ImportSucceeded(result.messageCount)
                is BackupResult.Failure -> _event.value = result.toEvent()
            }
            _operation.value = BackupOperation.IDLE
        }
    }

    internal fun consumeBackupEvent() {
        _event.value = null
    }

    private fun BackupResult.Failure.toEvent(): BackupEvent = when (this) {
        BackupResult.Failure.NotDefaultSmsApp -> BackupEvent.NotDefaultSmsApp
        BackupResult.Failure.DestinationUnavailable -> BackupEvent.DestinationUnavailable
        is BackupResult.Failure.Error -> BackupEvent.Error(message)
    }
}
