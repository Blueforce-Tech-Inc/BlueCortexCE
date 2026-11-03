#!/usr/bin/env python3
"""Reject a downward edit of a gate value.

The real-history fixtures are scored offline, and their floors live in
``scripts/eval-gates.json``. A low measurement is a reason to improve the extractor,
not to lower the gate: ``scripts/eval-gates-floor.json`` records the highest value
ever adopted. This check fails when the current floor is below that high-water mark.

The same rule applies to ``scripts/test-baseline.json``, whose per-layer counts are a
ratchet over the suite: ``scripts/test-baseline-floor.json`` records the highest count
ever adopted, so removing tests cannot be hidden by editing the baseline down.

``--ratchet`` raises the high-water marks to the current values. It never lowers a
value; a lower current value is still rejected.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CURRENT = ROOT / "eval-gates.json"
FLOOR = ROOT / "eval-gates-floor.json"
TEST_CURRENT = ROOT / "test-baseline.json"
TEST_FLOOR = ROOT / "test-baseline-floor.json"


def load(path: Path) -> dict:
    return json.loads(path.read_text())


def test_baseline_lowered(current: dict, floor: dict) -> list[tuple[str, int, int]]:
    """Every count that fell, across the total and each recorded layer."""
    lowered = []
    if current.get("total", 0) < floor.get("total", 0):
        lowered.append(("total", floor["total"], current["total"]))
    for layer, was in floor.get("layers", {}).items():
        now = current.get("layers", {}).get(layer, 0)
        if now < was:
            lowered.append((layer, was, now))
    return lowered


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ratchet", action="store_true",
                        help="raise the recorded floor to the current values (never lowers)")
    args = parser.parse_args()

    current = load(CURRENT).get("real", {})
    floor = load(FLOOR).get("real", {})
    lowered = [(key, floor.get(key), current.get(key)) for key in current
               if current.get(key) is not None and floor.get(key) is not None
               and current[key] < floor[key]]
    if lowered:
        for key, was, now in lowered:
            print(f"FAIL: gate {key} fell from {was} to {now}; gates may only rise")
        return 1

    test_current = load(TEST_CURRENT)
    test_floor = load(TEST_FLOOR)
    for name, was, now in test_baseline_lowered(test_current, test_floor):
        print(f"FAIL: test baseline {name} fell from {was} to {now}; "
              "the baseline may only rise")
        return 1

    if args.ratchet:
        raised = {key: max(current[key], floor.get(key, 0)) for key in current}
        data = load(FLOOR)
        data["real"] = raised
        FLOOR.write_text(json.dumps(data, indent=2) + "\n")
        test_raised = {
            "total": max(test_current.get("total", 0), test_floor.get("total", 0)),
            "layers": {
                layer: max(test_current.get("layers", {}).get(layer, 0),
                           test_floor.get("layers", {}).get(layer, 0))
                for layer in set(test_current.get("layers", {}))
                | set(test_floor.get("layers", {}))
            },
        }
        test_data = load(TEST_FLOOR)
        test_data.update(test_raised)
        TEST_FLOOR.write_text(json.dumps(test_data, indent=2) + "\n")
        print(f"ratcheted floors to {raised}")
        print(f"ratcheted test baseline to {test_raised}")
    else:
        print(f"gate floors stable: {current}")
        print(f"test baseline stable: {test_current.get('total')} tests "
              f"{test_current.get('layers')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
