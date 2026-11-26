# Community translations catalogue

This directory is the source of truth for the public ProCraft community translations catalogue. The catalogue is accessed from the built-in Language Packs manager; users do not install a separate plugin or marketplace.

- Catalogue: [index.json](index.json)
- Catalogue schema: [community-translation-index.schema.json](../schemas/community-translation-index.schema.json)
- Pack format: [procraft-language-pack.schema.json](../schemas/procraft-language-pack.schema.json)
- Translator guide: [COMMUNITY_LANGUAGE_PACKS_GUIDE.md](../docs/COMMUNITY_LANGUAGE_PACKS_GUIDE.md)

## Publishing a translation

Only reviewed, data-only `.procraft-lang` packs belong in the Hub. Never publish executable code, scripts, plugins, installers or arbitrary archives here.

1. Receive the contributor's `.procraft-lang` file and confirm the author agrees to public distribution.
2. Validate it against the current pack schema and the application's placeholder rules.
3. Check that the locale, author, source strings and translated UI fit are correct on Android and Desktop.
4. Put the file in this directory using a stable, lowercase filename, for example `ja-jp.procraft-lang`.
5. Calculate its SHA-256 digest:
   - PowerShell: `(Get-FileHash .\ja-jp.procraft-lang -Algorithm SHA256).Hash.ToLower()`
   - Linux: `sha256sum ja-jp.procraft-lang`
   - macOS: `shasum -a 256 ja-jp.procraft-lang`
6. Add an entry to `index.json` with the exact pack metadata, digest, string count and raw GitHub download URL.
7. Update `updated_at`, run CI and test installation from the Hub on both platforms.

Example entry:

```json
{
  "id": "ja-jp.community",
  "locale": "ja-JP",
  "name": "日本語",
  "author": "Contributor name",
  "description": "Japanese community translation.",
  "version": "1.0.0",
  "catalog_version": "procraft-ui-source-3",
  "strings_count": 1200,
  "updated_at": "2026-11-25",
  "download_url": "https://raw.githubusercontent.com/M1ST0R/procraft_localization/main/community-translations/ja-jp.procraft-lang",
  "sha256": "REPLACE_WITH_64_LOWERCASE_HEX_CHARACTERS"
}
```

The example is illustrative; do not publish placeholder hashes or metadata. The pack's ID, locale, name, author, catalogue revision and number of strings must match the index entry.

## Review and trust

The Hub is a curated catalogue, not an unrestricted upload service. A listing means the pack was reviewed by project maintainers; it does not mean every wording choice is universally correct. Contributors remain credited as authors.

Clients accept HTTPS downloads only from this repository's `raw.githubusercontent.com` path. Before installation they verify the SHA-256 digest, parse the package, validate limits and placeholders, and compare the package metadata against the catalogue. Packs remain plain text data and cannot execute code.

The catalogue is currently empty. Community-submitted packs are added after review and compatibility checks.
