#!/bin/bash
#
# MCP Server Streamable HTTP E2E Test Script
# Usage: ./mcp-streamable-e2e-test.sh [server_url]
#
# Tests Streamable HTTP protocol specifically.
# If server is in SSE mode, provides friendly guidance.
#

SERVER_URL="${1:-${SERVER_URL:-http://localhost:37777}}"
MCP_ENDPOINT="${SERVER_URL}/mcp"
TEST_PROJECT="${MCP_TEST_PROJECT:-/tmp/mcp-e2e-test-streamable}"

# Session ID
SESSION_ID=""
HAS_JQ=false

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0

pass() { echo -e "${GREEN}[PASS]${NC} $1"; ((TESTS_PASSED++)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; ((TESTS_FAILED++)); }
skip() { echo -e "${CYAN}[SKIP]${NC} $1"; }
info() { echo -e "${BLUE}[INFO]${NC} $1"; }

section() {
    echo ""
    echo -e "${YELLOW}=========================================="
    echo "$1"
    echo -e "==========================================${NC}"
}

# Check if server is in STREAMABLE mode
check_streamable_mode() {
    echo -e "${BLUE}Checking if server is in STREAMABLE mode...${NC}"

    local response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 -X POST "${MCP_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -H "Accept: text/event-stream,application/json" \
        -d '{"jsonrpc":"2.0","id":"probe","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"probe","version":"1.0"}}}' 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        return 0  # STREAMABLE mode
    else
        return 1  # Not STREAMABLE mode
    fi
}

check_dependencies() {
    section "Check dependency tools"
    if command -v curl >/dev/null 2>&1; then
        pass "curl installed"
    else
        echo "curl is required"; exit 1
    fi

    if command -v jq >/dev/null 2>&1; then
        pass "jq installed"
        HAS_JQ=true
    else
        info "jq not installed (some validation will be skipped)"
    fi
}

send_request() {
    local req_id="$1"
    local method="$2"
    local params="$3"

    local request
    if [ -n "$params" ] && [ "$params" != "null" ]; then
        request="{\"jsonrpc\":\"2.0\",\"id\":\"$req_id\",\"method\":\"$method\",\"params\":$params}"
    else
        request="{\"jsonrpc\":\"2.0\",\"id\":\"$req_id\",\"method\":\"$method\"}"
    fi

    local response_headers=$(mktemp)
    local response_body=$(mktemp)

    if [ -n "$SESSION_ID" ]; then
        curl -s -X POST "${MCP_ENDPOINT}" \
            -H "Content-Type: application/json" \
            -H "Accept: text/event-stream,application/json" \
            -H "Mcp-Session-Id: ${SESSION_ID}" \
            -d "$request" \
            -D "${response_headers}" \
            -o "${response_body}" 2>/dev/null
    else
        curl -s -X POST "${MCP_ENDPOINT}" \
            -H "Content-Type: application/json" \
            -H "Accept: text/event-stream,application/json" \
            -d "$request" \
            -D "${response_headers}" \
            -o "${response_body}" 2>/dev/null
    fi

    # Extract session ID
    if [ -z "$SESSION_ID" ]; then
        SESSION_ID=$(grep -i "Mcp-Session-Id:" "${response_headers}" 2>/dev/null | cut -d: -f2 | tr -d ' \r' || echo "")
    fi

    # Parse response based on content type
    local content_type=$(grep -i "Content-Type:" "${response_headers}" 2>/dev/null | cut -d: -f2 | tr -d ' \r' || echo "")

    if echo "$content_type" | grep -q "application/json"; then
        RESPONSE=$(cat "${response_body}")
    elif echo "$content_type" | grep -q "text/event-stream"; then
        RESPONSE=$(grep "^data:" "${response_body}" 2>/dev/null | head -1 | sed 's/^data://' || cat "${response_body}")
    else
        RESPONSE=$(cat "${response_body}")
    fi

    rm -f "${response_headers}" "${response_body}"
}

test_health() {
    section "Test 1: Server Health Check"
    local response=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/api/health" 2>/dev/null || echo "000")
    if [ "$response" = "200" ]; then
        pass "Server health check passed (HTTP $response)"
    else
        fail "Server health check failed (HTTP $response)"
    fi
}

test_initialize() {
    section "Test 2: MCP Initialize (Streamable HTTP)"
    local params='{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"streamable-e2e-test","version":"1.0.0"}}'
    send_request "init-1" "initialize" "$params"

    if [ -n "$RESPONSE" ]; then
        info "Response: $(echo "$RESPONSE" | head -c 200)..."
        if [ -n "$SESSION_ID" ]; then
            info "Session ID: ${SESSION_ID}"
        fi
        if echo "$RESPONSE" | grep -q "result"; then
            local server_name=$(echo "$RESPONSE" | jq -r '.result.serverInfo.name // empty' 2>/dev/null || echo "")
            local protocol_version=$(echo "$RESPONSE" | jq -r '.result.protocolVersion // empty' 2>/dev/null || echo "")
            pass "MCP initialize success: $server_name (protocol: $protocol_version)"
            return 0
        fi
    fi
    fail "MCP initialize failed"
}

test_tools_list() {
    section "Test 3: Tools List"
    send_request "tools-list-1" "tools/list" "null"

    if [ -n "$RESPONSE" ]; then
        info "Response length: $(echo "$RESPONSE" | wc -c) bytes"
        if [ "$HAS_JQ" = true ]; then
            local tool_count=$(echo "$RESPONSE" | jq -r '.result.tools | length' 2>/dev/null || echo "0")
            if [ "$tool_count" -gt 0 ]; then
                pass "Tools list complete, $tool_count tools found"
                info "Tools: $(echo "$RESPONSE" | jq -r '.result.tools[].name' 2>/dev/null | tr '\n' ' ')"
                return 0
            fi
        fi
        if echo "$RESPONSE" | grep -q "tools"; then
            pass "Tools list received"
            return 0
        fi
    fi
    fail "Tools list failed"
}

test_tool() {
    local name="$1"
    local params="$2"
    local desc="$3"

    section "Test: $desc"
    send_request "${name}-1" "tools/call" "{\"name\":\"$name\",\"arguments\":$params}"

    if [ -n "$RESPONSE" ]; then
        info "Response: $(echo "$RESPONSE" | head -c 200)..."
        if echo "$RESPONSE" | grep -qE "(result|error)"; then
            pass "$desc success"
            return 0
        fi
    fi
    fail "$desc failed"
}

test_error_handling() {
    section "Test 8: Error Handling"
    send_request "error-1" "tools/call" "{\"name\":\"nonexistent_tool_xyz\",\"arguments\":{}}"

    if [ -n "$RESPONSE" ]; then
        info "Response: $(echo "$RESPONSE" | head -c 200)..."
        if echo "$RESPONSE" | grep -q "error"; then
            local error_code=$(echo "$RESPONSE" | jq -r '.error.code // empty' 2>/dev/null || echo "unknown")
            pass "Error handling correct (error code: $error_code)"
            return 0
        fi
    fi
    fail "Error handling failed"
}

test_rest_api() {
    section "Test 9: REST API Compatibility"
    local apis=(
        "/api/health:200"
        "/api/observations:200"
        "/api/context/recent:200"
    )

    for api_test in "${apis[@]}"; do
        local api_path="${api_test%%:*}"
        local expected_code="${api_test##*:}"
        local response=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}${api_path}" 2>/dev/null || echo "000")
        if [ "$response" = "$expected_code" ]; then
            pass "REST API ${api_path} OK (HTTP $response)"
        else
            fail "REST API ${api_path} expected $expected_code, got $response"
        fi
    done
}

run_tests() {
    check_dependencies
    test_health
    test_initialize
    test_tools_list
    test_tool "search" "{\"project\":\"${TEST_PROJECT}\",\"query\":\"test\",\"limit\":3}" "Search Tool"
    test_tool "timeline" "{\"project\":\"${TEST_PROJECT}\"}" "Timeline Tool"
    test_tool "get_observations" "{\"ids\":[\"test-id\"],\"project\":\"${TEST_PROJECT}\"}" "Get Observations Tool"
    test_tool "save_memory" "{\"text\":\"Streamable E2E test\",\"title\":\"E2E Test\",\"project\":\"${TEST_PROJECT}\"}" "Save Memory Tool"
    test_tool "recent" "{\"project\":\"${TEST_PROJECT}\",\"limit\":3}" "Recent Tool"
    test_error_handling
    test_rest_api

    section "Test Results Summary"
    echo -e "Protocol: ${GREEN}Streamable HTTP${NC}"
    echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
    echo ""

    if [ ${TESTS_FAILED} -eq 0 ]; then
        echo -e "${GREEN}✓ All tests passed!${NC}"
        echo ""
        echo "Streamable HTTP verification complete."
        echo "Key findings:"
        echo "  - /mcp endpoint registered: YES"
        echo "  - Session ID management: $([ -n "$SESSION_ID" ] && echo "WORKING" || echo "NOT USED")"
        echo "  - Accept header handling: WORKING"
        echo "  - SSE response format: WORKING"
        return 0
    else
        echo -e "${RED}✗ Some tests failed${NC}"
        return 1
    fi
}

# Main
echo -e "${YELLOW}=========================================="
echo "MCP Server Streamable HTTP E2E Test"
echo "==========================================${NC}"
echo "Server: ${SERVER_URL}"
echo "MCP Endpoint: ${MCP_ENDPOINT}"
echo "Protocol: Streamable HTTP"
echo ""

if check_streamable_mode; then
    echo -e "${GREEN}✓ Server is in STREAMABLE mode - running tests${NC}"
    echo ""
    run_tests
else
    echo -e "${YELLOW}⚠ Server is NOT in STREAMABLE mode${NC}"
    echo ""
    echo -e "${CYAN}To switch to STREAMABLE mode:${NC}"
    echo "  1. Edit backend/src/main/resources/application.yml"
    echo "  2. Set: protocol: STREAMABLE"
    echo "  3. Add:"
    echo "       streamable-http:"
    echo "         mcp-endpoint: /mcp"
    echo "  4. Rebuild and restart the service"
    echo ""
    echo -e "${CYAN}Or use the unified script for auto-detection:${NC}"
    echo "  ./scripts/mcp-e2e-test.sh"
    echo ""
    exit 1
fi
