#!/bin/bash
#
# MCP Server End-to-End Test Script
# Smart protocol detection: auto-detects SSE or Streamable HTTP mode
# Usage: ./mcp-e2e-test.sh [server_url]
#
# Automatically detects server protocol by probing endpoints:
# - SSE mode: /sse returns 200, /mcp returns 404
# - STREAMABLE mode: /mcp returns 200, /sse returns 404 or works
#

SERVER_URL="${1:-${SERVER_URL:-http://localhost:37777}}"
TEST_PROJECT="${MCP_TEST_PROJECT:-/tmp/mcp-e2e-test}"

# Detected protocol
DETECTED_PROTOCOL=""

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

# Output functions
pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

skip() {
    echo -e "${CYAN}[SKIP]${NC} $1"
    ((TESTS_SKIPPED++))
}

fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

section() {
    echo ""
    echo -e "${YELLOW}=========================================="
    echo "$1"
    echo -e "==========================================${NC}"
}

# Check dependencies
check_dependencies() {
    section "Check dependency tools"

    if ! command -v curl >/dev/null 2>&1; then
        echo "curl is required"
        exit 1
    fi
    pass "curl installed"

    if ! command -v jq >/dev/null 2>&1; then
        info "jq not installed (some validation will be skipped)"
        HAS_JQ=false
    else
        pass "jq installed"
        HAS_JQ=true
    fi
}

# Detect which protocol the server is using
detect_protocol() {
    section "Detecting MCP Server Protocol"

    # Test SSE endpoint (don't treat timeout as failure)
    local sse_status
    sse_status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "${SERVER_URL}/sse" 2>/dev/null)
    # Timeout (28) is still a valid connection - treat as potential SSE
    [ "$sse_status" = "28" ] && sse_status="200"

    # Test STREAMABLE endpoint (proper initialize request)
    local mcp_status
    mcp_status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 -X POST "${SERVER_URL}/mcp" \
        -H "Content-Type: application/json" \
        -H "Accept: text/event-stream,application/json" \
        -d '{"jsonrpc":"2.0","id":"probe","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"probe","version":"1.0"}}}' 2>/dev/null)

    info "SSE endpoint (/sse): HTTP $sse_status"
    info "STREAMABLE endpoint (/mcp): HTTP $mcp_status"

    if [ "$sse_status" = "200" ] && [ "$mcp_status" = "404" ]; then
        DETECTED_PROTOCOL="SSE"
        echo ""
        echo -e "${GREEN}✓ Detected: SSE Mode${NC}"
        info "Server is using SSE (Server-Sent Events) protocol"
    elif [ "$mcp_status" = "200" ]; then
        DETECTED_PROTOCOL="STREAMABLE"
        echo ""
        echo -e "${GREEN}✓ Detected: Streamable HTTP Mode${NC}"
        info "Server is using Streamable HTTP protocol"
    elif [ "$sse_status" = "200" ] || [ "$mcp_status" = "200" ]; then
        # Mixed state - try to determine
        if [ "$sse_status" = "200" ]; then
            DETECTED_PROTOCOL="SSE"
            echo ""
            echo -e "${GREEN}✓ Detected: SSE Mode (inferred from /sse)${NC}"
        else
            DETECTED_PROTOCOL="STREAMABLE"
            echo ""
            echo -e "${GREEN}✓ Detected: Streamable HTTP Mode (inferred from /mcp)${NC}"
        fi
    else
        echo ""
        echo -e "${RED}✗ Cannot detect MCP protocol!${NC}"
        echo -e "${RED}  Both /sse and /mcp endpoints are not responding correctly.${NC}"
        echo -e "${YELLOW}  Please check if the MCP server is running.${NC}"
        exit 1
    fi

    info "Detected protocol: $DETECTED_PROTOCOL"
}

# =============================================================================
# SSE Mode Tests
# =============================================================================

# SSE session globals
MESSAGE_ENDPOINT=""
SSE_OUTPUT_FILE=""
SSE_PID=""

# Establish SSE connection
init_sse_session() {
    section "Initialize SSE Session"

    SSE_ENDPOINT="${SERVER_URL}/sse"
    SSE_OUTPUT_FILE=$(mktemp)

    curl -N "${SSE_ENDPOINT}" \
        -H "Accept: text/event-stream" \
        -o "${SSE_OUTPUT_FILE}" \
        --max-time 10 \
        2>/dev/null &

    SSE_PID=$!

    local max_attempts=10
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        if grep -q "event:endpoint" "${SSE_OUTPUT_FILE}" 2>/dev/null; then
            break
        fi
        sleep 1
        ((attempt++))
    done

    if grep -A1 "event:endpoint" "${SSE_OUTPUT_FILE}" 2>/dev/null | grep -q "data:/mcp/message"; then
        MESSAGE_ENDPOINT=$(grep "data:/mcp/message" "${SSE_OUTPUT_FILE}" | head -1 | sed 's/data://')
        SESSION_ID=$(echo "${MESSAGE_ENDPOINT}" | grep -o 'sessionId=[^&]*' | cut -d= -f2)

        if [ -n "$SESSION_ID" ]; then
            pass "SSE session established"
            info "Session ID: ${SESSION_ID}"
            info "Message Endpoint: ${MESSAGE_ENDPOINT}"
            return 0
        else
            fail "Cannot extract Session ID"
            return 1
        fi
    else
        fail "SSE connection did not receive endpoint event"
        return 1
    fi
}

# Send SSE request
send_sse_request() {
    local req_id="$1"
    local method="$2"
    local params="$3"

    local full_endpoint="${SERVER_URL}${MESSAGE_ENDPOINT}"

    if [ -n "$params" ] && [ "$params" != "null" ]; then
        local request="{\"jsonrpc\": \"2.0\", \"id\": \"$req_id\", \"method\": \"$method\", \"params\": $params}"
    else
        local request="{\"jsonrpc\": \"2.0\", \"id\": \"$req_id\", \"method\": \"$method\"}"
    fi

    curl -s -X POST "${full_endpoint}" \
        -H "Content-Type: application/json" \
        -d "$request" 2>/dev/null

    local max_wait=10
    local waited=0
    local response_line=""
    while [ $waited -lt $max_wait ]; do
        sleep 0.5
        waited=$((waited + 1))
        response_line=$(grep "data:" "${SSE_OUTPUT_FILE}" 2>/dev/null | grep "\"id\"" | grep "$req_id" | tail -1 | sed 's/^data:\s*//; s/^data://')
        if [ -n "$response_line" ]; then
            break
        fi
    done

    if [ -n "$response_line" ]; then
        RESPONSE="$response_line"
        return 0
    else
        RESPONSE=""
        return 1
    fi
}

send_sse_notification() {
    local method="$1"
    local params="$2"

    local full_endpoint="${SERVER_URL}${MESSAGE_ENDPOINT}"

    if [ -n "$params" ] && [ "$params" != "null" ]; then
        local request="{\"jsonrpc\": \"2.0\", \"method\": \"$method\", \"params\": $params}"
    else
        local request="{\"jsonrpc\": \"2.0\", \"method\": \"$method\"}"
    fi

    curl -s -X POST "${full_endpoint}" \
        -H "Content-Type: application/json" \
        -d "$request" 2>/dev/null

    sleep 0.5
}

# SSE Mode test functions
run_sse_tests() {
    section "Running SSE Mode Tests"

    # Health check
    test_health_sse() {
        section "Test 1: Server Health Check"
        local response=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/actuator/health" 2>/dev/null || echo "000")
        if [ "$response" = "200" ]; then
            pass "Server health check passed (HTTP $response)"
        else
            fail "Server health check failed (HTTP $response)"
        fi
    }

    test_initialize_sse() {
        section "Test 2: MCP Initialize"
        local params='{"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "e2e-test", "version": "1.0.0"}}'
        if send_sse_request "init-1" "initialize" "$params"; then
            info "Response: $(echo "$RESPONSE" | head -c 200)..."
            if echo "$RESPONSE" | grep -q "result"; then
                local server_name=$(echo "$RESPONSE" | jq -r '.result.serverInfo.name // empty' 2>/dev/null || echo "")
                pass "MCP initialize success: $server_name"
                send_sse_notification "notifications/initialized"
            else
                fail "MCP initialize failed"
            fi
        else
            fail "MCP initialize: no response"
        fi
    }

    test_tools_list_sse() {
        section "Test 3: Tools List"
        if send_sse_request "tools-list-1" "tools/list" "null"; then
            info "Response length: $(echo "$RESPONSE" | wc -c) bytes"
            if [ "$HAS_JQ" = true ]; then
                local tool_count=$(echo "$RESPONSE" | jq -r '.result.tools | length' 2>/dev/null || echo "0")
                if [ "$tool_count" -gt 0 ]; then
                    pass "Tools list complete, $tool_count tools"
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

    test_tool_sse() {
        local name="$1"
        local params="$2"
        local desc="$3"
        section "Test: $desc"
        if send_sse_request "$name-1" "tools/call" "{\"name\":\"$name\",\"arguments\":$params}"; then
            info "Response: $(echo "$RESPONSE" | head -c 150)..."
            if echo "$RESPONSE" | grep -qE "(result|error)"; then
                pass "$desc success"
                return 0
            fi
        fi
        fail "$desc failed"
    }

    test_error_sse() {
        section "Test: Error Handling"
        if send_sse_request "error-1" "tools/call" "{\"name\":\"nonexistent_tool_xyz\",\"arguments\":{}}"; then
            info "Response: $(echo "$RESPONSE" | head -c 200)..."
            if echo "$RESPONSE" | grep -q "error"; then
                pass "Error handling correct"
                return 0
            fi
        fi
        fail "Error handling test failed"
    }

    # Initialize SSE session
    init_sse_session || {
        echo -e "${RED}Failed to initialize SSE session - cannot continue SSE tests${NC}"
        return 1
    }

    test_health_sse
    test_initialize_sse
    test_tools_list_sse
    test_tool_sse "search" "{\"project\":\"${TEST_PROJECT}\",\"query\":\"test\",\"limit\":3}" "Search Tool"
    test_tool_sse "timeline" "{\"project\":\"${TEST_PROJECT}\"}" "Timeline Tool"
    test_tool_sse "get_observations" "{\"ids\":[\"test\"],\"project\":\"${TEST_PROJECT}\"}" "Get Observations Tool"
    test_tool_sse "save_memory" "{\"text\":\"test\",\"project\":\"${TEST_PROJECT}\"}" "Save Memory Tool"
    test_tool_sse "recent" "{\"project\":\"${TEST_PROJECT}\",\"limit\":3}" "Recent Tool"
    test_error_sse
}

# =============================================================================
# STREAMABLE Mode Tests
# =============================================================================

# Streamable globals
SESSION_ID=""

# Send STREAMABLE request
send_streamable_request() {
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
        curl -s -X POST "${SERVER_URL}/mcp" \
            -H "Content-Type: application/json" \
            -H "Accept: text/event-stream,application/json" \
            -H "Mcp-Session-Id: ${SESSION_ID}" \
            -d "$request" \
            -D "${response_headers}" \
            -o "${response_body}" 2>/dev/null
    else
        curl -s -X POST "${SERVER_URL}/mcp" \
            -H "Content-Type: application/json" \
            -H "Accept: text/event-stream,application/json" \
            -d "$request" \
            -D "${response_headers}" \
            -o "${response_body}" 2>/dev/null
    fi

    # Extract session ID
    if [ -z "$SESSION_ID" ]; then
        local new_session=$(grep -i "Mcp-Session-Id:" "${response_headers}" 2>/dev/null | cut -d: -f2 | tr -d ' \r' || echo "")
        if [ -n "$new_session" ]; then
            SESSION_ID="$new_session"
        fi
    fi

    # Parse response
    local content_type=$(grep -i "Content-Type:" "${response_headers}" 2>/dev/null | cut -d: -f2 | tr -d ' \r' || echo "")

    if echo "$content_type" | grep -q "application/json"; then
        RESPONSE=$(cat "${response_body}")
    elif echo "$content_type" | grep -q "text/event-stream"; then
        RESPONSE=$(grep "^data:" "${response_body}" 2>/dev/null | head -1 | sed 's/^data://' || cat "${response_body}")
    else
        RESPONSE=$(cat "${response_body}")
    fi

    rm -f "${response_headers}" "${response_body}"
    return 0
}

run_streamable_tests() {
    section "Running Streamable HTTP Mode Tests"

    # Health check
    test_health_streamable() {
        section "Test 1: Server Health Check"
        local response=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/api/health" 2>/dev/null || echo "000")
        if [ "$response" = "200" ]; then
            pass "Server health check passed (HTTP $response)"
        else
            fail "Server health check failed (HTTP $response)"
        fi
    }

    test_initialize_streamable() {
        section "Test 2: MCP Initialize"
        local params='{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"e2e-test","version":"1.0.0"}}'
        send_streamable_request "init-1" "initialize" "$params"
        if [ -n "$RESPONSE" ]; then
            info "Response: $(echo "$RESPONSE" | head -c 200)..."
            info "Session ID: ${SESSION_ID}"
            if echo "$RESPONSE" | grep -q "result"; then
                local server_name=$(echo "$RESPONSE" | jq -r '.result.serverInfo.name // empty' 2>/dev/null || echo "")
                pass "MCP initialize success: $server_name"
                return 0
            fi
        fi
        fail "MCP initialize failed"
    }

    test_tools_list_streamable() {
        section "Test 3: Tools List"
        send_streamable_request "tools-list-1" "tools/list" "null"
        if [ -n "$RESPONSE" ]; then
            info "Response length: $(echo "$RESPONSE" | wc -c) bytes"
            if [ "$HAS_JQ" = true ]; then
                local tool_count=$(echo "$RESPONSE" | jq -r '.result.tools | length' 2>/dev/null || echo "0")
                if [ "$tool_count" -gt 0 ]; then
                    pass "Tools list complete, $tool_count tools"
                    info "Tools: $(echo "$RESPONSE" | jq -r '.result.tools[].name' 2>/dev/null | tr '\n' ' ')"
                    return 0
                fi
            fi
        fi
        fail "Tools list failed"
    }

    test_tool_streamable() {
        local name="$1"
        local params="$2"
        local desc="$3"
        section "Test: $desc"
        send_streamable_request "$name-1" "tools/call" "{\"name\":\"$name\",\"arguments\":$params}"
        if [ -n "$RESPONSE" ]; then
            info "Response: $(echo "$RESPONSE" | head -c 150)..."
            if echo "$RESPONSE" | grep -qE "(result|error)"; then
                pass "$desc success"
                return 0
            fi
        fi
        fail "$desc failed"
    }

    test_error_streamable() {
        section "Test: Error Handling"
        send_streamable_request "error-1" "tools/call" "{\"name\":\"nonexistent_tool_xyz\",\"arguments\":{}}"
        if [ -n "$RESPONSE" ]; then
            info "Response: $(echo "$RESPONSE" | head -c 200)..."
            if echo "$RESPONSE" | grep -q "error"; then
                pass "Error handling correct"
                return 0
            fi
        fi
        fail "Error handling test failed"
    }

    test_rest_api() {
        section "Test: REST API Compatibility"
        local apis=(
            "/api/health:200"
            "/api/observations:200"
            "/api/context/recent:200"
        )
        local all_passed=true
        for api_test in "${apis[@]}"; do
            local api_path="${api_test%%:*}"
            local expected_code="${api_test##*:}"
            local response=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}${api_path}" 2>/dev/null || echo "000")
            if [ "$response" = "$expected_code" ]; then
                pass "REST API ${api_path} OK (HTTP $response)"
            else
                fail "REST API ${api_path} expected $expected_code, got $response"
                all_passed=false
            fi
        done
    }

    test_health_streamable
    test_initialize_streamable
    test_tools_list_streamable
    test_tool_streamable "search" "{\"project\":\"${TEST_PROJECT}\",\"query\":\"test\",\"limit\":3}" "Search Tool"
    test_tool_streamable "timeline" "{\"project\":\"${TEST_PROJECT}\"}" "Timeline Tool"
    test_tool_streamable "get_observations" "{\"ids\":[\"test\"],\"project\":\"${TEST_PROJECT}\"}" "Get Observations Tool"
    test_tool_streamable "save_memory" "{\"text\":\"test\",\"project\":\"${TEST_PROJECT}\"}" "Save Memory Tool"
    test_tool_streamable "recent" "{\"project\":\"${TEST_PROJECT}\",\"limit\":3}" "Recent Tool"
    test_error_streamable
    test_rest_api
}

# =============================================================================
# Main
# =============================================================================

run_all_tests() {
    echo -e "${YELLOW}=========================================="
    echo "MCP Server End-to-End Test (Auto-Detect)"
    echo "==========================================${NC}"
    echo "Server: ${SERVER_URL}"
    echo "Test project: ${TEST_PROJECT}"
    echo ""

    check_dependencies
    detect_protocol

    if [ "$DETECTED_PROTOCOL" = "SSE" ]; then
        run_sse_tests
    elif [ "$DETECTED_PROTOCOL" = "STREAMABLE" ]; then
        run_streamable_tests
    else
        echo -e "${RED}Unknown protocol - cannot run tests${NC}"
        exit 1
    fi

    # Cleanup SSE background process if running
    if [ -n "$SSE_PID" ] && kill -0 "$SSE_PID" 2>/dev/null; then
        kill "$SSE_PID" 2>/dev/null
    fi
    rm -f "${SSE_OUTPUT_FILE}" 2>/dev/null

    # Summary
    section "Test Results Summary"
    echo -e "Protocol tested: ${GREEN}${DETECTED_PROTOCOL}${NC}"
    echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
    echo -e "Skipped: ${CYAN}${TESTS_SKIPPED}${NC}"
    echo ""

    if [ ${TESTS_FAILED} -eq 0 ]; then
        echo -e "${GREEN}✓ All tests passed!${NC}"
        return 0
    else
        echo -e "${RED}✗ Some tests failed${NC}"
        return 1
    fi
}

run_all_tests
exit $?
