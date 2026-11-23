# Community Language Packs — User and Contributor Guide

This guide is for ProCraft users, volunteer translators and maintainers. It describes the working Language Packs manager in the Android and Desktop community builds.

## What the feature is for

A language pack translates visible interface text without rebuilding the app. It is a portable `.procraft-lang` file containing exact English source strings and their translated values, plus language metadata.

The same file format is supported by Android and Desktop (Windows and Linux): create a pack on one platform, export it, then import it on another. Packs are data-only JSON. They are not plugins, applications or scripts and cannot add executable features.

## Where to open it

**Windows / Linux Desktop**
1. Launch ProCraft.
2. Open **Preferences**.
3. Select **Language Packs**.

**Android**
1. Launch ProCraft.
2. Open **Settings**.
3. Select **Language packs**.

The manager contains built-in language choices and the custom packs installed on this device.

## Create a translation

1. In the manager select **New translation** (Desktop) or **New** (Android).
2. Enter a display name, e.g. `Español`, `Deutsch` or `Français`.
3. Enter a locale code, e.g. `es-ES`, `de-DE` or `fr-FR`.
4. Optionally enter a translator or community name in **Author**.
5. Select **Create and edit** (Desktop) or **Create** (Android).

A new pack starts empty. It does not copy the built-in Russian dictionary.

## Translate the interface

The editor displays the app's English source-string catalog and a field for each translation.

- Keep the English source string unchanged. It is the lookup key used by the app.
- Enter the translation into the editable field beside or below the source.
- Use **Search source text** / **Search source strings** to filter the list.
- Select **Save** when finished. The saved custom pack becomes active.

Example:

| English source key | Russian value |
|---|---|
| `Language packs` | `Языковые пакеты` |
| `Merge down` | `Объединить с нижним слоем` |
| `Highlights` | `Света` |

### Rules that matter

**Exact source keys:** Format v1 uses English UI text as keys. Capitalization, punctuation and spaces are significant. `Licenses` and `Licences` are separate entries.

**Placeholders:** Preserve variables such as `{count}`, `{used}` and `{maximum}` exactly. They may be moved to a more natural position in the translated sentence, but must not be deleted, renamed or changed.

**Empty fields:** An empty translation uses the original English text as a fallback.

**No mixed-language fallback:** A custom pack does not silently inherit Russian. If an entry is missing from a custom pack, the English source string is shown. The built-in Russian dictionary is a separate selection.

## Choose the active language

The manager provides three kinds of choice:

- **English (source strings):** displays the English source wording.
- **Русский — встроенный:** activates the Russian dictionary bundled with the community build.
- **Installed custom pack:** activates the selected community translation.

On Desktop select the pack row; on Android tap it. The active selection is retained by the app.

## Import a translation

1. Open the manager.
2. Select **Import .procraft-lang** on Desktop or **Import** on Android.
3. Choose the file.
4. The app validates it before installation.
5. Select it in the installed list if it is not already active.

A pack ID identifies the pack. If the same ID is already installed with different contents, the import is rejected rather than silently replacing the existing pack.

## Export and share

1. Select an installed custom pack.
2. Choose **Export** and save the `.procraft-lang` file.
3. Send the file to another user or translator.

On Android, **Share** opens the system sharing sheet. On Desktop, **Share** exports the file and reveals it in the file manager where supported.

Built-in English and Russian choices are bundled dictionaries, not custom packs, so they are not exported by these controls.

## Edit, update and delete

Select an installed custom pack and choose **Edit** to continue working on it. Search for a source string, update the translation and save.

**Delete** removes the selected custom pack from this device. If that pack is active, ProCraft switches back to the built-in Russian choice. Keep an exported backup before making substantial edits.

## File format

The file extension is `.procraft-lang`. Format version 1 is JSON. A minimal example:

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

The `strings` object maps exact English keys to translated values. `catalog_version` records which source catalog the pack was created against; the current revision is `procraft-ui-source-2`. This catalog revision does not change `format_version`.

The importer applies size and structure limits, including a 5 MB maximum file size, up to 20,000 entries, source keys up to 160 characters, translated values up to 4,000 characters and at most 128 dynamic-template entries. Pack IDs, locale codes and metadata also have validation rules. See the [JSON Schema](../schemas/procraft-language-pack.schema.json).

## Developer notes

The shared source catalog is `assets/localization/ui-catalog.json`; Android ships the same catalog at `android/app/src/main/assets/localization/ui-catalog.json`. Keep these files identical.

- Desktop rendering-boundary translations live in `desktop/src/translation.rs`.
- Desktop pack parsing, storage and UI live in `desktop/src/language_packs.rs`.
- Android rendering-boundary translations live in `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt`.
- Android pack parsing, storage and Compose UI live in `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt`.
- The shared format is described by `schemas/procraft-language-pack.schema.json`.
- The example pack is `examples/ru-community.procraft-lang`.

When adding a translatable label, add its exact source text to the shared catalog and both platform dictionaries (where that UI exists). Add or update regression tests, bump `catalog_version` when the catalog changes, keep the Android catalog copy byte-identical to the shared one and update this guide if the user-facing workflow changes. Do not bump the format version unless the serialized structure or validation contract changes.

## Troubleshooting and contributing

If a label remains English:
1. Confirm the intended pack is active.
2. Search for the exact visible text, including case and punctuation.
3. If it exists in the catalog, add the translation and save.
4. If it does not exist, report the exact wording, the screen or menu where it appears and a screenshot if possible.

For clipped or overlapping labels, include the platform, window size or screen resolution, display scaling and a screenshot. This may require a layout fix rather than a shorter translation.

There is no online translation marketplace or automatic synchronization in this demo. Packs are shared as files and contributions can be submitted through repository issues or pull requests.

## Security and privacy

A pack contains text data, not executable code. Importers bound the input size, validate the JSON structure and check placeholder consistency before storing the pack. Android stores packs in app-private files; Desktop stores them under the app data directory.

Validation cannot determine whether a translation is accurate, respectful or trustworthy. Only import packs from sources you trust. The security review is documented separately under `security/`.
