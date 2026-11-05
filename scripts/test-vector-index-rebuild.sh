#!/usr/bin/env bash
# =============================================================================
# Prove the vector-index rebuild path is re-runnable (manual; needs Docker).
#
# Covers the three properties the "load bulk rows first, build the graph once"
# recipe depends on, against scripts/rebuild-vector-index.sh:
#
#   A. Idempotent      a second run finds a valid index and does nothing
#   B. Interrupt       a build killed mid-way leaves no usable index; the next
#                      run detects the invalid index, drops it and rebuilds
#   C. Concurrent      with CONCURRENTLY=1, writes keep succeeding while the
#                      graph is built (latency recorded, no failures)
#
# Usage:
#   scripts/test-vector-index-rebuild.sh              # 120,000 rows (~2 min)
#   ROWS=30000 scripts/test-vector-index-rebuild.sh   # quick pass
#
# Environment:
#   CONTAINER   postgres container (default claude-mem-test-pg)
#   DB          scratch database (default cortexce_idx_rebuild_it; dropped)
#   ROWS        rows to build over (default 120000)
#
# Exit: 0 all cases pass, 1 a case failed (the log names it).
# =============================================================================

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="${CONTAINER:-claude-mem-test-pg}"
DB="${DB:-cortexce_idx_rebuild_it}"
ROWS="${ROWS:-120000}"
INDEX="idx_obs_embedding_1024"
RESULTS="$(mktemp -t idxrebuild.XXXXXX)"
trap 'rm -f "$RESULTS"' EXIT

log() { printf '[index-rebuild-test] %s\n' "$*"; }
pass() { printf 'PASS %s\n' "$*" | tee -a "$RESULTS"; }
fail() { printf 'FAIL %s\n' "$*" | tee -a "$RESULTS" >&2; }

psql_db() { # psql_db <sql>
  docker exec -i "$CONTAINER" psql -U postgres -d "$DB" -v ON_ERROR_STOP=1 -t -A -c "$1"
}

index_state() { # valid | invalid | absent
  local exists
  exists=$(psql_db "SELECT to_regclass('public.${INDEX}') IS NOT NULL;" 2>/dev/null)
  if [ "$exists" != "t" ]; then echo absent; return; fi
  local valid
  valid=$(psql_db "SELECT indisvalid FROM pg_index WHERE indexrelid='public.${INDEX}'::regclass;" 2>/dev/null)
  if [ "$valid" = "t" ]; then echo valid; else echo invalid; fi
}

wait_for_build() { # wait_for_build <timeout-seconds>
  local deadline=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if [ "$(psql_db "SELECT count(*) FROM pg_stat_activity
         WHERE datname='${DB}' AND query ILIKE 'CREATE INDEX%' AND pid <> pg_backend_pid();")" -ge 1 ]; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------
log "container=${CONTAINER} database=${DB} rows=${ROWS}"
docker exec -i "$CONTAINER" psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS ${DB} WITH (FORCE);" -c "CREATE DATABASE ${DB};" >/dev/null || {
  fail "could not create the scratch database"; exit 1; }

printf '%s\n' \
  "CREATE EXTENSION IF NOT EXISTS vector;" \
  "CREATE TABLE mem_observations (id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
     embedding_1024 vector(1024));" \
  "CREATE TABLE centers (id int PRIMARY KEY, v vector(1024));" \
  "INSERT INTO centers SELECT g, ARRAY(SELECT random() + 0*g FROM generate_series(1,1024))::vector
     FROM generate_series(1,256) g;" \
  "CREATE TABLE noises (id int PRIMARY KEY, v vector(1024));" \
  "INSERT INTO noises SELECT g, ARRAY(SELECT random()*0.05 + 0*g FROM generate_series(1,1024))::vector
     FROM generate_series(1,20001) g;" \
  "INSERT INTO mem_observations (embedding_1024)
     SELECT (SELECT v FROM centers WHERE id = 1 + s % 256) +
            (SELECT v FROM noises WHERE id = 1 + s % 20001)
     FROM generate_series(1,${ROWS}) s;" \
  "ANALYZE mem_observations;" \
  | docker exec -i "$CONTAINER" psql -U postgres -d "$DB" -v ON_ERROR_STOP=1 >/dev/null \
  || { fail "could not seed ${ROWS} rows"; exit 1; }
log "seeded $(psql_db "SELECT count(*) FROM mem_observations;") rows"

rebuild() { # rebuild <extra-env>...
  env SOURCE_DB="$DB" CONTAINER="$CONTAINER" WORK_MEM=1GB WORKERS=2 "$@" \
    bash "$ROOT/scripts/rebuild-vector-index.sh"
}

# ---------------------------------------------------------------------------
# A. First build, then an idempotent second run
# ---------------------------------------------------------------------------
START=$(date +%s)
rebuild >/tmp/index-rebuild-a1.log 2>&1
CODE=$?
SECONDS_FIRST=$(( $(date +%s) - START ))
if [ "$CODE" != "0" ] || [ "$(index_state)" != "valid" ]; then
  tail -5 /tmp/index-rebuild-a1.log >&2
  fail "A1: first build did not produce a valid index (exit ${CODE}, state $(index_state))"
else
  pass "A1: built in ${SECONDS_FIRST}s, index valid ($(psql_db "SELECT pg_relation_size('public.${INDEX}'::regclass);") bytes)"
fi

START=$(date +%s)
rebuild >/tmp/index-rebuild-a2.log 2>&1
CODE=$?
SECONDS_SECOND=$(( $(date +%s) - START ))
if [ "$CODE" != "0" ] || ! grep -q "skip: ${INDEX} is already valid" /tmp/index-rebuild-a2.log; then
  tail -5 /tmp/index-rebuild-a2.log >&2
  fail "A2: rerun was not a no-op (exit ${CODE})"
elif [ "$SECONDS_SECOND" -gt 5 ]; then
  fail "A2: rerun took ${SECONDS_SECOND}s; it should return without building"
else
  pass "A2: rerun skipped the build in ${SECONDS_SECOND}s (idempotent)"
fi

# ---------------------------------------------------------------------------
# B. Interrupt a rebuild, then recover
# ---------------------------------------------------------------------------
python3 - "$CONTAINER" "$DB" "$ROOT" "$ROWS" >/tmp/index-rebuild-b.log 2>&1 <<'PY'
import os, subprocess, sys, time
container, db, root, rows = sys.argv[1:5]
env = dict(os.environ, SOURCE_DB=db, CONTAINER=container, FORCE="1",
           WORK_MEM="1GB", WORKERS="2")
proc = subprocess.Popen(["bash", os.path.join(root, "scripts/rebuild-vector-index.sh")],
                        env=env, stdout=open("/tmp/index-rebuild-b-inner.log", "w"),
                        stderr=subprocess.STDOUT)
# Wait until the backend running CREATE INDEX is visible, then kill it: that is
# the closest analogue to an operator aborting the build or the container being
# restarted mid-build.
deadline = time.time() + 60
pid = None
while time.time() < deadline:
    out = subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "postgres",
                          "-d", db, "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c",
                          "SELECT pid FROM pg_stat_activity WHERE datname='" + db +
                          "' AND query ILIKE 'CREATE INDEX%' AND pid <> pg_backend_pid();"],
                         capture_output=True, text=True)
    pid = (out.stdout or "").strip().splitlines()
    if pid:
        pid = pid[0]
        break
    time.sleep(0.5)
if not pid:
    proc.kill()
    print("no build session observed")
    sys.exit(1)
time.sleep(2)
subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", db,
                "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c",
                f"SELECT pg_terminate_backend({pid});"], capture_output=True, text=True)
code = proc.wait(timeout=120)
print(f"interrupted build exit={code}")
sys.exit(0 if code == 3 else 1)
PY
CODE=$?
if [ "$CODE" != "0" ] || [ "$(index_state)" != "absent" ]; then
  tail -5 /tmp/index-rebuild-b.log >&2
  fail "B1: interrupted run should exit 3 and leave no index (exit ${CODE}, state $(index_state))"
else
  pass "B1: build killed mid-way -> exit 3, partial index dropped"
fi

START=$(date +%s)
rebuild >/tmp/index-rebuild-b2.log 2>&1
CODE=$?
SECONDS_RECOVER=$(( $(date +%s) - START ))
if [ "$CODE" != "0" ] || [ "$(index_state)" != "valid" ]; then
  tail -5 /tmp/index-rebuild-b2.log >&2
  fail "B2: rerun after the interruption did not recover (exit ${CODE}, state $(index_state))"
else
  pass "B2: rerun rebuilt a valid index in ${SECONDS_RECOVER}s"
fi

# ---------------------------------------------------------------------------
# C. Writes during the build
# ---------------------------------------------------------------------------
python3 - "$CONTAINER" "$DB" "$ROOT" >/tmp/index-rebuild-c.log 2>&1 <<'PY'
import os, statistics, subprocess, sys, time
container, db, root = sys.argv[1:4]
env = dict(os.environ, SOURCE_DB=db, CONTAINER=container, FORCE="1",
           WORK_MEM="1GB", WORKERS="2", CONCURRENTLY="1")

def insert_once():
    started = time.perf_counter()
    result = subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "postgres",
                             "-d", db, "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c",
                             "INSERT INTO mem_observations (embedding_1024) "
                             "SELECT (SELECT v FROM centers WHERE id = 1 + random()*0) + "
                             "(SELECT v FROM noises WHERE id = 1 + random()*0);"],
                            capture_output=True, text=True)
    return (time.perf_counter() - started) * 1000, result.returncode

def build_active():
    out = subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "postgres",
                          "-d", db, "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c",
                          "SELECT count(*) FROM pg_stat_activity WHERE datname='" + db +
                          "' AND query ILIKE 'CREATE INDEX%' AND pid <> pg_backend_pid();"],
                          capture_output=True, text=True)
    return int((out.stdout or "0").strip() or 0) > 0

baseline = [insert_once()[0] for _ in range(5)]
proc = subprocess.Popen(["bash", os.path.join(root, "scripts/rebuild-vector-index.sh")],
                        env=env, stdout=open("/tmp/index-rebuild-c-inner.log", "w"),
                        stderr=subprocess.STDOUT)
deadline = time.time() + 60
while not build_active() and time.time() < deadline:
    time.sleep(0.5)
during, failures = [], 0
deadline = time.time() + 200
while proc.poll() is None and time.time() < deadline:
    latency, code = insert_once()
    during.append(latency)
    if code != 0:
        failures += 1
code = proc.wait(timeout=120)
count = subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", db,
                        "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c",
                        "SELECT count(*) FROM mem_observations;"],
                       capture_output=True, text=True).stdout.strip()
rows_after = int(count)
valid = subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", db,
                        "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c",
                        "SELECT indisvalid FROM pg_index WHERE indexrelid='public.idx_obs_embedding_1024'::regclass;"],
                       capture_output=True, text=True).stdout.strip()

def p95(values):
    values = sorted(values)
    return values[min(len(values) - 1, int(len(values) * 0.95))]

print(f"build_exit={code} writes_during_build={len(during)} failures={failures} "
      f"rows_after={rows_after} index_valid={valid}")
print(f"insert latency ms: baseline p50={statistics.median(baseline):.0f}, "
      f"during p50={statistics.median(during) if during else -1:.0f}, "
      f"during p95={p95(during) if during else -1:.0f}, "
      f"during max={max(during) if during else -1:.0f}")
ok = (code == 0 and failures == 0 and valid == "t" and len(during) >= 20
      and p95(during) < 1000)
sys.exit(0 if ok else 1)
PY
CODE=$?
SUMMARY=$(tail -2 /tmp/index-rebuild-c.log | tr '\n' ' ')
if [ "$CODE" != "0" ]; then
  cat /tmp/index-rebuild-c.log >&2
  fail "C: writes during the build failed the acceptance check (${SUMMARY})"
else
  pass "C: ${SUMMARY}"
fi

# ---------------------------------------------------------------------------
# Cleanup + verdict
# ---------------------------------------------------------------------------
docker exec -i "$CONTAINER" psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS ${DB} WITH (FORCE);" >/dev/null 2>&1 || true

if grep -q '^FAIL' "$RESULTS"; then
  log "RESULT: FAIL ($(grep -c '^FAIL' "$RESULTS") case(s))"
  exit 1
fi
log "RESULT: PASS (idempotent, interrupt-recoverable, writes stay open during the build)"
exit 0
