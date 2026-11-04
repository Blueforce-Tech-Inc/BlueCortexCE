# Round 20: system-prompt slimming — rejected

- Date: 2026-11-04
- Question: the extraction prompt is ~4k input tokens and dominates per-call
  latency. Can it be compressed without losing output quality?
- Pre-committed rule: ship only if **quality does not drop and tokens drop
  >= 20%**; otherwise archive the result and reject.
- Verdict: **REJECTED**. Tokens dropped 34%, but title quality regressed well
  beyond noise. The production prompts are unchanged.

## Method

`GoldenSetEvaluator` against the real provider (`LongCat-2.5`) with the 87-sample
golden set (13 key samples × 3 repeats = 113 real calls per run, temperature 0,
production `XmlParser`, `EVAL_VALIDATE=1`). Identical provider, samples, seeds and
scoring for both runs; only the prompt directory differed:

| run | prompt source | init/continuation words |
|---|---|---|
| before | `backend/src/test/resources/eval/prompts-prompt-slim-baseline/` (frozen copy) | 2531 / 2131 |
| after | classpath `prompts/` (compressed) | 1386 / 1385 |

Compression kept every rule (record/skip conditions, verb list, title/facts/
concepts contracts, output format, mechanical signals) but merged repeated
sentences, dropped the 3 GOOD / 3 BAD narrative examples, the 10 category
examples, the SPATIAL AWARENESS section, the XML bracket comments, and the
closing boilerplate.

Reports: `2026-11-04_prompt-slim-before.md`, `2026-11-04_prompt-slim-after.md`.

## Results

### Tokens (gate metric — PASS)

| metric | before | after | change |
|---|---|---|---|
| tokens p50 | 5013 | **3289** | -34% |
| tokens p95 | 6094 | **4695** | -23% |
| tokens mean | 5010 | 3412 | -32% |

### Quality (gate metric — FAIL)

| metric | before | after |
|---|---|---|
| contract pass rate | 98% | 100% |
| skip accuracy (TP/FP/FN/TN) | 99% (19/0/1/67) | 100% (20/0/0/67) |
| format errors | 0 | 0 |
| call failures | 1/113 | 0/113 |
| type accuracy (raw / auditable) | 88% / 85% | 90% / 88% |
| facts precision / recall | 77% / 93% | 79% / 95% |
| concepts precision / recall | 84% / 83% | 85% / 81% |
| **title exact-hit** | **52%** | **36%** |
| **title normalized-hit** | **55%** | **40%** |
| **title trace contract (A6)** | **83%** | **67%** |
| **title trace-visible subject (A6b)** | **93%** | **79%** |
| **title identifiers (A6c)** | **95%** | **88%** |
| title action class (A6a) | 92% | 91% |

Latency did not improve: p50 11367 → 13857 ms, p95 38218 → 40439 ms (provider
variance, still inside the A11 1.5× window). The fewer input tokens bought no
measurable wall-clock gain at these sample sizes.

### Gate verdicts

| id | threshold | before | after |
|---|---|---|---|
| A1 format errors = 0 | pass | pass | pass |
| A2 skip accuracy >= 95% | pass | 99% | 100% |
| A5 facts precision >= 75% | pass | 77% | 79% |
| A6a action >= 80% | pass | 92% | 91% |
| A6b subject >= 90% | **fail** | 93% (pass) | 79% (fail) |
| A6c identifiers = 100% | **fail** | 95% | 88% |
| A11/A12/A13 | pass | pass | pass |

The gate fails on title subject and identifier coverage in both runs; the
compressed prompt makes both worse. `A6` was already failing before this
experiment, so the relevant comparison is the direction of change, and it is
negative: exact-title hits fell by 16 points and trace-contract hits by 16 points
while subject and identifier coverage both dropped.

## Second attempt (aborted, no data)

A follow-up variant that restored the `<candidate_identifiers>` pointer plus the
category examples was prepared (1505 words, still ~40% below baseline) and the
evaluation was started. It was stopped at 32/113 calls when this round switched
to small-batch execution, so **no report exists and no data from it is used
here**. If the title section is revisited later, that variant is the starting
point and needs its own full run.

## Decision and rollback

Rejected per the pre-committed rule: tokens met the bar, quality did not.

- `backend/src/main/resources/prompts/init.txt` and `continuation.txt` are
  restored to the pre-experiment content (2531 / 2131 words); `git status` shows
  no modification to them.
- The frozen `prompts-prompt-slim-baseline` snapshot and both evaluation reports
  are kept as the evidence for this verdict, matching the existing
  `prompts-baseline` convention.

What would actually help the title gate is a separate task: the verb list and the
identifier rule are already present in the uncompressed prompt, and A6 still
fails there. Further slimming is not the lever; the title contract needs a
targeted fix with its own golden-set run.
