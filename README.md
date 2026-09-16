# Messages

A modern Android SMS/MMS client. Single `:app` module, Jetpack Compose throughout, no XML
layouts and no fragments.

Package: `text.message.sms.messaging`

## Status

This is the scaffold pass. The project builds, installs and navigates between four empty
screens. The domain layer, the Room schema and the dependency graph are complete and wired;
the SMS/MMS pipelines behind them are not yet implemented — see
[What is not built yet](#what-is-not-built-yet).

## Stack

| Concern           | Choice                                               |
| ----------------- | ---------------------------------------------------- |
| Language          | Kotlin 2.2.10 (bundled with AGP 9.4.0)               |
| UI                | Jetpack Compose, Material 3 (Compose BOM 2026.09.00) |
| Async             | Coroutines + Flow                                    |
| Persistence       | Room 2.8.5                                           |
| Dependency inject | Hilt 2.60.1 (KSP, not kapt)                          |
| Navigation        | Navigation Compose 2.10.1                            |
| Build             | Gradle 9.7.1, Kotlin DSL, version catalog            |

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
├── domain/                     no Android imports; pure Kotlin
│   ├── model/                  Conversation, Message, Attachment, Contact, BlockedNumber, …
│   ├── repository/             repository interfaces + the MessageTransmitter /
│   │                           IncomingMessageSource ports the data layer implements
│   └── usecase/                one class per action, one `operator fun invoke` each
│
├── data/
│   ├── local/db/               Room database, entities, DAOs, type converters
│   ├── local/provider/         Telephony + Contacts ContentProvider read/write helpers
│   ├── receiver/               SMS_DELIVER, WAP_PUSH_DELIVER, sent and delivery reports
│   ├── repository/             repository implementations
│   └── mapper/                 entity ⇄ domain model conversion
│
├── di/                         Hilt modules and qualifiers
│
├── ui/
│   ├── theme/                  Material 3 color, type and shape tokens
│   ├── navigation/             NavHost + route definitions
│   ├── screens/                one package per screen
│   └── components/             shared composables
│
├── service/                    default-SMS-role handling, notification channels,
│                               RESPOND_VIA_MESSAGE
└── util/                       phone number and timestamp helpers
```

### Why there is a local database at all

The system Telephony provider stays the source of truth for messages — the platform requires
it, and other apps read from it. Room is a cache on top, for two reasons:

1. The provider hands back a `Cursor`, which has to be polled. Room exposes `Flow`, which the
   Compose UI can collect directly.
2. Archive, pin, mute and block are app-level state that the Telephony provider has nowhere to
   store.

`SyncRepository` is what keeps the two in step.

## Default SMS app requirements

Android will only offer an app as the default SMS handler if all four of these are declared in
the manifest. All four are present:

| Requirement                                     | Declared by                          |
| ----------------------------------------------- | ------------------------------------ |
| `SENDTO` for `sms:`/`smsto:`/`mms:`/`mmsto:`    | `MainActivity`                       |
| `SMS_DELIVER` receiver, `BROADCAST_SMS`         | `data.receiver.SmsDeliverReceiver`   |
| `WAP_PUSH_DELIVER` receiver, `BROADCAST_WAP_PUSH` | `data.receiver.MmsWapPushReceiver` |
| `RESPOND_VIA_MESSAGE` service, `SEND_RESPOND_VIA_MESSAGE` | `service.QuickResponseService` |

Declaring them makes the app *eligible*. Holding the role is a runtime step:
`service.DefaultSmsAppGuard.buildRoleRequestIntent()` produces the prompt, and `isDefault`
reports whether the app currently has it. Without the role the app can read the provider but
cannot write to it and never receives `SMS_DELIVER`.

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
a migration is reviewed by diffing them.

## What is not built yet

Deliberately out of scope for this pass. Each is a `TODO(...)` at its call site rather than a
silent no-op, so nothing fails quietly:

- **Provider sync** — `TelephonySyncRepository` holds the progress channel but does not walk
  the provider yet.
- **MMS receiving** — `MmsProviderGateway` reads the header row; PDU download and part decoding
  are not implemented.
- **MMS sending and scheduled sends** — `TelephonyMessageTransmitter` implements the plain-text
  SMS path only.
- **Attachment export** — `LocalAttachmentRepository.exportToGallery`.
- **Screens** — all four render a placeholder. Navigation between them works.
- **Theme** — sensible Material 3 defaults. The expressive tokens land in a later pass.
