# Test stability: three repeated rounds — 2026-11-05

## Question

A single green `mvn test` does not distinguish a test that always passes from one
that passes because it happened to run after the right fixture. This round
measured that directly: run the whole backend suite three times, each round
against its own database, with Surefire's random execution order, and compare
per-test outcomes.

## Method

`scripts/repeat-tests.sh` (new) packages the backend once, boots it against a
seed database so Flyway applies the full schema, then for each round:

1. `CREATE DATABASE <prefix>_r<N>_flyway_rehearsal TEMPLATE <seed>` — a fresh
   database, not a truncated shared one;
2. runs `mvn -B test -Dsurefire.runOrder=random`
   `-Dsurefire.runOrder.random.seed=<1000 + round*7919>`;
3. copies the Surefire XML into a per-round directory and compares the
   `(class, test)` outcome across rounds.

The comparison is deliberately unforgiving: a test that passes in one round and
fails in another is reported as **flaky**; a test present in one round but absent
in another is reported as **missing**; a test failing in every round is
**failed-all**. Flaky or missing exits 1, so this can be used as a pre-push
check, not just a report.

## Result

```
round 1: mvn exit=0
round 2: mvn exit=0
round 3: mvn exit=0

tests seen          : 601
stable pass         : 601
flaky               : 0
missing in some     : 0
failed every round  : 0
```

| Metric | Before this round | After |
|--------|-------------------|-------|
| Independent executions | 1 run (the gate) | **1,803** (3 × 601) |
| Divergent outcomes | not measured | **0** |
| Flaky rate | unknown / single-sample | **0 / 1,803 = 0%** |
| Order-dependence evidence | none | 3 random orders, no difference |
| Missing-in-some-rounds | not measured | 0 |

The suite at the time of the run was 601 tests (L1 565, L2 29, L3 6, L4 1):
the 595 gate floor plus `ArchitectureGuardTest` (4) and
`ErrorCodeRegistryTest` (2). No test was changed for this measurement — the
point was to observe the suite as shipped, not to make it look stable.

## What the zero means

Zero divergence over three random orders is evidence, not proof. It rules out
the common failure modes that a 1M-scale batch exposed: class-order dependence,
leftovers from a previous test, and random seeds that only fail for some inputs.
Three rounds cannot catch a rare clock boundary or a race that needs a loaded
host. The script exists so a suspicious change can be checked with more rounds
(`ROUNDS=10 ./scripts/repeat-tests.sh`) without inventing a new process.

## Reproduction

```bash
PG_CONTAINER=cortexce-flaky-pg PG_PORT=15433 ROUNDS=3 ./scripts/repeat-tests.sh
```

Raw per-round logs and `stability.json` were produced under
`/tmp/cortexce-repeat`; the JSON is archived beside this report.
