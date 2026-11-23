# Security review actions — Language Packs and Community Packaging

| Item | Priority | Status | Notes |
|---|---|---|---|
| Align Desktop metadata length validation with schema/Android | Low | Fixed | Locale, app_version, catalog_version, base_locale plus tests |
| Strict UTF-8 import/read on Android | Hardening | Present | Invalid byte sequences rejected |
| Require integer format_version=1 on Android | Hardening | Present | Floating 1.0 rejected |
| Validate pack ID before path construction | Hardening | Present | Traversal-like IDs rejected |
| Bound file size, entries, string lengths and template count | Hardening | Present | Limits and placeholder checks |
| Android FileProvider non-exported and scoped to cache exports | Hardening | Present | Manifest and XML reviewed |
| Atomic Desktop pack-file replacement | Low | Follow-up | Ordinary filesystem writes currently used |
| Fuzz/property tests and Android/Desktop parser parity | Medium | Follow-up | Not performed |
| Windows MSI and Linux package identity isolation | High compatibility | Implemented; build/manual verification pending | Edition-specific product/package IDs, install paths, app IDs and portable names |
| Fresh Windows/Linux builds after identity and supply-chain fixes | Build validation | In progress | Latest runs are linked from the live Desktop workflow pages; do not use older artifacts as proof |
| Verify coexistence/upgrades/uninstall of community and upstream editions | Manual validation | Pending | Required before closing F-06 on clean Windows/Linux test systems |
| Pin Actions/toolchain to immutable reviewed versions | Supply-chain hardening | Fixed | All three workflows use full commit SHAs for external Actions; no mutable master/tag refs remain |
| Verify rulesets, branch protection, secret permissions | Owner action | Pending | Requires repo administration |
| Restrict Russian APK signing secrets to trusted owner push/manual runs | High | Fixed | PR/other triggers use debug-signed APK without release secrets |
| Full dependency audit and independent security review | Follow-up | Not performed | Outside scope of this review |


Supply-chain pinning and binary checksums are documented in [SUPPLY_CHAIN.md](../SUPPLY_CHAIN.md).
