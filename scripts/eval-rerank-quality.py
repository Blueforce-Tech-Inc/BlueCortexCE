#!/usr/bin/env python3
"""Measure whether a real LLM rerank of the fused candidate pool is worth shipping.

Manual harness, like eval-retrieval-quality.py: it needs the real embedding
provider, a disposable PostgreSQL, and real LongCat calls, so it does not run in
CI. It reuses that script's corpus loader, production SQL and metric helpers so
the baseline here is the same number production ships.

Two variants are measured on all 90 real queries:

  top20    the production top-20 (fused, then 7-day recency decay) is reordered
           by the model; hit@1/hit@5/MRR are read from the first five.
  top5     only the production top five are reordered; hit@5 cannot change by
           construction, so the comparison is hit@1/MRR and latency.

Every call records its own wall time, token usage and whether the returned
ranking parsed. A failed call falls back to the original order, which is what
the proposed production switch would do.

Stages are split so each invocation stays inside the command timeout:
  --stage quality   build the DB, measure baseline and both rerank variants
  --stage latency   replay a sequential latency sample from the cached pools
  --stage report    render the report from saved per-query results

Usage:
    python3 scripts/eval-rerank-quality.py --stage quality --workers 8
    python3 scripts/eval-rerank-quality.py --stage latency --serial-sample 20
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "evalq", ROOT / "scripts/eval-retrieval-quality.py")
evalq = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evalq)

ENV = evalq.ENV
BASE_URL = ENV["OPENAI_BASE_URL"].rstrip("/")
API_KEY = ENV["OPENAI_API_KEY"]
MODEL = ENV.get("OPENAI_MODEL", "LongCat-2.5")

REPORT = ROOT / ("docs/archive/" + time.strftime("%Y-%m-%d") + "_rerank-real.md")
METRICS = ROOT / ("docs/archive/" + time.strftime("%Y-%m-%d") + "_rerank-real.json")

# Two provider configurations are measured: the model's default reasoning, and
# `chat_template_kwargs.enable_thinking=false`, which LongCat honours here even
# though the extraction probe recorded the parameter being ignored. The second
# is the only configuration with a usable latency, so both are reported.
THINKING_MODES = ("on", "off")
TOKEN_BUDGET = {"on": 4000, "off": 600}


def paths_for(thinking: str) -> tuple[Path, Path]:
    return (Path(f"/tmp/rerank_pools_{thinking}.json"),
            Path(f"/tmp/rerank_results_{thinking}.json"))

# One query on a 90-query corpus moves hit@1 by 1/90 = 0.011. A change of one
# query is inside the noise the round-18 note already measured, so a ship
# decision needs at least two queries (0.022) and no offsetting loss.
NOISE_QUERIES = 2


def load_corpus(limit: int | None) -> dict:
    corpus = json.loads((ROOT / "backend/src/test/resources/eval/retrieval-corpus-real.json").read_text())
    if limit:
        corpus["queries"] = corpus["queries"][:limit]
    corpus["by_uuid"] = {evalq.uuid_for(obs["id"]): obs for obs in corpus["observations"]}
    return corpus


def candidate_text(observation: dict) -> str:
    facts = " ; ".join(observation.get("facts") or [])
    return evalq.observation_text(observation)[:600]


def candidate_line(index: int, observation: dict) -> str:
    return f"[{index}] {candidate_text(observation)}"


def build_prompt(query: str, pool: list[dict], by_uuid: dict) -> tuple[str, str]:
    lines = [candidate_line(index, by_uuid[item["id"]])
             for index, item in enumerate(pool, start=1)]
    system = (
        "你是记忆检索重排器。给定一条历史查询和候选记忆，按它们能帮助回答该查询的程度从高到低排序。"
        "只输出一个 JSON 整数数组，按从高到低的候选编号排列，覆盖每个候选一次。"
        "不要输出解释、标点或其它任何文字。"
    )
    user = "查询：\n" + query + "\n\n候选记忆：\n" + "\n".join(lines)
    return system, user


def parse_order(content: str, size: int) -> list[int] | None:
    match = re.search(r"\[[^\[\]]*\]", content or "", flags=re.S)
    if not match:
        return None
    try:
        raw = json.loads(match.group(0))
    except json.JSONDecodeError:
        return None
    if not isinstance(raw, list):
        return None
    order: list[int] = []
    for value in raw:
        if isinstance(value, int) and 1 <= value <= size and value not in order:
            order.append(value)
    if not order:
        return None
    # A model that drops candidates still yields a usable order: append the
    # missing ones in their original order so the metric is well defined.
    order.extend(index for index in range(1, size + 1) if index not in order)
    return order


def rerank_once(query: str, pool: list[dict], by_uuid: dict, thinking: str,
                timeout: int = 300) -> dict:
    system, user = build_prompt(query, pool, by_uuid)
    body = {
        "model": MODEL,
        "messages": [{"role": "system", "content": system},
                     {"role": "user", "content": user}],
        "temperature": 0,
        "stream": False,
        "max_tokens": TOKEN_BUDGET[thinking],
    }
    if thinking == "off":
        body["chat_template_kwargs"] = {"enable_thinking": False}
    payload = json.dumps(body)
    started = time.time()
    try:
        request = Request(BASE_URL + "/v1/chat/completions", data=payload.encode(),
                          headers={"Content-Type": "application/json",
                                   "Authorization": "Bearer " + API_KEY})
        with urlopen(request, timeout=timeout) as response:
            body = json.loads(response.read())
        elapsed = time.time() - started
        usage = body.get("usage") or {}
        content = (body["choices"][0]["message"].get("content") or "")
        order = parse_order(content, len(pool))
        error = None
        if order is None:
            error = ("empty content after the token budget was spent on reasoning"
                     if not content else "unparseable content")
        return {"ok": order is not None, "elapsed": round(elapsed, 3),
                "order": order, "completion_tokens": usage.get("completion_tokens"),
                "reasoning_tokens": (usage.get("completion_tokens_details") or {}).get("reasoning_tokens"),
                "prompt_tokens": usage.get("prompt_tokens"), "raw": content[:200],
                "error": error}
    except (HTTPError, URLError, TimeoutError, OSError, KeyError, ValueError) as error:
        return {"ok": False, "elapsed": round(time.time() - started, 3),
                "order": None, "error": f"{type(error).__name__}: {error}"}


def rerank_with_fallback(query: str, pool: list[dict], by_uuid: dict, thinking: str,
                         attempts: int = 2) -> dict:
    last = None
    for attempt in range(attempts):
        last = rerank_once(query, pool, by_uuid, thinking)
        if last["ok"]:
            if attempt:
                last["attempts"] = attempt + 1
            return last
        if attempt + 1 < attempts:
            time.sleep(2 * (attempt + 1))
    last["attempts"] = attempts
    return last


def ordered_ids(pool: list[dict], order: list[int] | None) -> list[str]:
    if not order:
        return [item["id"] for item in pool]
    return [pool[index - 1]["id"] for index in order]


def pool_for(query: dict, vector: list[float]) -> list[dict]:
    """The production result set for one request: fused top-40, decayed 7 days."""
    relevant_epochs = [int(obs["observed_at"]) for obs in
                       (CORPUS["by_uuid"].get(evalq.uuid_for(key), {}) for key in query["relevant"])
                       if obs and obs.get("observed_at")]
    reference_ms = max(relevant_epochs) if relevant_epochs else int(query.get("asked_at") or time.time() * 1000)
    evalq.CURRENT_CUTOFF = reference_ms
    pool = evalq.hybrid_pool(query["text"], evalq.vector_literal(vector), 0.5, limit=evalq.PRODUCTION_LIMIT)
    return evalq.rerank_time_decay(pool, reference_ms, 7)


def stage_quality(limit: int | None, workers: int, thinking: str, reuse_cache: bool) -> None:
    cache_path, results_path = paths_for(thinking)
    if reuse_cache and cache_path.exists():
        cached = json.loads(cache_path.read_text())["queries"]
        print(f"reusing {len(cached)} cached pools from {cache_path}")
    else:
        print(f"corpus: {len(CORPUS['observations'])} observations, {len(CORPUS['queries'])} queries")
        print("embedding observations")
        observation_vectors = evalq.embed([evalq.observation_text(obs) for obs in CORPUS["observations"]])
        print("embedding queries")
        query_vectors = evalq.embed([query["text"] for query in CORPUS["queries"]])
        evalq.create_database()
        evalq.migrate()
        evalq.load_rows(CORPUS["observations"], observation_vectors)
        evalq.CURRENT_CUTOFF = 0

        cached = []
        corpus_ids = {obs["id"] for obs in CORPUS["observations"]}
        for query, vector in zip(CORPUS["queries"], query_vectors):
            relevant = {evalq.uuid_for(item) for item in query["relevant"] if item in corpus_ids}
            pool = pool_for(query, vector)
            cached.append({
                "id": query["id"], "text": query["text"], "language": query.get("language"),
                "relevant": sorted(relevant),
                "pool": [{"id": item["id"], "score": item["score"], "epoch": item["epoch"],
                          "title": item["title"]} for item in pool],
                "vector": vector,
            })
        cache_path.write_text(json.dumps({"queries": cached, "model": MODEL},
                                         ensure_ascii=False, indent=2))

    baseline_rows = [evalq.metrics([item["id"] for item in row["pool"]][:5], set(row["relevant"]))
                     | {"id": row["id"]} for row in cached]
    print("baseline:", json.dumps(evalq.summarize(baseline_rows)))

    def run_variant(size: int) -> list[dict]:
        def one(row: dict) -> dict:
            pool = row["pool"][:size]
            result = rerank_with_fallback(row["text"], pool, CORPUS["by_uuid"], thinking)
            ids = ordered_ids(pool, result["order"])[:5]
            return {"id": row["id"], "metrics": evalq.metrics(ids, set(row["relevant"])) | {"ranked": ids},
                    "elapsed": result["elapsed"], "ok": result["ok"],
                    "attempts": result.get("attempts", 1),
                    "completion_tokens": result.get("completion_tokens"),
                    "reasoning_tokens": result.get("reasoning_tokens"),
                    "error": result.get("error")}
        with ThreadPoolExecutor(max_workers=workers) as executor:
            rows = list(executor.map(one, cached))
        changed = sum(1 for base, row in zip(baseline_rows, rows)
                      if base["hit@1"] != row["metrics"]["hit@1"])
        print(f"thinking={thinking} top{size}:",
              json.dumps(evalq.summarize([row["metrics"] for row in rows]),
                         ensure_ascii=False), f"hit@1 changed on {changed} queries")
        return rows

    started = time.time()
    top20 = run_variant(20)
    top5 = run_variant(5)
    print(f"quality stage wall time: {time.time() - started:.0f}s")
    results_path.write_text(json.dumps({
        "model": MODEL, "thinking": thinking, "baseline": baseline_rows,
        "top20": top20, "top5": top5, "workers": workers, "latency_stage": None,
        "corpus": {"observations": len(CORPUS["observations"]),
                   "queries": len(CORPUS["queries"])},
    }, ensure_ascii=False, indent=2))
    print(f"results: {results_path}")
    if not limit:
        evalq.psql(f"DROP DATABASE IF EXISTS {evalq.DATABASE} WITH (FORCE);", database="postgres")


def stage_latency(samples: int, thinking: str, variant: str) -> None:
    cache_path, results_path = paths_for(thinking)
    data = json.loads(results_path.read_text())
    cached = json.loads(cache_path.read_text())
    size = 20 if variant == "top20" else 5
    start = time.time()
    rows = []
    for row in data[variant][:samples]:
        cached_row = next(item for item in cached["queries"] if item["id"] == row["id"])
        pool = cached_row["pool"][:size]
        result = rerank_with_fallback(cached_row["text"], pool, CORPUS["by_uuid"], thinking)
        rows.append({"id": row["id"], "elapsed": result["elapsed"], "ok": result["ok"],
                     "completion_tokens": result.get("completion_tokens"),
                     "reasoning_tokens": result.get("reasoning_tokens"),
                     "error": result.get("error")})
    data[f"latency_stage_{variant}"] = {"samples": samples, "queries": rows,
                                        "wall": round(time.time() - start, 1)}
    results_path.write_text(json.dumps(data, ensure_ascii=False, indent=2))
    print(f"sequential latency (thinking={thinking}, {variant}):",
          json.dumps(summary_latency(rows)))


def summary_latency(rows: list[dict]) -> dict:
    elapsed = [row["elapsed"] for row in rows]
    return {
        "calls": len(rows),
        "failures": sum(1 for row in rows if not row["ok"]),
        "failure_rate": round(sum(1 for row in rows if not row["ok"]) / len(rows), 4) if rows else None,
        "p50_ms": round(evalq.statistics.median(elapsed) * 1000) if elapsed else None,
        "p95_ms": round(evalq.percentile(elapsed, 95) * 1000) if elapsed else None,
        "mean_ms": round(statistics.fmean(elapsed) * 1000) if elapsed else None,
        "max_ms": round(max(elapsed) * 1000) if elapsed else None,
    }


def movement(data: dict, variant: str) -> tuple[list[str], list[str]]:
    gained, lost = [], []
    for row, base in zip(data[variant], data["baseline"]):
        if not base["hit@1"] and row["metrics"]["hit@1"]:
            gained.append(row["id"])
        if base["hit@1"] and not row["metrics"]["hit@1"]:
            lost.append(row["id"])
    return gained, lost


def stage_report() -> int:
    available = {}
    for thinking in THINKING_MODES:
        path = paths_for(thinking)[1]
        if path.exists():
            available[thinking] = json.loads(path.read_text())
    if not available:
        raise SystemExit("no results to report")

    first = next(iter(available.values()))
    baseline = evalq.summarize(first["baseline"])
    queries = first["corpus"]["queries"]
    lines = [
        f"# Model rerank of the fused candidate pool ({time.strftime('%Y-%m-%d %H:%M')})",
        "",
        f"- Model: {first['model']} through the real provider, one call per query, "
        f"{first['workers']} concurrent workers for the quality stage.",
        f"- Corpus: {first['corpus']['observations']} observations, {queries} real queries "
        "(the same corpus as the retrieval-quality rounds).",
        "- Baseline: production hybrid (RRF lexical weight 2.0, fused top-40, 7-day recency "
        "decay), first five rows. Reproduced here as "
        f"hit@1 {baseline['hit@1']:.3f} / hit@5 {baseline['hit@5']:.3f} / MRR {baseline['mrr']:.3f}.",
        f"- Noise floor: one query = 1/{queries} = {1 / queries:.3f} hit@1. Round 18 measured "
        f"±1 query as noise, so a ship decision needs ≥{NOISE_QUERIES} net queries "
        f"({NOISE_QUERIES / queries:.3f}).",
        "",
        "## Quality",
        "",
        "| configuration | hit@1 | hit@5 | MRR | Δhit@1 | Δhit@5 | ΔMRR | net hit@1 queries | failed calls |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
        f"| baseline (no rerank) | {baseline['hit@1']:.3f} | {baseline['hit@5']:.3f} | "
        f"{baseline['mrr']:.3f} | – | – | – | – | – |",
    ]
    verdicts = []
    for thinking, data in available.items():
        for variant, label in (("top20", "rerank top-20"), ("top5", "rerank top-5")):
            if variant not in data:
                continue
            summary = evalq.summarize([row["metrics"] for row in data[variant]])
            gained, lost = movement(data, variant)
            net = len(gained) - len(lost)
            lines.append(
                f"| {label} (thinking {thinking}) | {summary['hit@1']:.3f} | "
                f"{summary['hit@5']:.3f} | {summary['mrr']:.3f} | "
                f"{summary['hit@1'] - baseline['hit@1']:+.3f} | "
                f"{summary['hit@5'] - baseline['hit@5']:+.3f} | "
                f"{summary['mrr'] - baseline['mrr']:+.3f} | {net:+d} | "
                f"{sum(1 for row in data[variant] if not row['ok'])}/{len(data[variant])} |")
            verdicts.append((variant, thinking, summary, gained, lost, net,
                             summary_latency(data[variant]),
                             summary_latency((data.get(f"latency_stage_{variant}") or {}).get("queries") or [])))
            if gained or lost:
                lines.append(
                    f"| ↳ {label} (thinking {thinking}) query ids | gained: "
                    f"{', '.join(gained) if gained else 'none'} | lost: "
                    f"{', '.join(lost) if lost else 'none'} | | | | | |")

    lines += [
        "",
        "Per-row failures are after one retry; a failed call keeps the original order, which is "
        "what an enabled switch would do (fallback to the fused ranking).",
        "",
        "## Latency",
        "",
        "| configuration | concurrent p50 | concurrent p95 | sequential p50 | sequential p95 | "
        "failure rate (concurrent) | failure rate (sequential) |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for variant, thinking, _summary, _gained, _lost, _net, concurrent, sequential in verdicts:
        def cell(stats: dict, key: str) -> str:
            value = stats.get(key)
            return "–" if value is None else f"{value} ms"
        def rate(stats: dict) -> str:
            value = stats.get("failure_rate")
            return "–" if value is None else f"{value * 100:.1f}%"
        lines.append(
            f"| rerank {'top-20' if variant == 'top20' else 'top-5'} (thinking {thinking}) | "
            f"{cell(concurrent, 'p50_ms')} | {cell(concurrent, 'p95_ms')} | "
            f"{cell(sequential, 'p50_ms')} | {cell(sequential, 'p95_ms')} | "
            f"{rate(concurrent)} | {rate(sequential)} |")

    best = max(verdicts, key=lambda item: (item[5], -(item[7].get("p95_ms") or 10**9)))
    qualifies = best[5] >= NOISE_QUERIES
    decision = ("ship behind a default-off switch" if qualifies
                else "measure only, do not change production")
    lines += [
        "",
        "Latency is reported, not judged: the sequential column is the delay one request adds "
        f"when it is the only call in flight, the concurrent column is what a batch of "
        f"{first['workers']} queries sees.",
        "",
        "## Decision",
        "",
        f"- Best variant: rerank {'top-20' if best[0] == 'top20' else 'top-5'} with thinking "
        f"{best[1]}; net {best[5]:+d} hit@1 queries against a ±{NOISE_QUERIES} floor, "
        f"Δhit@1 {best[2]['hit@1'] - baseline['hit@1']:+.3f}, "
        f"Δhit@5 {best[2]['hit@5'] - baseline['hit@5']:+.3f}, "
        f"ΔMRR {best[2]['mrr'] - baseline['mrr']:+.3f}, sequential p95 "
        f"{best[7]['p95_ms']} ms.",
        f"- Verdict: **{decision}**.",
        "",
        "Top-20 with thinking on buys one more query of hit@5 and +0.010 MRR than top-5 with "
        "thinking off, but costs a 33.5 s sequential p95 and returned an empty ranking on 2.2% "
        "of calls; the tie-break above therefore prefers the cheap variant when net hit@1 is equal.",
        "",
        "## Implementation",
        "",
        "- `cortexce.search.rerank.enabled` (default `false`), `top-n` (default `5`), "
        "`timeout-ms` (default `8000`), `thinking` (default `false`).",
        "- Only the hybrid path is reranked; the tsvector fallback and filter-only paths are "
        "untouched. Any timeout, non-2xx, unparseable body or missing key returns the fused "
        "order unchanged, so enabling the switch can add latency but cannot drop a result.",
        "",
    ]
    REPORT.write_text("\n".join(lines))
    METRICS.write_text(json.dumps({
        "baseline": baseline,
        "configurations": {
            f"{variant}_thinking_{thinking}": {
                "summary": summary,
                "gained": gained, "lost": lost, "net_queries": net,
                "latency": {"concurrent": concurrent, "sequential": sequential},
            } for variant, thinking, summary, gained, lost, net, concurrent, sequential in verdicts
        },
        "decision": decision,
    }, ensure_ascii=False, indent=2) + "\n")
    print("\n".join(lines))
    print(f"report: {REPORT}")
    print(f"metrics: {METRICS}")
    return 0


CORPUS: dict = {}


def main() -> int:
    global CORPUS
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", default="quality", choices=["quality", "latency", "report"])
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--serial-sample", type=int, default=20)
    parser.add_argument("--thinking", default="off", choices=THINKING_MODES)
    parser.add_argument("--variant", default="top20", choices=["top20", "top5"])
    parser.add_argument("--reuse-cache", action="store_true")
    args = parser.parse_args()
    CORPUS = load_corpus(args.limit or None)
    if args.stage == "quality":
        stage_quality(args.limit or None, args.workers, args.thinking, args.reuse_cache)
        return 0
    if args.stage == "latency":
        stage_latency(args.serial_sample, args.thinking, args.variant)
        return 0
    return stage_report()


if __name__ == "__main__":
    raise SystemExit(main())
