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
