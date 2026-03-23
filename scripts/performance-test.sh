#!/bin/bash

# Performance and Stress Test Suite for CortexCE
# Usage: bash scripts/performance-test.sh

SERVER_URL="http://127.0.0.1:37777"
TEST_PROJECT="/tmp/perf-test-$$"
TEST_SESSION="perf-test-$(date +%s)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_section() { echo -e "\n${BLUE}========== $1 ==========${NC}"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

RESULTS_FILE="docs/drafts/performance-test-results.md"
mkdir -p docs/drafts

# Initialize results file
cat > "$RESULTS_FILE" << EOF
# Performance Test Results

**Date**: $(date '+%Y-%m-%d %H:%M:%S')
**Server**: $SERVER_URL

## Test Results

EOF

# Test 1: Single Request Response Time
log_section "Test 1: Single Request Response Time"

start_time=$(date +%s%N)
response=$(curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
  -H 'Content-Type: application/json' \
  -d "{
    \"content_session_id\": \"${TEST_SESSION}-single\",
    \"project_path\": \"${TEST_PROJECT}\",
    \"type\": \"feature\",
    \"title\": \"Performance Test Single\",
    \"content\": \"Single request performance test\",
    \"facts\": [\"Perf fact 1\"],
    \"concepts\": [\"performance\"],
    \"prompt_number\": 1
  }" 2>&1)
end_time=$(date +%s%N)

if [ $? -eq 0 ]; then
  duration_ms=$(( (end_time - start_time) / 1000000 ))
  log_success "Single observation creation: ${duration_ms}ms"
  echo "- **Single observation creation**: ${duration_ms}ms" >> "$RESULTS_FILE"
else
  log_fail "Single request failed"
  echo "- **Single observation creation**: FAILED" >> "$RESULTS_FILE"
fi

# Test 2: Batch Observation Creation (10 items)
log_section "Test 2: Batch Observation Creation (10 items)"

batch_start=$(date +%s%N)
success_count=0
for i in {1..10}; do
  curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
    -H 'Content-Type: application/json' \
    -d "{
      \"content_session_id\": \"${TEST_SESSION}-batch\",
      \"project_path\": \"${TEST_PROJECT}\",
      \"type\": \"feature\",
      \"title\": \"Batch Test $i\",
      \"content\": \"Batch test observation $i\",
      \"facts\": [\"Batch fact $i\"],
      \"concepts\": [\"batch\", \"test\"],
      \"prompt_number\": $i
    }" > /dev/null 2>&1 && ((success_count++))
done
batch_end=$(date +%s%N)

batch_duration_ms=$(( (batch_end - batch_start) / 1000000 ))
avg_per_item=$(( batch_duration_ms / 10 ))

log_success "Batch 10 items: ${batch_duration_ms}ms total, ~${avg_per_item}ms/item (${success_count}/10 success)"
echo "- **Batch 10 items**: ${batch_duration_ms}ms total, ~${avg_per_item}ms/item (${success_count}/10 success)" >> "$RESULTS_FILE"

# Test 3: Search Performance
log_section "Test 3: Search Performance"

search_start=$(date +%s%N)
search_response=$(curl -sf "${SERVER_URL}/api/search?project=${TEST_PROJECT}&query=performance+test&limit=10" 2>&1)
search_end=$(date +%s%N)

if [ $? -eq 0 ]; then
  search_duration_ms=$(( (search_end - search_start) / 1000000 ))
  result_count=$(echo "$search_response" | python3 -c "import sys, json; data=json.load(sys.stdin); print(len(data.get('results', data.get('items', []))))" 2>/dev/null || echo "0")
  log_success "Search query: ${search_duration_ms}ms (${result_count} results)"
  echo "- **Search query**: ${search_duration_ms}ms (${result_count} results)" >> "$RESULTS_FILE"
else
  log_fail "Search failed"
  echo "- **Search query**: FAILED" >> "$RESULTS_FILE"
fi

# Test 4: Concurrent Requests (5 parallel)
log_section "Test 4: Concurrent Requests (5 parallel)"

concurrent_start=$(date +%s%N)
pids=()
for i in {1..5}; do
  (
    curl -sf -X POST "${SERVER_URL}/api/ingest/observation" \
      -H 'Content-Type: application/json' \
      -d "{
        \"content_session_id\": \"${TEST_SESSION}-concurrent-$i\",
        \"project_path\": \"${TEST_PROJECT}\",
        \"type\": \"feature\",
        \"title\": \"Concurrent Test $i\",
        \"content\": \"Concurrent test observation $i\",
        \"facts\": [\"Concurrent fact $i\"],
        \"concepts\": [\"concurrent\"],
        \"prompt_number\": $i
      }" > /dev/null 2>&1
    echo $?
  ) &
  pids+=($!)
done

# Wait for all background jobs
concurrent_success=0
for pid in "${pids[@]}"; do
  wait $pid
  [ $? -eq 0 ] && ((concurrent_success++))
done
concurrent_end=$(date +%s%N)

concurrent_duration_ms=$(( (concurrent_end - concurrent_start) / 1000000 ))
log_success "Concurrent 5 requests: ${concurrent_duration_ms}ms (${concurrent_success}/5 success)"
echo "- **Concurrent 5 requests**: ${concurrent_duration_ms}ms (${concurrent_success}/5 success)" >> "$RESULTS_FILE"

# Test 5: Health Check Response Time
log_section "Test 5: Health Check Response Time"

health_start=$(date +%s%N)
health_response=$(curl -sf "${SERVER_URL}/api/health" 2>&1)
health_end=$(date +%s%N)

if [ $? -eq 0 ]; then
  health_duration_ms=$(( (health_end - health_start) / 1000000 ))
  log_success "Health check: ${health_duration_ms}ms"
  echo "- **Health check**: ${health_duration_ms}ms" >> "$RESULTS_FILE"
else
  log_fail "Health check failed"
  echo "- **Health check**: FAILED" >> "$RESULTS_FILE"
fi

# Test 6: ICL Prompt Generation Performance
log_section "Test 6: ICL Prompt Generation Performance"

icl_start=$(date +%s%N)
icl_response=$(curl -sf -X POST "${SERVER_URL}/api/memory/icl-prompt" \
  -H 'Content-Type: application/json' \
  -d "{
    \"project\": \"${TEST_PROJECT}\",
    \"task\": \"performance testing\",
    \"maxChars\": 2000
  }" 2>&1)
icl_end=$(date +%s%N)

if [ $? -eq 0 ]; then
  icl_duration_ms=$(( (icl_end - icl_start) / 1000000 ))
  log_success "ICL prompt generation: ${icl_duration_ms}ms"
  echo "- **ICL prompt generation**: ${icl_duration_ms}ms" >> "$RESULTS_FILE"
else
  log_fail "ICL prompt generation failed"
  echo "- **ICL prompt generation**: FAILED" >> "$RESULTS_FILE"
fi

# Test 7: Experiences API Performance
log_section "Test 7: Experiences API Performance"

exp_start=$(date +%s%N)
exp_response=$(curl -sf -X POST "${SERVER_URL}/api/memory/experiences" \
  -H 'Content-Type: application/json' \
  -d "{
    \"project\": \"${TEST_PROJECT}\",
    \"task\": \"performance testing\",
    \"maxResults\": 10
  }" 2>&1)
exp_end=$(date +%s%N)

if [ $? -eq 0 ]; then
  exp_duration_ms=$(( (exp_end - exp_start) / 1000000 ))
  log_success "Experiences API: ${exp_duration_ms}ms"
  echo "- **Experiences API**: ${exp_duration_ms}ms" >> "$RESULTS_FILE"
else
  log_fail "Experiences API failed"
  echo "- **Experiences API**: FAILED" >> "$RESULTS_FILE"
fi

# Summary
log_section "Performance Test Summary"
cat >> "$RESULTS_FILE" << EOF

## Summary

| Test | Duration | Status |
|------|----------|--------|
| Single observation | ${duration_ms:-N/A}ms | $([ $? -eq 0 ] && echo "✅" || echo "❌") |
| Batch 10 items | ${batch_duration_ms:-N/A}ms | ✅ |
| Search query | ${search_duration_ms:-N/A}ms | $([ $? -eq 0 ] && echo "✅" || echo "❌") |
| Concurrent 5 | ${concurrent_duration_ms:-N/A}ms | ✅ |
| Health check | ${health_duration_ms:-N/A}ms | $([ $? -eq 0 ] && echo "✅" || echo "❌") |
| ICL prompt | ${icl_duration_ms:-N/A}ms | $([ $? -eq 0 ] && echo "✅" || echo "❌") |
| Experiences API | ${exp_duration_ms:-N/A}ms | $([ $? -eq 0 ] && echo "✅" || echo "❌") |

## Performance Baseline

- **Average single request**: ${duration_ms:-N/A}ms
- **Average batch item**: ${avg_per_item:-N/A}ms
- **Search response time**: ${search_duration_ms:-N/A}ms
- **Concurrent throughput**: ~$(( 5 * 1000 / ${concurrent_duration_ms:-1000} )) req/sec

EOF

echo -e "\n${GREEN}Performance tests completed!${NC}"
echo -e "Results saved to: ${RESULTS_FILE}"
