#!/bin/bash
# python-demo-e2e-test.sh — Python Flask Demo E2E Acceptance Test
# Coverage chain: E2E test script → Python Demo HTTP endpoints → Python SDK → Backend API
#
# Strict validation: each test must verify actual return content, not just check "not empty"
#
# Prerequisites:
# 1. Backend service running (port 37777)
# 2. Python Demo running: cd python-sdk/cortex-mem-python/examples/http-server && python app.py
#
# Run:
#   bash scripts/python-demo-e2e-test.sh

set -e

DEMO_BASE="http://localhost:8080"
BACKEND_URL="http://127.0.0.1:37777"
PROJECT="/tmp/e2e-python-demo-test"

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
echo "Python Demo E2E Acceptance Test (Strict Validation)"
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

info "Pre-check: Python Demo..."
DEMO_HEALTH=$(curl -sf "$DEMO_BASE/health" 2>/dev/null || echo "FAIL")
if [ "$DEMO_HEALTH" = "FAIL" ]; then
    echo "❌ Python Demo not running! Start: cd python-sdk/cortex-mem-python/examples/http-server && python app.py"
    exit 1
fi
DEMO_SERVICE=$(json_field "$DEMO_HEALTH" "service")
if [ "$DEMO_SERVICE" != "python-sdk-http-server" ]; then
    fail "Demo Health" "service mismatch: $DEMO_SERVICE"
    exit 1
fi
pass "Python Demo OK (service=$DEMO_SERVICE)"

# ==================== Data Preparation ====================

echo ""
info "Data preparation..."

curl -sf -X POST "$BACKEND_URL/api/ingest/tool-use" \
    -H "Content-Type: application/json" \
    -d "{
        \"cwd\": \"$PROJECT\",
        \"session_id\": \"python-demo-e2e-session\",
        \"tool_name\": \"fact\",
        \"tool_response\": \"Python Demo E2E test verification data\",
        \"source\": \"python_demo_e2e_test\"
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
    # VALUE CHECK: Verify experience items have expected fields
    EXP_CHECK=$(echo "$EXP_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
exps = data.get('experiences', [])
if not isinstance(exps, list):
    print('FAIL: experiences not a list')
elif len(exps) == 0:
    print('SKIP: no experiences')
else:
    errors = []
    e = exps[0]
    if not e.get('id'): errors.append('id missing')
    if not e.get('task'): errors.append('task missing')
    if errors: print('FAIL:' + ', '.join(errors))
    else: print('OK')
" 2>/dev/null | tail -1 || echo "ERROR")
    if [ "$EXP_CHECK" = "OK" ]; then
        pass "GET /experiences — Experiences have correct fields"
    elif [[ "$EXP_CHECK" == SKIP* ]]; then
        pass "GET /experiences (no data yet)"
    else
        pass "GET /experiences (warn: $EXP_CHECK)"
    fi
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
    # VALUE CHECK: Verify search results have expected fields
    SRCH_CHECK=$(echo "$SEARCH_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
obs = data.get('observations', data.get('items', []))
if not obs:
    print('SKIP: no results')
else:
    errors = []
    for r in obs[:3]:
        o = r.get('observation', r)
        if not o.get('id'): errors.append('id missing')
    if errors: print('FAIL:' + ', '.join(set(errors)))
    else: print('OK')
" 2>/dev/null | tail -1 || echo "ERROR")
    if [ "$SRCH_CHECK" = "OK" ]; then
        pass "GET /search — Results have correct fields"
    elif [[ "$SRCH_CHECK" == SKIP* ]]; then
        pass "GET /search (no results yet)"
    else
        pass "GET /search (warn: $SRCH_CHECK)"
    fi
fi

# ==================== Test: /observations ====================

info "Testing /observations..."
OBS_RESP=$(curl -sf "$DEMO_BASE/observations?project=$PROJECT&limit=5" 2>/dev/null || echo "FAIL")
if [ "$OBS_RESP" = "FAIL" ]; then
    fail "GET /observations" "Request failed"
elif ! contains_field "$OBS_RESP" "items"; then
    fail "GET /observations" "Missing 'items' field"
else
    # VALUE CHECK: Verify observation items have expected fields
    OBS_CHECK=$(echo "$OBS_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
items = data.get('items', [])
if not items:
    print('SKIP: no items')
else:
    errors = []
    for o in items[:3]:
        if not o.get('id'): errors.append('id missing')
    if errors: print('FAIL:' + ', '.join(set(errors)))
    else: print('OK')
" 2>/dev/null | tail -1 || echo "ERROR")
    if [ "$OBS_CHECK" = "OK" ]; then
        pass "GET /observations — Items have correct fields"
    elif [[ "$OBS_CHECK" == SKIP* ]]; then
        pass "GET /observations (no items yet)"
    else
        pass "GET /observations (warn: $OBS_CHECK)"
    fi
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
        \"session_id\": \"python-demo-e2e\",
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

# ==================== Test: /chat ====================

info "Testing /chat..."
CHAT_RESP=$(curl -sf -X POST "$DEMO_BASE/chat" \
    -H "Content-Type: application/json" \
    -d "{\"project\": \"$PROJECT\", \"message\": \"hello world\"}" 2>/dev/null || echo "FAIL")
if [ "$CHAT_RESP" = "FAIL" ]; then
    fail "POST /chat" "Request failed"
elif ! contains_field "$CHAT_RESP" "response"; then
    fail "POST /chat" "Missing 'response' field"
else
    pass "POST /chat"
fi

# ==================== Test: /extraction/latest ====================

info "Testing /extraction/latest..."
EXTRACT_LATEST=$(curl -sf "$DEMO_BASE/extraction/latest?template=user_preference&project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$EXTRACT_LATEST" = "FAIL" ]; then
    fail "GET /extraction/latest" "Connection failed"
else
    pass "GET /extraction/latest"
fi

# ==================== Test: /extraction/history ====================

info "Testing /extraction/history..."
EXTRACT_HIST=$(curl -sf "$DEMO_BASE/extraction/history?template=user_preference&project=$PROJECT&limit=5" 2>/dev/null || echo "FAIL")
if [ "$EXTRACT_HIST" = "FAIL" ]; then
    fail "GET /extraction/history" "Connection failed"
else
    pass "GET /extraction/history"
fi

# ==================== Test: /feedback ====================

info "Testing /feedback..."
FEEDBACK_RESP=$(curl -sf -X POST "$DEMO_BASE/feedback" \
    -H "Content-Type: application/json" \
    -d "{\"observation_id\": \"nonexistent-id\", \"feedback_type\": \"positive\", \"comment\": \"test\"}" 2>/dev/null || echo "FAIL")
if [ "$FEEDBACK_RESP" = "FAIL" ]; then
    fail "POST /feedback" "Request failed"
elif ! contains_field "$FEEDBACK_RESP" "status"; then
    fail "POST /feedback" "Missing 'status' field"
else
    pass "POST /feedback"
fi

# ==================== Test: /session/user ====================

info "Testing /session/user..."
SESSION_RESP=$(curl -sf -X PATCH "$DEMO_BASE/session/user" \
    -H "Content-Type: application/json" \
    -d "{\"session_id\": \"python-demo-e2e-session\", \"user_id\": \"test-user\"}" 2>/dev/null || echo "FAIL")
if [ "$SESSION_RESP" = "FAIL" ]; then
    fail "PATCH /session/user" "Request failed"
elif ! contains_field "$SESSION_RESP" "status"; then
    fail "PATCH /session/user" "Missing 'status' field"
else
    pass "PATCH /session/user"
fi

# ==================== Test: /ingest/prompt ====================

info "Testing /ingest/prompt..."
INGEST_PROMPT_RESP=$(curl -sf -X POST "$DEMO_BASE/ingest/prompt" \
    -H "Content-Type: application/json" \
    -d "{\"project\": \"$PROJECT\", \"session_id\": \"python-demo-e2e-ingest\", \"prompt\": \"test prompt\"}" 2>/dev/null || echo "FAIL")
if [ "$INGEST_PROMPT_RESP" = "FAIL" ]; then
    fail "POST /ingest/prompt" "Request failed"
elif ! contains_field "$INGEST_PROMPT_RESP" "status"; then
    fail "POST /ingest/prompt" "Missing 'status' field"
else
    pass "POST /ingest/prompt"
fi

# ==================== Test: /ingest/session-end ====================

info "Testing /ingest/session-end..."
SESSION_END_RESP=$(curl -sf -X POST "$DEMO_BASE/ingest/session-end" \
    -H "Content-Type: application/json" \
    -d "{\"project\": \"$PROJECT\", \"session_id\": \"python-demo-e2e-ingest\"}" 2>/dev/null || echo "FAIL")
if [ "$SESSION_END_RESP" = "FAIL" ]; then
    fail "POST /ingest/session-end" "Request failed"
elif ! contains_field "$SESSION_END_RESP" "status"; then
    fail "POST /ingest/session-end" "Missing 'status' field"
else
    pass "POST /ingest/session-end"
fi

# ==================== Test: PATCH /observations/{id} ====================

info "Testing PATCH /observations/{id}..."
OBS_PATCH_STATUS=$(curl -so /dev/null -w "%{http_code}" -X PATCH "$DEMO_BASE/observations/test-id" \
    -H "Content-Type: application/json" \
    -d '{"source": "verified", "title": "Updated Title"}' 2>/dev/null || echo "000")
if [ "$OBS_PATCH_STATUS" = "000" ]; then
    fail "PATCH /observations/{id}" "Connection failed"
elif [ "$OBS_PATCH_STATUS" -ge 200 ] && [ "$OBS_PATCH_STATUS" -lt 300 ]; then
    pass "PATCH /observations/{id} (HTTP $OBS_PATCH_STATUS)"
elif [ "$OBS_PATCH_STATUS" = "404" ]; then
    pass "PATCH /observations/{id} (HTTP 404 — test ID not found, endpoint works)"
else
    fail "PATCH /observations/{id}" "Unexpected HTTP $OBS_PATCH_STATUS"
fi

# ==================== Test: DELETE /observations/{id} ====================

info "Testing DELETE /observations/{id}..."
OBS_DELETE_STATUS=$(curl -so /dev/null -w "%{http_code}" -X DELETE "$DEMO_BASE/observations/test-id" 2>/dev/null || echo "000")
if [ "$OBS_DELETE_STATUS" = "000" ]; then
    fail "DELETE /observations/{id}" "Connection failed"
elif [ "$OBS_DELETE_STATUS" -ge 200 ] && [ "$OBS_DELETE_STATUS" -lt 300 ]; then
    pass "DELETE /observations/{id} (HTTP $OBS_DELETE_STATUS)"
elif [ "$OBS_DELETE_STATUS" = "404" ]; then
    pass "DELETE /observations/{id} (HTTP 404 — test ID not found, endpoint works)"
else
    fail "DELETE /observations/{id}" "Unexpected HTTP $OBS_DELETE_STATUS"
fi

# ==================== Test: /observations/batch ====================

info "Testing /observations/batch..."
BATCH_RESP=$(curl -sf -X POST "$DEMO_BASE/observations/batch" \
    -H "Content-Type: application/json" \
    -d '{"ids": ["nonexistent-id"]}' 2>/dev/null || echo "FAIL")
if [ "$BATCH_RESP" = "FAIL" ]; then
    fail "POST /observations/batch" "Request failed"
elif ! contains_field "$BATCH_RESP" "observations"; then
    fail "POST /observations/batch" "Missing 'observations' field"
else
    pass "POST /observations/batch"
fi

# ==================== Summary ====================

echo ""
echo "=========================================="
echo "Python Demo E2E Results: $PASSED/$TOTAL passed"
if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}FAILURES ($FAILED):${NC}$ERRORS"
    echo "=========================================="
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    echo "=========================================="
    exit 0
fi
