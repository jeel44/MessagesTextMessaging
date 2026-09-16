package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import text.message.sms.messaging.domain.model.SearchResult
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.MessageRepository
import javax.inject.Inject

/** Matches a query against both thread titles and message bodies. */
class SearchConversations @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) : UseCase {

    operator fun invoke(query: String): Flow<List<SearchResult>> =
        combine(
            conversationRepository.search(query),
            messageRepository.search(query),
        ) { conversations, messages ->
            val byThread = messages.groupBy { it.threadId }
            conversations.map { conversation ->
                val matches = byThread[conversation.threadId].orEmpty()
                SearchResult(
                    conversation = conversation,
                    matchCount = matches.size,
                    topMatch = matches.maxByOrNull { it.receivedAtMillis },
                )
            }
        }
}
