package text.message.sms.messaging.domain.repository

/** What a scheduled send checks about the device right before it goes out. */
interface SendEnvironment {

    /** Whether this app holds the default-SMS role -- without it nothing can be sent. */
    fun isDefaultSmsApp(): Boolean

    /** Subscription ids of the SIMs active right now (empty without READ_PHONE_STATE). */
    suspend fun activeSubscriptionIds(): List<Int>
}

/** Posts the user-facing notice for a scheduled message that didn't go out. Never for a success. */
interface ScheduledSendNotifier {

    /** The send was attempted (or couldn't be) and failed. */
    fun notifyFailed(messageId: Long, threadId: Long)

    /** The send time passed by more than the overdue limit, so it wasn't sent at all. */
    fun notifyMissed(messageId: Long, threadId: Long)
}
