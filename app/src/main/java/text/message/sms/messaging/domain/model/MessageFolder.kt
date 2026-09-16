package text.message.sms.messaging.domain.model

/**
 * Mirrors the folder a message lives in inside the system Telephony provider
 * (`Telephony.TextBasedSmsColumns.MESSAGE_TYPE` / `Telephony.BaseMmsColumns.MESSAGE_BOX`).
 */
enum class MessageFolder(val providerValue: Int) {
    INBOX(1),
    SENT(2),
    DRAFT(3),
    OUTBOX(4),
    FAILED(5),
    QUEUED(6),
    ;

    companion object {
        fun fromProviderValue(value: Int): MessageFolder =
            entries.firstOrNull { it.providerValue == value } ?: INBOX
    }
}
