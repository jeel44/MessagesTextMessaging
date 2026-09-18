package text.message.sms.messaging.domain.model

/** Outcome of a [text.message.sms.messaging.domain.repository.BackupRepository] export or
 * import pass. */
sealed interface BackupResult {

    data class Success(val messageCount: Int) : BackupResult

    sealed interface Failure : BackupResult {

        /** The app does not currently hold the default-SMS-app role, so it cannot write restored
         * messages into the system provider -- the same requirement
         * [text.message.sms.messaging.data.repository.TelephonySyncRepository.syncAll] enforces
         * before touching the provider. */
        data object NotDefaultSmsApp : Failure

        /** The picked file [android.content.ContentResolver.openOutputStream]/`openInputStream`
         * refused to open, e.g. the underlying document was deleted after being picked. */
        data object DestinationUnavailable : Failure

        /** An unexpected failure; [message] is the real exception description (see
         * [text.message.sms.messaging.data.repository.TelephonySyncRepository]'s own
         * `describe()`), not a canned string, so it is diagnosable from the failure toast alone. */
        data class Error(val message: String) : Failure
    }
}
