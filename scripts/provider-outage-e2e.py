#!/usr/bin/env python3
"""End-to-end behaviour when the model provider is down.

Manual harness, not part of the gate. It starts a throwaway database, an
OpenAI-compatible stub that answers HTTP 500 to every chat and embedding call,
and the real jar pointed at it, then drives the three user-visible paths:

1. `POST /api/ingest/observation` — the write must still succeed, and the row
   must say `embedding_state=failed` with a readable `embedding_error`.
2. `GET /api/search` — the reader must get lexical results, and the envelope
   must name the strategy (`tsvector`) it actually used.
3. `POST /api/ingest/tool-use` — the hook must keep receiving `accepted`
   while the provider fails; after `failure-threshold` unreachable calls the
   circuit opens, later jobs return in milliseconds without a provider call,
   and each failure is visible in `mem_pending_messages`
   (`status` / `retry_count` / `failure_kind`).

Everything here is a throwaway: the database is dropped and recreated, the
jar and stub are started on free-ish ports, and the app log is written to
/tmp/provider-outage-app.log. Raw results go to /tmp/provider-outage.json.

The env var for the container is PROVIDER_PG_CONTAINER (default
claude-mem-test-pg) and the jar comes from backend/target unless --jar is
passed, matching the other harnesses.

Usage:
    python3 scripts/provider-outage-e2e.py
    python3 scripts/provider-outage-e2e.py --jar backend/target/cortex-ce-0.1.0.jar
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = os.environ.get("PROVIDER_PG_CONTAINER", "claude-mem-test-pg")
DB_USER = "postgres"
DB_PASSWORD = "123456"
DB_PORT = int(os.environ.get("PROVIDER_DB_PORT", "5433"))
DB_NAME = "cortexce_provider_outage"
PROJECT = "/tmp/provider-outage"
APP_PORT = int(os.environ.get("PROVIDER_APP_PORT", "18123"))
STUB_PORT = int(os.environ.get("PROVIDER_STUB_PORT", "18124"))
APP_LOG = Path("/tmp/provider-outage-app.log")
CIRCUIT_LOG = "Provider circuit is open; skipping the call (fast fail)"
OPENED_LOG = "Provider circuit opened"


def psql(sql: str, database: str = "postgres", timeout: int = 300) -> str:
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


# ---------------------------------------------------------------------------
# A stub that is always unavailable, but counts what it was asked to do
# ---------------------------------------------------------------------------

def start_outage_stub(port: int) -> tuple[ThreadingHTTPServer, dict]:
    counters = {"chat": 0, "embeddings": 0, "other": 0}
    lock = threading.Lock()
    body = json.dumps({
        "error": {"message": "stub provider is down for the outage test",
                  "type": "server_error", "code": 500}
    }).encode()

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def read_body(self) -> bytes:
            length = int(self.headers.get("Content-Length", "0"))
            return self.rfile.read(length) if length else b""

        def fail(self) -> None:
            try:
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                self.close_connection = True

        def do_POST(self):  # noqa: N802 - BaseHTTPRequestHandler contract
            self.read_body()
            path = self.path.rstrip("/")
            with lock:
                if path.endswith("/embeddings"):
                    counters["embeddings"] += 1
                elif path.endswith("/chat/completions"):
                    counters["chat"] += 1
                else:
                    counters["other"] += 1
            self.fail()

        def do_GET(self):  # noqa: N802
            self.fail()

        def log_message(self, *_args):
            pass

    class Server(ThreadingHTTPServer):
        request_queue_size = 64
        daemon_threads = True

    server = Server(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, counters


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


def start_app(jar: str = "") -> subprocess.Popen:
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(APP_PORT),
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{DB_NAME}",
        "SPRING_DATASOURCE_USERNAME": DB_USER,
        "SPRING_DATASOURCE_PASSWORD": DB_PASSWORD,
        "SPRING_AI_OPENAI_API_KEY": "stub-key",
        "SPRING_AI_OPENAI_BASE_URL": f"http://127.0.0.1:{STUB_PORT}",
        "SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL": "stub-model",
        "SPRING_AI_OPENAI_EMBEDDING_BASE_URL": f"http://127.0.0.1:{STUB_PORT}",
        "SPRING_AI_OPENAI_EMBEDDING_API_KEY": "stub-key",
        "SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL": "stub-embed",
        # One failure per job, so the circuit's three-strike rule is reached by
        # three jobs rather than by three attempts of a single job.
        "SPRING_AI_RETRY_MAX_ATTEMPTS": "1",
        "CLAUDEMEM_LLM_RETRY_MAX_ATTEMPTS": "1",
        "CLAUDEMEM_EXTRACTION_CONSISTENCY_SAMPLES": "1",
        "CLAUDEMEM_MAX_RETRIES": "1",
        "CLAUDEMEM_LLM_CIRCUIT_BREAKER_FAILURE_THRESHOLD": "3",
        "CLAUDEMEM_LLM_CIRCUIT_BREAKER_COOLDOWN_MS": "60000",
        "MEMORY_REFINE_ENABLED": "false",
        "EXTRACTION_ENABLED": "false",
        "CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS": "100000",
        "CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS": "1",
        # Keep the scheduled sweep and the backfill out of the timeline; the
        # enqueue event is what should drive the job.
        "APP_PENDING_MESSAGE_SCHEDULE_INTERVAL_MS": "999999999",
        "CLAUDEMEM_EMBEDDING_BACKFILL_INTERVAL_MS": "999999999",
        "APP_MEMORY_REFINE_SCHEDULE_INTERVAL_MS": "999999999",
    })
    log = open(APP_LOG, "w")
    return subprocess.Popen(
        ["java", "-Xmx1g", "-jar", str(jar_path(jar))],
        cwd=str(ROOT), env=env, stdout=log, stderr=subprocess.STDOUT,
    )


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


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def request(method: str, path: str, payload: dict | None = None,
            timeout: float = 30) -> tuple[int, str, float]:
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(
        f"http://127.0.0.1:{APP_PORT}{path}", data=data, method=method,
        headers={"Content-Type": "application/json"} if data else {})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read().decode(errors="replace")
            return response.status, body, round((time.perf_counter() - started) * 1000, 1)
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")
        return error.code, body, round((time.perf_counter() - started) * 1000, 1)


def wait_for(predicate, timeout: float, interval: float = 0.25):
    deadline = time.time() + timeout
    while time.time() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(interval)
    return None


def log_text() -> str:
    return APP_LOG.read_text(errors="replace") if APP_LOG.exists() else ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default="")
    parser.add_argument("--out", default="/tmp/provider-outage.json")
    parser.add_argument("--observation-timeout", type=float, default=150.0,
                        help="seconds to wait for the embedding call to give up; the "
                             "manual embedding model still uses Spring AI's default retry "
                             "template (ten attempts), so a write under outage is slow")
    parser.add_argument("--keep", action="store_true",
                        help="leave the database and the app running for inspection")
    args = parser.parse_args()

    prepare_database()
    stub, counters = start_outage_stub(STUB_PORT)
    process = start_app(args.jar)
    result: dict = {"database": DB_NAME, "project": PROJECT,
                    "stub": f"http://127.0.0.1:{STUB_PORT}", "steps": {}}
    try:
        result["health_seconds"] = wait_health(process)

        # 1. A write still succeeds; the row records why it has no vector.
        status, body, elapsed = request("POST", "/api/ingest/observation", {
            "content_session_id": "outage-session",
            "project_path": PROJECT,
            "type": "bugfix",
            "title": "Queue handler drops events at session end",
            "narrative": "The queue handler drops events at session end because the "
                         "drain deadline expires before pending rows are claimed.",
        }, timeout=args.observation_timeout)
        observation = json.loads(body) if body.startswith("{") else {}
        db_state = psql(
            "SELECT embedding_state, embedding_error FROM mem_observations "
            "ORDER BY created_at_epoch DESC LIMIT 1;", DB_NAME).split("|")
        result["steps"]["observation_write"] = {
            "http_status": status, "elapsed_ms": elapsed,
            "embedding_state": observation.get("embedding_state"),
            "db_embedding_state": db_state[0] if db_state else None,
            "db_embedding_error": db_state[1] if len(db_state) > 1 else None,
            "rows": int(psql("SELECT count(*) FROM mem_observations;", DB_NAME)),
        }

        # 2. The reader gets lexical results and is told that is what happened.
        status, body, elapsed = request(
            "GET", "/api/search?project=" + urllib.parse.quote(PROJECT)
            + "&query=" + urllib.parse.quote("queue handler drops events at session end")
            + "&limit=5", timeout=args.observation_timeout)
        search = json.loads(body) if body.startswith("{") else {}
        result["steps"]["search_without_embedding"] = {
            "http_status": status, "elapsed_ms": elapsed,
            "strategy": search.get("strategy"),
            "fell_back": search.get("fell_back"),
            "results": len(search.get("observations") or []),
            "first_title": (search.get("observations") or [{}])[0].get("title")
            if search.get("observations") else None,
        }

        # 3. Three jobs, one provider call each -> the circuit opens.
        jobs = []
        for index in range(3):
            status, body, elapsed = request("POST", "/api/ingest/tool-use", {
                "session_id": "outage-session",
                "tool_name": "Bash",
                "tool_input": {"command": f"echo job-{index}"},
                "tool_response": {"output": "ok"},
                "cwd": PROJECT,
            })
            jobs.append({"http_status": status, "body": body.strip(),
                         "elapsed_ms": elapsed})
        opened = wait_for(lambda: OPENED_LOG in log_text(), timeout=30)
        result["steps"]["circuit_opens"] = {
            "jobs": jobs, "opened_log_seen": bool(opened),
            "chat_calls": counters["chat"],
            "pending_after_three": psql(
                "SELECT coalesce(string_agg(status || '/' || retry_count || '/' || "
                "coalesce(failure_kind, '-'), ',' ORDER BY created_at_epoch), '') "
                "FROM mem_pending_messages;", DB_NAME),
        }

        # 4. The next job returns without touching the provider.
        chat_before = counters["chat"]
        status, body, elapsed = request("POST", "/api/ingest/tool-use", {
            "session_id": "outage-session",
            "tool_name": "Bash",
            "tool_input": {"command": "echo after-circuit"},
            "tool_response": {"output": "ok"},
            "cwd": PROJECT,
        })
        fast_fail = wait_for(lambda: CIRCUIT_LOG in log_text(), timeout=20)
        time.sleep(0.5)
        result["steps"]["circuit_open_fast_fail"] = {
            "http_status": status, "body": body.strip(), "elapsed_ms": elapsed,
            "fast_fail_log_seen": bool(fast_fail),
            "chat_calls_before": chat_before, "chat_calls_after": counters["chat"],
            "extra_provider_calls": counters["chat"] - chat_before,
            "pending_rows": psql(
                "SELECT coalesce(string_agg(status || '/' || retry_count || '/' || "
                "coalesce(failure_kind, '-'), ',' ORDER BY created_at_epoch), '') "
                "FROM mem_pending_messages;", DB_NAME),
        }

        # 5. Health stays green; embedding calls were attempted and failed.
        status, _body, elapsed = request("GET", "/api/health")
        result["steps"]["health"] = {"http_status": status, "elapsed_ms": elapsed}
        result["stub_counters"] = dict(counters)
        result["log_lines"] = {
            "circuit_opened": sum(1 for line in log_text().splitlines()
                                  if OPENED_LOG in line),
            "circuit_closed": sum(1 for line in log_text().splitlines()
                                  if "Provider circuit closed" in line),
            "fast_fail": sum(1 for line in log_text().splitlines()
                             if CIRCUIT_LOG in line),
            "observation_embedding_failed": sum(
                1 for line in log_text().splitlines()
                if "saving without embedding" in line),
        }
    finally:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
        stub.shutdown()
        if not args.keep:
            psql(f"DROP DATABASE IF EXISTS {DB_NAME} WITH (FORCE);")

    Path(args.out).write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    print(f"raw: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
