package text.message.sms.messaging.data.local.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for [MIGRATION_5_6] -- adds `conversations.pinned_at` (see its own doc for
 * why) without dropping any existing row, matching every other migration in [Migrations.kt] -- and
 * [MIGRATION_6_7], which re-keys `blocked_numbers` for alphanumeric senders.
 */
@RunWith(AndroidJUnit4::class)
class MessagingDatabaseMigrationTest {

    @get:Rule
    val migrationTestHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MessagingDatabase::class.java,
    )

    @Test
    fun migrate5To6_preservesExistingConversationsAndDefaultsPinnedAtToZero() {
        val dbName = "migration-test"

        migrationTestHelper.createDatabase(dbName, 5).apply {
            execSQL(
                """
                INSERT INTO conversations
                    (thread_id, snippet, last_message_at, unread_count, is_archived, is_pinned, is_blocked, is_muted, draft, subscription_slot)
                VALUES
                    (1, 'hello', 1700000000000, 2, 0, 1, 0, 0, NULL, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        migrated.query("SELECT thread_id, snippet, is_pinned, pinned_at FROM conversations WHERE thread_id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("thread_id")))
            assertEquals("hello", cursor.getString(cursor.getColumnIndexOrThrow("snippet")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")))
            // The migration itself can't know *when* an already-pinned row was pinned -- 0 (the
            // epoch) is the documented, deliberate default until the user re-pins it.
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("pinned_at")))
        }
    }

    @Test
    fun migrate5To6_thenOpeningTheRealDatabase_succeeds() {
        val dbName = "migration-open-test"
        migrationTestHelper.createDatabase(dbName, 5).close()
        migrationTestHelper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        // Opening with the real Room.databaseBuilder (rather than the raw framework helper above)
        // is what actually exercises MessagingDatabase's own @Database(version = 6) declaration
        // against the migrated schema -- a mismatch here (e.g. a hand-written migration that
        // drifted from the entity) throws IllegalStateException on open.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, MessagingDatabase::class.java, dbName)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(MIGRATION_5_6)
            .build()
        database.openHelper.writableDatabase
        database.close()
    }

    @Test
    fun migrate6To7_rekeysAlphanumericSendersAndKeepsNumbers() {
        val dbName = "migration-6-7-test"

        migrationTestHelper.createDatabase(dbName, 6).apply {
            // v6 keyed every row by normalize(): "" for an all-letter id, "1" for VM-HDFC1.
            insertBlocked(id = 1, address = "VM-HDFCBK", key = "")
            insertBlocked(id = 2, address = "AX-HDFC1", key = "1")
            insertBlocked(id = 3, address = "+91 98765 43210", key = "+919876543210")
            insertBlocked(id = 4, address = "56161", key = "56161")
            close()
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        assertEquals(
            mapOf(1L to "HDFCBK", 2L to "HDFC1", 3L to "+919876543210", 4L to "56161"),
            migrated.blockedKeysById(),
        )
    }

    @Test
    fun migrate6To7_collapsesRowsThatShareANewKeyToTheLowestId() {
        val dbName = "migration-6-7-dupes-test"

        migrationTestHelper.createDatabase(dbName, 6).apply {
            // Distinct v6 keys (the unique index allowed both), one sender once prefixes are dropped.
            insertBlocked(id = 1, address = "VM-HDFCBK", key = "")
            insertBlocked(id = 2, address = "AX-HDFCBK", key = "legacy-ax-hdfcbk")
            insertBlocked(id = 3, address = "JD-SBIBNK", key = "legacy-jd-sbibnk")
            close()
        }

        val migrated = migrationTestHelper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        assertEquals(mapOf(1L to "HDFCBK", 3L to "SBIBNK"), migrated.blockedKeysById())
        // The rebuilt unique index is enforced: a second HDFCBK row can't be inserted.
        assertThrows(SQLiteConstraintException::class.java) {
            migrated.insertBlocked(id = 9, address = "HDFCBK", key = "HDFCBK")
        }
    }

    @Test
    fun migrate6To7_thenOpeningTheRealDatabase_succeeds() {
        val dbName = "migration-6-7-open-test"
        migrationTestHelper.createDatabase(dbName, 6).close()
        migrationTestHelper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, MessagingDatabase::class.java, dbName)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .build()
        database.openHelper.writableDatabase
        database.close()
    }
}

private fun SupportSQLiteDatabase.insertBlocked(id: Long, address: String, key: String) = execSQL(
    "INSERT INTO blocked_numbers (id, address, normalized_address, reason, blocked_at) VALUES (?, ?, ?, 'MANUAL', 0)",
    arrayOf<Any>(id, address, key),
)

private fun SupportSQLiteDatabase.blockedKeysById(): Map<Long, String> =
    query("SELECT id, normalized_address FROM blocked_numbers ORDER BY id").use { cursor ->
        buildMap { while (cursor.moveToNext()) put(cursor.getLong(0), cursor.getString(1)) }
    }
