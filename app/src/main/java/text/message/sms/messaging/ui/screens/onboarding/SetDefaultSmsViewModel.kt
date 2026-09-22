package text.message.sms.messaging.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.usecase.SyncContacts
import text.message.sms.messaging.domain.usecase.SyncMessages
import javax.inject.Inject

/**
 * Backs [SetDefaultSmsScreen]. Its only job is [onDefaultSmsAppGranted]: the moment the screen
 * confirms [text.message.sms.messaging.service.DefaultSmsAppGuard.isDefault] just turned true, it
 * kicks off a catch-up sync so messages start appearing immediately, rather than waiting for
 * [LanguageViewModel]'s onboarding-flow sync (which may already have run, and no-opped, while the
 * role was still ungranted -- see [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll]).
 *
 * It also fires a matching contacts catch-up sync here, gated on [Manifest.permission.READ_CONTACTS]
 * being granted: on API 29+, the SMS role bundles that permission (see AOSP's `role_sms` entry in
 * `roles.xml`), so it's typically already granted the instant this fires -- without this, nothing
 * resyncs contacts until [text.message.sms.messaging.ui.screens.newmessage.NewMessageViewModel
 * .onContactsPermissionGranted] happens to run as a side effect of the user opening New Message,
 * since [text.message.sms.messaging.MessagingApplication]'s own launch-time check ran before this
 * grant existed, and [text.message.sms.messaging.ui.screens.conversationlist.ConversationListViewModel
 * .refreshContactsPermissionStatus]'s false-to-true edge detection never sees an edge here -- its
 * own permission seed, read when that screen is first navigated to (i.e. after this already ran),
 * is already `true`. On API 26-28, the role doesn't bundle contacts, so this permission check
 * simply no-ops and New Message's existing first-use request is left to cover it, unchanged.
 */
@HiltViewModel
class SetDefaultSmsViewModel @Inject constructor(
    private val syncMessages: SyncMessages,
    private val syncContacts: SyncContacts,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    fun onDefaultSmsAppGranted() {
        Log.d(TAG, "onDefaultSmsAppGranted: entry")
        // Independent launches, not sequential awaits in one coroutine: syncMessages() and
        // syncContacts() touch disjoint tables (messages/conversations vs. contacts/
        // contact_numbers) with no ordering dependency between them, so there's no reason for a
        // slow first-run SMS/MMS backfill to hold up the contacts sync behind it.
        viewModelScope.launch {
            Log.d(TAG, "onDefaultSmsAppGranted: launching syncMessages()")
            syncMessages()
            Log.d(TAG, "onDefaultSmsAppGranted: syncMessages() returned")
        }
        viewModelScope.launch {
            val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "onDefaultSmsAppGranted: READ_CONTACTS granted=$contactsGranted")
            if (contactsGranted) {
                Log.d(TAG, "onDefaultSmsAppGranted: calling syncContacts()")
                syncContacts()
                Log.d(TAG, "onDefaultSmsAppGranted: syncContacts() returned")
            }
        }
    }

    private companion object {
        const val TAG = "SetDefaultSmsVM"
    }
}
