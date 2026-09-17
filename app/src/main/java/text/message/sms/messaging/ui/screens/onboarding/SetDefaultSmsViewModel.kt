package text.message.sms.messaging.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.usecase.SyncMessages
import javax.inject.Inject

/**
 * Backs [SetDefaultSmsScreen]. Its only job is [onDefaultSmsAppGranted]: the moment the screen
 * confirms [text.message.sms.messaging.service.DefaultSmsAppGuard.isDefault] just turned true, it
 * kicks off a catch-up sync so messages start appearing immediately, rather than waiting for
 * [LanguageViewModel]'s onboarding-flow sync (which may already have run, and no-opped, while the
 * role was still ungranted -- see [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll]).
 */
@HiltViewModel
class SetDefaultSmsViewModel @Inject constructor(
    private val syncMessages: SyncMessages,
) : ViewModel() {

    fun onDefaultSmsAppGranted() {
        viewModelScope.launch { syncMessages() }
    }
}
