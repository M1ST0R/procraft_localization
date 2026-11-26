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

The CI workflow audits all 1,377 catalog entries, checks identical Android/Desktop Russian values, validates the full sample pack and its JSON preview, then runs Android lint and unit tests.

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

## Update from upstream — safe procedure

Do not replace either community branch with a fresh upstream checkout, and do not merge `main` into `russian` or vice versa. Preserve community changes and merge the exact upstream release tag into each maintained branch separately.

The repository includes `tools/merge_upstream_release.sh` on both branches. It requires a clean working tree, checks the selected branch, fetches the exact upstream tag `v0.0.8-beta` and stages a no-commit merge. It does not push, create a branch or resolve conflicts automatically.

Run this locally from a clone of the repository.

**Community Language Packs — main**

```bash
git switch main
git pull --ff-only origin main
bash tools/merge_upstream_release.sh main
```

Resolve conflicts manually. Keep upstream engine/brush/plugin behavior, then reapply the relevant localization changes in the existing wrappers and preserve the Language Manager, catalogue, built-in Russian, Hub validation and pack schemas.

Run the checks before committing:

```bash
python3 tools/check_translation_coverage.py
python3 tools/check_community_hub.py
cargo fmt --all -- --check
cargo clippy --locked --workspace --all-targets -- -D warnings
cargo test --locked -p procraft-desktop translation::tests
cargo test --locked -p procraft-desktop language_packs::tests
cargo test --locked -p procraft-core --lib project::signature_tests
```

Then run the Android unit tests/lint, build the APK and run Windows/Linux Desktop packaging. Update the shared Android/Desktop source catalog and `catalog_version` only after reviewing the upstream UI strings. Revalidate every published pack and preserve the format-v1 contract unless its serialization actually changes.

Commit and push only after all checks pass:

```bash
git commit -m "Merge upstream v0.0.8-beta"
git push origin main
```

**Russian Edition — russian**

Repeat the procedure from a clean `russian` checkout:

```bash
git switch russian
git pull --ff-only origin russian
bash tools/merge_upstream_release.sh russian
```

Preserve the Russian-only build and its dictionary; do not copy the Community Language Packs demo or editor into this edition. Run Russian CI, Android APK and Desktop packaging before committing and pushing.

The upstream release changes brush importing, default brushes, dual/mixing brushes, rendering and plugin code. After builds pass, manually smoke-test brush imports (including Krita), default brush loading, pressure/smoothing, dual/mixing brushes, brush sounds, export/import and plugin loading on Android and Desktop. The upstream iPad IPA is not a build produced by this fork.
