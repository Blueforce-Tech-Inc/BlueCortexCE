#!/usr/bin/env python3
"""Prove the integrity audit catches every class, using seeded dirty rows.

Creates a disposable database, lets the packaged application migrate it, inserts
one fixture per defect class, then asserts:

  * the default run reports all nine checks with a non-zero count and writes no
    rows (read-only);
  * a repair run clears the six repairable classes and leaves the two
    review-only classes (project mismatch, summary provenance) plus the backlog.

Usage: python3 scripts/test-data-integrity-audit.py
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = "claude-mem-test-pg"
PORT = 37892
DB = f"cortexce_integrity_{int(time.time())}"
USER = "postgres"


def psql(sql: str, database: str = DB) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", USER, "-d", database,
         "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, timeout=120,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}\n---\n{sql[:800]}")
    return result.stdout.strip()


def migrate() -> None:
    jar = sorted((ROOT / "backend/target").glob("cortex-ce-*.jar"))
    jar = [item for item in jar if not item.name.endswith("-sources.jar")][-1]
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": str(PORT),
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:5433/{DB}",
        "SPRING_DATASOURCE_USERNAME": USER,
        "SPRING_DATASOURCE_PASSWORD": "123456",
        "SPRING_AI_OPENAI_API_KEY": "unused",
        "SPRING_AI_OPENAI_EMBEDDING_API_KEY": "unused",
        "MEMORY_REFINE_ENABLED": "false",
    })
    log = open("/tmp/integrity-selftest-migrate.log", "w")
    process = subprocess.Popen(["java", "-Xmx512m", "-jar", str(jar)],
                               env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(120):
            if process.poll() is not None:
                raise SystemExit("application exited during migration; see "
                                 "/tmp/integrity-selftest-migrate.log")
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/api/health", timeout=2):
                    return
            except OSError:
                time.sleep(1)
        raise SystemExit("application did not become healthy")
    finally:
        process.terminate()
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()


def seed() -> None:
    psql("""
    INSERT INTO mem_memory_scopes (id, scope_type, scope_key, scope_key_sha256, display_name)
    VALUES ('00000000-0000-0000-0000-00000000000a', 'project', '/tmp/audit-a',
            sha256(convert_to('/tmp/audit-a', 'UTF8')), 'A'),
           ('00000000-0000-0000-0000-00000000000b', 'project', '/tmp/audit-b',
            sha256(convert_to('/tmp/audit-b', 'UTF8')), 'B');

    INSERT INTO mem_sessions (id, content_session_id, project_path, started_at_epoch,
                              status, platform_source)
    VALUES ('00000000-0000-0000-0000-0000000000a1', 'audit-a', '/tmp/audit-a',
            extract(epoch FROM now()) * 1000, 'active', 'claude'),
           ('00000000-0000-0000-0000-0000000000b1', 'audit-b', '/tmp/audit-b',
            extract(epoch FROM now()) * 1000, 'active', 'claude');

    -- 1 orphan observation (no parent session)
    SET session_replication_role = replica;
    INSERT INTO mem_observations (id, content_session_id, project_path, type, title,
                                  content, created_at_epoch, memory_scope_id,
                                  conversation_id, embedding_state)
    VALUES ('00000000-0000-0000-0000-0000000000f1', 'audit-missing', '/tmp/audit-a',
            'discovery', 'orphan observation', 'orphan', extract(epoch FROM now()) * 1000,
            '00000000-0000-0000-0000-00000000000a',
            '00000000-0000-0000-0000-0000000000ff', 'skipped');

    -- 2 orphan summary
    INSERT INTO mem_summaries (id, content_session_id, project_path, request,
                               created_at_epoch, conversation_id)
    VALUES ('00000000-0000-0000-0000-0000000000f2', 'audit-missing', '/tmp/audit-a',
            'orphan summary', extract(epoch FROM now()) * 1000,
            '00000000-0000-0000-0000-0000000000ff');
    SET session_replication_role = DEFAULT;

    -- 3 scope/project mismatch
    INSERT INTO mem_observations (id, content_session_id, project_path, type, title,
                                  content, created_at_epoch, memory_scope_id,
                                  conversation_id, embedding_state)
    VALUES ('00000000-0000-0000-0000-0000000000f3', 'audit-a', '/tmp/audit-a',
            'discovery', 'scope mismatch', 'scope', extract(epoch FROM now()) * 1000,
            '00000000-0000-0000-0000-00000000000b',
            '00000000-0000-0000-0000-0000000000a1', 'skipped');

    -- 4 observation/conversation project mismatch (scope matches its own project)
    INSERT INTO mem_observations (id, content_session_id, project_path, type, title,
                                  content, created_at_epoch, memory_scope_id,
                                  conversation_id, embedding_state)
    VALUES ('00000000-0000-0000-0000-0000000000f4', 'audit-a', '/tmp/audit-b',
            'discovery', 'conversation mismatch', 'conversation',
            extract(epoch FROM now()) * 1000,
            '00000000-0000-0000-0000-00000000000b',
            '00000000-0000-0000-0000-0000000000a1', 'skipped');

    -- 5 embedding state without vector, and vector hidden behind a failed state
    INSERT INTO mem_observations (id, content_session_id, project_path, type, title,
                                  content, created_at_epoch, memory_scope_id,
                                  conversation_id, embedding_state)
    VALUES ('00000000-0000-0000-0000-0000000000f5', 'audit-a', '/tmp/audit-a',
            'discovery', 'ready without vector', 'state', extract(epoch FROM now()) * 1000,
            '00000000-0000-0000-0000-00000000000a',
            '00000000-0000-0000-0000-0000000000a1', 'ready');
    INSERT INTO mem_observations (id, content_session_id, project_path, type, title,
                                  content, created_at_epoch, memory_scope_id,
                                  conversation_id, embedding_state, embedding_1024)
    VALUES ('00000000-0000-0000-0000-0000000000f6', 'audit-a', '/tmp/audit-a',
            'discovery', 'vector behind failed', 'state', extract(epoch FROM now()) * 1000,
            '00000000-0000-0000-0000-00000000000a',
            '00000000-0000-0000-0000-0000000000a1', 'failed',
            array_fill(0.01::real, ARRAY[1024])::vector);

    -- 6 duplicate hash inside the 30-second window
    INSERT INTO mem_observations (id, content_session_id, project_path, type, title,
                                  content, created_at_epoch, memory_scope_id,
                                  conversation_id, embedding_state, content_hash)
    VALUES ('00000000-0000-0000-0000-0000000000f7', 'audit-a', '/tmp/audit-a',
            'discovery', 'duplicate one', 'duplicate', extract(epoch FROM now()) * 1000,
            '00000000-0000-0000-0000-00000000000a',
            '00000000-0000-0000-0000-0000000000a1', 'skipped', 'duplicate-hash'),
           ('00000000-0000-0000-0000-0000000000f8', 'audit-a', '/tmp/audit-a',
            'discovery', 'duplicate two', 'duplicate',
            extract(epoch FROM now()) * 1000 + 1000,
            '00000000-0000-0000-0000-00000000000a',
            '00000000-0000-0000-0000-0000000000a1', 'skipped', 'duplicate-hash');

    -- 7 one stuck processing row
    INSERT INTO mem_pending_messages (id, session_db_id, content_session_id, message_type,
                                      status, created_at_epoch, started_processing_at_epoch)
    VALUES ('00000000-0000-0000-0000-0000000000e1',
            '00000000-0000-0000-0000-0000000000a1', 'audit-a', 'observation', 'processing',
            extract(epoch FROM now()) * 1000 - 7200000,
            extract(epoch FROM now()) * 1000 - 7200000);

    -- 8 five pending rows (threshold in the self-test is 2)
    INSERT INTO mem_pending_messages (id, session_db_id, content_session_id, message_type,
                                      status, created_at_epoch)
    SELECT ('00000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
           '00000000-0000-0000-0000-0000000000a1', 'audit-a', 'observation', 'pending',
           extract(epoch FROM now()) * 1000 - n * 60000
    FROM generate_series(1, 5) AS n;

    -- 9 summary snapshot naming a deleted observation
    INSERT INTO mem_pending_messages (id, session_db_id, content_session_id, message_type,
                                      status, created_at_epoch, summary_input_snapshot)
    VALUES ('00000000-0000-0000-0000-0000000000e2',
            '00000000-0000-0000-0000-0000000000a1', 'audit-a', 'summarize', 'processed',
            extract(epoch FROM now()) * 1000,
            '{"observation_ids":["00000000-0000-0000-0000-0000000000fe"]}'::jsonb);
    """)


def audit(*extra: str) -> tuple[int, dict]:
    command = [sys.executable, str(ROOT / "scripts/data-integrity-audit.py"),
               "--database", DB, "--backlog-max", "2", "--stuck-minutes", "15", *extra]
    result = subprocess.run(command, text=True, capture_output=True, timeout=120)
    return result.returncode, {"stdout": result.stdout, "stderr": result.stderr}


def main() -> int:
    psql(f'DROP DATABASE IF EXISTS "{DB}" WITH (FORCE);', database="postgres")
    psql(f'CREATE DATABASE "{DB}";', database="postgres")
    try:
        migrate()
        seed()
        before = psql("SELECT count(*) FROM mem_observations;")
        code, first = audit("--json", "/tmp/integrity-selftest-first.json")
        report = json.load(open("/tmp/integrity-selftest-first.json"))
        counts = {item["key"]: item["count"] for item in report["checks"]}
        after_dry_run = psql("SELECT count(*) FROM mem_observations;")
        print("first run:", json.dumps(counts, sort_keys=True))
        assert code == 1, f"expected findings exit 1, got {code}: {first['stderr']}"
        assert before == after_dry_run, "dry-run changed the table"
        missing = [key for key, value in counts.items() if value == 0]
        assert not missing, f"checks that caught nothing: {missing}"

        code, second = audit("--apply-destructive")
        assert code == 1, f"repair run should still report the pre-repair findings, got {code}"

        code, third = audit("--json", "/tmp/integrity-selftest-third.json")
        repaired = json.load(open("/tmp/integrity-selftest-third.json"))
        remaining = {item["key"]: item["count"] for item in repaired["checks"]}
        print("after repair:", json.dumps(remaining, sort_keys=True))
        for key in ("orphan_observations", "orphan_summaries", "scope_project_mismatch",
                    "embedding_state_mismatch", "duplicate_hash_unsuppressed",
                    "stuck_processing"):
            assert remaining[key] == 0, f"{key} not repaired: {remaining[key]}"
        assert remaining["summary_snapshot_missing_obs"] == 1
        assert remaining["conversation_project_mismatch"] == 1
        assert remaining["pending_backlog"] > 0
        print("PASS: nine classes detected; six repairable classes cleared; "
              "review-only classes still reported")
        return 0
    finally:
        psql(f'DROP DATABASE IF EXISTS "{DB}" WITH (FORCE);', database="postgres")


if __name__ == "__main__":
    raise SystemExit(main())
