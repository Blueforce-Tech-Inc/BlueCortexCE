#!/usr/bin/env bash
#
# Run all local E2E test scripts in one pass (excluding Docker suites and test-llm-provider.sh).
#
# Prerequisites:
#   - Backend running and healthy at SERVER_URL (default http://127.0.0.1:37777)
#   - Node.js + npm where required (thin-proxy, openclaw-plugin, folder-claudemd)
#   - curl; jq recommended (export-test, several others)
#
# Usage:
#   ./scripts/run-all-e2e.sh [--fail-fast] [--skip-build]
#
# --skip-build   Passed to regression-test.sh and thin-proxy-test.sh only.
# --fail-fast    Stop on first failing suite (default: run all, exit non-zero if any failed).
#
# Excluded by design (per project convention):
#   - docker-e2e-test.sh, docker-compose-test.sh
#   - test-llm-provider.sh
#
# Note: mcp-streamable-e2e-test.sh runs only if the server exposes Streamable HTTP on /mcp;
#       otherwise it is reported as SKIPPED (not a failure).

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

SERVER_URL="${SERVER_URL:-http://127.0.0.1:37777}"
export SERVER_URL
export JAVA_API_URL="$SERVER_URL"

FAIL_FAST=false
SKIP_BUILD=false
for arg in "$@"; do
  case "$arg" in
    --fail-fast) FAIL_FAST=true ;;
    --skip-build) SKIP_BUILD=true ;;
    --help|-h)
      grep '^#' "$SCRIPT_DIR/run-all-e2e.sh" | head -n 22 | sed 's/^# \{0,1\}//'
      exit 0
      ;;
  esac
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

SUITES_RAN=0
SUITES_FAILED=0
SUITES_SKIPPED=0

banner() {
  echo ""
  echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
}

need_health() {
  if ! curl -sf "${SERVER_URL}/actuator/health" >/dev/null 2>&1; then
    echo -e "${RED}ERROR: No healthy server at ${SERVER_URL}${NC}" >&2
    echo "Start the backend first, e.g. load backend/.env and run the jar." >&2
    exit 1
  fi
}

# Same probe as mcp-streamable-e2e-test.sh
is_streamable_mcp() {
  local mcp="${SERVER_URL}/mcp"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 -X POST "$mcp" \
    -H "Content-Type: application/json" \
    -H "Accept: text/event-stream,application/json" \
    -d '{"jsonrpc":"2.0","id":"probe","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"probe","version":"1.0"}}}' 2>/dev/null || echo "000")
  [ "$code" = "200" ]
}

run_suite() {
  local title="$1"
  shift
  banner "$title"
  SUITES_RAN=$((SUITES_RAN + 1))
  if "$@"; then
    echo -e "${GREEN}[OK]${NC} $title"
    return 0
  else
    echo -e "${RED}[FAIL]${NC} $title"
    SUITES_FAILED=$((SUITES_FAILED + 1))
    if $FAIL_FAST; then
      exit 1
    fi
    return 1
  fi
}

skip_suite() {
  local title="$1"
  local reason="$2"
  banner "$title"
  echo -e "${CYAN}[SKIP]${NC} $reason"
  SUITES_SKIPPED=$((SUITES_SKIPPED + 1))
}

echo ""
echo -e "${YELLOW}Cortex CE — aggregate E2E${NC}"
echo "Repo:     $REPO_ROOT"
echo "SERVER:   $SERVER_URL"
echo "fail-fast: $FAIL_FAST  skip-build (regression+thin-proxy): $SKIP_BUILD"
echo ""

need_health

REG_OPTS=()
THIN_OPTS=()
if $SKIP_BUILD; then
  REG_OPTS+=(--skip-build)
  THIN_OPTS+=(--skip-build)
fi

run_suite "1/10 regression-test.sh" "$SCRIPT_DIR/regression-test.sh" "${REG_OPTS[@]}"
run_suite "2/10 thin-proxy-test.sh" "$SCRIPT_DIR/thin-proxy-test.sh" "${THIN_OPTS[@]}"
run_suite "3/10 webui-integration-test.sh" "$SCRIPT_DIR/webui-integration-test.sh"
run_suite "4/10 mcp-e2e-test.sh" "$SCRIPT_DIR/mcp-e2e-test.sh" "$SERVER_URL"
if is_streamable_mcp; then
  run_suite "5/10 mcp-streamable-e2e-test.sh" "$SCRIPT_DIR/mcp-streamable-e2e-test.sh" "$SERVER_URL"
else
  skip_suite "5/10 mcp-streamable-e2e-test.sh" "Server not in STREAMABLE MCP mode (/mcp not HTTP 200 for initialize)."
fi
run_suite "6/10 export-test.sh" "$SCRIPT_DIR/export-test.sh"
run_suite "7/10 openclaw-plugin-test.sh" "$SCRIPT_DIR/openclaw-plugin-test.sh"
run_suite "8/10 folder-claudemd-test.sh" "$SCRIPT_DIR/folder-claudemd-test.sh"
run_suite "9/10 evo-memory-e2e-test.sh" "$SCRIPT_DIR/evo-memory-e2e-test.sh"
run_suite "10/10 evo-memory-value-test.sh" "$SCRIPT_DIR/evo-memory-value-test.sh"

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Summary${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo "Suites executed (attempted): $SUITES_RAN"
echo -e "Failed:  ${RED}${SUITES_FAILED}${NC}"
echo -e "Skipped: ${CYAN}${SUITES_SKIPPED}${NC}"
echo ""

if [ "$SUITES_FAILED" -eq 0 ]; then
  echo -e "${GREEN}All runnable E2E suites passed.${NC}"
  exit 0
fi
echo -e "${RED}One or more suites failed.${NC}" >&2
exit 1
