# Maintaining the Russian community edition

This guide is for the `russian` branch. The `main` branch is the Lang Pack demo edition. Both use the same upstream ProCraft base, but their translation and edition packaging are separate.

## Build and download

- [Android APK workflow for russian](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Arussian)
- [Desktop builds workflow for russian](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Arussian)
- [CI for russian](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Arussian)

After a successful run, download the artifact from its **Artifacts** section. Artifacts are temporary and retained for 30 days.

Desktop artifact contents:

- **Windows:** `ProCraft-RU-Windows-x64` contains an MSI installer and portable ZIP. The portable archive includes `ProCraft.exe`, DirectX Shader Compiler DLLs and license notices; extract before running.
- **Linux:** `ProCraft-RU-Linux-x86_64` contains AppImage, DEB and tar.gz formats.

Windows MSI and Linux DEB/AppImage/tar packages now use edition-specific product/package names and app IDs to avoid overwriting official ProCraft or the Lang Pack edition. Wait for successful workflow runs from the updated packaging commits before installing these packages alongside other editions.

The Android package ID for the Russian edition is `io.github.artcraftandroid.procraft.ru`. Main's demo APK uses a separate `.ru.demo` suffix. Debug-signed APKs may not update other builds; update signing requires the same package ID, signing certificate and increasing version code.

## Release signing (owner-only)

The Android workflow uses release signing secrets only for trusted owner-triggered pushes/manual runs on `russian`. Pull requests and other builds use a temporary debug key.

Secrets, if release signing is configured:

- `PROCRAFT_KEYSTORE_BASE64`
- `PROCRAFT_KEYSTORE_PASSWORD`
- `PROCRAFT_KEY_ALIAS`
- `PROCRAFT_KEY_PASSWORD`

Keep the keystore and passwords in secure offline backups. Losing the key prevents updating installs signed with that key.

## Update from upstream

Do not replace the community branch with a fresh upstream checkout. Preserve community translation changes and merge upstream changes into the `russian` branch.

One-time clone setup:

```bash
git remote add upstream https://github.com/artcraft-android/procraft.git
git fetch upstream
```

Update:

```bash
git switch russian
git fetch upstream
git merge upstream/main
```

For the ProCraft v0.0.7-beta baseline, upstream consolidated several standalone plugins into larger plugins (including Krita Brushes, ColorDrop tools, Interface, Effects, Layer fills and Layer tools). Keep the upstream plugin manifests and bundled files. Do not restore obsolete standalone plugin folders when carrying translation changes forward.

Resolve conflicts by keeping upstream functional changes and reapplying Russian UI changes only where needed. Then run CI and build workflows; test the APK on a tablet and Desktop packages on the target OS.

For a larger update, create a short-lived branch and PR into `russian` so conflicts and regressions are visible.

## Signing and versioning notes

Android can update an installed community APK only when the package ID and signing certificate match and the new version code is greater. CI debug keys are not persistent across runners, so debug artifacts should be treated as test installs, not reliable upgrade channels.

There is no community Release workflow in this repository. Use Actions artifacts for these community builds.
