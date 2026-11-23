## Russian community builds (this branch)

This branch is a **community-maintained Russian-language build** of ProCraft for Android, Windows and Linux. It is intended for Russian-speaking users who want a localized desktop and Android app before an official upstream localization is available. It is separate from the proposal for a reusable Community Language Packs system on [the main branch](https://github.com/M1ST0R/procraft_localization/tree/main).

### Working community language-pack demo

This branch includes an in-app, file-based translation manager on Android and Desktop. Users can create a language, search the shared UI source catalog, edit strings, save packs locally, import/export `.procraft-lang` files, switch active translations and delete packs. Missing strings fall back to English, and placeholders are validated on import/save.

- Android: **Settings → Language packs**
- Desktop: **Preferences → Language Packs**
- Shared source catalog: `assets/localization/ui-catalog.json`
- Importable sample: [`examples/ru-community.procraft-lang`](examples/ru-community.procraft-lang)
- User and contributor guide: [`docs/COMMUNITY_LANGUAGE_PACKS_GUIDE.md`](docs/COMMUNITY_LANGUAGE_PACKS_GUIDE.md)
- Detailed behavior and limitations: [`docs/COMMUNITY_LANGUAGE_PACKS.md`](docs/COMMUNITY_LANGUAGE_PACKS.md)
- Maintainer handoff and end-to-end acceptance checklist: [`docs/DEMO_HANDOFF.md`](docs/DEMO_HANDOFF.md)

Format v1 uses exact English UI strings as keys so it works with the current UI rendering boundary. The separate [`LOCALIZATION_PROPOSAL.md`](LOCALIZATION_PROPOSAL.md) describes a future stable-message-ID architecture.

### What this project changes

- Adds a Russian UI translation layer to the Android Jetpack Compose text-rendering boundary.
- Adds a separate Russian dictionary to the desktop `egui` text-rendering boundary, covering the main menus, canvas information, layers, colours, settings, brush library and Brush Studio.
- Translates built-in brush names and common interface labels while preserving formatting; unknown or not-yet-translated text falls back to English.
- Keeps user-created artwork names and arbitrary plugin-provided text unchanged unless they match a built-in UI string.
- Uses a separate Android application ID, `io.github.artcraftandroid.procraft.ru`, so the Android community build can coexist with the official ProCraft app.
- Builds Android APKs and Windows/Linux desktop packages with separate GitHub Actions workflows.
- Tracks upstream **ProCraft v0.0.7-beta**: 394 new built-in brushes, the dedicated Krita brush engine and studio, consolidated plugins, updated icons, and upstream performance and stability fixes.

### Install the Russian build

1. Open the [Community Language Packs Android workflow](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Ademo%2Fcommunity-language-packs).
2. Open the newest successful run on the `demo/community-language-packs` branch.
3. Download the **ProCraft-RU-Android-arm64** artifact from the bottom of the run page.
4. Extract the ZIP and install `ProCraft-RU-Android-arm64.apk` on an ARM64 Android tablet.
5. Allow installation from the browser or file manager if Android asks.

The Actions artifact is temporary (currently retained for 30 days). The feature demo APK is distributed from this branch's workflow artifacts; the older `0.0.3` release predates the in-app Language Packs manager and does **not** contain this feature. The Android community build has its own package ID and is installed alongside, rather than over, the official app.

### Current feature-demo builds

The current demo build runs are available here:

- [Android ARM64 APK — run 141](https://github.com/M1ST0R/procraft_localization/actions/runs/45055585910)
- [Windows x64 and Linux x86_64 — run 40](https://github.com/M1ST0R/procraft_localization/actions/runs/45055766062)

Download the artifacts only after the corresponding run has completed successfully. Each artifact is available from the run page under **Artifacts**.

### Build desktop versions

The [Desktop RU builds workflow](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Ademo%2Fcommunity-language-packs) runs automatically when desktop source, the shared engine, plugins, assets or packaging files change on this branch. It can also be started manually from **Actions → Desktop RU builds → Run workflow**.

- **Windows x64** — download the `ProCraft-RU-Windows-x64` artifact. It contains an `.msi` installer and a portable `.zip` with `ProCraft.exe` and the required DirectX Shader Compiler DLLs.
- **Linux x86_64** — download the `ProCraft-RU-Linux-x86_64` artifact. It contains an `.AppImage`, a `.deb` package and a `.tar.gz` archive.
- Desktop artifacts are retained for 30 days. Versioned releases can also include the desktop packages through the existing [Release workflow](https://github.com/M1ST0R/procraft_localization/actions/workflows/release.yml).


### Translation status and known limitations

The translation is an ongoing community effort, not yet a complete official localization. Some text can remain in English when it comes from an untranslated label, a separate rendering path, a plugin, user content, or a string that has not yet been added to the dictionary. Please report the exact English wording and where it appears; a screenshot is especially useful.

Russian phrases are often longer than their English originals. Some buttons, tabs or compact panels may clip or truncate translated text. Report the screen, device resolution/orientation, and a screenshot where possible. The translation layer can change wording, but fixing a layout that is too narrow may require a targeted UI layout change rather than shortening every translation.

### Updating the Russian build

- **New community demo build:** when app or shared source files change on `demo/community-language-packs`, GitHub Actions builds a new Android APK and Windows/Linux packages.
- **Manual Android build:** open **Actions → Android APK → Run workflow** and select `demo/community-language-packs`.
- **Manual desktop build:** open **Actions → Desktop RU builds → Run workflow** for `demo/community-language-packs`.
- **Upstream ProCraft update:** follow [the upstream merge and signing instructions](docs/UPSTREAM_AND_ANDROID_BUILDS.md). Merge upstream changes into this branch; do not replace this branch with a fresh upstream checkout.
- Keep the same package ID and signing key for every update. Android uses the matching signing certificate and a higher version code to install updates without uninstalling the existing app.

### Contributing translation fixes

Open an issue in this repository or report it in the community Discord. Include:
- the exact English text as it appears in the app;
- the screen or menu where it appears;
- a screenshot, if possible;
- the suggested Russian translation, if you have one.

For clipped text, include the device model, screen resolution, orientation and whether the problem occurs at the default display/font size. Avoid reporting user-created names or plugin text as missing built-in translations unless they are meant to be translated by the app.

---

> **Russian community build:** this branch carries the Russian interface variants for Android and desktop. It is kept separate from the community-localization proposal on [main](https://github.com/M1ST0R/procraft_localization/tree/main). Android APKs and desktop packages are built by [GitHub Actions](https://github.com/M1ST0R/procraft_localization/actions); see [build and update notes](docs/UPSTREAM_AND_ANDROID_BUILDS.md).

<p align="center">
  <img src="assets/app-icon/procraft-1024.png" alt="ProCraft's app icon: a paintbrush in a warm-to-cool blend on a black tile" width="128">
</p>

<h1 align="center">ProCraft</h1>

<p align="center">
  <b>An open-source painting app for Android tablets, Windows and Linux, with its own Rust engine and GPU renderer: a Kotlin/Jetpack Compose UI on Android, the same interface in egui on the desktop.</b><br>
  Pressure-sensitive brushes, layers with blend modes, adjustments, selection and transform, animation and 3D painting, written from scratch.
</p>

<p align="center">
  <a href="https://github.com/artcraft-android/procraft/releases"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/artcraft-android/procraft"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>
  <a href="https://discord.gg/gypGes3Uca"><img src="docs/images/discord-badge.svg" alt="Join us on Discord" height="60"></a>
</p>

<p align="center">
  <a href="https://discord.gg/gypGes3Uca"><img alt="Discord: join the community" src="https://img.shields.io/badge/Discord-join%20the%20community-5865F2?style=flat&logo=discord&logoColor=white"></a>
  <img alt="Rust engine" src="https://img.shields.io/badge/engine-Rust-lightblue?style=flat&logo=rust&logoColor=white">
  <img alt="GPU renderer: wgpu" src="https://img.shields.io/badge/renderer-wgpu-lightblue?style=flat">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/UI-Kotlin%20%C2%B7%20Jetpack%20Compose-blue?style=flat">
  <img alt="Minimum: Android 8 (API 26)" src="https://img.shields.io/badge/min-Android%208%20%28API%2026%29-blue?style=flat">
  <img alt="Target: Android 16 (API 36)" src="https://img.shields.io/badge/target-Android%2016%20%28API%2036%29-blue?style=flat">
  <img alt="License: MIT OR Apache-2.0" src="https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat">
  <img alt="Desktop: Windows and Linux" src="https://img.shields.io/badge/desktop-Windows%20%C2%B7%20Linux-blue?style=flat">
  <img alt="Status: beta" src="https://img.shields.io/badge/status-beta-lightblue?style=flat">
</p>

<p align="center">
  <sub>From <a href="https://github.com/artcraft-android/procraft/releases">Releases</a>: Android tablets take the <code>arm64.apk</code> (the <code>legacy-arm32.apk</code> is only for older 32-bit tablets), or Obtainium, for updates; Windows the <code>.msi</code> installer or the portable <code>.zip</code>; Linux the <code>.AppImage</code>, the <code>.deb</code> or the <code>.tar.gz</code> (videos and PDFs there use the system's <code>ffmpeg</code> and <code>poppler-utils</code>).</sub>
</p>

<p align="center">
  <a href="#painting">Painting</a> ·
  <a href="#layers-and-colour">Layers &amp; colour</a> ·
  <a href="#adjustments-and-transform">Adjustments &amp; transform</a> ·
  <a href="#actions">Actions</a> ·
  <a href="#planned-features">Planned features</a> ·
  <a href="#building-instructions">Building</a>
</p>

<br>

<p align="center">
  <img src="docs/images/demo.webp" alt="Dusk painted in ProCraft from a blank canvas, at a person's pace: the Soft Airbrush picked in the brush library and its size set; colours picked on the Classic square; the sky filled indigo by dragging the colour onto the canvas and airbrushed in bands of purple, magenta, orange and gold; a new layer for a glow and one for the sun, a circle that snaps round and is filled; two ranges of hills outlined and filled; pines and birds inked; stars airbrushed and made to glow with Bloom; the Layers panel opened on its seven layers" width="100%">
  <br>
  <sub><b>Dusk</b>, painted from a blank canvas on a Lenovo Legion Tab: the brush library, the colour picker, the layers, a sun snapped round and filled, hills filled the same way, pines, stars with Bloom. About 1:20; the <a href="docs/images/demo.mp4">full video</a> (MP4, 2:21) shows every menu at close to real speed. Every stroke is a pen stroke with pressure, eased in and out, played by the <a href="tools/showcase">demo script</a>.</sub>
</p>

<br>

<p align="center">
  <img src="docs/images/canvas.jpg" alt="The finished Dusk on the canvas: a sky from deep indigo through purple, magenta and orange to gold, a pale sun with a soft glow, birds, two ranges of purple hills with dark pines, and stars; the top bar with Gallery and the tools, and the sidebar with the size and opacity sliders, undo and redo" width="100%">
  <br>
  <sub>The canvas: the tools along the top, size and opacity in the sidebar, the whole screen for the painting.</sub>
</p>

<br>

## Painting

<table>
<tr>
<td width="50%"><img src="docs/images/brushes.jpg" alt="The Brush library over Dusk: the sets Sketching, Inking, Drawing, Painting (chosen), Watercolour, Calligraphy, Airbrushing, Spray Paint, Charcoals, Textures and Abstract, each with its icon, and the Painting set's brushes with a preview stroke each"><br><sub>The brush library: eleven sets of our own brushes, each previewed with a stroke.</sub></td>
<td width="50%"><img src="docs/images/studio.jpg" alt="The Brush Studio on Round Brush: the tabs from Stroke path to About this brush, the Shape tab with its shape source and input style, and the Drawing Pad with a pressure scribble"><br><sub>The Brush Studio: stroke path, stabilization, taper, shape, grain, rendering, wet mix, colour dynamics and more, tried out on the drawing pad.</sub></td>
</tr>
</table>

<br>

## Layers and colour

<table>
<tr>
<td width="50%"><img src="docs/images/layers.jpg" alt="The Layers panel over Dusk: seven layers, each with its thumbnail (stars, pines, the near and far hills, the sun, its glow and the sky), their blend mode letters and visibility boxes, and the background colour"><br><sub>Layers with blend modes, opacity, alpha lock, clipping masks, masks and groups. Layers past what the GPU holds are kept compressed, so artworks with hundreds of layers open and paint smoothly.</sub></td>
<td width="50%"><img src="docs/images/colour.jpg" alt="The Colors panel on its Disc tab: a hue ring around a saturation and brightness disc, the current and previous colours, and the Classic, Harmony, Value and Palettes tabs"><br><sub>Colour: disc, classic, harmony, value (with hex) and palettes.</sub></td>
</tr>
</table>

<br>

## Adjustments and transform

<table>
<tr>
<td width="50%"><img src="docs/images/adjust.jpg" alt="Hue, Saturation, Brightness on Dusk's sky: the hue moved to 40 percent turns the sky blue at the top and pink at the horizon, with the Hue, Saturation and Brightness sliders below"><br><sub>Hue, saturation, brightness on the sky layer, live. Also colour balance, curves, gradient map, blurs, noise, sharpen, bloom, glitch, halftone, chromatic aberration, liquify and clone, on the layer or painted on.</sub></td>
<td width="50%"><img src="docs/images/transform.jpg" alt="Transform in Warp on the sun layer: a mesh with corner handles over the sun, and the toolbar with Freeform, Uniform, Distort and Warp, then Advanced Mesh, Flip Horizontal, Flip Vertical, Rotate 45, Fit to Canvas, Bilinear and Reset"><br><sub>Transform: freeform, uniform, distort and warp, with snapping. Selection: automatic, freehand, rectangle and ellipse, with feather.</sub></td>
</tr>
</table>

<br>

## Actions

<p align="center">
  <img src="docs/images/actions.jpg" alt="The Actions panel on its Canvas tab over Dusk: Crop and Resize, the Animation Assist, Page Assist, Drawing Guide and Reference switches, Edit Drawing Guide, Flip horizontal, Flip vertical and Canvas information, under the Add, Canvas, Share, Video, Prefs and Help tabs" width="100%">
  <br>
  <sub>Actions: add files, photos and text; the canvas, its guides and assists; share as PNG, JPEG, PSD, PDF, TIFF, GIF or MP4; a time-lapse of every artwork; preferences and gesture controls.</sub>
</p>

<br>

## Planned features

ProCraft is in beta, on Android tablets and on the desktop (Windows and Linux).

| Area | Status |
|---|---|
| Canvas: the top bar, the sidebar (size, opacity, modify, undo, redo), full screen, gestures (pinch, rotate, quick fit, two- and three-finger taps, scrub to clear) | ✅ |
| Brush engine: shape and grain sources, stroke path, stabilization, taper, rendering and glazes, wet mix, colour dynamics, pen pressure and tilt; the library and the Brush Studio | ✅ |
| Layers: blend modes, opacity, alpha lock, clipping masks, masks, groups, merge, the reference layer; large artworks kept compressed off the GPU | ✅ |
| Colour: disc, classic, harmony, value and palettes; history; eyedropper; filling by dragging the colour onto the canvas | ✅ |
| Adjustments, each with its toolbar, on the layer or painted on | ✅ |
| Selection (automatic, freehand, shapes, feather) and transform (freeform, uniform, distort, warp, snapping) | ✅ |
| Shapes that snap clean, drawing guides and drawing assist (2D, isometric, perspective, symmetry), text | ✅ |
| Animation and page assists, the reference window | ✅ |
| Gallery: stacks, new-canvas presets, import and share (PNG, JPEG, PSD, PDF, TIFF, GIF, MP4), time-lapse | 🚧 nearly done |
| 3D painting: OBJ and USDZ models, texture sets, painting on the mesh, lights and environments | 🚧 |
| Desktop: Windows and Linux, the same interface with a mouse, a pen tablet and keyboard shortcuts; an installer, an AppImage and a .deb | 🚧 beta |
| Plugins: `.procraft-plugin` files you import in Prefs › Plugins, each off until you turn it on and gone without a trace when you turn it off. Filters (a few lines of WGSL, under Adjustments) and sandboxed WebAssembly that changes how the app behaves, such as Interface size, Touch offset and ColorDrop gap closing. How to write one: [docs/plugins.md](docs/plugins.md) | ✅ |
| More for plugins: buttons that change the artwork (asking permission first), panels of their own, the colour picker drawn their way, kinds of artwork and of layer of their own, such as Vector layers | ✅ |
| Phones: their own app, ProCraft Pocket (this one is for tablets) | ⬜ |

<sub>✅ works today · 🚧 in progress · ⬜ not started · 🚫 not planned</sub>

<br>

## Building Instructions
*One Gradle command builds the whole app: it compiles the Rust engine with [cargo-ndk](https://github.com/bbqsrc/cargo-ndk) and generates the Kotlin bindings.*

* Clone this repository
* Make sure you have at least 6 GB of free disk space
* enter `android` and run `./gradlew assembleRelease -Pprocraft.abis=arm64-v8a`, or open that folder in Android Studio and press Run.
* the desktop app: `cargo run --release -p procraft-desktop` from the repository's root; its installer and packages with `packaging/windows/package.ps1` (WiX v5) and `packaging/linux/package.sh` (nfpm, appimagetool).

Build time dependencies:

<details>
  <summary>required tools</summary>

  ```
      rustup, with the aarch64-linux-android and x86_64-linux-android targets
      cargo install cargo-ndk --version "^4" --locked
      Android SDK (API 37) and NDK 29.0.14206865
      JDK 17 or newer
  ```
</details>

`cargo test --workspace` runs the engine's and the renderer's tests on the desktop.

| Path | What |
|---|---|
| `crates/engine` | document, brushes, stroke geometry, canvas view (no GPU) |
| `crates/gpu` | wgpu renderer: layers, strokes, compositing, display |
| `crates/core` | what both apps share above the renderer: the canvas session, every touch and gesture, the tools' state (adjustments, transform, selection, crop, Recolor) and which of them a button ends, the gallery, presets, gradients, time-lapse, the brush library's files, the layer menu, exports and their file names, and keyboard shortcuts; with the Android app's UniFFI bindings |
| `android` | the Android app (Gradle) |
| `desktop` | the desktop app (egui) for Windows and Linux |
| `packaging` | the desktop app's Windows installer and Linux packages, as the release workflow builds them |

*Builds are only tested on Windows 11.*

<br>

## License

[MIT](LICENSE-MIT) OR [Apache-2.0](LICENSE-APACHE), at your option.

- **Third-party material:** the Inter font, Lucide icons and the light panoramas the app bundles,
  and the libraries it's built with, are listed in [NOTICE](NOTICE).
- **Every asset**, with its author, source and licence, is in [ATTRIBUTION.md](ATTRIBUTION.md).
- **Icons** drawn using vector outlines ([`tools/icons/draw_icons.py`](tools/icons/draw_icons.py),
  to the [style guide](docs/icons/STYLE_GUIDE.md)).
- **The demo painting**, *Dusk*, is our own, made in the app.

<br>

## Misc info for myself

Try to use [semantic commit](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index)
messages. Use `feat:` / `fix:` / `chore:` / `docs:` / etc.
