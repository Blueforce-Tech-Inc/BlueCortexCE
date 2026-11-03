#!/usr/bin/env python3
"""Replay gate for retrieval quality.

The real evaluation needs the embedding provider and a database
(scripts/eval-retrieval-quality.py), so it cannot run on every verification.
This gate replays the recorded rankings instead: it reads the committed baseline
and asserts that the measured quality has not dropped below the floor that was
accepted in round 14.

A floor, not a target: the corpus showed hybrid ties pure vector because the
English tsvector half is empty for Chinese requests. If fusion is improved later,
raise the floors deliberately rather than letting a silent regression slide.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "backend/src/test/resources/eval/retrieval-quality-baseline.json"
FLOORS = {
    "hybrid": {"hit@1": 0.50, "hit@5": 0.75, "mrr": 0.63},
}
MIN_QUERIES = 40
MAX_VECTOR_GAP = 0.02


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
    for path, floors in FLOORS.items():
        measured = summary[path]
        for metric, floor in floors.items():
            if measured[metric] < floor:
                failures.append(
                    f"{path} {metric} {measured[metric]:.3f} below floor {floor:.3f}")
    hybrid = summary["hybrid"]["mrr"]
    vector = summary["vector"]["mrr"]
    if hybrid < vector - MAX_VECTOR_GAP:
        failures.append(
            f"hybrid MRR {hybrid:.3f} trails vector {vector:.3f} by more than "
            f"{MAX_VECTOR_GAP:.2f}")
    if failures:
        print("retrieval quality gate failed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print(
        f"retrieval quality: {queries} queries, hybrid hit@1 {summary['hybrid']['hit@1']:.3f} "
        f"hit@5 {summary['hybrid']['hit@5']:.3f} MRR {hybrid:.3f} "
        f"(vector {vector:.3f})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
