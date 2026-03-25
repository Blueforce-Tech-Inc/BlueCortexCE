#!/bin/bash
# go-sdk-unit-test.sh — Run ALL Go SDK unit tests across all submodules.
#
# The Go SDK root module + each integration layer (eino, genkit, langchaingo)
# have separate go.mod files. `go test ./...` from root only covers root + dto.
# This script runs tests for ALL submodules.
#
# Usage:
#   bash scripts/go-sdk-unit-test.sh
#   bash scripts/go-sdk-unit-test.sh -v   (verbose)

set -e

# Resolve repo root (directory containing this script's parent)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SDK_DIR="$REPO_ROOT/go-sdk/cortex-mem-go"
VERBOSE="${1:-}"
TEST_FLAGS="-count=1"
if [ "$VERBOSE" = "-v" ]; then
    TEST_FLAGS="$TEST_FLAGS -v"
fi

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

TOTAL=0
PASSED=0
FAILED=0
ERRORS=""

run_tests() {
    local subdir="$1"
    local name="$2"
    ((TOTAL++))
    echo -e "${YELLOW}Testing: $name${NC}"
    local target_dir="$SDK_DIR"
    if [ -n "$subdir" ]; then
        target_dir="$SDK_DIR/$subdir"
    fi
    if (cd "$target_dir" && go test $TEST_FLAGS ./... 2>&1); then
        ((PASSED++))
        echo -e "${GREEN}✅ $name: PASSED${NC}"
    else
        ((FAILED++))
        ERRORS="$ERRORS\n  - $name"
        echo -e "${RED}❌ $name: FAILED${NC}"
    fi
    echo ""
}

echo "=========================================="
echo "Go SDK Unit Tests — All Submodules"
echo "=========================================="
echo ""

run_tests "" "Root (client + dto)"
run_tests "eino" "Eino integration"
run_tests "genkit" "Genkit integration"
run_tests "langchaingo" "LangChainGo integration"

echo "=========================================="
echo "Results: $PASSED/$TOTAL passed, $FAILED failed"
echo "=========================================="

if [ $FAILED -gt 0 ]; then
    echo ""
    echo -e "${RED}Failed modules:${NC}"
    echo -e "$ERRORS"
    exit 1
fi

echo ""
echo "🎉 All Go SDK tests passed!"
