# Proposal: Community Localization for ProCraft

**Status:** community proposal / proof of concept  
**Target:** Android (Kotlin + Jetpack Compose) and Desktop (Rust + egui)  
**Motivation:** let users translate ProCraft, maintain their own terminology, and share language packs without maintaining separate application forks.

## The problem

ProCraft has a large interface, and its Android and desktop front ends share part of the functionality through the Rust core. A translation added only to Android string resources would therefore leave many tool names, adjustment names, brush labels, actions, and dynamic messages untranslated. Keeping a full fork for every language also makes translations difficult to maintain when upstream changes.

## The proposal

Introduce a small, versioned localization layer shared by both front ends.

- Ship English as the built-in fallback catalog.
- Give every user-visible string a stable key, independent of its current English wording.
- Allow a user to select a built-in locale or import a community language pack.
- Support multiple packs for one locale (for example, Russian — Community and Russian — Custom).
- Provide an in-app editor for creating a pack, translating missing entries, reviewing changes, and exporting it.
- Keep language packs as data-only files. They must never contain executable code or gain plugin permissions.
- Validate pack compatibility and placeholders before activation.

The goal is to make localization maintainable without forcing the project to accept or ship every community translation.

## User experience

1. Open **Preferences → Language**.
2. Choose a built-in language or import a `.procraft-lang` file.
3. Select a translation variant for that locale.
4. Optionally duplicate it as a personal translation and edit strings.
5. See untranslated or outdated entries in a review screen.
6. Export the pack and share it with other users.

A missing translation falls back to English. A malformed or incompatible pack is rejected with a useful error; it must never make the app unusable.

## Proposed architecture

```text
                  Language pack (.procraft-lang)
                              |
                       Parse + validate
                              |
                    Shared Rust localization API
                     /                     \
           UniFFI / Android               egui / Desktop
          Jetpack Compose                 native UI
                     \                     /
                       Stable message keys
                              |
                    Built-in English catalog
                         (fallback)
```

The localization API should be UI-independent. Android and egui should request the same stable message keys, including named arguments and plural counts. The shared Rust core is a natural place for shared terminology and lookup rules; platform-only strings can live in the corresponding front end while using the same pack format.

## Language-pack format

See [the format proposal](docs/COMMUNITY_LANGUAGE_PACKS.md), its [JSON Schema](schemas/procraft-language-pack.schema.json), and [the Russian sample](examples/ru-community.procraft-lang).

The pack includes a format version, unique ID, locale, display name, author, target app version, fallback locale, and a map of stable message keys to translated strings. Placeholder names are preserved (for example, `{count}`).

## Compatibility and updates

- Stable message keys must not depend on English source text.
- Each app release should expose a source-catalog version or hash.
- On import, compare pack metadata with the app's catalog.
- If new keys appear, keep the pack usable and mark those entries as untranslated.
- If a key is removed or its placeholders change, show a review warning rather than silently discarding user work.
- Exporting a pack should preserve metadata and untranslated entries.
- The English catalog remains the final fallback.

## Security and privacy

Language packs are untrusted input. They should be parsed with size limits, schema validation, placeholder checks, and safe text rendering. A pack is only text data: no scripts, dynamic libraries, plugin API access, network access, or file access. Imported translations should not be automatically uploaded anywhere.

## Suggested implementation stages

1. **Catalog extraction:** inventory all Android, desktop, and shared-core user-visible text; introduce stable keys and an English source catalog.
2. **Runtime lookup:** implement a small shared Rust resolver with fallback, named arguments, plural rules, and pack validation.
3. **Platform integration:** expose the resolver through UniFFI for Android and use the same API from egui.
4. **Language settings:** select, import, remove, and export packs.
5. **Translation editor:** show source text, current translation, missing entries, and compatibility warnings.
6. **Community workflow:** document pack creation and optionally add a curated directory later.

## Acceptance criteria for an MVP

- A user can import and activate a Russian pack on Android and Desktop.
- The same stable key renders the same translation on both platforms.
- Missing entries fall back to English without crashing.
- Named placeholders and plural values are validated and rendered correctly.
- Users can export a pack without losing untranslated entries.
- Importing a pack cannot execute code or alter artwork files.
- A source-catalog update reports added, removed, and changed keys.

## Scope of this repository

This branch is a proposal for maintainer discussion. It is intentionally separate from the Android Russian application build in `translation/russian`. The sample schema and pack describe the proposed interface; they are not yet wired into the ProCraft application. The implementation should be adjusted with maintainer feedback before a production PR.
