#!/usr/bin/env bash
# =============================================================================
# Hook payload E2E: real Claude Code hook shapes through proxy/wrapper.js.
#
# L4EndToEndTest drives the REST endpoints directly. This script exercises the
# layer between Claude Code and the service: proxy/wrapper.js reading a JSON
# payload from stdin, choosing the handler, mapping fields and posting them.
#
# Payloads use the documented Claude Code shapes:
#   * UserPromptSubmit carries the submitted text in `prompt` (not `prompt_text`)
#   * PostToolUse carries tool_name / tool_input / tool_response
#   * SessionEnd carries transcript_path and a reason
# with hook_event_name plus session_id and cwd on every event.
#
# Checks:
#   1. user-prompt is persisted with the exact prompt text and the project path
#      taken from cwd;
#   2. PostToolUse is persisted once, and re-delivering the identical payload
#      creates no second queue row (the tool-input hash dedupe);
#   3. SessionEnd marks the conversation completed and produces a summary;
#   4. a delivery against an unreachable service exits 0 with a clear message
#      instead of crashing or hanging.
#
# Usage:
#   scripts/hook-payload-e2e.sh
#
# Environment:
#   PORT=37992, CONTAINER=claude-mem-test-pg, PGPORT=5433
#   KEEP=1 keeps the database and app log.
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="${CONTAINER:-claude-mem-test-pg}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-123456}"
PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5433}"
PORT="${PORT:-37992}"
KEEP="${KEEP:-0}"
DB="${HOOK_DB:-cortexce_hook_payload_flyway_rehearsal}"
PROJECT="/tmp/hook-payload-project"
SESSION="hook-session-$(date +%s)"
PROMPT_TEXT="真实 Hook 载荷：把代理转发的提示词原样写进数据库"
APP_PID=""
LOG="/tmp/hook-payload-e2e.log"

log() { printf '[hook-e2e] %s\n' "$*"; }
fail() { printf '[hook-e2e] FAIL: %s\n' "$*" >&2; exit 1; }

psql_run() { docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB" -v ON_ERROR_STOP=1 -t -A -c "$1"; }

cleanup() {
  if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true; fi
  if [ "$KEEP" != "1" ]; then
    docker exec -i "$CONTAINER" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
      -c "DROP DATABASE IF EXISTS \"${DB}\" WITH (FORCE);" >/dev/null 2>&1 || true
  else
    log "kept ${DB} and ${LOG}"
  fi
}
trap cleanup EXIT

[ -f "$ROOT/backend/.env" ] || fail "backend/.env with real provider keys is required"
set -a
# shellcheck disable=SC1091
. "$ROOT/backend/.env"
set +a

JAR="$(ls "$ROOT"/backend/target/cortex-ce-*.jar 2>/dev/null | grep -v -- '-sources' | tail -1 || true)"
[ -n "$JAR" ] || fail "no packaged jar under backend/target"
node -e 'process.exit(0)' || fail "node is required for proxy/wrapper.js"

docker exec "$CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1 || fail "postgres not ready"
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"${DB}\" WITH (FORCE);" >/dev/null
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "CREATE DATABASE \"${DB}\";" >/dev/null
psql_run "CREATE EXTENSION IF NOT EXISTS vector;" >/dev/null

SPRING_DATASOURCE_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${DB}" \
SPRING_DATASOURCE_USERNAME="$DB_USER" \
SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
SERVER_PORT="$PORT" \
CORTEXCE_API_TOKEN="" \
CI=false GITHUB_ACTIONS=false \
  java -Xmx768m -jar "$JAR" > "$LOG" 2>&1 &
APP_PID=$!

for _ in $(seq 1 120); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then tail -30 "$LOG" >&2; fail "service exited during boot"; fi
  if curl -sf -o /dev/null "http://127.0.0.1:${PORT}/api/health"; then break; fi
  sleep 1
done
curl -sf -o /dev/null "http://127.0.0.1:${PORT}/api/health" || { tail -30 "$LOG" >&2; fail "service never became healthy"; }
log "service healthy on ${PORT}, database ${DB}"

send_hook() { # send_hook <command> <payload-json>
  printf '%s' "$2" | node "$ROOT/proxy/wrapper.js" "$1" --url "http://127.0.0.1:${PORT}"
}

# ---------------------------------------------------------------------------
# 1. UserPromptSubmit
# ---------------------------------------------------------------------------
USER_PROMPT_PAYLOAD="$(python3 - "$SESSION" "$PROJECT" "$PROMPT_TEXT" <<'PY'
import json, sys
session, cwd, prompt = sys.argv[1:4]
print(json.dumps({
    "session_id": session,
    "transcript_path": "/tmp/hook-payload-transcript.jsonl",
    "cwd": cwd,
    "hook_event_name": "UserPromptSubmit",
    "prompt": prompt,
    "prompt_number": 1,
}))
PY
)"
send_hook user-prompt "$USER_PROMPT_PAYLOAD" >/dev/null 2>&1 || fail "wrapper exited non-zero for UserPromptSubmit"

PROMPT_ROWS="$(psql_run "SELECT count(*) FROM mem_user_prompts WHERE content_session_id='${SESSION}' AND prompt_text='${PROMPT_TEXT}';")"
[ "$PROMPT_ROWS" = "1" ] || fail "prompt not persisted from the prompt field (rows=${PROMPT_ROWS})"
PROMPT_PROJECT="$(psql_run "SELECT project_path FROM mem_user_prompts WHERE content_session_id='${SESSION}';")"
[ "$PROMPT_PROJECT" = "$PROJECT" ] || fail "cwd was not mapped to project_path (got '${PROMPT_PROJECT}')"
log "UserPromptSubmit: prompt text and cwd mapped and persisted"

# ---------------------------------------------------------------------------
# 2. PostToolUse, delivered twice
# ---------------------------------------------------------------------------
TOOL_PAYLOAD="$(python3 - "$SESSION" "$PROJECT" <<'PY'
import json, sys
session, cwd = sys.argv[1:3]
print(json.dumps({
    "session_id": session,
    "transcript_path": "/tmp/hook-payload-transcript.jsonl",
    "cwd": cwd,
    "hook_event_name": "PostToolUse",
    "tool_name": "Edit",
    "tool_input": {
        "file_path": cwd + "/RetentionService.java",
        "old_string": "return 0;",
        "new_string": "return rows;"
    },
    "tool_response": {"filePath": cwd + "/RetentionService.java", "success": True, "content": "retention cleanup wired"},
}))
PY
)"
send_hook tool-use "$TOOL_PAYLOAD" >/dev/null 2>&1 || fail "wrapper exited non-zero for PostToolUse"
send_hook tool-use "$TOOL_PAYLOAD" >/dev/null 2>&1 || fail "wrapper exited non-zero for the duplicate PostToolUse"

PENDING="$(psql_run "SELECT count(*) FROM mem_pending_messages pm JOIN mem_sessions s ON s.id = pm.session_db_id WHERE s.content_session_id='${SESSION}';")"
[ "$PENDING" = "1" ] || fail "duplicate PostToolUse created ${PENDING} queue rows, expected 1"
TOOL_INPUT="$(psql_run "SELECT tool_input FROM mem_pending_messages pm JOIN mem_sessions s ON s.id = pm.session_db_id WHERE s.content_session_id='${SESSION}' LIMIT 1;")"
printf '%s' "$TOOL_INPUT" | grep -q 'RetentionService.java' || fail "tool_input was not persisted verbatim"

OBS=""
for _ in $(seq 1 90); do
  OBS="$(psql_run "SELECT count(*) FROM mem_observations WHERE content_session_id='${SESSION}';")"
  [ "$OBS" != "0" ] && break
  sleep 2
done
[ "$OBS" != "0" ] || { tail -30 "$LOG" >&2; fail "no observation was extracted from the PostToolUse payload"; }
log "PostToolUse: one queue row after duplicate delivery, ${OBS} observation(s) extracted"

# ---------------------------------------------------------------------------
# 3. SessionEnd
# ---------------------------------------------------------------------------
SESSION_END_PAYLOAD="$(python3 - "$SESSION" "$PROJECT" <<'PY'
import json, sys
session, cwd = sys.argv[1:3]
print(json.dumps({
    "session_id": session,
    "transcript_path": "/tmp/hook-payload-transcript.jsonl",
    "cwd": cwd,
    "hook_event_name": "SessionEnd",
    "reason": "clear",
}))
PY
)"
send_hook session-end "$SESSION_END_PAYLOAD" >/dev/null 2>&1 || fail "wrapper exited non-zero for SessionEnd"

STATUS=""
for _ in $(seq 1 30); do
  STATUS="$(psql_run "SELECT status FROM mem_sessions WHERE content_session_id='${SESSION}';")"
  [ "$STATUS" = "completed" ] && break
  sleep 1
done
[ "$STATUS" = "completed" ] || fail "session status is '${STATUS}', expected completed"

SUMMARY=""
for _ in $(seq 1 90); do
  SUMMARY="$(psql_run "SELECT count(*) FROM mem_summaries WHERE content_session_id='${SESSION}';")"
  [ "$SUMMARY" != "0" ] && break
  sleep 2
done
[ "$SUMMARY" != "0" ] || { tail -30 "$LOG" >&2; fail "SessionEnd produced no summary row"; }
log "SessionEnd: conversation completed, ${SUMMARY} summary row(s)"

# ---------------------------------------------------------------------------
# 4. Failure path: unreachable service
# ---------------------------------------------------------------------------
set +e
FAIL_OUT="$(printf '%s' "$USER_PROMPT_PAYLOAD" | node "$ROOT/proxy/wrapper.js" user-prompt --url http://127.0.0.1:39999 2>&1)"
FAIL_CODE=$?
set -e
[ "$FAIL_CODE" = "0" ] || fail "wrapper exited ${FAIL_CODE} on an unreachable service (should fail gracefully)"
printf '%s' "$FAIL_OUT" | grep -q "Cannot connect" || fail "wrapper did not report the unreachable service: ${FAIL_OUT}"
log "failure path: unreachable service logged and exited 0"

log "PASS: real hook payloads mapped and persisted; duplicate PostToolUse deduped; failure path graceful"
