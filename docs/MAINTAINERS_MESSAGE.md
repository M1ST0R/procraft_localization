# Maintainer discussion — Russian community edition

Hello ProCraft maintainers,

This branch contains the standalone Russian-language community edition of ProCraft for Android, Windows and Linux. It is intentionally separate from the Community Language Packs demo on `main`.

The Android Russian UI is applied at the Jetpack Compose rendering boundary, and the Desktop translation is applied through the egui text-rendering helpers. Untranslated labels, plugin-provided strings and user content may still appear in English.

I would appreciate feedback on:
- whether the current Russian translation integration fits the upstream architecture;
- preferred terminology and any incorrect or inconsistent translations;
- the best way to submit translation-only changes without maintaining a long-lived full fork;
- whether a future stable-message-ID catalog should be shared with the separate Language Packs work on `main`.

The Russian branch does not contain the Language Packs manager. That working demo is maintained independently on `main`.

Thank you for your work on ProCraft.
