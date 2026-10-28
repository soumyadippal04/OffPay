# Bank directory and offline availability

Reviewed on **28 October 2026**. OffPay stores a local directory so bank selection
works without internet access. The directory is shared by setup, Settings and the
home screen; selecting a bank does not link an account or enable a payment rail.

The directory contains **714 bank issuer entries**, including **Central Bank of
India**, plus an **Other bank (check availability)** option. These names come from
NPCI's UPI membership data. Inclusion is **not a claim of support for `*99#`, UPI
123PAY, every carrier, or OffPay's particular call flow**. Confirm the relevant
service with the bank and use the app's configuration checks with the linked SIM.
Do not send money merely to test whether an account or rail is supported.

## Sources and selection rules

1. [NPCI: UPI live members](https://www.npci.org.in/product/upi/all-members)
   and its [public directory response](https://www.npci.org.in/api/all-members-tab-details?product_name=UPI&tab_name=upi&page_no=1&page_size=1000),
   retrieved on the review date. The response contained 752 records: 642 `Issuer`,
   72 `PSP & Issuer`, 37 `PSP & PPI Issuer`, and one `Acquirer`. The directory
   includes the 714 bank issuers and excludes PPI wallet entries and the
   acquirer-only entry. It does not substitute the broader UPI list for an
   offline-support list. The response's older table-title count differs from its
   actual `totalCount`; the checked records, not that title, determine this snapshot.
2. [Central Bank of India: NUUP](https://www.centralbankofindia.co.in/en/nuup)
   documents mobile banking through `*99#` without mobile internet, for customers
   with a registered mobile number. Its page describes NUUP/MMID/MPIN services;
   this does not establish support for every UPI menu, UPI 123PAY service, or
   OffPay's exact scan-to-pay sequence. Bank registration and carrier requirements
   still apply.
3. [NPCI: UPI 123PAY live members](https://www.npci.org.in/product/upi-123pay/all-members)
   and its [public provider response](https://www.npci.org.in/api/all-members-tab-details?product_name=UPI-123Pay&tab_name=upi-123-pay&page_no=1&page_size=10)
   distinguish partner banks/TPAPs, service providers and payment methods. The nine
   rows reviewed list Bank of Baroda twice, Canara Bank, City Union Bank, IDFC First
   Bank, Jio Payments Bank, Punjab National Bank, PhonePe and UCO Bank. Some rows
   describe feature-phone app functionality rather than IVR. These provider rows
   are not an exhaustive list of customers' account banks and do not prove that
   OffPay's dialled service accepts every bank in this directory.

The former NPCI `/what-we-do/99/live-members` address returned the new JavaScript
site shell during review, not a readable current `*99#` membership table. We do not
claim to have verified an exhaustive current offline-bank list from that address.

The source responses used for this snapshot had these SHA-256 digests:

- UPI directory: `3e56857da96a40c8efaab222e4e19224d3dbd1f7de679faa0cba851a27ef5deb`
- UPI 123PAY provider directory: `f15830cee8ae3895c6362e8de64843f9c06a4479d426575c7dbb716129abe9ee`

## Maintaining the directory

`OfflineBankDirectoryData.kt` contains the reviewed names; `OfflineBankCatalog.kt`
provides lookup and search. Existing saved IDs (`sbi`, `hdfc`, `icici`, `axis`,
`kotak`, `pnb`, `bob`, `yes`, `idbi`, `canara`) remain unchanged. Central Bank of
India uses `central` and can also be found by `CBI`. Common bank abbreviations are
search aliases. Core display names retain their familiar capitalization. Where
NPCI supplies a former name in parentheses before a current name, the current
name is shown and the former name becomes a search alias. Other entries preserve
NPCI's supplied names.

When refreshing the snapshot, verify the source and filter again, retain existing
IDs, review mergers and renamings, and update the review date and source counts.
Never silently map an unrecognized saved ID to a different bank. Availability can
change between releases; the Other option lets a user proceed with a bank not yet
listed while making clear that support must be checked.

`OfflineBankCatalogTest` covers saved-ID continuity, abbreviation and multiword
search, unknown-bank handling, unique identities and completeness against this
reviewed issuer snapshot. These checks validate the directory, not bank or carrier
payment availability.
