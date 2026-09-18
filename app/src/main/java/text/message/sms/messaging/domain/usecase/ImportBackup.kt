package text.message.sms.messaging.domain.usecase

import text.message.sms.messaging.domain.model.BackupResult
import text.message.sms.messaging.domain.repository.BackupRepository
import javax.inject.Inject

/** Restores every message from a backup file previously written by [ExportBackup]. */
class ImportBackup @Inject constructor(
    private val backupRepository: BackupRepository,
) : UseCase {

    suspend operator fun invoke(sourceUri: String): BackupResult =
        backupRepository.import(sourceUri)
}
