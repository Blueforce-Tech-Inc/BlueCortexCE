#!/usr/bin/env python3
"""Measure the write path at scale: bulk import batch size, concurrency and HNSW upkeep.

Manual harness, not part of the gate. It starts a throwaway database and the real
jar with no model provider configured (the import path needs none), then:

1. Probes the default 2 MiB body limit: sends increasing batches until the endpoint
   answers 413, with and without 1024-d vectors, and records the largest accepted
   batch plus the measured bytes per row.
2. Restarts the app with a 64 MiB limit and measures rows/second for batch sizes
   100 / 500 / 1000 / 2000 (three repetitions each).
3. Measures concurrent import throughput at 1 / 4 / 8 clients.
4. Fills the table to ~1,000,000 rows without vectors, then imports vector-bearing
   batches again so the per-row HNSW upkeep at that size is measured rather than
   extrapolated.

The row shape is a real observation: one session, unique title and createdAt
(the duplicate key), a short narrative, concepts/facts JSON, no provider calls.
Vectors are 1024 floats as the production embedding model emits them.

Raw results go to /tmp/write-path.json. The database and the app are removed at
the end unless --keep is passed.

Usage:
    python3 scripts/bench-write-path.py
    python3 scripts/bench-write-path.py --jar backend/target/cortex-ce-0.1.0.jar
"""

from __future__ import annotations

import argparse
import json
import os
import random
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = os.environ.get("WRITE_PG_CONTAINER", "cortexce-hnsw-bench-pg")
DB_PORT = int(os.environ.get("WRITE_DB_PORT", "55440"))
DB_USER = "postgres"
DB_PASSWORD = "123456"
DB_NAME = "cortexce_write_bench"
APP_PORT = int(os.environ.get("WRITE_APP_PORT", "18131"))
APP_LOG = Path("/tmp/write-path-app.log")
SESSION = "write-bench-session"
PROJECT = "/tmp/write-bench"
MAX_BODY_DEFAULT = 2 * 1024 * 1024


def psql(sql: str, database: str = "postgres", timeout: int = 3600) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", database,
         "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, timeout=timeout,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}")
    return result.stdout.strip()


def prepare_database() -> None:
    psql(f"DROP DATABASE IF EXISTS {DB_NAME} WITH (FORCE);")
    psql(f"CREATE DATABASE {DB_NAME};")


def jar_path(override: str = "") -> Path:
    if override:
        path = Path(override).resolve()
        if not path.is_file():
            raise SystemExit(f"--jar {override} does not exist")
        return path
    jars = sorted((ROOT / "backend" / "target").glob("cortex-ce-*.jar"))
    jars = [jar for jar in jars if not jar.name.endswith("-sources.jar")]
    if not jars:
        raise SystemExit("Build the jar first: cd backend && mvn -o -B -DskipTests package")
    return jars[-1]


def start_app(body_limit: int, jar: str = "") -> subprocess.Popen:
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(APP_PORT),
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{DB_NAME}",
        "SPRING_DATASOURCE_USERNAME": DB_USER,
        "SPRING_DATASOURCE_PASSWORD": DB_PASSWORD,
        "CORTEXCE_HTTP_MAX_BODY_BYTES": str(body_limit),
        "SERVER_MAX_SWALLOW_SIZE": "80MB",
        "CORTEXCE_HTTP_RATE_LIMIT_ENABLED": "false",
        "MEMORY_REFINE_ENABLED": "false",
        "EXTRACTION_ENABLED": "false",
        "APP_PENDING_MESSAGE_SCHEDULE_INTERVAL_MS": "999999999",
        "CLAUDEMEM_EMBEDDING_BACKFILL_INTERVAL_MS": "999999999",
    })
    for key in ("SPRING_AI_OPENAI_API_KEY", "SPRING_AI_ANTHROPIC_API_KEY",
                "OPENAI_API_KEY", "ANTHROPIC_API_KEY"):
        env.pop(key, None)
    log = open(APP_LOG, "a")
    return subprocess.Popen(["java", "-Xmx1g", "-jar", str(jar_path(jar))],
                            cwd=str(ROOT), env=env, stdout=log, stderr=subprocess.STDOUT)


def stop_app(process: subprocess.Popen) -> None:
    process.terminate()
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        process.kill()


def wait_health(process: subprocess.Popen, timeout: float = 180) -> float:
    started = time.perf_counter()
    deadline = time.time() + timeout
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit(f"Service exited early; see {APP_LOG}")
        try:
            with urllib.request.urlopen(
                    f"http://127.0.0.1:{APP_PORT}/api/health", timeout=2) as response:
                if response.status == 200:
                    return round(time.perf_counter() - started, 1)
        except Exception:
            time.sleep(1)
    raise SystemExit("Service did not become healthy in time")


def post(path: str, payload, timeout: float = 120) -> tuple[int, str, float, int]:
    data = json.dumps(payload).encode()
    req = urllib.request.Request(
        f"http://127.0.0.1:{APP_PORT}{path}", data=data, method="POST",
        headers={"Content-Type": "application/json"})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read().decode(errors="replace")
            status = response.status
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")
        status = error.code
    return status, body, round(time.perf_counter() - started, 1), len(data)


def ensure_session() -> None:
    status, body, _elapsed, _size = post("/api/import/sessions", [{
        "content_session_id": SESSION,
        "platform_source": "claude",
        "project": PROJECT,
        "started_at_epoch": int(time.time() * 1000) - 3600_000,
        "status": "active",
    }])
    if status != 200:
        raise SystemExit(f"session import failed: {status} {body[:200]}")


def rows(count: int, offset: int = 0, with_vector: bool = False, seed: int = 0) -> list:
    rng = random.Random(seed)
    base = int(time.time() * 1000) - 10_000_000
    out = []
    for index in range(count):
        number = offset + index
        row = {
            "session_id": SESSION,
            "platform_source": "claude",
            "project": PROJECT,
            "type": "feature" if number % 3 else "bugfix",
            "title": f"write bench observation {number}",
            "content": "The queue handler rejects the batch when the drain deadline "
                       "expires; the fix claims rows before shutdown and retries the "
                       "unclaimed remainder on the next start.",
            "facts": json.dumps([f"fact {number}", "drain deadline expires"]),
            "concepts": json.dumps(["shutdown", "queue", f"n{number % 97}"]),
            "files_read": json.dumps(["proxy/wrapper.js"]),
            "files_modified": json.dumps(["backend/src/main/java/QueueService.java"]),
            "prompt_number": number % 50,
            "created_at_epoch": base + number,
            "discovery_tokens": 120 + number % 30,
            "source": "api",
        }
        if with_vector:
            row["embedding_1024"] = [round(rng.uniform(-1, 1), 6) for _ in range(1024)]
            row["embedding_model_id"] = "bench-1024"
        out.append(row)
    return out


def measure_batch(batch: int, with_vector: bool, repetitions: int, start: int,
                  with_rate: bool = True) -> list:
    runs = []
    for repetition in range(repetitions):
        payload = rows(batch, start, with_vector, seed=start + repetition)
        status, body, elapsed, size = post("/api/import/observations", payload)
        try:
            imported = int(json.loads(body).get("imported", 0)) if body.startswith("{") else 0
        except Exception:
            imported = 0
        runs.append({
            "batch": batch, "rep": repetition, "http": status,
            "bytes": size, "seconds": elapsed, "imported": imported,
            "rows_per_s": round(imported / elapsed, 1) if elapsed and imported else 0,
        })
        start += batch
    return runs


def probe_default_limit() -> dict:
    """Largest batch the 2 MiB default accepts, with and without vectors."""
    probe = {}
    for label, with_vector in (("no_vector", False), ("vector_1024", True)):
        accepted = 0
        sizes = []
        for batch in (10, 25, 50, 100, 200, 400, 800):
            status, _body, _elapsed, size = post("/api/import/observations",
                                                 rows(batch, 900_000, with_vector, seed=batch))
            sizes.append({"batch": batch, "http": status, "bytes": size})
            if status == 200:
                accepted = batch
            else:
                break
        largest = next((entry for entry in sizes if entry["http"] == 200), None)
        probe[label] = {
            "attempts": sizes,
            "largest_accepted": accepted,
            "bytes_per_row": round(largest["bytes"] / largest["batch"], 1) if largest else None,
        }
    return probe


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default="")
    parser.add_argument("--out", default="/tmp/write-path.json")
    parser.add_argument("--batch-sizes", default="100,500,1000,2000")
    parser.add_argument("--concurrency", default="1,4,8")
    parser.add_argument("--fill", type=int, default=1_000_000,
                        help="rows to load before the at-scale vector measurement")
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()

    prepare_database()
    result: dict = {"database": DB_NAME, "container": CONTAINER, "steps": {}}
    process = start_app(MAX_BODY_DEFAULT, args.jar)
    try:
        result["health_seconds"] = wait_health(process)
        ensure_session()
        result["steps"]["default_body_limit"] = probe_default_limit()
        print(f"default body limit: {json.dumps(result['steps']['default_body_limit'])}",
              flush=True)
        stop_app(process)

        process = start_app(64 * 1024 * 1024, args.jar)
        result["health_seconds_raised"] = wait_health(process)
        start = 1_000
        scaling = []
        for batch in [int(value) for value in args.batch_sizes.split(",")]:
            runs = measure_batch(batch, False, 3, start)
            start += batch * 3
            scaling.append({"batch": batch, "runs": runs})
            print(f"batch {batch}: " + ", ".join(
                f"{run['rows_per_s']} rows/s ({run['seconds']} s, {run['http']})"
                for run in runs), flush=True)
        result["steps"]["batch_scaling"] = scaling

        # Concurrency: the median batch from the scaling phase, 20 s per level.
        concurrency_rows = []
        for clients in [int(value) for value in args.concurrency.split(",")]:
            import threading
            stop_at = time.time() + 20
            counters = {"requests": 0, "imported": 0, "errors": 0}
            lock = threading.Lock()

            def worker(worker_id: int) -> None:
                nonlocal start
                while time.time() < stop_at:
                    # The offset must be claimed under the lock: two clients reading
                    # the same `start` would write the same title and the second batch
                    # would be classified as duplicates, understating throughput.
                    with lock:
                        offset = start
                        start += batch
                    status, body, _elapsed, _size = post("/api/import/observations",
                                                         rows(batch, offset, False,
                                                              seed=offset))
                    with lock:
                        counters["requests"] += 1
                        if status != 200:
                            counters["errors"] += 1
                        else:
                            try:
                                counters["imported"] += int(json.loads(body).get("imported", 0))
                            except Exception:
                                pass

            threads = [threading.Thread(target=worker, args=(index,))
                       for index in range(clients)]
            began = time.time()
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            elapsed = time.time() - began
            concurrency_rows.append({
                "clients": clients, "batch": batch, "seconds": round(elapsed, 1),
                "requests": counters["requests"], "imported": counters["imported"],
                "errors": counters["errors"],
                "rows_per_s": round(counters["imported"] / elapsed, 1),
            })
            print(f"concurrency {clients}: {concurrency_rows[-1]['rows_per_s']} rows/s "
                  f"({counters['imported']} rows, {counters['errors']} errors)", flush=True)
        result["steps"]["concurrency"] = concurrency_rows

        # At-scale vector import (HNSW upkeep). Fill to the target total without
        # vectors first: those rows do not enter the HNSW graph, so the fill measures
        # the heap path and the vector batches after it measure the graph path at
        # that row count.
        existing = int(psql("SELECT count(*) FROM mem_observations;", DB_NAME))
        fill_started = time.perf_counter()
        filled = 0
        fill_offset = 2_000_000
        while existing + filled < args.fill:
            status, body, _elapsed, _size = post("/api/import/observations",
                                                 rows(1000, fill_offset, False,
                                                      seed=fill_offset))
            if status != 200:
                raise SystemExit(f"fill import failed: {status} {body[:200]}")
            filled += int(json.loads(body).get("imported", 0))
            fill_offset += 1000
        fill_seconds = round(time.perf_counter() - fill_started, 1)
        result["steps"]["fill"] = {
            "rows_added": filled, "seconds": fill_seconds,
            "rows_per_s": round(filled / fill_seconds, 1) if fill_seconds else 0,
            "table_rows": int(psql("SELECT count(*) FROM mem_observations;", DB_NAME)),
        }
        print(f"fill: {result['steps']['fill']}", flush=True)

        vector_rows = []
        vector_start = fill_offset
        for batch in (50, 100, 200):
            runs = measure_batch(batch, True, 2, vector_start)
            vector_start += batch * 2
            vector_rows.append({"batch": batch, "runs": runs})
            print(f"vector batch {batch}: " + ", ".join(
                f"{run['rows_per_s']} rows/s ({run['seconds']} s, {run['http']})"
                for run in runs), flush=True)
        result["steps"]["vector_batches_at_scale"] = vector_rows
        result["steps"]["index"] = {
            "ddl": psql("SELECT indexdef FROM pg_indexes WHERE tablename='mem_observations' "
                        "AND indexname='idx_obs_embedding_1024';", DB_NAME) or "(none)",
            "size": psql("SELECT pg_size_pretty(pg_relation_size('idx_obs_embedding_1024'));",
                         DB_NAME),
        }
    finally:
        if process.poll() is None:
            stop_app(process)
        if not args.keep:
            psql(f"DROP DATABASE IF EXISTS {DB_NAME} WITH (FORCE);")

    Path(args.out).write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    print(f"raw: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
