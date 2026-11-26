# ProCraft Community Builds

This repository maintains two community editions of [ProCraft](https://github.com/artcraft-android/procraft). This page is navigation; the main feature guide and build guide contain the maintained instructions.

## Editions

- **[main — Community Language Packs](https://github.com/M1ST0R/procraft_localization/tree/main):** built-in English and Russian, the integrated Language Manager, editable community packs and the in-app Hub.
- **[russian — Russian Edition](https://github.com/M1ST0R/procraft_localization/tree/russian):** standalone Russian UI build without the Language Manager.

Keep the editions separate. Do not copy the Lang Manager into `russian` or merge the two community branches together.

## Downloads and CI

- [Community releases](https://github.com/M1ST0R/procraft_localization/releases)
- [Upstream ProCraft releases](https://github.com/artcraft-android/procraft/releases)
- [main CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Amain)
- [main Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Amain)
- [main Desktop builds](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Amain)
- [russian CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Arussian)
- [russian Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Arussian)
- [russian Desktop builds](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Arussian)

Temporary Actions artifacts expire after 30 days. Use only successful runs.

## Language Manager — main only

- [User, translator and maintainer guide](docs/COMMUNITY_LANGUAGE_PACKS_GUIDE.md)
- [Available Hub catalogue](community-translations/index.json)
- [Japanese community pack](community-translations/ja-jp.procraft-lang)
- [Source-string coverage audit](tools/check_translation_coverage.py)
- [Hub/package validation](tools/check_community_hub.py)

Language packs are built into the app's Manager. There is no separate translation plugin or marketplace.

## Build and upstream maintenance

- [Builds, signing and safe upstream updates](docs/UPSTREAM_AND_ANDROID_BUILDS.md)
- [ProCraft upstream repository](https://github.com/artcraft-android/procraft)
- [ProCraft Discord](https://discord.gg/gypGes3Uca)

## Security and attribution

- [Supply-chain hardening](security/SUPPLY_CHAIN.md)
- [Language-pack security report](security/language-packs-2026-11-23/REPORT.md)
- [Security remediation checklist](security/language-packs-2026-11-23/ACTIONS.md)
- [Project attribution](ATTRIBUTION.md)

