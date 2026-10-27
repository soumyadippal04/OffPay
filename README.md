# OffPay APK downloads

This branch distributes signed APKs. Source is on the main branch.

## OffPay 1.0.3

- Requests Phone, Camera, incoming SMS and Contacts consent once on first setup. Users must approve the Android prompts; declining does not block setup.
- Settings → Appearance → Dark mode switches between dark and light backgrounds and remembers the selection.
- Android 10+; ARM64 and 32-bit ARM; application ID com.offpay.app; version code 8.
- Signed with the same local development key as OffPay 1.0.2, not an established official publisher key.
- Debug/release builds, 164 tests per variant, lint, detekt, coverage, APK signature and alignment checks passed. Physical-device UI and payment checks remain outstanding.

Download OffPay-1.0.3-release.apk and verify against its .apk.sha256 file. OffPay 1.0.2 is retained below for reference. Licence and attribution notices remain in the source branch.
