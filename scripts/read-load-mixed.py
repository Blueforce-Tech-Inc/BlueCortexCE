#!/usr/bin/env python3
"""Concurrent read load test: /api/search and /api/context/inject on a real database.

Manual harness, like the model evaluator and load-test: nothing here runs in CI.
It starts the packaged service against a disposable copy of a real-sized database
(>= 100k observations), points the embedding provider at a local OpenAI-compatible
stub that replays real 1024-d vectors from that database, then runs a mixed
read workload for a fixed duration:

    50 concurrent GET /api/search  (half Chinese, half English queries)
    20 concurrent GET /api/context/inject

It reports p50/p95/p99, throughput, error/5xx/timeout rates, database connection
usage and process CPU. The verdict is FAIL when any 5xx or timeout is observed.

Usage:
    python3 scripts/read-load-mixed.py --db cortexce_load --duration 180

The report is written to docs/archive/<date>_retrieval-concurrent-read.md
(or --report); raw metrics go to the matching .json file.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import statistics
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

import aiohttp

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = os.environ.get("READ_PG_CONTAINER", "claude-mem-test-pg")
DB_USER = "postgres"
DB_PASSWORD = "123456"
DB_PORT = 5433
VECTOR_SAMPLES = 256

CHINESE_QUERIES = [
    "队列处理为什么在会话结束时丢事件",
    "混合检索的全文权重是多少",
    "观察写入失败的修复流程",
    "向量索引如何选择 ef_search",
    "迁移版本号冲突怎么处理",
    "连接池耗尽时错误是什么形状",
    "摘要生成在哪个阶段触发",
    "中文分词检索的召回为什么低",
    "并发写入时如何保证顺序",
    "回填任务多久运行一次",
]
ENGLISH_QUERIES = [
    "queue handler drops events at session end",
    "what lexical weight does hybrid search use",
    "repair flow for failed observation writes",
    "how to choose ef_search for the vector index",
    "migration version conflict handling",
    "connection pool exhaustion error shape",
    "which phase triggers summary generation",
    "why is chinese token recall low",
    "ordering guarantees under concurrent writes",
    "how often does the backfill job run",
]


def psql(sql: str, database: str, timeout: int = 300) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", database,
         "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, timeout=timeout,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}")
    return result.stdout.strip()


def create_copy(source: str, target: str) -> None:
    """Template copy keeps the shared fixture untouched by scheduled writes."""
    active = psql(
        f"SELECT count(*) FROM pg_stat_activity WHERE datname = '{source}';", "postgres")
    if int(active):
        raise SystemExit(f"{source} still has {active} connections; stop them before copying")
    psql(f"DROP DATABASE IF EXISTS {target};", "postgres")
    psql(f"CREATE DATABASE {target} TEMPLATE {source};", "postgres", timeout=900)


def dataset_facts(db: str) -> dict:
    rows = psql(
        f"SELECT count(*), count(embedding_1024) FROM mem_observations;", db).split("|")
    project = psql(
        "SELECT project_path FROM mem_observations GROUP BY 1 "
        "ORDER BY count(*) DESC LIMIT 1;", db)
    return {
        "database": db,
        "observations": int(rows[0]),
        "with_embedding_1024": int(rows[1]),
        "project": project,
        "index": psql(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'mem_observations' "
            "AND indexdef ILIKE '%hnsw%' AND indexdef ILIKE '%1024%';", db),
    }


def sample_vectors(db: str, limit: int = VECTOR_SAMPLES) -> list[list[float]]:
    raw = psql(
        "SELECT embedding_1024::text FROM mem_observations "
        f"WHERE embedding_1024 IS NOT NULL LIMIT {limit};", db, timeout=300)
    vectors = []
    for line in raw.splitlines():
        if line.startswith("["):
            vectors.append([float(value) for value in line.strip("[]").split(",")])
    if not vectors:
        raise SystemExit("no real 1024-d vectors found in the database")
    return vectors


# ---------------------------------------------------------------------------
# OpenAI-compatible stub: replays real vectors, answers chat with a no-op
# ---------------------------------------------------------------------------

def start_stub(vectors: list[list[float]], port: int) -> ThreadingHTTPServer:
    state = {"cursor": 0}
    lock = threading.Lock()
    # Pre-serialize every reply: under ~300 embedding calls/s, json.dumps of a
    # 1024-float payload becomes the slowest part of the harness and shows up as
    # provider timeouts in the service under test.
    embedding_bodies = [
        json.dumps({"object": "list", "model": "stub-embed",
                    "data": [{"object": "embedding", "index": 0, "embedding": vector}],
                    "usage": {"prompt_tokens": 1, "total_tokens": 1}}).encode()
        for vector in vectors
    ]
    chat_body = json.dumps({
        "id": "stub-chat", "object": "chat.completion", "created": int(time.time()),
        "model": "stub-model",
        "choices": [{"index": 0, "message": {"role": "assistant", "content": "ok"},
                     "finish_reason": "stop"}],
        "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
    }).encode()

    class Handler(BaseHTTPRequestHandler):
        # BaseHTTPRequestHandler defaults to HTTP/1.0, which closes the socket
        # after every reply. The JDK client pools connections and occasionally
        # reuses a closed one: "HTTP/1.1 header parser received no bytes", a
        # Spring AI retry and a multi-second latency spike that has nothing to
        # do with the service under test. Content-Length is always set, so
        # HTTP/1.1 keep-alive is safe here.
        protocol_version = "HTTP/1.1"

        def read_body(self) -> bytes:
            body = b""
            if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
                while True:
                    line = self.rfile.readline().strip()
                    size = int(line.split(b";")[0] or b"0", 16)
                    if size == 0:
                        self.rfile.readline()
                        break
                    body += self.rfile.read(size)
                    self.rfile.read(2)
            else:
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            if self.headers.get("Content-Encoding", "").lower() == "gzip":
                import gzip
                body = gzip.decompress(body)
            return body

        def send_bytes(self, body: bytes) -> None:
            try:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                self.close_connection = True

        def do_POST(self):  # noqa: N802 - BaseHTTPRequestHandler contract
            # Read the request so the client's write side completes, then answer
            # from the pre-serialized payloads: this stub only has to prove the
            # embedding gate is open, and parsing 1024-float input is wasted work.
            self.read_body()
            if self.path.rstrip("/").endswith("/embeddings"):
                with lock:
                    body = embedding_bodies[state["cursor"] % len(embedding_bodies)]
                    state["cursor"] += 1
                self.send_bytes(body)
                return
            self.send_bytes(chat_body)

        def do_GET(self):  # noqa: N802
            self.send_bytes(b'{"data":[]}')

        def log_message(self, *_args):
            pass

    class Server(ThreadingHTTPServer):
        # Default backlog is 5; 50 concurrent search requests each call the
        # embedding endpoint, and a dropped connection turns into a Spring AI
        # retry instead of a measurement.
        request_queue_size = 256
        daemon_threads = True

    server = Server(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


# ---------------------------------------------------------------------------
# Service process
# ---------------------------------------------------------------------------

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


def start_app(db: str, port: int, stub_port: int, pool_size: int | None,
              jar: str = "") -> subprocess.Popen:
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(port),
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{db}",
        "SPRING_DATASOURCE_USERNAME": DB_USER,
        "SPRING_DATASOURCE_PASSWORD": DB_PASSWORD,
        "SPRING_AI_OPENAI_API_KEY": "stub-key",
        "SPRING_AI_OPENAI_BASE_URL": f"http://127.0.0.1:{stub_port}",
        "SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL": "stub-model",
        "SPRING_AI_OPENAI_EMBEDDING_BASE_URL": f"http://127.0.0.1:{stub_port}",
        "SPRING_AI_OPENAI_EMBEDDING_API_KEY": "stub-key",
        "SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL": "stub-embed",
        "SPRING_AI_RETRY_MAX_ATTEMPTS": "1",
        "MEMORY_REFINE_ENABLED": "false",
        "EXTRACTION_ENABLED": "false",
        "CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS": "100000",
        "CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS": "1",
        # Keep background writers out of the read measurement. The startup
        # refinement listener still runs once and is visible in the log.
        "APP_PENDING_MESSAGE_SCHEDULE_INTERVAL_MS": "999999999",
        "CLAUDEMEM_EMBEDDING_BACKFILL_INTERVAL_MS": "999999999",
        "APP_MEMORY_REFINE_SCHEDULE_INTERVAL_MS": "999999999",
    })
    extra = []
    if pool_size:
        extra = [f"-Dspring.datasource.hikari.maximum-pool-size={pool_size}"]
    log = open(f"/tmp/read-load-app-{port}.log", "w")
    return subprocess.Popen(
        ["java", "-Xmx1g", *extra, "-jar", str(jar_path(jar))],
        cwd=str(ROOT), env=env, stdout=log, stderr=subprocess.STDOUT,
    )


def wait_health(port: int, process: subprocess.Popen, timeout: float = 180) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit(f"Service exited early; see /tmp/read-load-app-{port}.log")
        try:
            with urlopen(f"http://127.0.0.1:{port}/api/health", timeout=2):
                return
        except Exception:
            time.sleep(1)
    raise SystemExit("Service did not become healthy in time")


# ---------------------------------------------------------------------------
# Sampling: database connections, process CPU, Prometheus pools
# ---------------------------------------------------------------------------

def sample_connections(db: str) -> dict:
    raw = psql(
        "SELECT count(*), count(*) FILTER (WHERE state = 'active'), "
        f"count(*) FILTER (WHERE wait_event_type = 'Lock') FROM pg_stat_activity "
        f"WHERE datname = '{db}';", "postgres", timeout=30)
    total, active, waiting = (int(part) for part in raw.split("|"))
    return {"connections": total, "active": active, "lock_waiters": waiting}


def sample_cpu(pid: int) -> float:
    result = subprocess.run(["ps", "-p", str(pid), "-o", "%cpu="], capture_output=True,
                            text=True)
    try:
        return float(result.stdout.strip())
    except ValueError:
        return 0.0


def prometheus_snapshot(port: int) -> dict:
    snapshot = {}
    try:
        with urlopen(f"http://127.0.0.1:{port}/actuator/prometheus", timeout=3) as response:
            text = response.read().decode("utf-8", "replace")
    except Exception as error:
        return {"error": type(error).__name__}
    for line in text.splitlines():
        if line.startswith("#"):
            continue
        for name in ("hikari_connections", "cortexce_search_duration_seconds",
                     "http_server_requests_seconds"):
            if line.startswith(name):
                key = line.split("}", 1)[-1] if "}" in line else line.split(" ", 1)[-1]
                # Keep only aggregate series; label sets are long and noisy.
                if "{" in line and "}" in line and "pool=" not in line and name == "hikari_connections":
                    continue
                parts = line.rsplit(" ", 1)
                if len(parts) != 2:
                    continue
                try:
                    value = float(parts[1])
                except ValueError:
                    continue
                match = re.match(r"([a-z_]+)", line.split("{", 1)[0])
                metric = match.group(1) if match else name
                label = ""
                if "{" in line:
                    label = line.split("{", 1)[1].split("}", 1)[0]
                key = f"{metric}_{label}" if label else metric
                snapshot[key] = value
    return snapshot


class Sampler:
    def __init__(self, db: str, app_pid: int, port: int, interval: float = 2.0):
        self.db = db
        self.app_pid = app_pid
        self.port = port
        self.interval = interval
        self.samples: list[dict] = []
        self.prometheus: list[dict] = []
        self.running = True
        self.thread = threading.Thread(target=self._run, daemon=True)

    def _run(self) -> None:
        while self.running:
            started = time.time()
            try:
                sample = sample_connections(self.db)
            except SystemExit:
                sample = {"connections": -1, "active": -1, "lock_waiters": -1}
            sample["cpu_percent"] = sample_cpu(self.app_pid)
            sample["elapsed"] = round(started - self.started_at, 1)
            self.samples.append(sample)
            snapshot = prometheus_snapshot(self.port)
            if snapshot:
                snapshot["elapsed"] = sample["elapsed"]
                self.prometheus.append(snapshot)
            time.sleep(max(0.2, self.interval - (time.time() - started)))

    def start(self) -> None:
        self.started_at = time.time()
        self.thread.start()

    def stop(self) -> dict:
        self.running = False
        self.thread.join(timeout=5)
        if not self.samples:
            return {}
        return {
            "samples": len(self.samples),
            "connections_max": max(item["connections"] for item in self.samples),
            "connections_mean": round(statistics.fmean(
                item["connections"] for item in self.samples), 2),
            "active_max": max(item["active"] for item in self.samples),
            "active_mean": round(statistics.fmean(
                item["active"] for item in self.samples), 2),
            "lock_waiters_max": max(item["lock_waiters"] for item in self.samples),
            "cpu_percent_max": max(item["cpu_percent"] for item in self.samples),
            "cpu_percent_mean": round(statistics.fmean(
                item["cpu_percent"] for item in self.samples), 2),
        }


# ---------------------------------------------------------------------------
# Workload
# ---------------------------------------------------------------------------

def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round(p / 100.0 * len(ordered) + 0.5)) - 1))
    return ordered[index]


async def run_worker(session: aiohttp.ClientSession, kind: str, index: int,
                     base: str, project: str, deadline: float, timeout: float,
                     limit: int, started_at: float, results: list[dict]) -> None:
    queries = CHINESE_QUERIES if index % 2 == 0 else ENGLISH_QUERIES
    cursor = index
    while time.time() < deadline:
        if kind == "search":
            query = queries[cursor % len(queries)]
            path = "/api/search?" + urlencode({"project": project, "query": query,
                                               "limit": limit})
            label = "search_zh" if index % 2 == 0 else "search_en"
        else:
            path = "/api/context/inject?" + urlencode({"projects": project})
            label = "inject"
        cursor += 1
        # Parsing 127 KB search responses inside the load client makes the
        # measurement client-bound (and the 10 s timeouts client-side). Verify
        # the response shape on a 1-in-25 sample instead of on every request.
        should_parse = cursor % 25 == 0
        started = time.perf_counter()
        status = 0
        error = ""
        detail = ""
        size = 0
        try:
            async with session.get(base + path, timeout=aiohttp.ClientTimeout(total=timeout)) as response:
                status = response.status
                body = await response.read()
                size = len(body)
                if status == 200 and should_parse:
                    if kind == "search":
                        payload = json.loads(body)
                        detail = str(payload.get("strategy", ""))
                    else:
                        payload = json.loads(body)
                        detail = "empty" if not payload.get("context") else "context"
        except asyncio.TimeoutError:
            error = "timeout"
        except (aiohttp.ClientError, json.JSONDecodeError) as exc:
            error = type(exc).__name__
        results.append({
            "kind": label,
            "latency_ms": (time.perf_counter() - started) * 1000,
            "status": status,
            "error": error,
            "detail": detail,
            "bytes": size,
            "at": round(time.time() - started_at, 1),
        })


async def workload(base: str, project: str, duration: float, search: int,
                   inject: int, timeout: float, limit: int = 20) -> tuple[list[dict], float]:
    results: list[dict] = []
    deadline = time.time() + duration
    started_at = time.time()
    connector = aiohttp.TCPConnector(limit=0, limit_per_host=0, ttl_dns_cache=60)
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = []
        for index in range(search):
            tasks.append(asyncio.create_task(
                run_worker(session, "search", index, base, project, deadline,
                           timeout, limit, started_at, results)))
        for index in range(inject):
            tasks.append(asyncio.create_task(
                run_worker(session, "inject", index, base, project, deadline,
                           timeout, limit, started_at, results)))
        started = time.perf_counter()
        await asyncio.gather(*tasks)
        return results, time.perf_counter() - started


def summarize(results: list[dict], elapsed: float) -> dict:
    def block(items: list[dict]) -> dict:
        latencies = [item["latency_ms"] for item in items]
        failures = [item for item in items if item["error"] or item["status"] >= 500]
        return {
            "requests": len(items),
            "errors": sum(1 for item in items if item["error"]),
            "five_xx": sum(1 for item in items if item["status"] >= 500),
            "non_2xx": sum(1 for item in items if item["status"] and item["status"] >= 400),
            "timeouts": sum(1 for item in items if item["error"] == "timeout"),
            "p50_ms": round(percentile(latencies, 50), 2),
            "p95_ms": round(percentile(latencies, 95), 2),
            "p99_ms": round(percentile(latencies, 99), 2),
            "mean_ms": round(statistics.fmean(latencies), 2) if latencies else 0.0,
            "max_ms": round(max(latencies), 2) if latencies else 0.0,
            "mean_kb": round(statistics.fmean(item["bytes"] for item in items) / 1024, 1)
            if items else 0.0,
            "failures": len(failures),
        }

    overall = block(results)
    by_kind = {kind: block([item for item in results if item["kind"] == kind])
               for kind in ("search_zh", "search_en", "inject")}
    strategies = {}
    for item in results:
        if item["kind"].startswith("search") and item["status"] == 200:
            strategies[item["detail"]] = strategies.get(item["detail"], 0) + 1
    inject_shapes = {}
    for item in results:
        if item["kind"] == "inject" and item["status"] == 200:
            inject_shapes[item["detail"]] = inject_shapes.get(item["detail"], 0) + 1
    overall["strategies"] = strategies
    overall["inject_shapes"] = inject_shapes
    overall["timeout_positions"] = sorted(
        item["at"] for item in results if item["error"] == "timeout")
    overall["throughput_rps"] = round(len(results) / elapsed, 2) if elapsed else 0.0
    overall["elapsed_seconds"] = round(elapsed, 2)
    overall["by_kind"] = by_kind
    overall["verdict"] = "PASS" if overall["five_xx"] == 0 and overall["timeouts"] == 0 else "FAIL"
    return overall


def search_timer_percentiles(prometheus: list[dict]) -> dict:
    """Server-side hybrid timer from the last scrape, to separate DB time from HTTP."""
    if not prometheus:
        return {}
    last = prometheus[-1]
    buckets = []
    for key, value in last.items():
        match = re.match(
            r'cortexce_search_duration_seconds_bucket_path="hybrid",le="([^"]+)"', key)
        if match:
            buckets.append((float(match.group(1)), value))
    if not buckets:
        return {}
    buckets.sort()
    total = max(value for _, value in buckets) or 1.0
    result = {}
    for name, fraction in (("p50", 0.5), ("p95", 0.95), ("p99", 0.99)):
        result[name + "_ms"] = round(next(
            edge for edge, value in buckets if value / total >= fraction) * 1000, 1)
    count = last.get('cortexce_search_duration_seconds_count_path="hybrid"')
    total_sum = last.get('cortexce_search_duration_seconds_sum_path="hybrid"')
    if count and total_sum:
        result["count"] = int(count)
        result["mean_ms"] = round(total_sum / count * 1000, 1)
    return result


def render_report(facts: dict, summary: dict, sampling: dict, prometheus: list[dict],
                  config: dict, server: dict) -> str:
    lines = [
        "# Concurrent read load: /api/search + /api/context/inject",
        "",
        "Manual harness (`scripts/read-load-mixed.py`). Real PostgreSQL copy, packaged jar,",
        "local embedding stub replaying real 1024-d vectors from the same database.",
        "",
        "## Environment",
        "",
        f"- Database: `{facts['database']}` ({facts['observations']:,} observations, "
        f"{facts['with_embedding_1024']:,} with 1024-d embeddings)",
        f"- Project queried: `{facts['project']}`",
        f"- Vector index: {facts['index'] or 'none'}",
        f"- Workload: {config['search']} concurrent search (limit {config['limit']}) + "
        f"{config['inject']} concurrent inject for {config['duration']:.0f}s, "
        f"client timeout {config['timeout']:.0f}s",
        f"- Hikari pool: {config['pool'] or 'application default (25)'}",
        f"- Jar: `{config.get('jar') or 'backend/target (default build)'}`",
        "",
        "Queries alternate Chinese and English; the corpus is English-only, so a Chinese",
        "query exercises the vector half plus an empty lexical half, while an English query",
        "exercises both halves.",
        "",
        "## Latency",
        "",
        "| workload | requests | errors | 5xx | timeouts | p50 ms | p95 ms | p99 ms | max ms | avg KB |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    rows = [("search_zh", summary["by_kind"]["search_zh"]),
            ("search_en", summary["by_kind"]["search_en"]),
            ("inject", summary["by_kind"]["inject"]),
            ("all", summary)]
    for label, item in rows:
        lines.append(
            f"| {label} | {item['requests']} | {item['errors']} | {item['five_xx']} | "
            f"{item['timeouts']} | {item['p50_ms']:.2f} | {item['p95_ms']:.2f} | "
            f"{item['p99_ms']:.2f} | {item['max_ms']:.2f} | {item['mean_kb']:.1f} |")
    lines += [
        "",
        f"Throughput: {summary['throughput_rps']:.2f} req/s over "
        f"{summary['elapsed_seconds']:.1f}s.",
        "",
        "Search strategy distribution: "
        + ", ".join(f"`{name or '(not sampled)'}` x{count}" for name, count in
                    sorted(summary.get("strategies", {}).items(), key=lambda pair: -pair[1]))
        + ". Inject response shapes: "
        + ", ".join(f"{name or '(not sampled)'} x{count}" for name, count in
                    sorted(summary.get("inject_shapes", {}).items(), key=lambda pair: -pair[1]))
        + ".",
        "",
        "## Resources",
        "",
        "| metric | max | mean |",
        "|---|---:|---:|",
        f"| database connections | {sampling.get('connections_max', 0)} | "
        f"{sampling.get('connections_mean', 0)} |",
        f"| active connections | {sampling.get('active_max', 0)} | "
        f"{sampling.get('active_mean', 0)} |",
        f"| lock waiters | {sampling.get('lock_waiters_max', 0)} | - |",
        f"| app CPU % | {sampling.get('cpu_percent_max', 0):.1f} | "
        f"{sampling.get('cpu_percent_mean', 0):.1f} |",
        "",
        f"Sampled {sampling.get('samples', 0)} times at 2s intervals.",
    ]
    pool_series = {}
    for snapshot in prometheus:
        for key, value in snapshot.items():
            if key.startswith("hikari_connections"):
                pool_series.setdefault(key, []).append(value)
    if pool_series:
        lines += ["", "### Hikari metrics (Prometheus)", "",
                  "| series | max | mean |", "|---|---:|---:|"]
        for key in sorted(pool_series):
            values = pool_series[key]
            lines.append(f"| `{key}` | {max(values):.2f} | {statistics.fmean(values):.2f} |")
    else:
        lines += ["", "Hikari pool metrics were not exposed; database connection counts "
                  "above come from `pg_stat_activity`."]
    if server:
        lines += [
            "",
            "### Server-side search timer (Prometheus, hybrid path)",
            "",
            "The timer covers `SearchService` only: query embedding is outside it and",
            "HTTP serialization is outside it. Compare with the client table above to",
            "see where the tail is spent.",
            "",
            "| metric | value |",
            "|---|---:|",
            f"| count | {server.get('count', 0)} |",
            f"| mean | {server.get('mean_ms', 0):.1f} ms |",
            f"| p50 | {server.get('p50_ms', 0):.1f} ms |",
            f"| p95 | {server.get('p95_ms', 0):.1f} ms |",
            f"| p99 | {server.get('p99_ms', 0):.1f} ms |",
        ]
    lines += [
        "",
        "## Verdict",
        "",
        f"**{summary['verdict']}** — 5xx {summary['five_xx']}, timeouts "
        f"{summary['timeouts']}, client errors {summary['errors']} "
        f"(over {summary['requests']} requests).",
        "",
    ]
    if summary["timeout_positions"]:
        positions = summary["timeout_positions"]
        lines += [
            "Timeout positions (seconds from measurement start): "
            + ", ".join(f"{value:.1f}" for value in positions[:20])
            + (" ..." if len(positions) > 20 else "") + ".",
            "",
        ]
    if summary["verdict"] == "PASS":
        lines += [
            "No request failed with 5xx or timed out. The observed p95 stays inside the",
            "10 s client timeout; see the attribution section for what dominates the tail.",
            "",
        ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", default=os.environ.get("READ_DB", "cortexce_load"))
    parser.add_argument("--copy", default="", help="copy the source DB to this name first")
    parser.add_argument("--duration", type=float, default=180.0)
    parser.add_argument("--search", type=int, default=50)
    parser.add_argument("--inject", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--limit", type=int, default=20, help="search page size")
    parser.add_argument("--port", type=int, default=37891)
    parser.add_argument("--stub-port", type=int, default=8799)
    parser.add_argument("--pool", type=int, default=0, help="Hikari max pool size override")
    parser.add_argument("--warmup", type=float, default=20.0)
    parser.add_argument("--jar", default="",
                        help="jar to run instead of backend/target (A/B controls)")
    parser.add_argument("--report", default="docs/archive/" +
                        time.strftime("%Y-%m-%d") + "_retrieval-concurrent-read.md")
    parser.add_argument("--metrics-out", default="")
    args = parser.parse_args()

    os.environ.setdefault("SPRING_AI_OPENAI_EMBEDDING_BASE_URL", "")
    source = args.db
    if args.copy:
        print(f"copying {source} -> {args.copy} ...")
        create_copy(source, args.copy)
        source = args.copy
    facts = dataset_facts(source)
    if facts["observations"] < 100000:
        raise SystemExit(f"{source} has {facts['observations']} rows, need >= 100000")
    print(f"dataset: {facts['observations']:,} rows, "
          f"{facts['with_embedding_1024']:,} embedded, project {facts['project']}")

    vectors = sample_vectors(source)
    print(f"sampled {len(vectors)} real vectors for the embedding stub")
    stub = start_stub(vectors, args.stub_port)
    app = start_app(source, args.port, args.stub_port, args.pool or None, args.jar)
    try:
        wait_health(args.port, app)
        base = f"http://127.0.0.1:{args.port}"
        # Warm up at the measured concurrency: a 5-worker warmup leaves 45 search
        # workers' code paths cold, and the first measured seconds then show
        # cold-start outliers that look like steady-state timeouts.
        warm_started = time.time()
        loop = asyncio.new_event_loop()
        loop.run_until_complete(workload(base, facts["project"], args.warmup,
                                         args.search, args.inject, args.timeout,
                                         args.limit))
        loop.close()
        print(f"warmup done in {time.time() - warm_started:.1f}s")

        sampler = Sampler(source, app.pid, args.port)
        sampler.start()
        results, elapsed = asyncio.run(
            workload(base, facts["project"], args.duration, args.search, args.inject,
                     args.timeout, args.limit))
        sampling = sampler.stop()
        summary = summarize(results, elapsed)
        server = search_timer_percentiles(sampler.prometheus)
        config = {"search": args.search, "inject": args.inject,
                  "duration": args.duration, "timeout": args.timeout,
                  "pool": args.pool, "limit": args.limit, "jar": args.jar}
        payload = {"dataset": facts, "config": config, "summary": summary,
                   "sampling": sampling, "prometheus": sampler.prometheus,
                   "samples": sampler.samples, "server": server}
        report_path = ROOT / args.report
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(render_report(facts, summary, sampling,
                                             sampler.prometheus, config, server))
        metrics_path = ROOT / (args.metrics_out or
                               str(Path(args.report).with_suffix(".json")))
        metrics_path.write_text(json.dumps(payload, indent=2))
        print(json.dumps({key: summary[key] for key in
                          ("requests", "p50_ms", "p95_ms", "p99_ms", "errors",
                           "five_xx", "timeouts", "throughput_rps", "verdict")}, indent=2))
        print(f"report: {report_path}")
        print(f"metrics: {metrics_path}")
        return 0 if summary["verdict"] == "PASS" else 1
    finally:
        app.terminate()
        try:
            app.wait(timeout=30)
        except subprocess.TimeoutExpired:
            app.kill()
        stub.shutdown()


if __name__ == "__main__":
    raise SystemExit(main())
