package text.message.sms.messaging.domain.repository

import text.message.sms.messaging.domain.model.BackupResult

/**
 * Exports/imports the full SMS/MMS history to/from a single local file the user picks via the
 * system file picker, matching QKSMS's local backup approach -- no cloud account, no proprietary
 * format, just a JSON file the user owns.
 */
interface BackupRepository {

    /** Writes every message -- and every MMS attachment inline, as base64 -- to
     * [destinationUri], a `content://` Uri the caller already opened for writing (e.g. via
     * `ActivityResultContracts.CreateDocument`). */
    suspend fun export(destinationUri: String): BackupResult

    /** Reads a file previously written by [export] from [sourceUri] and re-inserts every message
     * into the system Telephony provider, then triggers a full resync so the local cache picks
     * them up. Requires this app to currently hold the default-SMS-app role, the only role able
     * to write there. */
    suspend fun import(sourceUri: String): BackupResult
}
