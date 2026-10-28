# OffPay APK downloads

Signed development APKs are distributed on this branch. Source is on `main`.

## OffPay 1.0.5 — testing release

Choose one edition. Both use application ID `com.offpay.app`, version code 10, Android 10+, and the same development signing key as previous OffPay releases.

- [Optional SMS edition](OffPay-1.0.5-release.apk): SMS confirmation is an explicit Settings opt-in. Payment handoff still works when SMS is blocked. [Checksum](OffPay-1.0.5-release.apk.sha256)
- [Compatibility edition](OffPay-1.0.5-compat.apk): SMS and notification permissions/receiver are omitted. Check the bank's SMS/app for payment outcomes. [Checksum](OffPay-1.0.5-compat.apk.sha256)

Both editions add prominent glass controls and spring press animations, a searchable directory of 714 NPCI-listed bank issuers (including Central Bank), and dialer/gallery/manual-entry fallbacks for declined permissions. Directory membership does not guarantee offline support for every bank or SIM. No overlay, call-state, end-call or audio-control permission is requested.

All 198 tests passed in each of debug, release and compatibility variants (594 test runs); lint, detekt, coverage, signature, alignment and merged-manifest checks passed. Physical-device payment/UI and Play Protect verification remain outstanding. An earlier APK was reported as “Harmful app blocked”; these changes do not establish Google clearance. Do not disable protections to install a still-flagged APK. See the source branch's `docs/PLAY_PROTECT_REVIEW.md` for evidence and the official appeal route.

Previous APKs remain for reference. Original licences and attribution notices remain in the source branch.
