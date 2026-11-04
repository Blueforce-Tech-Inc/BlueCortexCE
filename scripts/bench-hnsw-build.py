#!/usr/bin/env python3
"""Benchmark pgvector HNSW build cost at 100k / 300k / 1M vector rows.

Manual harness: nothing here runs in CI. It builds a throwaway database
(`cortexce_hnsw_bench`) on a dedicated container and times
`CREATE INDEX ... USING hnsw (m=16, ef_construction=64)` — the same parameters as
migration V2 — under different `maintenance_work_mem` and
`max_parallel_maintenance_workers` settings.

Rows are **distinct** 1024-d vectors generated inside PostgreSQL: 256 cluster
centres and a pool of 20,001 noise vectors. The centre is `s % 256` and the noise
is `s % 20,001`, so **every** measured size sees the same distribution over all
256 centres — an earlier version advanced the centre every 20,000 rows
(`(s / 20000) % 256`), which gave the 100k table only 5 centres and the 300k
table 15. That made per-row cost fall as the table grew and the sizes were not
comparable. The pair repeats after lcm(256, 20001) = 5,120,256 rows, past every
size measured here. Uniform-random vectors would make every pair equidistant;
clustered vectors mimic embeddings, where neighbours sit close together. An
even earlier version replicated a 50k base set, which made the graph cheap to
build (duplicate vectors converge in a couple of hops); those numbers are
discarded.

3M is not built here: the Docker VM has 16.8 GB, and 3M rows need ~12 GB of
vectors plus a comparable index, so the report derives the 3M line from the
measured per-row cost and says so.

A dedicated container keeps the numbers free of CPU contention from other
suites; `BENCH_PG_CONTAINER` selects it. Everything is drop-and-recreate, so
any existing database of that name is destroyed.

Usage:
    python3 scripts/bench-hnsw-build.py --sizes 100000,300000,1000000

Writes /tmp/hnsw-bench.json (raw) and prints a table.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import time
from pathlib import Path

CONTAINER = os.environ.get("BENCH_PG_CONTAINER", "claude-mem-test-pg")
DB_USER = "postgres"
DB_NAME = "cortexce_hnsw_bench"
CENTERS = 256
NOISE_POOL = 20_001


def psql(sql: str, timeout: int = 7200, check: bool = True) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME,
         "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, timeout=timeout,
    )
    if check and result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}")
    return result.stdout.strip()


def prepare_database() -> None:
    subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", "postgres",
         "-v", "ON_ERROR_STOP=1",
         "-c", f"DROP DATABASE IF EXISTS {DB_NAME} WITH (FORCE);",
         "-c", f"CREATE DATABASE {DB_NAME};"],
        text=True, capture_output=True, check=True, timeout=300,
    )
    psql("CREATE EXTENSION IF NOT EXISTS vector;")
    # Scalar subqueries that do not reference the generate_series variable are
    # evaluated once (PostgreSQL hoists them), so the "+ 0*g" term is what makes
    # each centre and each noise vector distinct. Without it every row was the
    # same vector and the build collapsed to a few milliseconds per thousand rows.
    psql(
        "DROP TABLE IF EXISTS centers;\n"
        "DROP TABLE IF EXISTS noises;\n"
        "CREATE TABLE centers (id int primary key, v vector(1024));\n"
        f"INSERT INTO centers SELECT g, ARRAY(SELECT random() + 0*g "
        f"FROM generate_series(1,1024))::vector FROM generate_series(1,{CENTERS}) g;\n"
        "CREATE TABLE noises (id int primary key, v vector(1024));\n"
        f"INSERT INTO noises SELECT g, ARRAY(SELECT random()*0.05 + 0*g "
        f"FROM generate_series(1,1024))::vector FROM generate_series(1,{NOISE_POOL}) g;\n")
    psql("ANALYZE centers; ANALYZE noises;")


def load_rows(rows: int) -> int:
    psql("DROP TABLE IF EXISTS bench;")
    psql("CREATE TABLE bench (id uuid PRIMARY KEY, embedding_1024 vector(1024));")
    # Centre and noise both advance every row, so each size spreads evenly over
    # all 256 centres and a (centre, noise) pair repeats only after
    # lcm(256, 20001) = 5,120,256 rows — past every size measured here. Keep SET
    # and INSERT in one psql process: SET is session-scoped.
    psql("SET synchronous_commit = off;\n"
         "SET maintenance_work_mem = '256MB';\n"
         "INSERT INTO bench (id, embedding_1024)\n"
         "SELECT gen_random_uuid(),\n"
         f"       (SELECT v FROM centers WHERE id = 1 + s % {CENTERS})\n"
         f"       + (SELECT v FROM noises WHERE id = 1 + s % {NOISE_POOL})\n"
         f"FROM generate_series(1, {rows}) s;")
    psql("ANALYZE bench;")
    return int(psql("SELECT count(*) FROM bench;"))


def build(work_mem: str, workers: int) -> dict:
    # SET is session-scoped, and every psql call here is a separate process, so
    # the settings must travel in the same statement stream as CREATE INDEX.
    # (Sending them separately silently measured the container's stock 64MB.)
    script = (
        f"SET maintenance_work_mem = '{work_mem}';\n"
        f"SET max_parallel_maintenance_workers = {workers};\n"
        "CREATE INDEX bench_hnsw ON bench USING hnsw (embedding_1024 vector_cosine_ops) "
        "WITH (m = 16, ef_construction = 64);\n"
    )
    started = time.perf_counter()
    psql(script, timeout=7200)
    seconds = round(time.perf_counter() - started, 1)
    size = psql("SELECT pg_size_pretty(pg_relation_size('bench_hnsw'));")
    size_bytes = int(psql("SELECT pg_relation_size('bench_hnsw');"))
    psql("DROP INDEX bench_hnsw;")
    return {"maintenance_work_mem": work_mem, "workers": workers,
            "seconds": seconds, "index_size": size, "index_bytes": size_bytes}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sizes", default="100000,1000000")
    parser.add_argument("--out", default="/tmp/hnsw-bench.json")
    args = parser.parse_args()

    requested = [int(value) for value in args.sizes.split(",") if value.strip()]
    prepare_database()
    results = {"centers": CENTERS, "noise_pool": NOISE_POOL,
               "container": CONTAINER, "sizes": {}}
    if Path(args.out).exists():
        # Sizes measured on an earlier invocation are kept, so a long run can be
        # split (100k here, 1M later) without losing the earlier numbers. A size
        # that is re-run replaces its own entry.
        previous = json.loads(Path(args.out).read_text())
        for size, entry in (previous.get("sizes") or {}).items():
            results["sizes"][size] = entry
    # 64MB (the stock default) is measured last in the 100k phase: with the
    # graph spilling it is an order of magnitude slower, and it is the number
    # that justifies the recommendation. 1M is only built at a setting that
    # fits the 16.8 GB Docker VM; a second 1M run costs an hour and does not
    # change the recommendation.
    schedule = [
        (100_000, [("1GB", 2), ("2GB", 2), ("64MB", 0)]),
        (300_000, [("2GB", 2), ("4GB", 2)]),
        (1_000_000, [("4GB", 2)]),
    ]
    for size, settings in schedule:
        if size not in requested:
            continue
        load_started = time.perf_counter()
        rows = load_rows(size)
        load_seconds = round(time.perf_counter() - load_started, 1)
        if rows != size:
            raise SystemExit(f"loaded {rows} rows for requested size {size}")
        runs = []
        for work_mem, workers in settings:
            runs.append(build(work_mem, workers))
            results["sizes"][str(size)] = {"rows": rows, "load_seconds": load_seconds,
                                           "runs": runs}
            # Written after every run so a long 1M build still leaves the
            # completed numbers on disk if this is interrupted.
            Path(args.out).write_text(json.dumps(results, indent=2) + "\n")
            print(f"{size:,} rows: {work_mem}/{workers}w {runs[-1]['seconds']}s "
                  f"{runs[-1]['index_size']} (load {load_seconds}s)", flush=True)
    print(f"raw: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
