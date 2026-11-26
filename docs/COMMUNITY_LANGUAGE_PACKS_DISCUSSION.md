# Community Language Packs — Public feature walkthrough and discussion draft

**Status:** working demo on `main`. **Platforms:** Android, Windows and Linux Desktop. **Format:** `.procraft-lang` JSON v1. The separate `russian` branch is Russian-only and does not include the manager.

## What is this?

Community Language Packs lets users translate ProCraft's interface without compiling the app or editing source code. Translations are stored locally as portable files that can be exchanged. It is intended for users, volunteer translators and developers.

Features: create a translation; search/edit source strings; save and reopen; activate English, built-in Russian or a custom pack; browse curated community translations inside the built-in manager; install and update verified packs; import/export; share; delete. The catalogue currently contains a reviewed Japanese pack with 707 strings. Local editing works offline; catalogue browsing and downloads require an internet connection.

## Open the manager

**Android:** Open ProCraft → **Settings** → **Language packs**.

**Windows/Linux:** Open ProCraft → **Preferences** → **Language Packs**.

## Create a translation

**Android:** Tap **New**. Fill **Name**, **Language code** (examples: `es-ES`, `de-DE`, `fr-FR`, `ru-RU`) and optional **Author**. Tap **Create**.

**Desktop:** Click **New translation**, fill the display name, locale tag and optional author, then click **Create and edit**.

The pack begins empty. It does not copy or inherit the built-in Russian dictionary.

## Translate

The editor shows English source text and an editable **Translation** field.

1. Search using **Search source strings** (Android) or **Search source text** (Desktop).
2. Find a label and enter the translation beside/below it.
3. Continue with other strings.
4. Tap/click **Save**.

The pack is saved locally and activated. You can reopen it with **Edit**.

| Source | Example translation |
|---|---|
| `Language packs` | `Языковые пакеты` |
| `Merge down` | `Объединить с нижним слоем` |
| `Highlights` | `Света` |

## Choose the active language

- **English (source strings):** original UI text.
- **Русский — встроенный:** bundled Russian translation.
- **Installed custom pack:** your translation.

Select/tap the desired row. Selection persists. Missing or empty custom-pack entries display English, not Russian.

## Import a translation

1. Open the manager.
2. Android: tap **Import**. Desktop: click **Import .procraft-lang**.
3. Pick the file.
4. The app validates the pack.
5. A valid pack is installed, appears in the list and becomes active.

If an installed pack already uses the same ID but contains different data, import is rejected rather than silently overwriting it.

## Export and share

Select an installed custom pack and use **Export** or **Share**.

- Android Share opens the system share sheet.
- Desktop Share exports and reveals the file in the file manager where supported.
- Exported `.procraft-lang` files can be sent to other people and imported on Android or Desktop.

Built-in English/Russian choices are not custom files and cannot be exported with these controls.

## Edit/delete

**Edit** reopens the pack. **Save** stores the changes. **Delete** removes the pack from this device; if it was active, the app returns to a built-in choice. Export a backup before major edits.

## File format

Format v1 is UTF-8 JSON. Exact English source phrases are keys, including case, spaces and punctuation.

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

The source catalog has 1,377 entries. The published Japanese pack currently translates 707 strings. Keep placeholders such as `{count}`, `{used}` and `{maximum}` exactly; they may move within the translated sentence, but cannot be removed or renamed.

Validation limits include 5 MiB file size, 20,000 entries, source keys up to 512 characters, values up to 4,000 characters and up to 128 dynamic-template keys. Metadata, IDs and locale codes are also checked. See the JSON Schema linked below.

## Limitations

This is an MVP, not a complete official localization. Some generated strings, plugins, user content or UI outside the translation boundary may not be editable. English source text is the v1 key; upstream wording changes may require translation updates. Translation quality/layout fit require human review. The Community translations catalogue is curated and installed from inside the app; it is not an open upload portal, collaborative editor or cloud sync service. Updates currently require a manual catalogue refresh and confirmation; background auto-updates are planned but not implemented.

## Developer/contributor

Catalog: `assets/localization/ui-catalog.json` and identical Android copy under `android/app/src/main/assets/localization/ui-catalog.json`. The reviewed Hub index is `community-translations/index.json`. Pack UI/validation is in Android `LanguagePacks.kt` and Desktop `language_packs.rs`; translation lookup is in `Translation.kt` and `translation.rs`. Add exact source keys, platform dictionary entries and regression tests; bump catalog revision when catalog content changes, not format version unless the file contract changes.

## Security/privacy

Language packs are data-only text. Import does not grant plugin permissions, execute scripts or load native code. Validation bounds size and entries and checks structure, IDs and placeholders. Packs stay local unless users export/share. Still import only from sources you trust because translations can be misleading, offensive or inaccurate. The separate static audit documents its scope, findings and residual work.

## Links

- [Main Lang Pack demo](https://github.com/M1ST0R/procraft_localization/tree/main)
- [Russian-only edition](https://github.com/M1ST0R/procraft_localization/tree/russian)
- [README and downloads](../README.md)
- [User/contributor guide](COMMUNITY_LANGUAGE_PACKS_GUIDE.md)
- [Format/behavior notes](COMMUNITY_LANGUAGE_PACKS.md)
- [Acceptance checklist](DEMO_HANDOFF.md)
- [Schema](../schemas/procraft-language-pack.schema.json)
- [Complete importable sample pack](../examples/ru-community.procraft-lang)
- [Readable full pack preview](../examples/ru-community-preview.json)
- [Security report](../security/language-packs-2026-11-23/REPORT.md)
- [Security remediation checklist](../security/language-packs-2026-11-23/ACTIONS.md)
- [Supply-chain hardening](../security/SUPPLY_CHAIN.md)
- [Repository review](REPOSITORY_REVIEW.md)

## Discussion

Feedback is welcome about the in-app workflow, exact-English-key v1 and its limitations, missing strings, the future stable-message-ID design, and the preferred contribution/review process. The goal is to make translation accessible without requiring users to build the app or maintain a full fork.
