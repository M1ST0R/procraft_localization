# Supply-chain hardening — GitHub Actions and build tools

**Last updated:** 2026-11-23  
**Applies to:** `main` and the `edition-profiles` consolidation PR (both Lang Pack Demo and Russian Edition build profiles). The `russian` branch is a legacy snapshot pending retirement.

This document records the controls applied to the repository's CI and packaging workflows. The current workflows are maintained on `main` and tested on `edition-profiles`. Avoid parallel maintenance of the legacy `russian` branch; retire it after consolidation.

## GitHub Actions pinned to immutable commit SHAs

Every external GitHub Action referenced by the three retained workflows is pinned to a full 40-character commit SHA. The comment after the SHA is a human-readable release label; GitHub resolves the SHA, not the label.

- `actions/checkout` — `3d3c42e5aac5ba805825da76410c181273ba90b1` (v7.0.1)
- `actions/setup-java` — `d1374f8ac74474200d16ef8ecee22c7ae7a51880` (v6.3.0)
- `actions/upload-artifact` — `cf430e030ddbb5b0abf93d22962f4752f3646cd9` (v7.0.2)
- `gradle/actions/setup-gradle` — `8da46b539cd8fe6ab27ca75128919a18d757604c` (v6.4.1)
- `Swatinem/rust-cache` — `d1527c8f67ef9af357290475d4af4b0fab8f12e0` (v2.9.3)
- `dtolnay/rust-toolchain` — `df2a88e9fe4098bad09cb42c3dec7187f15861fc` (pinned upstream commit, 2026-11-15)

The first three pins were checked against the corresponding upstream release tags; the Gradle and rust-cache annotated tags were dereferenced to confirm their target commit SHAs. Do not replace these pins with mutable `@vN`, `@main` or `@master` references.

## Build tool versions and lockfiles

- Rust toolchain: `1.99.0`.
- Android NDK: `29.0.14206865`.
- Java: Eclipse Temurin 17. The major version and distribution are selected, but the patch-level JDK build is supplied by the upstream distribution.
- `cargo-ndk`: exact version `4.1.2`, installed with `--locked`.
- Gradle distribution: `9.5.1`, with `distributionSha256Sum` recorded in `android/gradle/wrapper/gradle-wrapper.properties`.
- WiX Toolset and UI/Util extensions: exact version `5.0.2`.
- nfpm: exact version `2.49.0`, downloaded from its versioned release and checked against its published SHA-256 digest.
- AppImage packer: `appimagetool 1.9.1`, downloaded from a versioned release and SHA-256 verified.
- AppImage runtime: `20251108`, downloaded separately and SHA-256 verified before use.
- Windows DirectX Shader Compiler: `v1.9.2609`; the archive SHA-256 is pinned in `packaging/windows/package.ps1`.

Cargo build/test operations use `--locked`; `cargo-ndk` is installed with `--locked`. Keep `Cargo.lock` committed and review dependency changes before updating it.

## Verified downloaded artifacts

**nfpm 2.49.0 — Linux x86_64**

- Asset: `nfpm_2.49.0_Linux_x86_64.tar.gz`
- SHA-256: `2afaa5ffb5e3493402e31e7c67a5a1fb42133e3f66fc315ca0051934125561c7`
- Upstream release: https://github.com/goreleaser/nfpm/releases/tag/v2.49.0

The GitHub release metadata marks nfpm v2.49.0 immutable and publishes the digest for this exact asset. The workflow downloads the versioned asset and checks the digest before extracting or executing it.

**AppImage tool and runtime**

- `appimagetool 1.9.1` x86_64: `ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0`
- `appimagetool 1.9.1` aarch64: `f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158`
- `type2-runtime 20251108` x86_64: `2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d`
- `type2-runtime 20251108` aarch64: `00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444`

Upstream release pages:

- https://github.com/AppImage/appimagetool/releases/tag/1.9.1
- https://github.com/AppImage/type2-runtime/releases/tag/20251108

Both binaries are downloaded from versioned URLs, verified on every run (including cache hits), and supplied explicitly to appimagetool. The workflow does not use the mutable `continuous` release. Downloads require HTTPS/TLS 1.2.

**Windows DirectX Shader Compiler**

The Windows packaging script pins DXC `v1.9.2609`, verifies the downloaded ZIP using its recorded SHA-256 before extracting it, and checks the machine architecture of the DLLs.

## Workflow permissions and credentials

- The CI, Android APK and Desktop workflows request only `contents: read`.
- Every checkout step sets `persist-credentials: false`; build jobs do not need a write-capable Git credential after checkout.
- The current edition-profile Android workflow builds both editions as debug-signed test APKs and does not expose signing secrets to pull requests. Production release signing should be introduced only through a dedicated trusted release workflow.
- All workflows cancel obsolete runs for the same branch when a newer commit arrives.
- Artifacts include SHA-256 manifests and expire after 30 days. They are temporary test artifacts, not permanent releases.

## Gradle Wrapper validation

The Android workflows use `gradle/actions/setup-gradle`. Since Gradle Actions v4, `setup-gradle` performs Gradle Wrapper validation automatically, so the separate standalone wrapper-validation job was removed. The Android build still executes the checked-in wrapper from `android/gradlew`; the distribution URL and SHA-256 are pinned in its wrapper properties.

See the official Gradle documentation: https://docs.gradle.org/current/userguide/github-actions.html

## What is not fully reproducible yet

The build is hardened against mutable Action tags and unverified downloaded packaging binaries, but it is not bit-for-bit reproducible:

- GitHub-hosted runner images (`ubuntu-24.04`, `ubuntu-22.04`, `windows-2025`) are maintained by GitHub and change over time.
- Temurin 17 is selected by major version rather than an immutable JDK archive digest.
- Ubuntu package repositories are updated during the Linux build; the installed `desktop-file-utils` version is controlled by the runner's package repository.
- Android SDK command-line tools come from the hosted runner image; the NDK version itself is pinned.
- Checksums detect corruption or unexpected binary changes, but the checksum manifest is part of the same Actions artifact. There is not yet a separate signed provenance attestation for each uploaded artifact.

These are recorded limitations, not claims of a fully hermetic or reproducible build.

## Maintenance checklist

1. Resolve each intended GitHub Action release tag to its full commit SHA and review the upstream source before updating it.
2. Update the same action pin in `main` and verify it on the edition-profile PR.
3. For downloaded tools, update the version, exact URL and SHA-256 together.
4. Verify hashes against the upstream release metadata before committing.
5. Keep mutable `master`, `main`, `latest` and `continuous` references out of executable build inputs.
6. Run CI, Android APK and Desktop workflows for both edition profiles after changing build tooling.
7. Never expose Android signing secrets to pull-request builds or untrusted actors.
