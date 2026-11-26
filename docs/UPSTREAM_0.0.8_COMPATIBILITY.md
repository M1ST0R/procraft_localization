# Upstream v0.0.8-beta compatibility review

**Reviewed:** 2026-11-26  
**Upstream target:** [v0.0.8-beta](https://github.com/artcraft-android/procraft/releases/tag/v0.0.8-beta)  
**Previous baseline:** [v0.0.7-beta](https://github.com/artcraft-android/procraft/releases/tag/v0.0.7-beta)

## Status

**The upstream v0.0.8-beta update is not merged into either maintained branch.** This report is a static Git-tree compatibility review, not a build or runtime test against v0.0.8.

The upstream release contains **263 commits** after v0.0.7-beta. Comparing the fork's current trees against the upstream v0.0.8-beta tree shows substantial divergence:

| Branch | Fork blobs | Upstream blobs | Identical blobs | Changed blobs | Missing upstream blobs |
|---|---:|---:|---:|---:|---:|
| `main` | 928 | 1,173 | 574 | 268 | 331 |
| `russian` | 909 | 1,173 | 575 | 267 | 331 |

These counts compare blob SHA-256-equivalent Git object IDs at the path level. A changed file may contain intentional community localization changes or older upstream code; the counts alone do not classify each change as a conflict.

## Brush engine / plugin compatibility risks

The following upstream files differ from the fork's v0.0.8-beta target:

- Android `BrushLibrary.kt`, `BrushStudio.kt`, `StrokeSound.kt`
- Core brush loading, libraries, brush pictures, stock brushes, and plugin brush/commands/panels/runtime
- Engine brush, brush-file/library, stroke, studio and related rendering/serialization modules
- Krita brush plugin code and plugin manifest/source list

The v0.0.8-beta tree also contains files missing from both maintained branches, including:

- `android/.../ui/PluginRing.kt`
- Core `brush_hold.rs` and `plugin_icon.rs`
- New bundled plugins: brush-extras, colour-tools, edit-layout, infinite-plugins, interface-look, layer-toolbox, pop-up-palette, storage-folder, view-tools
- Additional brush import/export, hold, sound, cursor and plugin tests
- Additional GPU filter shaders and tests

The fork currently contains custom localization wrappers and translation editor changes in Android/Desktop UI files. Those must be preserved while merging upstream changes; replacing files wholesale would remove community localization behavior.

## Compatibility conclusion

- The current fork CI can validate the fork against its own source tree, but **does not establish compatibility with upstream v0.0.8-beta**.
- A successful current Android/Desktop build is not proof that the upstream brush engine update has been merged or tested.
- Both `main` and `russian` need an upstream merge/rebase review. They are separate editions and must retain their edition-specific localization.
- The source-string catalog and `catalog_version` need to be regenerated/reviewed after upstream changes. Existing packs must then be audited for removed, changed and new source strings.

## iOS packaging status

The fork contains an `ios/` source tree, but neither maintained branch currently has an iOS build workflow. The unsigned `.ipa` mentioned in the upstream v0.0.8-beta announcement is an upstream asset, not a package produced by this fork. Do not list iPadOS builds as available from this fork until a macOS workflow, signing/distribution approach and device smoke tests exist.

## Required merge and regression checks

1. Merge upstream v0.0.8-beta into `main`, preserving community translation editor, catalog, Russian dictionary, Hub and security/CI changes.
2. Merge the same upstream release into `russian`, preserving the Russian-only build and its dictionary.
3. Reconcile Android and Desktop localization wrappers with upstream UI changes; update the source catalog and pack catalog revision.
4. Run CI, Android APK/lint/unit tests and Windows/Linux packaging on both branches.
5. Smoke-test brush import (including Krita), default brushes, brush-library loading, brush stroke latency, pressure/smoothing, dual/mixing brushes, brush sounds, brush export/import and plugin loading.
6. Test Android device behavior on at least one device with a recent Adreno GPU because upstream specifically reports fixes for dual-brush rendering on Adreno.
7. Only then publish a release based on v0.0.8-beta.

## Why the merge is not performed in this review

The available GitHub connector can inspect repository trees, files, commits and Actions, but it does not provide a local clone/checkout/merge workflow or an import of the upstream release tag into this repository. The local execution environment also cannot resolve GitHub for a clone/download. Performing a fake merge commit or overwriting the fork tree with upstream would discard localization changes and is unsafe. The merge therefore remains explicitly blocked rather than being reported as complete.


## Safe local merge procedure

The merge cannot be performed from this session: GitHub's connector exposes read/write file and ref operations but no checkout/merge workflow, and the execution environment cannot resolve GitHub for a clone. Creating a temporary branch is also avoided because branch deletion is unavailable and the repository is intentionally kept to exactly `main` and `russian`.

The same checked-in helper, [`tools/merge_upstream_release.sh`](../tools/merge_upstream_release.sh), is present on both maintained branches. It fetches the exact upstream release tag and stages a no-commit merge only on the selected clean `main` or `russian` branch. It does not push or create temporary branches, and it does not auto-resolve conflicts. Both branch CI workflows are configured to run `bash -n` on the helper. Run it locally, resolve conflicts while preserving localization, execute the listed checks, and commit only after validation.

## Current Actions verification (2026-11-26; code snapshot)

- [Main CI run 45881028803](https://github.com/M1ST0R/procraft_localization/actions/runs/45881028803) on code commit `f406fd1`: all four jobs passed (Clippy, Android unit tests, translation/Hub validation, Desktop translation/runtime/core tests).
- [Main Desktop build 45881028823](https://github.com/M1ST0R/procraft_localization/actions/runs/45881028823) on the same commit: Windows MSI/portable ZIP and Linux AppImage/DEB/tar.gz packaging, checksum generation and upload passed.
- On `russian`, [Android APK run 45880975875](https://github.com/M1ST0R/procraft_localization/actions/runs/45880975875) on commit `16a569d` passed. [Russian CI run 45880975817](https://github.com/M1ST0R/procraft_localization/actions/runs/45880975817) was still in progress when this snapshot was written. The previous full Russian CI, Android and Desktop baseline succeeded at commit `844c3628` on 2026-11-24.

These runs validate the fork's current code against its previous upstream baseline. They are **not** tests of the v0.0.8-beta merge.
