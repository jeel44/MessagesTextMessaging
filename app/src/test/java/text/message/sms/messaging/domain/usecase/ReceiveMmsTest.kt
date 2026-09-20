package text.message.sms.messaging.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import text.message.sms.messaging.domain.model.BlockReason
import text.message.sms.messaging.domain.model.BlockedNumber
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder
import text.message.sms.messaging.domain.repository.BlockedNumberRepository
import text.message.sms.messaging.domain.repository.IncomingMessageSource

/**
 * Mms-side counterpart to [ReceiveSmsTest]: a blocked sender's message must never reach storage
 * or the notifier. The success/duplicate paths need a real Room database (see [ReceiveSmsTest]'s
 * doc) so aren't covered here.
 */
class ReceiveMmsTest {

    @Test
    fun invoke_blockedSender_returnsNullAndNeverNotifies() = runTest {
        val notifier = RecordingIncomingMessageNotifier()
        val pending = Message(
            id = 0L,
            threadId = 1L,
            providerId = 42L,
            channel = MessageChannel.MMS,
            folder = MessageFolder.INBOX,
            deliveryState = DeliveryState.NONE,
            address = "12345",
            body = "",
            subject = null,
            sentAtMillis = 1_000L,
            receivedAtMillis = 1_000L,
            isRead = false,
            isSeen = false,
            subscriptionId = -1,
            errorCode = 0,
        )
        val incomingMessageSource = object : IncomingMessageSource {
            override suspend fun readMessage(providerUri: String): Message = pending
        }
        val blockedNumberRepository = object : BlockedNumberRepository {
            override fun observeAll(): Flow<List<BlockedNumber>> = MutableStateFlow(emptyList())
            override suspend fun isBlocked(address: String): Boolean = true
            override suspend fun block(addresses: Collection<String>, reason: BlockReason) = Unit
            override suspend fun unblock(addresses: Collection<String>) = Unit
        }
        val receiveMms = ReceiveMms(
            database = neverUsedDatabase(),
            incomingMessageSource = incomingMessageSource,
            messageRepository = neverCalledMessageRepository(),
            blockedNumberRepository = blockedNumberRepository,
            incomingMessageNotifier = notifier,
        )

        val result = receiveMms("content://mms/42")

        assertNull(result)
        assertTrue(notifier.notifiedMessages.isEmpty())
    }
}
