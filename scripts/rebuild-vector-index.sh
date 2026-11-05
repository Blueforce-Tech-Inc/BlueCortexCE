#!/usr/bin/env bash
# =============================================================================
# Rebuild the vector index on a live database — the "load bulk rows first, build
# the graph once" path, made re-runnable.
#
# Why this exists: feeding vectors into an existing HNSW graph costs ~19–26 ms
# per row at 1M rows (docs/archive/2026-11-05_write-path-scale.md §7), while a
# bulk load without the index runs at 900–1,300 rows/s and the graph then builds
# in one pass at 36.9 s / 100k and 801.6 s / 1M. The catch is that a rebuild is
# an interruption-prone operation, so this script defines what happens when it
# is interrupted and when it is run again:
#
#   * the index is valid and FORCE=0        -> nothing to do, exit 0 (idempotent)
#   * the index exists but is invalid       -> an interrupted build left it
#                                              behind; it is dropped and rebuilt
#   * no index                            -> build
#   * the build fails mid-way             -> the partial index is dropped so the
#                                              next run starts clean
#
# Concurrent writes: CONCURRENTLY=1 (the default) keeps INSERTs flowing while the
# graph is built; the build takes longer and writes are slower, but nothing is
# rejected. CONCURRENTLY=0 takes a SHARE lock: faster, but every write blocks
# (and the import path's calls wait instead of failing) until it finishes.
#
# Usage:
#   SOURCE_DB=claude_mem scripts/rebuild-vector-index.sh
#   SOURCE_DB=claude_mem FORCE=1 WORK_MEM=8GB scripts/rebuild-vector-index.sh
#
# Environment:
#   CONTAINER     postgres container (default claude-mem-test-pg)
#   DB_USER       role (default postgres)
#   SOURCE_DB     database (required)
#   TABLE         table (default mem_observations)
#   COLUMN        vector column (default embedding_1024)
#   INDEX         index name (default idx_obs_embedding_1024)
#   WORK_MEM      maintenance_work_mem (default 4GB; 8GB at 1M rows)
#   WORKERS       max_parallel_maintenance_workers (default 2)
#   CONCURRENTLY  1 = build without blocking writes (default 1)
#   FORCE         1 = rebuild even when the index is already valid (default 0)
#   ELEMENTS      rows estimated for the informational readout (default: count)
#
# Exit status:
#   0 pass (built, or skipped because it was already valid)
#   2 precondition (container/database/table/column missing)
#   3 build failed (partial index dropped)
# =============================================================================

set -euo pipefail

CONTAINER="${CONTAINER:-claude-mem-test-pg}"
DB_USER="${DB_USER:-postgres}"
SOURCE_DB="${SOURCE_DB:?set SOURCE_DB to the database whose index should be rebuilt}"
TABLE="${TABLE:-mem_observations}"
COLUMN="${COLUMN:-embedding_1024}"
INDEX="${INDEX:-idx_obs_embedding_1024}"
WORK_MEM="${WORK_MEM:-4GB}"
WORKERS="${WORKERS:-2}"
CONCURRENTLY="${CONCURRENTLY:-1}"
FORCE="${FORCE:-0}"
if [ "$CONCURRENTLY" = "1" ]; then CONCURRENTLY_FLAG="CONCURRENTLY"; else CONCURRENTLY_FLAG=""; fi

log() { printf '[rebuild] %s\n' "$*"; }

psql_db() { # psql_db <sql> (single value output)
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$SOURCE_DB" \
    -v ON_ERROR_STOP=1 -t -A -c "$1"
}

fail() { # fail <code> <reason>
  printf '[rebuild] FAIL: %s\n' "$2" >&2
  exit "$1"
}

# ---------------------------------------------------------------------------
# 0. Preconditions                                                   exit 2
# ---------------------------------------------------------------------------
docker exec "$CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1 \
  || fail 2 "postgres container ${CONTAINER} is not ready"
[ "$(psql_db "SELECT to_regclass('public.${TABLE}') IS NOT NULL;")" = "t" ] \
  || fail 2 "table ${TABLE} does not exist in ${SOURCE_DB}"
[ "$(psql_db "SELECT count(*) FROM information_schema.columns
  WHERE table_name='${TABLE}' AND column_name='${COLUMN}';")" = "1" ] \
  || fail 2 "column ${COLUMN} does not exist on ${TABLE}"
[ "$(psql_db "SELECT count(*) FROM pg_extension WHERE extname='vector';")" = "1" ] \
  || fail 2 "the vector extension is not installed in ${SOURCE_DB}"

ROWS=$(psql_db "SELECT count(*) FROM ${TABLE};")
ROWS_WITH_VECTOR=$(psql_db "SELECT count(*) FROM ${TABLE} WHERE ${COLUMN} IS NOT NULL;")

# ---------------------------------------------------------------------------
# 1. Current state: absent, valid, or left behind by an interrupted build
# ---------------------------------------------------------------------------
STATE="absent"
VALID=""
if [ "$(psql_db "SELECT to_regclass('public.${INDEX}') IS NOT NULL;")" = "t" ]; then
  STATE="exists"
  VALID=$(psql_db "SELECT indisvalid FROM pg_index
    WHERE indexrelid = 'public.${INDEX}'::regclass;") \
    || fail 2 "could not read the state of ${INDEX}"
  INDEXDEF=$(psql_db "SELECT indexdef FROM pg_indexes
    WHERE schemaname='public' AND indexname='${INDEX}';")
  case "$INDEXDEF" in
    *"USING hnsw"*) : ;;
    *) fail 2 "${INDEX} exists but is not an hnsw index: ${INDEXDEF}" ;;
  esac
  printf '%s' "$INDEXDEF" | grep -qF "($COLUMN " \
    || fail 2 "${INDEX} exists but does not index ${COLUMN}: ${INDEXDEF}"
fi

if [ "$STATE" = "exists" ] && [ "$VALID" = "t" ] && [ "$FORCE" != "1" ]; then
  BYTES=$(psql_db "SELECT pg_relation_size('public.${INDEX}'::regclass);")
  log "skip: ${INDEX} is already valid (${ROWS} rows, ${ROWS_WITH_VECTOR} with ${COLUMN}, ${BYTES} bytes)"
  log "PASS (nothing to do; FORCE=1 rebuilds anyway)"
  exit 0
fi

if [ "$STATE" = "exists" ] && [ "$VALID" != "t" ]; then
  log "found an invalid ${INDEX}: this is what an interrupted build leaves behind"
  log "dropping it before the clean rebuild"
  psql_db "DROP INDEX IF EXISTS public.${INDEX};" >/dev/null \
    || fail 2 "could not drop the invalid index ${INDEX}"
elif [ "$STATE" = "exists" ]; then
  log "FORCE=1: dropping the valid ${INDEX} before the rebuild"
  psql_db "DROP INDEX IF EXISTS public.${INDEX};" >/dev/null \
    || fail 2 "could not drop ${INDEX}"
fi

# ---------------------------------------------------------------------------
# 2. Build                                                           exit 3
#
# SET and CREATE INDEX travel in one psql process because SET is session-scoped;
# sending them separately silently measures the container's stock 64 MB.
# ---------------------------------------------------------------------------
BUILD_SQL="SET maintenance_work_mem = '${WORK_MEM}';
SET max_parallel_maintenance_workers = ${WORKERS};
CREATE INDEX ${CONCURRENTLY_FLAG} ${INDEX}
  ON ${TABLE} USING hnsw (${COLUMN} vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);"

log "building ${INDEX} on ${TABLE}.${COLUMN}: ${ROWS} rows (${ROWS_WITH_VECTOR} non-null), \
maintenance_work_mem=${WORK_MEM}, workers=${WORKERS}, concurrently=${CONCURRENTLY}"
STARTED=$(date +%s)
if ! docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$SOURCE_DB" \
     -v ON_ERROR_STOP=1 -t -A <<SQL
${BUILD_SQL}
SQL
then
  # A failed or killed build can leave a partially built index behind; removing
  # it is what makes the next run a clean rebuild instead of another error.
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$SOURCE_DB" \
    -v ON_ERROR_STOP=1 -t -A -c "DROP INDEX IF EXISTS public.${INDEX};" >/dev/null 2>&1 || true
  fail 3 "CREATE INDEX ${INDEX} failed (partial index dropped; rerun to rebuild)"
fi
ELAPSED=$(( $(date +%s) - STARTED ))

# ---------------------------------------------------------------------------
# 3. Verify                                                          exit 3
# ---------------------------------------------------------------------------
VALID=$(psql_db "SELECT indisvalid FROM pg_index
  WHERE indexrelid = 'public.${INDEX}'::regclass;" || echo "")
[ "$VALID" = "t" ] || fail 3 "the rebuilt index ${INDEX} is not valid"
BYTES=$(psql_db "SELECT pg_relation_size('public.${INDEX}'::regclass);")
log "built ${INDEX}: ${ELAPSED}s, ${BYTES} bytes, ${ROWS_WITH_VECTOR} vectors indexed"
log "PASS"
