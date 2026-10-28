# OffPay — Payments Without Internet

An Android companion for bank-supported `*99#` USSD and UPI 123Pay phone flows, maintained and personalised by **Soumyadip Pal**.

![Build](https://github.com/soumyadippal04/OffPay/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/license-Apache_2.0-blue.svg)

OffPay combines a blue glass-style interface, smooth press animations, saved light/dark mode, QR image import and an About page. Android application ID: `com.offpay.app`.

## What it does

- **Scan or import a QR:** validate a UPI QR, copy its payee address for the bank menu, and open the configured `*99#` flow. Gallery import works without camera access.
- **Pay a contact:** select a saved contact or type a number and amount, then continue through the configured UPI 123Pay dialer flow. Direct calling is optional; without permission, the system dialer lets the user initiate the call.
- **Choose your bank:** search a locally packaged directory of 714 NPCI-listed bank issuers, including Central Bank of India. Common abbreviations such as CBI, SBI and BoB are searchable. Directory membership does **not** guarantee offline support; check availability with your bank and SIM. [Directory sources and limits](docs/OFFLINE_BANKS.md).
- **Review attempts:** payment handoffs without SMS access are labelled **Unverified**. Opening a dialer, completing setup or returning from a call never proves that money moved. Check your bank's confirmation before paying again.

OffPay is not a bank, wallet or payment processor. Your bank and payment provider authorise and process payments. OffPay never requests or captures a UPI PIN; enter it only in the bank's own phone flow.

## Permissions and APK editions

Phone, Camera and Contacts access are optional. Declining them leaves system-dialer handoff, QR image import and typed recipient entry available. Neither edition requests floating-overlay, call-state, end-call or audio-control access.

| Edition | SMS confirmation | Intended use |
| --- | --- | --- |
| **OffPay-1.0.5-release.apk** | Optional opt-in in Settings; only checks incoming messages during an active payment, never the inbox | Phones that permit SMS access; payments also work when it is denied |
| **OffPay-1.0.5-compat.apk** | SMS permission and receiver omitted entirely; outcomes checked with the bank | Phones/installers that restrict SMS-capable sideloaded apps |

The editions share the same package, version code and signing certificate. Install one edition; switching between them replaces the installed edition without creating a second app. The About page identifies the installed edition.

The owner reported a **Play Protect “Harmful app blocked”** warning on an earlier APK. Its specific cause has not been established. Permission changes do not constitute Google approval, and either APK may still require review. Do not disable device protections to install an APK that remains flagged. [Review evidence and Google's appeal channel](docs/PLAY_PROTECT_REVIEW.md).

## Requirements and practical limits

- Android 10 (API 29) or later, with a working phone/dialer service for payments.
- A compatible Indian SIM and bank account configured for the selected offline service.
- Bank and carrier support for the chosen rail. `*99#` availability varies; Jio users must use a supported alternative such as their bank's IVR service.

Bank menus, service numbers, DTMF timing and handset behaviour can change. OffPay's setup checks require the user to confirm what their bank actually displayed or said. The bank directory is not a list of banks physically tested with this APK. No universal phone, bank or payment-success guarantee is made.

## Privacy and architecture

The app has no backend, analytics or `INTERNET` permission. Transaction history stays in an encrypted local database and is excluded from cloud backup. SMS confirmation, when enabled and permitted, is limited to active payment windows. The compatibility edition has no SMS access.

The main components are:

- `managers/PaymentDialer.kt`: permission-aware call or dialer handoff.
- `managers/SmsConfirmation.kt`: optional SMS capability check.
- `payment/PaymentSessionManager.kt`: bank-confirmed or explicitly unverified payment states.
- `data/OfflineBankCatalog.kt`: shared searchable bank directory.
- `features/qr_scanner/`: CameraX/ZXing scanning and Android photo-picker import.
- `ui/components/GlassButton.kt`: reusable glass surfaces and accessible spring press feedback.

Kotlin 2.1, Jetpack Compose Material 3, Room/SQLCipher, min SDK 29 and target/compile SDK 35. See [architecture](docs/ARCHITECTURE.md) and [testing](docs/TESTING.md) for implementation details and the retained SMS regression tests.

## Running it

```bash
git clone https://github.com/soumyadippal04/OffPay.git OffPay
cd OffPay
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew installDebug
```

That's it. The build is self-contained — every dependency comes from public Maven repos.

First launch routes through Setup → connectivity test → home screen. Setup offers optional Phone, Camera and Contacts requests once. SMS confirmation is an explicit Settings opt-in in the SMS edition. Denials do not block payment handoff; SMS inbox access is never requested.

Use **Settings → Appearance → Dark mode** to switch between dark and light backgrounds. The choice applies immediately and is saved across launches.

If you want to skim the code without running it, the build also works without an Android device — `./gradlew assembleDebug` produces a working APK in `app/build/outputs/apk/debug/`.

**Signed release build:** copy `keystore.properties.example` to `keystore.properties`, fill in your signing-key details, then run `./gradlew assembleRelease`. The `keystore.properties` file and any `*.jks`/`*.keystore` files are gitignored, so signing material is never committed. Without a keystore, `assembleRelease` stops rather than handing you an unsigned, uninstallable APK — pass `-PallowUnsigned` if that's what you actually want.

**Compatibility edition:** run `./gradlew assembleCompat` with the same signing configuration. Its merged manifest excludes SMS and notification permissions and the SMS receiver. `-PallowUnsigned` is supported for both release variants when signing is performed separately.

Published APKs and checksums are linked from [GitHub Releases](https://github.com/soumyadippal04/OffPay/releases). Device and Play Protect verification status is stated in each release.

See [CONTRIBUTING.md](CONTRIBUTING.md) for code style and PR conventions, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the system fits together, [docs/FAQ.md](docs/FAQ.md) for the trust/permissions questions, [docs/TESTING.md](docs/TESTING.md) for how outcomes are verified, [SECURITY.md](SECURITY.md) for vulnerability disclosure, [CHANGELOG.md](CHANGELOG.md) for release history, [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community standards, and [LEGAL.md](LEGAL.md) for the full terms.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

You may use, modify, and redistribute this code under the terms of the Apache License 2.0. The license includes an explicit patent grant. There is no warranty of any kind.

---

## Legal

Full terms — no warranty, no affiliation with any bank or telecom, liability, and compliance — are in [LEGAL.md](LEGAL.md). By using, building, modifying, redistributing, or otherwise interacting with this software, you acknowledge that you have read, understood, and agreed to those terms.

