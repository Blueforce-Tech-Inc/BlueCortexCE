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
# The script never touches a production database: it refuses to run unless the target
# database name ends with a rehearsal suffix, and it drops that database each cycle.
#
# Usage:
#   scripts/db/rehearse-conversation-scope.sh \
#       --container memscope-v20 --database v20 --migration-dir backend/src/main/resources/db/migration
#
# Exit code is 0 only when every expectation holds.

set -uo pipefail

CONTAINER="${CONTAINER:-memscope-v20}"
DATABASE="${DATABASE:-v20}"
MIGRATION_DIR="${MIGRATION_DIR:-backend/src/main/resources/db/migration}"
BASE_FIXTURE="${BASE_FIXTURE:-scripts/db/conversation-scope-fixture.sql}"
CONFLICT_FIXTURE="${CONFLICT_FIXTURE:-scripts/db/conversation-scope-conflict-fixture.sql}"
PGUSER="${PGUSER:-postgres}"

while [ $# -gt 0 ]; do
  case "$1" in
    --container) CONTAINER="$2"; shift 2 ;;
    --database)  DATABASE="$2"; shift 2 ;;
    --migration-dir) MIGRATION_DIR="$2"; shift 2 ;;
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

PASS=0
FAIL=0

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\n== %s\n' "$1"; }

psql_db() { docker exec -i "$CONTAINER" psql -U "$PGUSER" -d "$DATABASE" "$@"; }
scalar()  { psql_db -X -tA -c "$1" 2>/dev/null | tr -d '[:space:]'; }

recreate() {
  docker exec "$CONTAINER" psql -U "$PGUSER" -d postgres -q \
    -c "DROP DATABASE IF EXISTS $DATABASE" -c "CREATE DATABASE $DATABASE" >/dev/null 2>&1
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
recreate
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
recreate; apply_pre_v20
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
recreate; apply_pre_v20
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

printf '\n== RESULT: %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
