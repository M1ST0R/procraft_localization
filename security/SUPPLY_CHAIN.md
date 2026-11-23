# Supply-chain hardening — GitHub Actions and build tools

**Last updated:** 2026-11-23  
**Applies to:** `main` (Lang Pack Demo) and `russian` (Russian Edition)

This document records dependency pinning and integrity checks added to the CI and packaging workflows. The two existing edition branches were updated directly. No extra long-lived branch is required.

## GitHub Actions pinned by immutable commit SHA

All external Actions used by the three retained workflows are pinned to a full 40-character commit SHA. The version label after each SHA is a human-readable reference, not the value used for resolution.

| Action | Pinned commit | Upstream release |
|---|---|---|
| `actions/checkout` | `3d3c42e5aac5ba805825da76410c181273ba90b1` | v7.0.1 |
| `actions/setup-java` | `d1374f8ac74474200d16ef8ecee22c7ae7a51880` | v6.3.0 |
| `actions/upload-artifact` | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` | v7.0.2 |
| `gradle/actions/setup-gradle` | `8da46b539cd8fe6ab27ca75128919a18d757604c` | v6.4.1 |
| `Swatinem/rust-cache` | `d1527c8f67ef9af357290475d4af4b0fab8f12e0` | v2.9.3 |
| `dtolnay/rust-toolchain` | `df2a88e9fe4098bad09cb42c3dec7187f15861fc` | pinned upstream commit (2026-11-15) |

When updating an Action, resolve the intended release tag to its commit SHA, review the release notes and source, and update both `main` and `russian`. Do not replace a SHA with a mutable major tag.

## Build tool versions and lockfiles

- Rust toolchain: `1.99.0` (explicitly selected in each Rust workflow).
- Android NDK: `29.0.14206865`.
- Java: Temurin 17. The setup action verifies authoritative vendor checksums when provided.
- `cargo-ndk`: exact version `4.1.2`, installed with `--locked`.
- WiX Toolset and UI/Util extensions: `5.0.2`.
- nfpm: `2.48.0`, downloaded from its versioned release and checked against a pinned SHA-256 digest.
- AppImage packer: `appimagetool 1.9.1`, downloaded from its versioned release and SHA-256 verified.
- AppImage runtime: `20251108`, separately downloaded and SHA-256 verified before being embedded into the generated AppImage.
- Windows DirectX Shader Compiler: version `v1.9.2609`; its archive SHA-256 is pinned in `packaging/windows/package.ps1`.

All Cargo build/package commands use the repository lockfile; Cargo package installation uses a locked resolution. Do not remove `Cargo.lock` or update it without reviewing the dependency changes.

## Pinned artifact digests

| Artifact | SHA-256 |
|---|---|
| nfpm 2.48.0 Linux x86_64 archive | `6d7f9a28b2f397934403950fcbba83f598b08d02edbe79a0815b3b4897341750` |
| appimagetool 1.9.1 x86_64 AppImage | `ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0` |
| type2-runtime 20251108 x86_64 | `2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d` |

The hashes match the asset digests published by the corresponding upstream GitHub releases:

- [nfpm v2.48.0](https://github.com/goreleaser/nfpm/releases/tag/v2.48.0)
- [appimagetool 1.9.1](https://github.com/AppImage/appimagetool/releases/tag/1.9.1)
- [type2-runtime 20251108](https://github.com/AppImage/type2-runtime/releases/tag/20251108)

The Linux packaging script validates the AppImage packer and runtime every time before execution. It no longer downloads from the mutable `continuous` release.

## Workflow permissions and credentials

- All three workflows request only `contents: read`.
- Checkout steps set `persist-credentials: false`; jobs do not need a write-capable Git credential after checkout.
- Android release signing material is restricted to trusted owner-triggered events on `russian`. Other builds use a temporary debug key.
- Artifacts are uploaded with a 30-day retention period. These Actions artifacts are test outputs, not signed official releases.

## Remaining limits

Pinning actions and build tools does not make the entire build bit-for-bit reproducible. GitHub-hosted runner images (`ubuntu-24.04`, `ubuntu-22.04`, `windows-2025`), OS package repositories and Java 17 patch resolution continue to evolve. The Rust dependency graph is lockfile-controlled, but build output may still vary with runner image, timestamps and external tool behavior.

The Android SDK command-line tools are supplied by the hosted runner image; the NDK itself is explicitly versioned. A future hardening pass could pin the SDK command-line tools archive and add a reproducible-build/attestation pipeline.

## Maintenance checklist

1. Update the relevant version label and full SHA together for each Action.
2. Review the upstream release commit and security advisories before changing a pin.
3. Update the same pin in both `main` and `russian`.
4. For downloaded binaries, update the version, URL and SHA-256 together; verify the digest against the upstream release metadata.
5. Do not use mutable `master`, `main`, `latest` or `continuous` references for executable build dependencies.
6. Run CI, Android APK and Desktop workflows on both edition branches after changing the toolchain.
