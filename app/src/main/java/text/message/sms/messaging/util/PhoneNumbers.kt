package text.message.sms.messaging.util

/**
 * Address helpers. Carriers echo numbers back in inconsistent formats, so every lookup key
 * is reduced to digits before it is compared or stored.
 */
object PhoneNumbers {

    private const val COMPARABLE_SUFFIX_LENGTH = 9

    /** Strips formatting, keeping a leading `+` so country codes survive. Two representations of
     * the exact same handset can still normalize to *different* strings here -- e.g. a contact
     * saved without a country code ("5550101234") versus the same number as the system Telephony
     * provider reports it ("+15550101234") -- so this is only safe for exact-match use (e.g.
     * de-duping numbers already known to belong to one contact); matching one address against
     * another from a different source should use [comparableSuffix]/[areEquivalent] instead. */
    fun normalize(address: String): String {
        val digits = address.filter { it.isDigit() }
        return if (address.trimStart().startsWith("+")) "+$digits" else digits
    }

    /** The trailing digits that decide whether two addresses "almost certainly reach the same
     * handset" -- see [areEquivalent]. Exposed as a lookup key in its own right (not just a
     * comparison) so a caller matching one address against a *set* of others from a different
     * source (e.g. a message's address against a contact's saved numbers) can key a map by this
     * instead of [normalize], which a country-code mismatch between the two sources would miss
     * entirely on an exact-match lookup. */
    fun comparableSuffix(address: String): String = normalize(address).takeLast(COMPARABLE_SUFFIX_LENGTH)

    /**
     * True when two addresses almost certainly reach the same handset. Compares the trailing
     * digits so `+1 555 010 1234` and `5550101234` match.
     */
    fun areEquivalent(first: String, second: String): Boolean {
        val a = comparableSuffix(first)
        val b = comparableSuffix(second)
        return a.isNotEmpty() && a == b
    }

    /** True for short codes, which cannot receive MMS and are never real contacts. */
    fun isShortCode(address: String): Boolean = normalize(address).length in 1..6

    /** True when [address], once common formatting punctuation is stripped, is nothing but an
     * optional leading `+` and digits -- i.e. it's shaped like something a human could actually
     * be reached at (a real number or a short code). False for an RCS/business sender address
     * such as `agent@rbm.goog` (RCS Business Messaging) or an alphanumeric SMS sender id --
     * neither of which is a person who can be a thread "participant". Deliberately shape-based
     * rather than a list of known suffixes (`@rbm.goog` etc.), since new business-address formats
     * can show up without a matching app update. */
    fun looksLikePhoneNumber(address: String): Boolean {
        val stripped = address.trim().filterNot { it in FORMATTING_CHARACTERS }
        if (stripped.isEmpty()) return false
        val digits = stripped.removePrefix("+")
        return digits.isNotEmpty() && digits.all { it.isDigit() }
    }

    /** Narrows [addresses] to the ones [looksLikePhoneNumber] accepts, so a non-human sender
     * address never inflates a thread's participant count or flips
     * [text.message.sms.messaging.domain.model.Conversation.isGroup]. Falls back to the original
     * [addresses] when every one of them fails the check -- resolving *some* thread beats
     * silently dropping the message this participant set came from. */
    fun realParticipantsOnly(addresses: Set<String>): Set<String> {
        val filtered = addresses.filterTo(mutableSetOf(), ::looksLikePhoneNumber)
        return filtered.ifEmpty { addresses }
    }

    private val FORMATTING_CHARACTERS = charArrayOf(' ', '-', '(', ')', '.')
}
