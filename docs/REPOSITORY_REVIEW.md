# Repository review — ProCraft Community Builds

**Review date:** 2026-11-23  
**Scope:** public repository structure, branches, documentation, GitHub Actions, community package identity and Language Pack integration. This is not a full application audit.

## Overall assessment

The repository now has exactly two intended branches:

- `main`: Community Language Packs demo.
- `russian`: Russian-only community edition.

The default branch is `main`. The repository branch list was re-checked after cleanup; no temporary audit/fix/demo refs remain.

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
| Russian Clippy baseline failed on duplicate match arms, collapsible ifs and unnecessary dereference | Medium (CI reliability) | Translation and gallery code fixed in russian; new CI pending |
| Russian docs described old branch/workflow/release state and confused the feature availability | High (documentation correctness) | README, maintenance guide, maintainer message and language-pack note corrected |
| CI job label implied Clippy/tests while it actually ran Android/Gradle validation | Medium (clarity) | Renamed to Android native build/lint/unit-test job |

## Workflow structure

Both branches contain exactly three workflow files:

- `.github/workflows/ci.yml`
- `.github/workflows/android-apk.yml`
- `.github/workflows/desktop-build.yml`

The workflows request read-only `contents` permissions. Checkout steps disable credential persistence. Third-party Actions are pinned by full commit SHA. The Android signing secrets are restricted to the trusted owner-triggered signing path; other APK builds use debug signing.

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

## Current verification

Use the live [main Actions](https://github.com/M1ST0R/procraft_localization/actions?query=branch%3Amain) and [russian Actions](https://github.com/M1ST0R/procraft_localization/actions?query=branch%3Arussian) pages. Older successful artifacts predate parts of the packaging/supply-chain changes and are not proof of the current source. The latest runs must pass before publishing install links.
