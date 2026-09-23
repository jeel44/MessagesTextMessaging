package text.message.sms.messaging.ui.navigation

import text.message.sms.messaging.domain.model.CallSession
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Every destination in the app, with its route pattern kept next to the builder that fills it
 * in, so a route and its arguments can never drift apart.
 */
sealed class MessagingDestination(val route: String) {

    data object Splash : MessagingDestination("splash")

    data object Welcome : MessagingDestination("welcome")

    /** Gates onboarding on "draw over other apps" -- see
     * [text.message.sms.messaging.ui.screens.permissions.OverlayPermissionScreen]. */
    data object OverlayPermission : MessagingDestination("overlay_permission")

    data object SetDefaultSms : MessagingDestination("set_default_sms")

    data object Language : MessagingDestination("language")

    data object ConversationList : MessagingDestination("conversations")

    /** [ARG_PREFILL_TEXT] carries a Forward's prefilled body text (see [Chat]'s own
     * `initialText`) through the contact picker to whichever thread the user ends up picking --
     * [NewMessageScreen][text.message.sms.messaging.ui.screens.newmessage.NewMessageScreen]
     * itself never reads it, only the nav host, which closes over it when building the [Chat]
     * route it navigates to next. */
    data object NewMessage : MessagingDestination("conversations/new?prefill={$ARG_PREFILL_TEXT}") {
        fun routeFor(prefillText: String? = null): String {
            val base = "conversations/new"
            return if (prefillText.isNullOrEmpty()) base else "$base?prefill=${URLEncoder.encode(prefillText, "UTF-8")}"
        }

        fun decodePrefill(encoded: String): String = URLDecoder.decode(encoded, "UTF-8")
    }

    data object Search : MessagingDestination("search")

    data object Archived : MessagingDestination("conversations/archived")

    data object Settings : MessagingDestination("settings")

    /** Settings' "Language" row -- distinct from onboarding's [Language] since it behaves
     * differently (a back arrow, not a "Continue" bar; see [text.message.sms.messaging.ui.screens.onboarding.LanguageScreen]). */
    data object LanguageSettings : MessagingDestination("settings/language")

    /** [ARG_INITIAL_TEXT] seeds the composer with a Forward's prefilled text -- see
     * [ChatViewModel][text.message.sms.messaging.ui.screens.chat.ChatViewModel]'s `init`, which
     * reads it straight off `SavedStateHandle` (the same way it already reads [ARG_THREAD_ID]),
     * so [text.message.sms.messaging.ui.screens.chat.ChatScreen] itself needs no new parameter. */
    data object Chat : MessagingDestination("conversations/{$ARG_THREAD_ID}?initialText={$ARG_INITIAL_TEXT}") {
        fun routeFor(threadId: Long, initialText: String? = null): String {
            val base = "conversations/$threadId"
            return if (initialText.isNullOrEmpty()) base else "$base?initialText=${URLEncoder.encode(initialText, "UTF-8")}"
        }

        /** [ChatViewModel][text.message.sms.messaging.ui.screens.chat.ChatViewModel] reads
         * [ARG_INITIAL_TEXT] straight off its `SavedStateHandle`, same as every other argument
         * here -- Navigation Compose hands a `String` `NavType` argument through exactly as it
         * appears in the route (see [MediaViewer]'s own `decodeContentUri`), so that raw value
         * still needs this decode. */
        fun decodeInitialText(encoded: String): String = URLDecoder.decode(encoded, "UTF-8")
    }

    data object ConversationInfo : MessagingDestination("conversations/{$ARG_THREAD_ID}/info") {
        fun routeFor(threadId: Long): String = "conversations/$threadId/info"
    }

    data object MediaViewer : MessagingDestination("conversations/media/{$ARG_CONTENT_URI}") {
        fun routeFor(contentUri: String): String =
            "conversations/media/${URLEncoder.encode(contentUri, "UTF-8")}"

        fun decodeContentUri(encoded: String): String = URLDecoder.decode(encoded, "UTF-8")
    }

    /** Read-only contacts browse off the call-end screen's More tab -- see
     * [text.message.sms.messaging.ui.screens.contactslist.ContactsListScreen]. */
    data object ContactsList : MessagingDestination("contacts")

    /** Carries a full [CallSession] as primitive query args -- the same "plain primitives in the
     * route" approach [Chat] already uses for its [ARG_THREAD_ID], just spread across more of
     * them. [ARG_PHONE_NUMBER] alone is nullable/best-effort, matching [CallSession.phoneNumber]
     * itself; the rest always have a real value by the time [routeFor] builds the route, so they
     * carry a default only to satisfy Navigation Compose's argument declaration, never actually
     * relied on. Reached as the sole destination in [text.message.sms.messaging.ui.screens
     * .callend.CallEndActivity]'s own single-destination `NavHost` (its real launch trigger -- see
     * that Activity's own doc comment). */
    data object CallEnd : MessagingDestination(
        "call_end?phone={$ARG_PHONE_NUMBER}&direction={$ARG_CALL_DIRECTION}&outcome={$ARG_CALL_OUTCOME}" +
            "&startedAt={$ARG_CALL_STARTED_AT}&endedAt={$ARG_CALL_ENDED_AT}&durationMillis={$ARG_CALL_DURATION_MILLIS}",
    ) {
        fun routeFor(session: CallSession): String {
            val phonePart = session.phoneNumber
                ?.let { "&phone=${URLEncoder.encode(it, "UTF-8")}" }
                .orEmpty()
            return "call_end?direction=${session.direction.name}&outcome=${session.outcome.name}" +
                "&startedAt=${session.startedAt}&endedAt=${session.endedAt}" +
                "&durationMillis=${session.durationMillis}$phonePart"
        }
    }

    /** Generic "not built yet" stub -- see [text.message.sms.messaging.ui.screens.callend
     * .ComingSoonScreen]. [ARG_FEATURE_TITLE] is shown verbatim as the screen's title. */
    data object ComingSoon : MessagingDestination("coming_soon/{$ARG_FEATURE_TITLE}") {
        fun routeFor(featureTitle: String): String =
            "coming_soon/${URLEncoder.encode(featureTitle, "UTF-8")}"

        fun decodeFeatureTitle(encoded: String): String = URLDecoder.decode(encoded, "UTF-8")
    }

    companion object {
        const val ARG_THREAD_ID: String = "threadId"
        const val ARG_CONTENT_URI: String = "contentUri"
        const val ARG_INITIAL_TEXT: String = "initialText"
        const val ARG_PREFILL_TEXT: String = "prefill"
        const val ARG_PHONE_NUMBER: String = "phone"
        const val ARG_FEATURE_TITLE: String = "feature"
        const val ARG_CALL_DIRECTION: String = "direction"
        const val ARG_CALL_OUTCOME: String = "outcome"
        const val ARG_CALL_STARTED_AT: String = "startedAt"
        const val ARG_CALL_ENDED_AT: String = "endedAt"
        const val ARG_CALL_DURATION_MILLIS: String = "durationMillis"
    }
}
