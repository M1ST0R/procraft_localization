# Community Language Packs — Current implementation

**Edition:** `main`  
**Platforms:** Android, Windows and Linux Desktop  
**Pack format:** `.procraft-lang`, JSON format v1  
**Catalog revision:** `procraft-ui-source-3`

## What the feature does

The main branch contains a working community translation manager. A user can create a translation, search the source catalog, edit strings, save the pack locally, activate it, import/export a pack file, share it and delete it. The manager works offline for local packs and includes a built-in community translations catalogue for discovering and installing reviewed translations. It is not a separate plugin or marketplace.

- Android: **Settings → Language packs**
- Desktop: **Preferences → Language Packs**

The repository maintains one edition on `main`, with the Language Packs manager included.

## Actions supported

- **New / New translation:** create a pack with a display name, locale tag and optional author.
- **Search:** find source UI strings.
- **Edit:** reopen an installed custom pack.
- **Save:** save changes locally and activate the saved pack.
- **Community translations:** refresh the catalogue from inside the existing manager, inspect published translations and install or update a reviewed pack. Downloads are restricted to this repository and checked against SHA-256, pack metadata, catalogue revision, size limits and placeholders.
- **Import:** validate and install a `.procraft-lang` file. A successfully imported pack is activated.
- **Export:** write the selected custom pack to a portable file.
- **Share:** Android opens the system share sheet; Desktop exports and reveals the file in the file manager where supported.
- **Delete:** remove a custom pack from this device.
- **English (source strings):** use original English UI text.
- **Русский — встроенный:** use the built-in Russian dictionary.
- **Installed custom pack:** use that pack's own values.

A custom pack does not inherit built-in Russian. Empty or missing entries display English.

## Format v1 and catalog revision

The file stores exact English source UI strings as keys and translated values as strings. Capitalization, punctuation and spaces are significant. A wording change upstream may cause an old entry to stop matching; format v1 does not use stable message IDs yet.

The source catalog currently contains **1,377 entries**. Both apps ship the same catalog:

- `assets/localization/ui-catalog.json`
- `android/app/src/main/assets/localization/ui-catalog.json`

The files must remain identical. Catalog revision `procraft-ui-source-3` is metadata; it is not the serialized format version. Do not change `format_version` unless the file structure or validation contract changes.

## Validation and limits

Import/save validates the pack structure, metadata, pack ID, resource limits and placeholders.

- Maximum file size: 5 MiB (5 MB as shown in the UI)
- Maximum string entries: 20,000
- Source key length: 512 characters
- Translation value length: 4,000 characters
- Maximum dynamic-template entries: 128
- Metadata lengths and locale/ID formats are validated
- Named placeholders (such as `{count}`) must match between source and translation

The JSON Schema is in [`schemas/procraft-language-pack.schema.json`](../schemas/procraft-language-pack.schema.json).

## Known limitations

- English text is used as the key; upstream wording changes can break matching.
- Some generated/parameterized text, plugin text or UI text outside the translation boundary may not be included in the editor catalog.
- The Hub is a curated catalogue, not an open upload service or a cloud editor. Maintainers review submissions and publish them through `community-translations/index.json`.
- Hub updates are user-initiated. Replacing an installed pack requires confirmation because local edits would be lost.
- The built-in Russian dictionary is a separate built-in choice and is not exported as a custom pack.
- Translation quality, terminology consistency and layout fit require human review.

## Community Hub

The remote catalogue is maintained in [`community-translations/index.json`](../community-translations/index.json). It is accessed from the built-in Language Packs manager, not through a separate plugin or marketplace. It currently includes the Japanese (`ja-JP`) community translation; additional packs are published after review. See the [publishing instructions](../community-translations/README.md) before adding a pack.

## Sample pack

[`examples/ru-community.procraft-lang`](../examples/ru-community.procraft-lang) is a complete Russian sample containing all 1,377 catalog entries. Inspect the [readable JSON preview](../examples/ru-community-preview.json) on GitHub before importing it.

## Related documentation

- [User/translator guide](COMMUNITY_LANGUAGE_PACKS_GUIDE.md)
- [Public feature discussion and walkthrough](COMMUNITY_LANGUAGE_PACKS_DISCUSSION.md)
- [Maintainer acceptance tests](DEMO_HANDOFF.md)
- [Scoped security review](../security/language-packs-2026-11-23/REPORT.md)
