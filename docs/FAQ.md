# FAQ

### Can I use OffPay if my phone refuses SMS or other permissions?

Yes, where the bank, SIM and system dialer support the payment flow. Phone,
Camera and Contacts are optional: use the system dialer, import a QR image,
or enter a recipient number. Blocked SMS does not block these actions.
No edition requires floating-overlay access.

The standard APK offers SMS confirmation as a Settings opt-in. The compatibility
APK omits SMS permission and the receiver entirely. Both editions share the
same package and signing certificate; choose one. About identifies your edition.

### Why can Android refuse SMS even after a permission request?

`RECEIVE_SMS` is hard restricted and can depend on installer allowlisting.
A runtime prompt cannot override that. Some sideloading protections also act
on declared SMS permissions. The compatibility edition avoids requesting that
capability; it does not bypass device protections or establish Google approval.

### What should I do if Play Protect says “Harmful app blocked”?

Do not disable protection to force installation. The exact warning/category
and APK hash matter, and the cause of the reported warning remains unconfirmed.
See [review notes](PLAY_PROTECT_REVIEW.md) for the official evidence and Google's
appeal process. A reduced permission list and passing tests are not proof that
Google has cleared an APK.

### Does optional SMS access read my inbox?

No. `READ_SMS` is never requested. When enabled and permitted, the app checks
new incoming messages only during an active payment window. It checks that
window before decoding the message, parses locally and stores a summary rather
than the raw body. Result notifications are optional; history remains available.

### What does Unverified mean?

OffPay handed the attempt to the dialer but cannot verify whether money moved.
This is the normal status without SMS capability, including the compatibility
edition and QR codes without an encoded amount. It is not a receipt or an
instruction to pay again. Check your bank's SMS, app or statement first.

When SMS confirmation is enabled for an eligible payment, the existing matching
window is used. If no matching confirmation arrives before its deadline, the
unconfirmed automatic-mode row is discarded; absence from history does not prove
a failed payment. A matching amount and timing alone are not cryptographic proof
of a particular payee: compare the bank's details before relying on a record.

### Are all listed banks guaranteed to support offline UPI?

No. The searchable directory contains NPCI-listed bank issuers, including Central
Bank of India, and is intended to avoid blocking bank selection. Availability
varies by bank, account, provider and SIM. Confirm the actual service through
setup and your bank. [Directory sources and limitations](OFFLINE_BANKS.md).

### Does the app see my UPI PIN?

No. Enter it only in the bank's own phone/USSD flow. OffPay does not request a PIN,
use an accessibility service, or read another app's screen.

### Which SIM will place the call?

The system dialer/default voice SIM controls the call. Select the SIM linked to
your bank account. Choosing a carrier in OffPay setup does not change Android's
default calling SIM, and OffPay does not read phone state to infer it.

### Does offline mean no mobile network is needed?

No. The app has no backend or internet permission, but the bank's USSD/IVR flow
still needs a compatible cellular connection. Jio's USSD limitations require a
supported alternative such as the bank's IVR service. Bank menus and timing can
change and require testing with the actual bank/SIM/device.

### Where are APKs and source available?

Source and signed development APK links are in this repository's GitHub Releases.
OffPay is not listed on Google Play. Store policy eligibility and Play Protect
classification are separate questions; neither is guaranteed by a source build.
See [README](../README.md), [testing](TESTING.md) and [security](../SECURITY.md).
