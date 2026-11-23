# Repository review — ProCraft Community Builds

Review scope: public repository structure, branch/documentation clarity, GitHub Actions configuration, community package identity and the Language Pack feature. This is not a full application code audit.

## Overall assessment

The two-edition direction is clearer: `main` is the Lang Pack demo and `russian` is the Russian-only build. The default branch is `main`. The remaining clarity issue is old refs: GitHub still lists temporary branches from the earlier workflow; they need deletion by the repository owner.

## Findings and corrections

| Finding | Priority | Status |
|---|---|---|
| README top sections still referred to old demo branch names, obsolete run IDs and a removed Release workflow | High (documentation correctness) | README introductions rewritten for the two current editions; docs updated |
| Windows MSI and Linux package identities originally matched upstream, so installing the community package could replace or conflict with upstream | High (install isolation) | Edition-specific product/package IDs, UpgradeCodes, install folders, app IDs and portable filenames added; fresh builds required to verify |
| Desktop workflows did not pass an explicit edition to packaging | High | `main` passes `langpack`; `russian` passes `ru`; packaging shell syntax check added |
| Russian Clippy failed on existing indexing/chunks warnings in straight-alpha conversion | Medium (CI reliability) | Russian code changed to array-chunk iteration matching current main; verify fresh CI |
| Release workflow was removed but documentation still linked to it | Medium | Docs now state that community builds are distributed as temporary Actions artifacts |
| Maintainer message described only a proposal although the feature was implemented | Medium | Maintainer message updated to request feedback on the working MVP and future stable IDs |
| Old feature/docs pages had stale branch/workflow names and catalog counts | Medium | README, guide, behavior docs, handoff, proposal and build instructions revised |
| Temporary branches remain on GitHub | Cleanup | Owner action: delete obsolete refs after verifying they are no longer needed |

## Branch layout

- `main`: Community Language Packs demo.
- `russian`: Russian-only community edition.
- Default branch: `main`.

Old audit/fix/demo branches are not part of the intended public workflow and should be removed by the owner. Do not delete `main` or `russian`.

## Workflow review

The three retained workflows are CI, Android APK and Desktop builds. They run on the intended edition branches. Actions permissions are read-only for contents in the reviewed workflows. Android signing secrets are restricted by the workflow condition to the trusted owner on the Russian branch; other builds use debug signing. Artifacts are temporary (30-day retention), not permanent releases.

## Packaging isolation fix

Windows now selects an edition-specific MSI ProductName, UpgradeCode, component GUID, registry key, installation folder and portable ZIP prefix. Linux selects edition-specific package names, binary names, desktop entry IDs, icon names and AppImage/tar/deb basenames. The official package mode remains the default when no `EDITION` is supplied. Fresh Desktop builds and installation/coexistence tests are required before declaring this fix verified.

## Remaining cleanup/hardening

1. Delete temporary refs so only `main` and `russian` remain.
2. Verify Windows MSI installs both editions alongside upstream and each other; uninstall/upgrade each independently.
3. Verify Linux DEB package names, desktop entries and icons coexist; test AppImage/tar extraction.
4. Consider pinning GitHub Actions to immutable commit SHAs instead of major tags, and replace toolchain `master` references with reviewed version pins where practical.
5. Keep the separate security report scoped and updated; this review does not claim a full app audit.

## Current build verification

Use the live [main Actions](https://github.com/M1ST0R/procraft_localization/actions?query=branch%3Amain) and [russian Actions](https://github.com/M1ST0R/procraft_localization/actions?query=branch%3Arussian) pages. Do not treat older artifacts as proof that the newly changed packaging identity code has been built. Fresh workflow success plus manual install/coexistence tests are required.
