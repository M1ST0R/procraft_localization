# Repository review — ProCraft Community Builds

**Review date:** 2026-11-24
**Scope:** branch structure, repository tree hygiene, docs, Actions, release metadata, edition consolidation and packaging. This is not a full application or dependency security audit.

## Branch inventory and consolidation

- Default branch: main.
- Existing edition branches before this work: main (Lang Pack Demo) and russian (Russian-only snapshot).
- Temporary consolidation branch: edition-profiles.
- main and russian are diverged. Their merge base is 6ebe9e49d0055e827e4ffcd8679c93cfa4c96a7a; main is 258 commits ahead and russian is 57 commits ahead.
- The Russian branch's unique diff is mostly branch-specific CI/docs and packaging identity. Main already contains the important package-isolation and supply-chain controls and several code-level cleanup fixes. The Russian branch should be retired only after both profiles on the consolidation branch pass.
- GitHub branch metadata reports main, russian and edition-profiles as **unprotected**. Repository rulesets were not independently inspected. No required status checks or review gate is confirmed.

## Tree hygiene

| Branch | Tree entries | Files | Approx. tracked file size |
|---|---:|---:|---:|
| main | 1,077 | 924 | 39.47 MB |
| russian | 1,058 | 909 | 39.05 MB |

No obvious temporary, backup, prototype, or obsolete path names were found in the tree scan. The largest files are intentional documentation media:

- docs/images/demo.mp4 — about 3.99 MB
- docs/images/demo.webp — about 1.52 MB

The README's personal “Misc info for myself” note is removed in the consolidation branch. These media files are retained because they are the project walkthrough/demo assets, not build artifacts.

## Release 0.0.4

- Tag 0.0.4 points to 07f48c17ee32fd65244edee35c6b8c23e7a7259a.
- Release metadata confirms six platform binaries plus SHA256SUMS.txt: Android ARM64, Windows MSI + portable ZIP, Linux AppImage + DEB + tar.gz.
- GitHub Release API provides SHA-256 digests for the binary assets and includes a SHA256SUMS.txt asset. The manifest bytes could not be downloaded through the connector, so the manifest entries were not independently cross-checked against the API digests.
- The release is not marked immutable in GitHub metadata; tag protection was not verified.
- The APK is debug-signed; it is a community demo build.

## Build and verification controls

- Three workflows are retained: CI, Android APK and Desktop builds.
- External Actions are pinned by full commit SHA; checkout credential persistence is disabled; workflows request contents: read.
- Packaging scripts verify pinned tool downloads, and packages include SHA-256 manifests.
- Main commit 07f48c1 has successful CI and Desktop builds; Android APK build succeeded on be7e5f6, with Android sources unchanged in the later Desktop-only commit.
- Edition profile tests are in progress on draft PR #6. No profile build or installation test is marked verified until the latest matrix finishes.

## Current cleanup changes

- Consolidate the two edition workflows into profile-matrix builds from one source branch.
- Desktop Russian Edition uses Cargo --no-default-features, which compile-excludes the language-pack module/UI.
- Android uses an edition BuildConfig flag to hide the manager and disable custom pack activation in Russian Edition. The Android manager source is still compiled into the APK; complete source-level exclusion remains a possible follow-up.
- The obsolete russian branch cannot be deleted through the currently available GitHub action in this session. After profile builds and side-by-side checks pass, delete it in GitHub Branches settings. Git history remains available through the commits already in the repository.

## Remaining validation

1. Complete both profile CI matrices on edition-profiles.
2. Verify Android Lang Pack and Russian APKs on a device; confirm package IDs and settings visibility.
3. Verify Windows MSI/portable and Linux AppImage/DEB/tar.gz for both profiles.
4. Confirm separate install/upgrade/uninstall behavior alongside upstream ProCraft and the Lang Pack build.
5. Merge the verified profile branch to main, then delete the legacy russian branch.
6. Enable protection for main: require PRs and successful CI checks, block force pushes, and optionally require review. Protect release tags as well.
7. Re-run the security/dependency audit if the consolidation changes permissions, file handling or packaging.
