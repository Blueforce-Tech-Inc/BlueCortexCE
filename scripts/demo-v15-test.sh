#!/bin/bash
#
# Demo V15 Feature Test Script
# Tests the extraction endpoints added to examples/cortex-mem-demo (Phase 3)
#
# Prerequisites:
# - Backend running on port 37777
# - Demo app running on port 37778 (mvn spring-boot:run -pl examples/cortex-mem-demo -Plocal)
#

set -euo pipefail

DEMO_URL="${DEMO_URL:-http://localhost:37778}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:37777}"
TEST_PROJECT="/tmp/demo-v15-test"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_section() { echo ""; echo "=========================================="; echo "$1"; echo "=========================================="; }

TESTS_PASSED=0
TESTS_FAILED=0

# Check if demo is running
check_demo() {
    # The probe must answer "is the DEMO up", not "is the backend up".
    # /actuator/health aggregates the backend's own health, so it answers 503
    # whenever the backend is down — measured live: a demo pointed at a dead
    # backend port answered 503 on /actuator/health while serving every route
    # normally. Using it here reported "demo is NOT running" and skipped the
    # suite, which turns a backend outage into a misleading "start the demo"
    # instruction. /demo/observations/status is a static endpoint of the demo
    # itself, so it answers 200 exactly when the demo is up.
    if curl -sf "${DEMO_URL}/demo/observations/status" > /dev/null 2>&1; then
        log_info "Demo is running at ${DEMO_URL}"
        return 0
    else
        log_warn "Demo is NOT running at ${DEMO_URL}"
        log_warn "Start with: cd examples/cortex-mem-demo && mvn spring-boot:run -Plocal"
        return 1
    fi
}

# Fetch a URL, splitting the HTTP status code from the body.
#
# The old form was `curl -sf`, which fails on any status >= 400 and — because
# of the -s — prints nothing at all, so the failure message carried no
# information beyond the URL. Two consequences, both measured:
#
#   * `/demo/extraction/latest` answers 404 {"error":"No extraction found"} when
#     the project has no extraction for that template and user. That is the
#     endpoint's correct behaviour, and this project
#     (TEST_PROJECT=/tmp/demo-v15-test) never has one, so -f turned the first
#     test of this script into a guaranteed failure.
#   * Any 500 was reported as "failed: " with an empty body.
#
# So the status code is read explicitly and the body is always captured.
fetch() {
    local url="$1"
    local raw code body
    raw=$(curl -s -w '\n%{http_code}' "$url" 2>/dev/null) || raw=$'\n000'
    code=$(printf '%s' "$raw" | tail -n 1)
    body=$(printf '%s' "$raw" | sed '$d')
    HTTP_CODE="$code"
    HTTP_BODY="$body"
}

# Test /demo/extraction/latest endpoint (V15: Extraction API)
test_extraction_latest() {
    log_section "Test: /demo/extraction/latest (V15)"

    fetch "${DEMO_URL}/demo/extraction/latest?project=${TEST_PROJECT}&template=user_preferences&userId=alice"

    # 404 is a legitimate answer here: the project has no extraction for that
    # template and user. 200 means one exists. Anything else is a failure.
    if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "404" ]; then
        log_fail "GET /demo/extraction/latest returned HTTP ${HTTP_CODE}: ${HTTP_BODY}"
        return 1
    fi

    # Response should be JSON (may be empty or have status)
    if ! printf '%s' "$HTTP_BODY" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
        log_fail "Response is not valid JSON (HTTP ${HTTP_CODE}): ${HTTP_BODY}"
        return 1
    fi

    # A 404 has to say what is missing, or a caller cannot tell it apart from a
    # routing error.
    if [ "$HTTP_CODE" = "404" ] && ! printf '%s' "$HTTP_BODY" | grep -q "No extraction found"; then
        log_fail "HTTP 404 body does not report the missing extraction: ${HTTP_BODY}"
        return 1
    fi

    log_info "Test PASSED (HTTP ${HTTP_CODE})"
    ((TESTS_PASSED++))
    return 0
}

# Test /demo/extraction/history endpoint (V15: Extraction History)
test_extraction_history() {
    log_section "Test: /demo/extraction/history (V15)"

    fetch "${DEMO_URL}/demo/extraction/history?project=${TEST_PROJECT}&template=user_preferences&userId=alice&limit=5"

    if [ "$HTTP_CODE" != "200" ]; then
        log_fail "GET /demo/extraction/history returned HTTP ${HTTP_CODE}: ${HTTP_BODY}"
        return 1
    fi

    # Response should be a JSON list
    if ! printf '%s' "$HTTP_BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); assert isinstance(data, list)" 2>/dev/null; then
        log_fail "Response is not a list (HTTP ${HTTP_CODE}): ${HTTP_BODY}"
        return 1
    fi

    log_info "Test PASSED (HTTP ${HTTP_CODE})"
    ((TESTS_PASSED++))
    return 0
}

# Test /memory/experiences with userId parameter (V15: Multi-user)
test_experiences_with_user() {
    log_section "Test: /memory/experiences with userId (V15 multi-user)"

    fetch "${DEMO_URL}/memory/experiences?task=test&project=${TEST_PROJECT}&count=2"

    if [ "$HTTP_CODE" != "200" ]; then
        log_fail "GET /memory/experiences returned HTTP ${HTTP_CODE}: ${HTTP_BODY}"
        return 1
    fi

    if ! printf '%s' "$HTTP_BODY" | python3 -c "import sys, json; data=json.load(sys.stdin); assert isinstance(data, list)" 2>/dev/null; then
        log_fail "Response is not a list (HTTP ${HTTP_CODE}): ${HTTP_BODY}"
        return 1
    fi

    log_info "Test PASSED (HTTP ${HTTP_CODE})"
    ((TESTS_PASSED++))
    return 0
}

# Main
main() {
    echo "=========================================="
    echo "   Demo V15 Feature Test Suite"
    echo "=========================================="
    echo ""
    echo "Demo URL: ${DEMO_URL}"
    echo "Backend URL: ${BACKEND_URL}"
    echo ""

    # Check demo is running
    if ! check_demo; then
        log_warn "Skipping tests (demo not running)"
        exit 1
    fi

    # Run tests
    test_extraction_latest || ((TESTS_FAILED++))
    test_extraction_history || ((TESTS_FAILED++))
    test_experiences_with_user || ((TESTS_FAILED++))

    # Summary
    echo ""
    echo "=========================================="
    echo "Test Summary"
    echo "=========================================="
    echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
    
    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}✅ All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}❌ Some tests failed${NC}"
        exit 1
    fi
}

main "$@"
