# Russian Edition — build and maintenance guide

> **Active branch policy.** The repository intentionally keeps two independent long-lived branches: `main` for the Community Language Packs demo and `russian` for the standalone Russian-translation edition. Do not merge the editions or create another permanent edition branch.

- `russian`: built-in Russian UI for Android, Windows and Linux; no Community Language Packs manager/editor.
- `main`: Community Language Packs demo, including the built-in Russian language and custom-pack editor.

Maintain and update this branch independently. Apply Russian translation and Russian-build fixes here; keep demo-only custom language-pack functionality on `main`.

## 1. Get the latest builds

Use the live GitHub Actions pages:

- [Russian Android APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Arussian)
- [Russian Windows and Linux Desktop builds](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Arussian)
- [Russian CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Arussian)

Open the newest run for the `russian` branch, check that its conclusion is **success**, then download its artifact from the **Artifacts** section.

Actions artifacts are temporary and are currently retained for 30 days. They are community test builds, not permanent upstream releases. The published [release 0.0.4](https://github.com/M1ST0R/procraft_localization/releases/tag/0.0.4) is the `main` Lang Pack Demo build, not a Russian Edition release.

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

GitHub Issues and Discussions are disabled for this fork. Share the report in the [ProCraft Discord](https://discord.gg/gypGes3Uca) or submit a Pull Request, and include:

1. the exact English text shown by the app;
2. the screen, panel or menu where it appears;
3. Android/Windows/Linux and app build/run number;
4. a screenshot;
5. the proposed Russian wording, if known.

For clipping or overlapping text, include the device resolution or desktop window size, display scaling and orientation. A layout bug may need a UI change rather than a shorter translation.

## 3. Local development builds

Use the existing branch; do not create a permanent branch for the Russian edition.

### Windows

Requirements: Rust toolchain `1.99.0`, MSVC build tools, and .NET/WiX v5.0.2 with the UI and Util extensions.

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

Do not replace this community branch with a fresh upstream checkout. Merge the exact upstream release tag into the existing `russian` branch so the Russian dictionaries, tests and packaging remain intact.

The repository includes `tools/merge_upstream_release.sh`. It checks that the current branch is `russian`, requires a clean working tree, fetches the exact upstream tag `v0.0.8-beta` and stages a no-commit merge. It does not push, create a branch or resolve conflicts automatically.

From a clean local clone:

```bash
git switch russian
git pull --ff-only origin russian
bash tools/merge_upstream_release.sh russian
```

Resolve conflicts manually. Keep upstream engine/brush/plugin behavior and preserve the Russian-only UI/build identity. Do not copy the Community Language Packs manager, editor or Hub from `main`.

Before committing, run:

```bash
cargo fmt --all -- --check
cargo clippy --locked --workspace --all-targets -- -D warnings
cargo test --locked -p procraft-desktop translation::tests
cargo test --locked -p procraft-core --lib project::signature_tests
```

Then run Russian CI, the Android APK workflow and Windows/Linux Desktop packaging. Smoke-test brush imports (including Krita), default brush loading, pressure/smoothing, dual/mixing brushes, brush sounds, export/import and plugin loading on Android and Desktop. Check MSI/Portable or DEB/AppImage can coexist with upstream ProCraft and the Lang Pack edition.

Commit and push only after validation:

```bash
git commit -m "Merge upstream v0.0.8-beta"
git push origin russian
```

The upstream iPad IPA is not a build produced by this fork. This branch currently targets Android, Windows and Linux.

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
