package text.message.sms.messaging.util

/** The first letter/digit in this string, as a proper (possibly multi-`Char`) code point rather
 * than a raw `Char` -- naive `first()` splits a leading emoji's UTF-16 surrogate pair in two
 * (emoji outside the Basic Multilingual Plane are represented as a surrogate pair, not one
 * `Char`), which rendered as a "?" tofu glyph for a display name like "😀 Sarah"
 * ("😀 Sarah") instead of skipping the emoji and using "S". Returns `null` if the string has no
 * letter/digit at all (e.g. entirely emoji/punctuation). Uppercased for scripts that have case
 * (Latin, Cyrillic, Greek...); a no-op for scripts that don't (Arabic, Thai, Devanagari,
 * Hangul, CJK...), so this is also safe to use for non-Latin native-language names. */
internal fun String.firstLetterOrDigitOrNull(): String? {
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
