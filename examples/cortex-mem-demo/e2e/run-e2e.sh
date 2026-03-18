#!/usr/bin/env bash
# E2E test for cortex-mem-demo — verifies memory system integration.
#
# Requires: demo on 37778, backend on 37777
# Usage: ./run-e2e.sh
#   Or:  ./run-e2e.sh http://localhost:37778 http://localhost:37777

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEMO_BASE="${1:-http://localhost:37778}"
BACKEND_BASE="${2:-http://localhost:37777}"

passed=0
failed=0

run_ok() {
  local name="$1"
  local cmd="$2"
  local expect="$3"
  echo -n "  $name ... "
  out=$(eval "$cmd" 2>/dev/null || echo "__ERR__")
  if echo "$out" | grep -qE "$expect"; then
    echo "OK"
    ((passed++)) || true
  else
    echo "FAIL"
    echo "    expect match: $expect"
    echo "    got: ${out:0:120}..."
    ((failed++)) || true
  fi
}

echo "=========================================="
echo "Cortex Memory Demo — E2E Test"
echo "  Demo:    $DEMO_BASE"
echo "  Backend: $BACKEND_BASE"
echo "=========================================="
echo ""

# 0. Project configuration
echo "=== 0. Project Config ==="
run_ok "demo/projects"         "curl -sf $DEMO_BASE/demo/projects" "configured_projects|default"

# 1. Health & memory API
echo ""
echo "=== 1. Health & Memory Retrieval ==="
run_ok "actuator/health"       "curl -sf $DEMO_BASE/actuator/health"             "UP"
run_ok "memory/quality"        "curl -sf \"$DEMO_BASE/memory/quality?project=%2F\"" "project"
run_ok "memory/experiences"    "curl -sf \"$DEMO_BASE/memory/experiences?task=test&project=%2F&count=1\"" "\"id\"|\\[\\]"
run_ok "memory/icl"            "curl -sf \"$DEMO_BASE/memory/icl?task=test&project=%2F\"" "task|Current"
run_ok "memory/refine (GET)"   "curl -sf \"$DEMO_BASE/memory/refine?project=%2F\"" "Refinement|triggered"

# 2. Memory capture verification (core: prove memory system works)
echo ""
echo "=== 2. Memory Capture ==="
before_count=$(curl -sf "$BACKEND_BASE/api/stats" 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(d.get('database',{}).get('totalObservations',0))
" 2>/dev/null || echo "0")

echo "  Calling /demo/tool to trigger @Tool auto-capture..."
tool_out=$(curl -sf "$DEMO_BASE/demo/tool?path=%2Ftmp%2Fe2e-test-$(date +%s).txt" 2>/dev/null || echo "__ERR__")
if echo "$tool_out" | grep -qE "Tool result|captured"; then
  echo "  demo/tool OK"
  ((passed++)) || true
else
  echo "  demo/tool FAIL: $tool_out"
  ((failed++)) || true
fi

echo "  Waiting for backend async processing (LLM extraction, ~15–20s)..."
sleep 20

after_count=$(curl -sf "$BACKEND_BASE/api/stats" 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(d.get('database',{}).get('totalObservations',0))
" 2>/dev/null || echo "0")

echo "  Backend totalObservations: before=$before_count, after=$after_count"
if [ -n "$after_count" ] && [ -n "$before_count" ] && [ "$after_count" -gt "$before_count" ]; then
  echo "  Memory capture OK — new observation recorded"
  ((passed++)) || true
else
  echo "  Memory capture FAIL — no new observation (async may not have finished or AOP not active)"
  ((failed++)) || true
fi

# 3. Session lifecycle (optional)
echo ""
echo "=== 3. Session Lifecycle ==="
lifecycle_out=$(curl -sf -X POST "$DEMO_BASE/demo/session/lifecycle?project=project-a&prompt=test&toolPath=%2Ftmp%2Fdemo-project-a%2Freadme.txt" 2>/dev/null || echo "__ERR__")
if echo "$lifecycle_out" | grep -qE "session_id|session_ended|prompt_recorded"; then
  echo "  lifecycle OK"
  ((passed++)) || true
else
  echo "  lifecycle skip or FAIL: ${lifecycle_out:0:80}"
fi

# 4. Chat
echo ""
echo "=== 4. Chat (LLM + Memory) ==="
chat_out=$(curl -sf --max-time 60 "$DEMO_BASE/chat?message=Hi" 2>/dev/null || echo "__ERR__")
if [ -n "$chat_out" ] && [ "$chat_out" != "__ERR__" ] && [ ${#chat_out} -gt 2 ]; then
  echo "  chat OK (${#chat_out} chars)"
  ((passed++)) || true
else
  echo "  chat FAIL"
  ((failed++)) || true
fi

echo ""
echo "=========================================="
echo "Result: $passed passed, $failed failed"
echo "=========================================="
[[ $failed -eq 0 ]] && exit 0 || exit 1
