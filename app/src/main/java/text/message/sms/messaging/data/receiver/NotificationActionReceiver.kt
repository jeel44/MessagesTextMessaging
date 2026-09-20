package text.message.sms.messaging.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.di.ApplicationScope
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.domain.usecase.MarkRead
import text.message.sms.messaging.domain.usecase.SendMessage
import javax.inject.Inject

/**
 * Handles the two actions [IncomingMessageNotifier] attaches to an incoming-message
 * notification. Neither needs UI -- "Mark as read" is a pair of writes, and "Reply" hands its
 * [RemoteInput] text straight to [SendMessage] -- so both run entirely here via `goAsync()`,
 * same pattern as [SmsDeliverReceiver]. Not exported: only this app's own PendingIntents (built
 * with an explicit component, never a matched intent-filter) ever target it.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var markRead: MarkRead

    @Inject
    lateinit var sendMessage: SendMessage

    @Inject
    lateinit var conversationRepository: ConversationRepository

    @Inject
    lateinit var smsProviderGateway: SmsProviderGateway

    @Inject
    lateinit var mmsProviderGateway: MmsProviderGateway

    @Inject
    lateinit var incomingMessageNotifier: IncomingMessageNotifier

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId == -1L) return
        val action = intent.action ?: return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                when (action) {
                    ACTION_MARK_READ -> markThreadRead(threadId)
                    ACTION_REPLY -> reply(threadId, intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Mirrors [text.message.sms.messaging.ui.screens.chat.ChatViewModel]'s own "opening a
     * thread marks it read" behavior, but also pushes the read state to the system provider --
     * unlike [MarkRead], which only ever touches this app's own Room cache (see its callers in
     * Home/Chat, none of which need the provider write since opening the thread there already
     * has the platform's own read state as a side effect of nothing at all, whereas this action
     * runs with the app in the background and the thread never actually opened). */
    private suspend fun markThreadRead(threadId: Long) {
        markRead(listOf(threadId))
        smsProviderGateway.markThreadRead(threadId)
        mmsProviderGateway.markThreadRead(threadId)
        incomingMessageNotifier.cancel(threadId)
    }

    private suspend fun reply(threadId: Long, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
        if (text.isNullOrEmpty()) return

        val addresses = conversationRepository.findByThreadId(threadId)
            ?.recipients
            ?.map { it.address }
            ?.toSet()
        if (addresses.isNullOrEmpty()) return

        sendMessage(SendMessage.Params(addresses = addresses, body = text, threadId = threadId))
        markThreadRead(threadId)
    }

    companion object {
        const val ACTION_MARK_READ = "text.message.sms.messaging.action.MARK_READ"
        const val ACTION_REPLY = "text.message.sms.messaging.action.REPLY"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val KEY_REPLY_TEXT = "key_reply_text"
    }
}
