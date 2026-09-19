package text.message.sms.messaging.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import text.message.sms.messaging.data.local.db.MessagingDatabase
import text.message.sms.messaging.data.local.db.entity.ConversationEntity
import text.message.sms.messaging.data.local.provider.MmsAttachmentStorage
import text.message.sms.messaging.data.local.provider.MmsProviderGateway
import text.message.sms.messaging.data.local.provider.SmsProviderGateway
import text.message.sms.messaging.domain.model.DeliveryState
import text.message.sms.messaging.domain.model.Message
import text.message.sms.messaging.domain.model.MessageChannel
import text.message.sms.messaging.domain.model.MessageFolder

/**
 * Data-integrity coverage for the sync perf pass's batched insert path -- see
 * [TelephonySyncRepository.syncAll], which now writes a whole chunk of a bulk sync through
 * [LocalMessageRepository.insertIncomingBatch] in one transaction instead of one transaction per
 * row. The perf change must not be able to lose, duplicate, or reorder a message; these tests
 * pin exactly that.
 */
@RunWith(AndroidJUnit4::class)
class LocalMessageRepositoryBatchSyncTest {

    private lateinit var database: MessagingDatabase
    private lateinit var repository: LocalMessageRepository

    private val threadId = 7L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MessagingDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val contentResolver = context.contentResolver
        val attachmentStorage = MmsAttachmentStorage(context)
        repository = LocalMessageRepository(
            database = database,
            messageDao = database.messageDao(),
            conversationDao = database.conversationDao(),
            attachmentDao = database.attachmentDao(),
            smsProviderGateway = SmsProviderGateway(contentResolver),
            mmsProviderGateway = MmsProviderGateway(contentResolver, attachmentStorage),
            attachmentStorage = attachmentStorage,
            contentResolver = contentResolver,
        )

        // messages.thread_id has a foreign key onto conversations(thread_id) -- every message
        // below needs that row to exist first, same as a real sync's resolveThreadId does before
        // ever calling insertIncomingBatch.
        runBlocking { database.conversationDao().upsert(ConversationEntity(threadId = threadId)) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sms(providerId: Long, receivedAtMillis: Long, body: String = "msg $providerId") = Message(
        id = 0L,
        threadId = threadId,
        providerId = providerId,
        channel = MessageChannel.SMS,
        folder = MessageFolder.INBOX,
        deliveryState = DeliveryState.NONE,
        address = "+15551234567",
        body = body,
        subject = null,
        sentAtMillis = receivedAtMillis,
        receivedAtMillis = receivedAtMillis,
        isRead = false,
        isSeen = false,
        subscriptionId = -1,
        errorCode = 0,
    )

    @Test
    fun insertIncomingBatch_insertsEveryRowInOneCall() = runBlocking {
        val batch = listOf(sms(1L, 1_000L), sms(2L, 2_000L), sms(3L, 3_000L))

        val inserted = repository.insertIncomingBatch(batch)

        assertEquals(3, inserted.size)
        assertTrue(inserted.all { it.id != 0L })
        val stored = database.messageDao().observeThread(threadId).first()
        assertEquals(3, stored.size)
    }

    @Test
    fun insertIncomingBatch_neverDuplicatesAnAlreadySyncedRow() = runBlocking {
        val batch = listOf(sms(1L, 1_000L), sms(2L, 2_000L))
        repository.insertIncomingBatch(batch)

        // Simulates a re-run sync pass re-processing the same provider rows (e.g. after a
        // watermark-stall re-inclusion) without first filtering them out.
        repository.insertIncomingBatch(batch)

        val stored = database.messageDao().observeThread(threadId).first()
        assertEquals("re-inserting the same provider rows must not duplicate them", 2, stored.size)
    }

    @Test
    fun insertIncomingBatch_preservesChronologicalOrderRegardlessOfInsertOrder() = runBlocking {
        // Deliberately out of order and newest-first, matching how TelephonySyncRepository.syncAll
        // now hands chunks to insertIncomingBatch (newest-first, for progressive Home visibility).
        val batch = listOf(sms(3L, 3_000L), sms(1L, 1_000L), sms(2L, 2_000L))

        repository.insertIncomingBatch(batch)

        val stored = database.messageDao().observeThread(threadId).first()
        assertEquals(listOf(1_000L, 2_000L, 3_000L), stored.map { it.message.receivedAtMillis })
    }

    @Test
    fun findExistingProviderIds_reportsOnlyRowsActuallyCached() = runBlocking {
        repository.insertIncomingBatch(listOf(sms(1L, 1_000L), sms(2L, 2_000L)))

        val existing = repository.findExistingProviderIds(listOf(1L, 2L, 3L), MessageChannel.SMS)

        assertEquals(setOf(1L, 2L), existing)
    }

    @Test
    fun findExistingProviderIds_neverCrossesChannels() = runBlocking {
        repository.insertIncomingBatch(listOf(sms(1L, 1_000L)))

        // SMS row 1 exists, but an MMS row with the same provider id space must not count --
        // Telephony's SMS and MMS tables don't share row-id space (see MessageRepository
        // .findByProviderId's own doc).
        val existing = repository.findExistingProviderIds(listOf(1L), MessageChannel.MMS)

        assertTrue(existing.isEmpty())
    }
}
