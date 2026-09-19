package text.message.sms.messaging.domain.model

/** One active SIM subscription, as reported by `SubscriptionManager`. */
data class SimInfo(
    val subscriptionId: Int,
    /** 0-based physical slot index -- what this app's SIM preferences are keyed by, since a
     * subscription id can change across a SIM swap while the slot stays stable. */
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String?,
) {
    /** 1-based slot number for UI labels ("SIM 1", "SIM 2"). */
    val slotNumber: Int get() = slotIndex + 1
}
