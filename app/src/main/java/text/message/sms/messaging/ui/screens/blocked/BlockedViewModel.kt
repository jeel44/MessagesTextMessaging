package text.message.sms.messaging.ui.screens.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.analytics.Analytics
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.ContactRepository
import text.message.sms.messaging.domain.usecase.ReblockNumber
import text.message.sms.messaging.domain.usecase.UnblockNumber
import text.message.sms.messaging.domain.usecase.UnblockedNumber
import javax.inject.Inject

/** One row of [BlockedScreen]: the number, plus its contact's name when there is one. */
internal data class BlockedNumberRow(
    val number: BlockedNumber,
    val contactName: String?,
)

/**
 * Backs [BlockedScreen]. [rows] is a live view over [BlockedNumberRepository.observeAll] -- an
 * unblocked number drops out the moment Room commits the delete, and an undo puts it back the same
 * way. Re-evaluated whenever the contact cache changes too, so a name saved later shows up.
 */
@HiltViewModel
class BlockedViewModel @Inject constructor(
    blockedNumberRepository: BlockedNumberRepository,
    contactRepository: ContactRepository,
    private val unblockNumber: UnblockNumber,
    private val reblockNumber: ReblockNumber,
) : ViewModel() {

    /** Null until the first read lands, so the empty state doesn't flash before the list. */
    internal val rows: StateFlow<List<BlockedNumberRow>?> = combine(
        blockedNumberRepository.observeAll(),
        contactRepository.contactsState,
    ) { numbers, _ ->
        numbers.map { BlockedNumberRow(it, contactRepository.findByAddress(it.address)?.displayName) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _unblocked = MutableSharedFlow<UnblockedNumber>(extraBufferCapacity = 8)

    /** One per unblock, for [BlockedScreen]'s undo snackbar. */
    internal val unblocked: SharedFlow<UnblockedNumber> = _unblocked.asSharedFlow()

    internal fun unblock(number: BlockedNumber) {
        viewModelScope.launch {
            val unblocked = unblockNumber(number)
            Analytics.numberUnblocked()
            _unblocked.emit(unblocked)
        }
    }

    internal fun undoUnblock(unblocked: UnblockedNumber) {
        viewModelScope.launch { reblockNumber(unblocked) }
    }
}
