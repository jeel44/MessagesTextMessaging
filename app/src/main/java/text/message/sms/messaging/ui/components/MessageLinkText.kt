package text.message.sms.messaging.ui.components

import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import text.message.sms.messaging.ui.theme.ChatLinkColor

/**
 * Renders [text] as an [AnnotatedString] with a tappable, blue-underlined [LinkAnnotation] over
 * every URL, email address, and phone/reference number [Patterns] recognizes -- matches the
 * reference design, which underlines any digit run the phone-number matcher would also treat as
 * tappable (transaction ids, amounts, dates), not only numbers a person could actually be reached
 * at. Works over any script (Gujarati/Hindi/etc.): [Patterns] matches Latin-alphabet URLs/emails/
 * numbers embedded in the string regardless of what surrounds them, and everything else is plain
 * unstyled text.
 *
 * URLs are matched first and phone numbers/emails are skipped wherever they'd overlap an
 * already-matched range, so a URL containing a run of digits (or an `@`) is never additionally
 * split into a second, nested link.
 */
fun buildLinkedMessageText(
    text: String,
    onUrlClick: (String) -> Unit,
    onPhoneClick: (String) -> Unit,
    onEmailClick: (String) -> Unit,
    linkColor: Color = ChatLinkColor,
): AnnotatedString = buildAnnotatedString {
    append(text)

    val consumedRanges = mutableListOf<IntRange>()

    fun linkIfFree(range: IntRange, onClick: () -> Unit) {
        if (range.isEmpty()) return
        if (consumedRanges.any { it.first <= range.last && range.first <= it.last }) return
        consumedRanges += range
        addStyle(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            start = range.first,
            end = range.last + 1,
        )
        addLink(
            LinkAnnotation.Clickable(
                tag = "messageLink_${range.first}",
                linkInteractionListener = { onClick() },
            ),
            range.first,
            range.last + 1,
        )
    }

    val urlMatcher = Patterns.WEB_URL.matcher(text)
    while (urlMatcher.find()) {
        val match = urlMatcher.group() ?: continue
        linkIfFree(urlMatcher.start()..urlMatcher.end() - 1) { onUrlClick(match) }
    }

    val emailMatcher = Patterns.EMAIL_ADDRESS.matcher(text)
    while (emailMatcher.find()) {
        val match = emailMatcher.group() ?: continue
        linkIfFree(emailMatcher.start()..emailMatcher.end() - 1) { onEmailClick(match) }
    }

    val phoneMatcher = Patterns.PHONE.matcher(text)
    while (phoneMatcher.find()) {
        val match = phoneMatcher.group() ?: continue
        linkIfFree(phoneMatcher.start()..phoneMatcher.end() - 1) { onPhoneClick(match) }
    }
}
