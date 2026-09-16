package text.message.sms.messaging.domain.model

/** Lifecycle of an outgoing message, from composed to acknowledged by the carrier. */
enum class DeliveryState {
    /** Queued locally, not handed to the radio yet. */
    PENDING,

    /** Handed to the platform and awaiting the sent broadcast. */
    SENDING,

    /** The carrier accepted the message. */
    SENT,

    /** The recipient handset acknowledged the message. */
    DELIVERED,

    /** The send attempt failed and may be retried. */
    FAILED,

    /** Not applicable — the message was received rather than sent. */
    NONE,
}
