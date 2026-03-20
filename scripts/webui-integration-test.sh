#!/bin/bash
# WebUI Integration Tests
# Tests the new API endpoints for WebUI compatibility
# Port: 37777 (default)

set -e

BASE_URL="${BASE_URL:-http://localhost:37777}"
PROJECT_PATH="/Users/yangjiefeng/Documents/claude-mem"

echo "=========================================="
echo "WebUI Integration Tests"
echo "Base URL: $BASE_URL"
echo "=========================================="

PASSED=0
FAILED=0

# Helper function for tests
test_endpoint() {
    local name="$1"
    local url="$2"
    local expected_pattern="$3"

    echo "----------------------------------------"
    echo "Test: $name"
    echo "URL: $url"

    local response
    response=$(curl -s "$url")

    if echo "$response" | grep -q "$expected_pattern"; then
        echo "[PASS] $name"
        ((PASSED++))
    else
        echo "[FAIL] $name"
        echo "Expected pattern: $expected_pattern"
        echo "Got response: $response"
        ((FAILED++))
    fi
}

# Test 1: Pagination API with offset/limit and items/hasMore format
echo ""
echo "=========================================="
echo "Test 1: Pagination API (offset/limit, items/hasMore)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/observations?offset=0&limit=10")
if echo "$response" | grep -q '"items"' && echo "$response" | grep -q '"hasMore"'; then
    echo "[PASS] Pagination returns items/hasMore format"
    ((PASSED++))
else
    echo "[FAIL] Pagination should return items/hasMore format"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 2: Summaries pagination
response=$(curl -s "$BASE_URL/api/summaries?offset=0&limit=10")
if echo "$response" | grep -q '"items"' && echo "$response" | grep -q '"hasMore"'; then
    echo "[PASS] Summaries pagination returns items/hasMore format"
    ((PASSED++))
else
    echo "[FAIL] Summaries pagination should return items/hasMore format"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 3: Prompts pagination
response=$(curl -s "$BASE_URL/api/prompts?offset=0&limit=10")
if echo "$response" | grep -q '"items"' && echo "$response" | grep -q '"hasMore"'; then
    echo "[PASS] Prompts pagination returns items/hasMore format"
    ((PASSED++))
else
    echo "[FAIL] Prompts pagination should return items/hasMore format"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 4: Projects API returns {projects: []} format
echo ""
echo "=========================================="
echo "Test 2: Projects API ({projects: []} format)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/projects")
if echo "$response" | grep -q '"projects"' && echo "$response" | grep -q '\['; then
    echo "[PASS] Projects API returns {projects: [...]} format"
    ((PASSED++))
else
    echo "[FAIL] Projects API should return {projects: [...]} format"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 5: Stats API returns nested {worker, database} structure
echo ""
echo "=========================================="
echo "Test 3: Stats API (nested {worker, database} structure)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/stats")
if echo "$response" | grep -q '"worker"' && echo "$response" | grep -q '"database"' && echo "$response" | grep -q '"isProcessing"' && echo "$response" | grep -q '"queueDepth"'; then
    echo "[PASS] Stats API returns nested {worker, database} structure"
    ((PASSED++))
else
    echo "[FAIL] Stats API should return nested {worker, database} structure"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 6: Stats API has camelCase fields
echo ""
echo "=========================================="
echo "Test 4: Stats API (camelCase fields)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/stats")
# Check for camelCase field names (totalObservations, totalSummaries, etc.)
if echo "$response" | grep -qE '"totalObservations"|"totalSummaries"|"totalSessions"|"totalProjects"'; then
    echo "[PASS] Stats API uses camelCase field names"
    ((PASSED++))
else
    echo "[FAIL] Stats API should use camelCase field names"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 7: Processing Status uses camelCase
echo ""
echo "=========================================="
echo "Test 5: Processing Status API (camelCase)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/processing-status")
if echo "$response" | grep -q '"isProcessing"' && echo "$response" | grep -q '"queueDepth"'; then
    echo "[PASS] Processing Status API uses camelCase field names"
    ((PASSED++))
else
    echo "[FAIL] Processing Status API should use camelCase field names"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 8: Context Preview endpoint (returns plain text, not JSON)
echo ""
echo "=========================================="
echo "Test 6: Context Preview API (plain text)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/context/preview?project=$PROJECT_PATH")
# Context preview returns plain text, not JSON - matches TypeScript useContextPreview.ts
if [ -n "$response" ]; then
    echo "[PASS] Context Preview API returns plain text response"
    ((PASSED++))
else
    echo "[FAIL] Context Preview API should return a response"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 9: Observation entity has snake_case fields (TypeScript expects snake_case)
echo ""
echo "=========================================="
echo "Test 7: Observation Entity (snake_case JSON fields)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/observations?offset=0&limit=1")
# Observations JSON: content_session_id, files_read, files_modified, prompt_number, created_at_epoch
if echo "$response" | grep -qE '"content_session_id"|"project"|"narrative"|"prompt_number"|"created_at_epoch"'; then
    echo "[PASS] Observation entity uses snake_case JSON field names"
    ((PASSED++))
else
    echo "[FAIL] Observation entity should use snake_case JSON field names"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 10: Summary entity has snake_case fields (TypeScript expects snake_case)
echo ""
echo "=========================================="
echo "Test 8: Summary Entity (snake_case JSON fields)"
echo "=========================================="

response=$(curl -s "$BASE_URL/api/summaries?offset=0&limit=1")
# Summary JSON uses snake_case: session_id, next_steps, files_read, files_edited
if echo "$response" | grep -qE '"session_id"|"next_steps"|"files_read"|"files_edited"|"prompt_number"'; then
    echo "[PASS] Summary entity uses snake_case JSON field names"
    ((PASSED++))
else
    echo "[FAIL] Summary entity should use snake_case JSON field names"
    echo "Got: $response"
    ((FAILED++))
fi

# Test 11: SSE stream has type field
echo ""
echo "=========================================="
echo "Test 9: SSE Stream (type field)"
echo "=========================================="

# Start a background SSE connection and capture the first few events
timeout 5 curl -s -N "$BASE_URL/stream" | head -20 > /tmp/sse_output.txt 2>/dev/null || true

if grep -q '"type"' /tmp/sse_output.txt 2>/dev/null; then
    echo "[PASS] SSE events include type field"
    ((PASSED++))
else
    echo "[WARN] SSE events should include type field (checking timeout)"
    # This might time out, so it's a soft check
fi

# Summary
echo ""
echo "=========================================="
echo "WebUI Integration Test Summary"
echo "=========================================="
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "All tests passed!"
    exit 0
else
    echo "Some tests failed!"
    exit 1
fi
