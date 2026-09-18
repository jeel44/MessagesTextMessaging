package text.message.sms.messaging.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.usecase.SyncContacts
import javax.inject.Inject

/**
 * Backs [WelcomeScreen]. Its only job is [onContactsPermissionGranted]: the moment the screen's
 * permission dialog result confirms READ_CONTACTS was granted, it kicks off a contacts sync right
 * away -- mirroring [SetDefaultSmsViewModel.onDefaultSmsAppGranted] for the default-SMS-app role.
 *
 * This can't be left to [text.message.sms.messaging.ui.screens.conversationlist.ConversationListViewModel]'s
 * own resume-based catch-up check alone: that check compares the current permission state against
 * the state the ViewModel was *constructed* with, but [text.message.sms.messaging.ui.screens.conversationlist.ConversationListScreen]
 * is only ever reached (and its ViewModel only ever constructed) after this screen already granted
 * the permission earlier in the same onboarding flow -- so by construction time the "before" and
 * "after" states are identical (both already granted) and that check's false-to-true transition
 * never fires. Only a direct call from the exact moment of the grant, same as the default-SMS-app
 * role, actually starts the sync.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val syncContacts: SyncContacts,
) : ViewModel() {

    fun onContactsPermissionGranted() {
        viewModelScope.launch { syncContacts() }
    }
}
