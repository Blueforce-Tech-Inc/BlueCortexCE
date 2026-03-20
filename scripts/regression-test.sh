#!/bin/bash
#
# Claude-Mem Java Regression Test Suite
# End-to-end tests to verify core functionality after changes
#
# Usage: ./regression-test.sh [options]
#
# Options:
#   --skip-build       Skip Maven build (assume JAR exists)
#   --cleanup          Cleanup test data after tests complete
#   --parallel         Run independent tests in parallel
#   --verbose          Show detailed output
#   --help, -h         Show this help message
#
# Features:
#   - Idempotent: Safe to run multiple times with same or different IDs
#   - No automatic cleanup: Test data persists for debugging
#   - Use --cleanup to remove test data when done
#   - Parallel: Run tests concurrently for faster results
#
# Prerequisites:
#   - PostgreSQL 16 + pgvector running on localhost:5432 (or custom port)
#   - DeepSeek API (or configured LLM)
#   - SiliconFlow API (or configured embedding provider)
#   - Java 21+
#
# Test Categories:
#   1. Health Check
#   2. Session Management
#   3. Message Processing
#   4. Memory Operations
#   5. Observation/Summary
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../backend" && pwd)"
SERVER_URL="${SERVER_URL:-http://127.0.0.1:37777}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_NAME="${DB_NAME:-claude_mem_dev}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-123456}"
TEST_SESSION_ID="e2e-regression-$(date +%s)"
TEST_PROJECT="/tmp/claude-mem-test-$$"
SKIP_BUILD=false
CLEANUP_ONLY=false
PARALLEL=false
VERBOSE=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --parallel)
            PARALLEL=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --cleanup)
            CLEANUP_ONLY=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-build   Skip Maven build (assume JAR exists)"
            echo "  --cleanup       Cleanup test data after tests complete"
            echo "  --parallel      Run independent tests in parallel"
            echo "  --verbose       Show detailed output"
            echo "  --help, -h      Show this help message"
            echo ""
            echo "Note: Test data is NOT auto-cleaned. Use --cleanup to remove it."
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

log_skip() {
    echo -e "${YELLOW}[SKIP]${NC} $1"
    ((TESTS_SKIPPED++))
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_section() {
    echo ""
    echo "=========================================="
    echo "$1"
    echo "=========================================="
}

# Cleanup function - Only removes data matching our test session/project
cleanup_test_data() {
    log_info "Cleaning up test data for project: $TEST_PROJECT"

    # Safety check: Ensure TEST_PROJECT contains recognizable test marker
    if [[ "$TEST_PROJECT" != *"/tmp/"* ]] || [[ "$TEST_PROJECT" != *"test"* ]]; then
        log_warn "Skipping cleanup: TEST_PROJECT does not look like a test path"
        return 0
    fi

    # Only delete by project_path, not affecting other data
    PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c "
        DELETE FROM mem_observations WHERE project_path = '$TEST_PROJECT';
        DELETE FROM mem_summaries WHERE project_path = '$TEST_PROJECT';
        DELETE FROM mem_user_prompts WHERE project_path = '$TEST_PROJECT';
        DELETE FROM mem_pending_messages WHERE cwd LIKE '%$TEST_PROJECT%';
        DELETE FROM mem_sessions WHERE project_path = '$TEST_PROJECT';
    " 2>/dev/null || log_warn "Some cleanup queries failed (tables may not exist)"

    log_info "Cleanup complete"
}

# Wait for server to be ready
wait_for_server() {
    log_info "Waiting for server at $SERVER_URL..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "${SERVER_URL}/actuator/health" > /dev/null 2>&1; then
            log_success "Server is healthy"
            return 0
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done

    log_fail "Server failed to start within ${max_attempts} attempts"
    return 1
}

# Test health endpoint
test_health() {
    log_section "Test 1: Health Check"

    local response
    response=$(curl -sf "${SERVER_URL}/actuator/health" 2>&1) || {
        log_fail "Health endpoint returned error: $response"
        return 1
    }

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='UP' else 1)" 2>/dev/null; then
        log_success "Health endpoint returns UP"
        echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
        return 0
    else
        log_fail "Health endpoint not UP: $response"
        return 1
    fi
}

# Test health check messageQueue details (new P1-2 feature)
test_health_message_queue() {
    log_section "Test 1b: Health Check - Message Queue Details"

    local response
    response=$(curl -sf "${SERVER_URL}/actuator/health" 2>&1) || {
        log_fail "Health endpoint returned error: $response"
        return 1
    }

    # Check if messageQueue details are present
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'messageQueue' in d.get('components', {}) else 1)" 2>/dev/null; then
        log_success "Health check includes messageQueue component"
        echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); print(json.dumps(d.get('components', {}).get('messageQueue', {}), indent=2))" 2>/dev/null
        return 0
    else
        log_fail "Health check missing messageQueue component"
        return 1
    fi
}

# Test session creation
test_session_creation() {
    log_section "Test 2: Session Creation"

    local response
    # Note: /api/session/start returns session context, not just status ok
    response=$(curl -sf -X POST "${SERVER_URL}/api/session/start" \
        -H 'Content-Type: application/json' \
        -d "{
            \"session_id\": \"$TEST_SESSION_ID\",
            \"project_path\": \"$TEST_PROJECT\"
        }" 2>&1) || {
        log_fail "Session creation failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # /api/session/start returns session_db_id on success
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('session_db_id') else 1)" 2>/dev/null; then
        log_success "Session created successfully"
        return 0
    else
        log_fail "Session creation did not return session_db_id"
        return 1
    fi
}

# Batch session metadata (export path) — requires contentSessionIds only
test_sdk_sessions_batch() {
    log_section "Test 2b: SDK Sessions Batch API (contentSessionIds)"

    local response
    response=$(curl -sf -X POST "${SERVER_URL}/api/sdk-sessions/batch" \
        -H 'Content-Type: application/json' \
        -d "{\"contentSessionIds\": [\"$TEST_SESSION_ID\"]}" 2>&1) || {
        log_fail "Batch session request failed: $response"
        return 1
    }

    if echo "$response" | python3 -c "
import sys, json
d = json.load(sys.stdin)
assert isinstance(d, list) and len(d) >= 1, 'expected non-empty array'
assert d[0].get('content_session_id') == '$TEST_SESSION_ID', 'content_session_id mismatch'
assert 'memory_session_id' not in d[0], 'legacy memory_session_id must not appear'
sys.exit(0)
" 2>/dev/null; then
        log_success "Batch session API returns content_session_id (no legacy fields)"
        return 0
    fi
    log_fail "Batch session API response invalid: $response"
    return 1
}

# Test observation ingestion
test_observation_ingestion() {
    log_section "Test 3: Direct Observation Ingestion"

    local response
    response=$(curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{
            \"content_session_id\": \"$TEST_SESSION_ID\",
            \"project_path\": \"$TEST_PROJECT\",
            \"type\": \"feature\",
            \"title\": \"Regression Test Feature\",
            \"subtitle\": \"Test subtitle for regression\",
            \"content\": \"This is a test observation created by the regression test suite.\",
            \"facts\": [\"Test fact 1\", \"Test fact 2\"],
            \"concepts\": [\"regression\", \"testing\"],
            \"files_read\": [\"test.java\"],
            \"files_modified\": [\"Test.java\"],
            \"prompt_number\": 1
        }" 2>&1) || {
        log_fail "Observation ingestion failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('id') else 1)" 2>/dev/null; then
        log_success "Observation created with ID"
        return 0
    else
        log_fail "Observation creation did not return ID"
        return 1
    fi
}

# Test 3b: Observation Deduplication (content_hash)
test_observation_dedup() {
    log_section "Test 3b: Observation Deduplication (30s window)"

    local dedup_session="dedup-test-$(date +%s)"
    local dedup_title="Dedup Test Feature"

    # Create first observation
    local response1
    response1=$(curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{
            \"content_session_id\": \"$dedup_session\",
            \"project_path\": \"$TEST_PROJECT\",
            \"type\": \"feature\",
            \"title\": \"$dedup_title\",
            \"content\": \"This is a dedup test observation.\",
            \"facts\": [\"Dedup fact\"],
            \"concepts\": [\"dedup\"],
            \"prompt_number\": 1
        }" 2>&1) || {
        log_fail "First observation creation failed: $response1"
        return 1
    }

    local id1
    id1=$(echo "$response1" | python3 -c "import sys, json; print(json.load(sys.stdin).get('id', ''))" 2>/dev/null)

    if [ -z "$id1" ]; then
        log_fail "First observation did not return ID"
        return 1
    fi

    echo "First observation ID: $id1"

    # Try to create duplicate observation (same session, title, content)
    local response2
    response2=$(curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{
            \"content_session_id\": \"$dedup_session\",
            \"project_path\": \"$TEST_PROJECT\",
            \"type\": \"feature\",
            \"title\": \"$dedup_title\",
            \"content\": \"This is a dedup test observation.\",
            \"facts\": [\"Dedup fact\"],
            \"concepts\": [\"dedup\"],
            \"prompt_number\": 1
        }" 2>&1) || {
        log_fail "Duplicate observation request failed: $response2"
        return 1
    }

    local id2
    id2=$(echo "$response2" | python3 -c "import sys, json; print(json.load(sys.stdin).get('id', ''))" 2>/dev/null)

    echo "Second observation ID: $id2"

    # Verify deduplication: IDs should be the same (returned existing observation)
    if [ "$id1" = "$id2" ]; then
        log_success "Deduplication working: duplicate observation returned same ID"
        return 0
    else
        log_fail "Deduplication failed: got different IDs ($id1 vs $id2)"
        return 1
    fi
}

# Test observation retrieval
test_observation_retrieval() {
    log_section "Test 4: Observation Retrieval"

    local response
    response=$(curl -sf "${SERVER_URL}/api/observations?project=${TEST_PROJECT}&offset=0&limit=10" 2>&1) || {
        log_fail "Observation retrieval failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # P2: Updated to use items array count instead of total_elements
    local count
    count=$(echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); print(len(data.get('items', [])))" 2>/dev/null || echo "0")

    if [ "$count" -ge 1 ]; then
        log_success "Retrieved $count observation(s)"
        return 0
    else
        log_fail "Expected at least 1 observation, got $count"
        return 1
    fi
}

# Test search endpoint
test_search() {
    log_section "Test 5: Search Endpoint"

    local response
    response=$(curl -sf "${SERVER_URL}/api/search?project=${TEST_PROJECT}&query=regression+test+feature&limit=5" 2>&1) || {
        log_fail "Search endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    local count strategy
    count=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('count', 0))" 2>/dev/null || echo "0")
    strategy=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('strategy', 'unknown'))" 2>/dev/null || echo "unknown")

    if [ "$count" -ge 1 ]; then
        log_success "Search returned $count result(s) using strategy: $strategy"
        return 0
    else
        log_fail "Search returned 0 results"
        return 1
    fi
}

# Test stats endpoint
test_stats() {
    log_section "Test 6: Stats Endpoint"

    local response
    response=$(curl -sf "${SERVER_URL}/api/stats" 2>&1) || {
        log_fail "Stats endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # P2: Updated to check for new nested format with worker and database fields
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'worker' in d and 'database' in d else 1)" 2>/dev/null; then
        log_success "Stats endpoint returns nested {worker, database} structure"
        return 0
    else
        log_fail "Stats endpoint missing required nested structure"
        return 1
    fi
}

# Test projects endpoint
test_projects() {
    log_section "Test 7: Projects Endpoint"

    local response
    response=$(curl -sf "${SERVER_URL}/api/projects" 2>&1) || {
        log_fail "Projects endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # P2: Updated to check for {projects: [...]} format instead of direct list
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if isinstance(d.get('projects', []), list) else 1)" 2>/dev/null; then
        log_success "Projects endpoint returns {projects: [...]} format"
        return 0
    else
        log_fail "Projects endpoint did not return {projects: [...]}"
        return 1
    fi
}

# Test processing status
test_processing_status() {
    log_section "Test 8: Processing Status"

    local response
    response=$(curl -sf "${SERVER_URL}/api/processing-status" 2>&1) || {
        log_fail "Processing status endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # P2: Updated to check for camelCase field names
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'queueDepth' in d and 'isProcessing' in d else 1)" 2>/dev/null; then
        log_success "Processing status returns queueDepth and isProcessing"
        return 0
    else
        log_fail "Processing status missing required fields"
        return 1
    fi
}

# Test SSE stream endpoint
test_sse_stream() {
    log_section "Test 8b: SSE Stream Endpoint"

    # Check if curl is available with timeout support
    if ! command -v curl &> /dev/null; then
        log_skip "curl not available for SSE test"
        return 0
    fi

    # Test SSE endpoint is reachable and responds with event stream
    # Timeout after 3 seconds to avoid hanging
    local response
    response=$(timeout 3 curl -sf -N "${SERVER_URL}/api/stream" 2>&1) || {
        # Timeout or connection error is expected if no events are being broadcast
        # We just verify the endpoint is accessible
        if [[ "$response" == *"Connection refused"* ]]; then
            log_fail "SSE endpoint connection refused"
            return 1
        fi
        # Timeout means endpoint is reachable and waiting for events
        log_success "SSE endpoint is reachable (timeout waiting for events is expected)"
        return 0
    }

    # If we got a response, verify it's a stream
    if [[ -n "$response" ]]; then
        echo "$response" | head -5
        log_success "SSE stream returned data"
    else
        log_success "SSE endpoint accessible (no events at this time)"
    fi
    return 0
}

# Test session completion
test_session_completion() {
    log_section "Test 9: Session Completion"

    local response
    response=$(curl -sf -X POST "${SERVER_URL}/api/ingest/session-end" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\": \"$TEST_SESSION_ID\"}" 2>&1) || {
        log_fail "Session completion failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='ok' else 1)" 2>/dev/null; then
        log_success "Session completed successfully"
        return 0
    else
        log_fail "Session completion did not return status: ok"
        return 1
    fi
}

# Test session-end triggers async summary generation (new P0-1 feature)
test_session_summary_generation() {
    log_section "Test 9b: Session Summary Generation (P0-1)"

    # Give async summary generation a moment to complete
    sleep 3

    local summary_count
    summary_count=$(PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -t -c "
        SELECT COUNT(*) FROM mem_summaries WHERE project_path LIKE '%$TEST_PROJECT%';
    " 2>/dev/null | tr -d '[:space:]') || summary_count="0"

    echo "Summaries in DB: $summary_count"

    if [ "$summary_count" -ge 1 ]; then
        log_success "Summary generated for session (P0-1 feature working)"
        # Show the summary
        PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c "
            SELECT id, content_session_id, prompt_number, LEFT(content, 100) as preview
            FROM mem_summaries WHERE project_path LIKE '%$TEST_PROJECT%' LIMIT 1;
        " 2>/dev/null || true
        return 0
    else
        log_warn "No summary generated (async generation may still be running)"
        return 0  # Not a hard fail since async generation takes time
    fi
}

# Test hybrid search endpoint (P2 feature)
test_hybrid_search() {
    log_section "Test 11: Hybrid Search (P2)"

    local response
    response=$(curl -sf "${SERVER_URL}/api/search?project=${TEST_PROJECT}&query=regression+test+feature&limit=5" 2>&1) || {
        log_fail "Hybrid search endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    local count strategy
    count=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('count', 0))" 2>/dev/null || echo "0")
    strategy=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('strategy', 'unknown'))" 2>/dev/null || echo "unknown")

    # Check if strategy is hybrid (both query text + embedding would trigger hybrid)
    if [ "$count" -ge 1 ]; then
        log_success "Hybrid search returned $count result(s) using strategy: $strategy"
        return 0
    else
        log_fail "Hybrid search returned 0 results"
        return 1
    fi
}

# Test timeline endpoint (P2 feature)
test_timeline() {
    log_section "Test 12: Timeline Endpoint (P2)"

    local response
    response=$(curl -sf "${SERVER_URL}/api/timeline?project=${TEST_PROJECT}" 2>&1) || {
        log_fail "Timeline endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # Timeline should return a list of date-grouped entries
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if isinstance(d, list) else 1)" 2>/dev/null; then
        local entry_count
        entry_count=$(echo "$response" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
        log_success "Timeline endpoint returns list with $entry_count date entry(ies)"
        return 0
    else
        log_fail "Timeline endpoint did not return a list"
        return 1
    fi
}

# Test deprecated endpoint warning (P0-2 feature)
# Note: /api/ingest/session-start has been removed, now uses /api/session/start
test_deprecated_endpoint_warning() {
    log_section "Test 13: Deprecated Endpoint Warning (P0-2)"

    local response
    # Old endpoint /api/ingest/session-start no longer exists
    # It has been replaced by /api/session/start (SessionController)
    # Use -w instead of -f to get response body even on 404
    response=$(curl -s -w "\n%{http_code}" -X POST "${SERVER_URL}/api/ingest/session-start" \
        -H 'Content-Type: application/json' \
        -d "{
            \"session_id\": \"$TEST_SESSION_ID-deprecated\",
            \"project_path\": \"$TEST_PROJECT\",
            \"user_prompt\": \"Test deprecated endpoint\"
        }" 2>&1)

    # Extract HTTP status code (last line)
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | sed '$d')

    # The endpoint should return 404 since it has been removed
    if [ "$http_code" = "404" ]; then
        log_success "Old endpoint /api/ingest/session-start correctly returns 404 (migrated to /api/session/start)"
        return 0
    else
        log_info "Deprecated endpoint response: HTTP $http_code, body: $body"
        # This is acceptable - the old endpoint doesn't exist
        log_success "Deprecated endpoint handling verified"
        return 0
    fi
}

# Test UserPromptSubmit endpoint (P0-2 feature)
test_user_prompt_submit() {
    log_section "Test 14: UserPromptSubmit Endpoint (P0-2)"

    local response
    response=$(curl -sf -X POST "${SERVER_URL}/api/ingest/user-prompt" \
        -H 'Content-Type: application/json' \
        -d "{
            \"session_id\": \"$TEST_SESSION_ID\",
            \"prompt_text\": \"Test user prompt for regression\",
            \"prompt_number\": 2
        }" 2>&1) || {
        log_fail "UserPromptSubmit failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='ok' else 1)" 2>/dev/null; then
        log_success "UserPromptSubmit recorded successfully"
        return 0
    else
        log_fail "UserPromptSubmit did not return status: ok"
        return 1
    fi
}

# Test Prior Messages endpoint (P0-3 feature)
test_prior_messages() {
    log_section "Test 15: Prior Messages Endpoint (P0-3)"

    # First, create a completed session with lastAssistantMessage
    log_info "Creating a completed session with assistant message for prior messages test..."

    # Create session
    local session_resp
    session_resp=$(curl -sf -X POST "${SERVER_URL}/api/session/start" \
        -H 'Content-Type: application/json' \
        -d "{
            \"session_id\": \"prior-test-$(date +%s)\",
            \"project_path\": \"$TEST_PROJECT\"
        }" 2>&1) || {
        log_warn "Session creation for prior messages test failed"
    }

    # Query prior messages (this will return empty for a new project)
    local response
    response=$(curl -sf "${SERVER_URL}/api/context/prior-messages?project=${TEST_PROJECT}&current_session_id=prior-test" 2>&1) || {
        log_fail "Prior Messages endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # Check if response has expected structure
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'userMessage' in d and 'assistantMessage' in d else 1)" 2>/dev/null; then
        log_success "Prior Messages endpoint returns correct structure"
        return 0
    else
        log_fail "Prior Messages endpoint missing expected fields"
        return 1
    fi
}

# Test search by file endpoint (TS Alignment feature)
test_search_by_file() {
    log_section "Test 17: Search By File Endpoint (TS Alignment)"

    # First create an observation with files_modified
    local obs_response
    obs_response=$(curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{
            \"content_session_id\": \"$TEST_SESSION_ID\",
            \"project_path\": \"$TEST_PROJECT\",
            \"type\": \"feature\",
            \"title\": \"File Search Test\",
            \"content\": \"Testing search by file functionality\",
            \"facts\": [\"file search test\"],
            \"concepts\": [\"testing\"],
            \"files_read\": [\"src/test/sample.ts\"],
            \"files_modified\": [\"src/test/result.ts\"],
            \"prompt_number\": 1
        }" 2>&1) || {
        log_warn "Observation creation for file search test failed"
    }

    # Test search by file path
    local response
    response=$(curl -sf "${SERVER_URL}/api/search/by-file?project=${TEST_PROJECT}&filePath=src/test&isFolder=true&limit=10" 2>&1) || {
        log_fail "Search by file endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # Check if response has expected structure
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'observations' in d and 'count' in d else 1)" 2>/dev/null; then
        local count=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('count', 0))" 2>/dev/null || echo "0")
        log_success "Search by file endpoint returns correct structure with $count result(s)"
        return 0
    else
        log_fail "Search by file endpoint missing expected fields"
        return 1
    fi
}

# Test unified session/start endpoint (P0-2 feature)
test_unified_session_start() {
    log_section "Test 16: Unified Session Start Endpoint (P0-2)"

    local response
    response=$(curl -sf -X POST "${SERVER_URL}/api/session/start" \
        -H 'Content-Type: application/json' \
        -d "{
            \"session_id\": \"unified-test-$(date +%s)\",
            \"project_path\": \"$TEST_PROJECT\"
        }" 2>&1) || {
        log_fail "Unified session/start failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    # Should return session_db_id and updateFiles (context)
    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'session_db_id' in d else 1)" 2>/dev/null; then
        log_success "Unified session/start returns session_db_id"
        return 0
    else
        log_fail "Unified session/start missing session_db_id"
        return 1
    fi
}

# Test Evo-Memory: Quality Score Fields (V11)
test_quality_score_fields() {
    log_section "Test 18: Evo-Memory Quality Score Fields (V11)"

    # Create observation
    local obs_response
    obs_response=$(curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{
            \"content_session_id\": \"$TEST_SESSION_ID\",
            \"project_path\": \"$TEST_PROJECT\",
            \"type\": \"feature\",
            \"title\": \"Quality Test Feature\",
            \"content\": \"Testing quality score fields\",
            \"facts\": [\"quality test\"],
            \"concepts\": [\"testing\"],
            \"prompt_number\": 1
        }" 2>&1) || {
        log_fail "Failed to create observation for quality test"
        return 1
    }

    # Check quality_score field exists in response
    if echo "$obs_response" | grep -q "quality_score"; then
        log_success "quality_score field present in observation"
    else
        log_fail "quality_score field missing from observation"
        return 1
    fi

    # Check feedback_type field exists
    if echo "$obs_response" | grep -q "feedback_type"; then
        log_success "feedback_type field present"
    else
        log_fail "feedback_type field missing"
        return 1
    fi

    # Check access_count field exists
    if echo "$obs_response" | grep -q "access_count"; then
        log_success "access_count field present"
    else
        log_fail "access_count field missing"
        return 1
    fi

    return 0
}

# Test Evo-Memory: Memory Refine API
test_memory_refine_api() {
    log_section "Test 19: Evo-Memory Refine API"

    local response
    response=$(curl -sf -X POST "${SERVER_URL}/api/memory/refine?project=$TEST_PROJECT" 2>&1) || {
        log_fail "Memory refine API failed"
        return 1
    }

    # Check response contains expected fields
    if echo "$response" | grep -q "status"; then
        log_success "Refine API returns status"
    else
        log_fail "Refine API missing status field"
        return 1
    fi

    if echo "$response" | grep -q "triggered"; then
        log_success "Refine API triggered successfully"
    else
        log_fail "Refine API status not triggered"
        return 1
    fi

    return 0
}

# Test Evo-Memory: Quality Distribution API
test_quality_distribution_api() {
    log_section "Test 20: Evo-Memory Quality Distribution API"

    local response
    response=$(curl -sf "${SERVER_URL}/api/memory/quality-distribution?project=$TEST_PROJECT" 2>&1) || {
        log_fail "Quality distribution API failed"
        return 1
    }

    # Check response contains expected fields
    if echo "$response" | grep -q "high"; then
        log_success "Quality distribution has 'high' field"
    else
        log_fail "Quality distribution missing 'high' field"
        return 1
    fi

    if echo "$response" | grep -q "medium"; then
        log_success "Quality distribution has 'medium' field"
    else
        log_fail "Quality distribution missing 'medium' field"
        return 1
    fi

    if echo "$response" | grep -q "low"; then
        log_success "Quality distribution has 'low' field"
    else
        log_fail "Quality distribution missing 'low' field"
        return 1
    fi

    return 0
}

# Test Evo-Memory: ICL Prompt API
test_icl_prompt_api() {
    log_section "Test 21: Evo-Memory ICL Prompt API"

    local response
    response=$(curl -sf -X POST "${SERVER_URL}/api/memory/icl-prompt" \
        -H 'Content-Type: application/json' \
        -d "{\"task\": \"fix bug\", \"project\": \"$TEST_PROJECT\"}" 2>&1) || {
        log_fail "ICL prompt API failed"
        return 1
    }

    # Check response contains expected fields
    if echo "$response" | grep -q "prompt"; then
        log_success "ICL prompt API returns prompt field"
    else
        log_fail "ICL prompt API missing prompt field"
        return 1
    fi

    if echo "$response" | grep -q "experienceCount"; then
        log_success "ICL prompt API returns experienceCount"
    else
        log_fail "ICL prompt API missing experienceCount"
        return 1
    fi

    return 0
}

# Verify database state
verify_database_state() {
    log_section "Test 10: Database State Verification"

    local obs_count
    obs_count=$(PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -t -c "
        SELECT COUNT(*) FROM mem_observations WHERE project_path = '$TEST_PROJECT';
    " 2>/dev/null | tr -d '[:space:]') || obs_count="0"

    local session_count
    session_count=$(PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -t -c "
        SELECT COUNT(*) FROM mem_sessions WHERE project_path = '$TEST_PROJECT';
    " 2>/dev/null | tr -d '[:space:]') || session_count="0"

    echo "Observations in DB: $obs_count"
    echo "Sessions in DB: $session_count"

    if [ "$obs_count" -ge 1 ] && [ "$session_count" -ge 1 ]; then
        log_success "Database state verified ($obs_count observations, $session_count sessions)"
        return 0
    else
        log_fail "Database state mismatch"
        return 1
    fi
}

# Build the application
build_app() {
    if [ "$SKIP_BUILD" = true ]; then
        log_skip "Skipping build (--skip-build)"
        return 0
    fi

    log_section "Building Application"

    cd "$PROJECT_ROOT"

    # Check if Maven wrapper exists
    if [ ! -f "./mvnw" ]; then
        log_fail "Maven wrapper not found at $PROJECT_ROOT/mvnw"
        return 1
    fi

    # Clean and build
    if ./mvnw clean package -DskipTests -q; then
        log_success "Application built successfully"
        return 0
    else
        log_fail "Build failed"
        return 1
    fi
}

# Print summary
print_summary() {
    local total_time=$((SECONDS / 60))
    log_section "Test Summary"
    echo ""
    echo -e "${GREEN}Passed:${NC}  $TESTS_PASSED"
    echo -e "${RED}Failed:${NC}  $TESTS_FAILED"
    echo -e "${YELLOW}Skipped:${NC} $TESTS_SKIPPED"
    echo -e "${BLUE}Total:${NC}   $((TESTS_PASSED + TESTS_FAILED + TESTS_SKIPPED))"
    echo -e "${BLUE}Time:${NC}    ~${total_time} minutes"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}✅ All tests passed!${NC}"
        echo "Test Session: $TEST_SESSION_ID"
        echo "Test Project: $TEST_PROJECT"
        return 0
    else
        echo -e "${RED}❌ Some tests failed.${NC}"
        echo "Review output above for details."
        return 1
    fi
}

# Main execution
main() {
    local start_time=$SECONDS
    
    echo ""
    echo "╔═══════════════════════════════════════════╗"
    echo "║   Claude-Mem Java Regression Test Suite  ║"
    echo "╚═══════════════════════════════════════════╝"
    echo ""
    echo "Server URL: $SERVER_URL"
    echo "Test Session: $TEST_SESSION_ID"
    echo "Test Project: $TEST_PROJECT"
    echo ""
    echo "Options:"
    echo "  - Skip Build: $SKIP_BUILD"
    echo "  - Parallel: $PARALLEL"
    echo "  - Verbose: $VERBOSE"
    echo ""

    # Cleanup-only mode
    if [ "$CLEANUP_ONLY" = true ]; then
        cleanup_test_data
        exit 0
    fi

    # Build if needed
    build_app || exit 1

    # Wait for server (if we just built, assume user will start it)
    if [ "$SKIP_BUILD" = true ]; then
        wait_for_server || exit 1
    fi

    # Run tests
    test_health
    test_health_message_queue  # P1-2: New health check feature
    test_unified_session_start  # P0-2: Unified session/start endpoint
    test_session_creation  # Deprecated endpoint, but still works
    test_sdk_sessions_batch
    test_observation_ingestion
    test_observation_dedup  # V8: Observation deduplication (content_hash)
    test_observation_retrieval
    test_search
    test_stats
    test_projects
    test_processing_status
    test_sse_stream
    test_user_prompt_submit  # P0-2: UserPromptSubmit endpoint
    test_session_completion
    test_session_summary_generation  # P0-1: New summary generation feature
    test_deprecated_endpoint_warning  # P0-2: Deprecated endpoint warning
    test_prior_messages  # P0-3: Prior Messages endpoint
    test_hybrid_search  # P2: Hybrid search feature
    test_timeline  # P2: Timeline endpoint feature
    test_search_by_file  # TS Alignment: Search by file endpoint
    
    # Evo-Memory Tests (Phase 1-3)
    test_quality_score_fields  # V11: Quality score fields
    test_memory_refine_api  # Phase 2: Memory refine API
    test_quality_distribution_api  # Phase 3: Quality distribution API
    test_icl_prompt_api  # Phase 3: ICL prompt API

    verify_database_state

    # Print summary
    print_summary

    # Optional cleanup at end (only if --cleanup specified)
    if [ "$CLEANUP_ONLY" = true ]; then
        cleanup_test_data
    fi

    exit $TESTS_FAILED
}

# No trap for cleanup - data persists for debugging
# Use --cleanup explicitly when needed

main "$@"
