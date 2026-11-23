# Community Language Packs — not included in the Russian edition

The in-app Community Language Packs manager is implemented on the repository's `main` branch only. This `russian` branch is the separate Russian-only build and does **not** include the custom-pack editor.

On `main`, the manager is available at:

- Android: **Settings → Language packs**
- Windows/Linux: **Preferences → Language Packs**

Users of the Lang Pack demo can create, edit, save, activate, import, export, share and delete data-only `.procraft-lang` files. The current format is JSON v1, uses exact English UI text as keys and falls back to English for missing entries.

For the actual feature documentation, use:

- [Public feature walkthrough](https://github.com/M1ST0R/procraft_localization/blob/main/docs/COMMUNITY_LANGUAGE_PACKS_DISCUSSION.md)
- [User and translator guide](https://github.com/M1ST0R/procraft_localization/blob/main/docs/COMMUNITY_LANGUAGE_PACKS_GUIDE.md)
- [Format, limits and implementation](https://github.com/M1ST0R/procraft_localization/blob/main/docs/COMMUNITY_LANGUAGE_PACKS.md)
- [JSON Schema](https://github.com/M1ST0R/procraft_localization/blob/main/schemas/procraft-language-pack.schema.json)
- [Security report](https://github.com/M1ST0R/procraft_localization/blob/main/security/language-packs-2026-11-23/REPORT.md)

Do not assume that any of these editor screens or import/export controls exist in the Russian-only build.
