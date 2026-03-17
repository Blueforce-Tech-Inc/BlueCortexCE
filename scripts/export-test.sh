#!/bin/bash
#
# Claude-Mem Java Export Functionality Test Suite
# End-to-end tests to verify export functionality
#
# Usage: ./export-test.sh [--help]
#
# Prerequisites:
#   - Java backend running on localhost:37777
#   - Some observations/sessions in the database

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_URL="${SERVER_URL:-http://127.0.0.1:37777}"
SKIP_BUILD=false

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

# Check if server is running
check_server() {
    log_info "Checking if server is running..."
    if curl -s "$SERVER_URL/actuator/health" | jq -e '.status == "UP"' > /dev/null 2>&1; then
        log_success "Server is running at $SERVER_URL"
        return 0
    else
        log_fail "Server is not running at $SERVER_URL"
        return 1
    fi
}

# Test 1: Export without project (fetches from all projects)
test_export_no_project() {
    log_info "Test 1: Export without project filter..."
    OUTPUT_FILE="/tmp/test-export-no-project-$$.json"

    # Run export script
    if "$SCRIPT_DIR/export-memories.sh" --limit 10 --output "$OUTPUT_FILE" > /dev/null 2>&1; then
        # Check if output file exists and has valid JSON
        if [ -f "$OUTPUT_FILE" ] && jq empty "$OUTPUT_FILE" 2>/dev/null; then
            # Check required fields
            if jq -e '.observations' "$OUTPUT_FILE" > /dev/null 2>&1 && \
               jq -e '.sessions' "$OUTPUT_FILE" > /dev/null 2>&1 && \
               jq -e '.summaries' "$OUTPUT_FILE" > /dev/null 2>&1 && \
               jq -e '.prompts' "$OUTPUT_FILE" > /dev/null 2>&1; then
                OBS_COUNT=$(jq '.totalObservations' "$OUTPUT_FILE")
                log_success "Export without project works (found $OBS_COUNT observations)"
                rm -f "$OUTPUT_FILE"
                return 0
            fi
        fi
    fi

    log_fail "Export without project failed"
    rm -f "$OUTPUT_FILE"
    return 1
}

# Test 2: Export with project filter
test_export_with_project() {
    log_info "Test 2: Export with project filter..."

    # First, get a valid project from the database
    PROJECT=$(PGPASSWORD=123456 psql -h 127.0.0.1 -U postgres -d claude_mem_dev -t -c "SELECT DISTINCT project_path FROM mem_observations LIMIT 1;" 2>/dev/null | xargs)

    if [ -z "$PROJECT" ]; then
        log_skip "No projects in database, skipping test"
        return 0
    fi

    OUTPUT_FILE="/tmp/test-export-with-project-$$.json"

    # Run export script with project filter and a specific query
    # Use a simple query that should match test data
    if "$SCRIPT_DIR/export-memories.sh" --project "$PROJECT" --query "test" --limit 10 --output "$OUTPUT_FILE" > /dev/null 2>&1; then
        # Check if output file exists and has valid JSON
        if [ -f "$OUTPUT_FILE" ] && jq empty "$OUTPUT_FILE" 2>/dev/null; then
            # Check that the project matches
            EXPORT_PROJECT=$(jq -r '.project' "$OUTPUT_FILE")
            if [ "$EXPORT_PROJECT" = "$PROJECT" ]; then
                OBS_COUNT=$(jq '.totalObservations' "$OUTPUT_FILE")
                log_success "Export with project works (found $OBS_COUNT observations for project: $PROJECT)"
                rm -f "$OUTPUT_FILE"
                return 0
            fi
        fi
    fi

    log_fail "Export with project failed"
    rm -f "$OUTPUT_FILE"
    return 1
}

# Test 3: Batch session API
test_batch_session_api() {
    log_info "Test 3: Batch session API..."

    # Get a session ID from the database
    SESSION_ID=$(PGPASSWORD=123456 psql -h 127.0.0.1 -U postgres -d claude_mem_dev -t -c "SELECT memory_session_id FROM mem_sessions LIMIT 1;" 2>/dev/null | xargs)

    if [ -z "$SESSION_ID" ]; then
        log_skip "No sessions in database, skipping test"
        return 0
    fi

    # Call the batch API
    RESPONSE=$(curl -s -X POST "$SERVER_URL/api/sdk-sessions/batch" \
        -H "Content-Type: application/json" \
        -d "{\"memorySessionIds\": [\"$SESSION_ID\"]}")

    # Check if response is valid JSON array
    if echo "$RESPONSE" | jq -e '. | type == "array"' > /dev/null 2>&1; then
        # Check if session ID matches
        if echo "$RESPONSE" | jq -e ".[0].memory_session_id == \"$SESSION_ID\"" > /dev/null 2>&1; then
            log_success "Batch session API works"
            return 0
        fi
    fi

    log_fail "Batch session API failed"
    return 1
}

# Test 4: Export output format
test_export_format() {
    log_info "Test 4: Export output format..."

    OUTPUT_FILE="/tmp/test-export-format-$$.json"

    # Run export script
    if "$SCRIPT_DIR/export-memories.sh" --limit 5 --output "$OUTPUT_FILE" > /dev/null 2>&1; then
        # Check output format
        if jq -e '.exportedAt | length > 0' "$OUTPUT_FILE" > /dev/null 2>&1 && \
           jq -e '.exportedAtEpoch > 0' "$OUTPUT_FILE" > /dev/null 2>&1 && \
           jq -e '.query | type == "string"' "$OUTPUT_FILE" > /dev/null 2>&1 && \
           jq -e '.totalObservations >= 0' "$OUTPUT_FILE" > /dev/null 2>&1 && \
           jq -e '.totalSessions >= 0' "$OUTPUT_FILE" > /dev/null 2>&1 && \
           jq -e '.totalSummaries >= 0' "$OUTPUT_FILE" > /dev/null 2>&1 && \
           jq -e '.totalPrompts >= 0' "$OUTPUT_FILE" > /dev/null 2>&1; then
            log_success "Export output format is correct"
            rm -f "$OUTPUT_FILE"
            return 0
        fi
    fi

    log_fail "Export output format is incorrect"
    rm -f "$OUTPUT_FILE"
    return 1
}

# Main
main() {
    echo "=============================================="
    echo " Claude-Mem Java Export Test Suite"
    echo "=============================================="
    echo ""
    echo "Server URL: $SERVER_URL"
    echo ""

    # Check server
    if ! check_server; then
        echo ""
        echo "Please start the Java backend and run this test again."
        exit 1
    fi

    echo ""
    echo "Running export tests..."
    echo "=========================================="

    # Run tests
    test_export_no_project
    test_export_with_project
    test_batch_session_api
    test_export_format

    echo ""
    echo "=========================================="
    echo "Test Summary"
    echo "=========================================="
    echo -e "${GREEN}Passed:${NC}  $TESTS_PASSED"
    echo -e "${RED}Failed:${NC}  $TESTS_FAILED"
    echo -e "${YELLOW}Skipped:${NC} $TESTS_SKIPPED"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}Some tests failed!${NC}"
        exit 1
    fi
}

main "$@"
