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

## 5. Browse, install and update community translations

1. Open **Language packs** and choose **Community translations**.
2. The Manager uses its locally cached catalogue immediately when available.
3. When the Manager is opened, it checks the online catalogue automatically if the previous check is at least six hours old. The check runs while the app is open; it is not an operating-system background service.
4. You can also press **Refresh** in Community translations to check immediately.
5. Review the language, author, version, translated-string count and description.
6. Choose **Install** for a new pack. The app downloads it, verifies SHA-256 and metadata, validates the `.procraft-lang` structure/placeholders and activates it.

The Manager shows a notice when a new catalogue entry or an update for an installed Hub pack is available. The cached catalogue remains usable offline; downloads and refreshes require internet access.

### Protecting local edits during a Hub update

Hub-installed packages keep three pieces of update metadata in the app's private data: the last downloaded Hub version (baseline), its version/SHA-256 and a backup folder.

When you confirm **Update and merge**, the Manager compares the previously downloaded Hub version, your currently installed pack and the newly published Hub version.

- If you did not change a string locally, the new Hub wording is applied.
- If the Hub did not change a string, your local wording is kept.
- If both you and the Hub changed the same string, your local wording wins and the conflict is reported.
- Local-only strings are retained.
- A string removed upstream is removed only when you have not locally changed it.
- A backup of the currently installed pack is written before a changed pack is saved.

For an older pack installed before this update system existed, there is no previous Hub baseline. The first Hub update is deliberately conservative: existing strings are treated as local edits, new Hub strings are added, and existing local text is not silently replaced. Subsequent updates use the saved baseline and can apply three-way merging.

Updates are never silently downloaded or installed. The catalogue check is automatic, but you review and confirm each pack update. This protects local wording and the user's control over changes.

**Available now:** the reviewed Japanese pack (`ja-JP`, 707 translated strings) is published in the catalogue.

## 6. Import a pack shared by another person

1. Open Language Packs.
2. On Android tap **Import**; on Desktop click **Import .procraft-lang**.
3. Select the file.
4. Wait for validation.
5. The pack appears in the installed list and is activated.

If the pack ID already exists locally with different content, the app rejects the import rather than overwriting your installed pack silently. Keep a backup/export before changing an existing translation.

## 7. Export and share

Select an installed custom pack.

- **Export:** save a `.procraft-lang` file to a location you choose.
- **Share:** Android opens the system share sheet. Desktop exports the pack and reveals it in the file manager where supported.

Send the exported file to another translator or user. The file can be imported on Android or Desktop. Built-in English and Russian choices are not custom files and cannot be exported from these buttons.

## 8. Edit, update or delete a pack

- **Edit:** reopen the editor, search for entries and change translations.
- **Save:** stores your changes and activates the pack.
- **Delete:** removes that custom pack from this device. If it was active, the app returns to a built-in language choice.

Export a backup before making large edits.

## 9. File format

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

The `strings` object maps exact English UI keys to translation values. Catalog revision `procraft-ui-source-3` is metadata and does not change format v1. Stable message IDs and plural-form handling are not implemented in format v1; the current roadmap keeps exact source text as keys until a separately tested migration exists.

The importer limits the file to 5 MiB, 20,000 strings, source keys up to 512 characters, values up to 4,000 characters and 128 dynamic-template keys. Metadata, IDs and locale codes are also checked. The [JSON Schema](../schemas/procraft-language-pack.schema.json) documents the contract.

## 10. Troubleshooting

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

## 11. Contributing and publishing a community translation

The Hub is a curated catalogue, not an open upload service or a plugin marketplace. Contributors submit data-only `.procraft-lang` files; maintainers review them before publication. GitHub Issues and Discussions are disabled for this fork, so use a Pull Request or the [ProCraft Discord](https://discord.gg/gypGes3Uca).

For a translation report or contribution, include the locale, author/credit approval, exact source strings, platform screenshots and any layout problems.

To publish a reviewed pack on `main`:

1. Confirm the contributor agrees to public distribution and attribution.
2. Validate the pack against `schemas/procraft-language-pack.schema.json`, placeholder rules and the current source catalog.
3. Test it in the Android and Desktop Manager, including RTL layout when applicable.
4. Add the `.procraft-lang` file directly inside `community-translations/` with a stable lowercase filename.
5. Calculate its SHA-256:
   - PowerShell: `(Get-FileHash .\ja-jp.procraft-lang -Algorithm SHA256).Hash.ToLower()`
   - Linux: `sha256sum ja-jp.procraft-lang`
   - macOS: `shasum -a 256 ja-jp.procraft-lang`
6. Add its exact metadata, version, catalogue revision, string count, raw GitHub download URL and digest to `community-translations/index.json`.
7. Update the index and pack `updated_at` dates, then run `python3 tools/check_community_hub.py` and the main CI workflow.
8. Test install and update from the in-app Hub on Android and Desktop.

The pack ID, locale, name, author, version, catalogue revision, string count and SHA-256 in the index must match the package. Never publish placeholder digests, executable code, scripts, plugins, installers or arbitrary archives.

## 12. Developer/contributor notes

Source files:

| Area | File |
|---|---|
| Shared source catalog | `assets/localization/ui-catalog.json` |
| Android catalog mirror | `android/app/src/main/assets/localization/ui-catalog.json` |
| Android dictionary/rendering lookup | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt` |
| Android pack parser/storage/UI | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt` |
| Desktop dictionary/rendering lookup | `desktop/src/translation.rs` |
| Desktop pack parser/storage/UI | `desktop/src/language_packs.rs` |
| Hub catalogue | `community-translations/index.json` |
| Hub catalogue schema | `schemas/community-translation-index.schema.json` |
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
7. Update this guide when the Manager workflow or pack contract changes. Do not create a second feature guide or discussion document.

## 13. Security

Packs are untrusted data. Import validation bounds file size, entry counts and lengths; validates IDs/metadata; and checks placeholders. Imported text is not executable code and does not grant plugin permissions. Still, import packs from sources you trust: a malicious or offensive translation can mislead users even if it cannot execute code.

See the separate [security review](../security/language-packs-2026-11-23/REPORT.md).
