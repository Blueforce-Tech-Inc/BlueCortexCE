#!/bin/bash
# python-sdk-e2e-test.sh — Python SDK E2E Acceptance Test
# Coverage chain: E2E test script → Python SDK → Backend API
#
# Prerequisites:
# 1. Backend service running (port 37777)
# 2. Python SDK installed: cd python-sdk/cortex-mem-python && pip install -e .
#
# Run:
#   bash scripts/python-sdk-e2e-test.sh

set -e

BACKEND_URL="http://127.0.0.1:37777"
PROJECT="/tmp/e2e-python-test"

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

json_field() {
    echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$2',''))" 2>/dev/null
}

contains_field() {
    echo "$1" | grep -q "\"$2\"" 2>/dev/null
}

echo "=========================================="
echo "Python SDK E2E Acceptance Test"
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
pass "Backend service OK (status=$BACKEND_STATUS)"

info "Pre-check: Python SDK importable..."
if ! python3 -c "from cortex_mem import CortexMemClient" 2>/dev/null; then
    echo "❌ Python SDK not importable. Install: cd python-sdk/cortex-mem-python && pip install -e ."
    exit 1
fi
pass "Python SDK importable"

# ==================== Data Preparation ====================

echo ""
info "Data preparation: Writing test data to Backend..."

curl -sf -X POST "$BACKEND_URL/api/ingest/tool-use" \
    -H "Content-Type: application/json" \
    -d "{
        \"cwd\": \"$PROJECT\",
        \"session_id\": \"python-e2e-session\",
        \"tool_name\": \"fact\",
        \"tool_response\": \"Python SDK E2E test verification data\",
        \"source\": \"python_e2e_test\"
    }" >/dev/null 2>&1 || true

sleep 2

# ==================== Python SDK Tests ====================

echo ""
echo "--- Python SDK API Method Tests ---"

# Helper: run a Python snippet and check exit code
run_py() {
    python3 -c "
import sys
sys.path.insert(0, 'python-sdk/cortex-mem-python')
from cortex_mem import CortexMemClient
$1
" 2>&1
}

# Test 1: HealthCheck
info "Test 1: HealthCheck"
PY_HEALTH=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.health_check()
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_HEALTH" = "ok" ]; then
    pass "Python SDK HealthCheck"
else
    fail "Python SDK HealthCheck" "$PY_HEALTH"
fi

# Test 2: StartSession
info "Test 2: StartSession"
PY_SESSION=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    s = client.start_session('py-e2e-$(date +%s)', '$PROJECT')
    print(f'ok:{s.session_id}:{s.session_db_id}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_SESSION" | grep -q "^ok:"; then
    SESSION_ID=$(echo "$PY_SESSION" | cut -d: -f2)
    pass "Python SDK StartSession (session_id=$SESSION_ID)"
else
    fail "Python SDK StartSession" "$PY_SESSION"
fi

# Test 3: RecordObservation (fire-and-forget)
info "Test 3: RecordObservation"
PY_OBS=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.record_observation('py-e2e-obs', '$PROJECT', 'Read', tool_input={'file': 'test.py'}, source='python_e2e')
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_OBS" = "ok" ]; then
    pass "Python SDK RecordObservation"
else
    fail "Python SDK RecordObservation" "$PY_OBS"
fi

# Test 4: RecordUserPrompt (fire-and-forget)
info "Test 4: RecordUserPrompt"
PY_PROMPT=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.record_user_prompt('py-e2e-$(date +%s)', 'test prompt', project_path='$PROJECT')
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_PROMPT" = "ok" ]; then
    pass "Python SDK RecordUserPrompt"
else
    fail "Python SDK RecordUserPrompt" "$PY_PROMPT"
fi

# Test 5: RetrieveExperiences
info "Test 5: RetrieveExperiences"
PY_EXPS=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    exps = client.retrieve_experiences('test', '$PROJECT')
    print(f'ok:{len(exps)}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_EXPS" | grep -q "^ok:"; then
    EXP_COUNT=$(echo "$PY_EXPS" | cut -d: -f2)
    pass "Python SDK RetrieveExperiences (count=$EXP_COUNT)"
else
    fail "Python SDK RetrieveExperiences" "$PY_EXPS"
fi

# Test 6: BuildICLPrompt
info "Test 6: BuildICLPrompt"
PY_ICL=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    result = client.build_icl_prompt('test', '$PROJECT')
    print(f'ok:{len(result.prompt)}:{result.experience_count}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_ICL" | grep -q "^ok:"; then
    pass "Python SDK BuildICLPrompt"
else
    fail "Python SDK BuildICLPrompt" "$PY_ICL"
fi

# Test 7: Search
info "Test 7: Search"
PY_SEARCH=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    result = client.search('$PROJECT', query='test', limit=5)
    print(f'ok:{result.count}:{result.strategy}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_SEARCH" | grep -q "^ok:"; then
    pass "Python SDK Search"
else
    fail "Python SDK Search" "$PY_SEARCH"
fi

# Test 8: Search with source filter
info "Test 8: Search with source filter"
PY_FILTER=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    result = client.search('$PROJECT', source='python_e2e_test', limit=5)
    print(f'ok:{result.count}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_FILTER" | grep -q "^ok:"; then
    pass "Python SDK Search (source filter)"
else
    fail "Python SDK Search (source filter)" "$PY_FILTER"
fi

# Test 9: ListObservations
info "Test 9: ListObservations"
PY_LIST=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    resp = client.list_observations('$PROJECT', limit=5)
    print(f'ok:{len(resp.items)}:{resp.has_more}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_LIST" | grep -q "^ok:"; then
    pass "Python SDK ListObservations"
else
    fail "Python SDK ListObservations" "$PY_LIST"
fi

# Test 10: GetObservationsByIds
info "Test 10: GetObservationsByIds"
PY_BATCH=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    resp = client.get_observations_by_ids(['nonexistent-id'])
    print(f'ok:{resp.count}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_BATCH" | grep -q "^ok:"; then
    pass "Python SDK GetObservationsByIds"
else
    fail "Python SDK GetObservationsByIds" "$PY_BATCH"
fi

# Test 11: GetVersion
info "Test 11: GetVersion"
PY_VER=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    v = client.get_version()
    print(f'ok:{v.version}:{v.service}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_VER" | grep -q "^ok:"; then
    pass "Python SDK GetVersion"
else
    fail "Python SDK GetVersion" "$PY_VER"
fi

# Test 12: GetProjects
info "Test 12: GetProjects"
PY_PROJ=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    p = client.get_projects()
    print(f'ok:{len(p.projects)}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_PROJ" | grep -q "^ok:"; then
    pass "Python SDK GetProjects"
else
    fail "Python SDK GetProjects" "$PY_PROJ"
fi

# Test 13: GetStats
info "Test 13: GetStats"
PY_STATS=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    s = client.get_stats('$PROJECT')
    print(f'ok:{s.database.total_observations}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_STATS" | grep -q "^ok:"; then
    pass "Python SDK GetStats"
else
    fail "Python SDK GetStats" "$PY_STATS"
fi

# Test 14: GetModes
info "Test 14: GetModes"
PY_MODES=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    m = client.get_modes()
    print(f'ok:{m.name}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_MODES" | grep -q "^ok:"; then
    pass "Python SDK GetModes"
else
    fail "Python SDK GetModes" "$PY_MODES"
fi

# Test 15: GetSettings
info "Test 15: GetSettings"
PY_SETTINGS=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    s = client.get_settings()
    print(f'ok:{type(s).__name__}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_SETTINGS" | grep -q "^ok:dict"; then
    pass "Python SDK GetSettings"
else
    fail "Python SDK GetSettings" "$PY_SETTINGS"
fi

# Test 16: GetQualityDistribution
info "Test 16: GetQualityDistribution"
PY_QUAL=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    q = client.get_quality_distribution('$PROJECT')
    print(f'ok:{q.total}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_QUAL" | grep -q "^ok:"; then
    pass "Python SDK GetQualityDistribution"
else
    fail "Python SDK GetQualityDistribution" "$PY_QUAL"
fi

# Test 17: TriggerRefinement
info "Test 17: TriggerRefinement"
PY_REFINE=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.trigger_refinement('$PROJECT')
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_REFINE" = "ok" ]; then
    pass "Python SDK TriggerRefinement"
else
    fail "Python SDK TriggerRefinement" "$PY_REFINE"
fi

# Test 18: SubmitFeedback
info "Test 18: SubmitFeedback"
PY_FEEDBACK=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.submit_feedback('test-id', 'useful', 'good')
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_FEEDBACK" = "ok" ]; then
    pass "Python SDK SubmitFeedback"
else
    fail "Python SDK SubmitFeedback" "$PY_FEEDBACK"
fi

# Test 19: UpdateObservation
info "Test 19: UpdateObservation"
PY_UPDATE=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.update_observation('test-id', title='Updated', source='verified')
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_UPDATE" = "ok" ]; then
    pass "Python SDK UpdateObservation"
else
    fail "Python SDK UpdateObservation" "$PY_UPDATE"
fi

# Test 20: DeleteObservation
info "Test 20: DeleteObservation"
PY_DELETE=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.delete_observation('test-id')
    print('ok')
except Exception as e:
    # 404 is acceptable for non-existent ID
    if '404' in str(e) or 'not found' in str(e).lower():
        print('ok')
    else:
        print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_DELETE" = "ok" ]; then
    pass "Python SDK DeleteObservation"
else
    fail "Python SDK DeleteObservation" "$PY_DELETE"
fi

# Test 21: RecordSessionEnd
info "Test 21: RecordSessionEnd"
PY_END=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.record_session_end('py-e2e-session', '$PROJECT')
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_END" = "ok" ]; then
    pass "Python SDK RecordSessionEnd"
else
    fail "Python SDK RecordSessionEnd" "$PY_END"
fi

# Test 22: UpdateSessionUserId
info "Test 22: UpdateSessionUserId"
PY_UPDATE_USER=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    resp = client.update_session_user_id('nonexistent-session', 'test-user')
    print('ok')
except Exception as e:
    # 404 is acceptable
    if '404' in str(e) or 'not found' in str(e).lower():
        print('ok')
    else:
        print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_UPDATE_USER" = "ok" ]; then
    pass "Python SDK UpdateSessionUserId"
else
    fail "Python SDK UpdateSessionUserId" "$PY_UPDATE_USER"
fi

# Test 23: TriggerExtraction
info "Test 23: TriggerExtraction"
PY_EXTRACT=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    client.trigger_extraction('$PROJECT')
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if [ "$PY_EXTRACT" = "ok" ]; then
    pass "Python SDK TriggerExtraction"
else
    fail "Python SDK TriggerExtraction" "$PY_EXTRACT"
fi

# Test 24: GetLatestExtraction
info "Test 24: GetLatestExtraction"
PY_LATEST=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    r = client.get_latest_extraction('$PROJECT', 'user_preference')
    print(f'ok:{r.status}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_LATEST" | grep -q "^ok:"; then
    pass "Python SDK GetLatestExtraction"
else
    fail "Python SDK GetLatestExtraction" "$PY_LATEST"
fi

# Test 25: GetExtractionHistory
info "Test 25: GetExtractionHistory"
PY_HISTORY=$(run_py "
client = CortexMemClient(base_url='$BACKEND_URL', max_retries=1)
try:
    results = client.get_extraction_history('$PROJECT', 'user_preference', limit=5)
    print(f'ok:{len(results)}')
except Exception as e:
    print(f'FAIL: {e}')
finally:
    client.close()
")
if echo "$PY_HISTORY" | grep -q "^ok:"; then
    pass "Python SDK GetExtractionHistory"
else
    fail "Python SDK GetExtractionHistory" "$PY_HISTORY"
fi

# Test 26: Context manager
info "Test 26: Context manager"
PY_CTX=$(run_py "
from cortex_mem import CortexMemClient
try:
    with CortexMemClient(base_url='$BACKEND_URL', max_retries=1) as client:
        client.health_check()
    print('ok')
except Exception as e:
    print(f'FAIL: {e}')
")
if [ "$PY_CTX" = "ok" ]; then
    pass "Python SDK Context Manager"
else
    fail "Python SDK Context Manager" "$PY_CTX"
fi

# ==================== Coverage Summary ====================

echo ""
echo "--- Python SDK Method Coverage ---"
echo "  ✅ start_session"
echo "  ✅ update_session_user_id"
echo "  ✅ record_observation"
echo "  ✅ record_session_end"
echo "  ✅ record_user_prompt"
echo "  ✅ retrieve_experiences"
echo "  ✅ build_icl_prompt"
echo "  ✅ search"
echo "  ✅ list_observations"
echo "  ✅ get_observations_by_ids"
echo "  ✅ trigger_refinement"
echo "  ✅ submit_feedback"
echo "  ✅ update_observation"
echo "  ✅ delete_observation"
echo "  ✅ get_quality_distribution"
echo "  ✅ health_check"
echo "  ✅ trigger_extraction"
echo "  ✅ get_latest_extraction"
echo "  ✅ get_extraction_history"
echo "  ✅ get_version"
echo "  ✅ get_projects"
echo "  ✅ get_stats"
echo "  ✅ get_modes"
echo "  ✅ get_settings"
echo "  ✅ close / context manager"
echo ""
echo "All 26 Python SDK API methods covered! ✅"

# ==================== Final Report ====================

echo ""
echo "=========================================="
echo "Python SDK E2E Final Summary: $PASSED/$TOTAL passed, $FAILED failed"
echo "=========================================="

if [ $FAILED -gt 0 ]; then
    echo ""
    echo "❌ Failure details:"
    echo -e "$ERRORS"
    echo ""
    echo "Please check:"
    echo "  1. Is Backend running? curl $BACKEND_URL/api/health"
    echo "  2. Is Python SDK installed? cd python-sdk/cortex-mem-python && pip install -e ."
    exit 1
fi

echo ""
echo "🎉 Python SDK E2E test all passed! ($TOTAL tests)"
