# Repository review — ProCraft Community Builds

**Review date:** 2026-11-24  
**Scope:** both maintained branches, repository structure, documentation, GitHub Actions, release assets, package identity and Community Language Packs integration. This is not a full application or dependency audit.

## Overall assessment

The repository has exactly two long-lived branches:

- `main`: Community Language Packs demo, including the built-in Russian translation and the in-app custom-pack manager/editor.
- `russian`: independently maintained Russian-translation edition, without the Community Language Packs manager/editor.

The default branch is `main`. The rejected build-profile proposal in [PR #6](https://github.com/M1ST0R/procraft_localization/pull/6) was closed without merging on 2026-11-24. Its temporary `edition-profiles` branch was deleted. The live branch list was checked after cleanup and contains only `main` and `russian`.

## Tree hygiene

| Branch | Tree entries | Tracked files | Approx. tracked size |
|---|---:|---:|---:|
| `main` | 1,077 | 924 | 39.48 MB |
| `russian` | 1,058 | 909 | 39.05 MB |

A path scan found no tracked backup, temporary, build-output, prototype, cache or generated-junk files. Large bundled assets (brush sets, fonts, environment HDRs and the walkthrough media) are intentional application content.

## Resolved findings

| Finding | Priority | Status |
|---|---|---|
| README/docs referred to obsolete branch names, run IDs and removed Release workflow | High (documentation correctness) | Corrected in main and russian |
| Windows MSI/Linux package identities matched upstream | High (installation isolation) | Edition-specific identities added; fresh builds and manual coexistence verification pending |
| Linux DEB failed on unquoted nfpm YAML variable scalars and missing package-name substitutions | High (build correctness) | Quoted YAML scalars and substitutions added |
| Linux AppImage build consumed mutable `continuous` tool/runtime binaries | High (supply chain) | AppImage tool/runtime now version-pinned and SHA-256 verified |
| Third-party GitHub Actions used mutable major tags | High (supply chain) | Actions pinned to full immutable commit SHAs on both branches |
| Build tool installs were version ranges or mutable downloads | Medium/High | cargo-ndk and nfpm pinned; nfpm archive checksum verified; appimagetool/runtime checksums verified |
| Checkout left token credentials in local Git config | Medium | Checkout uses `persist-credentials: false` |
| Main security report had trailing whitespace | Low (CI hygiene) | Fixed |
| Russian Clippy baseline failed on duplicate match arms, collapsible ifs and unnecessary dereference | Medium (CI reliability) | Translation/gallery cleanup completed; strict Desktop and Android-target Clippy checks added to Russian CI; current run pending |
| Russian docs described old branch/workflow/release state and confused the feature availability | High (documentation correctness) | README, maintenance guide, maintainer message and language-pack note corrected |
| CI job label implied Clippy/tests while it actually ran Android/Gradle validation | Medium (clarity) | Renamed to Android native build/lint/unit-test job |
| Main Clippy flagged an unused lowercase-heading helper | Low (dead code) | Removed unused helper; latest Clippy rerun pending |
| APK package identity was not checked in workflow validation | Medium (install/update safety) | Main APK verifies `.ru.demo`; Russian workflow verifies `.ru` and `.ru.demo` APKs when both are produced |


## Live audit snapshot (2026-11-24)

The repository tree was re-scanned through the GitHub Git Trees API after cleanup:

- `main`: commit `56b49ba1a485d11a5e3dedee874d59b75c525d22`, 1,077 tree entries / 924 tracked files.
- `russian`: commit `844c3628810f8d667cdd943e04c36ba0d151a23e`, 1,058 tree entries / 909 tracked files.
- Branch list: exactly `main` and `russian`; no additional branch remains.
- Both trees retain exactly the three edition workflows (CI, Android APK, Desktop builds). The one-time cleanup workflow is no longer tracked.
- Automated filename scan found no tracked backup, temporary, log, cache, bytecode, prototype, or build-output artifacts.
- The branch-only files on `main` are the language-pack manager, catalog, tests, demo documentation/sample, coverage audit and security review materials. `russian` has no demo-only files from that list.

This is a tree hygiene and repository-structure check, not a line-by-line audit of the upstream ProCraft codebase.

## Workflow structure

Both branches contain exactly three workflow files:

- `.github/workflows/ci.yml`
- `.github/workflows/android-apk.yml`
- `.github/workflows/desktop-build.yml`

The workflows request read-only `contents` permissions. Checkout steps disable credential persistence. Third-party Actions are pinned by full commit SHA. Push and PR triggers are branch-specific (`main` checks main; `russian` checks russian). The Android signing secrets are restricted to the trusted owner-triggered signing path; other APK builds use debug signing. Main CI runs strict workspace Clippy; Russian CI runs strict desktop and Android-target Clippy. APK workflows also assert the expected application ID before uploading.

## Repository governance

- Branch metadata reports both `main` and `russian` as unprotected.
- The GitHub rulesets endpoint returned an empty list. The branch-protection endpoint was inaccessible to this connection (403), so required checks/reviews cannot be independently confirmed beyond the unprotected metadata.
- Release tag `0.0.4` points to commit `07f48c17ee32fd65244edee35c6b8c23e7a7259a`. GitHub marks the release non-immutable; tag protection was not verified.

## Packaging isolation

Windows MSI now has edition-specific product name, UpgradeCode, component GUID, registry key, install folder and portable ZIP prefix. Linux packages use edition-specific package names, binary names, desktop entry IDs, icon names and AppImage/tar/DEB names.

The implementation is present in the two branches. Do not mark the fix fully verified until fresh Desktop workflows finish successfully and manual side-by-side installation, upgrade and uninstall tests have been performed.

## Remaining work

1. Verify fresh Windows MSI and Portable builds from both branches.
2. Verify fresh Linux DEB, AppImage and tar.gz builds from both branches.
3. Manually install upstream, Lang Pack and Russian desktop versions together; verify upgrades and uninstalls independently.
4. Add atomic replacement for Desktop pack writes.
5. Add parser/placeholder fuzz tests and Android/Desktop acceptance parity tests.
6. Review repository rulesets, branch protection and secret access with owner-level admin permissions.
7. Run a broader dependency audit and independent application security review.

## Release and current verification

The published [ProCraft Community 0.0.4 release](https://github.com/M1ST0R/procraft_localization/releases/tag/0.0.4) is the `main` Lang Pack Demo build based on upstream `v0.0.7-beta`. It contains six platform binaries (Android ARM64, Windows x64 MSI + portable ZIP, Linux x86_64 AppImage + DEB + tar.gz) plus `SHA256SUMS.txt`. Release API metadata exposes SHA-256 digests, but the manifest contents could not be independently retrieved for cross-checking. The release is not marked immutable. It is not a Russian Edition release; `russian` builds are distributed through their own Actions artifacts.

Latest verification is in progress. On `main`, translation coverage is green; the Clippy rerun after removing the unused helper, Desktop tests, Android unit tests and APK/package-ID validation are still running. On `russian`, both Clippy checks passed and the Android APK workflow succeeded; the remaining Android lint/unit/page-alignment checks and Desktop Windows/Linux packaging are still running. Use the live [main Actions](https://github.com/M1ST0R/procraft_localization/actions?query=branch%3Amain) and [russian Actions](https://github.com/M1ST0R/procraft_localization/actions?query=branch%3Arussian) pages for current results. Older successful artifacts predate these workflow changes. A successful run proves only the checks and package steps that actually ran. It does not replace physical-device smoke tests or side-by-side installation/upgrade/uninstall checks on Windows and Linux. Confirm the latest run for the branch and commit before publishing new workflow artifacts.
