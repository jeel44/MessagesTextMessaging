package text.message.sms.messaging.util

/**
 * Pulls a one-time-password/verification code out of an SMS body, for the inbox's quick-copy
 * chip. Deliberately conservative -- missing a real OTP is far cheaper than offering to copy an
 * amount, phone number, or date as if it were one, so [extractCode] requires an OTP-ish keyword
 * *and* picks only the digit run closest to it, rather than grabbing the first 4-8 digit number
 * anywhere in the message.
 */
object OtpDetector {

    /** Compound phrases only -- no bare "code", which would also match "promo code" / "zip code"
     * / "pin code" messages that have nothing to do with a login. */
    private val KEYWORD_REGEX = Regex(
        "otp|one[- ]?time password|verification code|verification pin|security code|" +
            "passcode|pass code|auth(?:entication)? code|login code|access code|your code",
        RegexOption.IGNORE_CASE,
    )

    private val DIGIT_RUN_REGEX = Regex("\\b\\d{4,8}\\b")

    /** How far (in characters) a candidate digit run may sit from the nearest keyword match and
     * still be considered -- keeps a keyword-bearing but otherwise unrelated message (e.g. one
     * that also happens to mention an amount or reference number far from the word "OTP") from
     * matching on the wrong number. */
    private const val MAX_KEYWORD_DISTANCE = 40

    /** Short lead-ins that mark a nearby number as an identifier rather than a code -- order
     * numbers, account numbers, reference/tracking numbers -- which are exactly the kind of
     * digit run that otherwise satisfies the length check and can sit close to an OTP keyword in
     * the same message (e.g. "...OTP... for order ref 12345678"). */
    private val REFERENCE_PRECEDER_REGEX = Regex(
        "(?i)(order|ref(?:erence)?|id|awb|tracking|acc(?:ount)?|a/c|card(?:\\s*no\\.?)?|loan|txn(?:\\s*id)?)\\s*[:#]?\\s*$",
    )

    private const val REFERENCE_WINDOW = 20

    /** Returns the extracted code, or null if nothing in [text] confidently looks like one. */
    fun extractCode(text: String): String? {
        val keywordMatch = KEYWORD_REGEX.find(text) ?: return null
        val keywordRange = keywordMatch.range

        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in DIGIT_RUN_REGEX.findAll(text)) {
            if (looksLikeNonOtpNumber(text, candidate.range)) continue
            val distance = when {
                candidate.range.last < keywordRange.first -> keywordRange.first - candidate.range.last
                candidate.range.first > keywordRange.last -> candidate.range.first - keywordRange.last
                else -> 0
            }
            if (distance <= MAX_KEYWORD_DISTANCE && distance < bestDistance) {
                best = candidate.value
                bestDistance = distance
            }
        }
        return best
    }

    private fun looksLikeNonOtpNumber(text: String, range: IntRange): Boolean {
        val before = text.getOrNull(range.first - 1)
        val after = text.getOrNull(range.last + 1)

        // Adjacent to a date/range separator, e.g. the "2026" in "15-09-2026" or "12/2026".
        if (before == '-' || before == '/' || after == '-' || after == '/') return true

        // A phone number prefix, or the fractional part of a decimal amount.
        if (before == '+') return true
        if (before == '.' && text.getOrNull(range.first - 2)?.isDigit() == true) return true

        val windowStart = maxOf(0, range.first - REFERENCE_WINDOW)
        val window = text.substring(windowStart, range.first)
        if (REFERENCE_PRECEDER_REGEX.containsMatchIn(window)) return true

        // Currency-prefixed amounts: "Rs.5000", "Rs 5000", "INR5000", "₹5000", "$5000".
        if (window.contains('$') || window.contains('₹')) return true
        if (Regex("(?i)(rs\\.?|inr)\\s*$").containsMatchIn(window)) return true

        return false
    }
}
