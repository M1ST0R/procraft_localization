# Demo handoff: Community Language Packs

**Branch:** `demo/community-language-packs`  
**Base:** the Russian community build on ProCraft `v0.0.7-beta`  
**Purpose:** an end-to-end, in-app MVP that users can try, rather than a proposal or a static sample file.

## User-facing workflow

### Android

Open **Settings → Language packs** from the app's settings page.

### Desktop

Open **Preferences → Language Packs**.

On either platform the user can:

1. Create a translation by entering its display name, locale (for example `es-ES`) and optional author.
2. Search the shared English UI source catalog and edit the translation beside each source string.
3. Save the pack; it is stored locally and becomes active.
4. Reopen it later and continue editing.
5. Import a `.procraft-lang` file from another user.
6. Export the selected pack to a portable file.
7. Share it: Android opens the system Sharesheet; Desktop's Share action exports the pack and reveals it in the system file manager so it can be attached to a message.
8. Switch between English source strings, the built-in Russian translation and installed custom packs.
9. Delete an installed custom pack.

Changes take effect at the UI text-rendering boundary. The active selection is persisted and restored at the next app start.

## Quick acceptance test

1. Install the Android APK or Desktop build from the **Actions** artifacts for this branch.
2. Open Language packs and create a pack named `Español demo`, locale `es-ES`.
3. Search for `Gallery`, enter `Galería`, save and check the app's Gallery label.
4. Search for `Layers`, enter `Capas`, save and check the label changes too.
5. Export the pack as `es-es....procraft-lang`.
6. Import that same file on the other platform. Activate it and verify that Gallery/Layers show the same Spanish text.
7. Select built-in Russian, then English, then the custom pack again. The active UI should follow the selected option.
8. Try to translate a placeholder string such as `Layer {count}` as `Capa` (without `{count}`). Saving/importing should reject it; `Capa {count}` should pass.
9. Delete the custom pack and verify the app returns to the built-in Russian translation.

## Shared pack format v1

The same JSON structure is read and written by Android and Desktop:

- `format_version`: currently 1
- `id`, `locale`, `name`, `author`
- `app_version`, `catalog_version`
- `base_locale`: `en`
- `strings`: exact English source string → translated value

Both platforms ship the same source catalog at `assets/localization/ui-catalog.json`; Android includes a copy under `android/app/src/main/assets/localization/ui-catalog.json`.

### Validation and fallback

- Import is limited to 5 MB and no more than 20,000 translation entries.
- IDs and metadata have format/length checks.
- Keys and values are length-limited.
- Named placeholders like `{count}` must be preserved exactly.
- Missing or empty values fall back to the English source string.
- A custom pack does not silently fall back to Russian.
- Packs contain JSON text only. No code, scripts, plugin permissions or network actions are loaded.

## Implementation locations

| Area | File |
|---|---|
| Shared source catalog | `assets/localization/ui-catalog.json` |
| Android manager, file format, validation and sharing | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/LanguagePacks.kt` |
| Android text-rendering integration | `android/app/src/main/java/io/github/artcraftandroid/procraft/ui/Translation.kt` |
| Android settings entry | `android/app/src/main/java/io/github/artcraftandroid/procraft/SettingsActivity.kt` |
| Desktop manager, format, validation and file sharing | `desktop/src/language_packs.rs` |
| Desktop text-rendering integration | `desktop/src/translation.rs` |
| Desktop settings entry | `desktop/src/settings.rs` and `desktop/src/ui.rs` |
| Format and behavior docs | `docs/COMMUNITY_LANGUAGE_PACKS.md` |
| Importable sample | `examples/ru-community.procraft-lang` |

## CI and artifacts

The branch runs three platform jobs:
- Android ARM64 APK.
- Windows x64 MSI and portable ZIP.
- Linux x86_64 AppImage, DEB and tar.gz.

The release builds are published as workflow artifacts by GitHub Actions. They prove the branch compiles and packages; manual acceptance on a physical Android tablet and Windows/Linux machines is still required.

## Deliberate MVP trade-offs

This demo is built to work now on the existing text-rendering boundary, so v1 uses exact English source strings as keys. It is not yet the final stable-message-ID architecture.

- When upstream changes an English phrase, a corresponding pack entry may need updating.
- Some generated/parameterized text, plugin content and UI text outside the two rendering wrappers may not be editable yet.
- The pack manager uses a shared catalog of 1,364 source strings derived from the current Android and Desktop translation dictionaries.
- Sharing is file-based, not a hosted translation marketplace. A user exports the pack and sends the file.
- The bundled Russian dictionary remains a built-in option; users can create/import their own packs and choose which one is active.
- There is no auto-update marketplace, moderation or remote sync.

The longer-term proposal in `LOCALIZATION_PROPOSAL.md` describes stable IDs and a future catalog migration. It must not be confused with this working v1 demo format.
