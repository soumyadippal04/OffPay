# Security Policy

OffPay handles UPI 123Pay payment flows, USSD dialing, and SMS parsing. Please treat security issues with appropriate care: **do not file public GitHub issues for vulnerabilities.**

## Supported versions

Security fixes land on the latest tagged release and on `main`. Older releases
are not backported — update to the newest release.

| Version             | Supported |
|---------------------|-----------|
| 1.0.x               | ✅        |
| `main` (unreleased) | ✅        |
| < 1.0               | ❌ pre-release, never distributed |

## Builds and signing

Download published APKs only from this repository's [GitHub Releases](https://github.com/soumyadippal04/Flowpay/releases), or build from source using [Running it](README.md#running-it).

The current OffPay development APK is signed with a locally generated development key, not an established official publisher key. Its signing certificate SHA-256 digest is:

```text
51536574a0a4aa1468800bd50bc91035b3ac4e3c9cf04ff9843ad8f11ae7889f
```

Verify an APK with `apksigner verify --print-certs OffPay-1.0.1-release.apk`. A file's SHA-256 checksum checks file integrity; it is different from the signing certificate digest above. Never publish private keys, keystores or their passwords.

OffPay uses application ID `com.offpay.app`, so it installs separately from Flowpay. Different signing keys cannot update the same installed application without uninstalling it first, which removes its private data. Physical-device launch and payment validation remain outstanding for this rebrand.

## Reporting a vulnerability

Report vulnerabilities privately to the repository maintainer, [@soumyadippal04](https://github.com/soumyadippal04), using the contact details published on their GitHub profile. If this repository offers **Security → Report a vulnerability**, use that private reporting route. Do not publish exploit details, bank SMS, PINs or other personal data in a public issue.

Please include:

1. A clear description of the issue and the affected component (file path, screen, or flow).
2. Steps to reproduce on a debug build (`./gradlew assembleDebug`).
3. Impact — what an attacker can read, modify, or trigger.
4. Suggested fix, if you have one.
5. Your contact info if you want credit in the eventual fix commit.

If the issue involves a leaked secret in the codebase or git history, include the commit SHA.

## Disclosure timeline

- **Within 72 hours** — acknowledgement that the report was received.
- **Within 14 days** — initial assessment shared with the reporter.
- **Within 90 days** — fix landed in `main` and credit posted (if requested), or a written explanation of why disclosure should be delayed.

Industry-standard 90-day window. If a fix lands sooner, credit is posted sooner.

## Scope

In scope:

- The Android app source under `app/src/main/`.
- The Gradle build configuration.
- The Room database schema and migrations.
- The USSD dialer flow, SMS parser, and call-overlay service.

Out of scope:

- Issues with third-party Android system APIs we use (`TelecomManager`, `SmsManager`, etc.) — those belong to Google/AOSP.
- Issues with the user's bank or UPI provider — those belong to the bank/NPCI.
- Theoretical issues that require an already-rooted device.

## Safe harbour

Researchers acting in good faith, sticking to the disclosure timeline above and not exfiltrating real user data, will not be pursued legally for the testing itself.

## Data handling & threat model

What the app stores, where, and for how long:

| Data | Where | Lifetime | Leaves the device? |
|------|-------|----------|--------------------|
| Transaction rows (amount, status, bank, bank ref, counterparty name, privacy-safe summary) | Room DB `offpay_database`, app-private storage | Until the user deletes them or uninstalls | **Never** — excluded from cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`) |
| Raw bank SMS bodies | **Not stored** (since schema v3) | — | The verbatim SMS stays in the user's SMS inbox app only |
| Active payment-operation window (expected amount, recipient number, ~10-min deadline) | `payment_operation` SharedPreferences | Cleared on confirmation or timeout | Never — excluded from backup |
| UPI PIN | **Never seen by the app** — entered into the bank's IVR/dialer flow | — | — |

Design rules the code enforces:

- **No INTERNET permission.** The app cannot transmit anything, by construction.
- **`READ_SMS` is not requested.** The app only receives incoming SMS (`RECEIVE_SMS`) while a payment operation is active; it never reads the inbox.
- **SMS are only inspected inside an explicit payment window** — a ~10-minute operation (a 10-minute verification deadline plus a 30-second grace margin) started when the user initiates a transfer; outside it, incoming messages are never read. Bank matching itself is deliberately permissive keyword matching (field-proven against real bank templates), so the operation window — not sender authentication — is the primary control.
- **Stored summaries are built constructively** from parsed fields (amount/status/bank/ref), so account numbers and balances in message prose can never reach the database.
- **There is one SMS ingestion path.** A notification-listener fallback used to exist alongside the broadcast receiver; it was removed as dead code — the setting that would have enabled it was never wired to anything, so it could never run. Removing it also drops the app's only `BIND_NOTIFICATION_LISTENER_SERVICE` component, so nothing in the app can read notification content from other apps.
- CI rejects log statements that interpolate SMS bodies in payment-critical packages.

## Known deferred issues

The CI baselines (`app/lint-baseline.xml`, `app/detekt-baseline.xml`) freeze
pre-existing static-analysis findings so new code must come in clean. Two are
worth naming because they look security-relevant and are deliberate:

- **`StaticFieldLeak` in `CallOverlayService`** — the service holds a static
  reference to itself so the overlay can be addressed from a broadcast context.
  It is cleared in `onDestroy()`, so the reference is bounded by the service
  lifecycle rather than leaked indefinitely.
- **`ExportedReceiver` on `DebugSmsInjectionReceiver`** — an unguarded exported
  receiver, in the **debug** source set only. It exists so a developer can
  replay a bank SMS through the live pipeline via `adb` without a real SIM, and
  requiring a permission would defeat that. It is absent from release builds
  entirely (`src/debug/AndroidManifest.xml`), not merely disabled at runtime.
  Verified by inspecting a built release APK, not just by reading the manifest.

Two more are deliberate rather than baselined, and are named here because both
look like oversights:

- **A `SharedPreferences` read on the main thread in the SMS path.**
  `SimpleSMSReceiver.onReceive` checks whether a payment window is open before
  its `goAsync()` hop, so the first such read in a process does synchronous
  disk I/O on the main thread — for every SMS the device receives, not just
  bank ones. StrictMode is enabled in debug builds with `penaltyLog` and does
  flag it. The file is tiny and the read is cached thereafter; moving the check
  into the coroutine touches the money path's entry point, which is not a
  change worth making outside a release with a device gate behind it.

- **`-assumenosideeffects` strips `Log.e` as well as the rest.** Release builds
  therefore carry no logging at all, including the "Keystore unusable"
  diagnostic. This is intentional — logs are the main way a payments app leaks
  PII, and the app has no crash reporting and no `INTERNET` permission — but it
  does mean a user-reported problem comes with a stack trace and nothing else.
  Deobfuscating one needs the R8 `mapping.txt` from the exact build that
  produced it. CI does not archive this anywhere — there is no release
  workflow — so whoever built the release (see README's "Signed release
  build") is the only source for it; it is written locally to
  `app/build/outputs/mapping/release/mapping.txt` on every `assembleRelease`.

If you spot something else with security implications hiding behind a baseline,
please report it.
