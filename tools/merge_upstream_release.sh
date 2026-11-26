#!/usr/bin/env bash
set -euo pipefail

TARGET_BRANCH="${1:-}"
UPSTREAM_URL="https://github.com/artcraft-android/procraft.git"
RELEASE_TAG="v0.0.8-beta"

usage() {
  echo "Usage: $0 main|russian" >&2
  echo "Run from a clean checkout of the selected branch." >&2
}

if [[ "$TARGET_BRANCH" != "main" && "$TARGET_BRANCH" != "russian" ]]; then
  usage
  exit 2
fi

CURRENT_BRANCH="$(git branch --show-current)"
if [[ "$CURRENT_BRANCH" != "$TARGET_BRANCH" ]]; then
  echo "Refusing: current branch is '$CURRENT_BRANCH'; switch to '$TARGET_BRANCH' first." >&2
  exit 2
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing: working tree must be clean before merging upstream." >&2
  exit 2
fi

if git remote get-url upstream >/dev/null 2>&1; then
  REMOTE_URL="$(git remote get-url upstream)"
  if [[ "$REMOTE_URL" != *"artcraft-android/procraft"* ]]; then
    echo "Refusing: 'upstream' points to '$REMOTE_URL', not the official ProCraft repository." >&2
    exit 2
  fi
else
  git remote add upstream "$UPSTREAM_URL"
fi

echo "Fetching official release tag $RELEASE_TAG..."
git fetch upstream "+refs/tags/$RELEASE_TAG:refs/tags/$RELEASE_TAG"

if git merge-base --is-ancestor "$RELEASE_TAG" HEAD; then
  echo "The selected branch already contains $RELEASE_TAG."
  exit 0
fi

echo "Merging $RELEASE_TAG into $TARGET_BRANCH without committing yet."
echo "Conflicts, if any, must be resolved manually to preserve localization changes."
git merge --no-ff --no-commit "$RELEASE_TAG"

cat <<'EOF'

Upstream merge is staged but NOT committed.

Review and resolve any conflicts, preserving community localization and translation-pack
features. Then run the applicable checks:

  python3 tools/check_translation_coverage.py
  cargo clippy --locked --workspace --all-targets -- -D warnings
  cargo test --locked -p procraft-desktop translation::tests
  cargo test --locked -p procraft-desktop language_packs::tests
  cargo test --locked -p procraft-core --lib project::signature_tests

On main, also run:
  python3 tools/check_community_hub.py

Run Android unit tests and packaging CI as well. If the checks pass, commit the merge:
  git commit -m "Merge upstream v0.0.8-beta"

The script intentionally does not push, force-update refs, or create temporary branches.
EOF
