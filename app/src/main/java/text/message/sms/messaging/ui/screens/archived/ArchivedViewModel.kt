package text.message.sms.messaging.ui.screens.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.usecase.MarkUnarchived
import javax.inject.Inject

/** Backs [ArchivedScreen]. [conversations] is a live view over
 * [ConversationRepository.observeArchived] -- unarchiving a thread here removes it from this list
 * (and puts it back in the inbox) the moment Room commits the write, no manual refresh. */
@HiltViewModel
class ArchivedViewModel @Inject constructor(
    conversationRepository: ConversationRepository,
    private val markUnarchivedUseCase: MarkUnarchived,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = conversationRepository.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal fun unarchive(threadId: Long) {
        viewModelScope.launch { markUnarchivedUseCase(listOf(threadId)) }
    }
}
