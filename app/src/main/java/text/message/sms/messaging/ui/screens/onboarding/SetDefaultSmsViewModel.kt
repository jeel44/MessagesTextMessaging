package text.message.sms.messaging.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import text.message.sms.messaging.ads.AdConsentManager
import text.message.sms.messaging.ads.AdConsentState
import text.message.sms.messaging.ads.AdUnitIds
import text.message.sms.messaging.ads.InterstitialAdLoader
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
 *
 * It also owns the screen's interstitial ([AdUnitIds.SET_DEFAULT_SMS_INTERSTITIAL]): preloaded by
 * [startInterstitialPreload] as soon as the screen actually renders (behind [AdConsentManager]'s
 * gate), then shown by [showInterstitialOrAdvance] once the role and core permissions are granted.
 * Held here rather than in the composable so it survives rotation and the trips out to the system
 * role/permission dialogs.
 */
@HiltViewModel
class SetDefaultSmsViewModel @Inject constructor(
    private val syncMessages: SyncMessages,
    private val syncContacts: SyncContacts,
    @param:ApplicationContext private val context: Context,
    private val adConsentManager: AdConsentManager,
) : ViewModel() {

    private val interstitialLoader = InterstitialAdLoader(context, AdUnitIds.SET_DEFAULT_SMS_INTERSTITIAL)
    private var interstitialPreloadStarted = false
    private var advanced = false

    /** Called only when the screen actually renders -- the already-granted skip path never calls
     * it, so that path makes no ad request at all. Idempotent. Waits for consent to resolve (it
     * almost always already has, via Welcome's form), then loads or settles on unavailable. */
    fun startInterstitialPreload() {
        if (interstitialPreloadStarted) return
        interstitialPreloadStarted = true
        viewModelScope.launch {
            when (adConsentManager.state.first { it != AdConsentState.Pending }) {
                AdConsentState.Allowed -> interstitialLoader.start()
                else -> interstitialLoader.markUnavailable()
            }
        }
    }

    /** Shows the preloaded interstitial and calls [onAdvance] once it's dismissed (or fails to
     * show); if it isn't ready yet -- still loading, failed, or consent unavailable -- calls
     * [onAdvance] immediately. Never waits on an ad. [onAdvance] runs at most once per ViewModel. */
    fun showInterstitialOrAdvance(activity: Activity?, onAdvance: () -> Unit) {
        val advanceOnce = {
            if (!advanced) {
                advanced = true
                onAdvance()
            }
        }
        val shown = activity != null && interstitialLoader.showIfReady(activity, onFinished = advanceOnce)
        if (!shown) advanceOnce()
    }

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

    override fun onCleared() {
        interstitialLoader.destroy()
    }

    private companion object {
        const val TAG = "SetDefaultSmsVM"
    }
}
