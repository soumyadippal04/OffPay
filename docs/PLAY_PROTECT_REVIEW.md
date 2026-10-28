# Play Protect review notes

Prepared on 28 October 2026 for OffPay 1.0.5 (version code 10), package
`com.offpay.app`. This is a maintainer review record, not a claim of Google
approval or a guarantee that an APK will be accepted on every device.

## Report and evidence

The owner reported the exact warning **“Harmful app blocked”** when installing
an OffPay APK, and restricted SMS and other permissions on Nothing phones.
The detailed warning/category, affected APK hash, installer, device model and
Android/Play Protect versions have not been supplied. The cause is therefore
unconfirmed; a false positive has not been established.

Google documents two different cases that must not be conflated:

- **App blocked to protect your device:** in selected markets, APKs downloaded
  through internet sideloading sources can be blocked when they declare
  `RECEIVE_SMS`, `READ_SMS`, notification-listener or accessibility access.
  Merely requesting SMS later or making it optional does not remove a declared
  permission from this rule.
- **Harmful App Blocked:** Google says the app has been identified as a
  potentially harmful application within a malware or mobile unwanted software
  category. The detailed category matters. Removing a permission does not prove
  that this classification has been resolved.

Android separately documents `RECEIVE_SMS` as a **hard restricted permission**
that an app cannot hold until its installer allowlists it. An app's runtime
permission request cannot override that restriction. This explains why repeated
permission prompts are not a reliable compatibility strategy; it does not by
itself diagnose a particular phone's refusal.

Official sources reviewed:

- [Developer guidance for Google Play Protect warnings](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Android RECEIVE_SMS permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)
- [Google's potentially harmful application definitions](https://developers.google.com/android/play-protect/potentially-harmful-applications)
- [Play Protect appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals)

## Editions and changes in 1.0.5

At the owner's request, **the primary APK keeps optional SMS confirmation**.
A separate compatibility APK removes that capability and its manifest access.

| Capability | Primary APK | Compatibility APK |
| --- | --- | --- |
| Incoming SMS confirmation | Available only when Android grants `RECEIVE_SMS` | Not packaged |
| `RECEIVE_SMS` declaration and incoming-SMS receiver | Present | Absent |
| `POST_NOTIFICATIONS` declaration | Present for optional outcome notifications | Absent |
| Gradle build task | `assembleRelease` | `assembleCompat` |
| Payment handoff without SMS access | Available; outcome must be checked with the bank | Available; outcome must be checked with the bank |

Making SMS optional keeps payment handoff usable when access is denied, but the
primary APK **still declares the restricted permission**. It can therefore still
trigger Google's documented sideloading protection. The compatibility APK
removes that particular declaration; this does not establish why the owner saw
“Harmful app blocked” or guarantee that Google will accept either edition.

Both editions make the following changes:

- Remove `SYSTEM_ALERT_WINDOW` and the overlay service implementation, along with
  `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS` and `MODIFY_AUDIO_SETTINGS` from the
  manifest. The active payment flow does not cover the bank's phone UI, read
  call state, hang up calls or change call audio.
- Keep optional `CALL_PHONE`, `CAMERA` and `READ_CONTACTS`, plus the normal
  `VIBRATE` permission. Permission denials do not block unrelated features:
  direct-call denial falls back to the system dialer, camera denial retains QR
  image import, and contacts denial retains manual recipient entry.
- Use `ACTION_CALL` only when direct calls are granted. If access is absent or
  an OEM refuses the call, `ACTION_DIAL` opens the same telephone URI for the
  user to review and initiate. If neither action is available, the app reports
  that limitation instead of reporting a successful call or payment.
- Offer explicit user confirmation for bank setup when call-state observation
  is unavailable. Opening a dialer or confirming setup does not verify a
  payment. SMS confirmation is used only when it is packaged and Android has
  granted the permission; otherwise the outcome remains unverified in OffPay
  and must be checked directly with the bank.
- Require a finite positive amount encoded in a QR code before enabling SMS
  correlation for that QR payment. Amountless QR payments remain unverified
  in OffPay, even when SMS access is granted, so an unrelated incoming debit
  cannot be matched solely because it arrived during that payment attempt.
- Declare no `INTERNET` permission or accessibility service. The app does not
  download executable code, install other APKs or collect a UPI PIN. Payment
  authorisation stays in the bank's phone/USSD flow.

The two APKs share the same package, signing certificate and version code.
Choose one edition; they replace one another rather than installing side by
side. The compatibility edition deliberately removes automatic SMS confirmation
instead of trying to obtain a restricted permission through another route.

SMS matching is local receipt interpretation, not authenticated bank-server
verification. The existing matcher does not distinguish every concurrent debit
of the same amount by its payee. That correlation limitation remains in the
optional-SMS edition; the bank's own record is authoritative.

The package name and signing certificate remain stable to preserve upgrades.
They are not changed to conceal the app from security checks. Signing still
uses the existing local development key; that is not evidence of an established
publisher identity or Google approval.

These statements must be checked against the final merged **release** manifest
and signed APK. Debug SMS-injection tooling is for local testing only and must
not be packaged in the published release.

## Review identifiers

| Item | Value |
| --- | --- |
| Application ID | `com.offpay.app` |
| Candidate version | 1.0.5, version code 10 |
| Primary candidate APK | `OffPay-1.0.5-release.apk` |
| Compatibility candidate APK | See the compatibility download in the release notes |
| Primary candidate SHA-256 | [Published APK and checksum](https://github.com/soumyadippal04/OffPay/releases/tag/v1.0.5) |
| Compatibility candidate SHA-256 | [Published APK and checksum](https://github.com/soumyadippal04/OffPay/releases/tag/v1.0.5) |
| Candidate source commit | [Source tag v1.0.5](https://github.com/soumyadippal04/OffPay/tree/v1.0.5) |
| Signing certificate SHA-256 | `51536574a0a4aa1468800bd50bc91035b3ac4e3c9cf04ff9843ad8f11ae7889f` |
| Previously published 1.0.4 APK SHA-256 | `cf49970d145650bd8404f81c92e50b20b4bcbd88fb2469a687b2c815493a8496` |

The published 1.0.4 hash above is known. It has **not** been established that
this exact binary produced the reported warning. The owner should identify the
flagged file rather than assume it was the latest download.

## Information for an owner-submitted review

The [official appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals)
is the appropriate channel if Google still blocks the reviewed APK. No appeal
has been submitted as part of these source changes. Prepare:

1. The full warning screenshot and its expanded details/category, with personal
   information redacted as necessary.
2. The exact flagged APK, SHA-256, package/version, download URL and installer
   used, plus whether the warning occurred during installation or a later scan.
3. The phone model, Android/OEM version and Google Play Protect/Play services
   versions where available.
4. The current signed APK and checksum, **which edition it is**, source/release
   URL, signing certificate fingerprint and the permission/behaviour changes
   described above.
5. A concise explanation of the intended offline-UPI flow and reproducible
   steps. State that the reason for the classification is unknown; ask for
   review rather than asserting that Google has already made an error.

Local builds, unit tests and APK signature checks cannot establish Play Protect
acceptance. Verify the exact signed build on physical devices, including the
affected Nothing phone, with security protections enabled. Google may still
flag the new version, and carrier/bank support also varies. Do not disable
Play Protect, force restricted permissions, or cycle package names or signing
certificates to bypass a warning.
