# Building and updating the ProCraft community editions

This repository has two branches with different purposes:

- `main` — Lang Pack Demo: built-in Russian translations, the Community Language Packs editor and the full sample pack.
- `russian` — Russian Edition: a separate Russian UI build without the Language Packs manager.

Keep the branches separate. Do not copy one branch over the other.

## Main branch — Lang Pack Demo

- [CI — translation coverage and Android checks](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Amain)
- [Android ARM64 APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Amain)
- [Windows and Linux Desktop packages](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Amain)

Artifacts are retained for 30 days. Download them from a successful workflow run.

- Android artifact: `ProCraft-LangPack-Android-arm64` (debug-signed test APK).
- Windows artifact: `ProCraft-LangPack-Windows-x64` (MSI and portable ZIP).
- Linux artifact: `ProCraft-LangPack-Linux-x86_64` (AppImage, DEB and tar.gz).

The CI workflow audits all 1,374 catalog entries, checks identical Android/Desktop Russian values, validates the full sample pack and its JSON preview, then runs Android lint and unit tests.

## Russian branch — Russian Edition

- [CI](https://github.com/M1ST0R/procraft_localization/actions/workflows/ci.yml?query=branch%3Arussian)
- [Android ARM64 APK](https://github.com/M1ST0R/procraft_localization/actions/workflows/android-apk.yml?query=branch%3Arussian)
- [Windows and Linux Desktop packages](https://github.com/M1ST0R/procraft_localization/actions/workflows/desktop-build.yml?query=branch%3Arussian)

Artifacts are retained for 30 days.

- Android package ID: `io.github.artcraftandroid.procraft.ru`.
- Windows artifact: `ProCraft-RU-Windows-x64`.
- Linux artifact: `ProCraft-RU-Linux-x86_64`.

The Russian branch uses release signing secrets only for trusted owner-triggered pushes/manual runs. Pull requests and other builds use a temporary debug key. A debug-signed APK may not update an installation signed with a different certificate.

Secrets, if release signing is configured:

- `PROCRAFT_KEYSTORE_BASE64`
- `PROCRAFT_KEYSTORE_PASSWORD`
- `PROCRAFT_KEY_ALIAS`
- `PROCRAFT_KEY_PASSWORD`

Keep the keystore and passwords in secure offline backups. Losing the key prevents updating installs signed with that key.

## Update from upstream

Do not replace either community branch with a fresh upstream checkout. Preserve community changes and merge upstream changes into the branch being maintained.

One-time clone setup:

```bash
git remote add upstream https://github.com/artcraft-android/procraft.git
git fetch upstream
```

Update the selected branch:

```bash
git switch main       # or: git switch russian
git fetch upstream
git merge upstream/main
```

For the ProCraft v0.0.7-beta baseline, upstream consolidated several standalone plugins into larger plugins (including Krita Brushes, ColorDrop tools, Interface, Effects, Layer fills and Layer tools). Keep upstream plugin manifests and bundled files. Do not restore obsolete standalone plugin folders when carrying community changes forward.

Resolve conflicts by keeping upstream functional changes and reapplying the relevant Russian translation changes only where needed. On `main`, also update both translation dictionaries, the sample pack and its readable preview when the source catalog changes. Run the CI workflow and the package workflows for the branch you updated; test APKs on an Android tablet and Desktop packages on their target OS.

For a larger update, create a short-lived branch and PR into `main` or `russian` so conflicts and regressions are visible.
