# Edition profiles

The consolidation candidate uses one source tree and two build profiles. This document describes the temporary edition-profiles branch until it is merged into main.

## Lang Pack Demo

Purpose: community translator demo with the Russian dictionary and custom language-pack manager.

- Desktop Cargo feature: language-packs (enabled by default).
- Windows packaging: set EDITION=langpack.
- Linux packaging: set EDITION=langpack.
- Android: pass -Pprocraft.edition=langpack.
- Android package suffix: .ru.demo.
- Custom pack load/override and manager settings are enabled.

## Russian Edition

Purpose: the built-in Russian translation without the custom Language Packs manager.

- Desktop Cargo build: use --no-default-features. The language-packs Rust module and manager UI/settings are cfg-excluded.
- Windows packaging: set EDITION=ru.
- Linux packaging: set EDITION=ru.
- Android: pass -Pprocraft.edition=ru.
- Android package suffix: .ru.
- Android BuildConfig flag disables manager initialization, the settings entry and custom-pack translation overrides. The Android manager source currently remains compiled into the APK; full source-level exclusion is a future hardening task.

## Local build examples

Desktop, Lang Pack Demo:

```bash
cargo build --release --locked -p procraft-desktop --features language-packs
EDITION=langpack bash packaging/linux/package.sh
```

Desktop, Russian Edition:

```bash
cargo build --release --locked -p procraft-desktop --no-default-features
EDITION=ru bash packaging/linux/package.sh
```

Windows packaging uses the same EDITION values:

```powershell
$env:EDITION = 'langpack' # or 'ru'
./packaging/windows/package.ps1
```

Android:

```bash
cd android
./gradlew :app:assembleRelease -Pprocraft.abis=arm64-v8a -Pprocraft.edition=langpack
./gradlew :app:assembleRelease -Pprocraft.abis=arm64-v8a -Pprocraft.edition=ru
```

## CI acceptance criteria

Before merging the profile branch:

- [ ] Desktop translation tests pass in both Cargo profiles.
- [ ] Language-pack runtime tests pass in the Lang Pack profile.
- [ ] Android unit tests pass in both profiles.
- [ ] Android APK workflow builds both profiles and verifies APK signatures.
- [ ] Windows MSI/portable and Linux AppImage/DEB/tar.gz builds complete for both profiles.
- [ ] Both APKs install side-by-side with different package IDs.
- [ ] Russian APK hides the manager and custom packs do not override built-in Russian.
- [ ] Desktop Russian binary does not contain the language-pack feature/module.
- [ ] Windows and Linux editions install, upgrade and uninstall independently.
- [ ] Smoke tests are performed on Android, Windows and Linux.

## Branch retirement

The legacy russian branch remains available while validation is pending. After this branch is merged to main and both profiles are accepted, delete russian in GitHub Branches settings. Its commit history remains reachable from the repository's commit graph; do not force-rewrite either branch history.
