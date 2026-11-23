# Security Review Scope — Community Language Packs

**Date:** 2026-11-23  
**Target branch:** `main`  
**Reviewed commit:** `19568d8408835d68b5d9436516175b49e4da00a2`

## In scope

- Android and Desktop language-pack import and JSON decoding.
- Format-version, pack-ID, locale, metadata and translation-entry validation.
- Resource bounds: file size, entry count, individual string lengths, JSON depth and dynamic templates.
- Placeholder validation and runtime substitution.
- Pack storage paths, activation and deletion.
- Android FileProvider sharing and backup-related manifest configuration.
- Language catalog and translation lookup changes.
- Workflow permissions for the build workflows.

## Out of scope

- Full ProCraft application security, GPU renderer, brush engine, plugins and unrelated import/export formats.
- Full dependency/SBOM or supply-chain review.
- Network services and future translation marketplace (not part of this file-based feature).
- Organization-level GitHub permissions, secrets, branch protection and external collaborator settings.
- Runtime penetration testing, fuzzing and independent agent verification.

## Threat model

The principal untrusted input is a user-selected `.procraft-lang` file received from another person or source. The review considered malformed and oversized data, path manipulation, placeholder abuse, unintended code execution and accidental file exposure.

## Method

Manual source/configuration review of the listed paths, cross-checking Android/Desktop implementations against the shared JSON Schema and the regression tests. GitHub Actions workflows were inspected for their declared permissions and build/test steps.
