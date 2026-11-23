# Maintainer discussion — Russian community edition

Hello ProCraft maintainers,

This repository keeps two intentionally separate community editions:

- `russian` — the standalone Russian-language UI edition;
- `main` — the Community Language Packs demo with the in-app editor.

The Russian edition is available for Android, Windows and Linux. On Android, the Russian UI is applied at the Jetpack Compose rendering boundary; on Desktop, it is applied through the egui translation helpers. Untranslated labels, plugin-provided strings, generated text outside the dictionaries and user-created content can still appear in English.

The Russian branch has its own APK application suffix and separate Windows/Linux package identities, so it is intended to install alongside upstream ProCraft and the Lang Pack demo. The updated workflows also generate SHA-256 manifests for desktop artifacts and restrict release signing material to trusted owner-triggered runs on this branch.

I would appreciate feedback on:

- terminology or wording that is incorrect or inconsistent;
- missing UI strings or layout clipping;
- the preferred way to send translation-only changes upstream;
- whether upstream would prefer the Russian dictionary to remain community-maintained or eventually move into an upstream localization system;
- a future stable-message-ID catalog that could support both built-in translations and community packs without tying translations to English wording.

The Language Packs manager is **not** part of this branch. Its working demo and documentation are maintained separately on `main`.

Useful repository references:

- [Russian edition README](../README.md)
- [Russian edition build and maintenance guide](UPSTREAM_AND_ANDROID_BUILDS.md)
- [Community Language Packs feature overview](https://github.com/M1ST0R/procraft_localization/blob/main/docs/COMMUNITY_LANGUAGE_PACKS_DISCUSSION.md)
- [Scoped security review](https://github.com/M1ST0R/procraft_localization/blob/main/security/language-packs-2026-11-23/REPORT.md)
- [Supply-chain hardening](https://github.com/M1ST0R/procraft_localization/blob/main/security/SUPPLY_CHAIN.md)

Thank you for your work on ProCraft.
