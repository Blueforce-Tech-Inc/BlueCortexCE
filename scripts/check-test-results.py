#!/usr/bin/env python3
"""Fail a build when the backend test report is not actually green.

Surefire writes one XML file per test class. "BUILD SUCCESS" on its own is not
enough: a suite can be green while most of it is skipped, or while a test
disappears entirely. This script makes those states fail.

Usage:
    python3 scripts/check-test-results.py                      # failures/errors/no tests
    python3 scripts/check-test-results.py --require-zero-skips # CI: skips are failures
    python3 scripts/check-test-results.py --min-tests 360      # guard against test loss
    python3 scripts/check-test-results.py --require-layers     # L1-L4 must all be present
    python3 scripts/check-test-results.py --require-baseline   # no layer may shrink

Layers are inferred from the test class name: classes under
`com.ablueforce.cortexce.layers.L2*` belong to L2, `L3*` to L3, `L4*` to L4,
and every other test is L1. `--require-layers` fails when a layer executed no
tests at all, so deleting a whole layer cannot look green.

`--require-baseline` compares each layer against `scripts/test-baseline.json`
and fails when a layer executed fewer tests than the recorded baseline. The
baseline is a ratchet, not a target: raise it with
`python3 scripts/check-test-results.py --write-baseline` after adding tests.
This catches "one test was deleted from each error path" changes that keep the
suite green and the total comfortably above `--min-tests`.

Exit code is 0 only when every check passes.
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

DEFAULT_REPORTS = os.path.join("backend", "target", "surefire-reports")
DEFAULT_BASELINE = os.path.join("scripts", "test-baseline.json")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--reports",
        default=DEFAULT_REPORTS,
        help=f"directory containing TEST-*.xml (default: {DEFAULT_REPORTS})",
    )
    parser.add_argument(
        "--require-zero-skips",
        action="store_true",
        help="treat any skipped test as a failure (use in CI where the DB env is set)",
    )
    parser.add_argument(
        "--min-tests",
        type=int,
        default=0,
        help="fail when fewer than this many tests were executed",
    )
    parser.add_argument(
        "--require-layers",
        action="store_true",
        help="fail unless L1, L2, L3 and L4 each executed at least one test",
    )
    parser.add_argument(
        "--baseline",
        default=DEFAULT_BASELINE,
        help=f"per-layer baseline file (default: {DEFAULT_BASELINE})",
    )
    parser.add_argument(
        "--require-baseline",
        action="store_true",
        help="fail when any layer (or the total) executed fewer tests than the baseline",
    )
    parser.add_argument(
        "--write-baseline",
        action="store_true",
        help="rewrite the baseline file from the current reports, then exit",
    )
    return parser.parse_args()


def layer_of(classname: str) -> str:
    """Map a test class name to the layer that owns it."""
    for layer in ("L2", "L3", "L4"):
        if f".layers.{layer}" in classname:
            return layer
    return "L1"


def main() -> int:
    args = parse_args()
    files = sorted(glob.glob(os.path.join(args.reports, "TEST-*.xml")))
    if not files:
        print(f"FAIL: no Surefire reports found in {args.reports}", file=sys.stderr)
        return 1

    tests = failures = errors = skipped = 0
    skipped_cases: list[str] = []
    layers: dict[str, dict[str, int]] = {
        name: {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        for name in ("L1", "L2", "L3", "L4")
    }
    for path in files:
        suite = ET.parse(path).getroot()
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        layer = layer_of(suite.get("name", ""))
        counter = layers[layer]
        counter["tests"] += int(suite.get("tests", 0))
        counter["failures"] += int(suite.get("failures", 0))
        counter["errors"] += int(suite.get("errors", 0))
        counter["skipped"] += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            if case.find("skipped") is not None:
                skipped_cases.append(f"{case.get('classname')}#{case.get('name')}")

    print(
        f"tests={tests} failures={failures} errors={errors} skipped={skipped} "
        f"(reports={len(files)})"
    )
    for name in ("L1", "L2", "L3", "L4"):
        counter = layers[name]
        print(
            f"  {name}: tests={counter['tests']} failures={counter['failures']} "
            f"errors={counter['errors']} skipped={counter['skipped']}"
        )

    problems: list[str] = []
    if failures or errors:
        problems.append(f"{failures} failure(s) and {errors} error(s)")
    if args.min_tests and tests < args.min_tests:
        problems.append(f"only {tests} tests ran, expected at least {args.min_tests}")
    if args.require_layers:
        missing = [name for name in ("L1", "L2", "L3", "L4")
                   if layers[name]["tests"] == 0]
        if missing:
            problems.append("layer(s) executed no tests: " + ", ".join(missing))

    executed = {name: layers[name]["tests"] - layers[name]["skipped"]
                for name in ("L1", "L2", "L3", "L4")}
    executed_total = tests - skipped

    if args.write_baseline:
        if problems:
            print("FAIL: refusing to record a baseline from a red run: "
                  + "; ".join(problems), file=sys.stderr)
            return 1
        payload = {"total": executed_total, "layers": executed}
        with open(args.baseline, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2)
            handle.write("\n")
        print(f"baseline written to {args.baseline}: total={executed_total} {executed}")
        return 0

    if args.require_baseline:
        if not os.path.exists(args.baseline):
            problems.append(f"baseline file not found: {args.baseline}")
        else:
            with open(args.baseline, encoding="utf-8") as handle:
                baseline = json.load(handle)
            baseline_total = int(baseline.get("total", 0))
            if executed_total < baseline_total:
                problems.append(
                    f"total executed tests dropped from {baseline_total} to {executed_total}")
            baseline_layers = baseline.get("layers", {})
            for name in ("L1", "L2", "L3", "L4"):
                expected = int(baseline_layers.get(name, 0))
                if executed[name] < expected:
                    problems.append(
                        f"{name} executed {executed[name]} tests, below baseline {expected}")

    if args.require_zero_skips and skipped:
        problems.append(
            f"{skipped} test(s) were skipped; CI must run them (check MEMSCOPE_* env vars)"
        )
        for name in skipped_cases:
            print(f"  skipped: {name}", file=sys.stderr)
    elif skipped:
        print("note: skipped tests are allowed here; pass --require-zero-skips in CI")

    if problems:
        print("FAIL: " + "; ".join(problems), file=sys.stderr)
        return 1

    print("PASS: test report satisfies the gate")
    return 0


if __name__ == "__main__":
    sys.exit(main())
