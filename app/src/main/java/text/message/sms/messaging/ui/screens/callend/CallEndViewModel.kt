package text.message.sms.messaging.ui.screens.callend

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.model.CallDirection
import text.message.sms.messaging.domain.model.CallOutcome
import text.message.sms.messaging.domain.model.CallSession
import text.message.sms.messaging.domain.model.Contact
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.ui.navigation.MessagingDestination
import javax.inject.Inject

internal enum class CallEndTab { LIST, ARCHIVE, MORE }

/** Which quick-launch chat apps are actually installed -- see [CallEndViewModel]'s doc. */
internal data class QuickLaunchApps(
    val whatsApp: Boolean,
    val whatsAppBusiness: Boolean,
    val telegram: Boolean,
) {
    val any: Boolean get() = whatsApp || whatsAppBusiness || telegram
}

internal const val PACKAGE_WHATSAPP = "com.whatsapp"
internal const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
internal const val PACKAGE_TELEGRAM = "org.telegram.messenger"

/**
 * Backs [CallEndScreen].
 *
 * [callSession] is rebuilt from [MessagingDestination.CallEnd]'s route args -- the same
 * "primitives through the route" approach [text.message.sms.messaging.ui.screens.chat
 * .ChatViewModel] already uses for its thread id. [text.message.sms.messaging.service
 * .CallEndTriggerService], which builds the route, is responsible for putting real values in it;
 * this always trusts it as-is.
 */
@HiltViewModel
class CallEndViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    internal val callSession: CallSession = CallSession(
        phoneNumber = savedStateHandle.get<String>(MessagingDestination.ARG_PHONE_NUMBER),
        direction = savedStateHandle.get<String>(MessagingDestination.ARG_CALL_DIRECTION)
            ?.let { runCatching { CallDirection.valueOf(it) }.getOrNull() }
            ?: CallDirection.INCOMING,
        outcome = savedStateHandle.get<String>(MessagingDestination.ARG_CALL_OUTCOME)
            ?.let { runCatching { CallOutcome.valueOf(it) }.getOrNull() }
            ?: CallOutcome.MISSED,
        startedAt = savedStateHandle.get<Long>(MessagingDestination.ARG_CALL_STARTED_AT) ?: 0L,
        endedAt = savedStateHandle.get<Long>(MessagingDestination.ARG_CALL_ENDED_AT) ?: 0L,
        durationMillis = savedStateHandle.get<Long>(MessagingDestination.ARG_CALL_DURATION_MILLIS) ?: 0L,
    )

    private val _contact = MutableStateFlow<Contact?>(null)
    internal val contact: StateFlow<Contact?> = _contact.asStateFlow()

    /** Checked once, via [PackageManager], not observed -- an installed chat app doesn't
     * uninstall itself out from under an already-open screen, so there's nothing to react to. */
    internal val quickLaunchApps: QuickLaunchApps = QuickLaunchApps(
        whatsApp = isPackageInstalled(PACKAGE_WHATSAPP),
        whatsAppBusiness = isPackageInstalled(PACKAGE_WHATSAPP_BUSINESS),
        telegram = isPackageInstalled(PACKAGE_TELEGRAM),
    )

    private val _selectedTab = MutableStateFlow(CallEndTab.LIST)
    internal val selectedTab: StateFlow<CallEndTab> = _selectedTab.asStateFlow()

    /** Real inbox/archive data, same queries [text.message.sms.messaging.ui.screens
     * .conversationlist.ConversationListViewModel]/[text.message.sms.messaging.ui.screens.archived
     * .ArchivedScreen] read from -- collected directly here rather than through those heavier
     * ViewModels, which also own selection mode, swipe-action prefs and sync triggers this embedded
     * tab body has no use for. */
    internal val inboxConversations: StateFlow<List<Conversation>> = conversationRepository.observeInbox()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal val archivedConversations: StateFlow<List<Conversation>> = conversationRepository.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val phoneNumber = callSession.phoneNumber
        if (phoneNumber != null) {
            viewModelScope.launch { _contact.value = contactRepository.findByAddress(phoneNumber) }
        }
    }

    internal fun onTabSelected(tab: CallEndTab) {
        _selectedTab.value = tab
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
