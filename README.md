# Messages

A modern Android SMS/MMS client. Single `:app` module, Jetpack Compose throughout, no XML
layouts and no fragments.

Package: `text.message.sms.messaging`

## Status

The project builds, installs and navigates between four screens (still placeholders -- this
pass built the pipeline underneath them, not the UI). The domain, data, DI and UI layers are
wired, and the SMS/MMS send/receive/sync pipeline behind them is implemented and exercised by
unit tests -- see [What is not built yet](#what-is-not-built-yet) for the remaining, deliberate
gaps.

## Stack

| Concern           | Choice                                                              |
| ------------------ | -------------------------------------------------------------------- |
| Language          | Kotlin 2.2.10 (bundled with AGP 9.4.0)                              |
| UI                | Jetpack Compose, Material 3 (Compose BOM 2026.09.00)                |
| Async             | Coroutines + Flow                                                   |
| Persistence       | Room 2.8.5                                                          |
| Background work   | WorkManager 2.11.2 + Hilt integration (`androidx.hilt:hilt-work` 1.4.0) |
| Dependency inject | Hilt 2.60.1 (KSP, not kapt)                                         |
| Navigation        | Navigation Compose 2.10.1                                           |
| Build             | Gradle 9.7.1, Kotlin DSL, version catalog                           |

`minSdk 26`, `targetSdk 37`, `compileSdk 37`.

Every version is pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml); nothing
declares a version inline.

> **Kotlin version is not free to choose.** AGP 9 ships built-in Kotlin support, so the
> standalone `org.jetbrains.kotlin.android` plugin must *not* be applied. The Kotlin version is
> whatever the AGP release bundles (2.2.10 for AGP 9.4.0), and the Compose compiler plugin has
> to match it exactly.

## Module layout

Layers are packages inside the single `:app` module. Dependencies point inward: `ui` and `data`
both depend on `domain`, and `domain` depends on neither.

```
text.message.sms.messaging
|-- domain/                     no Android imports; pure Kotlin
|   |-- model/                  Conversation, Message, Attachment, Contact, BlockedNumber, ...
|   |-- repository/             repository interfaces + the MessageTransmitter /
|   |                           IncomingMessageSource ports the data layer implements
|   `-- usecase/                one class per action, one `operator fun invoke` each
|
|-- data/
|   |-- local/db/               Room database, entities, DAOs, type converters, migrations
|   |-- local/provider/         Telephony + Contacts ContentProvider read/write helpers,
|   |                           MMS transport (SmsManager wrapper) and attachment storage
|   |-- local/provider/mms/     MMS PDU codec -- pure Kotlin, no Android imports (see below)
|   |-- receiver/               SMS_DELIVER, WAP_PUSH_DELIVER, sent/delivery/download results,
|   |                           BOOT_COMPLETED
|   |-- work/                   WorkManager CoroutineWorker for scheduled sends
|   |-- repository/             repository implementations
|   `-- mapper/                 entity <-> domain model mappers
|
|-- di/                         Hilt modules and qualifiers
|
|-- ui/
|   |-- theme/                  Material 3 color, type and shape tokens
|   |-- navigation/             NavHost + route definitions
|   |-- screens/                one package per screen (still placeholders)
|   `-- components/             shared composables
|
|-- service/                    default-SMS-role handling, notification channels,
|                                RESPOND_VIA_MESSAGE
`-- util/                       phone number and timestamp helpers
```

### Why there is a local database at all

The system Telephony provider stays the source of truth for messages -- the platform requires
it, and other apps read from it. Room is a cache on top, for two reasons:

1. The provider hands back a `Cursor`, which has to be polled. Room exposes `Flow`, which the
   Compose UI can collect directly.
2. Archive, pin, mute and block are app-level state that the Telephony provider has nowhere to
   store.

`TelephonySyncRepository` is what keeps the two in step -- see
[The SMS/MMS pipeline](#the-smsmms-pipeline) below.

## The SMS/MMS pipeline

### Receiving

- **SMS** -- `SmsDeliverReceiver` gets `SMS_DELIVER` (only the default SMS app does), reassembles
  a multi-part message, and calls the `ReceiveSms` use case, which resolves the thread, checks
  the block list, and stores the message.
- **MMS** -- a WAP push only carries a *notification* (sender, subject, and a `Content-Location`
  URL to fetch the real message from). `MmsWapPushReceiver` decodes just that much and asks
  `MmsTransportGateway` (a thin wrapper over `SmsManager.downloadMultimediaMessage`) to fetch the
  rest over the carrier connection. `MmsDownloadResultReceiver` picks up once that download
  completes: decodes the retrieved PDU, mirrors it into the system `Telephony.Mms` provider (the
  same way an SMS lands in `Telephony.Sms`), and hands the resulting Uri to the `ReceiveMms` use
  case -- the same path a system-materialised row would go through.
- **PDU codec** -- `data/local/provider/mms` implements the WAP/MMS binary encoding
  (`OMA-WAP-209-MMS-Encapsulation`) fresh, using Android's own `SmsManager` for the actual
  carrier transport (HTTP/APN handling is entirely `SmsManager`'s job; this app never speaks HTTP
  or reads an APN table itself). It is pure Kotlin with no Android imports, which is what makes
  it unit-testable on the JVM -- see `PduIoTest`, `MmsPduDecoderTest`, `MmsPduEncoderTest`.

### Sending

- **SMS** -- `TelephonyMessageTransmitter.transmit` splits the body with
  `SmsManager.divideMessage` and sends via `sendMultipartTextMessage`.
- **MMS** -- the same method builds an `MmsSendRequest` from the message body and its
  attachments, encodes it with `MmsPduEncoder`, and hands it to
  `SmsManager.sendMultimediaMessage` via the documented cache-file + `FileProvider` +
  `grantUriPermission` pattern (the officially documented way to hand that API a PDU it can
  read). `MmsSendResultReceiver` updates delivery state once the MMSC responds.

### Scheduled sends

`ScheduleMessage` persists the message (folder `QUEUED`, the same folder the system SMS
provider itself uses for "waiting to go out") and registers a `OneTimeWorkRequest` via
`TelephonyMessageTransmitter.schedule`. **WorkManager, not `AlarmManager`, drives this** -- a
work request survives process death and a reboot on its own, tolerates Doze the same way every
other background job on the device does, and needs no extra permission. The alternative,
`AlarmManager.setExactAndAllowWhileIdle`, would need `SCHEDULE_EXACT_ALARM` on API 31+, a
permission the user has to separately grant in system settings, for a feature where "within
about a minute of the requested time" is an entirely acceptable trade against never showing that
prompt. See the class doc on `ScheduledSendWorker` for the full reasoning.

### Sync

`TelephonySyncRepository` mirrors `Telephony.Sms`/`Telephony.Mms` into Room incrementally: a
`sync_state` row tracks the newest timestamp already pulled in, so every sync -- the full walk
`syncAll` runs at app start, and the single-row `syncMessage` a `ProviderChangeObserver`
triggers when either provider changes outside this app's own writes -- only asks for rows newer
than that watermark. A row already cached (matched by provider id + channel) is skipped rather
than re-inserted, so re-running a sync is always safe.

### Attachments

Downloaded and sent MMS parts are copied into app-private storage (`MmsAttachmentStorage`, no
runtime permission needed) and exposed through a `FileProvider`. `ExportAttachment` copies one
into the shared `MediaStore` collection matching its type -- the scoped-storage way, no
filesystem path, no `WRITE_EXTERNAL_STORAGE` (see the gap noted in
[What is not built yet](#what-is-not-built-yet)).

## Default SMS app requirements

Android will only offer an app as the default SMS handler if all four of these are declared in
the manifest. All four are present:

| Requirement                                               | Declared by                          |
| ----------------------------------------------------------- | --------------------------------------- |
| `SENDTO` for `sms:`/`smsto:`/`mms:`/`mmsto:`               | `MainActivity`                       |
| `SMS_DELIVER` receiver, `BROADCAST_SMS`                    | `data.receiver.SmsDeliverReceiver`   |
| `WAP_PUSH_DELIVER` receiver, `BROADCAST_WAP_PUSH`          | `data.receiver.MmsWapPushReceiver`   |
| `RESPOND_VIA_MESSAGE` service, `SEND_RESPOND_VIA_MESSAGE`  | `service.QuickResponseService`       |

Declaring them makes the app *eligible*. Holding the role is a runtime step:
`service.DefaultSmsAppGuard.buildRoleRequestIntent()` produces the prompt, and `isDefault`
reports whether the app currently has it. Every provider read/write path in this pipeline
assumes the role is already held; `MessagingApplication.onCreate` itself checks `isDefault`
before registering the provider observer or running the first sync.

## Build

Requires JDK 17 and the Android SDK with platform 37 installed.

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew installDebug           # build and install on a connected device
./gradlew lintDebug              # Android lint
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests (device required)
```

Room exports its schema to `app/schemas/` on every build. Those files are committed on purpose:
a migration is reviewed by diffing them. The database is currently at version 2
(`MIGRATION_1_2` in `data/local/db/Migrations.kt`), which added the sync and scheduled-send
tables and fixed a latent bug where re-resolving a thread's participants had no unique
constraint stopping duplicate rows.

## What is not built yet

Deliberately out of scope, either for this pass or for a documented reason:

- **Screens** -- all four still render a placeholder. Navigation between them works; this pass
  was the pipeline, not the UI.
- **MMS delivery/read reports** -- treated as out of scope: most carriers do not reliably send
  them, and mainstream messaging apps generally treat "the MMSC accepted it" as the terminal
  success state, which is what `MmsSendResultReceiver` does here.
- **Attachment export on Android 8-9 (API 26-28)** -- `ExportAttachment` needs scoped storage
  (`MediaStore` writes with no extra permission), which only exists from Android 10 (API 29).
  This app's minSdk is 26, and a `WRITE_EXTERNAL_STORAGE` fallback was explicitly ruled out for
  this pass, so on those two platform versions the export step returns `null`/does nothing
  beyond what receiving already did (the file stays inside the app, just not pushed into the
  Gallery/Downloads).
- **The MMS PDU codec has no interop testing against a live carrier.** Its binary field-code
  table was cross-checked against AOSP's public constants (see the doc comment on
  `MmsConstants.kt`) and is covered by unit tests that build and decode realistic PDU byte
  streams by hand, but neither of those substitutes for a real MMSC round trip.
- **`android-smsmms` (klinker-labs) was evaluated and not used.** It resolves cleanly on Maven
  Central, but its only published version still pulls in `com.squareup.okhttp:okhttp:2.5.0`
  (2015, long EOL) for a raw APN/HTTP transport layer this app does not need: `SmsManager`
  already handles the carrier's MMSC address, proxy and data-connection requirements for
  `sendMultimediaMessage`/`downloadMultimediaMessage` (API 21+, well within minSdk 26). The part
  of that library actually useful here -- PDU encode/decode -- is not separable from its
  transport code in the published artifact, so this pass implements the PDU codec fresh instead
  (`data/local/provider/mms`), keeping the dependency list free of an abandoned HTTP stack.
- **Two interface changes from the previous pass**, both additive/behavior-preserving for
  existing callers:
  - `MessageRepository.insertOutgoing` gained a `folder` parameter (default `OUTBOX`, so
    existing calls are unaffected) so a scheduled send can be stored as `QUEUED` -- the same
    folder the system SMS provider itself uses for "waiting to go out" -- instead of `OUTBOX`.
  - `MessageTransmitter.schedule` changed from `schedule(addresses, body, sendAtMillis,
    subscriptionId)` (returning `Unit`) to `schedule(message, sendAtMillis)`. The original
    signature gave a caller no way to reference a specific scheduled item afterwards --
    `cancelPending(messageId)` had no `messageId` to be given. The new signature takes the
    already-persisted message (mirroring `transmit(message)`), so its id is available for
    cancellation from the moment it is scheduled. `ScheduleMessage` was updated to match; it was
    the only caller.
