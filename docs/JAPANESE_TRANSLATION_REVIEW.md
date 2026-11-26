# Japanese translation review — 2026-11-26

## Submitted file

- Locale: `ja-JP`
- Pack ID: `ja-jp.2f467c80`
- Contributor: `Helga (K_0w0)`
- Strings: 707
- Source catalogue revision: `procraft-ui-source-3`

The submitted file has the same 707 source keys as the currently published pack, but its translation values differ in five places. The published version already contains corrections, so the incoming file was **not** used to replace the current pack.

| English source | Submitted wording | Currently published wording |
|---|---|---|
| `Flip Horizontal` | `垂直反転` (vertical flip) | `水平反転` (horizontal flip) |
| `Grass` | `ガラス` (glass) | `草` (grass) |
| `Maximum Layers` | `最小レイヤー数` (minimum layers) | `最大レイヤー数` (maximum layers) |
| `Shear` | `共有` (share) | `せん断` (shear) |
| `Translate English source strings. Empty values use English fallback.` | `元の英語テキストを翻訳してかださあ。空欄は英語で表示されます。` | `英語の原文を翻訳してください。空欄は英語のまま表示されます。` |

The current published package remains the reviewed 707-string version in `community-translations/index.json`.

## Reported editor issues

The current `main` source already includes:

- system-bar safe-area padding and keyboard inset padding in the Language Packs and editor screens;
- explicit high-contrast input text, container, placeholder and cursor colors;
- RTL layout and text direction for pack editing.

The screenshots show the older installation (the Japanese pack displayed only 9 strings). The current catalogue package has 707 strings. These code fixes have build/test coverage, but they still need a device-specific visual confirmation from the contributor.

Arabic/RTL rendering was also improved: Android translated text is right-aligned for RTL locales, and Desktop's default UI alignment now follows the active RTL locale. Locale detection includes RTL script tags such as `az-Arab` and `nqo`.

## Reply to the contributor

Thanks for sending the Japanese translation and the screenshots! We compared your 707-entry file with the currently published pack. The source-key set is identical, and the published version already contains corrections for five wording issues (horizontal flip, grass, maximum layers, shear, and the editor's translation hint), so we kept those fixes.

The Language Packs/editor screens in the current `main` build include safe-area padding and explicit input-field colors. Your screenshots appear to show the older installation with only 9 translated strings; the current Japanese catalogue pack has 707. Please try installing/updating it from **Settings → Language packs → Community translations**.

We also improved RTL text alignment for Arabic. We'd still appreciate a quick visual check on your device after the next build.
