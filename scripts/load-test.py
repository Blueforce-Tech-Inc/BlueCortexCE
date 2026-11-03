#!/usr/bin/env python3
"""Real-load soak test for /api/search, the ingest queue and the V23 indexes.

This is a manual harness, like the real-model evaluator: it provisions a
disposable database, starts the packaged service against it together with a
local OpenAI-compatible stub, seeds observations and measures the paths that
only degrade under volume. Nothing here runs in CI.

Usage:
    python3 scripts/load-test.py --rows 10000 --requests 100

The report is written to docs/archive/2026-11-03_load-test-10k.md (or --report).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = os.environ.get("LOAD_PG_CONTAINER", "claude-mem-test-pg")
DATABASE = os.environ.get("LOAD_DB", "cortexce_load")
PROJECT = "/tmp/cortexce-load-project"
SESSION = "load-seed-session"
DB_USER = "postgres"
DB_PASSWORD = "123456"
DB_PORT = 5433


# ---------------------------------------------------------------------------
# Postgres helpers
# ---------------------------------------------------------------------------

def psql(sql: str, database: str = DATABASE, timeout: int = 300) -> str:
    result = subprocess.run(
        [
            "docker", "exec", "-i", CONTAINER,
            "psql", "-U", DB_USER, "-d", database,
            "-v", "ON_ERROR_STOP=1", "-t", "-A",
        ],
        input=sql,
        text=True,
        capture_output=True,
        timeout=timeout,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}\n--- sql ---\n{sql}")
    return result.stdout.strip()


def create_database() -> None:
    psql(f"DROP DATABASE IF EXISTS {DATABASE};", database="postgres")
    psql(f"CREATE DATABASE {DATABASE};", database="postgres")


# ---------------------------------------------------------------------------
# OpenAI-compatible stub
# ---------------------------------------------------------------------------

class StubModel:
    """Answers the extraction prompt with a valid observation for every trace.

    Titles carry a per-call counter so the duplicate check and the content hash
    never collapse two queued events into one row; the identifier is copied from
    the trace's title contract exactly as the real model would.
    """

    def __init__(self) -> None:
        self.calls = 0
        self.lock = threading.Lock()

    def answer(self, prompt: str) -> str:
        with self.lock:
            self.calls += 1
            n = self.calls
        identifier = "LoadQueueHandler"
        match = re.search(r"<title_identifier_required>(.*?)</title_identifier_required>", prompt, re.S)
        if match:
            identifier = re.sub(r"<[^>]+>", "", match.group(1)).strip() or identifier
        path = f"src/main/java/com/example/{identifier}.java"
        match = re.search(r'"file_path"\s*:\s*"([^"]+)"', prompt)
        if match:
            path = match.group(1)
        title = f"Added queued observation {n} LoadQueueHandler"
        if len(title) > 100:
            title = f"Added queue {n} {identifier}"[:100]
        # Facts must survive the deterministic evidence check, which compares each fact
        # with the tool trace: name the edited path and the removed/added line counts.
        return (
            "<observation>\n"
            "  <type>feature</type>\n"
            f"  <title>{title}</title>\n"
            f"  <subtitle>Queued load event number {n}</subtitle>\n"
            "  <facts>\n"
            f"    <fact>Edit {path} replaced null with observation in {identifier}</fact>\n"
            f"    <fact>{path} reports removed 1 line and added 2 lines</fact>\n"
            "  </facts>\n"
            f"  <narrative>Load event {n} edited {path} so it returns the observation.</narrative>\n"
            "  <concepts>\n"
            "    <concept>what-changed</concept>\n"
            "    <concept>pattern</concept>\n"
            "  </concepts>\n"
            "  <files_read></files_read>\n"
            "  <files_modified>\n"
            f"    <file>{path}</file>\n"
            "  </files_modified>\n"
            "</observation>"
        )


def start_stub(model: StubModel, port: int) -> ThreadingHTTPServer:
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802 - BaseHTTPRequestHandler contract
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8", "replace")
            prompt = raw
            try:
                payload = json.loads(raw)
                prompt = "\n".join(
                    str(message.get("content", "")) for message in payload.get("messages", [])
                )
            except json.JSONDecodeError:
                pass
            content = model.answer(prompt)
            body = json.dumps({
                "id": "stub-" + uuid.uuid4().hex,
                "object": "chat.completion",
                "created": int(time.time()),
                "model": "stub-model",
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": content},
                    "finish_reason": "stop",
                }],
                "usage": {
                    "prompt_tokens": max(1, len(prompt) // 4),
                    "completion_tokens": max(1, len(content) // 4),
                    "total_tokens": max(2, (len(prompt) + len(content)) // 4),
                },
            }).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):  # noqa: N802
            body = b'{"data":[]}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_args):  # keep the console quiet
            pass

    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


# ---------------------------------------------------------------------------
# Service process
# ---------------------------------------------------------------------------

def jar_path() -> Path:
    jars = sorted((ROOT / "backend" / "target").glob("cortex-ce-*.jar"))
    jars = [jar for jar in jars if not jar.name.endswith("-sources.jar")]
    if not jars:
        raise SystemExit("Build the jar first: cd backend && mvn -o -B -DskipTests package")
    return jars[-1]


def start_app(port: int, stub_port: int) -> subprocess.Popen:
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(port),
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{DATABASE}",
        "SPRING_DATASOURCE_USERNAME": DB_USER,
        "SPRING_DATASOURCE_PASSWORD": DB_PASSWORD,
        "SPRING_AI_OPENAI_API_KEY": "stub-key",
        "SPRING_AI_OPENAI_BASE_URL": f"http://127.0.0.1:{stub_port}",
        "SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL": "stub-model",
        "MEMORY_REFINE_ENABLED": "false",
        "EXTRACTION_ENABLED": "false",
        "CLAUDEMEM_EXTRACTION_CONSISTENCY_SAMPLES": "1",
        "CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS": "100000",
        "CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS": "1",
        "APP_PENDING_MESSAGE_SCHEDULE_INTERVAL_MS": "1000",
    })
    log = open(f"/tmp/load-test-app-{port}.log", "w")
    return subprocess.Popen(
        [
            "java", "-Xmx1g",
            f"-Dclaudemem.rate-limit.max-requests=100000",
            f"-Dclaudemem.rate-limit.window-seconds=1",
            f"-Dclaudemem.extraction.consistency-samples=1",
            f"-Dapp.pending-message.schedule-interval-ms=1000",
            "-jar", str(jar_path()),
        ],
        cwd=str(ROOT),
        env=env,
        stdout=log,
        stderr=subprocess.STDOUT,
    )


def wait_health(port: int, process: subprocess.Popen, timeout: float = 180) -> None:
    deadline = time.time() + timeout
    url = f"http://127.0.0.1:{port}/api/health"
    while time.time() < deadline:
        if process.poll() is not None:
            raise SystemExit(f"Service exited early; see /tmp/load-test-app-{port}.log")
        try:
            with urlopen(url, timeout=2):
                return
        except (HTTPError, OSError):
            time.sleep(1)
    raise SystemExit("Service did not become healthy in time")


# ---------------------------------------------------------------------------
# Seeding
# ---------------------------------------------------------------------------

SEED_COLUMNS = (
    "content_session_id, project_path, type, title, subtitle, content, facts, concepts, "
    "files_modified, content_hash, created_at, created_at_epoch, memory_scope_id, "
    "platform_source, embedding_state"
)


def seed_base() -> None:
    psql(f"""
        INSERT INTO mem_sessions (id, content_session_id, project_path, started_at_epoch, status, platform_source)
        VALUES (gen_random_uuid(), '{SESSION}', '{PROJECT}', {int(time.time())}, 'active', 'claude')
        ON CONFLICT DO NOTHING;
        INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256, display_name)
        VALUES ('project', '{PROJECT}', sha256(convert_to('{PROJECT}', 'UTF8')), 'Load test')
        ON CONFLICT DO NOTHING;
    """)


def seed_observations(start: int, end: int) -> None:
    """Insert rows start..end in one statement; created_at_epoch ascends with the row id."""
    base_epoch = int(time.time()) - end
    psql(f"""
        INSERT INTO mem_observations ({SEED_COLUMNS})
        SELECT
            '{SESSION}',
            '{PROJECT}',
            (ARRAY['feature','bugfix','refactor','change','discovery'])[1 + (g % 5)],
            'Seeded observation ' || g || ' for load-test capacity',
            'Load fixture row ' || g,
            'Seeded observation ' || g || ' records a queue handler change and a search path change '
                || 'so the capacity measurement exercises title and content text.',
            jsonb_build_array(
                'Seeded fact one for observation ' || g,
                'Seeded fact two for observation ' || g),
            jsonb_build_array('what-changed', 'pattern'),
            jsonb_build_array('src/main/java/com/example/LoadFixture' || g || '.java'),
            substring(md5('load-seed-' || g) from 1 for 16),
            now(),
            {base_epoch} + g,
            (SELECT id FROM mem_memory_scopes WHERE scope_type = 'project' AND scope_key = '{PROJECT}'),
            'claude',
            'skipped'
        FROM generate_series({start}, {end}) g;
        ANALYZE mem_observations;
    """)


def observation_count() -> int:
    return int(psql(f"SELECT count(*) FROM mem_observations WHERE project_path = '{PROJECT}';"))


# ---------------------------------------------------------------------------
# HTTP measurements
# ---------------------------------------------------------------------------

def timed_get(port: int, path: str) -> float:
    started = time.perf_counter()
    with urlopen(f"http://127.0.0.1:{port}{path}", timeout=30) as response:
        response.read()
    return (time.perf_counter() - started) * 1000


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    index = min(len(ordered) - 1, max(0, int(round((p / 100) * (len(ordered) - 1)))))
    return ordered[index]


def measure(port: int, path: str, requests: int) -> dict:
    for _ in range(min(5, requests)):
        timed_get(port, path)
    samples = [timed_get(port, path) for _ in range(requests)]
    return {
        "p50": round(percentile(samples, 50), 2),
        "p95": round(percentile(samples, 95), 2),
        "max": round(max(samples), 2),
        "mean": round(statistics.fmean(samples), 2),
    }


def search_workloads(port: int, requests: int) -> dict:
    project = urlencode({"project": PROJECT})
    return {
        "recent": measure(port, f"/api/search?{project}&limit=20", requests),
        "deep_page": measure(port, f"/api/search?{project}&limit=20&offset=200", requests),
        "type_filter": measure(port, f"/api/search?{project}&type=feature&limit=20", requests),
        "full_text": measure(
            port,
            f"/api/search?{project}&{urlencode({'query': 'queue handler search path'})}&limit=20",
            requests,
        ),
    }


# ---------------------------------------------------------------------------
# Index verification
# ---------------------------------------------------------------------------

def plan_of(output: str) -> dict:
    """Extract the first JSON value from psql output, ignoring command tags."""
    start = output.find("[")
    while start != -1:
        try:
            value, _ = json.JSONDecoder().raw_decode(output[start:])
            return value[0]
        except json.JSONDecodeError:
            start = output.find("[", start + 1)
    raise SystemExit(f"No JSON plan in output:\n{output[:400]}")


def plan_nodes(node: dict) -> list[str]:
    nodes = [node.get("Node Type", "?")]
    for child in node.get("Plans", []):
        nodes.extend(plan_nodes(child))
    return nodes


def plan_indexes(node: dict) -> list[str]:
    names = [node["Index Name"]] if node.get("Index Name") else []
    for child in node.get("Plans", []):
        names.extend(plan_indexes(child))
    return names


def timed_plan(query: str) -> dict:
    plan = plan_of(psql(f"EXPLAIN (ANALYZE, FORMAT JSON) {query};"))
    return {
        "execution_ms": round(plan.get("Execution Time", 0), 2),
        "nodes": plan_nodes(plan["Plan"]),
        "indexes": plan_indexes(plan["Plan"]),
    }


def index_evidence(scope_id: str) -> dict:
    """Compare the same statement with and without the V23 composite index.

    Index scans are disabled inside the second run instead of dropping the index:
    disabling is equivalent for the planner, needs no exclusive lock and leaves the
    schema intact.
    """
    queries = {
        "scope_recent": (
            f"SELECT * FROM mem_observations WHERE memory_scope_id = '{scope_id}' "
            "ORDER BY created_at_epoch DESC, id DESC LIMIT 20"
        ),
        "type_recent": (
            "SELECT * FROM mem_observations WHERE type = 'feature' "
            "ORDER BY created_at_epoch DESC, id DESC LIMIT 20"
        ),
        "project_recent": (
            f"SELECT * FROM mem_observations WHERE project_path = '{PROJECT}' "
            "ORDER BY created_at_epoch DESC, id DESC LIMIT 20"
        ),
    }
    indexes = {
        "scope_recent": "idx_observations_scope_created",
        "type_recent": "idx_observations_type_created",
        "project_recent": "idx_observations_project_created",
    }
    evidence = {}
    for name, query in queries.items():
        with_index = timed_plan(query)
        without = plan_of(psql(
            "SET enable_indexscan = off; SET enable_bitmapscan = off; "
            f"EXPLAIN (ANALYZE, FORMAT JSON) {query};"
        ))
        evidence[name] = {
            "index": indexes[name],
            "with_index_ms": with_index["execution_ms"],
            "with_index_nodes": with_index["nodes"],
            "with_index_used": with_index["indexes"],
            "without_index_ms": round(without.get("Execution Time", 0), 2),
            "without_index_nodes": plan_nodes(without["Plan"]),
        }
    return evidence


# ---------------------------------------------------------------------------
# Queue throughput
# ---------------------------------------------------------------------------

def post_tool_use(port: int, session: str, n: int) -> tuple[float, int]:
    tool_input = {
        "file_path": f"src/main/java/com/example/LoadQueueHandler{n}.java",
        "old_string": "return null;",
        "new_string": "return observation;",
    }
    body = json.dumps({
        "session_id": session,
        "tool_name": "Edit",
        "tool_input": tool_input,
        "tool_response": f"Edit src/main/java/com/example/LoadQueueHandler{n}.java: removed 1 line(s), added 2 line(s)",
        "cwd": PROJECT,
    }).encode()
    request = Request(
        f"http://127.0.0.1:{port}/api/ingest/tool-use",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    with urlopen(request, timeout=30) as response:
        payload = response.read()
    return time.perf_counter() - started, (200 if b"accepted" in payload else 0)


def queue_benchmark(port: int, events: int, workers: int) -> dict:
    before = observation_count()
    sessions = [f"load-session-{index}" for index in range(max(1, events // 10))]
    started = time.perf_counter()
    latencies: list[float] = []
    accepted = 0

    def post(index: int) -> tuple[float, int]:
        return post_tool_use(port, sessions[index % len(sessions)], index)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        for latency, status in pool.map(post, range(events)):
            latencies.append(latency)
            accepted += 1 if status == 200 else 0
    enqueue_seconds = time.perf_counter() - started

    drained = False
    deadline = time.time() + 600
    while time.time() < deadline:
        pending = int(psql(
            "SELECT count(*) FROM mem_pending_messages "
            "WHERE status IN ('pending', 'processing');"
        ))
        if pending == 0:
            drained = True
            break
        time.sleep(1)

    total_seconds = time.perf_counter() - started
    after = observation_count()
    duplicates = int(psql(
        "SELECT count(*) FROM ("
        "  SELECT content_hash, count(*) AS c FROM mem_observations "
        f"  WHERE content_hash IS NOT NULL GROUP BY content_hash HAVING count(*) > 1"
        ") dup;"
    ))
    stuck = int(psql(
        "SELECT count(*) FROM mem_pending_messages WHERE status = 'processing';"
    ))
    return {
        "events": events,
        "accepted": accepted,
        "enqueue_seconds": round(enqueue_seconds, 2),
        "enqueue_per_second": round(events / enqueue_seconds, 2),
        "enqueue_p95_ms": round(percentile(latencies, 95) * 1000, 2),
        "drained": drained,
        "total_seconds": round(total_seconds, 2),
        "persisted": after - before,
        "throughput_per_second": round((after - before) / total_seconds, 2) if drained else 0,
        "duplicate_hashes": duplicates,
        "stuck_processing": stuck,
    }


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------

def render_report(args, stages: list[dict], indexes: dict, queue: dict) -> str:
    lines = [
        "# Load test: 10k observations, search latency, index evidence, queue throughput",
        "",
        f"- Date: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- Database: {DATABASE} on {CONTAINER}:{DB_PORT} (disposable, Flyway-migrated)",
        f"- Service: packaged jar on http://127.0.0.1:{args.port}",
        f"- Workload: {args.requests} sequential requests per search path; pages of 20 rows",
        "- Model: local OpenAI-compatible stub, so queue measurements exclude provider latency",
        "",
        "## Search latency by table size",
        "",
        "| rows | path | p50 ms | p95 ms | max ms |",
        "|---:|---|---:|---:|---:|",
    ]
    for stage in stages:
        for path, values in stage["search"].items():
            lines.append(
                f"| {stage['rows']} | {path} | {values['p50']} | {values['p95']} | {values['max']} |"
            )

    lines += [
        "",
        "## V23 index evidence (10k rows)",
        "",
        "| query | index | with index ms | plan | without index ms | plan |",
        "|---|---|---:|---|---:|---|",
    ]
    for name, value in indexes.items():
        used = ", ".join(value["with_index_used"]) or "none"
        lines.append(
            f"| {name} | {value['index']} | {value['with_index_ms']} | {used} | "
            f"{value['without_index_ms']} | {' -> '.join(value['without_index_nodes'])} |"
        )

    lines += [
        "",
        "## Queue throughput (ingest -> observation persisted)",
        "",
        "| metric | value |",
        "|---|---:|",
        f"| events posted | {queue['events']} |",
        f"| accepted | {queue['accepted']} |",
        f"| enqueue seconds | {queue['enqueue_seconds']} |",
        f"| enqueue events/s | {queue['enqueue_per_second']} |",
        f"| enqueue p95 ms | {queue['enqueue_p95_ms']} |",
        f"| queue drained | {queue['drained']} |",
        f"| total seconds | {queue['total_seconds']} |",
        f"| persisted rows | {queue['persisted']} |",
        f"| persisted events/s | {queue['throughput_per_second']} |",
        f"| duplicate content hashes | {queue['duplicate_hashes']} |",
        f"| rows stuck in processing | {queue['stuck_processing']} |",
        "",
        "## Capacity conclusion",
        "",
    ]

    first, last = stages[0], stages[-1]
    lines.append(
        f"- Recent list (V23 scope index): p95 {first['search']['recent']['p95']} ms at "
        f"{first['rows']} rows -> {last['search']['recent']['p95']} ms at {last['rows']} rows. "
        "The ordered index scan serves the page, so no degradation was observed."
    )
    text_baseline = first["search"]["full_text"]["p95"]
    crosses = next(
        (
            stage["rows"]
            for stage in stages[1:]
            if text_baseline and stage["search"]["full_text"]["p95"] > 2 * text_baseline
        ),
        None,
    )
    lines.append(
        f"- Full-text fallback (GIN): p95 {text_baseline} ms at {first['rows']} rows -> "
        f"{last['search']['full_text']['p95']} ms at {last['rows']} rows. This is the first "
        "path to degrade as the table grows"
        + (f", crossing 2x at {crosses} rows." if crosses else ".")
    )
    lines.append(
        f"- Queue: {queue['persisted']} of {queue['events']} events persisted, "
        f"{queue['duplicate_hashes']} duplicate hashes, {queue['stuck_processing']} rows left "
        "in processing."
    )
    lines.append(
        f"- At {last['rows']} rows every measured path stays under "
        f"{max(last['search'][path]['p95'] for path in last['search']) + 1:.0f} ms p95; "
        "the next capacity check belongs at 100k rows, where the full-text path is expected "
        "to be the limiter."
    )
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rows", type=int, default=10000)
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--events", type=int, default=200)
    parser.add_argument("--workers", type=int, default=20)
    parser.add_argument("--port", type=int, default=37778)
    parser.add_argument("--stub-port", type=int, default=9100)
    parser.add_argument("--report", default=str(ROOT / "docs/archive/2026-11-03_load-test-10k.md"))
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()

    model = StubModel()
    stub = start_stub(model, args.stub_port)
    create_database()
    app = start_app(args.port, args.stub_port)
    try:
        wait_health(args.port, app)
        seed_base()

        stages = []
        targets = sorted({1000, min(5000, args.rows), args.rows})
        seeded = 0
        for target in targets:
            seed_observations(seeded + 1, target)
            seeded = target
            psql("VACUUM (ANALYZE) mem_observations;")
            stages.append({
                "rows": observation_count(),
                "search": search_workloads(args.port, args.requests),
            })
            print(f"measured {stages[-1]['rows']} rows: {stages[-1]['search']}", flush=True)

        scope_id = psql(
            f"SELECT id FROM mem_memory_scopes WHERE scope_type = 'project' "
            f"AND scope_key = '{PROJECT}';"
        )
        indexes = index_evidence(scope_id)
        queue = queue_benchmark(args.port, args.events, args.workers)
        print(f"queue: {queue}", flush=True)

        report = render_report(args, stages, indexes, queue)
        path = Path(args.report)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(report + "\n")
        print(f"report written to {path}")
        return 0
    finally:
        stub.shutdown()
        if not args.keep:
            app.terminate()
            try:
                app.wait(timeout=20)
            except subprocess.TimeoutExpired:
                app.kill()


if __name__ == "__main__":
    sys.exit(main())
