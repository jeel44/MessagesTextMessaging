package text.message.sms.messaging.util

/**
 * Address helpers. Carriers echo numbers back in inconsistent formats, so every lookup key
 * is reduced to digits before it is compared or stored.
 */
object PhoneNumbers {

    private const val COMPARABLE_SUFFIX_LENGTH = 9

    /** Strips formatting, keeping a leading `+` so country codes survive. */
    fun normalize(address: String): String {
        val digits = address.filter { it.isDigit() }
        return if (address.trimStart().startsWith("+")) "+$digits" else digits
    }

    /**
     * True when two addresses almost certainly reach the same handset. Compares the trailing
     * digits so `+1 555 010 1234` and `5550101234` match.
     */
    fun areEquivalent(first: String, second: String): Boolean {
        val a = normalize(first).takeLast(COMPARABLE_SUFFIX_LENGTH)
        val b = normalize(second).takeLast(COMPARABLE_SUFFIX_LENGTH)
        return a.isNotEmpty() && a == b
    }

    /** True for short codes, which cannot receive MMS and are never real contacts. */
    fun isShortCode(address: String): Boolean = normalize(address).length in 1..6
}
