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
    if curl -sf "${DEMO_URL}/actuator/health" > /dev/null 2>&1; then
        log_info "Demo is running at ${DEMO_URL}"
        return 0
    else
        log_warn "Demo is NOT running at ${DEMO_URL}"
        log_warn "Start with: cd examples/cortex-mem-demo && mvn spring-boot:run -Plocal"
        return 1
    fi
}

# Test /memory/extraction/latest endpoint (V15: Extraction API)
test_extraction_latest() {
    log_section "Test: /memory/extraction/latest (V15)"

    local response
    response=$(curl -sf "${DEMO_URL}/memory/extraction/latest?project=${TEST_PROJECT}&template=user_preferences&userId=alice" 2>&1) || {
        log_fail "GET /memory/extraction/latest failed: $response"
        return 1
    }

    # Response should be JSON (may be empty or have status)
    if echo "$response" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
        log_info "Response is valid JSON"
    else
        log_fail "Response is not valid JSON"
        return 1
    fi

    log_info "Test PASSED"
    ((TESTS_PASSED++))
    return 0
}

# Test /memory/extraction/history endpoint (V15: Extraction History)
test_extraction_history() {
    log_section "Test: /memory/extraction/history (V15)"

    local response
    response=$(curl -sf "${DEMO_URL}/memory/extraction/history?project=${TEST_PROJECT}&template=user_preferences&userId=alice&limit=5" 2>&1) || {
        log_fail "GET /memory/extraction/history failed: $response"
        return 1
    }

    # Response should be a JSON list
    if echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); assert isinstance(data, list)" 2>/dev/null; then
        log_info "Response is a list (extraction history)"
    else
        log_fail "Response is not a list"
        return 1
    fi

    log_info "Test PASSED"
    ((TESTS_PASSED++))
    return 0
}

# Test /memory/experiences with userId parameter (V15: Multi-user)
test_experiences_with_user() {
    log_section "Test: /memory/experiences with userId (V15 multi-user)"

    local response
    response=$(curl -sf "${DEMO_URL}/memory/experiences?task=test&project=${TEST_PROJECT}&count=2" 2>&1) || {
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
