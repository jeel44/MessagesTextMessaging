package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.BackupResult
import text.message.sms.messaging.domain.repository.BackupRepository
import javax.inject.Inject

/** Writes the full SMS/MMS history to [destinationUri], a location the user picked via the
 * system file picker. */
class ExportBackup @Inject constructor(
    private val backupRepository: BackupRepository,
) : UseCase {

    suspend operator fun invoke(destinationUri: String): BackupResult =
        backupRepository.export(destinationUri)
}
