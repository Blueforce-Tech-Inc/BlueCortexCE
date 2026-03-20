#!/bin/bash
#
# Demo V14 Feature Test Script
# Tests the new V14 endpoints added to examples/cortex-mem-demo
#
# Prerequisites:
# - Backend running on port 37777
# - Demo app running on port 37778 (mvn spring-boot:run -pl examples/cortex-mem-demo)
#

set -euo pipefail

DEMO_URL="${DEMO_URL:-http://localhost:37778}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:37777}"
TEST_PROJECT="/tmp/demo-v14-test"

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
    if curl -sf "${DEMO_URL}/actuator/health" > /dev/null 2>&1; then
        log_info "Demo is running at ${DEMO_URL}"
        return 0
    else
        log_warn "Demo is NOT running at ${DEMO_URL}"
        log_warn "Start with: cd examples/cortex-mem-demo && mvn spring-boot:run -Plocal"
        return 1
    fi
}

# Test /memory/icl/truncated endpoint (V14: maxChars)
test_icl_truncated() {
    log_section "Test: /memory/icl/truncated (V14 maxChars)"

    local response
    response=$(curl -sf "${DEMO_URL}/memory/icl/truncated?task=fix%20bug&project=${TEST_PROJECT}&maxChars=500" 2>&1) || {
        log_fail "GET /memory/icl/truncated failed: $response"
        return 1
    }

    # Check if response has expected fields
    if echo "$response" | grep -q "prompt"; then
        log_info "Response has 'prompt' field"
    else
        log_fail "Response missing 'prompt' field"
        return 1
    fi

    if echo "$response" | grep -q "experienceCount"; then
        log_info "Response has 'experienceCount' field"
    else
        log_fail "Response missing 'experienceCount' field"
        return 1
    fi

    log_info "Test PASSED"
    ((TESTS_PASSED++))
    return 0
}

# Test /memory/experiences/filtered endpoint (V14: source + requiredConcepts)
test_experiences_filtered() {
    log_section "Test: /memory/experiences/filtered (V14 source filtering)"

    # First create an observation with source
    local obs_response
    obs_response=$(curl -sf -X POST "${BACKEND_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{
            \"session_id\": \"demo-test\",
            \"project_path\": \"${TEST_PROJECT}\",
            \"type\": \"test\",
            \"title\": \"Demo Source Filter Test\",
            \"source\": \"demo_test_source\",
            \"concepts\": [\"demo-filter-test\"],
            \"prompt_number\": 1
        }" 2>&1) || {
        log_warn "Could not create test observation: $obs_response"
    }

    # Test filtered experiences
    local response
    response=$(curl -sf "${DEMO_URL}/memory/experiences/filtered?task=test&project=${TEST_PROJECT}&source=demo_test_source" 2>&1) || {
        log_fail "GET /memory/experiences/filtered failed: $response"
        return 1
    }

    # Should return a list
    if echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); assert isinstance(data, list)" 2>/dev/null; then
        log_info "Response is a list (experiences)"
    else
        log_fail "Response is not a list"
        return 1
    fi

    log_info "Test PASSED"
    ((TESTS_PASSED++))
    return 0
}

# Test /memory/health endpoint
test_memory_health() {
    log_section "Test: /memory/health"

    local response
    response=$(curl -sf "${DEMO_URL}/memory/health?project=${TEST_PROJECT}" 2>&1) || {
        log_fail "GET /memory/health failed: $response"
        return 1
    }

    if echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); assert data.get('status') == 'ok'" 2>/dev/null; then
        log_info "Health check returned status=ok"
    else
        log_fail "Health check did not return status=ok"
        return 1
    fi

    log_info "Test PASSED"
    ((TESTS_PASSED++))
    return 0
}

# Test basic /memory/experiences endpoint (should still work)
test_basic_experiences() {
    log_section "Test: /memory/experiences (basic)"

    local response
    response=$(curl -sf "${DEMO_URL}/memory/experiences?task=test&project=${TEST_PROJECT}" 2>&1) || {
        log_fail "GET /memory/experiences failed: $response"
        return 1
    }

    if echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); assert isinstance(data, list)" 2>/dev/null; then
        log_info "Response is a list (experiences)"
    else
        log_fail "Response is not a list"
        return 1
    fi

    log_info "Test PASSED"
    ((TESTS_PASSED++))
    return 0
}

# Main
main() {
    echo "=========================================="
    echo "   Demo V14 Feature Test Suite"
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
    test_basic_experiences || ((TESTS_FAILED++))
    test_icl_truncated || ((TESTS_FAILED++))
    test_experiences_filtered || ((TESTS_FAILED++))
    test_memory_health || ((TESTS_FAILED++))

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
