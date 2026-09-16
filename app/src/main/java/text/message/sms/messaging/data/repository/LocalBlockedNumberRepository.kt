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

/** Room-backed [BlockedNumberRepository]. */
@Singleton
class LocalBlockedNumberRepository @Inject constructor(
    private val blockedNumberDao: BlockedNumberDao,
) : BlockedNumberRepository {

    override fun observeAll(): Flow<List<BlockedNumber>> =
        blockedNumberDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun isBlocked(address: String): Boolean =
        blockedNumberDao.isBlocked(PhoneNumbers.normalize(address))

    override suspend fun block(addresses: Collection<String>, reason: BlockReason) {
        val now = System.currentTimeMillis()
        blockedNumberDao.upsertAll(
            addresses.map { address ->
                BlockedNumberEntity(
                    address = address,
                    normalizedAddress = PhoneNumbers.normalize(address),
                    reason = reason,
                    blockedAtMillis = now,
                )
            },
        )
    }

    override suspend fun unblock(addresses: Collection<String>) {
        blockedNumberDao.delete(addresses.map(PhoneNumbers::normalize))
    }
}
