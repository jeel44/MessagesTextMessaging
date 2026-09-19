package text.message.sms.messaging.util

import text.message.sms.messaging.domain.model.Conversation

/**
 * Single source of truth for "what kind of sender is this thread" -- backs Home's PERSONAL /
 * TRANSACTIONS / OTP filter chips
 * ([text.message.sms.messaging.ui.screens.conversationlist.ConversationListViewModel]) and Chat's
 * personal/non-personal mode ([isPersonalChat]), so the two screens can never disagree about the
 * same conversation. Each function is an independent membership test rather than a
 * mutually-exclusive bucket -- e.g. a resolved contact's message that happens to contain an OTP
 * keyword can match both [isPersonal] and [isOtp] -- since there's no per-message sender
 * classification in the data model to assign exactly one category, only heuristics run over
 * [Conversation.recipients]/[Conversation.snippet].
 */

/** Conservative keyword set for [isTransaction] -- there's no reliable sender-ID signal available
 * (a business's alphanumeric SMS sender ID and a plain phone number share the same
 * [text.message.sms.messaging.domain.model.Recipient.address] field, with nothing to tell them
 * apart), so this leans entirely on wording banks/payment processors actually use. */
private val TRANSACTION_KEYWORD_REGEX = Regex(
    "debited|credited|withdrawn|a/c|account balance|available balance|upi|txn|transaction|" +
        "emi|payment (?:of|received|successful)|amount (?:debited|credited|paid)|bill (?:due|paid)|" +
        "spent (?:on|at)|purchase of",
    RegexOption.IGNORE_CASE,
)

/** A saved contact makes a thread unambiguously personal; a group thread is treated the same way
 * even if not every member resolved to a contact, since a group text is inherently a
 * person-to-person conversation rather than a business one. */
fun Conversation.isPersonal(): Boolean = isGroup || recipients.any { it.contact != null }

/** [OtpDetector] already runs per-row for the inbox's quick-copy affordance -- reused here rather
 * than duplicated so the chip, the filter, and Chat's OTP copy button can never disagree about
 * which threads/messages count. Not restricted to unresolved senders: a saved contact forwarding a
 * code is rare but still worth surfacing under this filter. */
fun Conversation.isOtp(): Boolean = OtpDetector.extractCode(snippet) != null

/** Excludes [isOtp] matches (a verification text mentioning "your account" shouldn't double as a
 * transaction) and resolved contacts (a real person's message matching these words by coincidence
 * isn't a bank alert) -- see [TRANSACTION_KEYWORD_REGEX]'s doc comment for why keywords are the
 * only signal available at all. */
fun Conversation.isTransaction(): Boolean =
    !isOtp() && recipients.none { it.contact != null } && TRANSACTION_KEYWORD_REGEX.containsMatchIn(snippet)

/**
 * Whether [text.message.sms.messaging.ui.screens.chat.ChatScreen] should render this thread as a
 * personal chat (full composer, call button, blue sent bubbles) rather than a read-only
 * non-personal thread (business/short-code/OTP/transactional sender -- security notice instead of
 * a composer). Broader than [isPersonal]: an *unsaved* contact's plain phone number still counts as
 * personal here, since most 1:1 texting happens with people who were never saved as a contact --
 * Home's chip only needs "definitely a person" (saved contact) to build a useful filter, but Chat
 * needs the opposite call, "definitely NOT a person", before it can safely hide the composer.
 */
fun Conversation.isPersonalChat(): Boolean {
    if (isGroup) return true
    if (isOtp() || isTransaction()) return false
    val recipient = recipients.firstOrNull() ?: return true
    if (recipient.contact != null) return true
    return PhoneNumbers.looksLikePhoneNumber(recipient.address) && !PhoneNumbers.isShortCode(recipient.address)
}
