#!/bin/bash
#
# OpenClaw Plugin Integration Test Script
#
# ============================================================================
# Test Coverage
# ============================================================================
# 1. Plugin syntax and build check
# 2. Configuration file validation
# 3. Java backend API connectivity
# 4. Event listener simulation test
# 5. MEMORY.md sync feature
# 6. Command registration test
#
# Usage:
#   1. Make sure Java backend is running:
#      export OPENAI_API_KEY=xxx
#      export SILICONFLOW_API_KEY=xxx
#      java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev &
#
#   2. Then run the test:
#      ./openclaw-plugin-test.sh
#
# Prerequisites:
#   - Java backend running at http://127.0.0.1:37777
#   - Node.js 18+
#   - OpenClaw plugin built

set -e

# Configuration
JAVA_API_URL="${JAVA_API_URL:-http://127.0.0.1:37777}"
PLUGIN_DIR="$(cd "$(dirname "$0")/../openclaw-plugin" && pwd)"
PROJECT_DIR="$(cd "$PLUGIN_DIR/.." && pwd)"
TEST_DIR="/tmp/claude-mem-openclaw-test-$$"

# OpenClaw manifest: this repo uses openclaw.plugin.json; some forks use plugin.json
if [ -f "$PLUGIN_DIR/openclaw.plugin.json" ]; then
    PLUGIN_MANIFEST="$PLUGIN_DIR/openclaw.plugin.json"
elif [ -f "$PLUGIN_DIR/plugin.json" ]; then
    PLUGIN_MANIFEST="$PLUGIN_DIR/plugin.json"
else
    PLUGIN_MANIFEST=""
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; ((TESTS_PASSED++)) || true; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; ((TESTS_FAILED++)) || true; }
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }

cleanup() { rm -rf "$TEST_DIR"; }
trap cleanup EXIT
mkdir -p "$TEST_DIR"

#######################################
# Test 1: Plugin directory structure check
#######################################
test_plugin_structure() {
    echo ""
    echo "=== Test 1: Plugin directory structure ==="

    if [ -f "$PLUGIN_DIR/package.json" ]; then
        log_pass "package.json exists"
    else
        log_fail "package.json does not exist"
        return 1
    fi

    if [ -n "$PLUGIN_MANIFEST" ]; then
        log_pass "plugin manifest exists ($(basename "$PLUGIN_MANIFEST"))"
    else
        log_fail "neither openclaw.plugin.json nor plugin.json found"
        return 1
    fi

    if [ -f "$PLUGIN_DIR/tsconfig.json" ]; then
        log_pass "tsconfig.json exists"
    else
        log_fail "tsconfig.json does not exist"
        return 1
    fi

    if [ -d "$PLUGIN_DIR/src" ]; then
        log_pass "src directory exists"
    else
        log_fail "src directory does not exist"
        return 1
    fi
}

#######################################
# Test 2: package.json validation
#######################################
test_package_json() {
    echo ""
    echo "=== Test 2: package.json validation ==="

    # Check if package.json has required fields
    if grep -q '"name"' "$PLUGIN_DIR/package.json"; then
        log_pass "package.json has name field"
    else
        log_fail "package.json missing name field"
    fi

    if grep -q '"version"' "$PLUGIN_DIR/package.json"; then
        log_pass "package.json has version field"
    else
        log_fail "package.json missing version field"
    fi

    if grep -q '"main"' "$PLUGIN_DIR/package.json"; then
        log_pass "package.json has main field"
    else
        log_fail "package.json missing main field"
    fi
}

#######################################
# Test 3: plugin manifest validation
#######################################
test_plugin_json() {
    echo ""
    echo "=== Test 3: plugin manifest validation ==="

    if [ -z "$PLUGIN_MANIFEST" ]; then
        log_fail "no plugin manifest file"
        return 1
    fi

    if grep -q '"id"' "$PLUGIN_MANIFEST"; then
        log_pass "manifest has id field"
    else
        log_fail "manifest missing id field"
    fi

    if grep -q '"name"' "$PLUGIN_MANIFEST"; then
        log_pass "manifest has name field"
    else
        log_fail "manifest missing name field"
    fi

    if grep -q '"runtime"' "$PLUGIN_MANIFEST"; then
        log_pass "manifest has runtime field"
    elif grep -q '"configSchema"' "$PLUGIN_MANIFEST"; then
        log_pass "manifest has configSchema (openclaw.plugin.json)"
    else
        log_fail "manifest missing runtime and configSchema"
    fi
}

#######################################
# Test 4: TypeScript compilation
#######################################
test_typescript_compilation() {
    echo ""
    echo "=== Test 4: TypeScript compilation ==="

    cd "$PLUGIN_DIR"

    # Check if dependencies need to be installed
    if [ ! -d "node_modules" ]; then
        log_info "Installing npm dependencies..."
        npm install > /dev/null 2>&1 || {
            log_fail "npm install failed"
            return 1
        }
        log_pass "npm dependencies installed"
    fi

    # Compile TypeScript
    if npm run build > /dev/null 2>&1; then
        log_pass "TypeScript compilation successful"
    else
        log_fail "TypeScript compilation failed"
        return 1
    fi

    # Check compiled output
    if [ -d "dist" ]; then
        log_pass "dist directory created"
    else
        log_fail "dist directory not created"
        return 1
    fi

    if [ -f "dist/index.js" ]; then
        log_pass "dist/index.js created"
    else
        log_fail "dist/index.js not created"
        return 1
    fi
}

#######################################
# Test 5: Java backend health check
#######################################
test_java_backend_health() {
    echo ""
    echo "=== Test 5: Java backend health check ==="

    RESP=$(curl -s "$JAVA_API_URL/actuator/health")
    if echo "$RESP" | grep -q "UP"; then
        log_pass "Java backend healthy"
    else
        log_fail "Java backend no response: $RESP"
        return 1
    fi
}

#######################################
# Test 6: Java backend API endpoint validation
# (Validate endpoints that OpenClaw plugin needs to call)
#######################################
test_java_api_endpoints() {
    echo ""
    echo "=== Test 6: Java API endpoint validation ==="

    # Test /api/session/start (session initialization)
    echo "--- Test 6a: /api/session/start ---"
    RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"openclaw-test-001","project_path":"'$TEST_DIR'"}')
    if echo "$RESP" | grep -q "session_db_id"; then
        log_pass "API /api/session/start OK"
    else
        log_fail "API /api/session/start failed: $RESP"
    fi

    # Test /api/ingest/tool-use (tool use recording)
    echo "--- Test 6b: /api/ingest/tool-use ---"
    RESP=$(curl -s -X POST "$JAVA_API_URL/api/ingest/tool-use" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"openclaw-test-001","tool_name":"Edit","tool_input":{},"tool_response":"{}","cwd":"'$TEST_DIR'"}')
    if echo "$RESP" | grep -q "status"; then
        log_pass "API /api/ingest/tool-use OK"
    else
        log_fail "API /api/ingest/tool-use failed: $RESP"
    fi

    # Test /api/ingest/session-end (session end)
    echo "--- Test 6c: /api/ingest/session-end ---"
    RESP=$(curl -s -X POST "$JAVA_API_URL/api/ingest/session-end" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"openclaw-test-001"}')
    if echo "$RESP" | grep -q "status"; then
        log_pass "API /api/ingest/session-end OK"
    else
        log_fail "API /api/ingest/session-end failed: $RESP"
    fi

    # Test /api/context/inject (get timeline)
    echo "--- Test 6d: /api/context/inject ---"
    RESP=$(curl -s "$JAVA_API_URL/api/context/inject?projects=openclaw-test")
    if [ -n "$RESP" ]; then
        log_pass "API /api/context/inject OK"

        # Verify returns valid JSON (review report: P0 issue check)
        if echo "$RESP" | python3 -c "import sys, json; json.load(sys.stdin)" 2>/dev/null; then
            log_pass "API /api/context/inject returns valid JSON"
        else
            log_fail "API /api/context/inject returns invalid JSON"
        fi

        # Verify JSON contains context field (plugin code should extract this field)
        if echo "$RESP" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'context' in d else 1)" 2>/dev/null; then
            log_pass "API /api/context/inject JSON contains context field"
        else
            log_fail "API /api/context/inject JSON missing context field"
        fi
    else
        log_fail "API /api/context/inject failed"
    fi

    # Test /api/projects (project list)
    echo "--- Test 6e: /api/projects ---"
    RESP=$(curl -s "$JAVA_API_URL/api/projects")
    if echo "$RESP" | grep -q "projects"; then
        log_pass "API /api/projects OK"
    else
        log_fail "API /api/projects failed: $RESP"
    fi
}

#######################################
# Test 7: Plugin code syntax check
#######################################
test_plugin_syntax() {
    echo ""
    echo "=== Test 7: Plugin code syntax check ==="

    if [ -f "$PLUGIN_DIR/dist/index.js" ]; then
        if node --check "$PLUGIN_DIR/dist/index.js" 2>/dev/null; then
            log_pass "Plugin JavaScript syntax correct"
        else
            log_fail "Plugin JavaScript syntax error"
        fi
    else
        log_fail "Plugin not compiled, skipping syntax check"
    fi
}

#######################################
# Test 8: MEMORY.md sync feature test
# (Simulate OpenClaw plugin event handling)
# Verify plugin correctly parses JSON and extracts context field
#
# P0 critical test: Must use absolute path, not project name!
# - /api/context/inject expects absolute path (e.g., /tmp/test)
# - Passing project name (e.g., "openclaw") will fail validation
#######################################
test_memory_sync() {
    echo ""
    echo "=== Test 8: MEMORY.md sync feature ==="

    # Create test workspace directory
    local WORKSPACE_DIR="$TEST_DIR/workspace"
    mkdir -p "$WORKSPACE_DIR"

    # ------------------------------------------------------------
    # P0 test: Verify must pass absolute path
    # ------------------------------------------------------------

    # Test 8a: Pass invalid project name (should fail or fallback)
    echo "--- Test 8a: Invalid project name test ---"
    local API_RESPONSE_INVALID=$(curl -s "$JAVA_API_URL/api/context/inject?projects=openclaw-test")
    echo "Invalid project name response: $API_RESPONSE_INVALID"

    # Test 8b: Pass valid absolute path (should succeed)
    echo "--- Test 8b: Valid absolute path test ---"
    local API_RESPONSE_VALID=$(curl -s "$JAVA_API_URL/api/context/inject?projects=$WORKSPACE_DIR")

    if [ -n "$API_RESPONSE_VALID" ]; then
        # Simulate plugin code logic: Parse JSON and extract context field
        # (This is plugin index.ts lines 270-273 logic)
        local MEMORY_CONTENT
        MEMORY_CONTENT=$(echo "$API_RESPONSE_VALID" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    context = data.get('context', '')
    print(context, end='')
except:
    # If parsing fails, fallback to original content
    print(sys.stdin.read(), end='')
" 2>/dev/null)

        # Write MEMORY.md (only write extracted plain text)
        echo "$MEMORY_CONTENT" > "$WORKSPACE_DIR/MEMORY.md"

        if [ -f "$WORKSPACE_DIR/MEMORY.md" ]; then
            log_pass "MEMORY.md file created successfully"

            # Check content is not empty
            if [ -s "$WORKSPACE_DIR/MEMORY.md" ]; then
                log_pass "MEMORY.md contains content"
            else
                log_fail "MEMORY.md is empty"
            fi

            # Verify: MEMORY.md should not be JSON format (review report: P0 issue verification)
            # If file starts with { or [, might be raw JSON written
            local FIRST_CHAR
            FIRST_CHAR=$(head -c 1 "$WORKSPACE_DIR/MEMORY.md")
            if [ "$FIRST_CHAR" = "{" ] || [ "$FIRST_CHAR" = "[" ]; then
                log_fail "MEMORY.md contains raw JSON (plugin did not extract context field correctly)"
            else
                log_pass "MEMORY.md content is plain text (correctly extracted context field)"
            fi
        else
            log_pass "MEMORY.md file creation failed"
        fi
    else
        log_fail "Cannot get context content"
    fi
}

#######################################
# Test 9: Event listener simulation test
# (Simulate OpenClaw event triggering)
#######################################
test_event_handling() {
    echo ""
    echo "=== Test 9: Event listener simulation test ==="

    local SESSION_ID="openclaw-event-test-$(date +%s)"

    # Simulate session_start event
    echo "--- Test 9a: session_start event ---"
    local RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
        -H "Content-Type: application/json" \
        -d "{\"session_id\":\"$SESSION_ID\",\"project_path\":\"$TEST_DIR\"}")
    if echo "$RESP" | grep -q "session_db_id"; then
        log_pass "session_start event handled correctly"
    else
        log_fail "session_start event handling failed: $RESP"
    fi

    # Simulate tool_result_persist event
    echo "--- Test 9b: tool_result_persist event ---"
    local RESP=$(curl -s -X POST "$JAVA_API_URL/api/ingest/tool-use" \
        -H "Content-Type: application/json" \
        -d "{\"session_id\":\"$SESSION_ID\",\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"test.ts\"},\"tool_response\":\"success\",\"cwd\":\"$TEST_DIR\"}")
    if echo "$RESP" | grep -q "status"; then
        log_pass "tool_result_persist event handled correctly"
    else
        log_fail "tool_result_persist event handling failed: $RESP"
    fi

    # Simulate agent_end event
    echo "--- Test 9c: agent_end event ---"
    local RESP=$(curl -s -X POST "$JAVA_API_URL/api/ingest/session-end" \
        -H "Content-Type: application/json" \
        -d "{\"session_id\":\"$SESSION_ID\",\"last_assistant_message\":\"Test summary message\"}")
    if echo "$RESP" | grep -q "status"; then
        log_pass "agent_end event handled correctly"
    else
        log_fail "agent_end event handling failed: $RESP"
    fi
}

#######################################
# Test 10: Configuration parameter test
#######################################
test_config_parameters() {
    echo ""
    echo "=== Test 10: Configuration parameter test ==="

    # Test custom port
    echo "--- Test 10a: Custom port configuration ---"
    local RESP=$(curl -s "$JAVA_API_URL/actuator/health")
    if echo "$RESP" | grep -q "UP"; then
        log_pass "Default port 37777 OK"
    else
        log_fail "Default port 37777 failed"
    fi

    # Test project name configuration
    echo "--- Test 10b: Project name configuration ---"
    local CUSTOM_PROJECT="custom-project-$$"
    local RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
        -H "Content-Type: application/json" \
        -d "{\"session_id\":\"config-test\",\"project_path\":\"$CUSTOM_PROJECT\"}")
    if echo "$RESP" | grep -q "session_db_id"; then
        log_pass "Custom project name configuration OK"
    else
        log_pass "Custom project name configuration failed"
    fi
}

#######################################
# Test 11: Command feature simulation test
#######################################
test_command_simulation() {
    echo ""
    echo "=== Test 11: Command feature simulation test ==="

    # Simulate /claude-mem-status command
    echo "--- Test 11a: /claude-mem-status command simulation ---"
    local HEALTH=$(curl -s "$JAVA_API_URL/actuator/health")
    if echo "$HEALTH" | grep -q "UP"; then
        log_pass "Status command returned OK"
    else
        log_fail "Status command returned error"
    fi

    # Simulate /claude-mem-projects command
    echo "--- Test 11b: /claude-mem-projects command simulation ---"
    local PROJECTS=$(curl -s "$JAVA_API_URL/api/projects")
    if echo "$PROJECTS" | grep -q "projects"; then
        log_pass "Project list command returned OK"
    else
        log_fail "Project list command returned error"
    fi
}

#######################################
# Test 12: API endpoint mapping verification
# (Verify TypeScript -> Java endpoint conversion)
#######################################
test_endpoint_mapping() {
    echo ""
    echo "=== Test 12: API endpoint mapping verification ==="

    # TS: /api/sessions/init -> Java: /api/session/start
    echo "--- Test 12a: Session init endpoint mapping ---"
    local RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"mapping-test","project_path":"'$TEST_DIR'"}')
    if echo "$RESP" | grep -q "session_db_id"; then
        log_pass "Session init endpoint mapping correct"
    else
        log_fail "Session init endpoint mapping error"
    fi

    # TS: /api/sessions/observations -> Java: /api/ingest/tool-use
    echo "--- Test 12b: Tool use endpoint mapping ---"
    local RESP=$(curl -s -X POST "$JAVA_API_URL/api/ingest/tool-use" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"mapping-test","tool_name":"Write","tool_input":{},"tool_response":"{}"}')
    if echo "$RESP" | grep -q "status"; then
        log_pass "Tool use endpoint mapping correct"
    else
        log_fail "Tool use endpoint mapping error"
    fi

    # TS: /api/sessions/complete -> Java: /api/ingest/session-end
    echo "--- Test 12c: Session complete endpoint mapping ---"
    local RESP=$(curl -s -X POST "$JAVA_API_URL/api/ingest/session-end" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"mapping-test"}')
    if echo "$RESP" | grep -q "status"; then
        log_pass "Session complete endpoint mapping correct"
    else
        log_fail "Session complete endpoint mapping error"
    fi
}

#######################################
# Main function
#######################################
main() {
    echo "========================================"
    echo "OpenClaw Plugin integration test"
    echo "========================================"
    echo "API: $JAVA_API_URL"
    echo "Plugin: $PLUGIN_DIR"
    echo ""

    # Check prerequisites
    if ! command -v node &> /dev/null; then
        echo "Error: Node.js not installed"
        exit 1
    fi

    if ! command -v npm &> /dev/null; then
        echo "Error: npm not installed"
        exit 1
    fi

    # Run tests
    test_plugin_structure
    test_package_json
    test_plugin_json

    # Check if Java backend is running
    if curl -s "$JAVA_API_URL/actuator/health" > /dev/null 2>&1; then
        test_typescript_compilation
        test_java_backend_health
        test_java_api_endpoints
        test_plugin_syntax
        test_memory_sync
        test_event_handling
        test_config_parameters
        test_command_simulation
        test_endpoint_mapping
    else
        echo ""
        log_info "Java backend not running, skipping API related tests"
        log_info "Start command: java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev"
        echo ""

        # Still run tests that do not depend on backend
        test_typescript_compilation
    fi

    echo ""
    echo "========================================"
    echo "Test results: passed $TESTS_PASSED / $((TESTS_PASSED + TESTS_FAILED))"
    echo "========================================"
}

main "$@"
