# Medora Payment Capture — Android app (Modules 04 + 08)

Kotlin Android app for capturing **redacted** payment-SMS evidence and uploading
it to the Laravel site. It lives in `D:\mobile app\android-app`, deliberately
**outside** the Laravel tree at `C:\xampp\htdocs\medora`.

Laravel remains the source of truth. This app **never** reports a payment as
paid: it queues structured, masked metadata only, and every event goes to
`needs_review` for a human decision.

## Build & test

Java 17 and Android SDK (platform 35, build-tools 35) are required.
`local.properties` points at the installed SDK and is **not** committed.

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.19.10-hotspot"
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew testDebugUnitTest                  # JVM unit tests
./gradlew assembleDebug                      # debug APK
./gradlew compileDebugAndroidTestKotlin      # instrumentation sources compile
./gradlew connectedDebugAndroidTest          # needs an emulator or pilot handset
```

The API base URL is a build-time field in `app/build.gradle.kts`
(`BuildConfig.API_BASE_URL`). Debug defaults to the emulator host; for a
physical-device pilot override it (the debug build also enables cleartext HTTP,
release/staging remain HTTPS-only):

```bash
./gradlew assembleDebug -PpilotBaseUrl=http://<host-lan-ip>/medora/public/
```

## What is implemented

- **Screens** (`ui/AppScreens.kt`): Connect Device, Permissions & Privacy,
  Listening Status, Events (redacted), Troubleshooting, Disconnect.
- **Minimum permissions**: `RECEIVE_SMS` only. No `READ_SMS`, no inbox scan, no
  contacts, no OTP collection. Full phone numbers in the sender field are masked
  before storage; raw SMS bodies never enter Room or logs.
- **Local classification** (`sms/SmsClassifier.kt`, `sms/SenderAllowlist.kt`):
  heuristic, source-specific, conservative. Vodafone Cash EN/AR/Arabic-digit
  fixtures are classified; outgoing, balance, OTP, wrong currency, malformed
  amount, provider redesign and unknown senders are dropped. Nothing is guessed.
- **Durable queue** (`data/db/`): Room entity with a primary key on `event_uuid`
  and a **unique index on `content_digest`**, so duplicate broadcasts collapse to
  one pending event. UUID v4 ids, `pending → sent/failed` states, attempt counts.
- **Sync** (`sync/`): WorkManager, `CONNECTED` constraint, exponential backoff,
  idempotent retries. 401/403 clears credentials and stops sync; 4xx validation
  errors are dead-lettered locally; 404/429/5xx/network retry. Heartbeat is best
  effort. Laravel's 202 receipt is the only success condition.
- **Credentials** (`data/prefs/TokenStore.kt`): Keystore-backed
  `EncryptedSharedPreferences`; token is returned once by `/api/v1/device/pair`,
  never logged. Disconnect clears credentials but keeps local evidence.
- **Lifecycle** (`receiver/BootReceiver.kt`, `service/ListeningForegroundService.kt`):
  restores listening/sync after reboot or app update; visible low-priority status
  notification.
- **Reliability (Module 08)** (`sync/SyncHealth.kt`, `RetentionWorker.kt`):
  stale/degraded health policy, heartbeat tracking, and a daily worker that
  redacts retained local evidence. The Status screen shows sync health, last
  successful sync, heartbeat and pending/sent counts.

## API contract used

- `POST /api/v1/device/pair` `{code,name}` → `{success,data:{device,token}}`
- `POST /api/v1/device/heartbeat` (Bearer) → `{success,data:{last_seen_at}}`
- `POST /api/v1/device/events` (Bearer) → 202 `{success,data:{event_uuid,status,...}}`

The event body contains **no** `payment_status` and **no** raw message body —
only `event_uuid`, `source_type`, `source_id`, `occurred_at`, `amount_piastres`,
`provider_reference`, `sender_hint` and `content_digest`.

## Evidence

- `./gradlew testDebugUnitTest` → **22 passed, 0 failures** (classifier, masking,
  digest, sync policy and sync-health/retention policy tests).
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew compileDebugAndroidTestKotlin` → instrumentation sources compile:
  `PaymentEventDaoTest` (dedupe, sent queue, retry, permanent failure) and
  `DurableQueueInstrumentationTest` (queued event survives DB reopen, retried,
  sent once).
- **Physical handset pilot (2026-11-20):** installed on an Infinix X6885
  (Android 16), paired with a one-time code, and captured a **real 3000 EGP
  Vodafone Cash SMS** — classified, queued and uploaded (`Status: sent`), with a
  masked sender hint and no raw body stored. Status screen reported
  `Sync health: Healthy`. Laravel-side matching/approval evidence is in
  `C:\xampp\htdocs\medora\docs\payments\current-flow.md` §15.

## Limits

- Sender patterns and test fixtures are synthetic apart from the one real pilot
  capture. Replace/extend with real, manually redacted samples before wider
  distribution.
- Airplane mode, reboot recovery, battery-saver and manufacturer-ROM delays were
  not exercised on hardware; only the real-time capture/health path and the pure
  sync/health policies were verified.
- **Google Play restricts SMS permissions.** Do not assume payment SMS
  monitoring qualifies. Use internal/pilot distribution with informed consent
  until policy review is complete.

## Roadmap position

Laravel Modules 01–08 are complete and verified in
`C:\xampp\htdocs\medora\docs\payments\current-flow.md`. This app covers the
Android capture side (Module 04) and its reliability work (Module 08). A
1–2 week monitored pilot on the paired handset started 2026-11-20.
