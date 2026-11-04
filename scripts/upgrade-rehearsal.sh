#!/usr/bin/env bash
# =============================================================================
# Old-database upgrade rehearsal: V12 -> V25 with real rows in the way.
#
# The backup/restore drill proves a V25 database survives a dump/restore. The
# docker E2E proves an empty volume migrates straight to V25. Neither exercises
# the path every existing deployment actually takes: a database that stopped at
# an old schema and now must be upgraded in place.
#
# This script builds that database:
#
#   1. creates a throwaway database and boots the packaged service with
#      SPRING_FLYWAY_TARGET=12, so Flyway stops exactly at V12;
#   2. seeds rows shaped the way a pre-V13 database stores them (observations and
#      summaries point at mem_sessions.memory_session_id), with 1024-dim
#      embeddings and Chinese text;
#   3. takes a pg_dump of that V12 database — the rollback artifact;
#   4. boots the service again without a target and lets Flyway run V13..V25;
#   5. checks that no row vanished, the V13 rewritten link is populated, and a
#      Chinese query still finds the seeded observation through /api/search;
#   6. restores the V12 dump into a second database and proves the rollback
#      artifact is complete: V12 history, identical row counts, identical text.
#
# Usage:
#   scripts/upgrade-rehearsal.sh
#
# Environment:
#   CONTAINER        postgres container (default claude-mem-test-pg)
#   PGHOST/PGPORT    host address of the container (default 127.0.0.1:5433)
#   DB_USER/DB_PASS  credentials (default postgres / 123456)
#   UPGRADE_DB       target database; must end with _flyway_rehearsal
#                    (default cortexce_v12_upgrade_flyway_rehearsal)
#   PORT             upgrade service port (default 37991)
#   KEEP=1           keep the upgraded database, the rollback copy and the dump
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
UPGRADE_DB="${UPGRADE_DB:-cortexce_v12_upgrade_flyway_rehearsal}"
ROLLBACK_DB="${UPGRADE_DB}_rollback"
PORT="${PORT:-37991}"
KEEP="${KEEP:-0}"
PROJECT="/tmp/upgrade-rehearsal-project"
STAMP="$(date +%Y%m%d_%H%M%S)"
DUMP_FILE="/tmp/${UPGRADE_DB}_v12_${STAMP}.dump"
UPGRADE_LOG="/tmp/upgrade-rehearsal-v12_${STAMP}.log"
CURRENT_LOG="/tmp/upgrade-rehearsal-v25_${STAMP}.log"
APP_PID=""

log() { printf '[upgrade] %s\n' "$*"; }
fail() { printf '[upgrade] FAIL: %s\n' "$*" >&2; exit 1; }

psql_run() { # psql_run <database> <sql>
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$1" -v ON_ERROR_STOP=1 -t -A -c "$2"
}

cleanup() {
  if [ -n "$APP_PID" ]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [ "$KEEP" != "1" ]; then
    psql_run postgres "DROP DATABASE IF EXISTS \"${UPGRADE_DB}\" WITH (FORCE);" >/dev/null 2>&1 || true
    psql_run postgres "DROP DATABASE IF EXISTS \"${ROLLBACK_DB}\" WITH (FORCE);" >/dev/null 2>&1 || true
    rm -f "$DUMP_FILE"
  else
    log "kept ${UPGRADE_DB}, ${ROLLBACK_DB} and ${DUMP_FILE}"
  fi
}
trap cleanup EXIT

# The service refuses to start without a chat model, so load the same keys the
# manual harnesses use. Only the provider bootstrap needs them; the rehearsal
# itself never calls the provider (the Chinese query is answered by the lexical
# path, and embeddings are already present on the seeded rows).
if [ -f "$ROOT/backend/.env" ]; then
  while IFS='=' read -r key value; do
    case "$key" in ''|'#'*) continue ;; esac
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    export "$key=$value"
  done < "$ROOT/backend/.env"
fi
export SPRING_AI_OPENAI_API_KEY="${OPENAI_API_KEY:-}"
export SPRING_AI_OPENAI_BASE_URL="${OPENAI_BASE_URL:-}"
export SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL="${OPENAI_MODEL:-}"
export CLAUDEMEM_LLM_PROVIDER="${CLAUDEMEM_LLM_PROVIDER:-openai}"
export CI=false GITHUB_ACTIONS=false

JAR="$(ls "$ROOT"/backend/target/cortex-ce-*.jar 2>/dev/null | grep -v -- '-sources' | tail -1 || true)"
[ -n "$JAR" ] || fail "no packaged jar under backend/target; run 'cd backend && mvn -o -B -DskipTests package'"

boot() { # boot <log> <target-or-empty>
  local logfile="$1" target="$2" version
  if [ -n "$target" ]; then
    export SPRING_FLYWAY_TARGET="$target"
  else
    unset SPRING_FLYWAY_TARGET
  fi
  SPRING_DATASOURCE_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${UPGRADE_DB}" \
  SPRING_DATASOURCE_USERNAME="$DB_USER" \
  SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
  SERVER_PORT="$PORT" \
  CORTEXCE_API_TOKEN="" \
    java -Xmx512m -jar "$JAR" > "$logfile" 2>&1 &
  APP_PID=$!
  for _ in $(seq 1 120); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      tail -30 "$logfile" >&2 || true
      fail "service exited during boot (see ${logfile})"
    fi
    # `version` is varchar: max(version) would order "8" above "12". The last
    # successful row by installed_rank is the actual current version.
    version="$(psql_run "$UPGRADE_DB" \
      "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;" \
      2>/dev/null || true)"
    if [ -n "$target" ] && [ "$version" = "$target" ]; then return 0; fi
    if [ -z "$target" ] && [ "$version" = "25" ]; then return 0; fi
    sleep 1
  done
  tail -30 "$logfile" >&2 || true
  fail "Flyway never reached the expected version (see ${logfile})"
}

wait_health() { # wait_health <logfile>
  local logfile="$1" status
  for _ in $(seq 1 60); do
    status="$(curl -sf -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/api/health" 2>/dev/null || true)"
    [ "$status" = "200" ] && return 0
    kill -0 "$APP_PID" 2>/dev/null || { tail -30 "$logfile" >&2; fail "service exited before becoming healthy"; }
    sleep 1
  done
  tail -30 "$logfile" >&2 || true
  fail "service did not become healthy within 60 s (see ${logfile})"
}

stop_app() {
  if [ -n "$APP_PID" ]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
    APP_PID=""
  fi
}

# ---------------------------------------------------------------------------
# 0. Preconditions
# ---------------------------------------------------------------------------
log "jar=${JAR}"
docker exec "$CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1 \
  || fail "postgres container ${CONTAINER} is not ready"
case "$UPGRADE_DB" in
  *_flyway_rehearsal) ;;
  *) fail "UPGRADE_DB must end with _flyway_rehearsal (got ${UPGRADE_DB})" ;;
esac

psql_run postgres "DROP DATABASE IF EXISTS \"${UPGRADE_DB}\" WITH (FORCE);" >/dev/null
psql_run postgres "DROP DATABASE IF EXISTS \"${ROLLBACK_DB}\" WITH (FORCE);" >/dev/null
psql_run postgres "CREATE DATABASE \"${UPGRADE_DB}\";" >/dev/null
psql_run "$UPGRADE_DB" "CREATE EXTENSION IF NOT EXISTS vector;" >/dev/null
log "created empty database ${UPGRADE_DB}"

# ---------------------------------------------------------------------------
# 1. Stop Flyway at V12
# ---------------------------------------------------------------------------
boot "$UPGRADE_LOG" 12
V12_APPLIED="$(psql_run "$UPGRADE_DB" "SELECT count(*) FROM flyway_schema_history WHERE success;")"
V12_VERSION="$(psql_run "$UPGRADE_DB" "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;")"
log "schema stopped at V${V12_VERSION} (${V12_APPLIED} migrations applied)"
stop_app

# ---------------------------------------------------------------------------
# 2. Seed rows in the pre-V13 shape
# ---------------------------------------------------------------------------
SEED_SQL="$(cat <<'SQL'
INSERT INTO mem_sessions (id, content_session_id, memory_session_id, project_path, user_prompt, started_at_epoch, status)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'cs-old-1', 'ms-old-1', '/tmp/upgrade-rehearsal-project', '历史会话一：检索链路排查', (extract(epoch from now()) * 1000)::bigint - 7200000, 'completed'),
  ('22222222-2222-2222-2222-222222222222', 'cs-old-2', 'ms-old-2', '/tmp/upgrade-rehearsal-project', '历史会话二：队列卡住分析', (extract(epoch from now()) * 1000)::bigint - 3600000, 'completed');

-- Pre-V13 rows reference mem_sessions.memory_session_id through the FK that V8
-- added. V13 is the migration that rewrites this link to content_session_id.
INSERT INTO mem_observations
  (id, memory_session_id, project_path, type, title, subtitle, content, facts, concepts,
   files_modified, discovery_tokens, prompt_number, embedding_1024, embedding_model_id, created_at_epoch)
VALUES
  ('aaaa1111-1111-1111-1111-111111111111', 'ms-old-1', '/tmp/upgrade-rehearsal-project', 'architecture',
   '检索服务把中文查询和英文代码观察记录对齐',
   '中文查询走词法回退，英文代码走向量检索',
   'ObservationRepository.hybridSearchScored 用 search_tokens 处理中文查询，检索失败时回退到全文检索。',
   '["中文检索依赖 simple 分词"]'::jsonb, '["how-it-works"]'::jsonb,
   to_jsonb(ARRAY['backend/src/main/java/com/ablueforce/cortexce/service/SearchService.java']::text[]),
   120, 1,
   ('[' || array_to_string(ARRAY(SELECT random()::real FROM generate_series(1, 1024)), ',') || ']')::vector(1024),
   'BAAI/bge-m3', (extract(epoch from now()) * 1000)::bigint - 7100000),
  ('aaaa2222-2222-2222-2222-222222222222', 'ms-old-2', '/tmp/upgrade-rehearsal-project', 'gotcha',
   '队列里 processing 卡住的行要按 stale 阈值恢复',
   '重试预算耗尽后标记 failed',
   'PendingMessageProcessor 每轮扫描 processing 超过十分钟的行，重试预算用尽后写 failed_at_epoch。',
   '["卡住的行不会被重复领取"]'::jsonb, '["gotcha"]'::jsonb,
   to_jsonb(ARRAY['backend/src/main/java/com/ablueforce/cortexce/service/PendingMessageProcessor.java']::text[]),
   80, 1,
   ('[' || array_to_string(ARRAY(SELECT random()::real FROM generate_series(1, 1024)), ',') || ']')::vector(1024),
   'BAAI/bge-m3', (extract(epoch from now()) * 1000)::bigint - 3500000);

INSERT INTO mem_summaries
  (id, memory_session_id, project_path, request, investigated, learned, completed, next_steps, created_at_epoch)
VALUES
  ('bbbb1111-1111-1111-1111-111111111111', 'ms-old-1', '/tmp/upgrade-rehearsal-project',
   '排查中文检索', '检查 search_tokens', 'simple 分词可用于中文', '确认回退路径可用', '补充回归用例', (extract(epoch from now()) * 1000)::bigint - 7000000),
  ('bbbb2222-2222-2222-2222-222222222222', 'ms-old-2', '/tmp/upgrade-rehearsal-project',
   '排查队列卡住', '检查 stale 阈值', '十分钟阈值生效', '恢复卡住的行', '加监控告警', (extract(epoch from now()) * 1000)::bigint - 3400000);

INSERT INTO mem_user_prompts (id, content_session_id, prompt_number, prompt_text, project_path, created_at_epoch)
VALUES
  ('cccc1111-1111-1111-1111-111111111111', 'cs-old-1', 1, '帮我确认中文检索为什么不命中', NULL, (extract(epoch from now()) * 1000)::bigint - 7150000),
  ('cccc2222-2222-2222-2222-222222222222', 'cs-old-2', 2, '队列里卡住的行怎么恢复', '/tmp/upgrade-rehearsal-project', (extract(epoch from now()) * 1000)::bigint - 3550000);

-- processed/failed, not pending: the post-upgrade boot must not pick them up
-- and change the row counts under the assertions.
INSERT INTO mem_pending_messages
  (id, session_db_id, content_session_id, message_type, tool_name, status, retry_count, created_at_epoch, completed_at_epoch, failed_at_epoch)
VALUES
  ('dddd1111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'cs-old-1',
   'observation', 'Edit', 'processed', 0, (extract(epoch from now()) * 1000)::bigint - 7050000, (extract(epoch from now()) * 1000)::bigint - 7040000, NULL),
  ('dddd2222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'cs-old-2',
   'observation', 'Bash', 'failed', 3, (extract(epoch from now()) * 1000)::bigint - 3450000, NULL, (extract(epoch from now()) * 1000)::bigint - 3300000);
SQL
)"
printf '%s' "$SEED_SQL" | docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$UPGRADE_DB" \
  -v ON_ERROR_STOP=1 >/dev/null
log "seeded 2 sessions, 2 observations, 2 summaries, 2 prompts, 2 queue rows"

PRE_COUNTS="$(psql_run "$UPGRADE_DB" "
  SELECT 'sessions=' || (SELECT count(*) FROM mem_sessions)
      || ' observations=' || (SELECT count(*) FROM mem_observations)
      || ' summaries=' || (SELECT count(*) FROM mem_summaries)
      || ' prompts=' || (SELECT count(*) FROM mem_user_prompts)
      || ' pending=' || (SELECT count(*) FROM mem_pending_messages);")"
log "pre-upgrade counts: ${PRE_COUNTS}"
PRE_IDS="$(psql_run "$UPGRADE_DB" "SELECT string_agg(id::text, ',' ORDER BY id) FROM mem_observations;")"

# ---------------------------------------------------------------------------
# 3. Rollback artifact
# ---------------------------------------------------------------------------
docker exec -i "$CONTAINER" pg_dump -U "$DB_USER" -Fc -Z 6 "$UPGRADE_DB" > "$DUMP_FILE"
log "rollback dump written: ${DUMP_FILE} ($(wc -c < "$DUMP_FILE" | tr -d ' ') bytes)"

# ---------------------------------------------------------------------------
# 4. Upgrade in place
# ---------------------------------------------------------------------------
boot "$CURRENT_LOG" ""
V25_APPLIED="$(psql_run "$UPGRADE_DB" "SELECT count(*) FROM flyway_schema_history WHERE success;")"
V25_VERSION="$(psql_run "$UPGRADE_DB" "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;")"
[ "$V25_VERSION" = "25" ] || fail "expected V25, got V${V25_VERSION} (see ${CURRENT_LOG})"
log "upgraded to V${V25_VERSION}: ${V25_APPLIED} successful migrations (V13..V25 applied), service booted on ${PORT}"
wait_health "$CURRENT_LOG"

# ---------------------------------------------------------------------------
# 5. Data integrity
# ---------------------------------------------------------------------------
POST_COUNTS="$(psql_run "$UPGRADE_DB" "
  SELECT 'sessions=' || (SELECT count(*) FROM mem_sessions)
      || ' observations=' || (SELECT count(*) FROM mem_observations)
      || ' summaries=' || (SELECT count(*) FROM mem_summaries)
      || ' prompts=' || (SELECT count(*) FROM mem_user_prompts)
      || ' pending=' || (SELECT count(*) FROM mem_pending_messages);")"
[ "$PRE_COUNTS" = "$POST_COUNTS" ] || fail "row counts changed: before=${PRE_COUNTS} after=${POST_COUNTS}"
log "post-upgrade counts identical: ${POST_COUNTS}"

POST_IDS="$(psql_run "$UPGRADE_DB" "SELECT string_agg(id::text, ',' ORDER BY id) FROM mem_observations;")"
[ "$PRE_IDS" = "$POST_IDS" ] || fail "observation ids changed across the upgrade"
log "all observation ids preserved"

# V13 rewrites the child link; every row must now carry a resolvable
# content_session_id.
UNRESOLVED="$(psql_run "$UPGRADE_DB" "
  SELECT count(*) FROM mem_observations o
  WHERE o.content_session_id IS NULL
     OR NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.content_session_id = o.content_session_id);")"
[ "$UNRESOLVED" = "0" ] || fail "${UNRESOLVED} observation(s) have no resolvable content_session_id"
log "V13 link rewrite complete: every observation resolves to content_session_id"

[ "$(psql_run "$UPGRADE_DB" "SELECT count(*) FROM information_schema.columns WHERE table_name='mem_observations' AND column_name='memory_session_id';")" = "0" ] \
  || fail "memory_session_id should be dropped after V13"
[ "$(psql_run "$UPGRADE_DB" "SELECT count(*) FROM information_schema.columns WHERE table_name='mem_observations' AND column_name='search_tokens';")" = "1" ] \
  || fail "search_tokens column missing after V24"
log "V13/V24 schema shape is in place (memory_session_id dropped, search_tokens present)"

LEXICAL_HITS="$(psql_run "$UPGRADE_DB" "SELECT id FROM mem_observations WHERE search_tokens @@ plainto_tsquery('simple', '检索') ORDER BY created_at_epoch DESC LIMIT 1;")"
[ "$LEXICAL_HITS" = "aaaa1111-1111-1111-1111-111111111111" ] \
  || fail "Chinese lexical query '检索' did not find the seeded observation (got '${LEXICAL_HITS}')"
log "Chinese lexical query '检索' finds aaaa1111 (search_tokens generated over the old rows)"

# The HTTP path is what an operator sees; exercise it on the freshly upgraded DB.
HEALTH="$(curl -sf -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/api/health" || true)"
[ "$HEALTH" = "200" ] || fail "health check after upgrade returned ${HEALTH}"
SEARCH_BODY="$(curl -sfG --data-urlencode "project=${PROJECT}" --data-urlencode "query=检索" \
  --data-urlencode "limit=10" "http://127.0.0.1:${PORT}/api/search")" \
  || fail "GET /api/search failed after the upgrade"
printf '%s' "$SEARCH_BODY" | grep -q 'aaaa1111-1111-1111-1111-111111111111' \
  || fail "GET /api/search did not return the seeded observation after the upgrade"
log "GET /api/search returns the seeded observation (HTTP 200)"
stop_app

# ---------------------------------------------------------------------------
# 6. Rollback: restore the V12 dump and prove it is complete
# ---------------------------------------------------------------------------
psql_run postgres "CREATE DATABASE \"${ROLLBACK_DB}\";" >/dev/null
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$ROLLBACK_DB" --no-owner --no-privileges \
  --exit-on-error < "$DUMP_FILE"
ROLLBACK_VERSION="$(psql_run "$ROLLBACK_DB" "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;")"
ROLLBACK_COUNTS="$(psql_run "$ROLLBACK_DB" "
  SELECT 'sessions=' || (SELECT count(*) FROM mem_sessions)
      || ' observations=' || (SELECT count(*) FROM mem_observations)
      || ' summaries=' || (SELECT count(*) FROM mem_summaries)
      || ' prompts=' || (SELECT count(*) FROM mem_user_prompts)
      || ' pending=' || (SELECT count(*) FROM mem_pending_messages);")"
[ "$ROLLBACK_VERSION" = "12" ] || fail "rollback copy is at V${ROLLBACK_VERSION}, expected V12"
[ "$ROLLBACK_COUNTS" = "$PRE_COUNTS" ] || fail "rollback row counts differ: ${ROLLBACK_COUNTS} vs ${PRE_COUNTS}"
[ "$(psql_run "$ROLLBACK_DB" "SELECT count(*) FROM information_schema.columns WHERE table_name='mem_observations' AND column_name='content_session_id';")" = "0" ] \
  || fail "rollback copy unexpectedly has the V13 column"
ROLLBACK_TITLE="$(psql_run "$ROLLBACK_DB" "SELECT title FROM mem_observations WHERE id='aaaa1111-1111-1111-1111-111111111111';")"
[ "$ROLLBACK_TITLE" = "检索服务把中文查询和英文代码观察记录对齐" ] || fail "rollback copy lost the seeded observation text"
log "rollback restore is complete: V12, counts identical, pre-V13 columns, text intact"

# ---------------------------------------------------------------------------
# 7. Verdict
# ---------------------------------------------------------------------------
log "PASS: V${V12_VERSION} -> V${V25_VERSION} in place, ${POST_COUNTS}, no lost ids,"
log "      Chinese lexical + HTTP search work, rollback dump restores to V12 intact"
