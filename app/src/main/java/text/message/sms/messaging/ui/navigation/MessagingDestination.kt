package text.message.sms.messaging.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Every destination in the app, with its route pattern kept next to the builder that fills it
 * in, so a route and its arguments can never drift apart.
 */
sealed class MessagingDestination(val route: String) {

    data object Splash : MessagingDestination("splash")

    data object Welcome : MessagingDestination("welcome")

    data object SetDefaultSms : MessagingDestination("set_default_sms")

    data object Language : MessagingDestination("language")

    data object ConversationList : MessagingDestination("conversations")

    data object NewMessage : MessagingDestination("conversations/new")

    data object Search : MessagingDestination("search")

    data object Archived : MessagingDestination("conversations/archived")

    data object Settings : MessagingDestination("settings")

    /** Settings' "Language" row -- distinct from onboarding's [Language] since it behaves
     * differently (a back arrow, not a "Continue" bar; see [text.message.sms.messaging.ui.screens.onboarding.LanguageScreen]). */
    data object LanguageSettings : MessagingDestination("settings/language")

    data object Chat : MessagingDestination("conversations/{$ARG_THREAD_ID}") {
        fun routeFor(threadId: Long): String = "conversations/$threadId"
    }

    data object ConversationInfo : MessagingDestination("conversations/{$ARG_THREAD_ID}/info") {
        fun routeFor(threadId: Long): String = "conversations/$threadId/info"
    }

    data object MediaViewer : MessagingDestination("conversations/media/{$ARG_CONTENT_URI}") {
        fun routeFor(contentUri: String): String =
            "conversations/media/${URLEncoder.encode(contentUri, "UTF-8")}"

        fun decodeContentUri(encoded: String): String = URLDecoder.decode(encoded, "UTF-8")
    }

    companion object {
        const val ARG_THREAD_ID: String = "threadId"
        const val ARG_CONTENT_URI: String = "contentUri"
    }
}
