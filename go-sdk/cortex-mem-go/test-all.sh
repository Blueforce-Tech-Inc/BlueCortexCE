#!/usr/bin/env bash
# test-all.sh — Run all Go SDK tests across all modules.
# The Go SDK is 9 separate Go modules, not one: the integration layers
# (genkit, eino, langchaingo) and every directory under examples/ carry their
# own go.mod, so `go test ./...` from the root only tests core + dto.
# This script tests everything.
#
# The module list is DISCOVERED from go.mod files rather than hard-coded, so a
# newly added module cannot silently escape the suite. (An earlier version
# listed only ".", "genkit", "eino" and "langchaingo", which left the 33 tests
# in examples/http-server unrun while still reporting "All tests passed!".)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="$SCRIPT_DIR"

PASS=0
FAIL=0
TOTAL=0

# Discover every module: the directory holding each go.mod, relative to SDK_DIR.
# (Kept POSIX/bash-3.2 compatible: macOS still ships bash 3.2, which has no mapfile.)
MODULES=()
while IFS= read -r mod; do
    [ -n "$mod" ] && MODULES+=("$mod")
done < <(cd "$SDK_DIR" && find . -name go.mod -not -path '*/node_modules/*' \
    | xargs -n1 dirname | sed 's|^\./||' | sort)

echo "=== Go SDK: Full Test Suite ==="
echo ""

for mod in "${MODULES[@]}"; do
    TOTAL=$((TOTAL + 1))
    echo "--- Module: ${mod} ---"
    if (cd "$SDK_DIR/$mod" && go test -cover -count=1 ./... 2>&1); then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
    fi
    echo ""
done

echo "=== Summary ==="
echo "Passed: $PASS/$TOTAL"
echo "Failed: $FAIL/$TOTAL"

if [ "$FAIL" -gt 0 ]; then
    echo "❌ Some tests failed!"
    exit 1
else
    echo "✅ All tests passed!"
    exit 0
fi
