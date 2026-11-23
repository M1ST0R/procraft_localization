# Security Review Actions — Community Language Packs

| Item | Priority | Status | Notes |
|---|---|---|---|
| Align Desktop metadata length validation with the schema and Android | Low | Fixed | Added limits for locale, app_version, catalog_version and base_locale plus regression checks |
| Strict UTF-8 decoding on Android import and stored-pack read | Hardening | Present | Malformed byte sequences are rejected |
| Restrict format_version to integer 1 on Android | Hardening | Present | Floating-point 1.0 is rejected |
| Enforce pack ID constraints before constructing storage paths | Hardening | Present | IDs outside the allowed pattern are rejected |
| Bound imported file size and translation-entry resources | Hardening | Present | 5 MB and documented entry/string/template limits |
| Keep Android FileProvider non-exported and scoped to cache exports | Hardening | Present | Manifest and shared_files.xml reviewed |
| Use atomic replacement for Desktop pack writes | Low | Follow-up | Desktop currently uses ordinary filesystem writes |
| Add fuzz/property-based parser and placeholder tests | Medium | Follow-up | Test Android/Desktop parser parity |
| Verify GitHub branch protections and release secret access | Owner action | Pending | Requires repository/organization administration access |
| Run full dependency/supply-chain and independent security review | Follow-up | Not performed | Outside this scoped source review |
