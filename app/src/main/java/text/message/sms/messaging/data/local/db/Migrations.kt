package text.message.sms.messaging.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds the tables the sync and scheduled-send pipelines need, and fixes a latent bug
 * in `recipients` where re-resolving a thread's participants (which every send and every
 * receive does) could insert the same address twice with no unique constraint stopping it.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_state` (
                `id` TEXT NOT NULL,
                `last_sms_date_ms` INTEGER NOT NULL,
                `last_mms_date_sec` INTEGER NOT NULL,
                `last_full_sync_at` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `scheduled_messages` (
                `message_id` INTEGER NOT NULL,
                `send_at` INTEGER NOT NULL,
                `work_name` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`message_id`),
                FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        // Collapse any duplicate (thread_id, address) rows the old, non-unique index allowed
        // before the new unique index below can be created.
        db.execSQL(
            """
            DELETE FROM recipients
            WHERE id NOT IN (SELECT MIN(id) FROM recipients GROUP BY thread_id, address)
            """.trimIndent(),
        )

        db.execSQL("DROP INDEX IF EXISTS `index_recipients_thread_id`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recipients_thread_id_address` " +
                "ON `recipients` (`thread_id`, `address`)",
        )
    }
}

/**
 * v2 -> v3: adds the tables backing the contacts-groups mirror ("Family", "Work"), so group-MMS
 * recipient pickers and thread labelling can use a saved group the same way QKSMS's contact
 * picker does.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `contact_groups` (
                `id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `contact_group_members` (
                `group_id` INTEGER NOT NULL,
                `contact_lookup_key` TEXT NOT NULL,
                PRIMARY KEY(`group_id`, `contact_lookup_key`),
                FOREIGN KEY(`group_id`) REFERENCES `contact_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contact_group_members_group_id` " +
                "ON `contact_group_members` (`group_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contact_group_members_contact_lookup_key` " +
                "ON `contact_group_members` (`contact_lookup_key`)",
        )
    }
}

/**
 * v3 -> v4: one-time cleanup for the same person appearing twice in one thread's `recipients`
 * because their address was stored in two different formats -- e.g. a contact-picker's
 * ContactsContract number ("63510 00085") alongside an earlier message's raw Telephony address
 * ("6351000085"). [MIGRATION_1_2] already collapsed *exact* duplicate (thread_id, address) rows;
 * this collapses rows that are the same address once formatting is stripped, which is what let a
 * single real contact show up as "2 participants". Keeps the lowest-id row per
 * (thread_id, stripped-suffix) group, matching MIGRATION_1_2's convention. Only merges within a
 * thread the platform had already unified under one thread id -- it does not merge separate
 * threads/conversations.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM recipients
            WHERE id NOT IN (
                SELECT MIN(id) FROM recipients
                GROUP BY thread_id, substr(
                    replace(replace(replace(replace(replace(address, ' ', ''), '-', ''), '(', ''), ')', ''), '.', ''),
                    -9
                )
            )
            """.trimIndent(),
        )
    }
}
