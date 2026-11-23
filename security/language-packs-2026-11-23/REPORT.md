# Security Review — ProCraft Community Language Packs

**Review date:** 2026-11-23
**Review branch:** `main`
**Target branch:** `main`
**Reviewed source commit:** `19568d8408835d68b5d9436516175b49e4da00a2`
**Assessment:** Scoped static source/configuration review. No confirmed exploitable vulnerability found in the reviewed language-pack path.

## 1. Executive summary

The review focused on the import, validation, storage, activation, export and sharing of user-supplied `.procraft-lang` files in the Android and Desktop applications.

The format is data-only JSON. The reviewed import paths do not interpret translation values as scripts, load them as plugins, or execute commands from pack content. Both implementations apply an input-size limit, validate pack identifiers before using them in file paths, constrain entry and string sizes, and reject placeholder mismatches.

One consistency gap was found in Desktop metadata validation: its runtime validator did not enforce the schema's maximum lengths for `app_version`, `catalog_version` and `base_locale`. This was fixed before the demo was merged into `main`; regression checks were added. Android already enforced those limits.

**No critical, high or medium exploitable issue was confirmed in the reviewed scope.** This statement is limited to the files and checks below; it is not a claim that the entire application or its dependencies are vulnerability-free.

## 2. Scope

Reviewed source and configuration:

- `desktop/src/language_packs.rs` — Desktop pack parsing, validation, storage, activation, import/export and manager UI.
- `desktop/src/translation.rs` — Desktop rendering-boundary translation lookup.
- `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt` — Android pack parsing, validation, storage, activation, import/export and sharing.
- `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt` — Android translation lookup.
- `android/app/src/main/AndroidManifest.xml` and `android/app/src/main/res/xml/shared_files.xml` — backup and FileProvider configuration.
- `schemas/procraft-language-pack.schema.json` — documented file contract.
- `assets/localization/ui-catalog.json` and its Android mirror.
- `.github/workflows/android-apk.yml` and `.github/workflows/desktop-build.yml` — build permissions and trigger configuration.

Threat scenarios considered:

- Malformed, oversized or deeply nested JSON.
- Invalid UTF-8 and unsupported format versions.
- Path traversal or malicious pack IDs.
- Oversized metadata, translation entries or dynamic templates.
- Placeholder substitution inconsistencies.
- Unintended execution of imported content.
- Unintended exposure of exported files through Android sharing.

## 3. Findings

### F-01 — Desktop metadata length checks did not fully match the schema

**Severity:** Low — input-validation consistency / hardening
**Status:** Fixed in `main`

Before the fix, the Desktop runtime validator checked locale syntax, the pack ID, display name, author, entry count, key/value lengths and placeholder consistency, but did not enforce the schema's total `locale` length limit or the limits for `app_version`, `catalog_version` and `base_locale`.

The overall import limit was still 5 MB, and the missing field-length checks did not provide a demonstrated path to code execution or path traversal. However, the runtime contract was weaker than Android and the published schema.

**Change:** Desktop validation now enforces:
- `locale` ≤ 35 characters (in addition to locale syntax);
- `app_version` ≤ 40 characters;
- `catalog_version` ≤ 80 characters;
- `base_locale` ≤ 35 characters.

Regression assertions cover each limit in the Desktop language-pack tests.

### F-02 — No confirmed executable-content path in imported packs

**Severity:** Informational
**Status:** No issue found in reviewed path

Pack content is deserialized as JSON data into a constrained structure. Translation values are used as text; the reviewed importer does not evaluate scripts, load native libraries, or register imported packs as plugins. Unknown JSON fields are rejected by the Desktop structure, and Android explicitly checks the allowed top-level fields.

This does not protect users from misleading, offensive or poor-quality translations. Content authenticity and linguistic quality remain separate trust decisions.

### F-03 — File identity and path construction

**Severity:** Informational
**Status:** No issue found in reviewed path

Both implementations validate a pack ID against a restricted lowercase alphanumeric/dot/underscore/hyphen pattern before constructing the pack filename. IDs such as `../bad` are rejected by validation/tests. This prevents ordinary path traversal through a supplied pack ID in the reviewed pack storage path.

### F-04 — Resource-exhaustion controls

**Severity:** Informational
**Status:** Controls present

The reviewed code applies:
- a 5 MB input-file limit;
- a 20,000-entry limit;
- source-key and translated-value length limits;
- a 128-entry dynamic-template limit;
- Android JSON nesting-depth checks;
- placeholder parity checks.

These controls reduce the risk of pathological pack input. They are not a substitute for fuzzing or formal resource profiling.

## 4. Platform-specific security notes

### Android

- Imported and stored pack bytes are decoded with a strict UTF-8 decoder; malformed sequences are rejected.
- `format_version` must be an integer equal to `1`; a floating-point value such as `1.0` is rejected.
- Writes use Android `AtomicFile` to reduce the chance of leaving a partially written pack after interruption.
- The manifest sets `allowBackup=false`.
- The language-pack sharing FileProvider is not exported and grants URI permissions explicitly.
- Its configured path is limited to the cache `exports/` directory.

### Desktop

- Pack bytes are read through a bounded reader; conversion to Rust `String` rejects invalid UTF-8.
- Serde's `deny_unknown_fields` rejects unsupported pack fields; `format_version` is deserialized as an integer type.
- Pack IDs are validated before storage-path construction.
- Desktop pack files are currently written with ordinary filesystem writes rather than an atomic temp-file replacement. An interrupted write could leave a damaged pack; this is a data-integrity hardening opportunity, not a confirmed security exploit.

### CI configuration

The reviewed Android and Desktop build workflows request `contents: read`. This review did not verify repository/organization-level access rules, branch protections, collaborator permissions or secret access settings.

## 5. Verification status

Source-level regression coverage was added or retained for:

- malformed UTF-8 rejection;
- rejection of floating-point format versions;
- placeholder consistency and substitution behavior;
- pack ID and size limits;
- Desktop metadata length limits;
- Desktop translation coverage for the newly translated UI labels.

Build verification after moving the demo into `main`:

- Main-branch CI: [run 45212293121](https://github.com/M1ST0R/procraft_localization/actions/runs/45212293121) — the Rust format/whitespace step failed due to trailing whitespace in this report; it is being corrected and CI will rerun.
- Main-branch Desktop packaging: [run 45212218743](https://github.com/M1ST0R/procraft_localization/actions/runs/45212218743) — in progress.
- Main-branch Android APK: [run 45212218837](https://github.com/M1ST0R/procraft_localization/actions/runs/45212218837) — in progress.
- Russian-branch CI: [run 45212052420](https://github.com/M1ST0R/procraft_localization/actions/runs/45212052420) — Rust formatting failed against pre-existing workspace formatting; the branch CI check is being narrowed to whitespace in the diff.
- Russian-branch Android: [run 45212052434](https://github.com/M1ST0R/procraft_localization/actions/runs/45212052434) — in progress.

Do not treat pending jobs as passed. Re-check the latest Actions runs.

## 6. Residual risks and recommended follow-up

1. Consider using a temporary file plus atomic rename for Desktop pack writes, matching Android's stronger interruption behavior.
2. Add fuzz/property-based tests for JSON parser edge cases, placeholder extraction and the two platform implementations' acceptance parity.
3. Run dependency and supply-chain scanning separately; this source review did not audit every transitive dependency or third-party GitHub Action.
4. Verify repository-level branch protection, release-signing secret access and who can trigger trusted release workflows.
5. Consider a future stable message-ID catalog. Format v1 uses exact English text as the key, so upstream wording changes can make a translation entry stop matching.

## 7. Limitations

This was a scoped static review of the language-pack path and related configuration. It was not a penetration test, full application audit, dependency audit, runtime exploit exercise, fuzzing campaign or independent multi-reviewer verification. No organization-level GitHub settings were available to inspect. Conclusions apply only to the listed files and reviewed commit.
