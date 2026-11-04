#!/usr/bin/env bash
#
# Migration preflight: size the next schema migration before touching production.
#
# The dangerous migration shape in this service is "add a generated column and
# index it" (V24): the ADD COLUMN is a full table rewrite that holds ACCESS
# EXCLUSIVE and stops reads and writes, while a plain CREATE INDEX holds SHARE
# and stops writes. This script estimates both plans from the actual row count,
# compares them with the calibrated cost model, and states which plan the current
# database can afford. It does not run the migration.
#
# Cost model calibrated on a 100,000-row database at schema V25 on 2026-11-04
# (docs/archive/2026-11-04_migration-cost-100k.md):
#   full rewrite (ADD COLUMN ... GENERATED ... STORED)   6,657 ms / 100k rows
#   plain GIN build                                        998 ms / 100k rows
#   plain column add                                       0.89 ms (constant)
#   phased backfill (10k batches)                       10,189 ms / 100k rows
#   CREATE INDEX CONCURRENTLY                            1,400 ms / 100k rows
#
# Run --calibrate on a throwaway database to measure the rewrite and index cost
# on this host and verify the model is within +/-20%. Calibration creates and
# drops a scratch table; never point it at production.
#
# Usage:
#   scripts/migration-preflight.sh --database-url jdbc:postgresql://host:5432/db \
#       --user postgres --password secret [--table mem_observations] [--calibrate]
#
# Connection can also come from PGURL / SPRING_DATASOURCE_URL and PGUSER /
# PGPASSWORD / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD.

set -euo pipefail

DB_URL="${PGURL:-${SPRING_DATASOURCE_URL:-}}"
DB_USER="${PGUSER:-${SPRING_DATASOURCE_USERNAME:-}}"
DB_PASSWORD="${PGPASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
TABLE="mem_observations"
CALIBRATE=0
CALIBRATE_ROWS=100000
BLOCK_THRESHOLD_SECONDS=30
HARD_THRESHOLD_SECONDS=300

# Model coefficients: milliseconds per 100,000 rows, on the V25 index set.
REWRITE_MS_PER_100K=6657
PLAIN_INDEX_MS_PER_100K=998
PHASED_BACKFILL_MS_PER_100K=10189
CONCURRENT_INDEX_MS_PER_100K=1400

usage() {
  sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --database-url) DB_URL="$2"; shift 2 ;;
    --user) DB_USER="$2"; shift 2 ;;
    --password) DB_PASSWORD="$2"; shift 2 ;;
    --table) TABLE="$2"; shift 2 ;;
    --calibrate) CALIBRATE=1; shift ;;
    --calibrate-rows) CALIBRATE_ROWS="$2"; shift 2 ;;
    --block-threshold-seconds) BLOCK_THRESHOLD_SECONDS="$2"; shift 2 ;;
    --hard-threshold-seconds) HARD_THRESHOLD_SECONDS="$2"; shift 2 ;;
    -h|--help) usage 0 ;;
    *) echo "unknown argument: $1" >&2; usage 1 ;;
  esac
done

if [[ -z "$DB_URL" ]]; then
  echo "error: no database URL. Pass --database-url or set PGURL / SPRING_DATASOURCE_URL." >&2
  exit 1
fi

command -v psql >/dev/null || { echo "error: psql not found" >&2; exit 1; }

PSQL_ARGS=(-v ON_ERROR_STOP=1 -d "$DB_URL" -At)
if [[ -n "$DB_USER" ]]; then PSQL_ARGS+=(-U "$DB_USER"); fi
export PGPASSWORD="$DB_PASSWORD"

psql_run() { psql "${PSQL_ARGS[@]}" -c "$1"; }

now_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }

fail() { echo "error: $*" >&2; exit 1; }

serialize_url() {
  # psql accepts a jdbc: URL only after the scheme is removed; keep the original
  # for JDBC users and normalise once here.
  local url="$1"
  echo "${url#jdbc:}"
}

DB_URL="$(serialize_url "$DB_URL")"

server_version="$(psql_run "SHOW server_version")"
current_version="$(psql_run "SELECT coalesce((SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1), 'none')" 2>/dev/null || echo "no-history")"
row_count="$(psql_run "SELECT count(*) FROM ${TABLE}")"
[[ "$row_count" =~ ^[0-9]+$ ]] || fail "could not count ${TABLE}: ${row_count}"

sizes="$(psql_run "SELECT pg_total_relation_size('${TABLE}'::regclass) || ' ' || pg_relation_size('${TABLE}'::regclass) || ' ' || pg_indexes_size('${TABLE}'::regclass)")"
read -r total_bytes heap_bytes index_bytes <<<"$sizes"

to_mb() { awk -v b="$1" 'BEGIN { printf "%.1f", b / 1048576 }'; }

# Estimates, in milliseconds.
est_rewrite_ms=$(( row_count * REWRITE_MS_PER_100K / 100000 ))
est_plain_index_ms=$(( row_count * PLAIN_INDEX_MS_PER_100K / 100000 ))
est_blocking_full_ms=$(( est_rewrite_ms + est_plain_index_ms ))
est_phased_ms=$(( row_count * PHASED_BACKFILL_MS_PER_100K / 100000 + row_count * CONCURRENT_INDEX_MS_PER_100K / 100000 + 1 ))
est_blocking_phased_ms=1

printf 'Migration preflight\n'
printf '  database        : %s\n' "$DB_URL"
printf '  server          : %s\n' "$server_version"
printf '  flyway version  : %s\n' "$current_version"
printf '  table           : %s\n' "$TABLE"
printf '  rows            : %s\n' "$row_count"
printf '  size            : %s MB total (%s MB heap, %s MB indexes)\n' \
  "$(to_mb "$total_bytes")" "$(to_mb "$heap_bytes")" "$(to_mb "$index_bytes")"
printf '\n'
printf 'Cost model (calibrated at 100k rows)\n'
printf '  full rewrite    : %d ms rewrite + %d ms plain index = %d ms blocked access\n' \
  "$est_rewrite_ms" "$est_plain_index_ms" "$est_blocking_full_ms"
printf '  phased online   : %d ms total, longest block %d ms (metadata-only ADD COLUMN)\n' \
  "$est_phased_ms" "$est_blocking_phased_ms"
printf '\n'

blocking_seconds=$(( est_blocking_full_ms / 1000 ))

recommendation() {
  if (( est_blocking_full_ms <= BLOCK_THRESHOLD_SECONDS * 1000 )); then
    echo "DIRECT: the exact migration blocks access for ${blocking_seconds}s, at or below the"
    echo "  ${BLOCK_THRESHOLD_SECONDS}s policy threshold. Schedule a short maintenance window and run V24/V25 as written."
  elif (( est_blocking_full_ms <= HARD_THRESHOLD_SECONDS * 1000 )); then
    echo "MAINTENANCE WINDOW REQUIRED: the exact migration blocks reads and writes for ${blocking_seconds}s."
    echo "  Announce a maintenance window of at least $(( blocking_seconds * 2 ))s (migration + verification),"
    echo "  or use the phased plan below to avoid the window entirely."
  else
    echo "PHASED ONLINE REQUIRED: the exact migration blocks access for ${blocking_seconds}s, above the"
    echo "  ${HARD_THRESHOLD_SECONDS}s hard limit. Do not run it in a rolling deploy. Use the phased plan:"
    echo "    plain nullable column, batched backfill, CREATE INDEX CONCURRENTLY, then VACUUM ANALYZE."
  fi
}

printf 'Recommendation\n'
recommendation
printf '\n'
printf '  - If flyway version < 24: V24 (chinese lexical search) is still pending and has this shape.\n'
printf '  - If flyway version >= 24: the schema is already at the generated-column shape; apply the\n'
printf '    same decision to the next migration that rewrites this table.\n'

if (( CALIBRATE == 1 )); then
  printf '\nCalibration on %s rows (throwaway database only)\n' "$CALIBRATE_ROWS"
  psql_run "DROP TABLE IF EXISTS _migration_preflight_calibration" >/dev/null

  if (( row_count > 0 )); then
    # Clone the real table with all of its indexes and copy real rows into it.
    # The rewrite rebuilds every index, so a scratch table with only a primary
    # key under-reports the cost by 3x (measured: 1.3 s vs 4.3 s at 100k rows).
    psql_run "CREATE TABLE _migration_preflight_calibration (LIKE ${TABLE} INCLUDING ALL)" >/dev/null
    psql_run "ALTER TABLE _migration_preflight_calibration DROP COLUMN IF EXISTS search_tokens" >/dev/null
    copy_columns="$(psql_run "SELECT string_agg(format('%I', column_name), ',')
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = '${TABLE}'
          AND is_generated = 'NEVER' AND column_name <> 'search_tokens'")"
    [[ -n "$copy_columns" ]] || fail "could not derive the copyable columns of ${TABLE}"
    copy_rows=$(( row_count < CALIBRATE_ROWS ? row_count : CALIBRATE_ROWS ))
    psql_run "INSERT INTO _migration_preflight_calibration (${copy_columns})
        SELECT ${copy_columns} FROM ${TABLE} LIMIT ${copy_rows}" >/dev/null
  else
    # Empty database: synthesise rows so the estimator still gets a reading.
    copy_rows=$CALIBRATE_ROWS
    psql_run "CREATE TABLE _migration_preflight_calibration (
        id bigserial PRIMARY KEY,
        title text NOT NULL DEFAULT '',
        content text NOT NULL DEFAULT '',
        files_modified text[] NOT NULL DEFAULT '{}'
    )" >/dev/null
    psql_run "INSERT INTO _migration_preflight_calibration (title, content)
        SELECT 'calibration title ' || g, repeat('中文内容 calibration ', 10)
        FROM generate_series(1, ${copy_rows}) AS g" >/dev/null
  fi
  psql_run "ANALYZE _migration_preflight_calibration" >/dev/null

  # Reproduce the path V24 measured: a weighted tsvector over the three text
  # columns the observation carries.
  has_cortex_tokens=0
  if [[ "$(psql_run "SELECT to_regprocedure('cortex_tokens(text)') IS NOT NULL")" == "t" ]]; then
    has_cortex_tokens=1
  fi
  tokenize() {
    if (( has_cortex_tokens == 1 )); then
      printf 'cortex_tokens(%s)' "$1"
    else
      printf '%s' "$1"
    fi
  }
  started=$(now_ms)
  psql_run "ALTER TABLE _migration_preflight_calibration
      ADD COLUMN search_tokens tsvector
      GENERATED ALWAYS AS (
          setweight(to_tsvector('simple', $(tokenize "coalesce(title, '')")), 'A') ||
          setweight(to_tsvector('simple', $(tokenize "coalesce(content, '')")), 'B') ||
          setweight(to_tsvector('simple', $(tokenize "coalesce(files_modified::text, '')")), 'C')
      ) STORED" >/dev/null
  rewrite_ms=$(( $(now_ms) - started ))

  started=$(now_ms)
  psql_run "CREATE INDEX _migration_preflight_cal_idx
      ON _migration_preflight_calibration USING gin (search_tokens)" >/dev/null
  index_ms=$(( $(now_ms) - started ))

  # Reset to the phased shape: plain column, batched backfill, concurrent index.
  psql_run "DROP INDEX IF EXISTS _migration_preflight_cal_idx" >/dev/null
  psql_run "ALTER TABLE _migration_preflight_calibration DROP COLUMN search_tokens" >/dev/null
  started=$(now_ms)
  psql_run "ALTER TABLE _migration_preflight_calibration ADD COLUMN search_tokens tsvector" >/dev/null
  plain_add_ms=$(( $(now_ms) - started ))

  backfill_expr="setweight(to_tsvector('simple', $(tokenize "coalesce(title, '')")), 'A')
      || setweight(to_tsvector('simple', $(tokenize "coalesce(content, '')")), 'B')
      || setweight(to_tsvector('simple', $(tokenize "coalesce(files_modified::text, '')")), 'C')"
  batch=$(( (copy_rows + 9) / 10 ))
  started=$(now_ms)
  offset=0
  while (( offset < copy_rows )); do
    psql_run "UPDATE _migration_preflight_calibration
        SET search_tokens = ${backfill_expr}
        WHERE id IN (
            SELECT id FROM _migration_preflight_calibration ORDER BY id
            OFFSET ${offset} LIMIT ${batch}
        )" >/dev/null
    offset=$(( offset + batch ))
  done
  backfill_ms=$(( $(now_ms) - started ))

  started=$(now_ms)
  psql_run "CREATE INDEX CONCURRENTLY _migration_preflight_cal_idx
      ON _migration_preflight_calibration USING gin (search_tokens)" >/dev/null
  concurrent_index_ms=$(( $(now_ms) - started ))

  psql_run "DROP TABLE _migration_preflight_calibration" >/dev/null

  measured_full_ms=$(( rewrite_ms + index_ms ))
  expected_full_ms=$(( copy_rows * REWRITE_MS_PER_100K / 100000 + copy_rows * PLAIN_INDEX_MS_PER_100K / 100000 ))
  measured_phased_ms=$(( plain_add_ms + backfill_ms + concurrent_index_ms ))
  expected_phased_ms=$(( copy_rows * PHASED_BACKFILL_MS_PER_100K / 100000 + copy_rows * CONCURRENT_INDEX_MS_PER_100K / 100000 + 1 ))

  full_error_pct=$(awk -v m="$measured_full_ms" -v e="$expected_full_ms" \
    'BEGIN { if (m == 0) { print 0 } else { printf "%.1f", (e - m) * 100 / m } }')
  phased_error_pct=$(awk -v m="$measured_phased_ms" -v e="$expected_phased_ms" \
    'BEGIN { if (m == 0) { print 0 } else { printf "%.1f", (e - m) * 100 / m } }')

  printf '  rows cloned     : %d\n' "$copy_rows"
  printf '  full rewrite    : measured %d ms, model %d ms, error %s%%\n' \
    "$measured_full_ms" "$expected_full_ms" "$full_error_pct"
  printf '  phased online   : measured %d ms (add %d + backfill %d + concurrent index %d), model %d ms, error %s%%\n' \
    "$measured_phased_ms" "$plain_add_ms" "$backfill_ms" "$concurrent_index_ms" \
    "$expected_phased_ms" "$phased_error_pct"

  if awk -v e="$full_error_pct" 'BEGIN { exit (e < -20 || e > 20) ? 0 : 1 }' \
     || awk -v e="$phased_error_pct" 'BEGIN { exit (e < -20 || e > 20) ? 0 : 1 }'; then
    fail "cost model is outside the +/-20% band on this host; recalibrate the coefficients"
  fi
  echo "  calibration passed"
fi
