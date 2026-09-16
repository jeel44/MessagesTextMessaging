package text.message.sms.messaging.ui.navigation

/**
 * Every destination in the app, with its route pattern kept next to the builder that fills it
 * in, so a route and its arguments can never drift apart.
 */
sealed class MessagingDestination(val route: String) {

    data object Splash : MessagingDestination("splash")

    data object ConversationList : MessagingDestination("conversations")

    data object NewMessage : MessagingDestination("conversations/new")

    data object Search : MessagingDestination("search")

    data object Chat : MessagingDestination("conversations/{$ARG_THREAD_ID}") {
        fun routeFor(threadId: Long): String = "conversations/$threadId"
    }

    companion object {
        const val ARG_THREAD_ID: String = "threadId"
    }
}
