#!/usr/bin/env bash
# =============================================================================
# Backup / restore drill for the memory database.
#
# Copies a live database with pg_dump -Fc, restores it into a new database,
# runs Flyway's validation by booting the real service against the copy, compares
# row counts, and proves that a Chinese lexical query still returns the same
# observation it returns on the source.
#
# Usage:
#   SOURCE_DB=cortexce_load scripts/backup-restore-drill.sh
#
# Environment:
#   CONTAINER          postgres container (default claude-mem-test-pg)
#   PGHOST/PGPORT      host-side address of the container (default 127.0.0.1:5433)
#   DB_USER/DB_PASS    credentials (default postgres / 123456)
#   SOURCE_DB          database to back up (required)
#   RESTORE_DB         target database (default <source>_restore_<timestamp>)
#   ZH_QUERY           Chinese term for the retrieval spot check (default 检索)
#   KEEP=1             keep the restored database and the dump file
#
# Exit status: 0 only when every check passed.
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="${CONTAINER:-claude-mem-test-pg}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-123456}"
PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5433}"
SOURCE_DB="${SOURCE_DB:?set SOURCE_DB to the database you want to drill}"
STAMP="$(date +%Y%m%d_%H%M%S)"
RESTORE_DB="${RESTORE_DB:-${SOURCE_DB}_restore_${STAMP}}"
ZH_QUERY="${ZH_QUERY:-检索}"
KEEP="${KEEP:-0}"
DUMP_FILE="/tmp/${SOURCE_DB}_${STAMP}.dump"
VALIDATION_PORT="${VALIDATION_PORT:-37990}"

log() { printf '[drill] %s\n' "$*"; }
fail() { printf '[drill] FAIL: %s\n' "$*" >&2; exit 1; }

ms_now() { python3 -c 'import time; print(int(time.time()*1000))'; }
elapsed() { echo $(( $(ms_now) - $1 )); }

psql_run() { # psql_run <database> <sql>
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$1" -v ON_ERROR_STOP=1 -t -A -c "$2"
}

cleanup() {
  if [ -n "${VALIDATION_PID:-}" ]; then
    kill "$VALIDATION_PID" 2>/dev/null || true
    wait "$VALIDATION_PID" 2>/dev/null || true
  fi
  if [ "$KEEP" != "1" ]; then
    psql_run postgres "DROP DATABASE IF EXISTS \"${RESTORE_DB}\";" >/dev/null 2>&1 || true
    rm -f "$DUMP_FILE"
  else
    log "kept $RESTORE_DB and $DUMP_FILE"
  fi
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 0. Preconditions
# ---------------------------------------------------------------------------
log "source=${SOURCE_DB} container=${CONTAINER} restore=${RESTORE_DB}"
if ! docker exec "$CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1; then
  fail "postgres container ${CONTAINER} is not ready"
fi
if [ "$(psql_run postgres "SELECT 1 FROM pg_database WHERE datname='${SOURCE_DB}';")" != "1" ]; then
  fail "source database ${SOURCE_DB} does not exist"
fi
psql_run postgres "DROP DATABASE IF EXISTS \"${RESTORE_DB}\";" >/dev/null
psql_run postgres "CREATE DATABASE \"${RESTORE_DB}\";" >/dev/null
log "created empty target database ${RESTORE_DB}"

# ---------------------------------------------------------------------------
# 1. Dump
# ---------------------------------------------------------------------------
DUMP_START=$(ms_now)
docker exec -i "$CONTAINER" pg_dump -U "$DB_USER" -Fc -Z 6 "$SOURCE_DB" > "$DUMP_FILE"
DUMP_MS=$(elapsed "$DUMP_START")
DUMP_BYTES=$(wc -c < "$DUMP_FILE" | tr -d ' ')
log "pg_dump completed in ${DUMP_MS} ms, ${DUMP_BYTES} bytes"

# ---------------------------------------------------------------------------
# 2. Restore
# ---------------------------------------------------------------------------
RESTORE_START=$(ms_now)
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$RESTORE_DB" --no-owner --no-privileges \
  --exit-on-error < "$DUMP_FILE"
RESTORE_MS=$(elapsed "$RESTORE_START")
log "pg_restore completed in ${RESTORE_MS} ms"

# ---------------------------------------------------------------------------
# 3. Row-count parity
# ---------------------------------------------------------------------------
for table in mem_sessions mem_observations mem_pending_messages mem_memory_scopes; do
  SRC=$(psql_run "$SOURCE_DB" "SELECT count(*) FROM ${table};")
  DST=$(psql_run "$RESTORE_DB" "SELECT count(*) FROM ${table};")
  [ "$SRC" = "$DST" ] || fail "${table}: source=${SRC} restore=${DST}"
  log "count ${table}: ${DST} (match)"
done
log "database size: $(psql_run postgres "SELECT pg_size_pretty(pg_database_size('${SOURCE_DB}'));") source, \
$(psql_run postgres "SELECT pg_size_pretty(pg_database_size('${RESTORE_DB}'));") restore"

# ---------------------------------------------------------------------------
# 4. Flyway validation against the restored copy
# ---------------------------------------------------------------------------
SRC_VERSION=$(psql_run "$SOURCE_DB" "SELECT max(version) FROM flyway_schema_history WHERE success;")
SRC_APPLIED=$(psql_run "$SOURCE_DB" "SELECT count(*) FROM flyway_schema_history WHERE success;")
DST_VERSION=$(psql_run "$RESTORE_DB" "SELECT max(version) FROM flyway_schema_history WHERE success;")
DST_APPLIED=$(psql_run "$RESTORE_DB" "SELECT count(*) FROM flyway_schema_history WHERE success;")
[ "$SRC_VERSION" = "$DST_VERSION" ] || fail "flyway version: source=${SRC_VERSION} restore=${DST_VERSION}"
[ "$SRC_APPLIED" = "$DST_APPLIED" ] || fail "flyway applied count: source=${SRC_APPLIED} restore=${DST_APPLIED}"
log "flyway history restored: version=${DST_VERSION}, ${DST_APPLIED} applied"

JAR=$(ls "$ROOT"/backend/target/cortex-ce-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || fail "no packaged jar under backend/target; run 'cd backend && mvn -DskipTests package'"
VALIDATION_LOG="/tmp/backup-restore-drill-flyway-${STAMP}.log"
SPRING_DATASOURCE_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${RESTORE_DB}" \
SPRING_DATASOURCE_USERNAME="$DB_USER" \
SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
SERVER_PORT="$VALIDATION_PORT" \
java -jar "$JAR" > "$VALIDATION_LOG" 2>&1 &
VALIDATION_PID=$!
VALIDATION_START=$(ms_now)
VALIDATED=0
for _ in $(seq 1 90); do
  if grep -q "Successfully validated ${DST_APPLIED} migration" "$VALIDATION_LOG" 2>/dev/null; then
    VALIDATED=1
    break
  fi
  if ! kill -0 "$VALIDATION_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done
VALIDATION_MS=$(elapsed "$VALIDATION_START")
if [ "$VALIDATED" != "1" ]; then
  tail -20 "$VALIDATION_LOG" >&2 || true
  fail "Flyway did not validate the restored copy (${DST_APPLIED} migrations)"
fi
log "flyway validate passed in ${VALIDATION_MS} ms (server booted on ${VALIDATION_PORT})"
kill "$VALIDATION_PID" 2>/dev/null || true
wait "$VALIDATION_PID" 2>/dev/null || true
VALIDATION_PID=""

# ---------------------------------------------------------------------------
# 5. Retrieval spot check (Chinese lexical path)
# ---------------------------------------------------------------------------
SRC_HITS=$(psql_run "$SOURCE_DB" \
  "SELECT count(*) FROM mem_observations WHERE search_tokens @@ plainto_tsquery('simple', '${ZH_QUERY}');")
[ "$SRC_HITS" -gt 0 ] || fail "source query '${ZH_QUERY}' has no hits; pick another ZH_QUERY"
DST_HITS=$(psql_run "$RESTORE_DB" \
  "SELECT count(*) FROM mem_observations WHERE search_tokens @@ plainto_tsquery('simple', '${ZH_QUERY}');")
[ "$SRC_HITS" = "$DST_HITS" ] || fail "query '${ZH_QUERY}': source=${SRC_HITS} restore=${DST_HITS}"
log "chinese query '${ZH_QUERY}': ${DST_HITS} hits (match)"

PROBE_ID=$(psql_run "$SOURCE_DB" \
  "SELECT id FROM mem_observations WHERE search_tokens @@ plainto_tsquery('simple', '${ZH_QUERY}') \
   ORDER BY created_at_epoch DESC LIMIT 1;")
PROBE_TITLE=$(psql_run "$RESTORE_DB" "SELECT title FROM mem_observations WHERE id='${PROBE_ID}';")
[ -n "$PROBE_TITLE" ] || fail "probe observation ${PROBE_ID} missing from the restored copy"
log "probe observation ${PROBE_ID} title restored: ${PROBE_TITLE}"

# ---------------------------------------------------------------------------
# 6. Verdict
# ---------------------------------------------------------------------------
TOTAL_MS=$((DUMP_MS + RESTORE_MS + VALIDATION_MS))
log "PASS: dump=${DUMP_MS}ms restore=${RESTORE_MS}ms validate=${VALIDATION_MS}ms total=${TOTAL_MS}ms \
dump_size=${DUMP_BYTES}B"
