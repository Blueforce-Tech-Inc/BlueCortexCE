#!/usr/bin/env bash
#
# Rehearse the one-shot V20 migration on an isolated PostgreSQL database.
#
# What it proves, in order:
#   1. V1-V19 replay from empty.
#   2. V20 succeeds in ONE transaction against realistic clean data, and leaves the
#      expected invariants (every observation scoped, standalone/conversation XOR).
#   3. With the conflict fixture loaded, V20 ABORTS and rolls back completely: the
#      new tables must not exist afterwards, and no data may have been rewritten.
#   4. Re-running V20 on the already-migrated database is rejected by the baseline
#      guard rather than half-applied.
#
# Point this only at a disposable PostgreSQL instance. The script refuses an existing
# target database, creates the target itself, and only drops/recreates that database
# after it has successfully created it during this run.
#
# Usage:
#   scripts/db/rehearse-conversation-scope.sh \
#       --container memscope-v20 --database v20_review_20261023_rehearsal --migration-dir backend/src/main/resources/db/migration
#   scripts/db/rehearse-conversation-scope.sh \
#       --container memscope-v20 --database v20_scale_rehearsal --scale-observations 100000
#
# Exit code is 0 only when every expectation holds.

set -uo pipefail
export LC_ALL=C

CONTAINER="${CONTAINER:-memscope-v20}"
DATABASE="${DATABASE:-v20_rehearsal_$(date +%s)_$$}"
MIGRATION_DIR="${MIGRATION_DIR:-backend/src/main/resources/db/migration}"
BASE_FIXTURE="${BASE_FIXTURE:-scripts/db/conversation-scope-fixture.sql}"
CONFLICT_FIXTURE="${CONFLICT_FIXTURE:-scripts/db/conversation-scope-conflict-fixture.sql}"
PGUSER="${PGUSER:-postgres}"
SCALE_OBSERVATIONS=0

while [ $# -gt 0 ]; do
  case "$1" in
    --container) CONTAINER="$2"; shift 2 ;;
    --database)  DATABASE="$2"; shift 2 ;;
    --migration-dir) MIGRATION_DIR="$2"; shift 2 ;;
    --scale-observations) SCALE_OBSERVATIONS="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

case "$DATABASE" in
  *rehearsal*|v20|memcopy_test*|*_copy) ;;
  *)
    echo "REFUSING: database '$DATABASE' does not look like a rehearsal target." >&2
    echo "Use a name containing 'rehearsal', or one of: v20, *_copy." >&2
    exit 2
    ;;
esac
case "$DATABASE" in
  ''|*[!a-zA-Z0-9_]*)
    echo "REFUSING: database names may contain only ASCII letters, digits, and underscores." >&2
    exit 2
    ;;
esac
if [ "${#DATABASE}" -gt 63 ]; then
  echo "REFUSING: database names must be at most 63 ASCII characters." >&2
  exit 2
fi

case "$SCALE_OBSERVATIONS" in
  ''|*[!0-9]*) echo "REFUSING: --scale-observations must be a non-negative integer." >&2; exit 2 ;;
esac
if [ "$SCALE_OBSERVATIONS" -gt 1000000 ]; then
  echo "REFUSING: --scale-observations is capped at 1000000." >&2
  exit 2
fi
if [ "$SCALE_OBSERVATIONS" -gt 0 ]; then
  case "$DATABASE" in
    *_rehearsal) ;;
    *) echo "REFUSING: scale runs require a database name ending in _rehearsal." >&2; exit 2 ;;
  esac
fi

admin_psql() {
  docker exec -i "$CONTAINER" psql -U "$PGUSER" -d postgres -v "database=$DATABASE" "$@"
}

if ! EXISTS=$(admin_psql -X -tA -f - <<'SQL'
SELECT 1 FROM pg_database WHERE datname = :'database';
SQL
); then
  echo "REFUSING: could not verify whether target database '$DATABASE' already exists." >&2
  exit 2
fi
if [ "$EXISTS" = "1" ]; then
  echo "REFUSING: target database '$DATABASE' already exists; choose a new disposable name." >&2
  exit 2
fi

PASS=0
FAIL=0

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\n== %s\n' "$1"; }

psql_db() { docker exec -i "$CONTAINER" psql -U "$PGUSER" -d "$DATABASE" "$@"; }
scalar()  { psql_db -X -tA -c "$1" 2>/dev/null | tr -d '[:space:]'; }

DATABASE_CREATED=0
recreate() {
  if [ "$DATABASE_CREATED" -eq 0 ]; then
    if ! admin_psql -q -f - >/dev/null 2>&1 <<'SQL'
CREATE DATABASE :"database";
SQL
    then
      return 1
    fi
    DATABASE_CREATED=1
    return 0
  fi

  admin_psql -q -f - >/dev/null 2>&1 <<'SQL'
DROP DATABASE :"database";
CREATE DATABASE :"database";
SQL
}

apply_pre_v20() {
  local f
  for f in $(ls "$MIGRATION_DIR"/V*.sql | sort -V | grep -v 'V20'); do
    cat "$f" | psql_db -q -v ON_ERROR_STOP=1 >/dev/null 2>&1 || {
      echo "failed applying $(basename "$f")" >&2; return 1; }
  done
}

apply_v20() {
  # V20 deliberately contains no transaction-control or psql-only statements: Flyway
  # owns the JDBC transaction. The rehearsal supplies the transaction wrapper so the
  # raw-psql execution has the same all-or-nothing failure behaviour.
  { echo "BEGIN;"
    echo "SET LOCAL app.manual_sentinel_reviewed = 'true';"
    cat "$MIGRATION_DIR"/V20*.sql
    echo "COMMIT;"
    echo "SELECT 'V20 one-shot migration committed.';"; } |
    psql_db -v ON_ERROR_STOP=1 2>&1
}

info "1. V1-V19 replay from empty"
if ! recreate; then
  echo "failed creating target database '$DATABASE'" >&2
  exit 2
fi
if apply_pre_v20; then ok "V1-V19 applied"; else bad "V1-V19 applied"; fi
[ "$(scalar "select count(*) from mem_sessions")" = "0" ] \
  && ok "baseline schema is empty" || bad "baseline schema is empty"

info "2. V20 succeeds on clean data"
psql_db -q -v ON_ERROR_STOP=1 -f - < "$BASE_FIXTURE" >/dev/null 2>&1 \
  && ok "base fixture loaded" || bad "base fixture loaded"
OUT=$(apply_v20)
if echo "$OUT" | grep -q 'V20 one-shot migration committed'; then
  ok "V20 committed in one transaction"
else
  bad "V20 committed in one transaction"
  echo "$OUT" | tail -5
fi

TOTAL=$(scalar "select count(*) from mem_observations")
SCOPED=$(scalar "select count(memory_scope_id) from mem_observations")
[ "$TOTAL" = "$SCOPED" ] && [ "$TOTAL" != "0" ] \
  && ok "every observation ($TOTAL) has a memory scope" \
  || bad "every observation has a memory scope (got $SCOPED/$TOTAL)"

BOTH=$(scalar "select count(*) from mem_observations where conversation_id is not null and standalone_kind is not null")
NEITHER=$(scalar "select count(*) from mem_observations where conversation_id is null and standalone_kind is null")
[ "$BOTH" = "0" ] && [ "$NEITHER" = "0" ] \
  && ok "conversation/standalone relation is exclusive and total" \
  || bad "relation XOR (both=$BOTH neither=$NEITHER)"

KINDS=$(scalar "select count(distinct coalesce(standalone_kind,'conv')) from mem_observations")
[ "$KINDS" -ge 2 ] && ok "standalone kinds were classified ($KINDS distinct)" \
  || bad "standalone kinds were classified"

MANUAL_SRC=$(scalar "select count(*) from mem_observations where standalone_kind='manual' and source<>'manual'")
[ "$MANUAL_SRC" = "0" ] && ok "every manual row carries source='manual'" \
  || bad "manual rows carry source='manual'"

LEGACY_UNIQUE=$(scalar "select count(*) from pg_constraint where conrelid='mem_sessions'::regclass and contype='u' and pg_get_constraintdef(oid)='UNIQUE (content_session_id)'")
[ "$LEGACY_UNIQUE" = "0" ] && ok "legacy single-column UNIQUE removed" \
  || bad "legacy single-column UNIQUE removed"

COMPOSITE=$(scalar "select count(*) from pg_indexes where indexname='ux_sessions_platform_content'")
[ "$COMPOSITE" = "1" ] && ok "composite (platform, external id) uniqueness exists" \
  || bad "composite uniqueness exists"

ORPHAN_FEEDBACK=$(scalar "select count(*) from observation_feedback f left join mem_sessions s on s.id=f.session_db_id where f.session_db_id is not null and s.id is null")
[ "$ORPHAN_FEEDBACK" = "0" ] && ok "no dangling feedback session references" \
  || bad "no dangling feedback session references"

info "3. Cross-platform duplicate external id is now legal"
psql_db -q -v ON_ERROR_STOP=1 -c "
  INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status, started_at_epoch)
  VALUES ('e0000000-0000-0000-0000-000000000001','dup-ext','/work/x','claude','completed',1),
         ('e0000000-0000-0000-0000-000000000002','dup-ext','/work/x','codex','completed',2)
" >/dev/null 2>&1 && ok "same external id on two platforms coexists" \
                   || bad "same external id on two platforms coexists"
DUP=$(scalar "select count(*) from mem_sessions where content_session_id='dup-ext'")
[ "$DUP" = "2" ] && ok "duplicate-id rows retained (2)" || bad "duplicate-id rows retained (got $DUP)"

info "4. V20 refuses to run twice (baseline guard)"
recreate || { echo "failed recreating owned rehearsal database '$DATABASE'" >&2; exit 2; }
apply_pre_v20
psql_db -q -v ON_ERROR_STOP=1 -f - < "$BASE_FIXTURE" >/dev/null 2>&1
apply_v20 >/dev/null 2>&1
OUT2=$(apply_v20)
if echo "$OUT2" | grep -q "expects the legacy UNIQUE"; then
  ok "second application rejected by the baseline guard"
else
  bad "second application rejected by the baseline guard"
  echo "$OUT2" | grep -i error | head -3
fi

info "5. Conflict fixture aborts the migration and rolls back completely"
recreate || { echo "failed recreating owned rehearsal database '$DATABASE'" >&2; exit 2; }
apply_pre_v20
psql_db -q -v ON_ERROR_STOP=1 -f - < "$BASE_FIXTURE" >/dev/null 2>&1
psql_db -q -v ON_ERROR_STOP=1 -f - < "$CONFLICT_FIXTURE" >/dev/null 2>&1 \
  && ok "conflict fixture loaded" || bad "conflict fixture loaded"

BEFORE_OBS=$(scalar "select count(*) from mem_observations")
BEFORE_SRC=$(scalar "select count(*) from mem_observations where source='manual'")
OUT3=$(apply_v20)
if echo "$OUT3" | grep -qi 'ERROR'; then
  ok "migration aborted on conflicting data"
else
  bad "migration aborted on conflicting data"
  echo "$OUT3" | tail -5
fi
echo "$OUT3" | grep -qiE 'PLATFORM_SOURCE_NOT_NORMALISABLE|TECHNICAL_BUCKET_CONFLICT|LEGACY_SENTINEL_IDENTITY_AMBIGUITY|MANUAL_' \
  && ok "abort names the conflict class" || bad "abort names the conflict class"

NEW_TABLES=$(scalar "select count(*) from information_schema.tables where table_name in ('mem_memory_scopes','mem_identity_flags','mem_migration_note')")
[ "$NEW_TABLES" = "0" ] && ok "rollback removed every new table (0)" \
                         || bad "rollback removed every new table (found $NEW_TABLES)"

NEW_COLS=$(scalar "select count(*) from information_schema.columns where table_name='mem_observations' and column_name in ('memory_scope_id','conversation_id','standalone_kind')")
[ "$NEW_COLS" = "0" ] && ok "rollback removed every new column (0)" \
                       || bad "rollback removed every new column (found $NEW_COLS)"

AFTER_OBS=$(scalar "select count(*) from mem_observations")
AFTER_SRC=$(scalar "select count(*) from mem_observations where source='manual'")
[ "$BEFORE_OBS" = "$AFTER_OBS" ] && ok "observation row count unchanged ($AFTER_OBS)" \
                                  || bad "observation row count unchanged ($BEFORE_OBS -> $AFTER_OBS)"
[ "$BEFORE_SRC" = "$AFTER_SRC" ] && ok "no source rewritten during the aborted run" \
                                  || bad "no source rewritten during the aborted run ($BEFORE_SRC -> $AFTER_SRC)"

if [ "$SCALE_OBSERVATIONS" -gt 0 ]; then
  info "6. V20 representative-volume rehearsal ($SCALE_OBSERVATIONS observations)"
  recreate || { echo "failed recreating owned rehearsal database '$DATABASE'" >&2; exit 2; }
  apply_pre_v20 || { bad "V1-V19 applied for scale rehearsal"; }
  psql_db -q -v ON_ERROR_STOP=1 -f - < "$BASE_FIXTURE" >/dev/null 2>&1 \
    && ok "base fixture loaded for scale rehearsal" || bad "base fixture loaded for scale rehearsal"

  SCALE_SESSIONS=$(((SCALE_OBSERVATIONS + 9) / 10))
  psql_db -v ON_ERROR_STOP=1 -v scale_observations="$SCALE_OBSERVATIONS" \
    -v scale_sessions="$SCALE_SESSIONS" <<'SQL' >/dev/null \
    && ok "representative rows seeded ($SCALE_SESSIONS sessions)" \
    || bad "representative rows seeded"
BEGIN;
INSERT INTO mem_sessions (id, content_session_id, project_path, platform_source, status, started_at_epoch)
SELECT md5('v20-scale-session-row-' || n)::uuid,
       'v20-scale-session-' || lpad(n::text, 8, '0'),
       '/scale/project-' || (n % 100)::text,
       CASE WHEN n % 2 = 0 THEN 'claude' ELSE 'codex' END,
       'completed', 1800000000000 + n
FROM generate_series(1, :scale_sessions) AS rows(n);

INSERT INTO mem_observations (content_session_id, project_path, platform_source, type, title,
                              content, prompt_number, created_at_epoch, source)
SELECT 'v20-scale-session-' || lpad((((n - 1) / 10) + 1)::text, 8, '0'),
       '/scale/project-' || ((n / 1000) % 100)::text,
       'claude', 'feature', 'Scale observation ' || n,
       'Synthetic migration rehearsal observation ' || n,
       (n % 30)::integer, 1800000000000 + n, 'tool_result'
FROM generate_series(1, :scale_observations) AS rows(n);

INSERT INTO mem_summaries (content_session_id, project_path, platform_source, request, completed,
                           prompt_number, created_at_epoch)
SELECT 'v20-scale-session-' || lpad(n::text, 8, '0'),
       '/scale/project-' || (n % 100)::text, 'claude', 'scale request', 'scale completed',
       1, 1800000000000 + n
FROM generate_series(1, :scale_sessions) AS rows(n);

INSERT INTO mem_user_prompts (content_session_id, project_path, platform_source, prompt_number,
                              prompt_text, created_at_epoch)
SELECT 'v20-scale-session-' || lpad(n::text, 8, '0'),
       '/scale/project-' || (n % 100)::text, 'claude', 1,
       'Synthetic prompt ' || n, 1800000000000 + n
FROM generate_series(1, :scale_sessions) AS rows(n);

INSERT INTO mem_pending_messages (id, session_db_id, content_session_id, message_type, status,
                                  retry_count, created_at_epoch)
SELECT md5('v20-scale-pending-' || n)::uuid,
       md5('v20-scale-session-row-' || n)::uuid,
       'v20-scale-session-' || lpad(n::text, 8, '0'),
       'observation', 'processed', 0, 1800000000000 + n
FROM generate_series(1, :scale_sessions) AS rows(n);
COMMIT;
SQL

  BEFORE_OBS=$(scalar "select count(*) from mem_observations")
  BEFORE_SIZE=$(scalar "select pg_database_size(current_database())")
  STARTED_AT=$(scalar "select extract(epoch from clock_timestamp())")
  OUT4=$(apply_v20)
  FINISHED_AT=$(scalar "select extract(epoch from clock_timestamp())")
  ELAPSED=$(scalar "select round(($FINISHED_AT::numeric - $STARTED_AT::numeric), 3)")
  AFTER_SIZE=$(scalar "select pg_database_size(current_database())")
  if echo "$OUT4" | grep -q 'V20 one-shot migration committed'; then
    ok "V20 committed on representative volume in ${ELAPSED}s"
  else
    bad "V20 committed on representative volume"
    echo "$OUT4" | tail -5
  fi

  AFTER_OBS=$(scalar "select count(*) from mem_observations")
  SCOPED=$(scalar "select count(memory_scope_id) from mem_observations")
  SCALE_SCOPED=$(scalar "select count(*) from mem_observations where title like 'Scale observation %' and memory_scope_id is not null and conversation_id is not null")
  [ "$BEFORE_OBS" = "$AFTER_OBS" ] && [ "$AFTER_OBS" = "$SCOPED" ] \
    && ok "all observations retained and scoped ($AFTER_OBS)" \
    || bad "all observations retained and scoped (before=$BEFORE_OBS after=$AFTER_OBS scoped=$SCOPED)"
  [ "$SCALE_SCOPED" = "$SCALE_OBSERVATIONS" ] \
    && ok "all scale observations belong to Conversations ($SCALE_SCOPED)" \
    || bad "all scale observations belong to Conversations ($SCALE_SCOPED/$SCALE_OBSERVATIONS)"
  printf '  Database size: %.1f MiB before, %.1f MiB after migration\n' \
    "$(awk "BEGIN {print $BEFORE_SIZE / 1048576}")" \
    "$(awk "BEGIN {print $AFTER_SIZE / 1048576}")"
fi

printf '\n== RESULT: %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
