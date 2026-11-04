#!/usr/bin/env bash
# Verify that the viewer committed under backend static is what webui/src builds now.
#
# The check builds the viewer in a throwaway copy of webui (scripts/ + src/ +
# package.json, with node_modules symlinked), so it never writes to the submodule,
# then byte-compares the build against backend/src/main/resources/static.
#
# The build uses the pinned toolchain in scripts/viewer-build (its own lockfile),
# not webui/node_modules: the submodule has no lockfile, so two machines running
# `npm install` can get different react patch versions and produce different
# minified bytes for identical sources.
#
# Exit codes:
#   0  backend static matches a fresh build
#   1  drift (prints per-file hashes and rebuild instructions)
#   2  cannot build here (scripts/viewer-build/node_modules missing); callers may treat this as SKIP
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Overridable so the drift path can be exercised without touching the checkout.
WEBUI="${VIEWER_WEBUI_DIR:-$ROOT/webui}"
STATIC="${VIEWER_STATIC_DIR:-$ROOT/backend/src/main/resources/static}"
DEPS="${VIEWER_DEPS_DIR:-$ROOT/scripts/viewer-build/node_modules}"

if [[ ! -f "$WEBUI/package.json" || ! -d "$DEPS/esbuild" || ! -d "$DEPS/react" ]]; then
  echo "SKIP: the pinned viewer toolchain is not installed; run (cd scripts/viewer-build && npm ci) to enable the drift check." >&2
  exit 2
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/viewer-bundle-check.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
cp -R "$WEBUI/scripts" "$TMP/scripts"
cp -R "$WEBUI/src" "$TMP/src"
cp "$WEBUI/package.json" "$TMP/package.json"
ln -s "$DEPS" "$TMP/node_modules"

if ! node "$TMP/scripts/build-viewer.js" > "$TMP/build.log" 2>&1; then
  cat "$TMP/build.log"
  echo "FAIL: the viewer build itself failed; fix webui first." >&2
  exit 1
fi

DRIFT=0
compare() {
  local rel="$1" built="$2" shipped="$3"
  if [[ ! -f "$shipped" ]]; then
    echo "DRIFT: $rel is missing from backend static"
    DRIFT=1
    return
  fi
  if ! cmp -s "$built" "$shipped"; then
    echo "DRIFT: $rel"
    echo "  built  : $(shasum -a 256 "$built" | cut -d' ' -f1)  $(wc -c < "$built" | tr -d ' ') bytes"
    echo "  shipped: $(shasum -a 256 "$shipped" | cut -d' ' -f1)  $(wc -c < "$shipped" | tr -d ' ') bytes"
    DRIFT=1
  fi
}

compare "viewer-bundle.js" "$TMP/plugin/ui/viewer-bundle.js" "$STATIC/viewer-bundle.js"
compare "index.html" "$TMP/plugin/ui/viewer.html" "$STATIC/index.html"

shopt -s nullglob
for font in "$TMP"/plugin/ui/assets/fonts/*; do
  name="$(basename "$font")"
  compare "assets/fonts/$name" "$font" "$STATIC/assets/fonts/$name"
done
for icon in "$TMP"/plugin/ui/icon-thick-*.svg; do
  name="$(basename "$icon")"
  compare "$name" "$icon" "$STATIC/$name"
done

if [[ "$DRIFT" -ne 0 ]]; then
  cat >&2 <<'EOF'

The committed viewer under backend/src/main/resources/static no longer matches webui/src.
Rebuild and redeploy:

  (cd scripts/viewer-build && npm ci)      # pinned build toolchain, if not installed
  (cd webui && node scripts/build-viewer.js)
  ./scripts/deploy-webui.sh

Then commit backend/src/main/resources/static/. If you do not intend to update the
submodule, restore its build output afterwards: git -C webui checkout -- plugin/ui
EOF
  exit 1
fi

echo "PASS: backend static viewer matches a fresh build of webui/src"
