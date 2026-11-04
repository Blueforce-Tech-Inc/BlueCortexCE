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

## Second version: examples kept, prose compressed

The first version lost because it removed the examples. The second version kept
every rule, the 3 GOOD / 3 BAD examples, all 10 category examples and the
`<candidate_identifiers>` bullet verbatim, and compressed only repetitive
explanations, duplicated prohibitions and redundant wording: 2531 → 1805 words
(-28.7%). It was evaluated in full (113 calls, 0 failures, 1829 s), not aborted.

| metric | before | v1 (examples removed) | v2 (examples kept) |
|---|---|---|---|
| tokens p50 | 5013 | 3289 | **4055 (-19.1%)** |
| tokens p95 | 6094 | 4695 | **5077 (-16.7%)** |
| title exact | 52% | 36% | 55% |
| title normalized | 55% | 40% | 60% |
| A6a title action | 92% | 91% | **87%** |
| A6b trace-visible subject | 93% | 79% | **89%** |
| A6c title identifiers | 95% | 88% | 97% |
| A6 title contract | 83% | 67% | **76%** |
| facts precision / recall | 77% / 93% | 79% / 95% | 76% / 90% |
| concepts precision / recall | 84% / 83% | 85% / 81% | 83% / 79% |
| actionable rate | 92% | 79% | 84% |
| type / auditable type | 88% / 85% | 90% / 88% | 91% / 89% |
| skip accuracy (TP/FP/FN/TN) | 99% (19/0/1/67) | 100% (20/0/0/67) | 100% (20/0/0/67) |
| latency p50 / p95 (ms) | 11367 / 38218 | 13857 / 40439 | 12412 / 34666 |

Restoring the examples recovered exact and normalized title hits above the
baseline (52→55, 55→60) but did not recover the contract dimensions: A6 fell
83→76, action class 92→87, trace-visible subject 93→89, and actionable
92→84. Tokens stopped short of the bar: -19.1% p50 and -16.7% p95 against the
required -20%.

Verdict for v2: **rejected** on both counts — it missed the token bar and lost
quality. This replaces the earlier aborted attempt, which produced no data.

## Decision and rollback

Rejected per the pre-committed rule. Neither version met both bars: v1 hit
tokens (-34%) but lost title quality, v2 kept the examples yet missed the token
bar (-19.1%) and still lost quality.

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
