package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import text.message.sms.messaging.data.local.db.dao.BlockedNumberDao
import text.message.sms.messaging.data.local.db.entity.BlockedNumberEntity
import text.message.sms.messaging.data.mapper.toDomain
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.util.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [BlockedNumberRepository]. Rows are stored by [PhoneNumbers.blockKey], but matched
 * with [PhoneNumbers.isSameSender] in memory, so a number blocked as `+919876543210` also blocks
 * `09876543210` -- a lookup by the stored key alone would miss that.
 */
@Singleton
class LocalBlockedNumberRepository @Inject constructor(
    private val blockedNumberDao: BlockedNumberDao,
) : BlockedNumberRepository {

    override fun observeAll(): Flow<List<BlockedNumber>> =
        blockedNumberDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun isBlocked(address: String): Boolean =
        blockedNumberDao.getAll().any { PhoneNumbers.isSameSender(it.address, address) }

    override suspend fun block(addresses: Collection<String>, reason: BlockReason) {
        val now = System.currentTimeMillis()
        blockedNumberDao.upsertAll(
            addresses.map { address ->
                BlockedNumberEntity(
                    address = address,
                    normalizedAddress = PhoneNumbers.blockKey(address),
                    reason = reason,
                    blockedAtMillis = now,
                )
            },
        )
    }

    /** Removes every row [isBlocked] would match for [addresses], not just exact ones -- otherwise
     * unblocking `09876543210` would leave a `+919876543210` row still blocking it. */
    override suspend fun unblock(addresses: Collection<String>) {
        val matching = blockedNumberDao.getAll()
            .filter { row -> addresses.any { PhoneNumbers.isSameSender(row.address, it) } }
            .map { it.normalizedAddress }
        blockedNumberDao.delete(matching + addresses.map(PhoneNumbers::blockKey))
    }
}
