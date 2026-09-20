package text.message.sms.messaging.domain.model

import androidx.compose.runtime.Immutable

/** An entry resolved from the system contacts provider. */
@Immutable
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
    val numbers: List<String> = emptyList(),
    val isStarred: Boolean = false,
) {
    /** Up to two letters/digits used when no contact photo is available. */
    val initials: String
        get() = displayName
            .split(' ')
            .filter { it.isNotBlank() }
            .mapNotNull { it.firstLetterOrDigitOrNull() }
            .take(2)
            .joinToString(separator = "")
}

/** The first letter/digit in this string, as a proper (possibly multi-`Char`) code point rather
 * than a raw `Char` -- naive `first()` splits a leading emoji's UTF-16 surrogate pair in two
 * (emoji outside the Basic Multilingual Plane are represented as a surrogate pair, not one
 * `Char`), which rendered as a "?" tofu glyph for a display name like "😀 Sarah"
 * ("😀 Sarah") instead of skipping the emoji and using "S". Returns `null` if the word has no
 * letter/digit at all (e.g. a word that's entirely emoji/punctuation). */
private fun String.firstLetterOrDigitOrNull(): String? {
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        if (Character.isLetterOrDigit(codePoint)) {
            return String(Character.toChars(codePoint)).uppercase()
        }
        offset += Character.charCount(codePoint)
    }
    return null
}
