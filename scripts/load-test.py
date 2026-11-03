#!/usr/bin/env python3
"""Real-load soak test for /api/search, the ingest queue and the V23 indexes.

This is a manual harness, like the real-model evaluator: it provisions a
disposable database, starts the packaged service against it together with a
local OpenAI-compatible stub, seeds observations and measures the paths that
only degrade under volume. Nothing here runs in CI.

Usage:
    python3 scripts/load-test.py --rows 50000 --requests 100

The report is written to docs/archive/<date>_load-test-<rows/1000>k.md (or --report).
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

    Fault injection switches:
      chat_delay          seconds to wait before answering a chat request, so a
                          process kill lands while jobs are mid-flight;
      embedding_fault     None, or "stall-close": wait embedding_delay seconds and
                          drop the connection without a response, which is what an
                          embedding outage looks like from the client side.
    """

    def __init__(self) -> None:
        self.calls = 0
        self.lock = threading.Lock()
        self.chat_delay = 0.0
        self.embedding_delay = 0.0
        self.embedding_fault: str | None = None

    def answer(self, prompt: str) -> str:
        with self.lock:
            self.calls += 1
            n = self.calls
        if "<summary>" in prompt or "next_steps" in prompt:
            return (
                "<summary>\n"
                "  <request>Load test ingests events while the session ends.</request>\n"
                "  <investigated>Concurrent ingest and session-end on the same conversation.</investigated>\n"
                "  <learned>Admitted observation jobs must drain before the summary completes.</learned>\n"
                f"  <completed>Queued ten events per session and closed every session mid-flight (call {n}).</completed>\n"
                "  <next_steps>Verify no observation is lost and no row stays processing.</next_steps>\n"
                "  <notes>Load harness summary.</notes>\n"
                "</summary>"
            )
        identifier = "LoadQueueHandler"
        match = re.search(r"<title_identifier_required>(.*?)</title_identifier_required>", prompt, re.S)
        if match:
            identifier = re.sub(r"<[^>]+>", "", match.group(1)).strip() or identifier
        path = f"src/main/java/com/example/{identifier}.java"
        # The trace renders tool input either as raw JSON or as an escaped text
        # block, so match the concrete load-test path anywhere in the prompt.
        match = re.search(r"([\w./-]+/LoadQueueHandler\d+\.java)", prompt)
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
        def read_body(self) -> bytes:
            """Spring AI streams the chat request chunked (and may gzip it)."""
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

        def send_json(self, payload: dict) -> None:
            body = json.dumps(payload).encode()
            try:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                # The service under test was killed mid-request; nothing to answer.
                self.close_connection = True

        def do_POST(self):  # noqa: N802 - BaseHTTPRequestHandler contract
            raw = self.read_body().decode("utf-8", "replace")
            if os.environ.get("LOAD_STUB_DEBUG"):
                with open("/tmp/load-stub-requests.log", "a") as debug:
                    debug.write(raw[:600] + "\n---\n")
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                payload = {}
            if "input" in payload:
                self.answer_embedding(payload)
                return
            # Spring AI sends the system prompt both as a top-level field and,
            # for some providers, inside messages; joining both keeps the
            # summary template's <summary> tags visible to the stub.
            prompt = str(payload.get("system", "")) + "\n" + "\n".join(
                str(message.get("content", "")) for message in payload.get("messages", [])
            )
            if model.chat_delay:
                time.sleep(model.chat_delay)
            content = model.answer(prompt)
            self.send_json({
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
            })

        def answer_embedding(self, payload: dict) -> None:
            """Answer /v1/embeddings with a real 1024-wide vector, or time out on demand."""
            if model.embedding_fault == "timeout":
                # Hold the request and then answer 504: the provider-side timeout
                # shape. Dropping the socket instead leaves the OpenAI client waiting
                # with no read timeout, so the failure never becomes observable.
                time.sleep(model.embedding_delay)
                try:
                    self.send_response(504)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                except (BrokenPipeError, ConnectionResetError):
                    self.close_connection = True
                return
            if model.embedding_fault == "reject":
                # A non-retryable 4xx: the write path gives up at once, records the
                # failure on the row, and leaves the repair to the backfill sweep.
                time.sleep(0.2)
                try:
                    self.send_response(400)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                except (BrokenPipeError, ConnectionResetError):
                    self.close_connection = True
                return
            inputs = payload.get("input")
            if isinstance(inputs, str):
                inputs = [inputs]
            data = []
            for index in range(max(1, len(inputs or [""]))):
                # A deterministic non-zero vector keeps pgvector's HNSW index happy.
                data.append({"object": "embedding",
                             "index": index,
                             "embedding": [((index * 31 + slot) % 97 + 1) / 100.0
                                           for slot in range(1024)]})
            self.send_json({"object": "list", "model": "stub-embed",
                            "data": data, "usage": {"prompt_tokens": 1, "total_tokens": 1}})

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


def start_app(port: int, stub_port: int, fault: bool = False) -> subprocess.Popen:
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(port),
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{DATABASE}",
        "SPRING_DATASOURCE_USERNAME": DB_USER,
        "SPRING_DATASOURCE_PASSWORD": DB_PASSWORD,
        "SPRING_AI_OPENAI_API_KEY": "stub-key",
        "SPRING_AI_OPENAI_BASE_URL": f"http://127.0.0.1:{stub_port}",
        "SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL": "stub-model",
        # Embeddings have their own base URL, defaulting to a public provider. Route
        # them to the stub as well, or the fault test measures the internet instead.
        "SPRING_AI_OPENAI_EMBEDDING_BASE_URL": f"http://127.0.0.1:{stub_port}",
        "SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL": "stub-embed",
        # The embedding bean is @ConditionalOnProperty(spring.ai.openai.embedding.api-key);
        # without this key SpringAiConfig registers no EmbeddingModel and every write
        # records "Embedding not configured" instead of exercising the provider path.
        "SPRING_AI_OPENAI_EMBEDDING_API_KEY": "stub-key",
        # Spring AI retries provider I/O errors by default; one attempt makes the
        # injected outage visible instead of silently succeeding after recovery.
        "SPRING_AI_RETRY_MAX_ATTEMPTS": "1",
        "MEMORY_REFINE_ENABLED": "false",
        "EXTRACTION_ENABLED": "false",
        "CLAUDEMEM_EXTRACTION_CONSISTENCY_SAMPLES": "1",
        "CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS": "100000",
        "CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS": "1",
        "APP_PENDING_MESSAGE_SCHEDULE_INTERVAL_MS": "1000",
    })
    extra = []
    if fault:
        # A small pool makes exhaustion reachable without hundreds of clients; the
        # short stale threshold and recovery interval keep the kill/restart test to
        # minutes instead of the ten-minute production default.
        extra = [
            "-Dspring.datasource.hikari.maximum-pool-size=3",
            "-Dspring.datasource.hikari.connection-timeout=2000",
            "-Dclaudemem.stale-message.threshold-minutes=1",
            "-Dclaudemem.stale-message.recovery-interval-ms=5000",
            "-Dclaudemem.embedding.backfill-interval-ms=5000",
            # One provider attempt, so a 504 is recorded instead of being retried for
            # minutes and then succeeding once the injected outage ends.
            "-Dspring.ai.retry.max-attempts=1",
        ]
    log = open(f"/tmp/load-test-app-{port}.log", "w")
    return subprocess.Popen(
        [
            "java", "-Xmx1g",
            f"-Dclaudemem.rate-limit.max-requests=100000",
            f"-Dclaudemem.rate-limit.window-seconds=1",
            f"-Dclaudemem.extraction.consistency-samples=1",
            f"-Dapp.pending-message.schedule-interval-ms=1000",
            *extra,
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
        "p99": round(percentile(samples, 99), 2),
        "max": round(max(samples), 2),
        "mean": round(statistics.fmean(samples), 2),
    }


def probe_strategy(port: int, path: str) -> str:
    """Read the strategy the server actually chose, so the report cannot mislabel a path.

    With an embedding model configured, a query request runs the hybrid query (pgvector
    plus tsvector), not the pure tsvector fallback. Recording the server's own strategy
    keeps the latency table honest.
    """
    try:
        with urlopen(f"http://127.0.0.1:{port}{path}", timeout=10) as response:
            payload = json.loads(response.read())
        return payload.get("strategy") or "-"
    except (HTTPError, OSError, ValueError):
        return "-"


def search_workloads(port: int, requests: int) -> dict:
    project = urlencode({"project": PROJECT})
    query_path = f"/api/search?{project}&{urlencode({'query': 'queue handler search path'})}&limit=20"
    workloads = {
        "recent": f"/api/search?{project}&limit=20",
        "deep_page": f"/api/search?{project}&limit=20&offset=200",
        "type_filter": f"/api/search?{project}&type=feature&limit=20",
        "query": query_path,
    }
    results = {}
    for name, path in workloads.items():
        measured = measure(port, path, requests)
        measured["strategy"] = probe_strategy(port, path)
        results[name] = measured
    return results


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
        # Exact shape served by /api/search?project=&type= after the V25 rewrite:
        # the scope predicate is scalar InitPlans, so the composite index sees a
        # constant scope id and can serve both the filter and the ordering.
        "type_filter": (
            "SELECT * FROM mem_observations WHERE ("
            "  memory_scope_id = (SELECT id FROM mem_memory_scopes "
            "    WHERE scope_type = 'project' "
            f"    AND scope_key = '{PROJECT}' COLLATE \"C\" LIMIT 1)"
            f"  OR ('{PROJECT}' COLLATE \"C\" = 'manual-memories' COLLATE \"C\""
            "      AND memory_scope_id = (SELECT id FROM mem_memory_scopes "
            "        WHERE scope_type = 'manual_default' "
            "          AND scope_key = 'manual-default-v1' LIMIT 1))"
            ") AND type = 'feature' ORDER BY created_at_epoch DESC LIMIT 40"
        ),
    }
    indexes = {
        "scope_recent": "idx_observations_scope_created",
        "type_recent": "idx_observations_type_created",
        "project_recent": "idx_observations_project_created",
        "type_filter": "idx_observations_scope_type_created",
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
    try:
        with urlopen(request, timeout=30) as response:
            payload = response.read()
    except HTTPError as error:
        return time.perf_counter() - started, error.code
    except OSError:
        # The service was killed mid-request: the event is not accepted, so it is
        # excluded from the expected set instead of being counted as lost.
        return time.perf_counter() - started, 0
    return time.perf_counter() - started, (200 if b"accepted" in payload else 0)


def post_session_end(port: int, session: str) -> int:
    body = json.dumps({
        "session_id": session,
        "cwd": PROJECT,
        "last_assistant_message": "Load test closed this session while events were in flight.",
    }).encode()
    request = Request(
        f"http://127.0.0.1:{port}/api/ingest/session-end",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=30) as response:
            return response.status
    except HTTPError as error:
        return error.code


def wait_drained(timeout: float = 600, require_sessions_closed: bool = False) -> bool:
    """Wait until no job is pending, processing or mid-finalize.

    `result_persisted` is transient: the summary is saved but the terminal
    transaction has not run yet. Killing the service in that window leaves the
    session open, so the concurrency mode waits for it too.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        open_jobs = int(psql(
            "SELECT count(*) FROM mem_pending_messages "
            "WHERE status IN ('pending', 'processing', 'result_persisted');"
        ))
        open_sessions = 0
        if require_sessions_closed:
            open_sessions = int(psql(
                "SELECT count(*) FROM mem_sessions "
                "WHERE content_session_id LIKE 'load-concurrent-%' "
                "AND status IN ('active', 'summarizing');"
            ))
        if open_jobs == 0 and open_sessions == 0:
            return True
        time.sleep(1)
    return False


def concurrency_benchmark(port: int, sessions: int, per_session: int, workers: int) -> dict:
    """20 sessions ingest while session-end closes them mid-flight.

    The warm-up post is required: session-end resolves an existing conversation,
    so each session must own at least one accepted event before the close wave.
    """
    before = observation_count()
    started = time.perf_counter()
    statuses = []

    def record(status: int) -> int:
        statuses.append(status)
        return status

    def warm(index: int) -> int:
        return record(post_tool_use(port, f"load-concurrent-{index}", index)[1])

    with ThreadPoolExecutor(min(workers, sessions)) as pool:
        list(pool.map(warm, range(sessions)))

    remaining = max(0, per_session - 1)
    wave_statuses: list[int] = []

    def wave(pair: tuple[int, int]) -> int:
        session, offset = pair
        status = post_tool_use(port, f"load-concurrent-{session}",
                               sessions + session * remaining + offset)[1]
        wave_statuses.append(status)
        return record(status)

    def close(index: int) -> int:
        return post_session_end(port, f"load-concurrent-{index}")

    with ThreadPoolExecutor(workers) as ingest_pool, ThreadPoolExecutor(workers) as close_pool:
        futures = [ingest_pool.submit(wave, (session, offset))
                   for session in range(sessions) for offset in range(remaining)]
        # Fire the closes only after most of the wave has been accepted. A fixed sleep
        # made the accepted share swing between 36% and 92% depending on JIT warm-up,
        # which left the concurrency claim resting on very few events. Waiting for 65%
        # keeps session-end genuinely mid-flight while guaranteeing a meaningful load.
        target = int(len(futures) * 0.65)
        deadline = time.time() + 5
        while time.time() < deadline and sum(1 for status in wave_statuses if status == 200) < target:
            time.sleep(0.05)
        closes = [close_pool.submit(close, session) for session in range(sessions)]
        for future in futures:
            future.result()
        closed = sum(1 for future in closes if future.result() == 200)

    drained = wait_drained(require_sessions_closed=True)
    total_seconds = time.perf_counter() - started
    observed = observation_count() - before
    accepted = sum(1 for status in statuses if status == 200)
    expected = accepted
    rejected = len(statuses) - accepted
    duplicates = int(psql(
        "SELECT count(*) FROM ("
        "  SELECT content_hash, count(*) AS c FROM mem_observations "
        "  WHERE content_hash IS NOT NULL GROUP BY content_hash HAVING count(*) > 1"
        ") dup;"
    ))
    stuck = int(psql(
        "SELECT count(*) FROM mem_pending_messages WHERE status = 'processing';"
    ))
    completed = int(psql(
        "SELECT count(*) FROM mem_sessions WHERE content_session_id LIKE 'load-concurrent-%' "
        "AND status = 'completed';"
    ))
    return {
        "sessions": sessions,
        "per_session": per_session,
        "posted": len(statuses),
        "rejected": rejected,
        "expected": expected,
        "accepted": accepted,
        "closed": closed,
        "drained": drained,
        "total_seconds": round(total_seconds, 2),
        "persisted": observed,
        "lost": expected - observed,
        "duplicate_hashes": duplicates,
        "stuck_processing": stuck,
        "completed_sessions": completed,
    }


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
# Fault injection
# ---------------------------------------------------------------------------

def api_status(port: int, path: str) -> int:
    try:
        with urlopen(f"http://127.0.0.1:{port}{path}", timeout=30) as response:
            response.read()
            return response.status
    except HTTPError as error:
        return error.code
    except OSError:
        return 0


def search_status(port: int) -> int:
    return api_status(port, f"/api/search?{urlencode({'project': PROJECT})}&limit=20")


def current_observation_state(session: str) -> str:
    state = psql(
        "SELECT coalesce(embedding_state, '<null>') || '|' || coalesce(embedding_error, '') "
        f"FROM mem_observations WHERE content_session_id = '{session}' "
        "ORDER BY created_at_epoch DESC LIMIT 1;"
    )
    # psql prints nothing when the row does not exist yet, which must not be read as
    # "the write already finished".
    return state or "<missing>"


def fault_pool_exhaustion(port: int) -> dict:
    """Hold an exclusive lock on mem_observations, then oversubscribe a 3-connection pool.

    Every blocked statement keeps its Hikari connection, so six concurrent readers plus
    twelve ingests turn a lock wait into real pool exhaustion. The assertion is not that
    requests fail (they should), but that accepted events survive and the queue recovers.
    """
    sessions = [f"fault-pool-{index}" for index in range(4)]
    before = observation_count()
    lock = subprocess.Popen(
        [
            "docker", "exec", "-i", CONTAINER,
            "psql", "-U", DB_USER, "-d", DATABASE,
            "-c", "BEGIN; LOCK TABLE mem_observations IN ACCESS EXCLUSIVE MODE; "
                  "SELECT pg_sleep(12); COMMIT;",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    deadline = time.time() + 15
    while time.time() < deadline:
        granted = int(psql(
            "SELECT count(*) FROM pg_locks WHERE relation = 'mem_observations'::regclass "
            "AND mode = 'AccessExclusiveLock' AND granted;"
        ))
        if granted:
            break
        time.sleep(0.2)

    ingest_statuses: list[int] = []
    reads: list[int] = []

    def ingest(index: int) -> None:
        ingest_statuses.append(
            post_tool_use(port, sessions[index % len(sessions)], 10_000 + index)[1])

    def read(_index: int) -> None:
        reads.append(search_status(port))

    started = time.perf_counter()
    with ThreadPoolExecutor(8) as pool:
        futures = [pool.submit(ingest, index) for index in range(12)]
        futures += [pool.submit(read, index) for index in range(6)]
        for future in futures:
            future.result()
    window_seconds = time.perf_counter() - started
    lock.wait(timeout=30)

    accepted = sum(1 for status in ingest_statuses if status == 200)
    time.sleep(2)
    drained = wait_drained(timeout=180)
    persisted = observation_count() - before
    stuck = int(psql("SELECT count(*) FROM mem_pending_messages WHERE status = 'processing';"))
    duplicates = int(psql(
        "SELECT count(*) FROM ("
        "  SELECT content_hash, count(*) AS c FROM mem_observations "
        "  WHERE content_hash IS NOT NULL GROUP BY content_hash HAVING count(*) > 1"
        ") dup;"
    ))
    return {
        "fault_window_seconds": round(window_seconds, 2),
        "ingest_posted": len(ingest_statuses),
        "ingest_accepted": accepted,
        "ingest_errors": [status for status in ingest_statuses if status != 200],
        "read_errors": [status for status in reads if status != 200],
        "drained": drained,
        "persisted": persisted,
        "lost": accepted - persisted,
        "stuck_processing": stuck,
        "duplicate_hashes": duplicates,
        "search_after": search_status(port),
    }


def fault_embedding(port: int, model: StubModel) -> dict:
    """Two embedding outages: a non-retryable rejection, and a provider-side timeout."""
    healthy_session = "fault-embed-healthy"
    model.embedding_fault = None
    post_tool_use(port, healthy_session, 20_001)
    baseline_deadline = time.time() + 30
    baseline = "<missing>"
    while time.time() < baseline_deadline:
        baseline = current_observation_state(healthy_session)
        if baseline.startswith("ready"):
            break
        time.sleep(1)

    # 1. Non-retryable rejection: the write path must record the failure on the row.
    reject_session = "fault-embed-reject"
    model.embedding_fault = "reject"
    post_tool_use(port, reject_session, 20_002)
    deadline = time.time() + 30
    reject_state = "<missing>"
    while time.time() < deadline:
        reject_state = current_observation_state(reject_session)
        if reject_state.startswith("failed"):
            break
        time.sleep(1)

    # 2. Provider-side timeout: the client keeps retrying, so the observation is not
    #    written while the outage lasts. Clear it after 25 s and require the queued
    #    job to finish exactly once, which is the recovery property under test.
    timeout_session = "fault-embed-timeout"
    model.embedding_fault = "timeout"
    model.embedding_delay = 3
    started = time.perf_counter()
    post_tool_use(port, timeout_session, 20_003)
    time.sleep(25)
    rows_during_outage = int(psql(
        f"SELECT count(*) FROM mem_observations WHERE content_session_id = '{timeout_session}';"))
    model.embedding_fault = None
    model.embedding_delay = 0.0
    deadline = time.time() + 320
    timeout_state = "<missing>"
    while time.time() < deadline:
        timeout_state = current_observation_state(timeout_session)
        if timeout_state.startswith("ready"):
            break
        time.sleep(2)
    recovery_seconds = time.perf_counter() - started

    # The rejection path must be repaired by the backfill sweep now that the endpoint
    # is healthy again.
    repair_started = time.perf_counter()
    deadline = time.time() + 60
    repaired = "<missing>"
    while time.time() < deadline:
        repaired = current_observation_state(reject_session)
        if repaired.startswith("ready"):
            break
        time.sleep(1)
    repair_seconds = time.perf_counter() - repair_started

    return {
        "healthy_baseline": baseline,
        "reject_fault_state": reject_state,
        "reject_repaired_state": repaired,
        "reject_repair_seconds": round(repair_seconds, 2),
        "timeout_rows_during_outage": rows_during_outage,
        "timeout_state_after_recovery": timeout_state,
        "timeout_recovery_seconds": round(recovery_seconds, 2),
        "timeout_rows_final": int(psql(
            f"SELECT count(*) FROM mem_observations WHERE content_session_id = '{timeout_session}';")),
    }


def fault_kill_restart(port: int, args, model: StubModel, app: subprocess.Popen) -> tuple[dict, subprocess.Popen]:
    """SIGKILL the service while observation jobs are processing, then restart it."""
    sessions = [f"fault-kill-{index}" for index in range(3)]
    before = observation_count()
    statuses: list[int] = []

    def post(index: int) -> None:
        statuses.append(post_tool_use(port, sessions[index % len(sessions)], 30_000 + index)[1])

    model.chat_delay = 2.0
    with ThreadPoolExecutor(6) as pool:
        list(pool.map(post, range(15)))

    deadline = time.time() + 40
    processing_at_kill = 0
    while time.time() < deadline:
        processing_at_kill = int(psql(
            "SELECT count(*) FROM mem_pending_messages WHERE status = 'processing';"))
        if processing_at_kill >= 3:
            break
        time.sleep(0.5)
    app.kill()
    app.wait(timeout=20)
    model.chat_delay = 0.0

    accepted = sum(1 for status in statuses if status == 200)
    restarted = start_app(args.port, args.stub_port, fault=True)
    wait_health(args.port, restarted)
    # Startup recovery only picks rows older than the stale threshold, and the periodic
    # sweep starts after a one-minute initial delay; give both a chance to run.
    drained = wait_drained(timeout=200)
    persisted = observation_count() - before
    stuck = int(psql("SELECT count(*) FROM mem_pending_messages WHERE status = 'processing';"))
    duplicates = int(psql(
        "SELECT count(*) FROM ("
        "  SELECT content_hash, count(*) AS c FROM mem_observations "
        "  WHERE content_hash IS NOT NULL GROUP BY content_hash HAVING count(*) > 1"
        ") dup;"
    ))
    recovered_log = ""
    try:
        with open(f"/tmp/load-test-app-{port}.log", "rb") as log_file:
            for line in log_file:
                text = line.decode("utf-8", "replace")
                if "Recovered" in text and "stale" in text:
                    # The jar logs with ANSI colours; keep the report plain text.
                    recovered_log = re.sub(r"\x1b\[[0-9;]*m", "", text).strip()
    except OSError:
        pass
    return {
        "accepted": accepted,
        "processing_at_kill": processing_at_kill,
        "drained": drained,
        "persisted": persisted,
        "lost": accepted - persisted,
        "stuck_processing": stuck,
        "duplicate_hashes": duplicates,
        "recovery_log": recovered_log,
        "search_after": search_status(port),
    }, restarted


def fault_passed(pool: dict, embedding: dict, kill: dict) -> bool:
    return (
        pool["lost"] == 0 and pool["stuck_processing"] == 0 and pool["drained"]
        and pool["search_after"] == 200
        and embedding["healthy_baseline"].startswith("ready")
        and embedding["reject_fault_state"].startswith("failed")
        and embedding["reject_repaired_state"].startswith("ready")
        and embedding["timeout_state_after_recovery"].startswith("ready")
        and embedding["timeout_rows_final"] == 1
        and kill["lost"] == 0 and kill["stuck_processing"] == 0 and kill["drained"]
        and kill["duplicate_hashes"] == 0 and kill["search_after"] == 200
    )


def render_fault_report(args, pool: dict, embedding: dict, kill: dict) -> str:
    verdict = "PASS" if fault_passed(pool, embedding, kill) else "FAIL"
    lines = [
        "# Fault injection: pool exhaustion, embedding outage, kill during processing",
        "",
        f"- Date: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- Database: {DATABASE} on {CONTAINER}:{DB_PORT} (disposable, Flyway-migrated)",
        f"- Service: packaged jar on http://127.0.0.1:{args.port}, Hikari pool 3, "
        "connection timeout 2 s, stale threshold 1 min, recovery every 5 s",
        "- Model: local OpenAI-compatible stub; chat delay 2 s during the kill, "
        "embedding endpoint stalls 3 s then answers 504 (provider-side timeout)",
        "",
        "## 1. DB connection-pool exhaustion",
        "",
        "| metric | value |",
        "|---|---:|",
        f"| fault window seconds | {pool['fault_window_seconds']} |",
        f"| ingest requests posted | {pool['ingest_posted']} |",
        f"| ingest accepted | {pool['ingest_accepted']} |",
        f"| ingest error codes | {pool['ingest_errors']} |",
        f"| search error codes during the lock | {pool['read_errors']} |",
        f"| queue drained after release | {pool['drained']} |",
        f"| accepted events persisted | {pool['persisted']} |",
        f"| accepted events lost | {pool['lost']} |",
        f"| rows stuck in processing | {pool['stuck_processing']} |",
        f"| duplicate content hashes | {pool['duplicate_hashes']} |",
        f"| search status after recovery | {pool['search_after']} |",
        "",
        "## 2. Embedding endpoint outage",
        "",
        f"`{embedding['healthy_baseline']}` is the healthy baseline: the stub returns a "
        "1024-wide vector and the row reaches `ready`.",
        "",
        "| metric | value |",
        "|---|---:|",
        f"| non-retryable rejection: row state | {embedding['reject_fault_state']} |",
        f"| non-retryable rejection: backfill repair | {embedding['reject_repaired_state']} "
        f"({embedding['reject_repair_seconds']} s) |",
        f"| timeout: rows written while the endpoint was down | "
        f"{embedding['timeout_rows_during_outage']} |",
        f"| timeout: state after the endpoint recovered | "
        f"{embedding['timeout_state_after_recovery']} |",
        f"| timeout: outage start to persisted row (s) | "
        f"{embedding['timeout_recovery_seconds']} |",
        f"| timeout: rows written in total | {embedding['timeout_rows_final']} |",
        "",
        "A provider-side timeout is retried by the OpenAI client, so the observation is "
        "not written until the retry budget is spent or the endpoint recovers; the event "
        "stays in the queue and is written exactly once. A non-retryable rejection is "
        "recorded as `failed` on the row and repaired by the embedding backfill sweep.",
        "",
        "## 3. SIGKILL during processing, then restart",
        "",
        "| metric | value |",
        "|---|---:|",
        f"| events accepted before the kill | {kill['accepted']} |",
        f"| jobs processing at the kill | {kill['processing_at_kill']} |",
        f"| queue drained after restart | {kill['drained']} |",
        f"| accepted events persisted | {kill['persisted']} |",
        f"| accepted events lost | {kill['lost']} |",
        f"| rows stuck in processing | {kill['stuck_processing']} |",
        f"| duplicate content hashes | {kill['duplicate_hashes']} |",
        f"| search status after restart | {kill['search_after']} |",
        f"| recovery log | {kill['recovery_log'] or 'none'} |",
        "",
        f"Verdict: {verdict}. The pass condition is persistence and recovery, not the "
        "absence of errors: during an injected outage the API is allowed to return errors, "
        "but no accepted event may be lost and no queue row may stay in processing.",
        "",
    ]
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------

def render_concurrency_report(args, result: dict) -> str:
    verdict = (
        "PASS"
        if result["accepted"] == result["expected"]
        and result["persisted"] == result["expected"]
        and result["duplicate_hashes"] == 0
        and result["stuck_processing"] == 0
        and result["drained"]
        else "FAIL"
    )
    lines = [
        "# Concurrency: 20 sessions ingesting while session-end closes them",
        "",
        f"- Date: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- Database: {DATABASE} on {CONTAINER}:{DB_PORT} (disposable, Flyway-migrated)",
        f"- Service: packaged jar on http://127.0.0.1:{args.port}",
        f"- Model: local OpenAI-compatible stub, so the queue is the only bottleneck",
        "",
        "| metric | value |",
        "|---|---:|",
        f"| sessions | {result['sessions']} |",
        f"| events per session | {result['per_session']} |",
        f"| ingest requests posted | {result['posted']} |",
        f"| ingest requests accepted | {result['accepted']} |",
        f"| ingest requests rejected (409 after session end) | {result['rejected']} |",
        f"| accepted events expected to persist | {result['expected']} |",
        f"| session-end requests accepted | {result['closed']} |",
        f"| sessions marked completed | {result['completed_sessions']} |",
        f"| queue drained | {result['drained']} |",
        f"| elapsed seconds | {result['total_seconds']} |",
        f"| observations persisted | {result['persisted']} |",
        f"| observations lost | {result['lost']} |",
        f"| duplicate content hashes | {result['duplicate_hashes']} |",
        f"| rows stuck in processing | {result['stuck_processing']} |",
        "",
        f"Verdict: {verdict}. A job is lost when session-end cancels it; the observed "
        "count above must equal the accepted ingest count.",
        "",
    ]
    return "\n".join(lines)


def render_report(args, stages: list[dict], indexes: dict, queue: dict) -> str:
    last = stages[-1]
    lines = [
        f"# Load test: {last['rows']} observations, search latency, index evidence, queue throughput",
        "",
        f"- Date: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- Database: {DATABASE} on {CONTAINER}:{DB_PORT} (disposable, Flyway-migrated)",
        f"- Service: packaged jar on http://127.0.0.1:{args.port}",
        f"- Workload: {args.requests} sequential requests per search path; pages of 20 rows",
        "- Model: local OpenAI-compatible stub; the query path therefore measures the hybrid "
        "SQL shape (pgvector + tsvector) without provider latency",
        "",
        "## Search latency by table size",
        "",
        "| rows | path | strategy | p50 ms | p95 ms | p99 ms | max ms |",
        "|---:|---|---|---:|---:|---:|---:|",
    ]
    for stage in stages:
        for path, values in stage["search"].items():
            lines.append(
                f"| {stage['rows']} | {path} | {values['strategy']} | {values['p50']} | "
                f"{values['p95']} | {values['p99']} | {values['max']} |"
            )

    lines += [
        "",
        f"## Index evidence ({last['rows']} rows; V23 composites + V25 scope/type)",
        "",
        "| query | index | index used | with index ms | without index ms | plan without index |",
        "|---|---|---|---:|---:|---|",
    ]
    for name, value in indexes.items():
        used = ", ".join(value["with_index_used"]) or "none"
        lines.append(
            f"| {name} | {value['index']} | {used} | {value['with_index_ms']} | "
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
        "## Degradation knee (p95 > 2x the 1k-row baseline)",
        "",
        "| path | strategy | p95 at 1k | 2x threshold | first rows over 2x |",
        "|---|---|---:|---:|---:|",
    ]
    first = stages[0]
    for path in first["search"]:
        baseline = first["search"][path]["p95"]
        knee = next(
            (
                stage["rows"]
                for stage in stages[1:]
                if baseline and stage["search"][path]["p95"] > 2 * baseline
            ),
            None,
        )
        lines.append(
            f"| {path} | {first['search'][path]['strategy']} | {baseline} | "
            f"{round(2 * baseline, 2)} | {knee if knee is not None else 'not crossed'} |"
        )

    lines += ["", "## Capacity conclusion", ""]
    crossed = []
    for path in first["search"]:
        baseline = first["search"][path]["p95"]
        knee = next(
            (
                stage["rows"]
                for stage in stages[1:]
                if baseline and stage["search"][path]["p95"] > 2 * baseline
            ),
            None,
        )
        if knee is not None:
            crossed.append(
                f"{path} ({first['search'][path]['strategy']}) p95 crossed 2x "
                f"({round(2 * baseline, 2)} ms) at {knee} rows"
            )
    if crossed:
        lines.append("- " + "; ".join(crossed) + ".")
    else:
        lines.append(
            f"- No measured path crossed the 2x p95 threshold between {first['rows']} and "
            f"{last['rows']} rows; every path stayed inside "
            f"{max(last['search'][p]['p95'] for p in last['search']) + 1:.0f} ms p95."
        )
    lines.append(
        f"- Queue: {queue['persisted']} of {queue['events']} events persisted "
        f"({queue['throughput_per_second']} rows/s), {queue['duplicate_hashes']} duplicate "
        f"hashes, {queue['stuck_processing']} rows left in processing."
    )
    lines.append(
        f"- Highest observed p95 across all paths at {last['rows']} rows: "
        f"{max(last['search'][p]['p95'] for p in last['search'])} ms "
        f"({max(last['search'], key=lambda p: last['search'][p]['p95'])})."
    )
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["soak", "concurrency", "fault"], default="soak")
    parser.add_argument("--sessions", type=int, default=20)
    parser.add_argument("--per-session", type=int, default=10)
    parser.add_argument("--rows", type=int, default=10000)
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--events", type=int, default=200)
    parser.add_argument("--workers", type=int, default=20)
    parser.add_argument("--port", type=int, default=37778)
    parser.add_argument("--stub-port", type=int, default=9100)
    parser.add_argument("--report", default=None)
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()

    model = StubModel()
    stub = start_stub(model, args.stub_port)
    create_database()
    app = start_app(args.port, args.stub_port, fault=args.mode == "fault")
    try:
        wait_health(args.port, app)
        seed_base()

        if args.mode == "fault":
            pool = fault_pool_exhaustion(args.port)
            print(f"pool: {pool}", flush=True)
            embedding = fault_embedding(args.port, model)
            print(f"embedding: {embedding}", flush=True)
            kill, app = fault_kill_restart(args.port, args, model, app)
            print(f"kill: {kill}", flush=True)
            report = render_fault_report(args, pool, embedding, kill)
            path = Path(args.report or ROOT / "docs/archive/2026-11-03_fault-injection.md")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(report + "\n")
            print(f"report written to {path}")
            return 0 if fault_passed(pool, embedding, kill) else 1

        if args.mode == "concurrency":
            result = concurrency_benchmark(args.port, args.sessions, args.per_session, args.workers)
            print(f"concurrency: {result}", flush=True)
            report = render_concurrency_report(args, result)
            path = Path(args.report or ROOT / "docs/archive/2026-11-03_concurrency-ingest.md")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(report + "\n")
            print(f"report written to {path}")
            return 0 if result["lost"] == 0 else 1

        stages = []
        # Always include the 1k/5k/10k points from the earlier report when the requested
        # size is larger, so every run produces a directly comparable curve.
        targets = sorted({t for t in (1000, 5000, 10000, args.rows) if 1000 <= t <= args.rows})
        if args.rows > targets[-1]:
            targets.append(args.rows)
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
        default_name = f"{time.strftime('%Y-%m-%d')}_load-test-{args.rows // 1000}k.md"
        path = Path(args.report or ROOT / "docs/archive" / default_name)
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
