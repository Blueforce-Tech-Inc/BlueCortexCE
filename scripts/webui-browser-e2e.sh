#!/usr/bin/env bash
# Manual browser end-to-end check of the bundled viewer.
#
# Boots the real backend against a disposable PostgreSQL database, seeds enough
# observations to cross the viewer's 50-row page size, then runs a real Chromium
# (Playwright) against http://127.0.0.1:<port>/ to prove the page renders,
# `files_modified` reaches the card, `hasMore` pagination pages through the whole
# feed, and a record created after load is visible after a plain refresh.
#
# Never runs in CI. Playwright is a local tool, not a production dependency:
#   npm i -g playwright && npx playwright install chromium   # once, if missing
#
# Usage:
#   ./scripts/webui-browser-e2e.sh
#   WEBUI_E2E_PORT=37800 SCREENSHOT=/tmp/viewer.png ./scripts/webui-browser-e2e.sh
#
# Overrides: PG_CONTAINER, PG_PORT, PG_USER, PG_PASSWORD, WEBUI_E2E_PORT,
#            SEED_COUNT (default 60), KEEP_DB (keep the database for inspection).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PG_CONTAINER="${PG_CONTAINER:-claude-mem-test-pg}"
PG_PORT="${PG_PORT:-5433}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-123456}"
PORT="${WEBUI_E2E_PORT:-37778}"
SEED_COUNT="${SEED_COUNT:-60}"
PROJECT_PATH="${PROJECT_PATH:-/tmp/cortexce-webui-browser-e2e}"
STAMP="$(date +%Y%m%d%H%M%S)"
DB="cortexce_browser_${STAMP}_$$_flyway_rehearsal"
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/cortexce-webui-e2e.XXXXXX")"
BASE_URL="http://127.0.0.1:${PORT}"
APP_PID=""
STEP=0

step() {
  STEP=$((STEP + 1))
  echo ""
  echo "=== ${STEP} $* ==="
}

fail() {
  echo ""
  echo "STEP FAILED: $*"
  [[ -f "$LOG_DIR/app.log" ]] && tail -40 "$LOG_DIR/app.log"
  exit 1
}

cleanup() {
  local status=$?
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [[ "${KEEP_DB:-0}" == "1" ]]; then
    echo "kept database $DB (KEEP_DB=1)"
  else
    docker exec "$PG_CONTAINER" psql -U "$PG_USER" -q \
      -c "DROP DATABASE IF EXISTS \"$DB\" WITH (FORCE);" >/dev/null 2>&1 || true
  fi
  echo "logs: $LOG_DIR"
  exit $status
}
trap cleanup EXIT

step "Locate the PostgreSQL container"
docker inspect "$PG_CONTAINER" >/dev/null 2>&1 || fail "container $PG_CONTAINER not found"
echo "container=$PG_CONTAINER port=$PG_PORT database=$DB"

step "Create and migrate-ready the disposable database"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -q -c "CREATE DATABASE \"$DB\";" || fail "create database"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$DB" -q -c "CREATE EXTENSION IF NOT EXISTS vector;" \
  || fail "enable pgvector"
echo "database $DB ready"

step "Locate the application jar"
JAR="$(ls "$ROOT"/backend/target/cortex-ce-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "$JAR" ]]; then
  echo "no jar yet; building one (mvn -DskipTests package)"
  (cd "$ROOT/backend" && mvn -q -B -DskipTests package) || fail "mvn package"
  JAR="$(ls "$ROOT"/backend/target/cortex-ce-*.jar | head -1)"
fi
echo "jar=$JAR"

step "Boot the backend on ${BASE_URL}"
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${DB}" \
SPRING_DATASOURCE_USERNAME="$PG_USER" \
SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
SERVER_PORT="$PORT" \
  java -jar "$JAR" --server.port="$PORT" > "$LOG_DIR/app.log" 2>&1 &
APP_PID=$!
for _ in $(seq 1 120); do
  if curl -sf "${BASE_URL}/api/health" >/dev/null 2>&1; then break; fi
  kill -0 "$APP_PID" 2>/dev/null || fail "application exited during startup"
  sleep 1
done
curl -sf "${BASE_URL}/api/health" >/dev/null || fail "application did not become healthy"
echo "health: $(curl -s "${BASE_URL}/api/health")"

step "Start a session and assert the updateFiles contract"
curl -sf -X POST "${BASE_URL}/api/session/start" -H 'Content-Type: application/json' \
  -d "{\"session_id\":\"browser-e2e-session\",\"project_path\":\"${PROJECT_PATH}\"}" \
  > "$LOG_DIR/session.json" || fail "session start"
python3 - "$LOG_DIR/session.json" <<'PY' || fail "session start must return an updateFiles array"
import json, sys
body = json.load(open(sys.argv[1]))
assert isinstance(body.get("updateFiles"), list), body
print(f"updateFiles entries: {len(body['updateFiles'])}")
PY

step "Seed ${SEED_COUNT} observations through the ingest API"
for i in $(seq 1 "$SEED_COUNT"); do
  curl -sf -X POST "${BASE_URL}/api/ingest/observation" -H 'Content-Type: application/json' \
    -d "{\"content_session_id\":\"browser-e2e-session\",\"project_path\":\"${PROJECT_PATH}\",\"type\":\"change\",\"title\":\"Seeded observation ${i}\",\"subtitle\":\"Seed row ${i} for viewer pagination\",\"facts\":[\"Seed row ${i} pins created_at_epoch ordering\"],\"concepts\":[\"what-changed\",\"pattern\"],\"files_modified\":[\"${PROJECT_PATH}/src/seed-${i}.ts\"]}" \
    >/dev/null || fail "seed observation $i"
done
echo "seeded rows: $(curl -s "${BASE_URL}/api/observations?offset=0&limit=1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["items"].__len__())') in first page slice"

step "Drive the bundled viewer with Playwright"
BASE_URL="$BASE_URL" PROJECT_PATH="$PROJECT_PATH" SEED_COUNT="$SEED_COUNT" \
SCREENSHOT="${SCREENSHOT:-}" \
  node "$ROOT/scripts/browser/webui-viewer-e2e.mjs" || fail "browser assertions"

echo ""
echo "ALL CHECKS PASSED"
echo "  base url : $BASE_URL"
echo "  database : $DB"
echo "  seeded   : $SEED_COUNT observations, page size 50"
echo "  logs     : $LOG_DIR"
