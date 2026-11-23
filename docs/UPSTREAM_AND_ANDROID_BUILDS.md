# Building and updating the ProCraft community edition

The repository maintains one edition on the `main` branch. It includes the Russian UI and the Community Language Packs manager for Android, Windows and Linux Desktop.

## Main workflows

- [Main action — translation coverage and Android checks](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Amain)
- [Android ARM64 APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Amain)
- [Windows and Linux Desktop packages](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Amain)

After a successful run, download the artifact from its **Artifacts** section. Artifacts are temporary and retained for 30 days.

## Package names

- **Android:** `ProCraft-LangPack-Android-arm64` contains the ARM64 APK. It is debug-signed and should be treated as a test install; it may not update an APK signed with another key.
- **Windows:** `ProCraft-LangPack-Windows-x64` contains an MSI and portable ZIP. Extract the ZIP before running.
- **Linux:** `ProCraft-LangPack-Linux-x86_64` contains AppImage, DEB and tar.gz packages.

Desktop packages use an edition-specific identity so they can coexist with upstream ProCraft. Check the workflow result and checksums before distributing an artifact.

## Update from upstream

Do not replace the repository with a fresh upstream checkout. Preserve community changes and merge upstream changes into `main`.

One-time clone setup:

```bash
git remote add upstream https://github.com/artcraft-android/procraft.git
git fetch upstream
```

Update:

```bash
git switch main
git fetch upstream
git merge upstream/main
```

For the ProCraft v0.0.7-beta baseline, upstream consolidated several standalone plugins into larger plugins (including Krita Brushes, ColorDrop tools, Interface, Effects, Layer fills and Layer tools). Keep the upstream plugin manifests and bundled files. Do not restore obsolete standalone plugin folders when carrying community changes forward.

Resolve conflicts by keeping upstream functional changes and reapplying community translation changes only where needed. Then run the Main workflow and both package workflows; test the APK on an Android tablet and Desktop packages on their target operating systems.

For a larger update, create a short-lived branch and PR into `main` so conflicts and regressions are visible.

## Localization checks

The Main workflow verifies:

- identical shared and Android source catalogs;
- complete Russian coverage on Android and Desktop;
- identical translations for all shared keys;
- a complete importable sample pack and matching readable JSON preview;
- placeholder preservation and the 512-character source-key limit.

Update both translation dictionaries, the sample pack and its preview when adding a catalog entry. The format remains v1 unless the serialization contract itself changes.
