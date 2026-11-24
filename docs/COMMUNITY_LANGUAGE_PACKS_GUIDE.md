# Community Language Packs — User, Translator and Developer Guide

This guide explains the working Language Packs manager in the `main` branch for regular users, translators and contributors.

## 1. Where to find it

**Android**

1. Open ProCraft.
2. Go to **Settings**.
3. Tap **Language packs**.

**Windows / Linux Desktop**

1. Open ProCraft.
2. Open **Preferences**.
3. Select **Language Packs**.

The repository now maintains one edition on `main`, including this editor.

## 2. Create a translation from scratch

**Android**

1. Tap **New**.
2. Fill in **Name** (what appears in the language list).
3. Fill in **Language code** (examples: `es-ES`, `de-DE`, `fr-FR`, `ru-RU`).
4. Optionally fill in **Author**.
5. Tap **Create**.

**Desktop**

1. Click **New translation**.
2. Enter the display name, locale and optional author.
3. Click **Create and edit**.

A new pack begins empty; it does not copy the built-in Russian dictionary. To inspect a complete working example before importing it, open [the full Russian pack preview](../examples/ru-community-preview.json).

## 3. Translate strings

The editor displays the original English source and an editable **Translation** field.

1. Search using **Search source strings** (Android) or **Search source text** (Desktop).
2. Find the exact source phrase you want to translate.
3. Enter your translation in its field.
4. Repeat for other entries.
5. Press **Save**.

The pack is stored locally and becomes the active language. Empty entries use English fallback.

### Example

| Source text | Translation |
|---|---|
| `Language packs` | `Языковые пакеты` |
| `Merge down` | `Объединить с нижним слоем` |
| `Highlights` | `Света` |

### Keep the source key exact

Format v1 uses exact English text as the key. Case, punctuation and spaces matter. For example, `Licenses` and `Licences` are different keys. Do not edit the source text; edit only the translation value.

### Preserve placeholders

Keep placeholders such as `{count}`, `{used}` and `{maximum}` exactly. They can move to a natural position in the sentence but cannot be deleted or renamed. A mismatch is rejected.

## 4. Select a language

The manager offers:

- **English (source strings):** original English UI text.
- **Русский — встроенный:** bundled Russian translation.
- **Installed custom pack:** the community translation you imported or created.

Select/tap the language row to activate it. The selection is saved.

Custom packs do **not** inherit Russian. Missing or empty entries show English.

## 5. Import a pack shared by another person

1. Open Language Packs.
2. On Android tap **Import**; on Desktop click **Import .procraft-lang**.
3. Select the file.
4. Wait for validation.
5. The pack appears in the installed list and is activated.

If the pack ID already exists locally with different content, the app rejects the import rather than overwriting your installed pack silently. Keep a backup/export before changing an existing translation.

## 6. Export and share

Select an installed custom pack.

- **Export:** save a `.procraft-lang` file to a location you choose.
- **Share:** Android opens the system share sheet. Desktop exports the pack and reveals it in the file manager where supported.

Send the exported file to another translator or user. The file can be imported on Android or Desktop. Built-in English and Russian choices are not custom files and cannot be exported from these buttons.

## 7. Edit, update or delete a pack

- **Edit:** reopen the editor, search for entries and change translations.
- **Save:** stores your changes and activates the pack.
- **Delete:** removes that custom pack from this device. If it was active, the app returns to a built-in language choice.

Export a backup before making large edits.

## 8. File format

A `.procraft-lang` file is UTF-8 JSON. Example:

```json
{
  "format_version": 1,
  "id": "es.community",
  "locale": "es-ES",
  "name": "Español — Community",
  "author": "Community contributor",
  "app_version": "0.0.7-beta",
  "catalog_version": "procraft-ui-source-3",
  "base_locale": "en",
  "strings": {
    "Gallery": "Galería",
    "New canvas": "Nuevo lienzo",
    "Layers": "Capas"
  }
}
```

The `strings` object maps exact English UI keys to translation values. Catalog revision `procraft-ui-source-3` is metadata and does not change format v1.

The importer limits the file to 5 MiB, 20,000 strings, source keys up to 512 characters, values up to 4,000 characters and 128 dynamic-template keys. Metadata, IDs and locale codes are also checked. The [JSON Schema](../schemas/procraft-language-pack.schema.json) documents the contract.

## 9. Troubleshooting

**A label is still English**
1. Confirm your custom pack is active.
2. Search for the exact text, including case and punctuation.
3. If the source exists, translate it and save.
4. If it is absent, report the exact label, screen/menu and screenshot.

**A translation is clipped or overlaps**
Report platform, window size or device resolution, display scaling/orientation and a screenshot. It may require a UI layout fix.

**Import fails**
Check that the file is a valid `.procraft-lang` pack, UTF-8 JSON, within size/entry limits, and that placeholders are unchanged. If the ID already exists with different contents, rename the pack ID in your own file before importing or export/backup and remove the old local pack first.

**I expected my pack to inherit Russian**
Custom packs intentionally fall back to English, not to the built-in Russian dictionary.

## 10. Contributing and sharing

There is no online pack marketplace, automatic update service or remote translation sync. Community sharing is file-based. You can report missing strings or propose translations through GitHub Issues or Pull Requests.

For translation reports, include:

- exact source text;
- platform and screen/menu;
- screenshot;
- proposed translation, if available.

## 11. Developer/contributor notes

Source files:

| Area | File |
|---|---|
| Shared source catalog | `assets/localization/ui-catalog.json` |
| Android catalog mirror | `android/app/src/main/assets/localization/ui-catalog.json` |
| Android dictionary/rendering lookup | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt` |
| Android pack parser/storage/UI | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt` |
| Desktop dictionary/rendering lookup | `desktop/src/translation.rs` |
| Desktop pack parser/storage/UI | `desktop/src/language_packs.rs` |
| Format schema | `schemas/procraft-language-pack.schema.json` |
| Sample pack | `examples/ru-community.procraft-lang` |
| Readable JSON preview | `examples/ru-community-preview.json` |

When adding a source label:

1. Add the exact source phrase to the shared catalog.
2. Keep the Android catalog copy byte-identical.
3. Add platform dictionary entries where built-in translations are maintained.
4. Add/update regression tests.
5. Bump `catalog_version` when catalog content changes.
6. Do not bump `format_version` unless the pack serialization/validation contract changes.
7. Update this guide and the public discussion document if the UI workflow changes.

## 12. Security

Packs are untrusted data. Import validation bounds file size, entry counts and lengths; validates IDs/metadata; and checks placeholders. Imported text is not executable code and does not grant plugin permissions. Still, import packs from sources you trust: a malicious or offensive translation can mislead users even if it cannot execute code.

See the separate [security review](../security/language-packs-2026-11-23/REPORT.md).
