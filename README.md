# Sync parity changes — QKSMS clone, batch 1

These files implement full parity for the message/contact **sync** layer against QKSMS's
behavior, on top of your existing Compose/Room/Hilt architecture (no Realm/RxJava/Dagger2 —
kept your stack, matched QKSMS's *behavior*).

## How to apply

Every path below is relative to:
`app/src/main/java/text/message/sms/messaging/`

Copy each file into that same relative path in your real repo, overwriting where it already
exists. Then commit and push yourself — this package has no git history, it's just the final
file contents.

```
cp -r sync-parity-changes/* /path/to/your/repo/app/src/main/java/text/message/sms/messaging/
```

## New files (10)

- `data/local/db/entity/ContactGroupEntity.kt`
- `data/local/db/entity/ContactGroupMemberEntity.kt`
- `data/local/db/entity/ContactGroupWithContacts.kt`
- `data/local/db/dao/ContactGroupDao.kt`
- `domain/model/ContactGroup.kt`
- `data/local/provider/ContactChangeObserver.kt`

## Modified files (13)

- `data/local/provider/ContactProviderGateway.kt` — contact number dedup (WhatsApp-style
  duplicates), plus reads groups/membership
- `data/mapper/ContactMapper.kt` — new mapper functions for `ContactEntity` and group rows
- `domain/repository/ContactRepository.kt` — added `observeGroups()`
- `data/repository/LocalContactRepository.kt` — wired `ContactGroupDao`, syncs groups in
  `refreshFromProvider()`
- `data/local/db/MessagingDatabase.kt` — registered new entities/DAO, bumped schema to v3
- `data/local/db/Migrations.kt` — added `MIGRATION_2_3` for the new tables
- `di/DatabaseModule.kt` — registered the migration and new DAO provider
- `MessagingApplication.kt` — registers `ContactChangeObserver`, runs initial contacts sync
  gated on `READ_CONTACTS` (separate from the SMS default-app role)
- `data/receiver/BootCompletedReceiver.kt` — also re-syncs contacts on boot
- `data/local/db/dao/MessageDao.kt` — added `hasAnyMessages()` (cheap `EXISTS` query)
- `domain/repository/MessageRepository.kt` — exposed `hasAnyMessages()`
- `data/repository/LocalMessageRepository.kt` — implemented `hasAnyMessages()`
- `data/repository/TelephonySyncRepository.kt` — guards against a cache-empty-but-
  watermark-advanced state by forcing a full backfill

## Before you build

1. **Room schema export**: since the DB version bumped 2 → 3, Room will regenerate
   `app/schemas/text.message.sms.messaging.data.local.db.MessagingDatabase/3.json` on your next
   build (assuming `exportSchema = true` and the KSP/kapt schema location arg are already
   configured, which they are in your existing `build.gradle.kts`). Don't hand-write that file.
2. **Sanity-build** in Android Studio and run any Room schema/migration tests you have —
   I wrote `MIGRATION_2_3` by hand (matching your existing `MIGRATION_1_2` style) but it hasn't
   been run against Room's own migration verifier.
3. `READ_CONTACTS` permission was already declared in your `AndroidManifest.xml` — no manifest
   changes needed for this batch.

## What this does NOT include yet

Feature-level QKSMS parity (Settings screen, Backup & Restore, Conversation info screen, Theme
picker) — that's the next batch, not part of this sync-layer package.
