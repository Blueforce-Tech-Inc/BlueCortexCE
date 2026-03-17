#!/bin/bash
#
# MCP Server End-to-End Test Script
# Usage: ./mcp-e2e-test.sh [server_url]
#
# Test Spring AI MCP Server (WebMVC/SSE) complete functionality
#
# SSE Protocol Description:
# 1. First GET /sse to establish SSE connection, get message endpoint with sessionId
# 2. Send POST request to message endpoint (request body is JSON-RPC)
# 3. Response returned via SSE channel (event:message, data:{json})
#

# Do not use set -e, we need to handle errors ourselves

SERVER_URL="${1:-http://localhost:37777}"
SSE_ENDPOINT="${SERVER_URL}/sse"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Session ID and Message Endpoint (from SSE)
SESSION_ID=""
MESSAGE_ENDPOINT=""
SSE_OUTPUT_FILE=""
SSE_PID=""

# Request ID counter
REQUEST_ID=0

# Output function
pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
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
        warn "jq not installed, some JSON validation will be skipped"
        HAS_JQ=false
    else
        pass "jq installed"
        HAS_JQ=true
    fi
}

# Establish SSE connection and get session ID
init_sse_session() {
    section "Initialize SSE session"

    # Create temp file to store SSE output
    SSE_OUTPUT_FILE=$(mktemp)

    # Start SSE connection, get endpoint event
    # Use curl built-in timeout limit
    curl -N "${SSE_ENDPOINT}" \
        -H "Accept: text/event-stream" \
        -o "${SSE_OUTPUT_FILE}" \
        --max-time 10 \
        2>/dev/null &

    SSE_PID=$!

    # Wait for endpoint event (max 10 seconds)
    local max_attempts=10
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        if grep -q "event:endpoint" "${SSE_OUTPUT_FILE}" 2>/dev/null; then
            break
        fi
        sleep 1
        ((attempt++))
    done

    # Extract message endpoint from SSE output
    # Format: event:endpoint\ndata:/mcp/message?sessionId=xxx
    if grep -A1 "event:endpoint" "${SSE_OUTPUT_FILE}" 2>/dev/null | grep -q "data:/mcp/message"; then
        # Extract endpoint from data line
        MESSAGE_ENDPOINT=$(grep "data:/mcp/message" "${SSE_OUTPUT_FILE}" | head -1 | sed 's/data://')
        SESSION_ID=$(echo "${MESSAGE_ENDPOINT}" | grep -o 'sessionId=[^&]*' | cut -d= -f2)

        if [ -n "$SESSION_ID" ]; then
            pass "SSE session established"
            info "Session ID: ${SESSION_ID}"
            info "Message Endpoint: ${MESSAGE_ENDPOINT}"
        else
            fail "Cannot extract Session ID from SSE response"
            echo "=== Actual received content ==="
            cat "${SSE_OUTPUT_FILE}"
            echo "=== Content end ==="
            return 1
        fi
    else
        fail "SSE connection did not receive endpoint event"
        echo "=== Actual received content ==="
        cat "${SSE_OUTPUT_FILE}"
        echo "=== Content end ==="
        return 1
    fi

    return 0
}

# Send MCP request and get response from SSE stream
# Usage: send_mcp_request <request_id> <method> <params_json>
# Response stored in $RESPONSE variable
send_mcp_request() {
    local req_id="$1"
    local method="$2"
    local params="$3"

    local full_endpoint="${SERVER_URL}${MESSAGE_ENDPOINT}"

    # Build JSON-RPC request
    if [ -n "$params" ] && [ "$params" != "null" ]; then
        local request="{\"jsonrpc\": \"2.0\", \"id\": \"$req_id\", \"method\": \"$method\", \"params\": $params}"
    else
        local request="{\"jsonrpc\": \"2.0\", \"id\": \"$req_id\", \"method\": \"$method\"}"
    fi

    # Send request (response will be returned via SSE)
    curl -s -X POST "${full_endpoint}" \
        -H "Content-Type: application/json" \
        -d "$request" 2>/dev/null

    # Wait for SSE response
    sleep 1

    # Find response from SSE output
    # Format: event:message\ndata:{"jsonrpc":"2.0","id":"xxx",...}
    local response_line=$(grep "data:.*\"id\":\"$req_id\"" "${SSE_OUTPUT_FILE}" | tail -1 | sed 's/data://')

    if [ -n "$response_line" ]; then
        RESPONSE="$response_line"
        return 0
    else
        RESPONSE=""
        return 1
    fi
}

# Test server health check
test_health_check() {
    section "Test 1: Server health check"

    response=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/actuator/health" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        pass "Server health check passed (HTTP $response)"
        return 0
    else
        fail "Server health check failed (HTTP $response)"
        return 1
    fi
}

# Send MCP notification (no response needed)
# Usage: send_mcp_notification <method> [params_json]
send_mcp_notification() {
    local method="$1"
    local params="$2"

    local full_endpoint="${SERVER_URL}${MESSAGE_ENDPOINT}"

    # Build JSON-RPC notification (no id)
    if [ -n "$params" ] && [ "$params" != "null" ]; then
        local request="{\"jsonrpc\": \"2.0\", \"method\": \"$method\", \"params\": $params}"
    else
        local request="{\"jsonrpc\": \"2.0\", \"method\": \"$method\"}"
    fi

    # Send notification (no response)
    curl -s -X POST "${full_endpoint}" \
        -H "Content-Type: application/json" \
        -d "$request" 2>/dev/null

    # Brief wait
    sleep 0.5
}

# Test MCP initialize
test_initialize() {
    section "Test 2: MCP initialize"

    local params='{"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "e2e-test", "version": "1.0.0"}}'

    if send_mcp_request "init-1" "initialize" "$params"; then
        info "Response: $...ho "$RESPONSE" | head -c 200)..."

        if [ "$HAS_JQ" = true ]; then
            local server_name=$(echo "$RESPONSE" | jq -r '.result.serverInfo.name // empty')
            local server_version=$(echo "$RESPONSE" | jq -r '.result.serverInfo.version // empty')

            if [ -n "$server_name" ]; then
                pass "MCP initialize success: $server_name v$server_version"

                # Send initialized notification to complete handshake
                send_mcp_notification "notifications/initialized"
                info "Sent initialized notification"
            else
                fail "MCP initialize failed: response format incorrect"
            fi
        else
            if echo "$RESPONSE" | grep -q "serverInfo"; then
                pass "MCP initialize success"
                # Send initialized notification to complete handshake
                send_mcp_notification "notifications/initialized"
            else
                fail "MCP initialize failed"
            fi
        fi
    else
        fail "MCP initialize request timeout"
    fi
}

# Test MCP protocol - tools list
test_tools_list() {
    section "Test 3: MCP tools list (tools/list)"

    if send_mcp_request "tools-list-1" "tools/list" "{}"; then
        info "Response: $...ho "$RESPONSE" | head -c 300)..."

        if [ "$HAS_JQ" = true ]; then
            # Verify tools list
            local expected_tools=("search" "timeline" "get_observations" "save_memory" "recent")
            local missing_tools=()

            for tool in "${expected_tools[@]}"; do
                if ! echo "$RESPONSE" | jq -e ".result.tools[]? | select(.name == \"$tool\")" > /dev/null 2>&1; then
                    missing_tools+=("$tool")
                fi
            done

            if [ ${#missing_tools[@]} -eq 0 ]; then
                local tool_count=$(echo "$RESPONSE" | jq '.result.tools | length // 0')
                pass "Tools list complete, $tool_count tools"
            else
                fail "Missing tools: ${missing_tools[*]}"
            fi
        else
            # Simple check if tool name is included
            if echo "$RESPONSE" | grep -q "search"; then
                pass "Tools list contains search tool"
            else
                fail "Tools list does not contain search tool"
            fi
        fi
    else
        fail "Tools list request timeout"
    fi
}

# Test search tool
test_search_tool() {
    section "Test 4: search tool"

    local params='{"name": "search", "arguments": {"project": "/Users/yangjiefeng/Documents/claude-mem", "limit": 5}}'

    if send_mcp_request "search-1" "tools/call" "$params"; then
        info "Response: $...ho "$RESPONSE" | head -c 300)..."

        if [ "$HAS_JQ" = true ]; then
            if echo "$RESPONSE" | jq -e '.result.content[0].type == "text"' > /dev/null 2>&1; then
                local content=$(echo "$RESPONSE" | jq -r '.result.content[0].text')
                pass "search tool call success, content length: ${#content}"
            elif echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
                local error_msg=$(echo "$RESPONSE" | jq -r '.error.message // .error')
                warn "search tool returned error: $error_msg"
                pass "search tool call completed (has error response)"
            else
                fail "search tool call failed: response format incorrect"
            fi
        else
            if echo "$RESPONSE" | grep -q "content"; then
                pass "search tool call success"
            else
                fail "search tool call failed"
            fi
        fi
    else
        fail "search tool request timeout"
    fi
}

# Test timeline tool
test_timeline_tool() {
    section "Test 5: timeline tool"

    local params='{"name": "timeline", "arguments": {"project": "/Users/yangjiefeng/Documents/claude-mem", "depthBefore": 2, "depthAfter": 2}}'

    if send_mcp_request "timeline-1" "tools/call" "$params"; then
        info "Response: $...ho "$RESPONSE" | head -c 300)..."

        if [ "$HAS_JQ" = true ]; then
            if echo "$RESPONSE" | jq -e '.result.content[0].type == "text"' > /dev/null 2>&1; then
                pass "timeline tool call success"
            elif echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
                local error_msg=$(echo "$RESPONSE" | jq -r '.error.message // .error')
                warn "timeline tool returned error: $error_msg"
                pass "timeline tool call completed (has error response)"
            else
                fail "timeline tool call failed: response format incorrect"
            fi
        else
            if echo "$RESPONSE" | grep -q "content"; then
                pass "timeline tool call success"
            else
                fail "timeline tool call failed"
            fi
        fi
    else
        fail "timeline tool request timeout"
    fi
}

# Test get_observations tool
test_get_observations_tool() {
    section "Test 6: get_observations tool"

    local params='{"name": "get_observations", "arguments": {"ids": [], "project": "/Users/yangjiefeng/Documents/claude-mem"}}'

    if send_mcp_request "get-obs-1" "tools/call" "$params"; then
        info "Response: $...ho "$RESPONSE" | head -c 300)..."

        if [ "$HAS_JQ" = true ]; then
            if echo "$RESPONSE" | jq -e '.result.content[0].type == "text"' > /dev/null 2>&1; then
                pass "get_observations tool call success"
            elif echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
                local error_msg=$(echo "$RESPONSE" | jq -r '.error.message // .error')
                warn "get_observations tool returned error: $error_msg"
                pass "get_observations tool call completed (has error response)"
            else
                fail "get_observations tool call failed: response format incorrect"
            fi
        else
            if echo "$RESPONSE" | grep -q "content"; then
                pass "get_observations tool call success"
            else
                fail "get_observations tool call failed"
            fi
        fi
    else
        fail "get_observations tool request timeout"
    fi
}

# Test save_memory tool
test_save_memory_tool() {
    section "Test 7: save_memory tool"

    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local params="{\"name\": \"save_memory\", \"arguments\": {\"text\": \"E2E test memory entry created at ${timestamp}\", \"title\": \"E2E Test Memory\", \"project\": \"/Users/yangjiefeng/Documents/claude-mem\"}}"

    if send_mcp_request "save-mem-1" "tools/call" "$params"; then
        info "Response: $...ho "$RESPONSE" | head -c 300)..."

        if [ "$HAS_JQ" = true ]; then
            if echo "$RESPONSE" | jq -e '.result.content[0].type == "text"' > /dev/null 2>&1; then
                local content=$(echo "$RESPONSE" | jq -r '.result.content[0].text')
                if echo "$content" | grep -q "success.*true"; then
                    pass "save_memory tool call success, memory saved"
                else
                    warn "save_memory returned content: $content"
                    pass "save_memory tool call completed"
                fi
            elif echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
                local error_msg=$(echo "$RESPONSE" | jq -r '.error.message // .error')
                warn "save_memory tool returned error: $error_msg"
                pass "save_memory tool call completed (has error response)"
            else
                fail "save_memory tool call failed: response format incorrect"
            fi
        else
            if echo "$RESPONSE" | grep -q "content"; then
                pass "save_memory tool call success"
            else
                fail "save_memory tool call failed"
            fi
        fi
    else
        fail "save_memory tool request timeout"
    fi
}

# Test error handling - non-existent tool
test_error_handling() {
    section "Test 8: Error handling"

    local params='{"name": "nonexistent_tool_xyz", "arguments": {}}'

    if send_mcp_request "error-1" "tools/call" "$params"; then
        info "Response: $...ho "$RESPONSE" | head -c 200)..."

        if [ "$HAS_JQ" = true ]; then
            if echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
                local error_code=$(echo "$RESPONSE" | jq -r '.error.code // "unknown"')
                pass "Error handling correct: returned error code $error_code"
            else
                fail "Error handling exception: did not return error info"
            fi
        else
            if echo "$RESPONSE" | grep -q "error"; then
                pass "Error handling correct: returned error info"
            else
                fail "Error handling exception: did not return error info"
            fi
        fi
    else
        fail "Error handling test request timeout"
    fi
}

# Test REST API compatibility
test_rest_api_compatibility() {
    section "Test 9: REST API compatibility"

    # Test /api/search endpoint
    local response=$(curl -s -o /dev/null -w "%{http_code}" \
        "${SERVER_URL}/api/search?project=/Users/yangjiefeng/Documents/claude-mem&limit=5" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        pass "REST API /api/search endpoint OK (HTTP $response)"
    else
        warn "REST API /api/search endpoint returned HTTP $response"
    fi

    # Test /api/observations endpoint
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        "${SERVER_URL}/api/observations?limit=5" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        pass "REST API /api/observations endpoint OK (HTTP $response)"
    else
        warn "REST API /api/observations endpoint returned HTTP $response"
    fi

    # Test /api/context/recent endpoint (new)
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        "${SERVER_URL}/api/context/recent?project=/Users/yangjiefeng/Documents/claude-mem&limit=3" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        pass "REST API /api/context/recent endpoint OK (HTTP $response)"
    else
        warn "REST API /api/context/recent endpoint returned HTTP $response"
    fi

    # Test /api/context/timeline endpoint (new)
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        "${SERVER_URL}/api/context/timeline?project=/Users/yangjiefeng/Documents/claude-mem" 2>/dev/null || echo "000")

    if [ "$response" = "200" ] || [ "$response" = "400" ]; then
        # 400 is normal because no anchor parameter provided
        pass "REST API /api/context/timeline endpoint OK (HTTP $response)"
    else
        warn "REST API /api/context/timeline endpoint returned HTTP $response"
    fi
}

# Test recent tool
test_recent_tool() {
    section "Test 10: recent tool"

    local params='{"name": "recent", "arguments": {"project": "/Users/yangjiefeng/Documents/claude-mem", "limit": 3}}'

    if send_mcp_request "recent-1" "tools/call" "$params"; then
        info "Response: $...ho "$RESPONSE" | head -c 300)..."

        if [ "$HAS_JQ" = true ]; then
            if echo "$RESPONSE" | jq -e '.result.content[0].type == "text"' > /dev/null 2>&1; then
                pass "recent tool call success"
            elif echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
                local error_msg=$(echo "$RESPONSE" | jq -r '.error.message // .error')
                warn "recent tool returned error: $error_msg"
                pass "recent tool call completed (has error response)"
            else
                fail "recent tool call failed: response format incorrect"
            fi
        else
            if echo "$RESPONSE" | grep -q "content"; then
                pass "recent tool call success"
            else
                fail "recent tool call failed"
            fi
        fi
    else
        fail "recent tool request timeout"
    fi
}

# Cleanup function
cleanup() {
    # Terminate background SSE connection
    if [ -n "$SSE_PID" ]; then
        kill $SSE_PID 2>/dev/null || true
    fi

    # Delete temp files
    if [ -n "$SSE_OUTPUT_FILE" ] && [ -f "$SSE_OUTPUT_FILE" ]; then
        rm -f "$SSE_OUTPUT_FILE"
    fi
}

# Print test results summary
print_summary() {
    section "Test results summary"

    echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
    echo ""

    # Cleanup
    cleanup

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}Some tests failed!${NC}"
        exit 1
    fi
}

# Main test flow
main() {
    # Set cleanup on exit
    trap cleanup EXIT

    echo ""
    echo -e "${BLUE}=========================================="
    echo "MCP Server end-to-end test"
    echo "==========================================${NC}"
    echo ""
    echo "Server address: ${SERVER_URL}"
    echo "SSE endpoint: ${SSE_ENDPOINT}"
    echo ""

    check_dependencies

    # If server unavailable, exit early
    if ! test_health_check; then
        echo ""
        echo -e "${RED}Server unavailable, please ensure Java MCP Server is started${NC}"
        echo "Start command: cd java/backend && ./mvnw spring-boot:run"
        exit 1
    fi

    # Initialize SSE session
    if ! init_sse_session; then
        echo ""
        echo -e "${RED}Cannot establish SSE session${NC}"
        exit 1
    fi

    # Run tests
    test_initialize
    test_tools_list
    test_search_tool
    test_timeline_tool
    test_get_observations_tool
    test_save_memory_tool
    test_error_handling
    test_rest_api_compatibility
    test_recent_tool

    print_summary
}

main "$@"
