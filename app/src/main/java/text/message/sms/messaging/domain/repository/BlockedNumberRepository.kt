package text.message.sms.messaging.domain.repository

import kotlinx.coroutines.flow.Flow
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber

/** Access to the block list. */
interface BlockedNumberRepository {

    fun observeAll(): Flow<List<BlockedNumber>>

    suspend fun isBlocked(address: String): Boolean

    suspend fun block(addresses: Collection<String>, reason: BlockReason)

    suspend fun unblock(addresses: Collection<String>)
}
