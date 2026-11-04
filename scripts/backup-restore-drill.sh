#!/usr/bin/env bash
# =============================================================================
# Backup / restore inspection — safe to run unattended (cron / launchd / CI).
#
# Closed loop:
#   1. pg_dump -Fc the source database
#   2. checksum the dump and prove pg_restore can read its table of contents
#   3. restore into a throwaway database
#   4. compare row counts for the core tables
#   5. replay one retrieval query on the copy and compare the hit count and the
#      top observation's title
#   6. write a JSON report (timestamped; old reports are rotated)
#   7. drop the throwaway database and delete the dump
#
# Optionally boot the packaged service against the copy to prove Flyway
# validates the restored schema (VALIDATE_FLYWAY=1; off by default so the
# scheduled inspection stays fast and does not need a built jar).
#
# Usage:
#   SOURCE_DB=cortexce scripts/backup-restore-drill.sh
#   SOURCE_DB=cortexce VALIDATE_FLYWAY=1 scripts/backup-restore-drill.sh
#
# Environment:
#   CONTAINER          postgres container (default claude-mem-test-pg)
#   PGHOST/PGPORT      host-side address of the container (default 127.0.0.1:5433)
#   DB_USER/DB_PASS    credentials (default postgres / 123456)
#   SOURCE_DB          database to back up (required)
#   RESTORE_DB         target database (default <source>_restore_<timestamp>)
#   ZH_QUERY           Chinese term for the retrieval spot check (default 检索)
#   REPORT_DIR         where JSON reports are written
#                      (default ${HOME}/.claude-mem/backup-drill/reports)
#   REPORT_KEEP        how many reports to keep (default 30)
#   VALIDATE_FLYWAY    1 = boot the jar and assert Flyway validation (default 0)
#   VALIDATION_PORT    port for that boot (default 37990)
#   INTEGRITY_AUDIT    1 = run data-integrity-audit.py on the restored copy and
#                      fail the run when it finds violations (default 0)
#   AUDIT_JSON         where the integrity report goes
#                      (default <REPORT_DIR>/integrity-drill-<timestamp>.json)
#   DUMP_DIR           where the dump is written (default ${TMPDIR:-/tmp})
#   KEEP_DUMP          1 = keep the verified dump as the backup and prune older
#                      dumps in DUMP_DIR (default follows KEEP)
#   DUMP_KEEP_DAYS     retention for kept dumps (default 30)
#   KEEP=1             keep the restored database too (debugging)
#
# Exit status (the first failing stage wins):
#   0 pass        2 precondition     3 dump          4 checksum / archive
#   5 restore     6 row-count parity 7 retrieval     8 Flyway validation
#   10 integrity  9 unexpected
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="${CONTAINER:-claude-mem-test-pg}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-123456}"
PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5433}"
SOURCE_DB="${SOURCE_DB:?set SOURCE_DB to the database you want to inspect}"
STAMP="$(date +%Y%m%d_%H%M%S)"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RESTORE_DB="${RESTORE_DB:-${SOURCE_DB}_restore_${STAMP}}"
ZH_QUERY="${ZH_QUERY:-检索}"
KEEP="${KEEP:-0}"
DUMP_DIR="${DUMP_DIR:-${TMPDIR:-/tmp}}"
DUMP_FILE="${DUMP_DIR}/${SOURCE_DB}_${STAMP}.dump"
KEEP_DUMP="${KEEP_DUMP:-$KEEP}"
DUMP_KEEP_DAYS="${DUMP_KEEP_DAYS:-30}"
REPORT_DIR="${REPORT_DIR:-${HOME}/.claude-mem/backup-drill/reports}"
REPORT_KEEP="${REPORT_KEEP:-30}"
VALIDATE_FLYWAY="${VALIDATE_FLYWAY:-0}"
VALIDATION_PORT="${VALIDATION_PORT:-37990}"
INTEGRITY_AUDIT="${INTEGRITY_AUDIT:-0}"
AUDIT_JSON="${AUDIT_JSON:-${REPORT_DIR}/integrity-drill-${STAMP}.json}"

# Accumulated facts for the report; every value defaults so a failed run still
# writes a complete report.
DUMP_MS=0; DUMP_BYTES=0; DUMP_SHA=""
CHECKSUM_MS=0; RESTORE_MS=0; COUNTS_MS=0; RETRIEVAL_MS=0; FLYWAY_MS=0
FLYWAY_STATE="skipped"; FLYWAY_DETAIL=""
INTEGRITY_MS=0; INTEGRITY_STATE="skipped"; INTEGRITY_FINDINGS=""; INTEGRITY_JSON=""
SRC_SIZE=""; DST_SIZE=""
SRC_HITS=""; DST_HITS=""; PROBE_ID=""; PROBE_TITLE=""
FAIL_STAGE=""; FAIL_REASON=""
TABLE_REPORT=""          # newline-separated "table src dst" triples
VALIDATION_LOG=""
VALIDATION_PID=""

log() { printf '[drill] %s\n' "$*"; }

ms_now() { python3 -c 'import time; print(int(time.time()*1000))'; }
elapsed() { echo $(( $(ms_now) - $1 )); }

psql_run() { # psql_run <database> <sql>
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$1" -v ON_ERROR_STOP=1 -t -A -c "$2"
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

write_report() { # write_report <exit-code>
  local code="$1"
  mkdir -p "$REPORT_DIR" 2>/dev/null || true
  REPORT_DIR="$REPORT_DIR" REPORT_KEEP="$REPORT_KEEP" REPORT_CODE="$code" \
  REPORT_STAMP="$STAMP" REPORT_STARTED="$STARTED_AT" \
  REPORT_SOURCE="$SOURCE_DB" REPORT_RESTORE="$RESTORE_DB" \
  REPORT_DUMP_FILE="$DUMP_FILE" REPORT_DUMP_MS="$DUMP_MS" \
  REPORT_DUMP_BYTES="$DUMP_BYTES" REPORT_DUMP_SHA="$DUMP_SHA" \
  REPORT_CHECKSUM_MS="$CHECKSUM_MS" REPORT_RESTORE_MS="$RESTORE_MS" \
  REPORT_COUNTS_MS="$COUNTS_MS" REPORT_RETRIEVAL_MS="$RETRIEVAL_MS" \
  REPORT_FLYWAY_MS="$FLYWAY_MS" REPORT_FLYWAY_STATE="$FLYWAY_STATE" \
  REPORT_FLYWAY_DETAIL="$FLYWAY_DETAIL" \
  REPORT_INTEGRITY_MS="$INTEGRITY_MS" REPORT_INTEGRITY_STATE="$INTEGRITY_STATE" \
  REPORT_INTEGRITY_FINDINGS="$INTEGRITY_FINDINGS" REPORT_INTEGRITY_JSON="$INTEGRITY_JSON" \
  REPORT_SRC_SIZE="$SRC_SIZE" REPORT_DST_SIZE="$DST_SIZE" \
  REPORT_ZH_QUERY="$ZH_QUERY" REPORT_SRC_HITS="$SRC_HITS" REPORT_DST_HITS="$DST_HITS" \
  REPORT_PROBE_ID="$PROBE_ID" REPORT_PROBE_TITLE="$PROBE_TITLE" \
  REPORT_TABLES="$TABLE_REPORT" \
  REPORT_FAIL_STAGE="$FAIL_STAGE" REPORT_FAIL_REASON="$FAIL_REASON" \
  python3 - <<'PY' || log "warning: could not write the JSON report"
import json, os, glob, datetime

def num(name):
    raw = os.environ.get(name, "")
    try:
        return int(raw)
    except (TypeError, ValueError):
        return None

tables = {}
for line in os.environ.get("REPORT_TABLES", "").splitlines():
    parts = line.split()
    if len(parts) == 3:
        tables[parts[0]] = {"source": int(parts[1]), "restore": int(parts[2])}

verdict = "pass" if os.environ.get("REPORT_CODE") == "0" else "fail"
report = {
    "tool": "backup-restore-drill",
    "version": 1,
    "stamp": os.environ["REPORT_STAMP"],
    "started_at": os.environ["REPORT_STARTED"],
    "finished_at": datetime.datetime.now(datetime.timezone.utc)
        .strftime("%Y-%m-%dT%H:%M:%SZ"),
    "source_db": os.environ["REPORT_SOURCE"],
    "restore_db": os.environ["REPORT_RESTORE"],
    "verdict": verdict,
    "exit_code": num("REPORT_CODE"),
    "reason": os.environ.get("REPORT_FAIL_REASON", ""),
    "failed_stage": os.environ.get("REPORT_FAIL_STAGE", ""),
    "dump": {
        "file": os.environ["REPORT_DUMP_FILE"],
        "bytes": num("REPORT_DUMP_BYTES"),
        "sha256": os.environ.get("REPORT_DUMP_SHA", ""),
    },
    "database_size": {
        "source": os.environ.get("REPORT_SRC_SIZE", ""),
        "restore": os.environ.get("REPORT_DST_SIZE", ""),
    },
    "stages": {
        "dump_ms": num("REPORT_DUMP_MS"),
        "checksum_ms": num("REPORT_CHECKSUM_MS"),
        "restore_ms": num("REPORT_RESTORE_MS"),
        "counts_ms": num("REPORT_COUNTS_MS"),
        "retrieval_ms": num("REPORT_RETRIEVAL_MS"),
        "flyway": {
            "state": os.environ.get("REPORT_FLYWAY_STATE", "skipped"),
            "ms": num("REPORT_FLYWAY_MS"),
            "detail": os.environ.get("REPORT_FLYWAY_DETAIL", ""),
        },
        "integrity": {
            "state": os.environ.get("REPORT_INTEGRITY_STATE", "skipped"),
            "ms": num("REPORT_INTEGRITY_MS"),
            "findings": num("REPORT_INTEGRITY_FINDINGS"),
            "report": os.environ.get("REPORT_INTEGRITY_JSON", ""),
        },
    },
    "tables": tables,
    "retrieval": {
        "query": os.environ.get("REPORT_ZH_QUERY", ""),
        "source_hits": num("REPORT_SRC_HITS"),
        "restore_hits": num("REPORT_DST_HITS"),
        "probe_id": os.environ.get("REPORT_PROBE_ID", ""),
        "probe_title": os.environ.get("REPORT_PROBE_TITLE", ""),
    },
}

report_dir = os.environ["REPORT_DIR"]
path = os.path.join(report_dir, f"backup-drill-{os.environ['REPORT_STAMP']}.json")
with open(path, "w", encoding="utf-8") as handle:
    json.dump(report, handle, ensure_ascii=False, indent=2)
    handle.write("\n")

keep = num("REPORT_KEEP") or 30
existing = sorted(glob.glob(os.path.join(report_dir, "backup-drill-*.json")))
for stale in existing[:-keep] if len(existing) > keep else []:
    try:
        os.remove(stale)
    except OSError:
        pass

print(f"report={path}")
PY
}

on_exit() {
  local code="$?"
  set +e
  if [ -n "${VALIDATION_PID:-}" ]; then
    kill "$VALIDATION_PID" 2>/dev/null || true
    wait "$VALIDATION_PID" 2>/dev/null || true
  fi
  EXIT_CODE="$code"
  if [ "$code" != "0" ] && [ -z "$FAIL_STAGE" ]; then
    FAIL_STAGE="unexpected"
    FAIL_REASON="unhandled failure (see log above)"
  fi
  write_report "$code"
  if [ "$KEEP" != "1" ]; then
    psql_run postgres "DROP DATABASE IF EXISTS \"${RESTORE_DB}\";" >/dev/null 2>&1 || true
  else
    log "kept restored database ${RESTORE_DB}"
  fi
  if [ "$KEEP_DUMP" = "1" ]; then
    find "$DUMP_DIR" -maxdepth 1 -type f -name "${SOURCE_DB}_*.dump" \
      -mtime +"$DUMP_KEEP_DAYS" -delete 2>/dev/null || true
    log "kept dump ${DUMP_FILE} (retention ${DUMP_KEEP_DAYS}d)"
  else
    rm -f "$DUMP_FILE"
  fi
  if [ "$code" = "0" ]; then
    log "PASS: dump=${DUMP_MS}ms checksum=${CHECKSUM_MS}ms restore=${RESTORE_MS}ms \
counts=${COUNTS_MS}ms retrieval=${RETRIEVAL_MS}ms total=$(( $(ms_now) - START_EPOCH_MS ))ms \
dump_size=${DUMP_BYTES}B"
  else
    log "FAIL[$code] stage=${FAIL_STAGE}: ${FAIL_REASON}"
  fi
}
trap on_exit EXIT

fail() { # fail <exit-code> <stage> <reason>
  FAIL_STAGE="$2"
  FAIL_REASON="$3"
  printf '[drill] FAIL: %s (%s)\n' "$3" "$2" >&2
  exit "$1"
}

START_EPOCH_MS=$(ms_now)

# ---------------------------------------------------------------------------
# 0. Preconditions                                                   exit 2
# ---------------------------------------------------------------------------
log "source=${SOURCE_DB} container=${CONTAINER} restore=${RESTORE_DB} report_dir=${REPORT_DIR}"
if ! docker exec "$CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1; then
  fail 2 precondition "postgres container ${CONTAINER} is not ready"
fi
if [ "$(psql_run postgres "SELECT 1 FROM pg_database WHERE datname='${SOURCE_DB}';")" != "1" ]; then
  fail 2 precondition "source database ${SOURCE_DB} does not exist"
fi
if [ "$VALIDATE_FLYWAY" = "1" ]; then
  JAR=$(ls "$ROOT"/backend/target/cortex-ce-*.jar 2>/dev/null | head -1 || true)
  [ -n "$JAR" ] || fail 2 precondition \
    "VALIDATE_FLYWAY=1 needs a packaged jar; run 'cd backend && mvn -DskipTests package'"
  VALIDATION_JAR="$JAR"
fi
psql_run postgres "DROP DATABASE IF EXISTS \"${RESTORE_DB}\";" >/dev/null
psql_run postgres "CREATE DATABASE \"${RESTORE_DB}\";" >/dev/null
log "created empty target database ${RESTORE_DB}"

# ---------------------------------------------------------------------------
# 1. Dump                                                            exit 3
# ---------------------------------------------------------------------------
DUMP_START=$(ms_now)
if ! docker exec -i "$CONTAINER" pg_dump -U "$DB_USER" -Fc -Z 6 "$SOURCE_DB" > "$DUMP_FILE"; then
  fail 3 dump "pg_dump failed (disk full, permissions, or a concurrent schema change?)"
fi
DUMP_MS=$(elapsed "$DUMP_START")
DUMP_BYTES=$(wc -c < "$DUMP_FILE" | tr -d ' ')
[ "$DUMP_BYTES" -gt 0 ] || fail 3 dump "pg_dump produced an empty file"
log "pg_dump completed in ${DUMP_MS} ms, ${DUMP_BYTES} bytes"

# ---------------------------------------------------------------------------
# 2. Checksum + archive readability                                  exit 4
# ---------------------------------------------------------------------------
CHECKSUM_START=$(ms_now)
DUMP_SHA=$(sha256_of "$DUMP_FILE") || fail 4 checksum "could not hash the dump file"
if ! docker exec -i "$CONTAINER" pg_restore --list < "$DUMP_FILE" > /dev/null; then
  fail 4 checksum "pg_restore cannot read the dump's table of contents"
fi
docker exec -i "$CONTAINER" pg_restore --list < "$DUMP_FILE" 2>/dev/null \
  | grep -q "TABLE DATA public mem_observations" \
  || fail 4 checksum "the dump does not contain TABLE DATA for mem_observations"
SHA_AFTER=$(sha256_of "$DUMP_FILE")
[ "$DUMP_SHA" = "$SHA_AFTER" ] || fail 4 checksum "the dump changed between hashing and restore"
CHECKSUM_MS=$(elapsed "$CHECKSUM_START")
log "checksum sha256=${DUMP_SHA:0:16}... verified, archive readable (${CHECKSUM_MS} ms)"

# ---------------------------------------------------------------------------
# 3. Restore                                                         exit 5
# ---------------------------------------------------------------------------
RESTORE_START=$(ms_now)
if ! docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$RESTORE_DB" \
     --no-owner --no-privileges --exit-on-error < "$DUMP_FILE"; then
  fail 5 restore "pg_restore failed; the dump is readable but the copy could not be rebuilt"
fi
RESTORE_MS=$(elapsed "$RESTORE_START")
log "pg_restore completed in ${RESTORE_MS} ms"

# ---------------------------------------------------------------------------
# 4. Row-count parity                                                exit 6
# ---------------------------------------------------------------------------
COUNTS_START=$(ms_now)
for table in mem_sessions mem_observations mem_user_prompts mem_summaries \
             mem_pending_messages mem_memory_scopes; do
  SRC=$(psql_run "$SOURCE_DB" "SELECT count(*) FROM ${table};") \
    || fail 6 counts "could not count ${table} on the source"
  DST=$(psql_run "$RESTORE_DB" "SELECT count(*) FROM ${table};") \
    || fail 6 counts "could not count ${table} on the restore"
  TABLE_REPORT+="${table} ${SRC} ${DST}"$'\n'
  [ "$SRC" = "$DST" ] || fail 6 counts "${table}: source=${SRC} restore=${DST}"
  log "count ${table}: ${DST} (match)"
done
COUNTS_MS=$(elapsed "$COUNTS_START")

SRC_SIZE=$(psql_run postgres "SELECT pg_size_pretty(pg_database_size('${SOURCE_DB}'));")
DST_SIZE=$(psql_run postgres "SELECT pg_size_pretty(pg_database_size('${RESTORE_DB}'));")
log "database size: ${SRC_SIZE} source, ${DST_SIZE} restore"

# ---------------------------------------------------------------------------
# 5. Flyway validation (optional)                                    exit 8
# ---------------------------------------------------------------------------
if [ "$VALIDATE_FLYWAY" = "1" ]; then
  SRC_VERSION=$(psql_run "$SOURCE_DB" "SELECT max(version) FROM flyway_schema_history WHERE success;")
  SRC_APPLIED=$(psql_run "$SOURCE_DB" "SELECT count(*) FROM flyway_schema_history WHERE success;")
  DST_VERSION=$(psql_run "$RESTORE_DB" "SELECT max(version) FROM flyway_schema_history WHERE success;")
  DST_APPLIED=$(psql_run "$RESTORE_DB" "SELECT count(*) FROM flyway_schema_history WHERE success;")
  [ "$SRC_VERSION" = "$DST_VERSION" ] || fail 8 flyway \
    "flyway version: source=${SRC_VERSION} restore=${DST_VERSION}"
  [ "$SRC_APPLIED" = "$DST_APPLIED" ] || fail 8 flyway \
    "flyway applied count: source=${SRC_APPLIED} restore=${DST_APPLIED}"

  VALIDATION_LOG="${DUMP_DIR}/backup-drill-flyway-${STAMP}.log"
  SPRING_DATASOURCE_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${RESTORE_DB}" \
  SPRING_DATASOURCE_USERNAME="$DB_USER" \
  SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
  SERVER_PORT="$VALIDATION_PORT" \
  java -jar "$VALIDATION_JAR" > "$VALIDATION_LOG" 2>&1 &
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
  FLYWAY_MS=$(elapsed "$VALIDATION_START")
  if [ "$VALIDATED" != "1" ]; then
    tail -20 "$VALIDATION_LOG" >&2 || true
    rm -f "$VALIDATION_LOG"
    fail 8 flyway "Flyway did not validate the restored copy (${DST_APPLIED} migrations)"
  fi
  kill "$VALIDATION_PID" 2>/dev/null || true
  wait "$VALIDATION_PID" 2>/dev/null || true
  VALIDATION_PID=""
  rm -f "$VALIDATION_LOG"
  FLYWAY_STATE="pass"
  FLYWAY_DETAIL="version=${DST_VERSION}, ${DST_APPLIED} applied"
  log "flyway validate passed in ${FLYWAY_MS} ms (server booted on ${VALIDATION_PORT})"
fi

# ---------------------------------------------------------------------------
# 6. Retrieval spot check                                            exit 7
# ---------------------------------------------------------------------------
RETRIEVAL_START=$(ms_now)
SRC_HITS=$(psql_run "$SOURCE_DB" \
  "SELECT count(*) FROM mem_observations WHERE search_tokens @@ plainto_tsquery('simple', '${ZH_QUERY}');") \
  || fail 7 retrieval "the retrieval query failed on the source"
[ "$SRC_HITS" -gt 0 ] || fail 7 retrieval \
  "source query '${ZH_QUERY}' has no hits; pick another ZH_QUERY"
DST_HITS=$(psql_run "$RESTORE_DB" \
  "SELECT count(*) FROM mem_observations WHERE search_tokens @@ plainto_tsquery('simple', '${ZH_QUERY}');") \
  || fail 7 retrieval "the retrieval query failed on the restore"
[ "$SRC_HITS" = "$DST_HITS" ] || fail 7 retrieval \
  "query '${ZH_QUERY}': source=${SRC_HITS} restore=${DST_HITS}"
log "chinese query '${ZH_QUERY}': ${DST_HITS} hits (match)"

PROBE_ID=$(psql_run "$SOURCE_DB" \
  "SELECT id FROM mem_observations WHERE search_tokens @@ plainto_tsquery('simple', '${ZH_QUERY}') \
   ORDER BY created_at_epoch DESC LIMIT 1;")
PROBE_TITLE=$(psql_run "$RESTORE_DB" "SELECT title FROM mem_observations WHERE id='${PROBE_ID}';")
[ -n "$PROBE_TITLE" ] || fail 7 retrieval "probe observation ${PROBE_ID} missing from the restored copy"
RETRIEVAL_MS=$(elapsed "$RETRIEVAL_START")
log "probe observation ${PROBE_ID} title restored: ${PROBE_TITLE}"

# ---------------------------------------------------------------------------
# 7. Data-integrity audit on the restored copy (optional)            exit 10
#
# Restoring proves the bytes are there; the audit proves the restored rows still
# satisfy the invariants the application relies on. It is read-only, so a bad
# restore reports without changing the copy.
# ---------------------------------------------------------------------------
if [ "$INTEGRITY_AUDIT" = "1" ]; then
  mkdir -p "$(dirname "$AUDIT_JSON")" 2>/dev/null || true
  AUDIT_LOG="${AUDIT_JSON%.json}.log"
  INTEGRITY_START=$(ms_now)
  set +e
  python3 "$ROOT/scripts/data-integrity-audit.py" --database "$RESTORE_DB" \
    --container "$CONTAINER" --user "$DB_USER" --json "$AUDIT_JSON" > "$AUDIT_LOG" 2>&1
  AUDIT_CODE=$?
  set -e
  INTEGRITY_MS=$(elapsed "$INTEGRITY_START")
  INTEGRITY_JSON="$AUDIT_JSON"
  if [ "$AUDIT_CODE" = "0" ]; then
    INTEGRITY_STATE="pass"
    rm -f "$AUDIT_LOG"
    log "integrity audit passed in ${INTEGRITY_MS} ms"
  elif [ "$AUDIT_CODE" = "1" ]; then
    INTEGRITY_STATE="findings"
    INTEGRITY_FINDINGS="$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['findings'])" "$AUDIT_JSON" 2>/dev/null || echo "?")"
    tail -n 30 "$AUDIT_LOG" >&2 || true
    fail 10 integrity \
      "the restored copy has ${INTEGRITY_FINDINGS} integrity finding(s); see ${AUDIT_JSON} (log ${AUDIT_LOG})"
  else
    INTEGRITY_STATE="error"
    tail -n 20 "$AUDIT_LOG" >&2 || true
    fail 10 integrity "the integrity audit could not inspect ${RESTORE_DB}; see ${AUDIT_LOG}"
  fi
fi

# ---------------------------------------------------------------------------
# 8. Verdict — on_exit writes the report, drops the copy and exits
# ---------------------------------------------------------------------------
exit 0
