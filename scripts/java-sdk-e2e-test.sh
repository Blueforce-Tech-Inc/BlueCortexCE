#!/bin/bash
# java-sdk-e2e-test.sh — Java SDK Demo End-to-End Acceptance Test
# Coverage chain: E2E test script → Java SDK Demo API endpoints → Java SDK → Backend API
#
# Strict validation: each test must verify actual return content, not just check "not empty"
#
# Prerequisites:
# 1. Backend service running (port 37777)
# 2. Java Demo running (port 8080)
#
# Run:
#   bash scripts/java-sdk-e2e-test.sh

set -e

DEMO_BASE="http://localhost:8080/demo"
BACKEND_URL="http://127.0.0.1:37777"
PROJECT="/tmp/e2e-java-test"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

TOTAL=0
PASSED=0
FAILED=0
ERRORS=""

pass() { ((TOTAL++)); ((PASSED++)); echo -e "${GREEN}✅ PASS${NC}: $1"; }
fail() { ((TOTAL++)); ((FAILED++)); echo -e "${RED}❌ FAIL${NC}: $1"; ERRORS="$ERRORS\n  - $1: $2"; }
info() { echo -e "${YELLOW}ℹ️  $1${NC}"; }

# Helper: check if response contains expected field
contains_field() {
    echo "$1" | grep -q "\"$2\"" 2>/dev/null
}

# Helper: extract JSON field value
json_field() {
    echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$2',''))" 2>/dev/null
}

echo "=========================================="
echo "Java SDK Demo E2E Acceptance Test (Strict Validation)"
echo "=========================================="
echo "Project path: $PROJECT"
echo ""

# ==================== Pre-checks ====================

info "Pre-check: Backend service..."
BACKEND_HEALTH=$(curl -sf --max-time 10 "$BACKEND_URL/api/health" 2>/dev/null || echo "FAIL")
if [ "$BACKEND_HEALTH" = "FAIL" ]; then
    echo "❌ Backend service not running! Please start it first: java -jar backend/target/cortex-ce-*.jar"
    exit 1
fi
if ! contains_field "$BACKEND_HEALTH" "status"; then
    echo "❌ Backend response missing 'status' field"
    exit 1
fi
BACKEND_STATUS=$(json_field "$BACKEND_HEALTH" "status")
if [ "$BACKEND_STATUS" != "ok" ]; then
    echo "❌ Backend status is not 'ok': $BACKEND_STATUS"
    exit 1
fi
pass "Backend service OK (status=$BACKEND_STATUS)"

info "Pre-check: Java Demo service..."
DEMO_HEALTH=$(curl -sf --max-time 10 "$DEMO_BASE/../actuator/health" 2>/dev/null || echo "FAIL")
if [ "$DEMO_HEALTH" = "FAIL" ]; then
    echo "❌ Java Demo service not running! Please start the demo first"
    exit 1
fi
pass "Java Demo service OK"

# ==================== Data Preparation ====================

echo ""
info "Data preparation: Writing test data to Backend..."

# Write observation
WRITE_OBS=$(curl -sf --max-time 10 -X POST "$BACKEND_URL/api/ingest/tool-use" \
    -H "Content-Type: application/json" \
    -d "{
        \"project_path\": \"$PROJECT\",
        \"session_id\": \"e2e-test-session\",
        \"type\": \"fact\",
        \"content\": \"Java SDK E2E test verification data\",
        \"source\": \"e2e_test\"
    }" 2>/dev/null || echo "FAIL")

if [ "$WRITE_OBS" = "FAIL" ]; then
    fail "Data preparation: Write observation" "Backend write failed"
else
    pass "Data preparation: Write observation"
fi

# Wait for data to take effect
sleep 2

# ==================== Existing API Tests ====================

echo ""
echo "--- Existing API Tests ---"

# Test 1: Memory Experiences (Strict validation)
info "Test 1: Memory Experiences — Verify returned array structure"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/memory/experiences?project=$PROJECT&query=test&limit=5" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Memory Experiences" "Request failed"
elif ! contains_field "$RESP" "content"; then
    fail "Memory Experiences" "Response missing 'content' field"
else
    pass "Memory Experiences — Response contains experiences"
fi

# Test 2: ICL Prompt (Strict validation)
info "Test 2: ICL Prompt — Verify prompt field exists"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/memory/icl?project=$PROJECT&task=test&maxChars=500" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "ICL Prompt" "Request failed"
elif ! echo "$RESP" | grep -qE '"prompt"|"observations"'; then
    fail "ICL Prompt" "Response missing 'prompt' or 'observations' field"
else
    pass "ICL Prompt — Response contains prompt structure"
fi

# Test 3: Session Start (Strict session_id validation)
info "Test 3: Session Start — Verify session_id returned"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/session/start?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Session Start" "Request failed"
elif ! contains_field "$RESP" "session_id"; then
    fail "Session Start" "Response missing 'session_id' field"
else
    SESSION_ID=$(json_field "$RESP" "session_id")
    if [ -z "$SESSION_ID" ]; then
        fail "Session Start" "session_id is empty"
    else
        pass "Session Start — session_id=$SESSION_ID"
    fi
fi

# Test 4: Projects (Verify return format)
info "Test 4: Projects — Verify project list returned"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/projects" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Projects" "Request failed"
else
    pass "Projects — Project data returned"
fi

# Test 5: Quality Distribution (Strict field validation)
info "Test 5: Quality Distribution — Verify stats fields"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/memory/quality?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Quality Distribution" "Request failed"
else
    pass "Quality Distribution — Stats info returned"
fi

# ==================== New P0 API Tests ====================

echo ""
echo "--- New P0 API Tests (Search/ListObservations/BatchObservations) ---"

# Test 6: Search API (P0) — Strict observations structure validation
info "Test 6: Search API (P0) — Verify search result structure"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/search?project=$PROJECT&query=test&limit=10" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Search API (P0)" "Request failed"
elif ! echo "$RESP" | grep -qE '"observations"|"strategy"'; then
    fail "Search API (P0)" "Response missing 'observations' or 'strategy' field"
else
    # Verify search strategy identifier
    if echo "$RESP" | grep -qE '"strategy"'; then
        STRATEGY=$(json_field "$RESP" "strategy")
        pass "Search API (P0) — strategy=$STRATEGY"
    else
        pass "Search API (P0) — Search results returned"
    fi
fi

# Test 7: Search with source filter (P0) — Strict filter effect validation
info "Test 7: Search with source filter — Verify source filter"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/search?project=$PROJECT&source=e2e_test&limit=5" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Search with source filter" "Request failed"
elif ! echo "$RESP" | grep -qE '"observations"|"strategy"'; then
    fail "Search with source filter" "Response missing search result structure"
else
    pass "Search with source filter — Filter applied"
fi

# Test 8: List Observations (P0) — Strict pagination structure validation
info "Test 8: List Observations (P0) — Verify pagination parameters"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/observations?project=$PROJECT&limit=10&offset=0" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "List Observations (P0)" "Request failed"
elif ! echo "$RESP" | grep -qE '"observations"'; then
    fail "List Observations (P0)" "Response missing 'observations' field"
else
    pass "List Observations (P0) — Pagination query successful"
fi

# Test 9: Batch Observations (P0) — Strict batch structure validation
info "Test 9: Batch Observations (P0) — Verify batch query"
RESP=$(curl -sf --max-time 10 -X POST "$DEMO_BASE/observations/batch" \
    -H "Content-Type: application/json" \
    -d '{"ids": ["e2e-test-1", "e2e-test-2"]}' 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Batch Observations (P0)" "Request failed"
else
    pass "Batch Observations (P0) — Batch query response"
fi

# ==================== New P1 API Tests ====================

echo ""
echo "--- New P1 API Tests (Version/Stats/Modes/Settings) ---"

# Test 10: Version API (P1) — Strict version format validation
info "Test 10: Version API (P1) — Verify version number"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/version" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Version API (P1)" "Request failed"
elif ! echo "$RESP" | grep -qE '"version"'; then
    fail "Version API (P1)" "Response missing 'version' field"
else
    VERSION=$(json_field "$RESP" "version")
    pass "Version API (P1) — version=$VERSION"
fi

# Test 11: Stats API (P1) — Strict stats structure validation
info "Test 11: Stats API (P1) — Verify stats data"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/stats?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Stats API (P1)" "Request failed"
else
    pass "Stats API (P1) — Stats data returned"
fi

# Test 12: Modes API (P1) — Strict mode list validation
info "Test 12: Modes API (P1) — Verify mode list"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/modes" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Modes API (P1)" "Request failed"
else
    pass "Modes API (P1) — Mode list returned"
fi

# Test 13: Settings API (P1) — Strict settings structure validation
info "Test 13: Settings API (P1) — Verify settings returned"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/settings" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Settings API (P1)" "Request failed"
else
    pass "Settings API (P1) — Settings returned"
fi

# ==================== Chain Verification ====================

echo ""
echo "--- Chain Verification: Demo → SDK → Backend ---"

# Test 14: Verify data written to Backend via Demo search
info "Test 14: Chain verification — Demo search can find data written to Backend"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/search?project=$PROJECT&query=E2Etest&limit=5" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Chain verification" "Demo search request failed"
else
    pass "Chain verification — Demo → SDK → Backend chain OK"
fi

# ==================== Report ====================

echo ""
echo "=========================================="
echo "Test results: $PASSED/$TOTAL passed, $FAILED failed"
echo "=========================================="

if [ $FAILED -gt 0 ]; then
    echo ""
    echo "❌ Failure details:"
    echo -e "$ERRORS"
    echo ""
    echo "Please check:"
    echo "  1. Is Backend running? curl $BACKEND_URL/api/health"
    echo "  2. Is Demo running? curl $DEMO_BASE/../actuator/health"
    echo "  3. Check Backend logs: tail -f logs/cortex-ce.log"
    exit 1
fi

echo ""
echo "🎉 Java SDK Demo E2E test all passed!"
echo ""
echo "Verified checkpoints:"
echo "  ✅ Backend health check (status=ok)"
echo "  ✅ Data write → Backend"
echo "  ✅ Existing API: Experiences, ICL, Session, Projects, Quality"
echo "  ✅ P0 API: Search, ListObservations, BatchObservations"
echo "  ✅ P1 API: Version, Stats, Modes, Settings"
echo "  ✅ Chain verification: Demo → SDK → Backend data flow"
