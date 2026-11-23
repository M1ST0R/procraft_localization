# Proposal: Future localization architecture for ProCraft

**Status:** future architecture proposal; current Lang Pack MVP already exists on `main`  
**Target:** Android (Kotlin/Jetpack Compose) and Desktop (Rust/egui)  
**Goal:** maintainable community translations without a separate full application fork for every language.

## Current implementation versus proposed design

The working `main`-branch MVP uses **format v1** with exact English UI strings as keys, because it is wired into the current Android and Desktop text-rendering boundaries. Users can create/edit/save/activate/import/export/share/delete packs today.

This document describes the longer-term design. It is not a description of the currently serialized v1 format.

Current implementation details: [format and behavior](docs/COMMUNITY_LANGUAGE_PACKS.md), [user guide](docs/COMMUNITY_LANGUAGE_PACKS_GUIDE.md), [public feature walkthrough](docs/COMMUNITY_LANGUAGE_PACKS_DISCUSSION.md).

## Why a future stable-ID design may help

English source wording can change between upstream versions. In format v1, changing the source phrase may break matching with an existing translation. Stable IDs would separate translation identity from English wording.

## Proposed direction

- Keep English as the built-in fallback.
- Assign stable IDs to UI messages.
- Support named placeholders and plural forms.
- Make Android and Desktop use the same lookup semantics.
- Preserve user translations when catalog entries are added or changed.
- Show missing, changed and removed entries for translator review.
- Keep packs data-only, without plugin or executable permissions.

A shared Rust resolver is one possible implementation boundary, with platform-specific UI strings supported by the same catalog contract.

## Proposed user flow

1. Select a language or import a pack.
2. Review missing and changed entries.
3. Translate or edit entries.
4. Validate placeholders and catalog compatibility.
5. Activate and export/share the pack.

## Compatibility/security requirements

- Version the serialized format independently from catalog revisions.
- Validate IDs, locale tags, metadata, sizes, structure and placeholders.
- Reject malformed/incompatible files with useful errors.
- Missing strings must never make the app unusable; use English fallback.
- Imported data must not execute code or access plugins, files or network.
- Preserve untranslated entries on export.

## Future stages

1. Inventory user-visible Android, Desktop and shared-core strings.
2. Introduce stable IDs and an English source catalog.
3. Implement shared validation/lookup semantics.
4. Add catalog compatibility reports and translator review UI.
5. Maintain cross-platform tests and a community sharing workflow.

## Repository scope

`main` is the working Lang Pack demo; `russian` is the separate Russian-only community edition. The demo is available for maintainer feedback and community testing. The stable-ID design is a future improvement and should be discussed before a larger architectural migration.
