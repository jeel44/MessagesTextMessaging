package text.message.sms.messaging.domain.model

/** A number whose messages are diverted away from the inbox. */
data class BlockedNumber(
    val id: Long,
    val address: String,
    val reason: BlockReason,
    val blockedAtMillis: Long,
)

/** Why a number ended up on the block list. */
enum class BlockReason {
    /** The user blocked the number from within this app. */
    MANUAL,

    /** Blocked in the platform-wide `BlockedNumberContract`. */
    SYSTEM,

    /** Flagged by an on-device spam heuristic. */
    SPAM,
}
