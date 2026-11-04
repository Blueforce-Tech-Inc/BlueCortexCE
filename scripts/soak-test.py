#!/usr/bin/env python3
"""One-hour mixed-rate soak against the real provider and a real PostgreSQL.

This is a manual harness, like the retrieval evaluator: it needs the keys in
backend/.env and a running pgvector container, and it does not run in CI.

It starts the packaged service on a disposable database, then drives a fixed
rate of ingest (one real extraction per --ingest-interval seconds) interleaved
with search (one request per --search-interval seconds) for --duration seconds.
Every --sample-interval seconds it scrapes /actuator/prometheus and records:

  * JVM heap used and live thread count (leak detection),
  * HikariCP active/idle/pending connections (pool exhaustion),
  * queue depth per status and the failed counter (stuck or buried work),
  * search and ingest latency.

The verdict at the end is mechanical: no monotonic heap growth beyond the
threshold, no thread growth, no connection saturation, no failed rows, no row
left processing past the stale threshold.

Usage:
    python3 scripts/soak-test.py --duration 3600
"""

from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = os.environ.get("SOAK_PG_CONTAINER", "claude-mem-test-pg")
DB_PORT = int(os.environ.get("SOAK_PG_PORT", "5433"))
DB_USER = "postgres"
DB_PASSWORD = "123456"
DATABASE = os.environ.get("SOAK_DB", f"cortexce_soak_{int(time.time())}")
PROJECT = "/tmp/cortexce-soak-project"
SESSION = "soak-session-" + str(int(time.time()))
QUERIES = (
    "检索服务怎么把中文查询和英文代码观察记录对上",
    "ObservationRepository.hybridSearchScored 的融合权重改动",
    "队列里 processing 卡住的行怎么恢复",
    "pending message 重试预算和 failure kind",
    "Flyway 迁移到最新的版本号是多少",
    "SSE 事件类型 initial_load new_observation",
    "embedding 回填任务如何避免重复调用",
    "health readiness 检查数据库和队列深度",
)

HEAP_GROWTH_LIMIT = 0.15   # >15% of the first sample is treated as growth
THREAD_GROWTH_LIMIT = 10   # live threads above this delta is a leak signal


def load_env() -> dict:
    values = {}
    for line in (ROOT / "backend/.env").read_text().splitlines():
        match = re.match(r"^([A-Z0-9_]+)=(.*)$", line.strip())
        if match:
            values[match.group(1)] = match.group(2).strip().strip('"').strip("'")
    return values


ENV = load_env()


def psql(sql: str, database: str = DATABASE, timeout: int = 120) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", database,
         "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, timeout=timeout,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}\n--- sql ---\n{sql[:2000]}")
    return result.stdout.strip()


def create_database() -> None:
    psql(f"DROP DATABASE IF EXISTS {DATABASE} WITH (FORCE);", database="postgres")
    psql(f"CREATE DATABASE {DATABASE};", database="postgres")
    psql("CREATE EXTENSION IF NOT EXISTS vector;")


def latest_jar() -> Path:
    jars = sorted((ROOT / "backend/target").glob("cortex-ce-*.jar"))
    jars = [jar for jar in jars if not jar.name.endswith("-sources.jar")]
    if not jars:
        raise SystemExit("Build the jar first: cd backend && mvn -o -B -DskipTests package")
    return jars[-1]


def start_app(port: int) -> subprocess.Popen:
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(port),
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{DATABASE}",
        "SPRING_DATASOURCE_USERNAME": DB_USER,
        "SPRING_DATASOURCE_PASSWORD": DB_PASSWORD,
        "OPENAI_BASE_URL": ENV.get("OPENAI_BASE_URL", ""),
        "OPENAI_MODEL": ENV.get("OPENAI_MODEL", ""),
        "OPENAI_API_KEY": ENV.get("OPENAI_API_KEY", ""),
        "SPRING_AI_OPENAI_API_KEY": ENV.get("OPENAI_API_KEY", ""),
        "SPRING_AI_OPENAI_BASE_URL": ENV.get("OPENAI_BASE_URL", ""),
        "SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL": ENV.get("OPENAI_MODEL", ""),
        "SPRING_AI_OPENAI_EMBEDDING_API_KEY": ENV.get("SPRING_AI_OPENAI_EMBEDDING_API_KEY", ""),
        "SPRING_AI_OPENAI_EMBEDDING_BASE_URL": ENV.get("SPRING_AI_OPENAI_EMBEDDING_BASE_URL", ""),
        "SPRING_AI_OPENAI_EMBEDDING_MODEL": ENV.get("SPRING_AI_OPENAI_EMBEDDING_MODEL", ""),
        "SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS": ENV.get("SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS", "1024"),
        "CLAUDEMEM_LLM_PROVIDER": ENV.get("CLAUDEMEM_LLM_PROVIDER", "openai"),
        "CORTEXCE_API_TOKEN": "",
        "CI": "false",
        "GITHUB_ACTIONS": "false",
    })
    log = open("/tmp/soak-app.log", "w")
    return subprocess.Popen(["java", "-Xmx768m", "-jar", str(latest_jar())],
                            env=env, stdout=log, stderr=subprocess.STDOUT)


def call(port: int, method: str, path: str, body: dict | None = None,
         timeout: float = 60) -> tuple[int, str, float]:
    started = time.time()
    payload = json.dumps(body).encode() if body is not None else None
    request = Request(f"http://127.0.0.1:{port}{path}", data=payload,
                      method=method, headers={"Content-Type": "application/json"})
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode(), time.time() - started
    except HTTPError as error:
        return error.code, error.read().decode(), time.time() - started


def wait_health(port: int, process: subprocess.Popen, timeout: float = 180) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit("service exited during startup; see /tmp/soak-app.log")
        try:
            status, _, _ = call(port, "GET", "/api/health", timeout=3)
            if status == 200:
                return
        except (URLError, OSError):
            pass
        time.sleep(1)
    raise SystemExit("service did not become healthy; see /tmp/soak-app.log")


def metric(body: str, name: str, tag: str | None = None) -> float:
    """Sum every sample of `name` whose line contains `tag`; NaN when absent."""
    total = None
    for line in body.splitlines():
        line = line.strip()
        if not line.startswith(name + "{") and not line.startswith(name + " "):
            continue
        if tag and tag not in line:
            continue
        try:
            value = float(line.rsplit(" ", 1)[-1])
        except ValueError:
            continue
        total = value if total is None else total + value
    return float("nan") if total is None else total


def sample(port: int, elapsed: float) -> dict:
    _, body, _ = call(port, "GET", "/actuator/prometheus", timeout=10)
    record = {
        "elapsed_s": round(elapsed, 1),
        "heap_used_bytes": metric(body, "jvm_memory_used_bytes", 'area="heap"'),
        "heap_max_bytes": metric(body, "jvm_memory_max_bytes", 'area="heap"'),
        "threads_live": metric(body, "jvm_threads_live_threads"),
        "hikari_active": metric(body, "hikaricp_connections_active"),
        "hikari_idle": metric(body, "hikaricp_connections_idle"),
        "hikari_pending": metric(body, "hikaricp_connections_pending"),
        "hikari_max": metric(body, "hikaricp_connections_max"),
        "queue_pending": metric(body, "cortexce_queue_depth", 'status="pending"'),
        "queue_processing": metric(body, "cortexce_queue_depth", 'status="processing"'),
        "queue_failed": metric(body, "cortexce_queue_depth", 'status="failed"'),
        "queue_stale": metric(body, "cortexce_queue_stale"),
    }
    status, health, _ = call(port, "GET", "/api/health", timeout=10)
    record["health_status"] = status
    try:
        record["health_queue_depth"] = json.loads(health)["checks"]["queueDepth"]
    except (KeyError, TypeError, ValueError):
        record["health_queue_depth"] = None
    print(f"  sample t+{record['elapsed_s']:.0f}s heap={record['heap_used_bytes']:.0f} "
          f"threads={record['threads_live']:.0f} active={record['hikari_active']:.0f} "
          f"pending={record['queue_pending']:.0f} processing={record['queue_processing']:.0f} "
          f"failed={record['queue_failed']:.0f}")
    return record


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    return ordered[min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1))))]


def verdict(samples: list[dict], search_latencies: list[float],
            ingest_failures: int, search_failures: int) -> dict:
    heaps = [s["heap_used_bytes"] for s in samples if s["heap_used_bytes"] == s["heap_used_bytes"]]
    threads = [s["threads_live"] for s in samples]
    active = [s["hikari_active"] for s in samples]
    pending_conn = [s["hikari_pending"] for s in samples]
    failed = [s["queue_failed"] for s in samples if s["queue_failed"] == s["queue_failed"]]
    stuck = int(psql(
        "SELECT count(*) FROM mem_pending_messages "
        "WHERE status = 'processing' AND started_processing_at_epoch < "
        f"{int(time.time() * 1000) - 600_000};") or 0)
    first_heap = heaps[0] if heaps else 0.0
    growth = (heaps[-1] - first_heap) / first_heap if first_heap else 0.0
    monotonic = sum(1 for before, after in zip(heaps, heaps[1:]) if after > before)
    return {
        "heap_first": first_heap,
        "heap_last": heaps[-1] if heaps else 0.0,
        "heap_growth_ratio": round(growth, 4),
        "heap_monotonic_steps": monotonic,
        "heap_steps": max(0, len(heaps) - 1),
        "threads_first": threads[0] if threads else 0,
        "threads_max": max(threads) if threads else 0,
        "hikari_active_max": max(active) if active else 0,
        "hikari_pending_max": max(pending_conn) if pending_conn else 0,
        "queue_failed_max": max(failed) if failed else 0,
        "queue_failed_final": failed[-1] if failed else 0,
        "stuck_processing": stuck,
        "ingest_failures": ingest_failures,
        "search_failures": search_failures,
        "search_p50_ms": round(percentile(search_latencies, 50) * 1000, 2),
        "search_p95_ms": round(percentile(search_latencies, 95) * 1000, 2),
        "pass_memory": growth <= HEAP_GROWTH_LIMIT,
        "pass_threads": (max(threads) - threads[0]) <= THREAD_GROWTH_LIMIT if threads else True,
        "pass_connections": max(pending_conn, default=0) == 0,
        "pass_failed": max(failed, default=0) == 0,
        "pass_stuck": stuck == 0,
        "pass_requests": ingest_failures == 0 and search_failures == 0,
    }


def render(result: dict, parser_args) -> str:
    checks = {key: value for key, value in result["verdict"].items() if key.startswith("pass_")}
    lines = [
        "# One-hour mixed-rate soak: real provider, real PostgreSQL",
        "",
        f"- Date: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- Duration: {parser_args.duration} s; ingest every {parser_args.ingest_interval} s, "
        f"search every {parser_args.search_interval} s; sample every {parser_args.sample_interval} s",
        f"- Model: real provider (`{ENV.get('OPENAI_MODEL', '')}` "
        f"at `{ENV.get('OPENAI_BASE_URL', '')}`), real embeddings; database {DATABASE}",
        f"- Verdict: {'PASS' if all(checks.values()) else 'FAIL'} "
        f"({sum(1 for value in checks.values() if value)}/{len(checks)} checks)",
        "",
        "## Samples",
        "",
        "| t (s) | heap used (MB) | threads | hikari active/idle/pending | queue pending/processing/failed | health |",
        "|---|---|---|---|---|---|",
    ]
    for row in result["samples"]:
        lines.append(
            f"| {row['elapsed_s']:.0f} | {row['heap_used_bytes'] / 1e6:.0f} | "
            f"{row['threads_live']:.0f} | {row['hikari_active']:.0f}/"
            f"{row['hikari_idle']:.0f}/{row['hikari_pending']:.0f} | "
            f"{row['queue_pending']:.0f}/{row['queue_processing']:.0f}/"
            f"{row['queue_failed']:.0f} | {row['health_status']} |")
    lines += [
        "",
        "## Checks",
        "",
    ]
    lines += [f"- {'PASS' if value else 'FAIL'} — {key[5:]}" for key, value in checks.items()]
    lines += [
        "",
        f"- Heap: first {result['verdict']['heap_first'] / 1e6:.0f} MB, last "
        f"{result['verdict']['heap_last'] / 1e6:.0f} MB, growth "
        f"{result['verdict']['heap_growth_ratio'] * 100:.1f}%, monotonic "
        f"{result['verdict']['heap_monotonic_steps']}/{result['verdict']['heap_steps']} steps",
        f"- Threads: first {result['verdict']['threads_first']}, max {result['verdict']['threads_max']}",
        f"- Hikari: active max {result['verdict']['hikari_active_max']}, pending max "
        f"{result['verdict']['hikari_pending_max']}",
        f"- Queue failed: max {result['verdict']['queue_failed_max']}, final "
        f"{result['verdict']['queue_failed_final']}, stuck processing {result['verdict']['stuck_processing']}",
        f"- Requests: {result['requests']['searches']} searches "
        f"(p50 {result['verdict']['search_p50_ms']} ms, p95 {result['verdict']['search_p95_ms']} ms), "
        f"{result['requests']['ingests']} ingests, {result['verdict']['ingest_failures']} ingest failures, "
        f"{result['verdict']['search_failures']} search failures",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--duration", type=int, default=3600)
    parser.add_argument("--sample-interval", type=int, default=300)
    parser.add_argument("--ingest-interval", type=int, default=20)
    parser.add_argument("--search-interval", type=int, default=2)
    parser.add_argument("--port", type=int, default=37779)
    parser.add_argument("--report", default="docs/archive/" + time.strftime("%Y-%m-%d") + "_soak-1h.md")
    parser.add_argument("--metrics-out", default="docs/archive/" + time.strftime("%Y-%m-%d") + "_soak-1h.json")
    parser.add_argument("--keep-db", action="store_true")
    args = parser.parse_args()

    print(f"soak: {args.duration}s into {DATABASE} on {CONTAINER}:{DB_PORT}")
    create_database()
    app = start_app(args.port)
    samples: list[dict] = []
    search_latencies: list[float] = []
    ingest_failures = 0
    search_failures = 0
    ingests = 0
    searches = 0
    try:
        wait_health(args.port, app)
        status, body, _ = call(args.port, "POST", "/api/session/start",
                               {"session_id": SESSION, "project_path": PROJECT})
        if status != 200:
            raise SystemExit(f"session start failed: {status} {body[:400]}")

        started = time.time()
        next_sample = started
        next_ingest = started
        next_search = started
        query_index = 0
        while time.time() - started < args.duration:
            now = time.time()
            if now >= next_sample:
                samples.append(sample(args.port, now - started))
                next_sample += args.sample_interval
            if now >= next_ingest:
                index = ingests
                status, body, elapsed = call(
                    args.port, "POST", "/api/ingest/tool-use", {
                        "session_id": SESSION,
                        "cwd": PROJECT,
                        "tool_name": "Write",
                        "tool_input": {"file_path": f"{PROJECT}/soak_{index}.java"},
                        "tool_response": {"content": f"第 {index} 次写入：把检索候选池的相邻观察纳入重排"},
                    }, timeout=60)
                ingests += 1
                if status != 200:
                    ingest_failures += 1
                    print(f"  ingest #{index} -> {status} {body[:120]}")
                else:
                    print(f"  ingest #{index} accepted in {elapsed:.0f} ms")
                next_ingest += args.ingest_interval
            if now >= next_search:
                query = QUERIES[query_index % len(QUERIES)]
                query_index += 1
                status, body, elapsed = call(
                    args.port, "GET",
                    "/api/search?limit=10&project=" + quote(PROJECT) + "&query=" + quote(query),
                    timeout=60)
                searches += 1
                if status == 200:
                    search_latencies.append(elapsed)
                else:
                    search_failures += 1
                    print(f"  search -> {status} {body[:120]}")
                next_search += args.search_interval
            time.sleep(0.2)
        samples.append(sample(args.port, time.time() - started))
    finally:
        app.terminate()
        try:
            app.wait(timeout=60)
        except subprocess.TimeoutExpired:
            app.kill()

    result = {
        "samples": samples,
        "requests": {"searches": searches, "ingests": ingests},
        "verdict": verdict(samples, search_latencies, ingest_failures, search_failures),
    }
    report_path = ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(render(result, args))
    metrics_path = ROOT / args.metrics_out
    metrics_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    checks = {key: value for key, value in result["verdict"].items() if key.startswith("pass_")}
    print(json.dumps(result["verdict"], indent=2))
    print(f"verdict: {'PASS' if all(checks.values()) else 'FAIL'}")
    print(f"report: {report_path}")

    if not args.keep_db:
        psql(f"DROP DATABASE IF EXISTS {DATABASE} WITH (FORCE);", database="postgres")
    return 0 if all(checks.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
