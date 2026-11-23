# Security Review — Community Language Packs and Community Packaging

**Review date:** 2026-11-23
**Scope:** language-pack import/storage/activation/export paths, related Android manifest/FileProvider, CI permissions and community package isolation
**Assessment:** scoped static review. **No confirmed exploitable vulnerability** was found in the reviewed language-pack data path. This is not a full application or dependency audit.

## Executive summary

The `.procraft-lang` format is JSON translation data. The reviewed import paths do not evaluate translation values as scripts, load them as native libraries or register them as plugins. Import and validation are bounded and check metadata, IDs and placeholder parity.

A Desktop metadata-validation mismatch was identified and fixed: Desktop now enforces the metadata length limits defined by the schema and Android. The review also found that Windows MSI and Linux package identities initially matched upstream, risking replacement/conflicts when installing community editions beside upstream. Edition-specific product/package names, identifiers, install paths and portable naming were added. **Fresh builds and coexistence installation tests are still pending**; the fix is not considered fully verified until those tests pass.

No critical, high or medium exploitable vulnerability was confirmed within this scope. The conclusions apply only to reviewed files.

## Scope reviewed

- `desktop/src/language_packs.rs`: parsing, validation, storage, activation, import/export/share and manager UI.
- `desktop/src/translation.rs`: rendering-boundary lookup.
- `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt`: parser, validation, storage, activation, import/export/share.
- `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt`: translation lookup.
- `android/app/src/main/AndroidManifest.xml` and `android/app/src/main/res/xml/shared_files.xml`: backup and FileProvider configuration.
- `schemas/procraft-language-pack.schema.json`, shared catalog and sample pack.
- Android/Desktop build workflows and the community edition package scripts/WiX/Linux packaging files.

Threat cases considered: malformed/oversized/deep JSON, UTF-8/version errors, path traversal, excessive metadata or translation strings, placeholder mismatch, code execution through pack data, export/share exposure, installer identity collisions and workflow permission scope.

## Findings

### F-01 — Desktop metadata length validation did not fully match schema
**Severity:** Low (validation consistency/hardening) — **Fixed**

Desktop previously omitted or mismatched maximum lengths for `locale`, `app_version`, `catalog_version` and `base_locale`. It now validates locale ≤35 characters, app version ≤40, catalog version ≤80 and base locale ≤35. Regression assertions cover the limits.

### F-02 — No executable-content path found in language-pack import
**Severity:** Informational — **No issue found in reviewed path**

Packs deserialize into constrained data structures. Translation values are rendered as text; the importer does not evaluate scripts, load native libraries or register plugins. Desktop rejects unknown structure through Serde; Android checks allowed top-level fields.

This does not authenticate pack authors or protect against misleading/offensive translation text.

### F-03 — Pack IDs are validated before storage-path construction
**Severity:** Informational — **No issue found in reviewed path**

IDs are constrained to an allowed lowercase alphanumeric/dot/underscore/hyphen pattern and checked before filenames are constructed. Path-traversal-style IDs are rejected.

### F-04 — Import resource limits and placeholder validation
**Severity:** Informational — **Controls present**

Reviewed controls include a 5 MiB file limit, 20,000 string entries, source-key and value length limits, a 128 dynamic-template limit, Android JSON-depth validation and placeholder parity checks. These reduce resource-abuse risk but do not replace fuzzing/resource profiling.

### F-05 — Desktop pack writes are not atomic
**Severity:** Low (integrity/recovery hardening) — **Follow-up**

Desktop uses ordinary filesystem writes for pack files. An interrupted write could leave a damaged pack. Android uses `AtomicFile`. Consider temp-file plus atomic replacement on Desktop. No exploit was demonstrated.

### F-06 — Community desktop installers originally shared upstream package identities
**Severity:** High (installation isolation/compatibility; not a code-execution vulnerability) — **Fix implemented, verification pending**

The original Windows MSI UpgradeCode/install path and Linux package/application identity matched upstream ProCraft. This could cause a community install to replace/conflict with upstream or the other community edition.

Fixes now in the branch packaging code:
- Windows: separate ProductName, UpgradeCode, component GUID, registry key/install path and portable ZIP name for official, LangPack and Russian editions.
- Linux: separate package name, binary name, desktop-entry/app ID, icon names and AppImage/tar/deb basenames.
- Desktop workflows pass `EDITION=langpack` on `main` and `EDITION=ru` on `russian`.
- A `bash -n` packaging-script check was added.

Fresh Windows/Linux builds and install/upgrade/coexistence tests are still required before this finding can be closed as verified.

## Platform notes

### Android
- Strict UTF-8 decoding rejects malformed sequences.
- `format_version` must be integer 1; floating-point `1.0` is rejected.
- Pack writes use Android `AtomicFile`.
- Manifest has `allowBackup=false`.
- Language-pack sharing FileProvider is not exported and grants URI permissions.
- FileProvider configuration is scoped to cache exports.

### Desktop
- Bounded reading; invalid UTF-8 is rejected when converted to Rust String.
- `deny_unknown_fields` and integer format-version typing reject unsupported structure.
- Pack IDs are validated before constructing paths.
- Non-atomic pack writes remain a low-priority integrity hardening item.

### GitHub Actions
- Reviewed workflows request `contents: read`.
- Android signing secrets are only used on the trusted owner push/manual run condition on `russian`; other builds use debug signing.
- Repository-level branch protection, rulesets, collaborator permissions and secret settings were not available for review.
- Actions currently use version tags and a toolchain action `master` reference. Pinning third-party actions to immutable SHAs and toolchain actions to reviewed versions is a supply-chain hardening follow-up.

## Verification

Use the current workflow pages; older runs may be cancelled or may predate the package-identity fix:

- [main CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Amain)
- [main Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Amain)
- [main Desktop builds](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Amain)
- [russian CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Arussian)
- [russian Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Arussian)
- [russian Desktop builds](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Arussian)

Do not mark the package-identity fix verified until fresh Desktop builds succeed and manual install/upgrade/coexistence tests are completed.

## Follow-up

1. Add atomic replacement for Desktop language-pack writes.
2. Add fuzz/property-based parser and placeholder tests, including Android/Desktop acceptance parity.
3. Run a separate dependency/supply-chain audit and review third-party workflow actions.
4. Verify branch protection, trusted workflow access and signing-secret permissions with repository administration access.
5. Consider stable message IDs; format v1 English source keys can become stale when upstream wording changes.
6. Complete packaging tests: install each community MSI/DEB without replacing upstream; install both community editions together; verify upgrades and uninstall independently.

## Limitations

This is a scoped static review, not a penetration test, full app audit, dependency audit, runtime exploit exercise, fuzzing campaign or independent multi-reviewer review. No organization-level settings were inspected. No claims are made about unreviewed application code or dependencies.
