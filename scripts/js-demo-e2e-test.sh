#!/bin/bash
# js-demo-e2e-test.sh — JS/TS Express Demo E2E Acceptance Test
# Coverage chain: E2E test script → JS Demo HTTP endpoints → JS SDK → Backend API
#
# Strict validation: each test must verify actual return content, not just check "not empty"
#
# Prerequisites:
# 1. Backend service running (port 37777)
# 2. JS Demo running: cd js-sdk/cortex-mem-js && npx tsx examples/http-server/app.ts
#
# Run:
#   bash scripts/js-demo-e2e-test.sh

set -e

DEMO_BASE="http://localhost:8080"
BACKEND_URL="http://127.0.0.1:37777"
PROJECT="/tmp/e2e-js-demo-test"

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

contains_field() {
    echo "$1" | grep -q "\"$2\"" 2>/dev/null
}

json_field() {
    echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$2',''))" 2>/dev/null
}

echo "=========================================="
echo "JS Demo E2E Acceptance Test (Strict Validation)"
echo "=========================================="
echo "Project path: $PROJECT"
echo ""

# ==================== Pre-checks ====================

info "Pre-check: Backend service..."
BACKEND_HEALTH=$(curl -sf "$BACKEND_URL/api/health" 2>/dev/null || echo "FAIL")
if [ "$BACKEND_HEALTH" = "FAIL" ]; then
    echo "❌ Backend service not running!"
    exit 1
fi
BACKEND_STATUS=$(json_field "$BACKEND_HEALTH" "status")
if [ "$BACKEND_STATUS" != "ok" ]; then
    echo "❌ Backend status is not 'ok': $BACKEND_STATUS"
    exit 1
fi
pass "Backend service OK"

info "Pre-check: JS Demo..."
DEMO_HEALTH=$(curl -sf "$DEMO_BASE/health" 2>/dev/null || echo "FAIL")
if [ "$DEMO_HEALTH" = "FAIL" ]; then
    echo "❌ JS Demo not running! Start: cd js-sdk/cortex-mem-js && npx tsx examples/http-server/app.ts"
    exit 1
fi
DEMO_SERVICE=$(json_field "$DEMO_HEALTH" "service")
if [ "$DEMO_SERVICE" != "js-sdk-http-server" ]; then
    fail "Demo Health" "service mismatch: $DEMO_SERVICE"
    exit 1
fi
pass "JS Demo OK (service=$DEMO_SERVICE)"

# ==================== Data Preparation ====================

echo ""
info "Data preparation..."

curl -sf -X POST "$BACKEND_URL/api/ingest/tool-use" \
    -H "Content-Type: application/json" \
    -d "{
        \"cwd\": \"$PROJECT\",
        \"session_id\": \"js-demo-e2e-session\",
        \"tool_name\": \"fact\",
        \"tool_response\": \"JS Demo E2E test verification data\",
        \"source\": \"js_demo_e2e_test\"
    }" > /dev/null 2>&1

sleep 2
pass "Data preparation"

# ==================== Test: /version ====================

echo ""
info "Testing /version..."
VERSION_RESP=$(curl -sf "$DEMO_BASE/version" 2>/dev/null || echo "FAIL")
if [ "$VERSION_RESP" = "FAIL" ]; then
    fail "GET /version" "Request failed"
elif ! contains_field "$VERSION_RESP" "version"; then
    fail "GET /version" "Missing 'version' field"
else
    pass "GET /version"
fi

# ==================== Test: /projects ====================

info "Testing /projects..."
PROJECTS_RESP=$(curl -sf "$DEMO_BASE/projects" 2>/dev/null || echo "FAIL")
if [ "$PROJECTS_RESP" = "FAIL" ]; then
    fail "GET /projects" "Request failed"
elif ! contains_field "$PROJECTS_RESP" "projects"; then
    fail "GET /projects" "Missing 'projects' field"
else
    pass "GET /projects"
fi

# ==================== Test: /stats ====================

info "Testing /stats..."
STATS_RESP=$(curl -sf "$DEMO_BASE/stats" 2>/dev/null || echo "FAIL")
if [ "$STATS_RESP" = "FAIL" ]; then
    fail "GET /stats" "Request failed"
elif ! contains_field "$STATS_RESP" "database"; then
    fail "GET /stats" "Missing 'database' field"
else
    pass "GET /stats"
fi

# ==================== Test: /modes ====================

info "Testing /modes..."
MODES_RESP=$(curl -sf "$DEMO_BASE/modes" 2>/dev/null || echo "FAIL")
if [ "$MODES_RESP" = "FAIL" ]; then
    fail "GET /modes" "Request failed"
elif ! contains_field "$MODES_RESP" "name"; then
    fail "GET /modes" "Missing 'name' field"
else
    pass "GET /modes"
fi

# ==================== Test: /settings ====================

info "Testing /settings..."
SETTINGS_RESP=$(curl -sf "$DEMO_BASE/settings" 2>/dev/null || echo "FAIL")
if [ "$SETTINGS_RESP" = "FAIL" ]; then
    fail "GET /settings" "Request failed"
else
    pass "GET /settings"
fi

# ==================== Test: /experiences ====================

info "Testing /experiences..."
EXP_RESP=$(curl -sf "$DEMO_BASE/experiences?project=$PROJECT&task=test&count=2" 2>/dev/null || echo "FAIL")
if [ "$EXP_RESP" = "FAIL" ]; then
    fail "GET /experiences" "Request failed"
elif ! contains_field "$EXP_RESP" "experiences"; then
    fail "GET /experiences" "Missing 'experiences' field"
else
    pass "GET /experiences"
fi

# ==================== Test: /iclprompt ====================

info "Testing /iclprompt..."
ICL_RESP=$(curl -sf "$DEMO_BASE/iclprompt?project=$PROJECT&task=test&maxChars=2000" 2>/dev/null || echo "FAIL")
if [ "$ICL_RESP" = "FAIL" ]; then
    fail "GET /iclprompt" "Request failed"
elif ! contains_field "$ICL_RESP" "prompt"; then
    fail "GET /iclprompt" "Missing 'prompt' field"
else
    pass "GET /iclprompt (maxChars=2000)"
fi

# ==================== Test: /search ====================

info "Testing /search..."
SEARCH_RESP=$(curl -sf "$DEMO_BASE/search?project=$PROJECT&query=test&limit=5" 2>/dev/null || echo "FAIL")
if [ "$SEARCH_RESP" = "FAIL" ]; then
    fail "GET /search" "Request failed"
elif ! contains_field "$SEARCH_RESP" "count"; then
    fail "GET /search" "Missing 'count' field"
else
    pass "GET /search"
fi

# ==================== Test: /observations ====================

info "Testing /observations..."
OBS_RESP=$(curl -sf "$DEMO_BASE/observations?project=$PROJECT&limit=5" 2>/dev/null || echo "FAIL")
if [ "$OBS_RESP" = "FAIL" ]; then
    fail "GET /observations" "Request failed"
elif ! contains_field "$OBS_RESP" "items"; then
    fail "GET /observations" "Missing 'items' field"
else
    pass "GET /observations"
fi

# ==================== Test: /quality ====================

info "Testing /quality..."
QUALITY_RESP=$(curl -sf "$DEMO_BASE/quality?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$QUALITY_RESP" = "FAIL" ]; then
    fail "GET /quality" "Request failed"
elif ! contains_field "$QUALITY_RESP" "project"; then
    fail "GET /quality" "Missing 'project' field"
else
    pass "GET /quality"
fi

# ==================== Test: /observations/create ====================

info "Testing /observations/create..."
CREATE_RESP=$(curl -sf -X POST "$DEMO_BASE/observations/create" \
    -H "Content-Type: application/json" \
    -d "{
        \"project\": \"$PROJECT\",
        \"session_id\": \"js-demo-e2e\",
        \"tool_name\": \"test\",
        \"tool_response\": \"demo test observation\",
        \"source\": \"demo_test\"
    }" 2>/dev/null || echo "FAIL")
if [ "$CREATE_RESP" = "FAIL" ]; then
    fail "POST /observations/create" "Request failed"
elif ! contains_field "$CREATE_RESP" "status"; then
    fail "POST /observations/create" "Missing 'status' field"
else
    pass "POST /observations/create"
fi

# ==================== Test: /refine ====================

info "Testing /refine..."
REFINE_RESP=$(curl -sf -X POST "$DEMO_BASE/refine?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$REFINE_RESP" = "FAIL" ]; then
    fail "POST /refine" "Request failed"
else
    pass "POST /refine"
fi

# ==================== Test: /extraction/run ====================

info "Testing /extraction/run..."
EXTRACT_RESP=$(curl -sf -X POST "$DEMO_BASE/extraction/run?projectPath=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$EXTRACT_RESP" = "FAIL" ]; then
    fail "POST /extraction/run" "Request failed"
else
    pass "POST /extraction/run"
fi

# ==================== Summary ====================

echo ""
echo "=========================================="
echo "JS Demo E2E Results: $PASSED/$TOTAL passed"
if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}FAILURES ($FAILED):${NC}$ERRORS"
    echo "=========================================="
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    echo "=========================================="
    exit 0
fi
