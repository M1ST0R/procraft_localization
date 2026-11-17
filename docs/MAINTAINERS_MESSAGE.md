# Maintainer discussion message

Hello ProCraft maintainers,

I would like to propose a community localization system for ProCraft.

Because the Android interface uses Kotlin/Jetpack Compose while Desktop uses Rust/egui and both share functionality through the Rust core, adding translations only to Android string resources would not cover the complete application. Maintaining separate forks for each language also makes translations hard to keep in sync with upstream releases.

My proposal is a shared localization layer based on stable message keys and versioned, data-only language packs (`.procraft-lang`). Users could import, create, edit, and export translation variants. Missing entries would fall back to English, and the app would report new or changed source strings after updates. Packs would contain plain text only and would not have plugin permissions or executable code.

This repository contains the proposed pack format, a JSON Schema, a Russian example, and an implementation outline. It is a discussion PoC, not a production-ready patch; I would prefer to align the API and integration points with your direction before preparing a full implementation PR.

Would this approach fit the project's goals? In particular, I would appreciate feedback on whether a shared Rust resolver exposed through UniFFI is a suitable boundary for Android and egui.

Thank you for your work on ProCraft.
