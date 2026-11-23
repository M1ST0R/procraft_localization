# Russian Edition — build, install and maintenance guide

This document applies to the `russian` branch only.

- `russian`: standalone Russian-language UI edition.
- `main`: Community Language Packs demo (custom pack manager).

The Russian branch does **not** include the Language Packs editor. It keeps the built-in Russian translation and does not add the editor's dependencies or UI.

## 1. Get the latest builds

Use the live GitHub Actions pages:

- [Russian Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Arussian)
- [Russian Windows and Linux Desktop builds](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Arussian)
- [Russian CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Arussian)

Open the newest run for the `russian` branch, check that its conclusion is **success**, then download its artifact from the **Artifacts** section.

Actions artifacts are temporary and are currently retained for 30 days. They are community test builds, not permanent upstream releases.

### Android (ARM64)

1. Open the Russian Android APK workflow.
2. Select the newest successful run for `russian`.
3. Download `ProCraft-RU-Android-arm64`.
4. Extract the ZIP.
5. Install `ProCraft-RU-arm64.apk` on an ARM64 Android device.

The package ID is `io.github.artcraftandroid.procraft.ru`. It is separate from the upstream ProCraft Android app.

**Signing behaviour**

- A push or manual run on `russian` started by the repository owner may use the configured release keystore.
- Pull requests and other actors/events build a debug-signed test APK and do not receive signing secrets.
- A debug APK may not update an APK signed with another key.
- Android can update an existing install only when the package ID and signing certificate match and the new version code is higher.

If no release keystore is configured, the owner-triggered build also falls back to a temporary debug key. The workflow prints a warning in this case.

### Windows x64

1. Open the Russian Desktop builds workflow.
2. Select a successful run on `russian`.
3. Download `ProCraft-RU-Windows-x64`.
4. Extract the artifact ZIP.

It contains:

- an `.msi` installer;
- a portable ZIP with `ProCraft.exe`, DirectX Shader Compiler DLLs and the relevant licence/attribution files.

For Portable, extract its ZIP to a folder and run `ProCraft.exe`. Keep the DLLs beside the executable; do not move only the EXE.

The Russian MSI has its own product identity, UpgradeCode, registry key and installation folder. It is designed to install separately from upstream ProCraft and the Lang Pack demo. This separation must still be verified by installing both editions on a clean Windows machine.

### Linux x86_64

The Russian Desktop artifact `ProCraft-RU-Linux-x86_64` contains:

- AppImage;
- DEB package;
- tar.gz portable directory.

The packages use the `procraft-ru` package/binary name and a separate desktop-entry/app ID. The AppImage is self-contained; DEB installation may require the listed system libraries.

The workflow places `SHA256SUMS.txt` beside the generated package files. From the extracted artifact directory, verify them with:

```bash
sha256sum --check SHA256SUMS.txt
```

A checksum helps detect accidental corruption. It does not independently authenticate who produced an artifact; use the linked GitHub Actions run as the source of the artifact.

## 2. What is translated

The Russian UI layer is applied at the Android Jetpack Compose and Desktop egui text-rendering boundaries.

It covers the built-in UI strings present in the translation dictionaries, including common menus, canvas information, layers, colours, settings, brush library and Brush Studio labels.

Some text may remain in English:

- strings that have not yet been added to the dictionary;
- generated/parameterized text outside the translation boundary;
- plugin-provided text;
- user-created artwork names and other user content;
- text rendered through a separate code path.

This is an ongoing community translation, not a claim that every visible string is translated.

### Report a missing or incorrect translation

Open an issue in this repository and include:

1. the exact English text shown by the app;
2. the screen, panel or menu where it appears;
3. Android/Windows/Linux and app build/run number;
4. a screenshot;
5. the proposed Russian wording, if known.

For clipping or overlapping text, include the device resolution or desktop window size, display scaling and orientation. A layout bug may need a UI change rather than a shorter translation.

## 3. Local development builds

Use the existing branch; do not create a permanent branch for the Russian edition.

### Windows

Requirements: Rust toolchain `1.99.0`, MSVC build tools, .NET/WiX v5.2-compatible tooling as used by the workflow.

From the repository root in PowerShell:

```powershell
$env:EDITION = "ru"
./packaging/windows/package.ps1
```

The script builds the desktop executable (unless `-SkipBuild` is passed), validates its architecture, downloads the pinned DXC release, checks its SHA-256 and creates the MSI plus portable ZIP under `dist/`.

### Linux

Requirements: Rust toolchain `1.99.0`, `nfpm` 2.49.0, `appimagetool` 1.9.1, `desktop-file-utils` and the required build libraries.

```bash
EDITION=ru bash packaging/linux/package.sh
```

The script verifies the pinned nfpm/AppImage tool/runtime digests before using them. Generated packages are written to `dist/`.

### Android

The CI workflow is the recommended build path because it configures the Android SDK/NDK, Rust target, Gradle properties, version code and signing rules consistently.

For local builds, follow the upstream Android build requirements and keep the Russian application suffix `.ru` intact. Do not use the demo suffix `.ru.demo` for the Russian edition.

## 4. Update from upstream ProCraft

Do not replace this community branch with a fresh upstream checkout. Merge upstream changes into the existing `russian` branch so the Russian dictionaries, tests and community packaging remain intact.

One-time remote setup:

```bash
git remote add upstream https://github.com/artcraft-android/procraft.git
git fetch upstream
```

Update the existing branch:

```bash
git switch russian
git fetch upstream
git merge upstream/main
```

Before committing:

1. Resolve conflicts while preserving upstream functional changes.
2. Reapply Russian UI changes only where upstream changed the relevant rendering code.
3. Keep the current plugin manifests and bundled plugin layout. Do not restore obsolete standalone plugin folders that upstream has consolidated.
4. Run the Russian CI, Android APK and Desktop builds.
5. Test the APK on a physical ARM64 Android device.
6. Test Windows MSI/Portable or Linux DEB/AppImage on the intended OS.
7. Verify the generated `SHA256SUMS.txt` before distributing the artifact.

The upstream baseline currently tracked by this branch is ProCraft `v0.0.7-beta`. When updating to a later upstream version, update the baseline/version notes in the README and this guide as part of the same change.

## 5. Signing and secrets

The Russian Android workflow uses these repository Actions secrets only for a trusted owner-triggered push or manual run on the `russian` branch:

- `PROCRAFT_KEYSTORE_BASE64`
- `PROCRAFT_KEYSTORE_PASSWORD`
- `PROCRAFT_KEY_ALIAS`
- `PROCRAFT_KEY_PASSWORD`

Do not put keystores or passwords in the repository, issues, commits, logs or artifacts. Keep a secure offline backup. Losing the signing key prevents updates to installations signed with that key.

The workflow uses a temporary debug key for pull requests and other untrusted triggers. Debug APKs are for testing only and are not a dependable update channel.

## 6. Branch and workflow policy

The intended long-lived branches are only:

- `main` — Community Language Packs demo;
- `russian` — standalone Russian edition.

The default branch is `main`. Do not create additional permanent edition branches. Temporary local work can be done without pushing another remote branch; merge finished changes directly into the appropriate existing edition through the normal repository workflow.

The repository keeps three workflows on each edition branch:

- **CI** — Android native build, lint/unit checks and Gradle setup;
- **Android APK** — ARM64 APK packaging;
- **Desktop builds** — Windows MSI/Portable and Linux packages.

Release workflows are not used for these community editions. Use successful Actions artifacts.

## 7. Security and supply chain

- [Scoped Language Packs security report](https://github.com/M1ST0R/procraft_localization/blob/main/security/language-packs-2026-11-23/REPORT.md)
- [Security remediation checklist](https://github.com/M1ST0R/procraft_localization/blob/main/security/language-packs-2026-11-23/ACTIONS.md)
- [Supply-chain hardening record](https://github.com/M1ST0R/procraft_localization/blob/main/security/SUPPLY_CHAIN.md)

The three Actions workflows use full commit-SHA pins, least-privilege `contents: read` permissions, checkout with persisted credentials disabled, explicit build-tool versions and SHA-256 checks for downloaded packaging executables. See the supply-chain document for current pins and residual limitations.

The security report is scoped to the Language Packs feature and related packaging configuration. It is not a full ProCraft application or dependency audit.

## 8. Maintainer checklist

Before publishing a new Russian build:

- [ ] Confirm branch is `russian` and no other edition branch was created.
- [ ] Confirm all CI, Android and Desktop workflows completed successfully on the current commit.
- [ ] Confirm the Android artifact name and signing mode match the trigger.
- [ ] Confirm Windows MSI and Portable build successfully.
- [ ] Confirm Linux AppImage, DEB and tar.gz build successfully.
- [ ] Verify Linux/Windows `SHA256SUMS.txt`.
- [ ] Smoke-test launch and basic drawing on the target platform.
- [ ] Check the package can coexist with upstream ProCraft and the Lang Pack demo.
- [ ] Update documentation/version notes if the upstream baseline changed.
