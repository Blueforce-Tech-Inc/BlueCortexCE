#!/usr/bin/env python3
"""Read-only data-integrity audit for the memory database.

Scans the rows the application assumes are consistent and reports every
violation with a sample, a severity, and an executable repair statement. The
default is dry-run: nothing is written, the SQL is printed and stored in the
JSON report. `--apply` runs the non-destructive repairs (embedding state,
stuck queue rows); `--apply-destructive` additionally deletes orphan/duplicate
rows and rewrites scope links and must be reviewed first.

Checks
  1 orphan_observations              conversation_id points at no mem_sessions row
  2 orphan_summaries                 conversation_id points at no mem_sessions row
  3 scope_project_mismatch           project-scope key differs from observation.project_path
  4 conversation_project_mismatch    observation.project_path differs from its conversation
  5 embedding_state_mismatch         state says ready without a vector, or a vector lacks state
  6 duplicate_hash_unsuppressed      same conversation + content_hash inside the dedupe window
  7 stuck_processing                 processing older than --stuck-minutes
  8 pending_backlog                  pending count or oldest age above the thresholds
  9 summary_snapshot_missing_obs     a summary input snapshot names an observation that is gone

Usage
  python3 scripts/data-integrity-audit.py --database cortexce
  python3 scripts/data-integrity-audit.py --database cortexce --json /tmp/audit.json
  python3 scripts/data-integrity-audit.py --database cortexce --apply

Exit codes: 0 clean, 1 findings, 2 could not inspect.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time

DEFAULT_CONTAINER = os.environ.get("AUDIT_PG_CONTAINER", "claude-mem-test-pg")
DEFAULT_USER = os.environ.get("AUDIT_PG_USER", "postgres")
VECTOR_PRESENT = ("(embedding_768 IS NOT NULL OR embedding_1024 IS NOT NULL "
                  "OR embedding_1536 IS NOT NULL)")


class AuditError(RuntimeError):
    pass


def psql(container: str, user: str, database: str, sql: str) -> list[list[str]]:
    result = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", user, "-d", database,
         "-v", "ON_ERROR_STOP=1", "-t", "-A", "-F", "\t", "-P", "pager=off"],
        input=sql, text=True, capture_output=True, timeout=180,
    )
    if result.returncode != 0:
        raise AuditError(result.stderr.strip()[:800] or "psql failed")
    return [line.split("\t") for line in result.stdout.strip().splitlines() if line]


def scalar(container: str, user: str, database: str, sql: str) -> str:
    rows = psql(container, user, database, sql)
    return rows[0][0] if rows else ""


def quote(text: str) -> str:
    return "'" + str(text).replace("'", "''") + "'"


def check(number: int, key: str, title: str, severity: str, count: int,
          sample: list[str], repair_sql: list[str], repair_kind: str,
          detail: str = "") -> dict:
    return {
        "check": number,
        "key": key,
        "title": title,
        "severity": severity if count else "ok",
        "count": count,
        "sample": sample,
        "detail": detail,
        "repair_sql": repair_sql,
        "repair_kind": repair_kind,
    }


def run_checks(container: str, user: str, database: str, args) -> list[dict]:
    checks: list[dict] = []

    # 1/2. Parent session missing. The composite foreign keys normally prevent
    # this, which is exactly why it is worth reporting: a row that slipped past
    # the constraints (restore without constraints, manual surgery) is invisible
    # to every join the Viewer performs.
    orphan_obs = int(scalar(container, user, database, """
        SELECT count(*) FROM mem_observations o
        LEFT JOIN mem_sessions s ON s.id = o.conversation_id
        WHERE o.conversation_id IS NOT NULL AND s.id IS NULL;"""))
    orphan_obs_sample = psql(container, user, database, """
        SELECT o.id || ' conversation=' || o.conversation_id
        FROM mem_observations o
        LEFT JOIN mem_sessions s ON s.id = o.conversation_id
        WHERE o.conversation_id IS NOT NULL AND s.id IS NULL
        ORDER BY o.created_at_epoch DESC LIMIT 10;""")
    checks.append(check(1, "orphan_observations", "Orphan observations", "critical",
                        orphan_obs, [row[0] for row in orphan_obs_sample],
                        ["""DELETE FROM mem_observations o
WHERE o.conversation_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.id = o.conversation_id);"""],
                        "destructive",
                        "conversation_id has no parent session"))

    orphan_sum = int(scalar(container, user, database, """
        SELECT count(*) FROM mem_summaries m
        LEFT JOIN mem_sessions s ON s.id = m.conversation_id
        WHERE s.id IS NULL;"""))
    orphan_sum_sample = psql(container, user, database, """
        SELECT m.id || ' conversation=' || m.conversation_id
        FROM mem_summaries m
        LEFT JOIN mem_sessions s ON s.id = m.conversation_id
        WHERE s.id IS NULL LIMIT 10;""")
    checks.append(check(2, "orphan_summaries", "Orphan summaries", "critical",
                        orphan_sum, [row[0] for row in orphan_sum_sample],
                        ["""DELETE FROM mem_summaries m
WHERE NOT EXISTS (SELECT 1 FROM mem_sessions s WHERE s.id = m.conversation_id);"""],
                        "destructive", "conversation_id has no parent session"))

    # 3. A project-scoped row whose scope key is another project would be
    # returned by that project's searches while the Viewer lists it under this
    # one. The scope link is what the retrieval SQL filters on.
    scope_mismatch = int(scalar(container, user, database, f"""
        SELECT count(*) FROM mem_observations o
        JOIN mem_memory_scopes ms ON ms.id = o.memory_scope_id
        WHERE ms.scope_type = 'project'
          AND o.project_path IS NOT NULL
          AND ms.scope_key IS DISTINCT FROM o.project_path;"""))
    scope_sample = psql(container, user, database, """
        SELECT o.id || ' scope=' || ms.scope_key || ' project=' || coalesce(o.project_path,'<null>')
        FROM mem_observations o
        JOIN mem_memory_scopes ms ON ms.id = o.memory_scope_id
        WHERE ms.scope_type = 'project'
          AND o.project_path IS NOT NULL
          AND ms.scope_key IS DISTINCT FROM o.project_path
        LIMIT 10;""")
    checks.append(check(3, "scope_project_mismatch", "Scope key and project disagree", "critical",
                        scope_mismatch, [row[0] for row in scope_sample],
                        ["""UPDATE mem_observations o
SET memory_scope_id = ms.id
FROM mem_memory_scopes ms
WHERE ms.scope_type = 'project'
  AND ms.scope_key = o.project_path
  AND o.project_path IS NOT NULL
  AND o.memory_scope_id IS DISTINCT FROM ms.id;"""],
                        "destructive",
                        "memory_scope_id resolves to a different project than project_path"))

    # 4. The conversation and the observation also carry a project path. A row
    # written under one project but attached to another conversation leaks
    # across the Viewer's session views.
    conversation_mismatch = int(scalar(container, user, database, """
        SELECT count(*) FROM mem_observations o
        JOIN mem_sessions s ON s.id = o.conversation_id
        WHERE o.project_path IS NOT NULL AND s.project_path IS NOT NULL
          AND o.project_path IS DISTINCT FROM s.project_path;"""))
    conversation_sample = psql(container, user, database, """
        SELECT o.id || ' observation=' || o.project_path || ' conversation=' || s.project_path
        FROM mem_observations o
        JOIN mem_sessions s ON s.id = o.conversation_id
        WHERE o.project_path IS NOT NULL AND s.project_path IS NOT NULL
          AND o.project_path IS DISTINCT FROM s.project_path LIMIT 10;""")
    checks.append(check(4, "conversation_project_mismatch",
                        "Observation and conversation projects disagree", "warning",
                        conversation_mismatch, [row[0] for row in conversation_sample],
                        [""], "review",
                        "join by conversation_id to decide which project is correct"))

    # 5. embedding_state is the only signal the backfill sweep uses. A ready
    # row without a vector is invisible to semantic search forever; a vector
    # behind a failed/skipped state is never ranked and never retried.
    ready_without = int(scalar(container, user, database, f"""
        SELECT count(*) FROM mem_observations
        WHERE embedding_state = 'ready' AND NOT {VECTOR_PRESENT};"""))
    vector_not_ready = int(scalar(container, user, database, f"""
        SELECT count(*) FROM mem_observations
        WHERE {VECTOR_PRESENT} AND embedding_state IS DISTINCT FROM 'ready';"""))
    embed_sample = psql(container, user, database, f"""
        SELECT id || ' state=' || coalesce(embedding_state,'<null>') ||
               ' vector=' || ({VECTOR_PRESENT})::text
        FROM mem_observations
        WHERE (embedding_state = 'ready' AND NOT {VECTOR_PRESENT})
           OR ({VECTOR_PRESENT} AND embedding_state IS DISTINCT FROM 'ready')
        LIMIT 10;""")
    checks.append(check(5, "embedding_state_mismatch", "Embedding state and vector disagree",
                        "warning", ready_without + vector_not_ready,
                        [row[0] for row in embed_sample],
                        [f"""UPDATE mem_observations SET embedding_state = 'failed',
     embedding_error = 'missing_vector'
WHERE embedding_state = 'ready' AND NOT {VECTOR_PRESENT};""",
                         f"""UPDATE mem_observations SET embedding_state = 'ready',
     embedding_error = NULL
WHERE {VECTOR_PRESENT} AND embedding_state IS DISTINCT FROM 'ready';"""],
                        "safe",
                        f"ready_without_vector={ready_without}, vector_not_ready={vector_not_ready}"))

    # 6. Deduplication is a 30-second window per conversation (content_hash).
    # Two identical hashes inside that window mean one write escaped the
    # check; outside it the duplicate is an intentional later write.
    window = max(1, args.dedupe_window_seconds)
    duplicate_groups = int(scalar(container, user, database, f"""
        SELECT count(*) FROM (
            SELECT 1 FROM mem_observations
            WHERE content_hash IS NOT NULL AND conversation_id IS NOT NULL
            GROUP BY conversation_id, content_hash
            HAVING count(*) > 1
               AND max(created_at_epoch) - min(created_at_epoch) <= {window * 1000}
        ) g;"""))
    duplicate_extras = int(scalar(container, user, database, f"""
        SELECT coalesce(sum(n - 1), 0) FROM (
            SELECT count(*) AS n FROM mem_observations
            WHERE content_hash IS NOT NULL AND conversation_id IS NOT NULL
            GROUP BY conversation_id, content_hash
            HAVING count(*) > 1
               AND max(created_at_epoch) - min(created_at_epoch) <= {window * 1000}
        ) g;"""))
    duplicate_sample = psql(container, user, database, f"""
        SELECT conversation_id || ' hash=' || content_hash || ' rows=' || count(*)
        FROM mem_observations
        WHERE content_hash IS NOT NULL AND conversation_id IS NOT NULL
        GROUP BY conversation_id, content_hash
        HAVING count(*) > 1
           AND max(created_at_epoch) - min(created_at_epoch) <= {window * 1000}
        LIMIT 10;""")
    checks.append(check(6, "duplicate_hash_unsuppressed",
                        "Duplicate content hash inside the dedupe window", "warning",
                        duplicate_extras, [row[0] for row in duplicate_sample],
                        [f"""DELETE FROM mem_observations d USING (
    SELECT conversation_id, content_hash,
           (array_agg(id ORDER BY created_at_epoch, id))[1] AS keep_id
    FROM mem_observations
    WHERE content_hash IS NOT NULL AND conversation_id IS NOT NULL
    GROUP BY conversation_id, content_hash
    HAVING count(*) > 1
       AND max(created_at_epoch) - min(created_at_epoch) <= {window * 1000}
) g
WHERE d.conversation_id = g.conversation_id
  AND d.content_hash = g.content_hash
  AND d.id <> g.keep_id;"""],
                        "destructive",
                        f"{duplicate_groups} groups, {duplicate_extras} extra rows, window {window}s"))

    # 7. A processing row whose worker died stays processing forever and its
    # content never reaches memory. The threshold counts from the later of
    # created_at and started_processing_at.
    stuck_ms = max(1, args.stuck_minutes) * 60_000
    cutoff = int(time.time() * 1000) - stuck_ms
    stuck = int(scalar(container, user, database, f"""
        SELECT count(*) FROM mem_pending_messages
        WHERE status = 'processing'
          AND coalesce(started_processing_at_epoch, created_at_epoch) < {cutoff};"""))
    stuck_sample = psql(container, user, database, f"""
        SELECT id || ' session=' || session_db_id || ' started=' ||
               coalesce(started_processing_at_epoch, created_at_epoch)
        FROM mem_pending_messages
        WHERE status = 'processing'
          AND coalesce(started_processing_at_epoch, created_at_epoch) < {cutoff}
        ORDER BY created_at_epoch LIMIT 10;""")
    checks.append(check(7, "stuck_processing", "Queue rows stuck in processing", "critical",
                        stuck, [row[0] for row in stuck_sample],
                        [f"""UPDATE mem_pending_messages
SET status = 'pending', started_processing_at_epoch = NULL
WHERE status = 'processing'
  AND coalesce(started_processing_at_epoch, created_at_epoch) < {cutoff};"""],
                        "safe",
                        f"older than {args.stuck_minutes} minutes; restart the worker first so it cannot hold the same row"))

    # 8. Backlog: a growing pending queue or an old head means the worker or
    # the provider is not draining. This is the leading indicator before rows
    # become stuck.
    pending = int(scalar(container, user, database,
                         "SELECT count(*) FROM mem_pending_messages WHERE status = 'pending';"))
    oldest_rows = psql(container, user, database, """
        SELECT coalesce(min(created_at_epoch), 0) FROM mem_pending_messages
        WHERE status = 'pending';""")
    oldest = int(oldest_rows[0][0]) if oldest_rows else 0
    oldest_age = int((int(time.time() * 1000) - oldest) / 60_000) if oldest else 0
    over_count = pending > args.backlog_max
    over_age = bool(oldest) and oldest_age > args.backlog_age_minutes
    checks.append(check(8, "pending_backlog", "Pending queue backlog",
                        "critical" if over_age else "warning",
                        pending if (over_count or over_age) else 0,
                        [f"pending={pending} oldest_age_minutes={oldest_age}"] if (over_count or over_age) else [],
                        [], "none",
                        f"thresholds count>{args.backlog_max} or age>{args.backlog_age_minutes}m; "
                        f"inspect worker logs and provider health"))

    # 9. A summary input snapshot is the frozen list of observations a summary
    # job was built from. If retention removed one, the stored summary and its
    # provenance disagree; regenerate it instead of trusting the snapshot.
    snapshot_missing = int(scalar(container, user, database, """
        SELECT count(*) FROM mem_pending_messages p
        CROSS JOIN LATERAL jsonb_array_elements_text(
            p.summary_input_snapshot -> 'observation_ids') AS ids(observation_id)
        LEFT JOIN mem_observations o ON o.id::text = ids.observation_id
        WHERE p.summary_input_snapshot -> 'observation_ids' IS NOT NULL
          AND ids.observation_id ~ '^[0-9a-fA-F-]{36}$'
          AND o.id IS NULL;"""))
    snapshot_sample = psql(container, user, database, """
        SELECT p.id || ' missing=' || ids.observation_id
        FROM mem_pending_messages p
        CROSS JOIN LATERAL jsonb_array_elements_text(
            p.summary_input_snapshot -> 'observation_ids') AS ids(observation_id)
        LEFT JOIN mem_observations o ON o.id::text = ids.observation_id
        WHERE p.summary_input_snapshot -> 'observation_ids' IS NOT NULL
          AND ids.observation_id ~ '^[0-9a-fA-F-]{36}$'
          AND o.id IS NULL
        LIMIT 10;""")
    checks.append(check(9, "summary_snapshot_missing_obs",
                        "Summary snapshot names a missing observation", "warning",
                        snapshot_missing, [row[0] for row in snapshot_sample],
                        [], "review",
                        "regenerate the summary from current observations; deletion is not the fix"))

    return checks


def apply_repairs(container: str, user: str, database: str, checks: list[dict],
                  include_destructive: bool) -> list[str]:
    applied: list[str] = []
    for item in checks:
        if not item["count"] or not item["repair_sql"]:
            continue
        if item["repair_kind"] == "none":
            continue
        if item["repair_kind"] == "destructive" and not include_destructive:
            continue
        for statement in item["repair_sql"]:
            psql(container, user, database, statement)
        applied.append(item["key"])
    return applied


def table_counts(container: str, user: str, database: str) -> dict:
    counts = {}
    for table in ("mem_sessions", "mem_observations", "mem_summaries",
                  "mem_user_prompts", "mem_pending_messages", "mem_memory_scopes"):
        counts[table] = int(scalar(container, user, database, f"SELECT count(*) FROM {table};"))
    return counts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--database", required=True)
    parser.add_argument("--container", default=DEFAULT_CONTAINER)
    parser.add_argument("--user", default=DEFAULT_USER)
    parser.add_argument("--json", help="write the JSON report here ('-' for stdout)")
    parser.add_argument("--apply", action="store_true",
                        help="run safe repairs (embedding state, stuck rows); default dry-run")
    parser.add_argument("--apply-destructive", action="store_true",
                        help="also delete orphans/duplicates and rewrite scope links")
    parser.add_argument("--stuck-minutes", type=int, default=15)
    parser.add_argument("--backlog-max", type=int, default=100)
    parser.add_argument("--backlog-age-minutes", type=int, default=60)
    parser.add_argument("--dedupe-window-seconds", type=int, default=30)
    args = parser.parse_args()

    started_ms = int(time.time() * 1000)
    try:
        counts = table_counts(args.container, args.user, args.database)
        checks = run_checks(args.container, args.user, args.database, args)
    except AuditError as error:
        print(f"cannot inspect {args.database}: {error}", file=sys.stderr)
        return 2

    findings = [item for item in checks if item["count"]]
    applied: list[str] = []
    if args.apply or args.apply_destructive:
        try:
            applied = apply_repairs(args.container, args.user, args.database, checks,
                                    args.apply_destructive)
        except AuditError as error:
            print(f"repair failed: {error}", file=sys.stderr)
            return 2

    report = {
        "tool": "data-integrity-audit",
        "version": 1,
        "database": args.database,
        "container": args.container,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "duration_ms": int(time.time() * 1000) - started_ms,
        "dry_run": not (args.apply or args.apply_destructive),
        "thresholds": {
            "stuck_minutes": args.stuck_minutes,
            "backlog_max": args.backlog_max,
            "backlog_age_minutes": args.backlog_age_minutes,
            "dedupe_window_seconds": args.dedupe_window_seconds,
        },
        "table_counts": counts,
        "checks": checks,
        "findings": len(findings),
        "findings_by_severity": {
            severity: sum(1 for item in findings if item["severity"] == severity)
            for severity in ("critical", "warning")
        },
        "applied_repairs": applied,
        "verdict": "clean" if not findings else "findings",
    }

    print(f"database={args.database} tables={counts}")
    for item in checks:
        marker = "FAIL" if item["count"] else "ok"
        print(f"  [{marker:4}] {item['check']}. {item['key']}: {item['count']}"
              + (f" ({item['detail']})" if item["detail"] else ""))
        if item["count"]:
            for statement in item["repair_sql"]:
                print("         repair: " + statement.replace("\n", " ").strip())
    if applied:
        print(f"applied repairs: {', '.join(applied)}")
    else:
        print("dry-run: no repair executed")

    if args.json:
        payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
        if args.json == "-":
            print(payload)
        else:
            with open(args.json, "w", encoding="utf-8") as handle:
                handle.write(payload)
            print(f"report: {args.json}")

    return 0 if not findings else 1


if __name__ == "__main__":
    raise SystemExit(main())
