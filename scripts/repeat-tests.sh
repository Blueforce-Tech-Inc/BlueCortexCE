#!/usr/bin/env bash
# Repeat the backend suite in isolated databases and report per-test stability.
#
# Why this exists
# ---------------
# A single green `mvn test` says nothing about whether the same test would pass
# again: order-dependent tests, shared static state, wall-clock/random input and
# fire-and-forget async work all pass once and fail later. This script runs the
# whole suite N times (default 3), each round against its own freshly created
# database, with Surefire's random execution order, then compares the per-test
# outcomes. It does not hide a failing round behind the others:
#
#   * a test that passes in one round and fails in another is "flaky" and exits 1
#   * a test that fails in every round is a normal failure, printed separately
#   * a test that only exists in some rounds is reported too (conditional
#     discovery is a form of instability)
#
# The database work is the same shape as verify-local.sh: one seed database is
# migrated by the packaged application, then each round is a `CREATE DATABASE
# ... TEMPLATE seed` copy. That keeps the rounds independent without paying the
# Flyway/boot cost three times.
#
# Usage
# -----
#   ./scripts/repeat-tests.sh                 # 3 rounds, random order
#   ROUNDS=5 ./scripts/repeat-tests.sh        # more rounds
#   RUN_ORDER=filesystem ./scripts/repeat-tests.sh
#   KEEP_DB=1 ./scripts/repeat-tests.sh       # keep the round databases
#   SKIP_PACKAGE=1 ./scripts/repeat-tests.sh  # reuse backend/target/*.jar
#
# Overrides: PG_CONTAINER (default claude-mem-test-pg), PG_PORT (5433),
# PG_USER/PG_PASSWORD, ROUNDS (3), RUN_ORDER (random), VERIFY_APP_PORT (37991),
# LOG_DIR, TEST_PREFIX, KEEP_DB.
#
# Exit: 0 = every test had the same outcome in every round; 1 = flaky or
# missing tests; 2 = the harness itself could not run (no container/jar).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PG_CONTAINER="${PG_CONTAINER:-claude-mem-test-pg}"
PG_PORT="${PG_PORT:-5433}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-123456}"
ROUNDS="${ROUNDS:-3}"
RUN_ORDER="${RUN_ORDER:-random}"
APP_PORT="${VERIFY_APP_PORT:-37991}"
KEEP_DB="${KEEP_DB:-0}"
PREFIX="${TEST_PREFIX:-cortexce_repeat_$(date +%Y%m%d%H%M%S)_$$}"
LOG_DIR="${LOG_DIR:-${TMPDIR:-/tmp}/cortexce-repeat-$$}"
SKIP_PACKAGE="${SKIP_PACKAGE:-0}"

mkdir -p "$LOG_DIR"

APP_PID=""
created_dbs=()

step() { printf '\n=== %s ===\n' "$*"; }
note() { printf '    %s\n' "$*"; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 2; }

psql_admin() { # psql_admin <sql>
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 -c "$1"
}

drop_db() { # drop_db <name>
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS \"$1\" WITH (FORCE)" >/dev/null 2>&1 || true
}

cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
    APP_PID=""
  fi
  if [[ "$KEEP_DB" != "1" ]]; then
    for db in "${created_dbs[@]}"; do
      drop_db "$db"
    done
  else
    printf 'kept databases: %s\n' "${created_dbs[*]}"
  fi
}
trap cleanup EXIT INT TERM

port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

# ---------------------------------------------------------------------------
# 1. package once and migrate a seed database
# ---------------------------------------------------------------------------
step "1/4 Package the backend"

if [[ "$SKIP_PACKAGE" != "1" ]]; then
  cd "$ROOT/backend"
  mvn -B -DskipTests package > "$LOG_DIR/mvn-package.log" 2>&1 \
    || die "mvn -B -DskipTests package failed; see $LOG_DIR/mvn-package.log"
fi
JAR="$(ls "$ROOT"/backend/target/cortex-ce-*.jar 2>/dev/null | head -1 || true)"
[[ -n "$JAR" ]] || die "no backend/target/cortex-ce-*.jar (run without SKIP_PACKAGE=1)"
cd "$ROOT"

step "2/4 Create and migrate the seed database"

docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" >/dev/null 2>&1 \
  || die "container $PG_CONTAINER is not accepting connections"

SEED_DB="${PREFIX}_seed_flyway_rehearsal"
drop_db "$SEED_DB"
created_dbs+=("$SEED_DB")
psql_admin "CREATE DATABASE \"$SEED_DB\"" >/dev/null
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$SEED_DB" -v ON_ERROR_STOP=1 \
  -c "CREATE EXTENSION IF NOT EXISTS vector" >/dev/null

port_in_use "$APP_PORT" && die "port $APP_PORT is in use; set VERIFY_APP_PORT"

cd "$ROOT/backend"
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${SEED_DB}" \
SPRING_DATASOURCE_USERNAME="$PG_USER" \
SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
SERVER_PORT="$APP_PORT" \
java -jar "$JAR" > "$LOG_DIR/migrate-seed.log" 2>&1 &
APP_PID=$!

for _ in $(seq 1 120); do
  if curl -sf "http://127.0.0.1:${APP_PORT}/api/health" >/dev/null 2>&1; then
    break
  fi
  kill -0 "$APP_PID" 2>/dev/null || die "the application exited while migrating; see $LOG_DIR/migrate-seed.log"
  sleep 1
done
curl -sf "http://127.0.0.1:${APP_PORT}/api/health" >/dev/null 2>&1 \
  || die "the application did not become healthy on port $APP_PORT"
kill "$APP_PID" 2>/dev/null || true
wait "$APP_PID" 2>/dev/null || true
APP_PID=""
cd "$ROOT"

# The seeded copy must be closed before CREATE DATABASE ... TEMPLATE can copy it.
APPLIED_VERSION="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$SEED_DB" -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" | tr -d '[:space:]')"
note "seed database migrated to Flyway V$APPLIED_VERSION"

# ---------------------------------------------------------------------------
# 2-3. run the rounds
# ---------------------------------------------------------------------------
step "3/4 Run $ROUNDS rounds (order=$RUN_ORDER, isolated database per round)"

round_dirs=()
for round in $(seq 1 "$ROUNDS"); do
  DB="${PREFIX}_r${round}_flyway_rehearsal"
  drop_db "$DB"
  created_dbs+=("$DB")
  psql_admin "CREATE DATABASE \"$DB\" TEMPLATE \"$SEED_DB\"" >/dev/null
  note "round $round: $DB"

  rm -rf "$ROOT/backend/target/surefire-reports"
  seed=$(( 1000 + round * 7919 ))
  cd "$ROOT/backend"
  set +e
  MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${DB}" \
  MEMSCOPE_TEST_DB_USER="$PG_USER" \
  MEMSCOPE_TEST_DB_PASSWORD="$PG_PASSWORD" \
  MEMSCOPE_FLYWAY_TEST_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/${DB}" \
  MEMSCOPE_FLYWAY_TEST_USER="$PG_USER" \
  MEMSCOPE_FLYWAY_TEST_PASSWORD="$PG_PASSWORD" \
  mvn -B test -Dsurefire.runOrder="$RUN_ORDER" -Dsurefire.runOrder.random.seed="$seed" \
    > "$LOG_DIR/round-${round}.log" 2>&1
  status=$?
  set -e
  cd "$ROOT"

  round_dir="$LOG_DIR/round-${round}-reports"
  mkdir -p "$round_dir"
  if [[ -d "$ROOT/backend/target/surefire-reports" ]]; then
    cp "$ROOT/backend/target/surefire-reports"/TEST-*.xml "$round_dir"/ 2>/dev/null || true
  fi
  round_dirs+=("$round_dir")
  note "round $round: mvn exit=$status, $(ls "$round_dir" | wc -l | tr -d ' ') report files"
  if [[ "$status" -ne 0 ]]; then
    note "round $round failed; see $LOG_DIR/round-${round}.log"
  fi
done

# ---------------------------------------------------------------------------
# 4. compare outcomes
# ---------------------------------------------------------------------------
step "4/4 Compare per-test outcomes"

python3 - "$LOG_DIR" "${round_dirs[@]}" <<'PY'
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

log_dir = sys.argv[1]
dirs = sys.argv[2:]
rounds = []
for directory in dirs:
    outcomes = {}
    for path in glob.glob(os.path.join(directory, "TEST-*.xml")):
        tree = ET.parse(path)
        root = tree.getroot()
        classname_default = root.get("name", "")
        for case in root.iter("testcase"):
            key = (case.get("classname", classname_default), case.get("name", ""))
            if key in outcomes:
                # Parameterised runs with the same display name are separate
                # executions; keep the worst outcome so nothing is hidden.
                pass
            status = "pass"
            if case.find("failure") is not None:
                status = "fail"
            elif case.find("error") is not None:
                status = "error"
            elif case.find("skipped") is not None:
                status = "skip"
            if key in outcomes and outcomes[key] != "pass" and status == "pass":
                continue
            outcomes[key] = status
    rounds.append(outcomes)

all_keys = set().union(*rounds) if rounds else set()
flaky = []
missing = []
always_failed = []
stable = 0
for key in sorted(all_keys):
    seen = [r.get(key) for r in rounds]
    present = [s for s in seen if s is not None]
    if len(present) != len(rounds):
        missing.append((key, seen))
        continue
    if all(s == "pass" for s in present):
        stable += 1
    elif any(s == "pass" for s in present) and any(s != "pass" for s in present):
        flaky.append((key, seen))
    else:
        always_failed.append((key, seen))

summary = {
    "rounds": len(rounds),
    "tests_seen": len(all_keys),
    "stable_pass": stable,
    "flaky": [{"test": ".".join(k), "outcomes": v} for k, v in flaky],
    "missing_in_some_rounds": [{"test": ".".join(k), "outcomes": v} for k, v in missing],
    "failed_in_every_round": [{"test": ".".join(k), "outcomes": v} for k, v in always_failed],
}
with open(os.path.join(log_dir, "stability.json"), "w", encoding="utf-8") as handle:
    json.dump(summary, handle, ensure_ascii=False, indent=2)
    handle.write("\n")

print("tests seen          : %d" % summary["tests_seen"])
print("stable pass         : %d" % summary["stable_pass"])
print("flaky               : %d" % len(flaky))
print("missing in some     : %d" % len(missing))
print("failed every round  : %d" % len(always_failed))
for label, entries in (("FLAKY", flaky), ("MISSING", missing), ("FAILED-ALL", always_failed)):
    for key, seen in entries[:20]:
        print("  %-9s %s  %s" % (label, ".".join(key), seen))
    if len(entries) > 20:
        print("  %-9s ... %d more (see stability.json)" % (label, len(entries) - 20))
sys.exit(1 if (flaky or missing) else 0)
PY

status=$?
printf '\nreport: %s/stability.json\n' "$LOG_DIR"
exit "$status"
