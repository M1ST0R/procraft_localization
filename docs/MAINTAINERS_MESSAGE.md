# Maintainer discussion message

Hello ProCraft maintainers,

This repository now contains a working community localization demo on `main`, rather than only a proposal. Android and Desktop users can create, edit, save, activate, import, export, share and delete file-based `.procraft-lang` packs. The maintained `main` branch now carries both the Russian UI and the Language Packs manager.

The current MVP is wired into the existing text-rendering boundaries and uses exact English UI strings as keys (format v1). It is intentionally simple, data-only, offline and cross-platform. The public walkthrough, user guide, schema, sample pack, acceptance checklist and scoped security report are linked from the README.

I would appreciate feedback on:
- whether the current text-rendering integration is an acceptable community demo;
- the exact English-source-key approach for v1 and its limitations;
- whether a future stable-message-ID catalog and shared localization resolver would fit upstream's architecture;
- how upstream would prefer translation contributions and review to be organized.

The current demo is not presented as a final production localization architecture. Feedback can guide whether it should remain a community-maintained implementation or evolve into an upstream PR.

Thank you for your work on ProCraft.
