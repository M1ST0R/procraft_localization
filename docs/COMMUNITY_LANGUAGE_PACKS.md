# Community language-pack format

## Goals

A language pack is a portable, versioned data file. It describes one translation variant for one locale. It contains text only; it is not a plugin and cannot run code.

Suggested extension: `.procraft-lang`  
Serialization: UTF-8 JSON  
Initial format version: `1`

## Example

```json
{
  "format_version": 1,
  "id": "ru.community",
  "locale": "ru-RU",
  "name": "Русский — Community",
  "author": "Community contributor",
  "app_version": "0.0.6-beta",
  "catalog_version": "procraft-ui-1",
  "base_locale": "en",
  "strings": {
    "settings.language.title": "Язык",
    "gallery.title": "Галерея",
    "layers.count": "Слоёв: {count}"
  }
}
```

## Required metadata

| Field | Purpose |
|---|---|
| `format_version` | Version of this file format |
| `id` | Stable, unique pack identifier |
| `locale` | BCP 47 locale tag, such as `ru-RU` |
| `name` | Display name shown in Language settings |
| `strings` | Map from stable message keys to translated strings |

Recommended metadata includes author, app version, source-catalog version, and fallback locale.

## Runtime rules

1. Validate the JSON shape, metadata, and supported format version.
2. Validate string limits and placeholders.
3. Resolve the selected pack first.
4. Use the built-in locale catalog if a key is not supplied by the pack.
5. Use English as the final fallback.
6. If a key is unknown to all catalogs, show a safe fallback and log the missing key in development builds.

## Placeholder rules

Named placeholders are preferred: `{count}`, `{name}`, `{size}`. Names and required placeholder sets must match the source entry. A translation must not remove a required argument or introduce a value that the application did not provide.

Pluralization should use locale-aware plural categories rather than English singular/plural concatenation. A future format revision can represent plural variants explicitly, for example with `one`, `few`, `many`, and `other` forms.

## Updating packs

The app should expose a catalog version (or hash). On an app update, compare the pack with the current catalog and report:
- new source keys that need translation;
- removed keys that are now obsolete;
- changed placeholder signatures that need review.

Do not rewrite imported packs silently. The editor should preserve user-authored strings and allow exporting a reviewed update.

## Safety requirements

- Set a maximum file size and maximum number/length of entries.
- Reject duplicate keys and invalid locale tags.
- Render values as plain text; never interpret them as markup or executable content.
- Never load scripts, dynamic libraries, plugin commands, URLs, or arbitrary paths from a language pack.
- Keep import/export local unless the user explicitly shares a file.
