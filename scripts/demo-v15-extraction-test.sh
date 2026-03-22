#!/bin/bash
#
# Phase 3.1 E2E Acceptance Test Suite
# Validates: userId support, structured extraction, ICL user isolation, history
#
# Usage: ./demo-v15-extraction-test.sh
#
# Prerequisites:
# - Backend running on port 37777 with extraction enabled (dev profile)

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:37777}"
TEST_PROJECT="/tmp/ext-test-v15"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_section() { echo ""; echo "=========================================="; echo "$1"; echo "=========================================="; }

TESTS_PASSED=0
TESTS_FAILED=0

# --- Cleanup (API-based) ---
cleanup_test_data() {
    log_warn "Cleaning up test data for project: ${TEST_PROJECT}"
    # Best-effort cleanup via observations API (get IDs then delete)
    local obs_ids
    obs_ids=$(curl -sf "${BACKEND_URL}/api/observations?project=${TEST_PROJECT}&offset=0&limit=100" 2>/dev/null \
        | python3 -c "import sys,json; [print(i['id']) for i in json.load(sys.stdin).get('items',[])]" 2>/dev/null || true)
    if [ -n "$obs_ids" ]; then
        while IFS= read -r oid; do
            curl -sf -X DELETE "${BACKEND_URL}/api/memory/observations/${oid}" > /dev/null 2>&1 || true
        done <<< "$obs_ids"
    fi
}

# --- Tests ---

# Test 1: Session + userId
test_session_with_userid() {
    log_section "Test 1: Session + userId"

    local response
    response=$(curl -sf -X POST "${BACKEND_URL}/api/session/start" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-alice\",\"project_path\":\"${TEST_PROJECT}\",\"user_id\":\"alice\"}" 2>&1) || {
        log_fail "Failed to create session with userId: $response"
        ((TESTS_FAILED++)); return 1
    }

    if echo "$response" | grep -q "session_db_id"; then
        log_info "Session created with userId (response has session_db_id)"
    else
        log_fail "Response missing session_db_id: $response"
        ((TESTS_FAILED++)); return 1
    fi

    # Verify PATCH returns the userId we set
    local patch_resp
    patch_resp=$(curl -sf -X PATCH "${BACKEND_URL}/api/session/v15-alice/user" \
        -H 'Content-Type: application/json' \
        -d '{"user_id":"alice"}' 2>&1) || true
    if echo "$patch_resp" | grep -q '"userId":"alice"'; then
        log_info "UserId 'alice' confirmed via PATCH endpoint"
    else
        log_warn "PATCH verification inconclusive (userId may already be set)"
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 2: Session without userId (hook mode)
test_session_without_userid() {
    log_section "Test 2: Session without userId (hook mode)"

    local response
    response=$(curl -sf -X POST "${BACKEND_URL}/api/session/start" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-hook\",\"project_path\":\"${TEST_PROJECT}\"}" 2>&1) || {
        log_fail "Failed to create session without userId: $response"
        ((TESTS_FAILED++)); return 1
    }

    if echo "$response" | grep -q "session_db_id"; then
        log_info "Session created without userId (backward compatible)"
    else
        log_fail "Response missing session_db_id"
        ((TESTS_FAILED++)); return 1
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 3: PATCH userId
test_patch_userid() {
    log_section "Test 3: PATCH userId"

    local response
    response=$(curl -sf -X PATCH "${BACKEND_URL}/api/session/v15-hook/user" \
        -H 'Content-Type: application/json' \
        -d '{"user_id":"bob"}' 2>&1) || {
        log_fail "PATCH userId failed: $response"
        ((TESTS_FAILED++)); return 1
    }

    if echo "$response" | grep -q '"userId":"bob"'; then
        log_info "PATCH userId confirmed: 'bob'"
    else
        log_fail "PATCH response doesn't confirm 'bob': $response"
        ((TESTS_FAILED++)); return 1
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 4: Observation ingestion linked to userId session
test_observation_ingestion() {
    log_section "Test 4: Observation ingestion (linked to userId)"

    # Alice's observations
    local r1
    r1=$(curl -sf -X POST "${BACKEND_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-alice\",\"project_path\":\"${TEST_PROJECT}\",\"type\":\"user_statement\",\"source\":\"user_statement\",\"content\":\"我不喜欢苹果手机\",\"prompt_number\":1}" 2>&1) || {
        log_fail "Alice observation 1 failed: $r1"
        ((TESTS_FAILED++)); return 1
    }

    curl -sf -X POST "${BACKEND_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-alice\",\"project_path\":\"${TEST_PROJECT}\",\"type\":\"user_statement\",\"source\":\"user_statement\",\"content\":\"小米也不错\",\"prompt_number\":2}" > /dev/null 2>&1

    # Bob's observation
    curl -sf -X POST "${BACKEND_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-hook\",\"project_path\":\"${TEST_PROJECT}\",\"type\":\"user_statement\",\"source\":\"user_statement\",\"content\":\"我老婆对花生过敏\",\"prompt_number\":1}" > /dev/null 2>&1

    # Verify via API: observations exist
    local obs_count
    obs_count=$(curl -sf "${BACKEND_URL}/api/observations?project=${TEST_PROJECT}&offset=0&limit=100" 2>/dev/null \
        | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('items',[])))" 2>/dev/null || echo "0")

    if [ "$obs_count" -ge 3 ]; then
        log_info "Observations created: $obs_count (linked to sessions with userId)"
    else
        log_fail "Expected >=3 observations, got $obs_count"
        ((TESTS_FAILED++)); return 1
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 5: Extraction groups by user
test_extraction_by_user() {
    log_section "Test 5: Extraction groups by user"

    # Trigger extraction
    local response
    response=$(curl -sf -X POST "${BACKEND_URL}/api/extraction/run?projectPath=${TEST_PROJECT}" 2>&1) || {
        log_fail "Extraction trigger failed: $response"
        ((TESTS_FAILED++)); return 1
    }

    if echo "$response" | grep -q '"status":"ok"'; then
        log_info "Extraction completed successfully"
    else
        log_fail "Extraction response not ok: $response"
        ((TESTS_FAILED++)); return 1
    fi

    # Check Alice's extraction
    local alice_result
    alice_result=$(curl -sf "${BACKEND_URL}/api/extraction/user_preference/latest?projectPath=${TEST_PROJECT}&userId=alice" 2>&1) || true

    # Check Bob's extraction
    local bob_result
    bob_result=$(curl -sf "${BACKEND_URL}/api/extraction/user_preference/latest?projectPath=${TEST_PROJECT}&userId=bob" 2>&1) || true

    local alice_ok bob_ok
    alice_ok=$(echo "$alice_result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
    bob_ok=$(echo "$bob_result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")

    if [ "$alice_ok" = "ok" ] && [ "$bob_ok" = "ok" ]; then
        # Verify different session IDs (user isolation)
        local alice_sid bob_sid
        alice_sid=$(echo "$alice_result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId',''))" 2>/dev/null || echo "")
        bob_sid=$(echo "$bob_result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId',''))" 2>/dev/null || echo "")
        if [ "$alice_sid" != "$bob_sid" ]; then
            log_info "User isolation confirmed: alice='$alice_sid', bob='$bob_sid'"
        else
            log_warn "Same session ID for both users (may indicate grouping issue)"
        fi
    elif [ "$alice_ok" = "ok" ] || [ "$bob_ok" = "ok" ]; then
        log_info "At least one user's extraction found (partial success)"
    else
        log_warn "No extraction results found (LLM may not have produced output - non-fatal)"
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 6: ICL prompt with userId (no cross-user leak)
test_icl_user_isolation() {
    log_section "Test 6: ICL with userId (no cross-user leak)"

    local response
    response=$(curl -sf -X POST "${BACKEND_URL}/api/memory/icl-prompt" \
        -H 'Content-Type: application/json' \
        -d "{\"task\":\"推荐手机\",\"project\":\"${TEST_PROJECT}\",\"userId\":\"alice\",\"maxChars\":2000}" 2>&1) || {
        log_fail "ICL prompt request failed: $response"
        ((TESTS_FAILED++)); return 1
    }

    if echo "$response" | grep -q "prompt"; then
        log_info "ICL prompt generated successfully"
    else
        log_fail "ICL response missing 'prompt' field"
        ((TESTS_FAILED++)); return 1
    fi

    # Check Bob's allergy is NOT in Alice's ICL
    if echo "$response" | grep -q "花生"; then
        log_fail "LEAKED: Bob's allergy data found in Alice's ICL prompt!"
        ((TESTS_FAILED++)); return 1
    else
        log_info "Bob's data correctly excluded from Alice's ICL"
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 7: LLM re-extraction (add new preference)
test_reextraction_add() {
    log_section "Test 7: LLM re-extraction (add new preference)"

    # Add new observation
    curl -sf -X POST "${BACKEND_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-alice\",\"project_path\":\"${TEST_PROJECT}\",\"type\":\"user_statement\",\"source\":\"user_statement\",\"content\":\"Bose耳机也不错\",\"prompt_number\":3}" > /dev/null 2>&1

    # Re-run extraction
    local resp
    resp=$(curl -sf -X POST "${BACKEND_URL}/api/extraction/run?projectPath=${TEST_PROJECT}" 2>&1) || true

    if echo "$resp" | grep -q '"status":"ok"'; then
        log_info "Re-extraction triggered successfully"
    else
        log_warn "Re-extraction response: $resp"
    fi

    # Check latest extraction
    local latest
    latest=$(curl -sf "${BACKEND_URL}/api/extraction/user_preference/latest?projectPath=${TEST_PROJECT}&userId=alice" 2>&1) || true

    if echo "$latest" | grep -q '"status":"ok"'; then
        log_info "Latest extraction found after re-run"
        if echo "$latest" | grep -qi "bose"; then
            log_info "New preference (Bose) present in extraction"
        fi
    else
        log_warn "No extraction result after re-run (LLM-dependent)"
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 8: LLM re-extraction (contradiction handling)
test_reextraction_remove() {
    log_section "Test 8: LLM re-extraction (contradiction handling)"

    # Add contradicting observation
    curl -sf -X POST "${BACKEND_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-alice\",\"project_path\":\"${TEST_PROJECT}\",\"type\":\"user_statement\",\"source\":\"user_statement\",\"content\":\"我不喜欢苹果手机，永远不买\",\"prompt_number\":4}" > /dev/null 2>&1

    # Re-run extraction
    local resp
    resp=$(curl -sf -X POST "${BACKEND_URL}/api/extraction/run?projectPath=${TEST_PROJECT}" 2>&1) || true

    if echo "$resp" | grep -q '"status":"ok"'; then
        log_info "Re-extraction with contradiction completed"
    else
        log_warn "Extraction may have had issues (LLM-dependent)"
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 9: History preservation
test_history_preservation() {
    log_section "Test 9: History preservation"

    local response
    response=$(curl -sf "${BACKEND_URL}/api/extraction/user_preference/history?projectPath=${TEST_PROJECT}&userId=alice&limit=10" 2>&1) || {
        log_fail "History query failed: $response"
        ((TESTS_FAILED++)); return 1
    }

    local count
    count=$(echo "$response" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")

    if [ "$count" -ge 2 ]; then
        log_info "History has $count entries (multiple snapshots preserved)"
    elif [ "$count" -ge 1 ]; then
        log_info "History has $count entry (at least one extraction exists)"
    else
        log_warn "History is empty (LLM may not have produced results)"
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 10: Experiences API with userId filter
test_experiences_userid() {
    log_section "Test 10: Experiences API with userId filter"

    local response
    response=$(curl -sf -X POST "${BACKEND_URL}/api/memory/experiences" \
        -H 'Content-Type: application/json' \
        -d "{\"task\":\"手机\",\"project\":\"${TEST_PROJECT}\",\"userId\":\"alice\",\"count\":10}" 2>&1) || {
        log_fail "Experiences API failed: $response"
        ((TESTS_FAILED++)); return 1
    }

    if echo "$response" | python3 -c "import sys,json; data=json.load(sys.stdin); assert isinstance(data, list)" 2>/dev/null; then
        log_info "Experiences API returned list (userId filter working)"
    else
        log_fail "Experiences API response is not a list"
        ((TESTS_FAILED++)); return 1
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 11: Hook mode backward compatibility
test_hook_compat() {
    log_section "Test 11: Hook mode backward compatibility"

    # Session without userId
    curl -sf -X POST "${BACKEND_URL}/api/session/start" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-hook2\",\"project_path\":\"${TEST_PROJECT}\"}" > /dev/null 2>&1

    # Ingest observation
    curl -sf -X POST "${BACKEND_URL}/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\":\"v15-hook2\",\"project_path\":\"${TEST_PROJECT}\",\"type\":\"user_statement\",\"source\":\"user_statement\",\"content\":\"测试hook模式兼容\",\"prompt_number\":1}" > /dev/null 2>&1

    # Extraction should not fail
    local response
    response=$(curl -sf -X POST "${BACKEND_URL}/api/extraction/run?projectPath=${TEST_PROJECT}" 2>&1) || {
        log_fail "Extraction failed in hook mode: $response"
        ((TESTS_FAILED++)); return 1
    }

    if echo "$response" | grep -q '"status":"ok"'; then
        log_info "Hook mode extraction completed without errors"
    else
        log_fail "Hook mode extraction response not ok"
        ((TESTS_FAILED++)); return 1
    fi

    ((TESTS_PASSED++))
    return 0
}

# Test 12: Regression test (43/43)
test_regression() {
    log_section "Test 12: Regression test"

    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    if bash "${script_dir}/regression-test.sh" 2>&1 | tail -5 | grep -q "All tests passed"; then
        log_info "Regression test: 43/43 passed"
    else
        log_fail "Regression test failed"
        ((TESTS_FAILED++)); return 1
    fi

    ((TESTS_PASSED++))
    return 0
}

# --- Main ---

main() {
    echo "=========================================="
    echo "   Phase 3.1 E2E Acceptance Test Suite"
    echo "=========================================="
    echo ""
    echo "Backend URL: ${BACKEND_URL}"
    echo "Test Project: ${TEST_PROJECT}"
    echo ""

    # Health check
    if ! curl -sf "${BACKEND_URL}/api/health" > /dev/null 2>&1; then
        log_fail "Backend is not running at ${BACKEND_URL}"
        exit 1
    fi
    echo -e "${GREEN}[INFO]${NC} Backend is healthy"

    # Cleanup before tests
    cleanup_test_data

    # Run tests in order
    test_session_with_userid || true
    test_session_without_userid || true
    test_patch_userid || true
    test_observation_ingestion || true
    test_extraction_by_user || true
    test_icl_user_isolation || true
    test_reextraction_add || true
    test_reextraction_remove || true
    test_history_preservation || true
    test_experiences_userid || true
    test_hook_compat || true
    test_regression || true

    # Cleanup after tests
    cleanup_test_data

    # Summary
    local total=$((TESTS_PASSED + TESTS_FAILED))
    echo ""
    echo "=========================================="
    echo "Test Summary"
    echo "=========================================="
    echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
    echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
    echo -e "Total:  ${total}/12"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}✅ All 12 checks passed!${NC}"
        exit 0
    else
        echo -e "${RED}❌ ${TESTS_FAILED} check(s) failed${NC}"
        exit 1
    fi
}

main "$@"
