package text.message.sms.messaging.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for [MIGRATION_5_6] -- adds `conversations.pinned_at` (see its own doc for
 * why) without dropping any existing row, matching every other migration in [Migrations.kt].
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
}
