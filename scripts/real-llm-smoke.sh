#!/usr/bin/env bash
# MANUAL real-provider smoke test. This is deliberately NOT part of
# scripts/verify-local.sh and NOT part of CI.
#
# Why it is manual: it spends real provider quota, depends on an outbound
# network, and its answers are non-deterministic. A gate that runs on every
# push must stay offline and reproducible.
#
# Budget: at most 5 provider calls, all with an explicit 30 s client timeout:
#   1. GET /api/test/embedding        -> 1 embedding call (dimension probe)
#   2. GET /api/test/llm              -> 1 chat call (connectivity probe)
#   3. POST /api/ingest/tool-use      -> 1 chat + 1 embedding (real path:
#                                       observation extraction + save)
#   4. GET /api/search                -> 1 embedding (query vector)
#   total: 2 chat + 3 embedding = 5. No retry: a failed call is reported as-is.
#
# Secrets: backend/.env is sourced, but this script never prints a value.
# It reports variable NAMES, model names, status codes, and timings only.
#
# Usage:
#   ./scripts/real-llm-smoke.sh
#
# Overrides:
#   SMOKE_PROVIDER=anthropic  SMOKE_TIMEOUT=30  SMOKE_APP_PORT=37890
#   SMOKE_ONLY=embedding  (one embedding call only; stops before the chat probe)
#   SMOKE_SKIP_PROBES=1   (skip both probes; the real path is the proof)
#   SMOKE_PATH_ONLY=ingest (stop after the observation write; 1 chat + 1 embedding)
#   PG_CONTAINER=postgresql  PG_PORT=5432  PG_USER=postgres  PG_PASSWORD=123456
#   TEST_DB=cortexce_flyway_rehearsal

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/backend/.env"
TIMEOUT="${SMOKE_TIMEOUT:-30}"
APP_PORT="${SMOKE_APP_PORT:-37890}"
PG_CONTAINER="${PG_CONTAINER:-postgresql}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-123456}"
TEST_DB="${TEST_DB:-cortexce_flyway_rehearsal}"
PROJECT="/tmp/cortexce-real-smoke-$$"
SESSION="real-smoke-$$"
LOG_DIR="${TMPDIR:-/tmp}/cortexce-real-smoke-$$"
mkdir -p "$LOG_DIR"

APP_PID=""

step() { printf '\n=== %s ===\n' "$*"; }
note() { printf '    %s\n' "$*"; }

fail() {
  local name="$1"; shift
  printf '\nSMOKE STEP FAILED: %s\n' "$name" >&2
  printf '%s\n' "$@" >&2
  printf 'Logs: %s\n' "$LOG_DIR" >&2
  exit 1
}

# Provider errors sometimes echo a masked key (e.g. "****13fc") or a bearer
# token. Never let that reach the terminal or the report.
redact() {
  sed -E \
    -e 's/(\*\*\*\*)[A-Za-z0-9_-]+/\1/g' \
    -e 's/(Bearer )[A-Za-z0-9._-]+/\1REDACTED/g' \
    -e 's/(sk-)[A-Za-z0-9_-]{6,}/\1REDACTED/g'
}

cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

# --------------------------------------------------------------------------
# 0. Safety rails: never run in CI, never load a missing env file, never echo
#    a value from it.
# --------------------------------------------------------------------------
step "0/4 Preconditions"

if [[ "${GITHUB_ACTIONS:-}" == "true" || "${CI:-}" == "true" ]]; then
  fail "preconditions" "refusing to spend real provider quota inside CI." \
       "This script is a manual step by design; keep it out of the pipeline."
fi

[[ -f "$ENV_FILE" ]] \
  || fail "preconditions" "no env file at $ENV_FILE; nothing to load (no key was read)."

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

# Presence check only. The value is never referenced, printed, or logged.
PROVIDER="${SMOKE_PROVIDER:-${CLAUDEMEM_LLM_PROVIDER:-openai}}"
if [[ "$PROVIDER" == "anthropic" ]]; then
  CHAT_KEY_VAR="ANTHROPIC_API_KEY"
  CHAT_MODEL="${ANTHROPIC_MODEL:-claude-sonnet-4-5}"
  CHAT_BASE_URL="${ANTHROPIC_BASE_URL:-https://api.anthropic.com}"
else
  # Either plain name is accepted: application.yml maps OPENAI_API_KEY into
  # spring.ai.openai.api-key, and Spring binds SPRING_AI_OPENAI_API_KEY itself.
  if [[ -n "${OPENAI_API_KEY:-}" ]]; then
    CHAT_KEY_VAR="OPENAI_API_KEY"
  else
    CHAT_KEY_VAR="SPRING_AI_OPENAI_API_KEY"
  fi
  CHAT_MODEL="${OPENAI_MODEL:-deepseek-chat}"
  CHAT_BASE_URL="${OPENAI_BASE_URL:-https://api.deepseek.com}"
fi
for name in "$CHAT_KEY_VAR" SPRING_AI_OPENAI_EMBEDDING_API_KEY; do
  if [[ -z "${!name:-}" ]]; then
    fail "preconditions" "variable $name is absent or empty in backend/.env (value not shown)."
  fi
done

EMBED_MODEL="${SPRING_AI_OPENAI_EMBEDDING_MODEL:-BAAI/bge-m3}"
EMBED_DIMS="${SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS:-1024}"

command -v curl >/dev/null 2>&1 || fail "preconditions" "curl is not on PATH."
command -v python3 >/dev/null 2>&1 || fail "preconditions" "python3 is not on PATH."
docker info >/dev/null 2>&1 || fail "preconditions" "the Docker daemon is not reachable."
docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER" \
  || fail "preconditions" "container '$PG_CONTAINER' is not running (override with PG_CONTAINER)."

note "env file: backend/.env (values not printed)"
note "variables present: $CHAT_KEY_VAR, SPRING_AI_OPENAI_EMBEDDING_API_KEY"
note "provider=$PROVIDER chat_model=$CHAT_MODEL chat_base_url=$CHAT_BASE_URL embedding_model=$EMBED_MODEL declared_dimensions=$EMBED_DIMS"
note "database=$TEST_DB on 127.0.0.1:$PG_PORT (container $PG_CONTAINER)"
note "budget: 2 chat + 3 embedding calls, client timeout ${TIMEOUT}s, no retry"

# Config mapping check (reads the same keys SpringAiConfig reads).
if [[ "$PROVIDER" != "openai" && "$PROVIDER" != "anthropic" ]]; then
  fail "preconditions" "claudemem.llm.provider is '$PROVIDER'; supported values are openai and anthropic."
fi
if [[ "$EMBED_DIMS" != "1024" ]]; then
  fail "preconditions" "declared embedding dimensions are '$EMBED_DIMS' but mem_observations.embedding_1024 is vector(1024)." \
       "Adjust SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS (or the migration) — do not change production config silently."
fi

DB_COLUMN_DIMS="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT format_type(atttypid, atttypmod) FROM pg_attribute
   WHERE attrelid='mem_observations'::regclass AND attname='embedding_1024'" 2>/dev/null \
  | tr -d '[:space:]')"
[[ "$DB_COLUMN_DIMS" == "vector(1024)" ]] \
  || fail "preconditions" "mem_observations.embedding_1024 is '$DB_COLUMN_DIMS', expected vector(1024)."
note "database column embedding_1024 = $DB_COLUMN_DIMS (matches declared dimensions)"

# --------------------------------------------------------------------------
# 1. Start the real application (no offline doubles, real provider beans).
#    Refinement is disabled for the smoke so one observation costs one chat
#    call; that is a runtime flag, not a production config change.
# --------------------------------------------------------------------------
step "1/4 Start the application against $TEST_DB (real provider beans)"

if (exec 3<>"/dev/tcp/127.0.0.1/${APP_PORT}") 2>/dev/null; then
  fail "start app" "port ${APP_PORT} is already in use; refusing to target an unknown server." \
       "Re-run with SMOKE_APP_PORT=<free port>."
fi

cd "$ROOT/backend"
jars=(target/cortex-ce-*.jar)
if [[ ! -f "${jars[0]}" ]]; then
  mvn -B -DskipTests package > "$LOG_DIR/package.log" 2>&1 \
    || fail "start app" "mvn -B -DskipTests package failed." "$(tail -n 20 "$LOG_DIR/package.log")"
  jars=(target/cortex-ce-*.jar)
fi

SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${TEST_DB}" \
SPRING_DATASOURCE_USERNAME="$PG_USER" \
SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
SERVER_PORT="$APP_PORT" \
MEMORY_REFINE_ENABLED=false \
CLAUDEMEM_LLM_PROVIDER="$PROVIDER" \
java -jar "${jars[0]}" > "$LOG_DIR/app.log" 2>&1 &
APP_PID=$!

for _ in $(seq 1 90); do
  if curl -sf --max-time 3 "http://127.0.0.1:${APP_PORT}/api/health" >/dev/null 2>&1; then
    note "application healthy on port ${APP_PORT}"
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    APP_PID=""
    fail "start app" "the application exited before becoming healthy." "$(tail -n 25 "$LOG_DIR/app.log")"
  fi
  sleep 1
done
curl -sf --max-time 3 "http://127.0.0.1:${APP_PORT}/api/health" >/dev/null 2>&1 \
  || fail "start app" "no /api/health response within 90s." "$(tail -n 25 "$LOG_DIR/app.log")"

BASE="http://127.0.0.1:${APP_PORT}"

# Small HTTP helpers: body in HTTP_BODY, status in HTTP_CODE, seconds in HTTP_TIME.
http_call() {
  local method="$1" url="$2" data="${3:-}" out
  if [[ -n "$data" ]]; then
    if ! out="$(curl -sS --max-time "$TIMEOUT" -X "$method" \
        -H 'Content-Type: application/json' -d "$data" \
        -w $'\n%{http_code}\n%{time_total}' "$url")"; then
      HTTP_CODE="curl-error"; HTTP_BODY=""; HTTP_TIME="0"; return 1
    fi
  else
    if ! out="$(curl -sS --max-time "$TIMEOUT" -X "$method" \
        -w $'\n%{http_code}\n%{time_total}' "$url")"; then
      HTTP_CODE="curl-error"; HTTP_BODY=""; HTTP_TIME="0"; return 1
    fi
  fi
  HTTP_TIME="${out##*$'\n'}"
  local rest="${out%$'\n'*}"
  HTTP_CODE="${rest##*$'\n'}"
  HTTP_BODY="${rest%$'\n'*}"
}

json_field() {
  local path="$1"
  printf '%s' "$HTTP_BODY" | python3 -c '
import json, sys
try:
    node = json.load(sys.stdin)
except Exception:
    print(""); raise SystemExit(0)
for part in sys.argv[1].split("."):
    if node is None:
        print(""); raise SystemExit(0)
    if isinstance(node, list):
        node = node[int(part)] if part.isdigit() and int(part) < len(node) else None
    elif isinstance(node, dict):
        node = next((v for k, v in node.items() if k == part), None)
    else:
        node = None
print("" if node is None else node)
' "$path"
}

# --------------------------------------------------------------------------
# 2. Two direct probes: one embedding call, one chat call.
#
# SMOKE_SKIP_PROBES=1 skips both and spends the whole budget on the real path
# (its extraction chat already proves chat + XmlParser, and its write proves
# the embedding width). Use it when the budget must cover two providers.
# --------------------------------------------------------------------------
if [[ "${SMOKE_SKIP_PROBES:-0}" == "1" ]]; then
  step "2/4 Probes skipped (SMOKE_SKIP_PROBES=1): the real path is the probe"
  EMB_OK="skipped"; CHAT_OK="skipped"; EMB_DIMS_ACTUAL=""; CHAT_ERROR=""; CHAT_REPLY=""
else
step "2/4 Probe 1/2: real EmbeddingModel (call 1 of 5)"

http_call GET "${BASE}/api/test/embedding" \
  || fail "embedding probe" "curl could not complete within ${TIMEOUT}s (code=$HTTP_CODE)."
note "http_status=$HTTP_CODE elapsed=${HTTP_TIME}s"
EMB_OK="no"
EMB_STATUS="$(json_field status)"
EMB_DIMS_ACTUAL="$(json_field dimensions)"
if [[ "$HTTP_CODE" == "200" && "$EMB_STATUS" == "success" ]]; then
  if [[ "$EMB_DIMS_ACTUAL" != "$EMBED_DIMS" ]]; then
    fail "embedding probe" "provider returned ${EMB_DIMS_ACTUAL} dimensions, config declares ${EMBED_DIMS}." \
         "Adjust SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS and the embedding_1024 column together."
  fi
  EMB_OK="yes"
  note "dimensions=$EMB_DIMS_ACTUAL (matches config and vector(1024))"
elif [[ "$HTTP_CODE" != "200" ]]; then
  note "error_type=$(json_field message | redact)"
  note "the embedding provider rejected the call (status $HTTP_CODE); not retried"
else
  note "message=$(json_field message | redact)"
  note "the embedding probe reported '$EMB_STATUS' (key absent, or provider disabled)"
fi

# SMOKE_ONLY=embedding spends exactly one call: useful while diagnosing a
# provider that rejects the embedding request, and it keeps chat quota intact.
if [[ "${SMOKE_ONLY:-}" == "embedding" ]]; then
  printf '\nSMOKE RESULT (embedding only)\n'
  printf '  provider          : %s\n' "$PROVIDER"
  printf '  embedding model   : %s\n' "$EMBED_MODEL"
  printf '  embedding probe   : %s (declared %s, returned %s)\n' \
    "$EMB_OK" "$EMBED_DIMS" "${EMB_DIMS_ACTUAL:-unknown}"
  printf '  http status/time  : %s %s\n' "$HTTP_CODE" "${HTTP_TIME}s"
  printf '  calls used        : 1 embedding (cap 5, no retry)\n'
  printf '  secrets           : no key value was printed; only variable names\n'
  printf '  logs              : %s\n' "$LOG_DIR"
  [[ "$EMB_OK" == "yes" ]] && exit 0 || exit 1
fi

step "2/4 Probe 2/2: real ChatModel (call 2 of 5)"

http_call GET "${BASE}/api/test/llm" \
  || fail "chat probe" "curl could not complete within ${TIMEOUT}s (code=$HTTP_CODE)."
note "http_status=$HTTP_CODE elapsed=${HTTP_TIME}s"
CHAT_OK="no"
CHAT_STATUS="$(json_field status)"
CHAT_ERROR="$(json_field message | redact)"
if [[ "$HTTP_CODE" == "200" && "$CHAT_STATUS" == "success" ]]; then
  CHAT_OK="yes"
  CHAT_REPLY="$(json_field response | redact)"
  note "reply=$(printf '%s' "$CHAT_REPLY" | head -c 120)"
elif [[ "$HTTP_CODE" != "200" ]]; then
  note "error_type=$CHAT_ERROR"
  note "the chat provider rejected the call (status $HTTP_CODE); not retried"
else
  note "probe reported status '$CHAT_STATUS' with HTTP 200"
fi

# A failed probe means there is nothing to prove on the expensive path, and
# continuing would spend quota on a chain that cannot work. Two calls were used.
if [[ "$CHAT_OK" != "yes" || "$EMB_OK" != "yes" ]]; then
  printf '\nSMOKE RESULT\n'
  printf '  provider          : %s\n' "$PROVIDER"
  printf '  chat model        : %s\n' "$CHAT_MODEL"
  printf '  embedding model   : %s\n' "$EMBED_MODEL"
  printf '  chat probe        : %s %s\n' "$CHAT_OK" "$CHAT_ERROR"
  printf '  embedding probe   : %s (%s dimensions)\n' "$EMB_OK" "${EMB_DIMS_ACTUAL:-unknown}"
  printf '  real path         : skipped (both probes must pass)\n'
  printf '  calls used        : 1 chat + 1 embedding = 2 (cap 5, no retry)\n'
  printf '  secrets           : no key value was printed; only variable names\n'
  printf '  logs              : %s\n' "$LOG_DIR"
  exit 1
fi
fi

# --------------------------------------------------------------------------
# 3. One real path: session start -> tool-use -> observation -> search.
#    The observation write plus the search query add 1 chat + 2 embedding calls.
# --------------------------------------------------------------------------
step "3/4 Real path: ingest -> XmlParser -> observation -> search"

http_call POST "${BASE}/api/session/start" \
  "{\"session_id\":\"${SESSION}\",\"project_path\":\"${PROJECT}\"}"
note "session/start http_status=$HTTP_CODE (no model call)"
[[ "$HTTP_CODE" == "200" ]] \
  || fail "real path" "session/start answered $HTTP_CODE: $(printf '%s' "$HTTP_BODY" | redact)"

http_call POST "${BASE}/api/ingest/tool-use" \
  "{\"session_id\":\"${SESSION}\",\"cwd\":\"${PROJECT}\",\"tool_name\":\"Write\",\"tool_input\":{\"file_path\":\"${PROJECT}/src/main/java/com/example/PaymentService.java\",\"content\":\"public Payment authorize(String orderId) {\\n    if (orderId == null || orderId.isBlank()) {\\n        throw new IllegalArgumentException(\\\"orderId must not be null\\\");\\n    }\\n    return gateway.charge(orderId);\\n}\"},\"tool_response\":{\"success\":true,\"content\":\"Fixed NullPointerException on missing orderId in PaymentService.authorize and added a regression test. Build and tests pass.\"}}"
note "tool-use http_status=$HTTP_CODE (queues the extraction; model call happens in the worker)"
[[ "$HTTP_CODE" == "200" ]] \
  || fail "real path" "tool-use answered $HTTP_CODE: $(printf '%s' "$HTTP_BODY" | redact)"

OBSERVED="no"
OBS_TITLE=""
for _ in $(seq 1 90); do
  http_call GET "${BASE}/api/observations?project=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "$PROJECT")&limit=5"
  if [[ "$HTTP_CODE" == "200" ]]; then
    OBS_TITLE="$(printf '%s' "$HTTP_BODY" | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
except Exception:
    print(""); raise SystemExit(0)
items = d.get("items") or []
print(items[0].get("title") if items else "")
')"
    if [[ -n "$OBS_TITLE" ]]; then OBSERVED="yes"; break; fi
  fi
  sleep 1
done

if [[ "$OBSERVED" != "yes" ]]; then
  JOB_STATUS="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
    "SELECT status || ' retry=' || retry_count FROM mem_pending_messages
     WHERE content_session_id='${SESSION}' ORDER BY created_at_epoch DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
  note "observation: none persisted; queue row: ${JOB_STATUS:-absent}"
  note "provider/parse error excerpt from app.log:"
  tail -n 40 "$LOG_DIR/app.log" | grep -Ei 'LLM call failed|parse|observation' | tail -n 5 | redact || true
  fail "real path" "the real observation never reached mem_observations." \
       "If the queue row is 'failed', the raw model answer was not parseable by XmlParser — see the excerpt above."
fi
note "observation persisted: title=$(printf '%s' "$OBS_TITLE" | head -c 100)"
note "=> the real chat answer was accepted by XmlParser and written as a structured observation"

# SMOKE_PATH_ONLY=ingest stops after the observation write: 1 chat + 1 embedding.
# Used to prove the second provider's chat/parse path without spending the
# search embedding call.
if [[ "${SMOKE_PATH_ONLY:-}" == "ingest" ]]; then
  ROW="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
    "SELECT vector_dims(embedding_1024) FROM mem_observations
     WHERE project_path='${PROJECT}' ORDER BY created_at_epoch DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
  JOBS="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
    "SELECT status || ':' || count(*) FROM mem_pending_messages
     WHERE content_session_id='${SESSION}' GROUP BY status" 2>/dev/null | tr '\n' ' ')"
  printf '\nSMOKE RESULT (ingest only)\n'
  printf '  provider          : %s\n' "$PROVIDER"
  printf '  chat model        : %s\n' "$CHAT_MODEL"
  printf '  observation       : persisted (XmlParser accepted the answer)\n'
  printf '  stored dimensions : %s\n' "${ROW:-unknown}"
  printf '  queue states      : %s\n' "${JOBS:-none}"
  printf '  calls used        : 1 chat + 1 embedding (cap 5, no retry)\n'
  printf '  secrets           : no key value was printed; only variable names\n'
  printf '  logs              : %s\n' "$LOG_DIR"
  [[ "$ROW" == "1024" && "$JOBS" != *"failed:"* ]] && exit 0 || exit 1
fi

http_call GET "${BASE}/api/search?project=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "$PROJECT")&query=real%20llm%20smoke%20verification&limit=5"
note "search http_status=$HTTP_CODE elapsed=${HTTP_TIME}s (one embedding call for the query)"
[[ "$HTTP_CODE" == "200" ]] || fail "real path" "search answered $HTTP_CODE: $HTTP_BODY"
SEARCH_STRATEGY="$(json_field strategy)"
SEARCH_FELL_BACK="$(json_field fell_back)"
SEARCH_COUNT="$(printf '%s' "$HTTP_BODY" | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
except Exception:
    print(0); raise SystemExit(0)
print(len(d.get("observations") or d.get("items") or []))
')"
note "strategy=$SEARCH_STRATEGY fell_back=$SEARCH_FELL_BACK hits=$SEARCH_COUNT"
# json_field prints Python booleans (False), so compare case-insensitively.
if [[ "$SEARCH_STRATEGY" != "hybrid" || "$(printf '%s' "$SEARCH_FELL_BACK" | tr '[:upper:]' '[:lower:]')" != "false" ]]; then
  fail "real path" "search did not use the vector branch (strategy=$SEARCH_STRATEGY fell_back=$SEARCH_FELL_BACK)."
fi
[[ "$SEARCH_COUNT" -ge 1 ]] \
  || fail "real path" "the vector branch returned zero observations for the text just ingested."

http_call GET "${BASE}/api/context/inject?projects=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "$PROJECT")"
note "context/inject http_status=$HTTP_CODE (database read, no model call)"
[[ "$HTTP_CODE" == "200" ]] || fail "real path" "context/inject answered $HTTP_CODE: $(printf '%s' "$HTTP_BODY" | redact)"
CTX_SUMMARY="$(printf '%s' "$HTTP_BODY" | python3 -c '
import json, sys
d = json.load(sys.stdin)
ctx = d.get("context") or ""
title = sys.argv[1]
has_update_files = "updateFiles" in d
print(f"chars={len(ctx)} has_observation_title={str(title in ctx).lower()} "
      f"has_update_files={str(has_update_files).lower()}")
' "$OBS_TITLE")"
note "$CTX_SUMMARY"
printf '%s' "$CTX_SUMMARY" | grep -q 'has_update_files=true' \
  || fail "real path" "context/inject response is missing the updateFiles contract."

# --------------------------------------------------------------------------
# 4. Database terminal state (no model call).
# --------------------------------------------------------------------------
step "4/4 Database checks (no model call)"

ROW="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT vector_dims(embedding_1024) || '|' || coalesce(source,'')
   FROM mem_observations WHERE project_path='${PROJECT}'
   ORDER BY created_at_epoch DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
note "stored embedding dimensions|source=$ROW"
[[ "${ROW%%|*}" == "1024" ]] \
  || fail "database" "stored vector dimensions are '${ROW%%|*}', expected 1024."

JOBS="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT status || ':' || count(*) FROM mem_pending_messages
   WHERE content_session_id='${SESSION}' GROUP BY status" 2>/dev/null | tr '\n' ' ')"
note "queue terminal states: ${JOBS:-none}"
[[ "$JOBS" != *"failed:"* ]] \
  || fail "database" "the real path left a failed queue job: $JOBS"

printf '\nSMOKE RESULT\n'
printf '  provider          : %s\n' "$PROVIDER"
printf '  chat model        : %s\n' "$CHAT_MODEL"
printf '  embedding model   : %s\n' "$EMBED_MODEL"
printf '  probes            : %s (skip with SMOKE_SKIP_PROBES=1)\n' \
  "$([[ "$EMB_OK" == "skipped" ]] && echo skipped || echo "$EMB_OK / $CHAT_OK")"
printf '  real path         : observation persisted (XmlParser accepted the answer), search strategy=%s, %s\n' "$SEARCH_STRATEGY" "$CTX_SUMMARY"
printf '  stored vector     : %s dimensions\n' "${ROW%%|*}"
printf '  queue states      : %s\n' "${JOBS:-none}"
printf '  budget            : cap 5 real calls, no retry\n'
printf '  secrets           : no key value was printed; only variable names\n'
printf '  logs              : %s\n' "$LOG_DIR"
