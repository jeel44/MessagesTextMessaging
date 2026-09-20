package text.message.sms.messaging.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import text.message.sms.messaging.MainActivity
import text.message.sms.messaging.R
import text.message.sms.messaging.data.receiver.NotificationActionReceiver
import text.message.sms.messaging.domain.model.Conversation
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.repository.ConversationRepository
import text.message.sms.messaging.domain.repository.IncomingMessageNotifier
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real, system-notification [IncomingMessageNotifier]: posts on the
 * [NotificationChannels.INCOMING_MESSAGES] channel -- and, if that thread already has one posted,
 * stacks onto it via [NotificationCompat.MessagingStyle] instead of replacing it, the same way any
 * chat app's notification grows across several messages before it's opened or dismissed.
 *
 * [notify] is called only from [text.message.sms.messaging.domain.usecase.ReceiveSms]/
 * [text.message.sms.messaging.domain.usecase.ReceiveMms] after a successful, non-duplicate
 * insert -- never from a sync pass, so a catch-up sync (whether from
 * [text.message.sms.messaging.data.local.provider.ProviderChangeObserver] or app launch) can
 * never itself generate a notification, only the two receivers that observed a message actually
 * arrive live.
 */
@Singleton
class DefaultIncomingMessageNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val conversationRepository: ConversationRepository,
    private val activeThreadTracker: ActiveThreadTracker,
) : IncomingMessageNotifier {

    override suspend fun notify(message: Message) {
        // A notification is never allowed to crash the app or block message insertion -- the
        // message is already committed to Room by the time this runs (see ReceiveSms/ReceiveMms),
        // so the worst a failure here can do is silently skip the notification. Regression: a
        // MessagingStyle Person built with an empty name throws IllegalArgumentException and
        // killed the process on every incoming SMS -- see safeName's doc.
        try {
            if (!canPostNotifications()) return
            if (activeThreadTracker.isActive(message.threadId)) return

            val conversation = conversationRepository.findByThreadId(message.threadId) ?: return
            val senderName = safeName(conversation.senderDisplayName(message.address), message.address)

            val style = restoreOrCreateStyle(message.threadId)
                .setGroupConversation(conversation.isGroup)
                .addMessage(
                    message.displayText(),
                    message.receivedAtMillis,
                    Person.Builder().setName(senderName).build(),
                )
            if (conversation.isGroup) style.conversationTitle = conversation.title

            val notification = NotificationCompat.Builder(context, NotificationChannels.INCOMING_MESSAGES)
                .setSmallIcon(R.drawable.ic_notifications)
                .setStyle(style)
                .setContentIntent(openChatPendingIntent(message.threadId))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setWhen(message.receivedAtMillis)
                .addAction(markAsReadAction(message.threadId))
                .addAction(replyAction(message.threadId))
                .build()

            notificationManager.notify(notificationId(message.threadId), notification)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to post incoming-message notification for thread ${message.threadId}", error)
        }
    }

    /** Called when a thread's chat screen is opened/resumed, or its "Mark as read" action is
     * tapped -- either way there's nothing left to notify about. */
    override fun cancel(threadId: Long) {
        notificationManager.cancel(notificationId(threadId))
    }

    /** Reads back this thread's own still-posted notification (if any) via
     * [NotificationManager.getActiveNotifications] and extracts its [NotificationCompat
     * .MessagingStyle] so [notify] can append to it -- the standard way to stack a chat
     * notification across multiple messages without this class keeping its own duplicate copy of
     * notification history. */
    private fun restoreOrCreateStyle(threadId: Long): NotificationCompat.MessagingStyle {
        val activeNotification = context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.firstOrNull { it.id == notificationId(threadId) }
            ?.notification
        val restored = activeNotification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
        // The platform requires this Person's name to be non-empty (IllegalArgumentException,
        // crashing the process, otherwise) -- a plain "" here is what caused the crash this fixes.
        // A constant, never anything derived from a value that could itself be blank.
        return restored
            ?: NotificationCompat.MessagingStyle(
                Person.Builder().setName(context.getString(R.string.notification_self_name)).build(),
            )
    }

    private fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openChatPendingIntent(threadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
        return PendingIntent.getActivity(
            context,
            requestCode(threadId, REQUEST_CODE_OFFSET_OPEN),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun markAsReadAction(threadId: Long): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_MARK_READ)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(threadId, REQUEST_CODE_OFFSET_MARK_READ),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notifications,
            context.getString(R.string.notification_action_mark_read),
            pendingIntent,
        ).build()
    }

    /** [SendMessage][text.message.sms.messaging.domain.usecase.SendMessage] needs no UI, so this
     * whole action -- reply text in, message sent -- runs entirely in
     * [NotificationActionReceiver], never opening the app. The PendingIntent carrying a
     * [RemoteInput] must be mutable (system requirement for inline reply since Android 12),
     * unlike every other action/content intent here. */
    private fun replyAction(threadId: Long): NotificationCompat.Action {
        val label = context.getString(R.string.notification_action_reply)
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(label)
            .build()
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_REPLY)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(threadId, REQUEST_CODE_OFFSET_REPLY),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notifications, label, pendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun notificationId(threadId: Long): Int = threadId.hashCode()

    /** [offset] keeps the open/mark-read/reply PendingIntents for the same thread from aliasing
     * each other -- their [Intent]s already differ by action and target component, but distinct
     * request codes make the intent no two of them could ever be mistaken for equal. */
    private fun requestCode(threadId: Long, offset: Int): Int = threadId.hashCode() + offset

    /** The sender's own name/number, resolved against this conversation's recipients by address
     * -- falls back to the conversation's title (every recipient's name, comma-separated) only
     * when [address] can't be matched to one of them (should not happen in practice; every
     * incoming message carries the address it came from). */
    private fun Conversation.senderDisplayName(address: String?): String {
        if (address == null) return title
        val match = recipients.firstOrNull { PhoneNumbers.areEquivalent(it.address, address) }
        return match?.displayName ?: title
    }

    /** Guarantees a non-blank [Person] name -- required by the platform, which throws
     * `IllegalArgumentException` (crashing the process) for an empty one. [name] is a resolved
     * contact/conversation display name that can legitimately come back blank (e.g. a
     * zero-recipient conversation, or a contact saved with no name and a blank address); this
     * falls back to [address], then to a fixed "Unknown" string if that's blank too. */
    private fun safeName(name: String, address: String?): String =
        name.ifBlank { address?.takeIf { it.isNotBlank() } }
            ?: context.getString(R.string.notification_unknown_sender)

    private fun Message.displayText(): String = body.ifBlank {
        if (channel == MessageChannel.MMS && attachments.any { it.isImage }) {
            context.getString(R.string.notification_mms_photo_placeholder)
        } else {
            context.getString(R.string.notification_mms_placeholder)
        }
    }

    private companion object {
        const val TAG = "IncomingMessageNotifier"
        const val REQUEST_CODE_OFFSET_OPEN = 0
        const val REQUEST_CODE_OFFSET_MARK_READ = 1
        const val REQUEST_CODE_OFFSET_REPLY = 2
    }
}
