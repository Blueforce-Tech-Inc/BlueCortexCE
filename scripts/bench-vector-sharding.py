#!/usr/bin/env python3
"""3M feasibility: one HNSW graph per scope shard vs one graph for the whole table.

The measured constants from docs/archive/2026-11-05_write-path-scale.md are
8,192 bytes of index per vector row and a build that needs
`maintenance_work_mem` >= the graph size to avoid spilling. At 3M rows that is a
24 GB graph and a 24 GB build — larger than this host, and larger than most
single-node deployments. This harness measures the alternative at full 3M
capacity:

* 3,000,000 distinct 1024-d vectors (same construction as bench-hnsw-build.py:
  256 centres + a 20,001-vector noise pool, both advancing every row) split
  across eight shard tables, 375,000 rows each — the shape a scope-partitioned
  or separate vector table would have;
* eight HNSW builds, one per shard, each with `maintenance_work_mem = 4GB`
  (one shard's graph fits, the whole 3M graph does not);
* 200 in-database queries per shard with `hnsw.ef_search = 40`, timed with
  `clock_timestamp()` so the measurement is the index, not the client;
* the same 200 queries against the already-built 1M single-table graph in
  `cortexce_ops_1m` (when that database exists) for a like-for-like latency
  number at the largest single-graph size this host can hold.

Raw results go to /tmp/vec-shards-3m.json. The database is dropped unless
--keep is passed.

Usage:
    python3 scripts/bench-vector-sharding.py            # full 3M
    python3 scripts/bench-vector-sharding.py --rows 1000000 --shards 8
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
DB_NAME = "cortexce_vec_shards_bench"
CENTERS = 256
NOISE_POOL = 20_001


def psql(sql: str, database: str = DB_NAME, timeout: int = 7200, check: bool = True) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", database,
         "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, timeout=timeout,
    )
    if check and result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}")
    return result.stdout.strip()


def prepare(rows: int, shards: int) -> None:
    subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", "postgres",
         "-v", "ON_ERROR_STOP=1",
         "-c", f"DROP DATABASE IF EXISTS {DB_NAME} WITH (FORCE);",
         "-c", f"CREATE DATABASE {DB_NAME};"],
        text=True, capture_output=True, check=True, timeout=300,
    )
    psql("CREATE EXTENSION IF NOT EXISTS vector;")
    psql(
        "DROP TABLE IF EXISTS centers; DROP TABLE IF EXISTS noises;\n"
        "CREATE TABLE centers (id int primary key, v vector(1024));\n"
        f"INSERT INTO centers SELECT g, ARRAY(SELECT random() + 0*g "
        f"FROM generate_series(1,1024))::vector FROM generate_series(1,{CENTERS}) g;\n"
        "CREATE TABLE noises (id int primary key, v vector(1024));\n"
        f"INSERT INTO noises SELECT g, ARRAY(SELECT random()*0.05 + 0*g "
        f"FROM generate_series(1,1024))::vector FROM generate_series(1,{NOISE_POOL}) g;\n"
        "ANALYZE centers; ANALYZE noises;")
    per_shard = rows // shards
    for shard in range(shards):
        psql(f"DROP TABLE IF EXISTS vec_p{shard};\n"
             f"CREATE TABLE vec_p{shard} (id bigint PRIMARY KEY, v vector(1024));")
    # The per-shard row offset keeps every shard's (centre, noise) walk distinct,
    # so shards do not share duplicate vectors.
    for shard in range(shards):
        base = shard * per_shard
        psql(f"SET maintenance_work_mem = '256MB';\n"
             f"SET synchronous_commit = off;\n"
             f"INSERT INTO vec_p{shard} (id, v)\n"
             f"SELECT {base} + s, c.v + n.v\n"
             f"FROM generate_series(1, {per_shard}) s\n"
             f"JOIN centers c ON c.id = 1 + s % {CENTERS}\n"
             f"JOIN noises n ON n.id = 1 + s % {NOISE_POOL};")


def build_shard(shard: int, work_mem: str, workers: int) -> dict:
    started = time.perf_counter()
    psql(f"SET maintenance_work_mem = '{work_mem}';\n"
         f"SET max_parallel_maintenance_workers = {workers};\n"
         f"DROP INDEX IF EXISTS idx_vec_p{shard};\n"
         f"CREATE INDEX idx_vec_p{shard} ON vec_p{shard} USING hnsw (v vector_cosine_ops) "
         f"WITH (m = 16, ef_construction = 64);", timeout=7200)
    seconds = round(time.perf_counter() - started, 1)
    size_bytes = int(psql(f"SELECT pg_relation_size('idx_vec_p{shard}');"))
    return {"shard": shard, "seconds": seconds, "index_bytes": size_bytes}


def query_latency(tables: list[str], repeats: int, ef_search: int,
                  database: str = DB_NAME, column: str = "v",
                  temp_catalog: bool = False) -> dict:
    """Time queries inside the server so client spawn overhead is excluded.

    The query vector is the same (centre + noise) construction the rows use, so
    the probe is a plausible nearest neighbour rather than a random point. When
    `temp_catalog` is set the generator lives in temp tables for this session:
    the single-table comparison database has no `centers` / `noises` tables, and
    creating them permanently there would change what is being measured.
    """
    statements = [f"SET hnsw.ef_search = {ef_search};", "DROP TABLE IF EXISTS qtiming;",
                  "CREATE TEMP TABLE qtiming (table_name text, ms double precision);"]
    if temp_catalog:
        statements.extend([
            "CREATE TEMP TABLE centers (id int primary key, v vector(1024));",
            f"INSERT INTO centers SELECT g, ARRAY(SELECT random() + 0*g "
            f"FROM generate_series(1,1024))::vector FROM generate_series(1,{CENTERS}) g;",
            "CREATE TEMP TABLE noises (id int primary key, v vector(1024));",
            f"INSERT INTO noises SELECT g, ARRAY(SELECT random()*0.05 + 0*g "
            f"FROM generate_series(1,1024))::vector FROM generate_series(1,{NOISE_POOL}) g;",
        ])
    for table in tables:
        statements.append(
            "DO $$ DECLARE q vector(1024); t0 timestamptz; dummy text[]; i int; BEGIN\n"
            f"  FOR i IN 1..{repeats} LOOP\n"
            f"    SELECT (SELECT v FROM centers WHERE id = 1 + i % {CENTERS}) + "
            f"(SELECT v FROM noises WHERE id = 1 + i % {NOISE_POOL}) INTO q;\n"
            "    t0 := clock_timestamp();\n"
            f"    SELECT array_agg(id::text) INTO dummy FROM (SELECT id FROM {table} "
            f"ORDER BY {column} <=> q LIMIT 10) s;\n"
            "    INSERT INTO qtiming VALUES ('" + table + "', "
            "extract(epoch FROM clock_timestamp() - t0) * 1000);\n"
            "  END LOOP;\nEND $$;")
    statements.append(
        "SELECT table_name || '|' || "
        "percentile_cont(0.5) WITHIN GROUP (ORDER BY ms) || '|' || "
        "percentile_cont(0.95) WITHIN GROUP (ORDER BY ms) || '|' || max(ms) "
        "FROM qtiming GROUP BY table_name ORDER BY table_name;")
    out = psql("\n".join(statements), database=database, timeout=3600)
    result = {}
    for line in out.splitlines():
        parts = line.split("|")
        if len(parts) == 4:
            result[parts[0]] = {"p50_ms": round(float(parts[1]), 2),
                                "p95_ms": round(float(parts[2]), 2),
                                "max_ms": round(float(parts[3]), 2)}
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=3_000_000)
    parser.add_argument("--shards", type=int, default=8)
    parser.add_argument("--work-mem", default="4GB")
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--queries", type=int, default=200)
    parser.add_argument("--out", default="/tmp/vec-shards-3m.json")
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()

    results = {"rows": args.rows, "shards": args.shards,
               "work_mem": args.work_mem, "workers": args.workers,
               "container": CONTAINER, "builds": [], "queries": {}}
    load_started = time.perf_counter()
    prepare(args.rows, args.shards)
    results["load_seconds"] = round(time.perf_counter() - load_started, 1)
    results["rows_per_shard"] = args.rows // args.shards
    total_rows = int(psql(f"SELECT count(*) FROM vec_p0;")) * args.shards
    results["loaded_rows"] = total_rows
    print(f"loaded {total_rows:,} rows across {args.shards} shards in "
          f"{results['load_seconds']}s", flush=True)

    for shard in range(args.shards):
        entry = build_shard(shard, args.work_mem, args.workers)
        results["builds"].append(entry)
        print(f"shard {shard}: {entry['seconds']}s {entry['index_bytes']} bytes",
              flush=True)
        Path(args.out).write_text(json.dumps(results, indent=2) + "\n")

    ascii_shards = [f"vec_p{shard}" for shard in range(args.shards)]
    results["queries"]["shards"] = query_latency(ascii_shards, args.queries, 40)
    if psql("SELECT 1;", database="cortexce_ops_1m", check=False) == "1":
        results["queries"]["single_1m"] = query_latency(
            ["mem_observations"], args.queries, 40, database="cortexce_ops_1m",
            column="embedding_1024", temp_catalog=True)
        results["single_1m_rows"] = int(
            psql("SELECT count(*) FROM mem_observations;", database="cortexce_ops_1m"))
    results["total_index_bytes"] = sum(b["index_bytes"] for b in results["builds"])
    results["build_seconds_total"] = round(sum(b["seconds"] for b in results["builds"]), 1)
    results["peak_maintenance_work_mem"] = args.work_mem
    Path(args.out).write_text(json.dumps(results, indent=2) + "\n")

    if not args.keep:
        subprocess.run(["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER,
                        "-d", "postgres", "-c",
                        f"DROP DATABASE IF EXISTS {DB_NAME} WITH (FORCE);"],
                       capture_output=True, text=True)
    print(f"raw: {args.out}")
    print(json.dumps({k: results[k] for k in
                      ("loaded_rows", "build_seconds_total", "total_index_bytes",
                       "peak_maintenance_work_mem", "queries")}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
