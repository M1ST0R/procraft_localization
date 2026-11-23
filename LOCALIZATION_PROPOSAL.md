# Proposal: Future localization architecture for ProCraft

**Status:** future architecture proposal. The current Community Language Packs MVP already exists on the `main` branch.  
**This branch:** `russian` is the separate Russian-only community edition.  
**Target architecture:** Android (Kotlin/Jetpack Compose) and Desktop (Rust/egui).

## Current implementation versus future proposal

Do not treat this document as the description of the currently shipped file format or user workflow.

The working `main`-branch MVP lets users create, edit, save, activate, import, export, share and delete data-only `.procraft-lang` packs. Format v1 uses exact English UI strings as keys, and missing translations fall back to English.

- [Current feature walkthrough](https://github.com/M1ST0R/procraft_localization/blob/main/docs/COMMUNITY_LANGUAGE_PACKS_DISCUSSION.md)
- [Current user and translator guide](https://github.com/M1ST0R/procraft_localization/blob/main/docs/COMMUNITY_LANGUAGE_PACKS_GUIDE.md)
- [Current format and limitations](https://github.com/M1ST0R/procraft_localization/blob/main/docs/COMMUNITY_LANGUAGE_PACKS.md)
- [Current security review](https://github.com/M1ST0R/procraft_localization/blob/main/security/language-packs-2026-11-23/REPORT.md)

This proposal describes a possible next step: stable message IDs, plural forms and better catalog compatibility. It is not implemented by the current v1 pack format.

## Why stable IDs may help

Format v1 uses the exact English source text as the key. Case, punctuation and spaces are significant. If upstream changes a label, a translation may stop matching that text.

A stable message ID would separate translation identity from English wording. Translators could keep their translation while maintainers update the English source.

## Proposed direction

- Give each translatable UI message a stable ID.
- Keep English as the built-in fallback.
- Support named placeholders and plural forms.
- Use the same lookup/validation semantics on Android and Desktop.
- Distinguish new, changed, missing and removed catalog entries.
- Preserve existing user translations when source wording changes.
- Keep packs data-only; they must never execute code or gain plugin permissions.

A shared Rust resolver is one possible implementation boundary. Platform-only UI strings can still use the same catalog contract.

## Proposed migration stages

1. Inventory visible Android, Desktop and shared-core UI strings.
2. Introduce stable IDs while retaining a migration map from v1 English keys.
3. Implement shared validation and lookup semantics.
4. Add catalog compatibility reports for translators.
5. Add tests for plural forms, placeholders and Android/Desktop parity.
6. Define whether the new format should coexist with v1 or use a one-time migration.

## Compatibility and security requirements

- Version the serialized file format independently from the catalog revision.
- Validate IDs, locale tags, metadata, file size, entry counts, nesting and placeholders.
- Reject malformed or incompatible files with useful errors.
- Missing translations must never make the app unusable; use English fallback.
- Imported data must not execute code, load native libraries or access plugins, files or network.
- Preserve untranslated entries when exporting a pack.

## Scope boundaries

The `main` branch owns the active Language Packs MVP and its future evolution. The `russian` branch remains the standalone Russian edition and should receive only Russian translation, upstream maintenance and packaging fixes unless the two-branch design is explicitly changed.

This proposal is a discussion document. Stable IDs, plural forms and catalog migration should not be presented as available features until implemented and tested.
