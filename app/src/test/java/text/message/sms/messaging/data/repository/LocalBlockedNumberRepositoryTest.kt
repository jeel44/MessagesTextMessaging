package text.message.sms.messaging.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.data.local.db.dao.BlockedNumberDao
import text.message.sms.messaging.data.local.db.entity.BlockedNumberEntity
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.usecase.BlockedSenderGate
import text.message.sms.messaging.domain.usecase.neverCalledConversationRepository

/** [LocalBlockedNumberRepository] stores by normalized address but matches by
 * [text.message.sms.messaging.util.PhoneNumbers.isSameSender] -- what SMS, MMS and calls all go
 * through via [BlockedSenderGate]. */
class LocalBlockedNumberRepositoryTest {

    private val dao = FakeBlockedNumberDao()
    private val repository = LocalBlockedNumberRepository(dao)
    private val gate = BlockedSenderGate(repository, neverCalledConversationRepository())

    @Test
    fun aNumberBlockedWithCountryCode_blocksItsTrunkAndPlainForms() = runTest {
        repository.block(listOf("+91 98765 43210"), BlockReason.MANUAL)

        assertTrue(gate.isBlocked("+919876543210"))
        assertTrue(gate.isBlocked("09876543210"))
        assertTrue(gate.isBlocked("9876543210"))
        assertFalse(gate.isBlocked("9876543211"))
    }

    @Test
    fun aPlainNumberBlocked_blocksItsCountryCodeForm() = runTest {
        repository.block(listOf("9876543210"), BlockReason.MANUAL)

        assertTrue(gate.isBlocked("+919876543210"))
    }

    @Test
    fun shortCodes_matchOnlyExactly() = runTest {
        repository.block(listOf("56161"), BlockReason.MANUAL)

        assertTrue(gate.isBlocked("56161"))
        assertFalse(gate.isBlocked("156161"))
        assertFalse(gate.isBlocked("+919999956161"))
    }

    @Test
    fun alphanumericSenders_matchOnlyTheSameId() = runTest {
        repository.block(listOf("VM-AMAZON"), BlockReason.MANUAL)

        assertTrue(gate.isBlocked("vm-amazon"))
        assertFalse(gate.isBlocked("VM-FLIPKART"))
    }

    @Test
    fun unblock_removesEveryEquivalentRow_soNoFormStaysBlocked() = runTest {
        repository.block(listOf("+919876543210"), BlockReason.MANUAL)
        repository.block(listOf("09876543210"), BlockReason.MANUAL)
        repository.block(listOf("5550001"), BlockReason.MANUAL)

        repository.unblock(listOf("9876543210"))

        assertFalse(gate.isBlocked("+919876543210"))
        assertFalse(gate.isBlocked("09876543210"))
        assertEquals(listOf("5550001"), dao.rows.map { it.address })
    }
}

/** An in-memory [BlockedNumberDao] keyed like the real table's unique `normalized_address`. */
private class FakeBlockedNumberDao : BlockedNumberDao {
    val rows = mutableListOf<BlockedNumberEntity>()

    override fun observeAll(): Flow<List<BlockedNumberEntity>> = MutableStateFlow(rows.toList())

    override suspend fun getAll(): List<BlockedNumberEntity> = rows.toList()

    override suspend fun upsertAll(numbers: List<BlockedNumberEntity>) {
        numbers.forEach { number ->
            rows.removeAll { it.normalizedAddress == number.normalizedAddress }
            rows += number
        }
    }

    override suspend fun delete(normalizedAddresses: Collection<String>) {
        rows.removeAll { it.normalizedAddress in normalizedAddresses }
    }

    override suspend fun clear() = rows.clear()
}
