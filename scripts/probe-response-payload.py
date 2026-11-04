#!/usr/bin/env python3
"""Measure response payload size and latency for the hot read endpoints.

Manual harness, like ``read-load-mixed.py``: nothing here runs in CI. It starts
the packaged jar against a real-sized database copy and a local
OpenAI-compatible embedding stub (replaying real 1024-d vectors), then issues a
fixed, *sequential* request sequence so the numbers describe the payload rather
than client concurrency:

    GET /api/search?project=...&query=...&limit=20   (5 Chinese + 5 English)
    GET /api/observations?project=...&limit=20

For every response it records the raw body size, latency, and how much of the
body is spent on vector columns (``embedding_768``/``embedding_1024``/
``embedding_1536``). The same script is rerun before and after a change, which
is what makes the "before" evidence reproducible.

Usage:
    python3 scripts/probe-response-payload.py --db cortexce_load
    python3 scripts/probe-response-payload.py --db cortexce_load --label after

Writes ``docs/archive/<date>_response-payload-<label>.md`` plus a ``.json``
sidecar with the raw samples.
"""

from __future__ import annotations

import argparse
import asyncio
import importlib.util
import json
import statistics
import time
from pathlib import Path
from urllib.parse import urlencode

import aiohttp

ROOT = Path(__file__).resolve().parents[1]
VECTOR_FIELDS = ("embedding_768", "embedding_1024", "embedding_1536")
CHINESE_QUERIES = [
    "队列处理为什么在会话结束时丢事件",
    "混合检索的全文权重是多少",
    "观察写入失败的修复流程",
    "向量索引如何选择 ef_search",
    "连接池耗尽时错误是什么形状",
]
ENGLISH_QUERIES = [
    "queue handler drops events at session end",
    "what lexical weight does hybrid search use",
    "repair flow for failed observation writes",
    "how to choose ef_search for the vector index",
    "connection pool exhaustion error shape",
]


def load_harness():
    """Reuse service/stub/DB helpers from the concurrent-load harness."""
    spec = importlib.util.spec_from_file_location(
        "read_load_mixed", ROOT / "scripts" / "read-load-mixed.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


harness = load_harness()


def vector_column_bytes(payload) -> tuple[int, int]:
    """Return (bytes spent on vector arrays, number of items carrying them)."""
    items = payload.get("items") or payload.get("observations") or []
    total = 0
    carriers = 0
    for item in items:
        found = False
        for field in VECTOR_FIELDS:
            value = item.get(field)
            if value:
                total += len(json.dumps(value, separators=(",", ":")))
                found = True
        if found:
            carriers += 1
    return total, carriers


async def probe(session: aiohttp.ClientSession, base: str, project: str,
                limit: int, repeats: int) -> list[dict]:
    requests = []
    for index in range(repeats):
        query = CHINESE_QUERIES[index % len(CHINESE_QUERIES)]
        requests.append(("search_zh", "/api/search?" + urlencode(
            {"project": project, "query": query, "limit": limit})))
    for index in range(repeats):
        query = ENGLISH_QUERIES[index % len(ENGLISH_QUERIES)]
        requests.append(("search_en", "/api/search?" + urlencode(
            {"project": project, "query": query, "limit": limit})))
    for index in range(repeats):
        requests.append(("observations", "/api/observations?" + urlencode(
            {"project": project, "limit": limit, "offset": index * limit})))

    samples = []
    for kind, path in requests:
        started = time.perf_counter()
        async with session.get(base + path,
                               timeout=aiohttp.ClientTimeout(total=30)) as response:
            body = await response.read()
        latency = (time.perf_counter() - started) * 1000
        payload = json.loads(body)
        vector_bytes, carriers = vector_column_bytes(payload)
        items = payload.get("items") or payload.get("observations") or []
        samples.append({
            "kind": kind,
            "status": response.status,
            "bytes": len(body),
            "latency_ms": round(latency, 2),
            "items": len(items),
            "vector_bytes": vector_bytes,
            "vector_items": carriers,
        })
    return samples


def block(items: list[dict]) -> dict:
    latencies = [item["latency_ms"] for item in items]
    return {
        "requests": len(items),
        "mean_kb": round(statistics.fmean(item["bytes"] for item in items) / 1024, 1),
        "p50_ms": round(harness.percentile(latencies, 50), 2),
        "p95_ms": round(harness.percentile(latencies, 95), 2),
        "p99_ms": round(harness.percentile(latencies, 99), 2),
        "max_ms": round(max(latencies), 2) if latencies else 0.0,
        "mean_items": round(statistics.fmean(item["items"] for item in items), 1),
        "vector_share_pct": round(
            statistics.fmean(item["vector_bytes"] for item in items)
            / statistics.fmean(item["bytes"] for item in items) * 100, 1)
        if items else 0.0,
        "responses_with_vectors": sum(1 for item in items if item["vector_items"]),
    }


def render(facts: dict, summary: dict, config: dict, sql_facts: dict, label: str) -> str:
    lines = [
        f"# Response payload probe ({label})",
        "",
        "Manual harness (`scripts/probe-response-payload.py`). Packaged jar, real",
        f"PostgreSQL database `{facts['database']}`, local embedding stub replaying real",
        "1024-d vectors. Requests are issued sequentially so the numbers describe the",
        "payload and the endpoint, not client concurrency.",
        "",
        "## Environment",
        "",
        f"- Observations: {facts['observations']:,} "
        f"({facts['with_embedding_1024']:,} with 1024-d embeddings)",
        f"- Project: `{facts['project']}`",
        f"- Search page size: {config['limit']}, repeats per kind: {config['repeats']}",
        f"- Database-side vector text size: avg "
        f"{sql_facts.get('avg_vector_text_bytes', 0):,} B, max "
        f"{sql_facts.get('max_vector_text_bytes', 0):,} B per `embedding_1024::text`",
        "",
        "## Results",
        "",
        "| endpoint | requests | avg KB | p50 ms | p95 ms | p99 ms | max ms | avg items | vector share of body | responses with vectors |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for kind in ("search_zh", "search_en", "observations", "all"):
        item = summary[kind]
        lines.append(
            f"| {kind} | {item['requests']} | {item['mean_kb']:.1f} | "
            f"{item['p50_ms']:.2f} | {item['p95_ms']:.2f} | {item['p99_ms']:.2f} | "
            f"{item['max_ms']:.2f} | {item['mean_items']:.1f} | "
            f"{item['vector_share_pct']:.1f}% | {item['responses_with_vectors']}/{item['requests']} |")
    lines += [
        "",
        "`vector share of body` is measured by re-serialising every vector array found",
        "in the parsed response (compact separators) and dividing the average array",
        "bytes by the average raw body bytes.",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", default="cortexce_load")
    parser.add_argument("--label", default="before", help="before | after")
    parser.add_argument("--repeats", type=int, default=10, help="requests per kind")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--port", type=int, default=37892)
    parser.add_argument("--stub-port", type=int, default=8798)
    parser.add_argument("--report", default="")
    args = parser.parse_args()

    facts = harness.dataset_facts(args.db)
    if facts["observations"] < 100000:
        raise SystemExit(f"{args.db} has {facts['observations']} rows, need >= 100000")
    vectors = harness.sample_vectors(args.db)
    stub = harness.start_stub(vectors, args.stub_port)
    app = harness.start_app(args.db, args.port, args.stub_port, None)
    try:
        harness.wait_health(args.port, app)
        base = f"http://127.0.0.1:{args.port}"

        async def run() -> list[dict]:
            connector = aiohttp.TCPConnector(limit=0)
            async with aiohttp.ClientSession(connector=connector) as session:
                # Warm the JIT and the prepared statements; discard the samples.
                await probe(session, base, facts["project"], args.limit, 2)
                return await probe(session, base, facts["project"], args.limit,
                                   args.repeats)

        samples = asyncio.run(run())
    finally:
        app.terminate()
        try:
            app.wait(timeout=30)
        except Exception:
            app.kill()
        stub.shutdown()

    summary = {kind: block([item for item in samples if item["kind"] == kind])
               for kind in ("search_zh", "search_en", "observations")}
    summary["all"] = block(samples)
    sql_facts = {
        key: int(value) for key, value in zip(
            ("avg_vector_text_bytes", "max_vector_text_bytes"),
            harness.psql(
                "SELECT round(avg(length(embedding_1024::text))), "
                "max(length(embedding_1024::text)) FROM mem_observations "
                "WHERE embedding_1024 IS NOT NULL;", args.db).split("|"))
    }
    config = {"limit": args.limit, "repeats": args.repeats}

    report = ROOT / (args.report or f"docs/archive/{time.strftime('%Y-%m-%d')}"
                     f"_response-payload-{args.label}.md")
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(render(facts, summary, config, sql_facts, args.label))
    report.with_suffix(".json").write_text(json.dumps(
        {"dataset": facts, "config": config, "sql": sql_facts,
         "summary": summary, "samples": samples}, indent=2))
    print(json.dumps({kind: {key: value for key, value in item.items()
                             if key in ("mean_kb", "p50_ms", "p95_ms", "p99_ms",
                                        "vector_share_pct")}
                      for kind, item in summary.items()}, indent=2))
    print(f"report: {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
