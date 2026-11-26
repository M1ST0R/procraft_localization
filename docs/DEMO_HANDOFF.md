# Maintainer handoff — Community Language Packs

**Target branch:** `main` (default)  
**Separate Russian edition:** `russian`  
**Upstream baseline:** ProCraft `v0.0.7-beta`  
**Purpose:** a working in-app demo, not a static mockup.

## What the demo delivers

Android and Desktop users can browse the curated community translations catalogue inside the built-in Language Packs manager (no separate plugin or marketplace), install and update verified translations, and create, edit, save, activate, import, export, share and delete local packs. Format v1 is data-only JSON, uses exact English UI text as keys and falls back to English for missing values. The built-in Russian dictionary remains selectable alongside installed custom packs.

- Android: **Settings → Language packs**
- Desktop: **Preferences → Language Packs**

## Acceptance test checklist

Run on Android and Windows Desktop where available:

1. Open the Language Packs manager.
2. Create a pack named `Español demo`, locale `es-ES`.
3. Search for `Gallery`, translate it as `Galería`, save; verify the visible Gallery label changes.
4. Search for `Layers`, translate it as `Capas`, save; verify it also changes.
5. Export the pack and confirm the exported file is `.procraft-lang`.
6. Import that file on the other platform and verify the same source strings display Spanish.
7. Switch English → built-in Russian → custom pack and verify active selection changes.
8. Try changing a placeholder source such as `Layer {count}` to a translation without `{count}`; it should fail validation. Preserve the placeholder and confirm validation succeeds.
9. Delete the custom pack and confirm a built-in choice becomes active.
10. Import a malformed, oversized or duplicate-ID pack and confirm a clear rejection with no silent overwrite.

## Source map

| Component | File |
|---|---|
| Catalog | `assets/localization/ui-catalog.json` |
| Android catalog copy | `android/app/src/main/assets/localization/ui-catalog.json` |
| Android pack UI/parser/storage | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt` |
| Android lookup | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt` |
| Desktop pack UI/parser/storage | `desktop/src/language_packs.rs` |
| Desktop lookup | `desktop/src/translation.rs` |
| Schema | `schemas/procraft-language-pack.schema.json` |
| Sample | `examples/ru-community.procraft-lang` |
| Hub catalogue | `community-translations/index.json` |
| Hub catalogue schema | `schemas/community-translation-index.schema.json` |

## Current CI and downloads

The workflows are:

- **CI:** Rust format, Clippy, tests and Android lint/unit/build checks.
- **Android APK:** ARM64 community APK.
- **Desktop builds:** Windows MSI + portable ZIP; Linux AppImage, DEB and tar.gz.

Live workflow pages (choose a successful run and its Artifacts section):

- [CI — coverage audit and Android checks](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Amain)
- [main Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Amain)
- [main Desktop builds](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Amain)

Artifacts expire after 30 days. A completed build proves that the workflow compiled and packaged its output; it does not replace manual smoke tests on a physical tablet and Windows/Linux systems.

## Deliberate MVP limitations

- Source text is the key in format v1. Upstream wording changes can require translation updates.
- Some generated text, plugin strings and UI text outside the wrappers may not be editable.
- The catalogue is curated, not an open upload portal or collaborative editor. Pack discovery and updates are user-initiated; update replacement requires confirmation and there is no background auto-update or cloud editing.
- The source catalog contains 1,377 entries; CI verifies Android/Desktop coverage, the intentional compact Desktop labels and the complete sample pack.
- [Readable full pack preview](../examples/ru-community-preview.json)
- The future stable-message-ID proposal is in `LOCALIZATION_PROPOSAL.md`; do not confuse it with the current v1 format.

## Security

See the separate [language-pack security report](../security/language-packs-2026-11-23/REPORT.md). This is a scoped static review, not a full app or dependency audit.
