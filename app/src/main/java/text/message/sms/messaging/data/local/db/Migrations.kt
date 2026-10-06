package text.message.sms.messaging.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import text.message.sms.messaging.util.PhoneNumbers

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

/**
 * v4 -> v5: adds `conversations.subscription_slot`, the dual-SIM "Ask every time" send
 * preference's per-conversation memory (see [text.message.sms.messaging.domain.model.Conversation
 * .subscriptionSlot]). Nullable with no default write, so every existing row comes through as
 * null ("no remembered SIM yet") rather than losing any data. `messages.subscription_id` needs no
 * migration here -- it has shipped since the table's very first version.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN subscription_slot INTEGER")
    }
}

/**
 * v5 -> v6: adds `conversations.pinned_at`, the pin feature's "when" -- `is_pinned` alone (shipped
 * since v1) only says whether a thread is pinned, not in what order several pinned threads should
 * sort, so [text.message.sms.messaging.data.local.db.dao.ConversationDao.observeInbox] fell back to
 * ordering pinned threads by `last_message_at` like everything else. `NOT NULL DEFAULT 0` means
 * every already-pinned row from before this migration sorts as if pinned at the epoch -- last among
 * pinned threads, ahead of everything unpinned -- until the user re-pins it, rather than losing its
 * pinned state.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN pinned_at INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v6 -> v7: recomputes `blocked_numbers.normalized_address` with [PhoneNumbers.blockKey]. Until v6
 * it was [PhoneNumbers.normalize] for every row, which reduces an alphanumeric sender id
 * (`VM-HDFCBK`) to its digits -- usually "" -- so the unique index let only one such sender be
 * blocked at a time. Phone-shaped rows keep the key they had. Rows whose new keys coincide (e.g.
 * `VM-HDFCBK` and `AX-HDFCBK`, now one sender) collapse to the lowest id, matching
 * [MIGRATION_1_2]/[MIGRATION_3_4]. Data only: the schema is unchanged, but the unique index is
 * dropped and rebuilt around the rewrite so no intermediate state can trip it.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val keyById = linkedMapOf<Long, String>()
        db.query("SELECT id, address FROM blocked_numbers ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                keyById[cursor.getLong(0)] = PhoneNumbers.blockKey(cursor.getString(1))
            }
        }
        val keptIdByKey = linkedMapOf<String, Long>()
        val duplicateIds = mutableListOf<Long>()
        for ((id, key) in keyById) {
            if (keptIdByKey.putIfAbsent(key, id) != null) duplicateIds += id
        }

        db.execSQL("DROP INDEX IF EXISTS `index_blocked_numbers_normalized_address`")
        for (id in duplicateIds) {
            db.execSQL("DELETE FROM blocked_numbers WHERE id = ?", arrayOf<Any>(id))
        }
        for ((key, id) in keptIdByKey) {
            db.execSQL("UPDATE blocked_numbers SET normalized_address = ? WHERE id = ?", arrayOf<Any>(key, id))
        }
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_blocked_numbers_normalized_address` " +
                "ON `blocked_numbers` (`normalized_address`)",
        )
    }
}

/**
 * Every migration, oldest first -- the one list both
 * [text.message.sms.messaging.di.DatabaseModule] and the migration tests register, so a new step
 * can't be tested without also shipping (or shipped without being tested). Append each new
 * migration here.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
)
