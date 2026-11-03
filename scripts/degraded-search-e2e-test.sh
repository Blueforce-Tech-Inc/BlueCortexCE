#!/usr/bin/env bash
#
# Degraded-mode search E2E: run the product with NO embedding provider at all,
# write one Chinese observation, search for it in Chinese, and require a hit.
#
# Why this exists
# ---------------
# "No embedding key configured" is the most likely real-user setup, and before
# V24 it was silently broken for Chinese text: the English tsvector analyser
# produced one lexeme per run of CJK characters, so 60 of 60 Chinese queries
# returned nothing. That made search look like "sometimes finds, sometimes not"
# depending only on whether an embedding provider happened to be configured.
#
# This script does not mock anything. It starts the packaged jar in a cleaned
# environment (env -i), so no SPRING_AI_OPENAI_EMBEDDING_API_KEY / OPENAI_API_KEY
# can leak in from the caller. The application then reports "No EmbeddingModel
# configured" at startup, the write path records embedding_state=failed, and the
# query path answers through the lexical half with strategy=tsvector.
#
# What is asserted
#   1. the jar becomes healthy with no key in the environment;
#   2. the startup log says the embedding model is absent (the premise);
#   3. POST /api/ingest/observation stores a Chinese row with no vector;
#   4. GET /api/search?query=<Chinese> returns that row, strategy=tsvector,
#      fell_back=false — the lexical half, not a vector fallback;
#   5. GET /api/search with a token that appears nowhere returns 0 rows, so the
#      hit in step 4 cannot be explained by "return everything".
#
# Usage:
#   ./scripts/degraded-search-e2e-test.sh
#
# Overrides:
#   PG_CONTAINER=claude-mem-test-pg  PG_PORT=5433  PG_USER=postgres  PG_PASSWORD=123456
#   DEGRADED_APP_PORT=37895  DEGRADED_KEEP_DB=1  DEGRADED_LOG_DIR=/tmp/...
#
# The database is created for this run and dropped on exit unless kept.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PG_CONTAINER="${PG_CONTAINER:-claude-mem-test-pg}"
PG_PORT="${PG_PORT:-5433}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-123456}"
APP_PORT="${DEGRADED_APP_PORT:-37895}"
KEEP_DB="${DEGRADED_KEEP_DB:-0}"
LOG_DIR="${DEGRADED_LOG_DIR:-/tmp/cortexce-degraded-$$}"

DB="cortexce_degraded_$(date +%Y%m%d%H%M%S)_$$"
PROJECT="/tmp/cortexce-degraded-e2e-$$"
SESSION="degraded-e2e-$$"
TITLE="中文全文检索降级验证"
NARRATIVE="在未配置向量模型的情况下，中文按二元分词写入倒排索引，查询仍然命中。"
QUERY="中文全文检索降级"          # substring of NARRATIVE and TITLE
ABSENT_TOKEN="qzxvbnmpplkjh"      # appears nowhere in the corpus

mkdir -p "$LOG_DIR"

APP_PID=""
fail() {
  printf '\nFAILED: %s\n' "$1" >&2
  shift
  printf '%s\n' "$@" >&2
  printf 'Logs: %s\n' "$LOG_DIR" >&2
  exit 1
}

cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [[ "$KEEP_DB" != "1" ]]; then
    docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
      -c "DROP DATABASE IF EXISTS \"$DB\" WITH (FORCE)" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

step() { printf '\n=== %s ===\n' "$*"; }

# --------------------------------------------------------------------------
# 1. Preconditions
# --------------------------------------------------------------------------
step "1/7 Check the database container and the packaged jar"

docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER" \
  || fail "database container" "container '$PG_CONTAINER' is not running."

command -v java >/dev/null 2>&1 || fail "java" "java is not on PATH."

JAR="$(ls -t backend/target/cortex-ce-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -n1 || true)"
if [[ -z "$JAR" ]]; then
  step "1/7 Build the jar (none found)"
  (cd backend && mvn -B -DskipTests package > "$LOG_DIR/mvn-package.log" 2>&1) \
    || fail "build" "mvn package failed; see $LOG_DIR/mvn-package.log"
  JAR="$(ls -t backend/target/cortex-ce-*.jar | grep -v -- '-sources.jar' | head -n1)"
fi
printf '    jar: %s\n' "$JAR"

# --------------------------------------------------------------------------
# 2. A disposable database; Flyway migrates it during startup
# --------------------------------------------------------------------------
step "2/7 Create disposable database $DB"

docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$DB\" WITH (FORCE)" >/dev/null \
  || fail "database" "could not drop '$DB'."
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "CREATE DATABASE \"$DB\"" >/dev/null \
  || fail "database" "could not create '$DB'."

# --------------------------------------------------------------------------
# 3. Start the application with a clean environment
#
# env -i is the point of this test: whether an embedding model exists is decided
# by ambient configuration, so the test must control the ambient configuration
# rather than assume the caller's shell is clean.
# --------------------------------------------------------------------------
step "3/7 Start the jar with no embedding/LLM keys in the environment"

if (exec 3<>"/dev/tcp/127.0.0.1/$APP_PORT") 2>/dev/null; then
  fail "port" "port $APP_PORT is already in use; set DEGRADED_APP_PORT to a free port."
fi

env -i \
  PATH="$PATH" \
  HOME="$HOME" \
  TMPDIR="${TMPDIR:-/tmp}" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${DB}" \
  SPRING_DATASOURCE_USERNAME="$PG_USER" \
  SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
  SERVER_PORT="$APP_PORT" \
  java -jar "$JAR" > "$LOG_DIR/app.log" 2>&1 &
APP_PID=$!

for _ in $(seq 1 120); do
  if curl -sf "http://127.0.0.1:${APP_PORT}/api/health" >/dev/null 2>&1; then
    break
  fi
  kill -0 "$APP_PID" 2>/dev/null \
    || fail "startup" "the application exited before becoming healthy; see $LOG_DIR/app.log"
  sleep 1
done
curl -sf "http://127.0.0.1:${APP_PORT}/api/health" >/dev/null 2>&1 \
  || fail "startup" "no healthy /api/health within 120s; see $LOG_DIR/app.log"
printf '    healthy on port %s\n' "$APP_PORT"

# --------------------------------------------------------------------------
# 4. The premise: no embedding model was wired
# --------------------------------------------------------------------------
step "4/7 Assert the startup log reports no embedding model"

grep -q "No EmbeddingModel configured" "$LOG_DIR/app.log" \
  || fail "premise" "the log does not say 'No EmbeddingModel configured'; an embedding model may have leaked in."

# --------------------------------------------------------------------------
# 5. Write a Chinese observation
# --------------------------------------------------------------------------
step "5/7 POST a Chinese observation"

cat > "$LOG_DIR/observation.json" <<JSON
{
  "content_session_id": "$SESSION",
  "project_path": "$PROJECT",
  "type": "feature",
  "title": "$TITLE",
  "narrative": "$NARRATIVE",
  "facts": ["中文二元分词写入 search_tokens 生成列"],
  "concepts": ["search"]
}
JSON

code="$(curl -s -o "$LOG_DIR/observation-response.json" -w '%{http_code}' \
  -X POST "http://127.0.0.1:${APP_PORT}/api/ingest/observation" \
  -H 'Content-Type: application/json' \
  --data-binary "@$LOG_DIR/observation.json")"
[[ "$code" == "200" ]] \
  || fail "write" "POST /api/ingest/observation answered $code; see $LOG_DIR/observation-response.json"

python3 - "$LOG_DIR/observation-response.json" "$TITLE" <<'PY' \
  || fail "write" "the stored row is not the no-vector Chinese row this test expects."
import json, sys
path, title = sys.argv[1], sys.argv[2]
row = json.load(open(path, encoding="utf-8"))
assert row.get("title") == title, f"title mismatch: {row.get('title')!r}"
state = row.get("embedding_state")
assert state != "ready", f"embedding_state is {state!r}; a vector was written"
assert row.get("embedding_1024") in (None, []), "embedding_1024 is populated"
print(f"    id={row.get('id')} embedding_state={state} embedding_model_id={row.get('embedding_model_id')!r}")
PY

# --------------------------------------------------------------------------
# 6. The degraded query hits the lexical half
# --------------------------------------------------------------------------
step "6/7 GET /api/search with the Chinese query"

code="$(curl -s -o "$LOG_DIR/search-response.json" -w '%{http_code}' -G \
  "http://127.0.0.1:${APP_PORT}/api/search" \
  --data-urlencode "project=$PROJECT" \
  --data-urlencode "query=$QUERY" \
  --data-urlencode "limit=5")"
[[ "$code" == "200" ]] \
  || fail "search" "GET /api/search answered $code; see $LOG_DIR/search-response.json"

python3 - "$LOG_DIR/search-response.json" "$TITLE" <<'PY' \
  || fail "search" "the degraded search did not return the Chinese row through the lexical half."
import json, sys
path, title = sys.argv[1], sys.argv[2]
body = json.load(open(path, encoding="utf-8"))
assert body.get("strategy") == "tsvector", f"strategy is {body.get('strategy')!r}, expected tsvector"
assert body.get("fell_back") is False, "fell_back must be false: no vector was attempted"
rows = body.get("observations") or []
assert rows, "search returned no rows"
assert rows[0].get("title") == title, f"first row is {rows[0].get('title')!r}"
print(f"    strategy={body.get('strategy')} count={body.get('count')} first={rows[0].get('title')!r}")
PY

# --------------------------------------------------------------------------
# 7. Negative control: the hit above is lexical, not "return everything"
# --------------------------------------------------------------------------
step "7/7 Negative control: an absent token returns nothing"

code="$(curl -s -o "$LOG_DIR/search-absent.json" -w '%{http_code}' -G \
  "http://127.0.0.1:${APP_PORT}/api/search" \
  --data-urlencode "project=$PROJECT" \
  --data-urlencode "query=$ABSENT_TOKEN")"
[[ "$code" == "200" ]] \
  || fail "negative control" "GET /api/search answered $code."

python3 - "$LOG_DIR/search-absent.json" <<'PY' \
  || fail "negative control" "an absent token matched rows, so the positive case proves nothing."
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
assert body.get("strategy") == "tsvector", body.get("strategy")
assert (body.get("observations") or []) == [], body.get("observations")
print("    count=0 (as expected)")
PY

printf '\nALL DEGRADED-MODE ASSERTIONS PASSED\n'
