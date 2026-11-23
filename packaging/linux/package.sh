#!/usr/bin/env bash
# Packages the desktop app for Linux into $DIST (default dist/), <arch> being this machine's:
#
#   procraft-<version>-linux-<arch>.AppImage   any distro with glibc as new as the build host's
#   procraft-<version>-linux-<arch>.deb        Debian, Ubuntu, Mint, Pop!_OS, ...
#   procraft-<version>-linux-<arch>.tar.gz     the plain tree (bin/, share/)
#
# Usage: packaging/linux/package.sh [--skip-build] [--formats "appimage deb tar"]
#
# VERSION is the release's (default: procraft.version in android/gradle.properties). Needs cargo,
# nfpm for the .deb (https://nfpm.goreleaser.com) and appimagetool for the AppImage (downloaded
# into the target folder if missing). Build on an old distro (CI: Ubuntu 22.04) so the binary
# runs on newer ones. Modelled on SolidCraft's packaging (tangency-labs/solidcraft).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HERE="$ROOT/packaging/linux"
SKIP_BUILD=0
FORMATS="appimage deb tar"
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1; shift ;;
    --formats) FORMATS="$2"; shift 2 ;;
    -h | --help) sed -n '2,13p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
VERSION="${VERSION:-$(sed -n 's/^procraft\.version=//p' "$ROOT/android/gradle.properties" | tr -d '\r')}"
DIST="$(mkdir -p "${DIST:-$ROOT/dist}" && cd "${DIST:-$ROOT/dist}" && pwd)"
TARGET_DIR="${CARGO_TARGET_DIR:-$ROOT/target}"
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64) DEB_ARCH=amd64 ;;
  aarch64 | arm64) ARCH=aarch64; DEB_ARCH=arm64 ;;
  *) echo "unsupported architecture $ARCH" >&2; exit 2 ;;
esac
EDITION="${EDITION:-official}"
case "$EDITION" in
  official) PKG_NAME="procraft"; BIN_NAME="procraft"; APP_NAME="ProCraft"; APP_ID="procraft" ;;
  langpack) PKG_NAME="procraft-langpack"; BIN_NAME="procraft-langpack"; APP_NAME="ProCraft LangPack Demo"; APP_ID="procraft-langpack" ;;
  ru) PKG_NAME="procraft-ru"; BIN_NAME="procraft-ru"; APP_NAME="ProCraft RU"; APP_ID="procraft-ru" ;;
  *) echo "unknown EDITION: $EDITION (expected official, langpack, or ru)" >&2; exit 2 ;;
esac
BASENAME="$PKG_NAME-$VERSION-linux-$ARCH"
has() { case " $FORMATS " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }
echo "==> ProCraft $VERSION for Linux $ARCH ($FORMATS)"

if [ "$SKIP_BUILD" = 0 ]; then
  (cd "$ROOT" && cargo build --release --locked -p procraft-desktop)
fi

# ---- the tree every format holds -----------------------------------------------------------------
WORK="$TARGET_DIR/linux-package"
STAGE="$WORK/root"
rm -rf "$WORK"
install -Dm755 "$TARGET_DIR/release/procraft" "$STAGE/usr/bin/$BIN_NAME"
strip "$STAGE/usr/bin/$BIN_NAME" 2>/dev/null || true
mkdir -p "$STAGE/usr/share/applications" "$STAGE/usr/share/icons"
sed -e "s|@@APP_NAME@@|$APP_NAME|g" -e "s|@@BIN_NAME@@|$BIN_NAME|g" -e "s|@@APP_ID@@|$APP_ID|g" \
  "$HERE/procraft.desktop" > "$STAGE/usr/share/applications/$APP_ID.desktop"
cp -R "$ROOT/assets/app-icon/hicolor" "$STAGE/usr/share/icons/"
while IFS= read -r icon; do mv "$icon" "${icon%/procraft.png}/$APP_ID.png"; done < <(find "$STAGE/usr/share/icons/hicolor" -type f -name procraft.png)
for f in LICENSE-MIT LICENSE-APACHE NOTICE ATTRIBUTION.md; do
  install -Dm644 "$ROOT/$f" "$STAGE/usr/share/doc/$PKG_NAME/$f"
done
if command -v desktop-file-validate >/dev/null; then
  desktop-file-validate "$STAGE/usr/share/applications/$APP_ID.desktop"
fi

# ---- .tar.gz -------------------------------------------------------------------------------------
if has tar; then
  mkdir -p "$WORK/tar"
  cp -R "$STAGE/usr" "$WORK/tar/$BASENAME"
  tar -C "$WORK/tar" -czf "$DIST/$BASENAME.tar.gz" "$BASENAME"
  echo "wrote $DIST/$BASENAME.tar.gz"
fi

# ---- .deb ----------------------------------------------------------------------------------------
if has deb; then
  command -v nfpm >/dev/null || { echo "error: nfpm not found (https://nfpm.goreleaser.com/install/)" >&2; exit 1; }
  export VERSION NFPM_ARCH="$DEB_ARCH"
  # nfpm expands arch/version from the environment; substitute identity and staged file paths first.
  sed \
    -e "s|\${STAGE}|$STAGE|g" \
    -e "s|@@PKG_NAME@@|$PKG_NAME|g" \
    -e "s|@@BIN_NAME@@|$BIN_NAME|g" \
    "$HERE/nfpm.yaml" > "$WORK/nfpm.yaml"
  (cd "$ROOT" && nfpm package -f "$WORK/nfpm.yaml" -p deb -t "$DIST/$BASENAME.deb")
fi

# ---- AppImage ------------------------------------------------------------------------------------
if has appimage; then
  APPDIR="$WORK/$PKG_NAME.AppDir"
  cp -R "$STAGE" "$APPDIR"
  rm -rf "$APPDIR/usr/share/doc"
  sed -e "s|@@BIN_NAME@@|$BIN_NAME|g" -e "s|@@APP_ID@@|$APP_ID|g" "$HERE/AppRun" > "$APPDIR/AppRun"
  chmod +x "$APPDIR/AppRun"
  cp "$STAGE/usr/share/applications/$APP_ID.desktop" "$APPDIR/$APP_ID.desktop"
  cp "$ROOT/assets/app-icon/hicolor/256x256/apps/procraft.png" "$APPDIR/$APP_ID.png"
  ln -s "$APP_ID.png" "$APPDIR/.DirIcon"
  # Pin both the AppImage packer and the embedded runtime. The runtime is executable code
  # prepended to every generated AppImage, so it is checksum-verified independently.
  APPIMAGETOOL_VERSION="1.9.1"
  APPIMAGETOOL_SHA256="ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"
  RUNTIME_VERSION="20251108"
  RUNTIME_SHA256="2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d"
  TOOL="$TARGET_DIR/appimagetool-$ARCH-$APPIMAGETOOL_VERSION.AppImage"
  RUNTIME="$TARGET_DIR/appimage-runtime-$ARCH-$RUNTIME_VERSION"
  if [ ! -f "$TOOL" ]; then
    curl -fsSL -o "$TOOL" "https://github.com/AppImage/appimagetool/releases/download/$APPIMAGETOOL_VERSION/appimagetool-$ARCH.AppImage"
    chmod +x "$TOOL"
  fi
  if [ ! -f "$RUNTIME" ]; then
    curl -fsSL -o "$RUNTIME" "https://github.com/AppImage/type2-runtime/releases/download/$RUNTIME_VERSION/runtime-$ARCH"
    chmod +x "$RUNTIME"
  fi
  printf '%s  %s\n' "$APPIMAGETOOL_SHA256" "$TOOL" | sha256sum --check --status
  printf '%s  %s\n' "$RUNTIME_SHA256" "$RUNTIME" | sha256sum --check --status
  # Extracted and run, so it needs no FUSE in containers or CI. Runtime is injected offline.
  ARCH="$ARCH" APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --runtime-file "$RUNTIME" --no-appstream "$APPDIR" "$DIST/$BASENAME.AppImage"
  echo "wrote $DIST/$BASENAME.AppImage"
fi

echo "==> done"
ls -l "$DIST"
