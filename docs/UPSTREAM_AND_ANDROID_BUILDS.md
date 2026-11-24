# Building, testing and updating ProCraft Community Builds

The goal is one maintained source branch with two edition profiles. The consolidation work is currently on the temporary edition-profiles branch and has not been merged into main yet.

## Edition profiles

See [Edition Profiles](EDITION_PROFILES.md) for the exact Cargo, Android and packaging switches.

- **Lang Pack Demo**: built-in Russian translation and the Community Language Packs editor.
- **Russian Edition**: built-in Russian translation; the manager is disabled.

Do not make parallel feature changes on the legacy russian branch. Its unique commits have been reviewed against main; the profile branch is the consolidation candidate.

## Current release

The latest release is [0.0.4](https://github.com/M1ST0R/procraft_localization/releases/tag/0.0.4), based on ProCraft 0.0.7-beta. It contains the Lang Pack Demo builds and checksum manifest.

## CI and artifacts

- [CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml)
- [Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml)
- [Desktop packages](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml)

Download artifacts only from successful runs of the commit/profile being tested. Workflow artifacts are temporary (30-day retention) and are not a replacement for tagged releases.

## Updating from upstream

Use the consolidated source branch after it is merged into main. Do not replace community files with a fresh upstream checkout.

```bash
git remote add upstream https://github.com/artcraft-android/procraft.git
git fetch upstream
git switch main
git merge upstream/main
```

Resolve conflicts by preserving upstream functional changes, then update translation dictionaries/catalog and pack preview as needed. Run both edition profiles in CI and verify the packaging outputs before publishing.

The current upstream baseline in release 0.0.4 is ProCraft v0.0.7-beta. Update baseline references in documentation as part of any upstream sync.
