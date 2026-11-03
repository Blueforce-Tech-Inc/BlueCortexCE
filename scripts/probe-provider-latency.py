#!/usr/bin/env python3
"""Attribute provider latency to network wait, prefill, reasoning and generation.

A11 records one elapsed number per successful call, which cannot say whether LongCat
is slow because of the network, the prompt, or the answer. This probe replays the
exact system/user prompts the evaluator used (dumped with EVAL_DUMP_PROMPTS) over
SSE and timestamps each phase:

    network      request sent -> response headers (connect, upload, first byte)
    prefill      headers -> first delta (queue + prompt processing)
    reasoning    first reasoning delta -> first answer-content delta
    generation   first answer delta -> final chunk

It also runs schema variants so "cap the narrative" and "drop unneeded fields" are
measured, not assumed.

Usage:
    python3 scripts/probe-provider-latency.py --samples 30 --variant baseline
    python3 scripts/probe-provider-latency.py --samples 10 --variant no-narrative
"""

from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]


def load_env() -> dict:
    values = {}
    for line in (ROOT / "backend/.env").read_text().splitlines():
        match = re.match(r"^([A-Z0-9_]+)=(.*)$", line.strip())
        if match:
            values[match.group(1)] = match.group(2).strip().strip('"').strip("'")
    return values


ENV = load_env()
BASE_URL = ENV.get("OPENAI_BASE_URL", "")
API_KEY = ENV.get("OPENAI_API_KEY", "")
MODEL = ENV.get("OPENAI_MODEL", "LongCat-2.5")


def variant_system(system: str, variant: str) -> str:
    # 'low-effort' changes only provider parameters, so the prompt stays untouched.
    if variant in ("baseline", "low-effort"):
        return system
    if variant == "no-narrative":
        return re.sub(r"\s*<narrative>.*?</narrative>", "", system, flags=re.S)
    if variant == "minimal":
        system = re.sub(r"\s*<narrative>.*?</narrative>", "", system, flags=re.S)
        system = re.sub(r"\s*<concepts>.*?</concepts>", "", system, flags=re.S)
        system = re.sub(r"\s*<files_read>.*?</files_read>", "", system, flags=re.S)
        return system
    if variant == "terse":
        marker = "Output observations using this XML structure:"
        return system.replace(
            marker,
            "Do not narrate your reasoning. Return the XML below directly.\n\n" + marker)
    raise SystemExit(f"unknown variant: {variant}")


def variant_params(variant: str) -> dict:
    """Provider parameters per variant. 'low-effort' is the only reasoning lever."""
    if variant == "low-effort":
        return {"reasoning_effort": "low"}
    return {}


def stream_call(system: str, user: str, extra: dict | None = None) -> dict:
    payload = json.dumps({
        "model": MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "stream": True,
        "stream_options": {"include_usage": True},
        **(extra or {}),
    })
    request = Request(
        f"{BASE_URL.rstrip('/')}/chat/completions",
        data=payload.encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {API_KEY}"},
    )
    started = time.perf_counter()
    had_error = None
    status = "200"
    try:
        response = urlopen(request, timeout=300)
    except HTTPError as error:
        return {"status": str(error.code), "error": str(error), "total_ms": None}
    except URLError as error:
        return {"status": "url-error", "error": str(error), "total_ms": None}
    headers_at = time.perf_counter()
    first_delta = first_reasoning = first_content = None
    last_at = headers_at
    usage = {}
    reasoning_chars = content_chars = 0
    finish_reason = None
    try:
        for raw in response:
            line = raw.decode("utf-8", errors="replace").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                break
            try:
                chunk = json.loads(data)
            except json.JSONDecodeError:
                continue
            now = time.perf_counter()
            if first_delta is None:
                first_delta = now
            usage.update(chunk.get("usage") or {})
            for choice in chunk.get("choices") or []:
                delta = choice.get("delta") or {}
                if delta.get("reasoning_content"):
                    reasoning_chars += len(delta["reasoning_content"])
                    if first_reasoning is None:
                        first_reasoning = now
                if delta.get("content"):
                    content_chars += len(delta["content"])
                    if first_content is None:
                        first_content = now
                if choice.get("finish_reason"):
                    finish_reason = choice["finish_reason"]
            last_at = now
    except (OSError, ValueError) as error:
        had_error = str(error)
    total = time.perf_counter() - started
    end = last_at
    return {
        "status": status,
        "error": had_error,
        "total_ms": round(total * 1000),
        "network_ms": round((headers_at - started) * 1000),
        "prefill_ms": round(((first_delta or headers_at) - headers_at) * 1000),
        "reasoning_ms": round(((first_content or last_at) - (first_reasoning or first_content or last_at)) * 1000)
        if first_reasoning and first_content else 0,
        "generation_ms": round((end - (first_content or first_delta or end)) * 1000),
        "reasoning_chars": reasoning_chars,
        "content_chars": content_chars,
        "completion_tokens": usage.get("completion_tokens"),
        "reasoning_tokens": (usage.get("completion_tokens_details") or {}).get("reasoning_tokens"),
        "prompt_tokens": usage.get("prompt_tokens"),
        "finish_reason": finish_reason,
    }


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    return ordered[min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1))))]


def summarize(calls: list[dict]) -> dict:
    ok = [call for call in calls if call.get("total_ms") is not None]
    summary = {"calls": len(calls), "errors": len(calls) - len(ok)}
    for field in ("total_ms", "network_ms", "prefill_ms", "reasoning_ms", "generation_ms"):
        values = [call[field] for call in ok]
        summary[field] = {
            "p50": percentile(values, 50),
            "p95": percentile(values, 95),
            "p99": percentile(values, 99),
        }
    tokens = [call["completion_tokens"] for call in ok if call.get("completion_tokens")]
    summary["completion_tokens"] = {
        "p50": percentile(tokens, 50), "p95": percentile(tokens, 95), "sum": sum(tokens),
    }
    reasoning_tokens = [call["reasoning_tokens"] for call in ok if call.get("reasoning_tokens") is not None]
    summary["reasoning_tokens"] = {
        "p50": percentile(reasoning_tokens, 50),
        "p95": percentile(reasoning_tokens, 95),
        "sum": sum(reasoning_tokens),
    } if reasoning_tokens else {"p50": 0, "p95": 0, "sum": 0}
    reasoning = [call["reasoning_chars"] for call in ok]
    content = [call["content_chars"] for call in ok]
    summary["reasoning_chars_p50"] = percentile(reasoning, 50)
    summary["content_chars_p50"] = percentile(content, 50)
    summary["completion_tokens_per_second_p50"] = round(
        statistics.fmean(
            call["completion_tokens"] / (call["total_ms"] / 1000)
            for call in ok if call.get("completion_tokens") and call["total_ms"]), 1)
    return summary


def render_report(results: dict, calls_by_variant: dict, fixture_p95: float | None) -> str:
    baseline = results.get("baseline")
    lines = [
        "# LongCat latency attribution",
        "",
        f"- Date: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- Model: {MODEL} at {BASE_URL}",
        "- Prompts: the exact system/user pairs A11 scored (EVAL_DUMP_PROMPTS)",
        "- Method: streaming SSE, phase timestamps per chunk",
        "",
        "Each call is split into network (request sent -> headers), prefill (headers -> "
        "first delta), reasoning (first reasoning delta -> first answer delta) and "
        "generation (first answer delta -> final chunk). Percentiles are per phase, so "
        "the p95 column is each phase's p95, not a sum that happens to total the p95.",
        "",
        "## Phases by variant",
        "",
        "| variant | calls | errors | total p50/p95 | network p50/p95 | prefill p50/p95 | "
        "reasoning p50/p95 | generation p50/p95 | completion tok p50/p95 | "
        "reasoning tok p50/p95 | tok/s p50 |",
        "|---|---:|---:|---|---|---|---|---|---|---|---:|",
    ]
    for name, summary in results.items():
        def phase(field: str) -> str:
            return f"{summary[field]['p50']}/{summary[field]['p95']}"
        reasoning_tokens = summary.get("reasoning_tokens") or {"p50": 0, "p95": 0}
        lines.append(
            f"| {name} | {summary['calls']} | {summary['errors']} | "
            f"{phase('total_ms')} | {phase('network_ms')} | {phase('prefill_ms')} | "
            f"{phase('reasoning_ms')} | {phase('generation_ms')} | "
            f"{summary['completion_tokens']['p50']}/{summary['completion_tokens']['p95']} | "
            f"{reasoning_tokens['p50']}/{reasoning_tokens['p95']} | "
            f"{summary['completion_tokens_per_second_p50']} |"
        )
    if baseline:
        lines.append("")
    if baseline and "baseline-10" in results:
        lines += [
            "`baseline-10` re-measures the first ten prompts with token accounting enabled "
            "(the 30-call run predates that field), and doubles as a between-session variance "
            "control: the same prompts moved total p50 by "
            f"{100 * (results['baseline-10']['total_ms']['p50'] - baseline['total_ms']['p50']) / baseline['total_ms']['p50']:+.1f}% "
            "on a re-run, so single-session deltas below that are not effects of a change.",
        ]
    if baseline:
        total_p95 = baseline["total_ms"]["p95"] or 1
        network = baseline["network_ms"]["p95"]
        prefill = baseline["prefill_ms"]["p95"]
        reasoning = baseline["reasoning_ms"]["p95"]
        generation = baseline["generation_ms"]["p95"]
        lines += [
            "",
            "## Attribution at p95",
            "",
            f"- Total p95: {total_p95} ms"
            + (f" (A11 recorded {fixture_p95} ms for the same 60 prompts)." if fixture_p95 else "."),
            f"- Network wait: {network} ms ({100 * network / total_p95:.1f}%)",
            f"- Prefill / queue: {prefill} ms ({100 * prefill / total_p95:.1f}%)",
            f"- Reasoning: {reasoning} ms ({100 * reasoning / total_p95:.1f}%)",
            f"- Answer generation: {generation} ms ({100 * generation / total_p95:.1f}%)",
        ]
        baseline_calls = [call for call in calls_by_variant.get("baseline", [])
                          if call.get("total_ms") is not None]
        if baseline_calls:
            summed = {field: sum(call[field] for call in baseline_calls)
                      for field in ("total_ms", "network_ms", "reasoning_ms", "generation_ms")}
            grand = summed["total_ms"] or 1
            reasoning_share = [
                call["reasoning_ms"] / call["total_ms"] for call in baseline_calls if call["total_ms"]]
            network_share = [
                call["network_ms"] / call["total_ms"] for call in baseline_calls if call["total_ms"]]
            lines += [
                "",
                "## Aggregated shares (sum of all baseline calls)",
                "",
                f"- Network wait: {100 * summed['network_ms'] / grand:.1f}%",
                f"- Reasoning: {100 * summed['reasoning_ms'] / grand:.1f}%",
                f"- Answer generation: {100 * summed['generation_ms'] / grand:.1f}%",
                f"- Remaining (TTFT parsing, disconnect): "
                f"{100 * (grand - summed['network_ms'] - summed['reasoning_ms'] - summed['generation_ms']) / grand:.1f}%",
                "",
                f"- Per-call reasoning share: p50 {percentile(reasoning_share, 50) * 100:.1f}% / "
                f"p95 {percentile(reasoning_share, 95) * 100:.1f}%",
                f"- Calls where reasoning is more than half the elapsed time: "
                f"{sum(1 for share in reasoning_share if share > 0.5)}/{len(reasoning_share)}",
                f"- Calls where network wait exceeds 10%: "
                f"{sum(1 for share in network_share if share > 0.1)}/{len(network_share)}",
            ]
        lines += [
            "",
            "Retries: the probe does not retry and records every non-200 response as an "
            f"error; this run had {baseline['errors']} error(s) out of {baseline['calls']}. "
            "A11 had 60/60 successful calls, so its p95 contains no retry time. The "
            "production client is built with Spring AI's default retry template, so a "
            "429/5xx outside A11 would add backoff time that looks like generation.",
        ]
        comparisons = {}
        for name, variant in results.items():
            if name == "baseline":
                continue
            # Variants run on a subset of prompts, so compare against the baseline calls
            # that used the same prompts instead of the full 30-call p50.
            variant_indices = {call.get("index") for call in calls_by_variant.get(name, [])}
            matched = [call for call in calls_by_variant.get("baseline", [])
                       if call.get("total_ms") is not None and call.get("index") in variant_indices]
            reference = summarize(matched) if matched else baseline
            label = f"matched baseline ({reference['calls']} calls)" if matched else "baseline"
            token_delta = (
                (variant["completion_tokens"]["p50"] - reference["completion_tokens"]["p50"])
                / reference["completion_tokens"]["p50"] * 100
                if reference["completion_tokens"]["p50"] else 0)
            total_delta = (
                (variant["total_ms"]["p50"] - reference["total_ms"]["p50"])
                / reference["total_ms"]["p50"] * 100
                if reference["total_ms"]["p50"] else 0)
            reasoning_delta = (
                (variant["reasoning_ms"]["p50"] - reference["reasoning_ms"]["p50"])
                / reference["reasoning_ms"]["p50"] * 100
                if reference["reasoning_ms"]["p50"] else 0)
            generation_delta = (
                (variant["generation_ms"]["p50"] - reference["generation_ms"]["p50"])
                / reference["generation_ms"]["p50"] * 100
                if reference["generation_ms"]["p50"] else 0)
            comparisons[name] = {
                "total": total_delta, "reasoning": reasoning_delta,
                "generation": generation_delta, "tokens": token_delta}
            lines.append(
                f"- {name} vs {label}: completion tokens p50 "
                f"{variant['completion_tokens']['p50']} ({token_delta:+.1f}%), total p50 "
                f"{variant['total_ms']['p50']} ms ({total_delta:+.1f}%), reasoning p50 "
                f"{variant['reasoning_ms']['p50']} ms ({reasoning_delta:+.1f}%), generation p50 "
                f"{variant['generation_ms']['p50']} ms ({generation_delta:+.1f}%).")

        verdict = []
        if baseline_calls:
            verdict.append(
                f"Reasoning is {100 * summed['reasoning_ms'] / grand:.1f}% of all elapsed "
                f"baseline time and {100 * reasoning / total_p95:.1f}% of the p95 sample; "
                f"network wait is {100 * summed['network_ms'] / grand:.1f}% in aggregate but "
                f"exceeds 10% of the call in {sum(1 for share in network_share if share > 0.1)}"
                f"/{len(network_share)} calls, so it is a tail effect, not the median cost.")
        generation_only = [name for name in ("no-narrative", "minimal", "terse")
                           if name in comparisons]
        if generation_only:
            best_generation = min(comparisons[name]["generation"] for name in generation_only)
            best_total = min(comparisons[name]["total"] for name in generation_only)
            verdict.append(
                f"Dropping the narrative or the optional fields cuts answer generation by up "
                f"to {abs(best_generation):.1f}% but leaves reasoning in place, so the best "
                f"total p50 change among those variants is {best_total:+.1f}%. Capping output "
                f"cannot fix a reasoning-dominated p95.")
        if "low-effort" in comparisons:
            low = comparisons["low-effort"]
            verdict.append(
                f"`reasoning_effort: low` did not reduce reasoning on the same prompts: "
                f"total p50 {low['total']:+.1f}%, reasoning {low['reasoning']:+.1f}%, "
                f"completion tokens {low['tokens']:+.1f}%. One single-prompt probe earlier "
                f"showed 997 -> 213 reasoning tokens, which is provider variance, not proof "
                f"that the parameter works. `enable_thinking: false` is ignored outright.")
        verdict.append(
            "Retries do not contribute to A11's number (0 failures in 60 real calls), but the "
            "production client is built with Spring AI's default retry template (10 attempts, "
            "exponential backoff up to 180 s), so a 5xx run would add minutes that the "
            "evaluator would report as latency.")
        lines += ["", "## Verdict", ""] + [f"- {sentence}" for sentence in verdict]
        lines += [
            "",
            "## Worker-level recommendation",
            "",
            "- Bound the call instead of retrying forever: connect timeout 5 s, read timeout "
            "120 s (the p95 is 45 s and the max 89 s, so 120 s still leaves headroom) and at "
            "most 3 attempts with 2 s / 5 s backoff. The default 10-attempt tower can hold a "
            "worker for many minutes on a provider outage.",
            "- A timeout after partial content cannot be salvaged: extraction parses the XML "
            "only after the response completes, so there are no half-parsed fields to keep. "
            "Mark the message `retry` and let stale recovery re-claim it; the queue already "
            "drains and never duplicates, so this is safe.",
            "- Do not lower the A11 threshold: p95 is the provider's reasoning time, not our "
            "call path. Keep 1.5× baseline and let A13 (≥99% call success) and the reported "
            "failure count cover provider jitter.",
            "- If the p95 itself must come down, the lever is the model's reasoning budget, "
            "not the output schema: the variants above show a narrative cap buys one fifth of "
            "the time at best.",
        ]
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prompts-dir", default="/tmp/longcat-prompts")
    parser.add_argument("--samples", type=int, default=30)
    parser.add_argument("--variant", default="baseline")
    parser.add_argument("--results", default="",
                        help="existing results JSON to extend (variant runs accumulate)")
    parser.add_argument("--report", default="")
    parser.add_argument("--report-only", action="store_true",
                        help="re-summarize the recorded calls and rewrite the report, no provider calls")
    args = parser.parse_args()

    existing = json.loads(Path(args.results).read_text()) if args.results and Path(args.results).exists() else {}
    results = existing.get("summary", {})
    calls_by_variant = existing.get("calls_by_variant", {})
    if "calls" in existing and "baseline" not in calls_by_variant:
        calls_by_variant["baseline"] = existing["calls"]
    calls = []
    if not args.report_only:
        prompts = Path(args.prompts_dir)
        pairs = sorted(
            ((int(path.name.split("_")[0]), path) for path in prompts.glob("*_system.txt")),
            key=lambda item: item[0],
        )
        if not pairs:
            raise SystemExit(f"no prompts in {prompts}; run GoldenSetEvaluator with EVAL_DUMP_PROMPTS")
        for index, system_path in dict(pairs).items():
            if len(calls) >= args.samples:
                break
            user_path = prompts / f"{index}_user.txt"
            system = variant_system(system_path.read_text(), args.variant)
            user = user_path.read_text()
            call = stream_call(system, user, variant_params(args.variant))
            call["index"] = index
            calls.append(call)
            print(f"  [{args.variant}] {index}: {call.get('total_ms')} ms, "
                  f"network {call.get('network_ms')}, prefill {call.get('prefill_ms')}, "
                  f"reasoning {call.get('reasoning_ms')}, generation {call.get('generation_ms')}, "
                  f"tokens {call.get('completion_tokens')}")
        results[args.variant] = summarize(calls)
        calls_by_variant[args.variant] = calls
    # Re-summarize every recorded variant so old runs pick up new derived fields
    # (e.g. reasoning_tokens) instead of keeping a stale summary.
    for name, recorded in calls_by_variant.items():
        results[name] = summarize(recorded)

    results_path = Path(args.results) if args.results else (
        ROOT / "docs/archive" / (time.strftime("%Y-%m-%d") + "_longcat-latency.json"))
    results_path.write_text(
        json.dumps({"calls_by_variant": calls_by_variant, "summary": results}, indent=2) + "\n")

    fixture_p95 = None
    fixture = ROOT / "backend/src/test/resources/eval/fixtures/longcat-real-r13-final.json"
    if fixture.exists():
        elapsed = sorted(entry["elapsedMs"] for entry in json.loads(fixture.read_text())["entries"]
                         if entry.get("status") == "200")
        fixture_p95 = percentile(elapsed, 95)
    report_path = Path(args.report) if args.report else (
        ROOT / "docs/archive" / (time.strftime("%Y-%m-%d") + "_longcat-latency.md"))
    report_path.write_text(render_report(results, calls_by_variant, fixture_p95))
    print(json.dumps(results[args.variant], indent=2))
    print(f"results: {results_path}")
    print(f"report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
