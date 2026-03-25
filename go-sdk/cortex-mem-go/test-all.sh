#!/usr/bin/env bash
# test-all.sh — Run all Go SDK tests across all modules.
# The Go SDK uses separate Go modules for integration layers (genkit, eino, langchaingo),
# so `go test ./...` from the root only tests core + dto. This script tests everything.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="$SCRIPT_DIR"

PASS=0
FAIL=0
MODULES=("." "genkit" "eino" "langchaingo")

echo "=== Go SDK: Full Test Suite ==="
echo ""

for mod in "${MODULES[@]}"; do
    echo "--- Module: ${mod} ---"
    if (cd "$SDK_DIR/$mod" && go test -cover -count=1 ./... 2>&1); then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
    fi
    echo ""
done

echo "=== Summary ==="
echo "Passed: $PASS/${#MODULES[@]}"
echo "Failed: $FAIL/${#MODULES[@]}"

if [ "$FAIL" -gt 0 ]; then
    echo "❌ Some tests failed!"
    exit 1
else
    echo "✅ All tests passed!"
    exit 0
fi
