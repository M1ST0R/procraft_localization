# Community Language Packs — working demo

The `main` branch contains a working, data-only language-pack manager in the Android and Desktop apps.

## What works

Open **Advanced settings → Language packs**:

- Create a translation with a display name, locale and author.
- Search the app's source-string catalog and edit translations.
- Save a pack locally and continue editing it later.
- Switch between English source strings, the built-in Russian dictionary and installed community packs.
- Import another `.procraft-lang` file.
- Export the selected pack to a portable `.procraft-lang` file.
- Delete an installed pack.

The Android editor is reachable from **Settings → Language packs**. On Desktop it is in **Preferences → Language Packs**.

A pack exported on Android can be imported on Desktop and vice versa. Both apps ship the same source catalog at `assets/localization/ui-catalog.json` (also copied into Android assets).

## Format v1

The MVP uses exact English UI strings as keys. This is deliberately compatible with the current rendering-boundary translators, so a user can translate actual visible strings without waiting for a full stable-message-ID migration.

Example:

```json
{
  "format_version": 1,
  "id": "es.community",
  "locale": "es-ES",
  "name": "Español — Community",
  "author": "Community contributor",
  "app_version": "0.0.7-beta",
  "catalog_version": "procraft-ui-source-2",
  "base_locale": "en",
  "strings": {
    "Gallery": "Galería",
    "New canvas": "Nuevo lienzo",
    "Layers": "Capas"
  }
}
```

Fields:

- `format_version`: currently `1`.
- `id`: unique portable pack ID, lowercase letters/digits plus `.`, `_` or `-`.
- `locale`, `name`, `author`: language metadata.
- `app_version` and `catalog_version`: the source build/catalog this pack was created against; the current catalog revision is `procraft-ui-source-2`.
- `base_locale`: the language of source strings, currently `en`.
- `strings`: exact English source string → translated string.

## Fallback and validation

- An empty or missing translation uses the English source text.
- Selecting **English (source strings)** disables the built-in Russian dictionary.
- Selecting **Русский — встроенный** uses the bundled Russian dictionary and its dynamic labels.
- Custom packs do not silently inherit Russian: they use their own translated value or English fallback.
- Imports are limited to 5 MB and 20,000 entries; string keys and values have length limits.
- Placeholders such as `{count}` must be preserved exactly. A pack with a mismatched placeholder is rejected.
- Packs are JSON data only. No scripts, libraries, plugins, network access or executable payloads are loaded.

## Current MVP limitations

- v1 keys are English source strings, not stable IDs. If upstream changes the English wording, that entry may need updating.
- Some generated or parameterized text and plugin-provided content may not appear as editable catalog entries yet.
- There is no online translation marketplace or automatic synchronization. Sharing is file-based: export the pack and send it to another user.
- The built-in Russian dictionary remains bundled as a special built-in choice; it is not automatically exported as a user pack.
- The main-branch demo proves the full create/edit/save/import/export/switch flow. It is an MVP implementation intended for maintainer review, not the final long-term localization architecture.

## Sample pack

See [`examples/ru-community.procraft-lang`](../examples/ru-community.procraft-lang). It uses the same format as the in-app manager and can be imported directly.
