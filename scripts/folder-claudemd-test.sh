#!/bin/bash
#
# Folder CLAUDE.md Update Test Script
# Simulates real PostToolUse hook flow to test --enable-folder-claudemd feature
#

set -e

WRAPPER_DIR="/Users/yangjiefeng/Documents/claude-mem/java/proxy"
WRAPPER="$WRAPPER_DIR/wrapper.js"
JAVA_API_URL="${JAVA_API_URL:-http://127.0.0.1:37777}"
TEST_DIR="/tmp/folder-claudemd-test-$$"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

cleanup() { rm -rf "$TEST_DIR"; }
trap cleanup EXIT
mkdir -p "$TEST_DIR"

echo "========================================"
echo "Folder CLAUDE.md Update Test"
echo "========================================"
echo "Test dir: $TEST_DIR"
echo "API: $JAVA_API_URL"
echo ""

# Check if Java API is available
if ! curl -s "$JAVA_API_URL/actuator/health" > /dev/null 2>&1; then
    echo "ERROR: Java API not running at $JAVA_API_URL"
    echo "Please start the Java backend first"
    exit 1
fi

# Create test directory structure
mkdir -p "$TEST_DIR/src/utils"
mkdir -p "$TEST_DIR/src/components"

# Create initial CLAUDE.md file in subdirectory
cat > "$TEST_DIR/src/utils/CLAUDE.md" << 'EOF'
# Utils

<claude-mem-context>
Old content
</claude-mem-context>
EOF

log_info "Created test directory structure with CLAUDE.md in src/utils/"

# Test 1: Create session and record observation
echo ""
echo "=== Test 1: Create session and record observation ==="
SESSION_ID="folder-test-$(date +%s)"

# Start session
RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
    -H "Content-Type: application/json" \
    -d "{\"session_id\":\"$SESSION_ID\",\"project_path\":\"$TEST_DIR\"}")
echo "Session start response: $RESP"

# Simulate tool use - edit a file in src/utils/
echo ""
echo "=== Test 2: Send PostToolUse for file edit in src/utils/ ==="
INPUT="{\"session_id\":\"$SESSION_ID\",\"cwd\":\"$TEST_DIR\",\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"src/utils/helper.ts\",\"content\":\"export function helper() { return 42; }\"},\"tool_response\":{}}"

log_info "Sending PostToolUse with --enable-folder-claudemd..."

# Use --enable-folder-claudemd to trigger update and wait for completion
echo "$INPUT" | node "$WRAPPER" tool-use --url "$JAVA_API_URL" --enable-folder-claudemd 2>&1

# Wait extra 3 seconds for async folder CLAUDE.md update
log_info "Waiting 5 seconds for async folder CLAUDE.md update..."
sleep 5

# Check if CLAUDE.md was updated
echo ""
echo "=== Test 3: Check if CLAUDE.md was updated ==="
if [ -f "$TEST_DIR/src/utils/CLAUDE.md" ]; then
    log_info "CLAUDE.md exists at $TEST_DIR/src/utils/CLAUDE.md"
    log_info "Content:"
    cat "$TEST_DIR/src/utils/CLAUDE.md"
    
    if grep -q "Recent Activity" "$TEST_DIR/src/utils/CLAUDE.md"; then
        log_pass "CLAUDE.md was updated with recent activity!"
    elif grep -q "helper.ts" "$TEST_DIR/src/utils/CLAUDE.md"; then
        log_pass "CLAUDE.md contains reference to the edited file!"
    else
        log_fail "CLAUDE.md was NOT updated with new content"
    fi
else
    log_fail "CLAUDE.md does not exist!"
fi

echo ""
echo "========================================"
echo "Test Complete"
echo "========================================"
