#!/usr/bin/env python3
"""Replay gate for retrieval quality.

The real evaluation needs the embedding provider and a database
(scripts/eval-retrieval-quality.py), so it cannot run on every verification.
This gate replays the recorded rankings instead: it reads the committed baseline
and asserts that the measured quality has not dropped below the floor accepted
in round 15.

The floors only move up. Round 14 accepted hybrid hit@1 0.50 / hit@5 0.75 /
MRR 0.63 because the English tsvector half was empty for every Chinese query
(60/60). V24 fixed the lexical half and raised the measured numbers; these
floors encode that gain, not the old tie. Round 16 added 30 en/mixed queries
(90 total) and shipped a 7-day recency half-life on the fused score, so the
replayed baseline now gates the decayed ranking too: hybrid hit@1 0.75 and
recency-weighted hit@1 0.80, with at least 30 en/mixed queries in the corpus.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "backend/src/test/resources/eval/retrieval-quality-baseline.json"
FLOORS = {
    "hybrid": {"hit@1": 0.75, "hit@5": 0.87, "mrr": 0.79},
    "hybrid_decay_7d": {"hit@1": 0.80, "hit@5": 0.88, "mrr": 0.83},
    "lexical": {"hit@1": 0.72, "hit@5": 0.82, "mrr": 0.75},
}
MIN_QUERIES = 80
# Round 16 requirement: the contrast set must exist, otherwise a later corpus
# rebuild could quietly drop back to Chinese-only queries.
MIN_EN_OR_MIXED_QUERIES = 30
# The requirement of round 15: the fused path must beat pure vector by >= 0.05.
# MRR carries the margin (measured +0.098); hit@5 is gated by its own absolute floor
# because one query swing is 0.017 and a +0.05 relative gate would be flaky.
MIN_HYBRID_GAIN = 0.05
MAX_LEXICAL_EMPTY = 2


def main() -> int:
    if not BASELINE.exists():
        print(f"missing baseline: {BASELINE}", file=sys.stderr)
        return 1
    data = json.loads(BASELINE.read_text())
    summary = data["summary"]
    queries = summary["hybrid"]["queries"]
    failures = []
    if queries < MIN_QUERIES:
        failures.append(f"corpus has {queries} queries, need >= {MIN_QUERIES}")
    languages = summary.get("languages") or data["corpus"].get("languages", {})
    contrast = languages.get("en", 0) + languages.get("mixed", 0)
    if contrast < MIN_EN_OR_MIXED_QUERIES:
        failures.append(
            f"corpus has {contrast} en/mixed queries, need >= {MIN_EN_OR_MIXED_QUERIES}")
    for path, floors in FLOORS.items():
        measured = summary[path]
        for metric, floor in floors.items():
            if measured[metric] < floor:
                failures.append(
                    f"{path} {metric} {measured[metric]:.3f} below floor {floor:.3f}")
    hybrid = summary["hybrid"]["mrr"]
    vector = summary["vector"]["mrr"]
    if hybrid < vector + MIN_HYBRID_GAIN:
        failures.append(
            f"hybrid MRR {hybrid:.3f} does not beat vector {vector:.3f} by "
            f"{MIN_HYBRID_GAIN:.2f}")
    lexical_empty = summary["lexical"]["empty_results"]
    if lexical_empty > MAX_LEXICAL_EMPTY:
        failures.append(
            f"lexical path is empty for {lexical_empty} queries (max {MAX_LEXICAL_EMPTY}); "
            f"the Chinese full-text regression is back")
    if failures:
        print("retrieval quality gate failed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print(
        f"retrieval quality: {queries} queries ({contrast} en/mixed), hybrid hit@1 "
        f"{summary['hybrid']['hit@1']:.3f} hit@5 {summary['hybrid']['hit@5']:.3f} MRR {hybrid:.3f} "
        f"(recency 7 d hit@1 {summary['hybrid_decay_7d']['hit@1']:.3f}, vector {vector:.3f}, "
        f"lexical hit@5 {summary['lexical']['hit@5']:.3f}, empty {lexical_empty})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
