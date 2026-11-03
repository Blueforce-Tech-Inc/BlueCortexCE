#!/usr/bin/env bash
# One command that proves the test base is green from a clean database.
#
# What it does, in order, and why each step is here:
#
#   1. locate a running PostgreSQL + pgvector container (reuse the one on this
#      machine; a missing container is an error, never a silent skip);
#   2. drop and recreate the dedicated *_flyway_rehearsal database;
#   3. enable the vector extension and prove it is usable;
#   4. boot the application once so Flyway migrates to the latest version, then
#      assert the recorded version equals the newest file in db/migration;
#   5. run the full backend suite: L1 unit/integration, L2 HTTP regression,
#      L3 acceptance and L4 end-to-end in one Maven run;
#   6. run the zero-skip / min-tests / four-layer / baseline-ratchet gate;
#   7. run both documentation consistency checks (checker self-test, then the
#      published EN/ZH API reference against the live server);
#   8. run the proxy E2E against that server and refuse a skipped run (this is
#      the proxy-forwarding part of L3).
#
# Every step prints its name. A failure prints the step, the command's own
# reason, and exits non-zero. Nothing here treats "skipped" as success.
#
# Usage:
#   ./scripts/verify-local.sh
#
# Overrides (all optional):
#   PG_CONTAINER=postgresql  PG_PORT=5432  PG_USER=postgres  PG_PASSWORD=123456
#   TEST_DB=cortexce_flyway_rehearsal
#   VERIFY_MIN_TESTS=474   VERIFY_APP_PORT=37888   VERIFY_E2E_PORT=37777
#   VERIFY_LOG_DIR=/tmp/cortexce-verify

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TEST_DB="${TEST_DB:-cortexce_flyway_rehearsal}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-123456}"
PG_CONTAINER="${PG_CONTAINER:-}"
PG_PORT="${PG_PORT:-}"
APP_PORT="${VERIFY_APP_PORT:-37888}"
E2E_PORT="${VERIFY_E2E_PORT:-37777}"
MIN_TESTS="${VERIFY_MIN_TESTS:-474}"
LOG_BASE="${TMPDIR:-/tmp}"
LOG_BASE="${LOG_BASE%/}"
LOG_DIR="${VERIFY_LOG_DIR:-${LOG_BASE}/cortexce-verify-$$}"
EXPECTED_VERSION="${PROXY_E2E_EXPECTED_VERSION:-0.1.0-beta}"

mkdir -p "$LOG_DIR"

APP_PID=""
SERVER_PID=""

step() {
  printf '\n=== %s ===\n' "$*"
}

note() {
  printf '    %s\n' "$*"
}

# Print the failing step and the reason, then stop. Never fall through to a
# later step that could mask the failure.
fail() {
  local name="$1"
  shift
  printf '\nSTEP FAILED: %s\n' "$name" >&2
  printf 'Reason:\n' >&2
  printf '%s\n' "$@" >&2
  printf 'Logs: %s\n' "$LOG_DIR" >&2
  exit 1
}

# Echo the useful part of a failed command's log, bounded so the terminal
# stays readable.
summarize_log() {
  local log="$1"
  {
    grep -E '<<< (FAILURE|ERROR)!|Tests run: .*(Failures: [1-9]|Errors: [1-9])' "$log" | head -n 20 || true
    tail -n 25 "$log" || true
  }
}

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]]; then
    kill "$SERVER_PID" 2>/dev/null || true
  fi
  if [[ -n "${APP_PID:-}" ]]; then
    kill "$APP_PID" 2>/dev/null || true
  fi
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

wait_for_health() {
  local port="$1" pid="$2" i
  for i in $(seq 1 120); do
    if curl -sf "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      return 1
    fi
    sleep 1
  done
  return 1
}

# --------------------------------------------------------------------------
# 1. locate a usable database
#
# Reuses a container that is already running on this machine. Whether pgvector
# is usable is decided by pg_available_extensions, never by the image tag: a
# postgres:16 image may or may not ship the extension.
# --------------------------------------------------------------------------
step "1/8 Locate a running PostgreSQL + pgvector container"

command -v docker >/dev/null 2>&1 \
  || fail "locate database" "docker is not installed or not on PATH."

if ! docker info >/dev/null 2>&1; then
  fail "locate database" "the Docker daemon is not reachable; start Docker and re-run."
fi

if [[ -z "$PG_CONTAINER" ]]; then
  # Prefer the project's dedicated test container. A generic container named
  # "postgresql" may be shared with unrelated workloads; with max_connections=100
  # the suite's cached Spring contexts can exhaust it ("sorry, too many clients"),
  # which would look like a test failure instead of an environment problem.
  if docker ps --format '{{.Names}}' | grep -qx 'claude-mem-test-pg'; then
    PG_CONTAINER="claude-mem-test-pg"
  fi
fi

if [[ -z "$PG_CONTAINER" ]]; then
  # Prefer the conventional name first, then any running postgres/pgvector
  # container. A container that lacks pgvector is not silently used.
  if docker ps --format '{{.Names}}' | grep -qx 'postgresql'; then
    PG_CONTAINER="postgresql"
  fi
fi

if [[ -z "$PG_CONTAINER" ]]; then
  while IFS=$'\t' read -r name image ports; do
    case "$image $name $ports" in
      *postgres*|*pgvector*)
        if [[ -n "$ports" ]]; then
          PG_CONTAINER="$name"
          break
        fi
        ;;
    esac
  done < <(docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}')
fi

if [[ -z "$PG_CONTAINER" ]]; then
  fail "locate database" \
    "no running PostgreSQL container was found." \
    "Start one with a pgvector image, then re-run:" \
    "  docker run -d --name postgresql -p 5432:5432 -e POSTGRES_PASSWORD=${PG_PASSWORD} pgvector/pgvector:pg16" \
    "Or point the script at an existing container: PG_CONTAINER=<name> PG_PORT=<port> $0"
fi

VECTOR_VERSION="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -tAc \
  "SELECT default_version FROM pg_available_extensions WHERE name='vector'" 2>/dev/null || true)"
VECTOR_VERSION="$(printf '%s' "$VECTOR_VERSION" | tr -d '[:space:]')"
if [[ -z "$VECTOR_VERSION" ]]; then
  fail "locate database" \
    "container '$PG_CONTAINER' is running but does not provide the pgvector extension" \
    "(pg_available_extensions has no 'vector' row)." \
    "Use a container built from pgvector/pgvector:pg16, or install the extension in this one."
fi

if [[ -z "$PG_PORT" ]]; then
  port_mapping="$(docker port "$PG_CONTAINER" 5432/tcp 2>/dev/null || true)"
  port_mapping="${port_mapping%%$'\n'*}"
  if [[ -n "$port_mapping" ]]; then
    PG_PORT="${port_mapping##*:}"
  fi
fi

if [[ -z "$PG_PORT" ]]; then
  fail "locate database" \
    "container '$PG_CONTAINER' does not publish port 5432 to the host, so JDBC cannot reach it." \
    "Set PG_PORT to the host port that maps to the container's 5432."
fi

if ! port_in_use "$PG_PORT"; then
  fail "locate database" \
    "nothing accepts TCP connections on 127.0.0.1:${PG_PORT} (container '$PG_CONTAINER')." \
    "Check 'docker port $PG_CONTAINER 5432/tcp' and set PG_PORT accordingly."
fi

note "container=$PG_CONTAINER host_port=$PG_PORT user=$PG_USER database=$TEST_DB vector_available=$VECTOR_VERSION"

# --------------------------------------------------------------------------
# 2 + 3. recreate the dedicated database and enable vector
#
# The tests truncate tables, so a business database must never be reused. The
# DROP makes this block re-runnable from a clean state.
# --------------------------------------------------------------------------
step "2/8 Recreate the dedicated test database"

docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$TEST_DB\" WITH (FORCE)" >/dev/null \
  || fail "recreate database" "could not drop '$TEST_DB' (is it in use?)."

docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "CREATE DATABASE \"$TEST_DB\"" >/dev/null \
  || fail "recreate database" "could not create '$TEST_DB'."

step "3/8 Enable and verify the vector extension"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -v ON_ERROR_STOP=1 \
  -c "CREATE EXTENSION IF NOT EXISTS vector" >/dev/null \
  || fail "enable vector" "CREATE EXTENSION vector failed inside '$TEST_DB'."

EXT_VERSION="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT extversion FROM pg_extension WHERE extname='vector'" 2>/dev/null | tr -d '[:space:]')"
if [[ -z "$EXT_VERSION" ]]; then
  fail "enable vector" "pg_extension does not list 'vector' inside '$TEST_DB' after CREATE EXTENSION."
fi
note "vector extension enabled in $TEST_DB (version $EXT_VERSION)"

# --------------------------------------------------------------------------
# 4. migrate through the application's own Flyway configuration
#
# The version is compared with the newest file in db/migration, so a migration
# that silently fails to apply is a failure here, not a surprise in the tests.
# --------------------------------------------------------------------------
step "4/8 Migrate to the latest version"

EXPECTED_MIGRATION="$(ls "$ROOT/backend/src/main/resources/db/migration" 2>/dev/null \
  | sed -n 's/^V\([0-9][0-9]*\)__.*\.sql$/\1/p' | sort -n | tail -n1)"
if [[ -z "$EXPECTED_MIGRATION" ]]; then
  fail "migrate" "no V*__*.sql migration files were found in backend/src/main/resources/db/migration."
fi

cd "$ROOT/backend"
mvn -B -DskipTests package > "$LOG_DIR/mvn-package.log" 2>&1 \
  || fail "migrate" "mvn -B -DskipTests package failed while building the migration jar." \
       "$(summarize_log "$LOG_DIR/mvn-package.log")"

jars=(target/cortex-ce-*.jar)
if [[ ! -f "${jars[0]}" ]]; then
  fail "migrate" "the packaged jar target/cortex-ce-*.jar does not exist."
fi

# A health check cannot tell our process from someone else's already listening
# on the same port, so refuse to start rather than measure an unknown server.
if port_in_use "$APP_PORT"; then
  fail "migrate" "port ${APP_PORT} is already in use; refusing to measure an unknown server." \
       "Free it, or re-run with VERIFY_APP_PORT=<free port>."
fi

SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${TEST_DB}" \
SPRING_DATASOURCE_USERNAME="$PG_USER" \
SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
SERVER_PORT="$APP_PORT" \
java -jar "${jars[0]}" > "$LOG_DIR/migrate-app.log" 2>&1 &
APP_PID=$!

if ! wait_for_health "$APP_PORT" "$APP_PID" "$LOG_DIR/migrate-app.log"; then
  kill "$APP_PID" 2>/dev/null || true
  APP_PID=""
  fail "migrate" "the application did not become healthy on port ${APP_PORT} within 120s." \
       "$(summarize_log "$LOG_DIR/migrate-app.log")"
fi

APPLIED_VERSION="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null \
  | tr -d '[:space:]')"
FAILED_MIGRATIONS="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE success = false" 2>/dev/null \
  | tr -d '[:space:]')"

kill "$APP_PID" 2>/dev/null || true
wait "$APP_PID" 2>/dev/null || true
APP_PID=""

[[ "$FAILED_MIGRATIONS" == "0" ]] \
  || fail "migrate" "flyway_schema_history contains ${FAILED_MIGRATIONS} failed migration(s)."
[[ "$APPLIED_VERSION" == "$EXPECTED_MIGRATION" ]] \
  || fail "migrate" "applied Flyway version is '${APPLIED_VERSION}', expected '${EXPECTED_MIGRATION}' (newest file in db/migration)."
note "flyway version=$APPLIED_VERSION (matches db/migration), failed migrations=0"

# --------------------------------------------------------------------------
# 5. full backend suite
# --------------------------------------------------------------------------
step "5/8 Full backend suite: L1 unit/integration + L2 regression + L3 acceptance + L4 end-to-end (MEMSCOPE_* pointed at $TEST_DB)"

cd "$ROOT/backend"
MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${TEST_DB}" \
MEMSCOPE_TEST_DB_USER="$PG_USER" \
MEMSCOPE_TEST_DB_PASSWORD="$PG_PASSWORD" \
MEMSCOPE_FLYWAY_TEST_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${TEST_DB}" \
MEMSCOPE_FLYWAY_TEST_USER="$PG_USER" \
MEMSCOPE_FLYWAY_TEST_PASSWORD="$PG_PASSWORD" \
mvn -B clean install > "$LOG_DIR/backend-tests.log" 2>&1 \
  || fail "backend tests" "mvn -B clean install failed." \
       "$(summarize_log "$LOG_DIR/backend-tests.log")"

# --------------------------------------------------------------------------
# 6. zero-skip + min-tests + four-layer + baseline gate
#
# A build that reports success while DB tests skipped must not pass. The gate
# also fails when the total test count drops below the agreed floor, or when
# any of L1-L4 executed no tests at all, or when any layer executed fewer tests
# than scripts/test-baseline.json records. The floor is a hard "must not go
# below" value; the baseline is a per-layer ratchet that you raise with
# `python3 scripts/check-test-results.py --write-baseline` after adding tests.
# --------------------------------------------------------------------------
step "6/8 Layer gate (--require-zero-skips --min-tests $MIN_TESTS --require-layers --require-baseline)"

cd "$ROOT"
GATE_OUTPUT="$(python3 scripts/check-test-results.py --require-zero-skips \
  --min-tests "$MIN_TESTS" --require-layers --require-baseline 2>&1)" \
  || fail "test report gate" "check-test-results.py rejected the Surefire report:" "$GATE_OUTPUT"
printf '%s\n' "$GATE_OUTPUT"

# --------------------------------------------------------------------------
# 7. documentation consistency, twice
#
# First the checker proves it can still fail (no server needed). Then the two
# published references are compared with the endpoints the server actually
# serves. The server stays up for step 8.
# --------------------------------------------------------------------------
step "7/8 Documentation consistency check 1/2 (checker self-test)"
SELF_TEST_OUTPUT="$(python3 scripts/check-api-doc-surface.py --self-test 2>&1)" \
  || fail "doc consistency (self-test)" "check-api-doc-surface.py --self-test failed:" "$SELF_TEST_OUTPUT"
printf '%s\n' "$SELF_TEST_OUTPUT"

step "7/8 Documentation consistency check 2/2 (EN/ZH reference vs live server)"

if port_in_use "$E2E_PORT"; then
  fail "doc consistency (live server)" \
       "port ${E2E_PORT} is already in use, so the comparison would target an unknown server." \
       "Stop the process using it, or re-run with VERIFY_E2E_PORT=<free port>."
fi

cd "$ROOT/backend"
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${TEST_DB}" \
SPRING_DATASOURCE_USERNAME="$PG_USER" \
SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
SERVER_PORT="$E2E_PORT" \
java -jar "${jars[0]}" > "$LOG_DIR/e2e-server.log" 2>&1 &
SERVER_PID=$!

if ! wait_for_health "$E2E_PORT" "$SERVER_PID" "$LOG_DIR/e2e-server.log"; then
  fail "doc consistency (live server)" \
       "the application did not become healthy on port ${E2E_PORT} within 120s." \
       "$(summarize_log "$LOG_DIR/e2e-server.log")"
fi

cd "$ROOT"
DOC_OUTPUT="$(python3 scripts/check-api-doc-surface.py --server-url "http://127.0.0.1:${E2E_PORT}" 2>&1)" \
  || fail "doc consistency (live server)" "the published API references do not match the running server:" "$DOC_OUTPUT"
printf '%s\n' "$DOC_OUTPUT"

# --------------------------------------------------------------------------
# 8. proxy E2E
#
# The env vars force the suite to run instead of skipping. The assertions on
# the output make a skipped or empty run a failure even if the exit code is 0.
# --------------------------------------------------------------------------
step "8/8 Proxy E2E against http://127.0.0.1:${E2E_PORT} (L3 proxy forwarding)"

cd "$ROOT/proxy"
npm ci > "$LOG_DIR/proxy-install.log" 2>&1 \
  || fail "proxy install" "npm ci failed in proxy/." "$(summarize_log "$LOG_DIR/proxy-install.log")"

PROXY_E2E_SERVER_URL="http://127.0.0.1:${E2E_PORT}" \
PROXY_E2E_EXPECTED_VERSION="$EXPECTED_VERSION" \
npm test > "$LOG_DIR/proxy-test.log" 2>&1 \
  || fail "proxy E2E" "npm test failed in proxy/." "$(summarize_log "$LOG_DIR/proxy-test.log")"

# Proof the suite actually executed rather than being skipped: the flow prints
# this banner only after it verified /api/health and /api/version.
grep -q 'Target verified:' "$LOG_DIR/proxy-test.log" \
  || fail "proxy E2E" "the proxy suite did not report 'Target verified:' — it did not run."
if ! grep -Eq '(^|[[:space:]])skipped[[:space:]]+0([[:space:]]|$)' "$LOG_DIR/proxy-test.log"; then
  fail "proxy E2E" "node --test reported skipped tests (a skipped suite is not a green run)." \
       "$(summarize_log "$LOG_DIR/proxy-test.log")"
fi
note "proxy E2E ran and reported 0 skipped"

printf '\nALL CHECKS PASSED\n'
printf '  database   : %s on 127.0.0.1:%s (pgvector %s)\n' "$TEST_DB" "$PG_PORT" "$EXT_VERSION"
printf '  migrations : V%s\n' "$APPLIED_VERSION"
printf '  layers     : L1-L4 all executed, 0 skipped\n'
printf '  tests      : gate --min-tests %s --require-zero-skips --require-layers --require-baseline\n' "$MIN_TESTS"
printf '  docs       : checker self-test + EN/ZH reference vs live server\n'
printf '  proxy      : E2E executed, 0 skipped (L3 proxy forwarding)\n'
printf '  logs       : %s\n' "$LOG_DIR"
