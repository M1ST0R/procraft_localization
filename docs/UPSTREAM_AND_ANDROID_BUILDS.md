# Maintaining the Russian community builds

## Build an APK

The workflow `.github/workflows/android-apk.yml` builds an ARM64 release APK when
`translation/russian` is updated, and can also be started from **Actions → Android APK → Run workflow**.

After a successful run, download **ProCraft-RU-Android-arm64** from the run's Artifacts section.
GitHub keeps this artifact for 30 days.

## Build desktop packages

The workflow `.github/workflows/desktop-build.yml` builds the desktop app for Windows x64 and Linux x86_64 when relevant desktop, engine, plugin, asset or packaging files change on `translation/russian`. It can also be started manually from **Actions → Desktop RU builds → Run workflow**.

After a successful run, download these artifacts:

- **ProCraft-RU-Windows-x64** — an `.msi` installer and a portable `.zip`. The ZIP contains `ProCraft.exe`, the required DirectX Shader Compiler DLLs and licence notices; extract it before launching the executable.
- **ProCraft-RU-Linux-x86_64** — an `.AppImage`, a `.deb` package and a `.tar.gz` archive. The packages are built on Ubuntu 22.04 for x86_64 Linux systems with glibc 2.35 or newer.

Desktop artifacts are kept by GitHub for 30 days. Version-tagged runs of `.github/workflows/release.yml` can attach Windows and Linux desktop packages to a versioned GitHub Release.

The desktop translation is maintained separately in `desktop/src/translation.rs` and is applied through the shared egui text-rendering helpers. It currently covers common interface labels, canvas information, layers, colours, settings, brush libraries and Brush Studio. Unrecognised or not-yet-translated strings intentionally remain in English.


The community build uses the package ID
`io.github.artcraftandroid.procraft.ru`, so it can be installed alongside the official app.

## Keep the signing key stable

Android only accepts an in-place update when the package ID and signing certificate match.
Do not generate a new key for every build and do not lose the original key.

Generate a PKCS12 key once on a trusted computer:

```bash
keytool -genkeypair -v -keystore procraft-release.p12 -storetype PKCS12 -alias procraft -keyalg RSA -keysize 2048 -validity 10000
```

Add these repository Actions secrets under **Settings → Secrets and variables → Actions**:

- `PROCRAFT_KEYSTORE_BASE64`: Base64-encoded bytes of `procraft-release.p12`
- `PROCRAFT_KEYSTORE_PASSWORD`: keystore password
- `PROCRAFT_KEY_ALIAS`: `procraft`
- `PROCRAFT_KEY_PASSWORD`: key password

On Linux, encode the file with `base64 -w 0 procraft-release.p12`.
On PowerShell, use
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("procraft-release.p12"))`.

Keep an offline backup of the keystore and passwords. If the key is lost, future APKs signed with a new key cannot update the installed app; the app would have to be uninstalled first, which may delete its data.

The workflow currently falls back to the runner's debug key when the secrets are absent. That APK can be useful for a first smoke test, but it is not suitable for reliable updates: GitHub-hosted runners do not share a persistent debug signing key.

## Bring in upstream ProCraft changes

Keep translation-specific edits on `translation/russian`. Do not replace this branch with a fresh copy of upstream.

One-time setup in a local clone:

```bash
git remote add upstream https://github.com/artcraft-android/procraft.git
git fetch upstream
```

When upstream publishes changes:

```bash
git switch translation/russian
git fetch upstream
git merge upstream/main
```

For v0.0.7-beta, several standalone plugins were consolidated into larger plugins (including Krita Brushes, ColorDrop tools, Interface, Effects, Layer fills and Layer tools). Keep the new upstream plugin manifests and bundled plugin files; do not restore the old standalone plugin directories when carrying translation changes forward.

Resolve conflicts carefully, keeping upstream functional changes and reapplying the Russian strings or UI changes where upstream touched the same code. Then run the Android build/tests, commit the merge and push:

```bash
git push origin translation/russian
```

The push starts the APK workflow. Review the app on a tablet before sharing the new artifact.

For a large upstream change, merge it on a separate update branch first and open a pull request into `translation/russian`. This makes conflicts and regressions easier to review.

## Important versioning note

The CI workflow assigns each build a higher Android `versionCode`, so a newly built APK can update the previous CI build when signed with the same key. Official upstream releases and this community build are separate packages.
