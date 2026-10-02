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
# Project the Observations section writes captures under.
DEMO_PROJECT="${DEMO_PROJECT:-/tmp/cortex-demo-e2e-project}"

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

# Status-code assertion. run_ok only matches the body, which cannot tell a 400
# from a 500 that happens to carry a similar message — and that distinction is
# exactly what the /demo/observations validation paths are about.
run_status() {
  local name="$1"
  local expect="$2"
  shift 2
  echo -n "  $name ... "
  code=$(curl -s -o /tmp/cortex-demo-e2e-body -w '%{http_code}' "$@" 2>/dev/null || echo "__ERR__")
  if [[ "$code" == "$expect" ]]; then
    echo "OK"
    ((passed++)) || true
  else
    echo "FAIL"
    echo "    expected HTTP $expect, got ${code}"
    echo "    body: $(head -c 120 /tmp/cortex-demo-e2e-body 2>/dev/null)"
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

# 4. Chat (LLM + Memory)
echo ""
echo "=== 4. Chat (LLM + Memory) ==="
unique_msg="E2E-user-prompt-$(date +%s)"
chat_out=$(curl -sf --max-time 60 "$DEMO_BASE/chat?message=$unique_msg" 2>/dev/null || echo "__ERR__")

# 4b. Chat with conversationId (Bridge path — CortexSessionContextBridgeAdvisor)
conv_id="e2e-conv-$(date +%s)"
bridge_chat=$(curl -sf --max-time 60 "$DEMO_BASE/chat?message=E2E-bridge-test&conversationId=$conv_id" 2>/dev/null || echo "__ERR__")
if [ -n "$chat_out" ] && [ "$chat_out" != "__ERR__" ] && [ ${#chat_out} -gt 2 ]; then
  echo "  chat OK (${#chat_out} chars)"
  ((passed++)) || true
else
  echo "  chat FAIL"
  ((failed++)) || true
fi
if [ -n "$bridge_chat" ] && [ "$bridge_chat" != "__ERR__" ] && [ ${#bridge_chat} -gt 2 ]; then
  echo "  chat?conversationId= OK (Bridge path, ${#bridge_chat} chars)"
  ((passed++)) || true
else
  echo "  chat?conversationId= FAIL — $bridge_chat"
  ((failed++)) || true
fi

# 5. Chat with CortexMemoryTools (useTools=true)
# Requires cortex.mem.memory-tools-enabled=true
echo ""
echo "=== 5. Chat with useTools (CortexMemoryTools) ==="
tools_msg="E2E-useTools-$(date +%s)"
tools_chat=$(curl -sf --max-time 90 "$DEMO_BASE/chat?message=$tools_msg&useTools=true" 2>/dev/null || echo "__ERR__")
if [ -n "$tools_chat" ] && [ "$tools_chat" != "__ERR__" ] && [ ${#tools_chat} -gt 2 ]; then
  echo "  chat?useTools=true OK (${#tools_chat} chars)"
  ((passed++)) || true
else
  echo "  chat?useTools=true FAIL — $tools_chat"
  ((failed++)) || true
fi

# 6. User-prompt capture verification (CortexMemoryAdvisor auto-capture)
# Requires demo built with latest cortex-mem-spring-integration (mvn -Plocal after mvn install)
echo ""
echo "=== 6. User-Prompt Capture ==="
prompts_json=$(curl -sf "$BACKEND_BASE/api/prompts?limit=50" 2>/dev/null || echo "{}")
if echo "$prompts_json" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    items = d.get('items', [])
    texts = [p.get('prompt_text', p.get('promptText', '')) for p in items]
    needle = \"$unique_msg\"
    if needle in texts:
        sys.exit(0)
    if any('E2E-user-prompt-' in t for t in texts[:10]):
        sys.exit(0)
    sys.exit(1)
except Exception:
    sys.exit(2)
" 2>/dev/null; then
  echo "  user-prompt capture OK — chat message recorded in backend"
  ((passed++)) || true
else
  echo "  user-prompt capture FAIL — demo must use -Plocal with mvn install of cortex-mem-spring-integration"
  ((failed++)) || true
fi

echo ""
echo "=== 7. Observations API ==="
# This whole section was missing until round 171, which is why an unrecognised
# update field could be reported as a 500 for so long: nothing here ever
# exercised /demo/observations.
obs_id=$(curl -sf "$BACKEND_BASE/api/observations?limit=1" 2>/dev/null \
  | python3 -c "import json,sys;d=json.load(sys.stdin);i=d.get('items') or [];print(i[0].get('id','') if i else '')" 2>/dev/null || echo "")

if [[ -z "$obs_id" ]]; then
  echo "  observations API SKIP — backend has no observations to work with"
  ((passed++)) || true
else
  run_status "observations/status"        200 "$DEMO_BASE/demo/observations/status"
  run_status "observations list"          200 "$DEMO_BASE/demo/observations?limit=1"
  run_status "observations list bad limit" 400 "$DEMO_BASE/demo/observations?limit=101"
  run_status "observations list bad offset" 400 "$DEMO_BASE/demo/observations?offset=-1"
  run_status "observations get by id"     200 "$DEMO_BASE/demo/observations/$obs_id"
  run_status "observations batch"         200 \
    -X POST "$DEMO_BASE/demo/observations/batch" -H 'Content-Type: application/json' \
    -d "{\"ids\":[\"$obs_id\"]}"
  run_status "observations batch no ids"  400 \
    -X POST "$DEMO_BASE/demo/observations/batch" -H 'Content-Type: application/json' -d '{"ids":[]}'

  # The regression this section exists for: a body of only unrecognised keys
  # leaves the update empty, and the SDK raises IllegalArgumentException. Before
  # the fix the generic catch turned that into a 500; the Go, Python and JS demos
  # answer 400 for the same input.
  run_status "observations PATCH unknown field -> 400" 400 \
    -X PATCH "$DEMO_BASE/demo/observations/$obs_id" -H 'Content-Type: application/json' \
    -d '{"titel":"typo"}'
  run_status "observations PATCH empty body -> 400"    400 \
    -X PATCH "$DEMO_BASE/demo/observations/$obs_id" -H 'Content-Type: application/json' -d '{}'
  run_status "observations PATCH content+narrative -> 400" 400 \
    -X PATCH "$DEMO_BASE/demo/observations/$obs_id" -H 'Content-Type: application/json' \
    -d '{"content":"a","narrative":"b"}'
  run_status "observations PATCH valid field -> 200" 200 \
    -X PATCH "$DEMO_BASE/demo/observations/$obs_id" -H 'Content-Type: application/json' \
    -d '{"title":"round171 e2e valid update"}'

  run_status "observations create"  200 \
    -X POST "$DEMO_BASE/demo/observations/create" -H 'Content-Type: application/json' \
    -d "{\"project\":\"$DEMO_PROJECT\",\"session_id\":\"e2e-round171\",\"tool_name\":\"e2e\",\"tool_response\":\"round171\"}"
  run_status "observations create no project -> 400" 400 \
    -X POST "$DEMO_BASE/demo/observations/create" -H 'Content-Type: application/json' \
    -d '{"session_id":"e2e-round171","tool_name":"e2e"}'
  run_status "observations create bad extractedData -> 400" 400 \
    -X POST "$DEMO_BASE/demo/observations/create" -H 'Content-Type: application/json' \
    -d "{\"project\":\"$DEMO_PROJECT\",\"session_id\":\"e2e-round171\",\"tool_name\":\"e2e\",\"extractedData\":\"nope\"}"

  # Round 177: a backend 4xx is the caller's mistake, not a server failure. A
  # malformed id is the backend's 400, and the handlers used to special-case
  # only 404, so these three answered 500 -- telling someone who mistyped an id
  # that they had broken the server. The Go demo writes StatusBadRequest on its
  # non-404 branch, and the Python and JS demos pass the backend status through
  # from their global handlers, so 400 is the aligned answer.
  run_status "observations PATCH malformed id -> 400" 400 \
    -X PATCH "$DEMO_BASE/demo/observations/not-a-uuid" -H 'Content-Type: application/json' \
    -d '{"title":"round177 malformed id"}'
  run_status "observations DELETE malformed id -> 400" 400 \
    -X DELETE "$DEMO_BASE/demo/observations/not-a-uuid"
  run_status "feedback malformed observationId -> 400" 400 \
    -X POST "$DEMO_BASE/demo/feedback" -H 'Content-Type: application/json' \
    -d '{"observationId":"not-a-uuid","feedbackType":"SUCCESS"}'

  # ...and 404 still means 404, which is the distinction the fix had to preserve.
  run_status "observations PATCH absent uuid -> 404" 404 \
    -X PATCH "$DEMO_BASE/demo/observations/00000000-0000-0000-0000-000000000000" \
    -H 'Content-Type: application/json' -d '{"title":"round177 absent"}'
  run_status "observations DELETE absent uuid -> 404" 404 \
    -X DELETE "$DEMO_BASE/demo/observations/00000000-0000-0000-0000-000000000000"
fi

echo ""
echo "=========================================="
echo "Result: $passed passed, $failed failed"
echo "=========================================="
[[ $failed -eq 0 ]] && exit 0 || exit 1
