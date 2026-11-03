# LongCat latency attribution

- Date: 2026-11-04 03:31:21
- Model: LongCat-2.5 at https://api.longcat.chat/openai
- Prompts: the exact system/user pairs A11 scored (EVAL_DUMP_PROMPTS)
- Method: streaming SSE, phase timestamps per chunk

Each call is split into network (request sent -> headers), prefill (headers -> first delta), reasoning (first reasoning delta -> first answer delta) and generation (first answer delta -> final chunk). Percentiles are per phase, so the p95 column is each phase's p95, not a sum that happens to total the p95.

## Phases by variant

| variant | calls | errors | total p50/p95 | network p50/p95 | prefill p50/p95 | reasoning p50/p95 | generation p50/p95 | completion tok p50/p95 | reasoning tok p50/p95 | tok/s p50 |
|---|---:|---:|---|---|---|---|---|---|---|---:|
| baseline | 30 | 0 | 19884/45178 | 1657/1999 | 0/2 | 11316/43309 | 4730/9001 | 1075/2648 | 0/0 | 61.4 |
| no-narrative | 10 | 0 | 15755/29480 | 1791/5456 | 0/0 | 9788/19969 | 4591/7720 | 1180/2130 | 0/0 | 65.7 |
| minimal | 10 | 0 | 18292/31780 | 1665/1795 | 0/0 | 13702/25539 | 3141/5644 | 1220/2023 | 0/0 | 67.9 |
| terse | 10 | 0 | 16877/142666 | 1670/1804 | 0/0 | 7238/24677 | 4241/7296 | 1155/1886 | 0/0 | 64.6 |
| low-effort | 10 | 0 | 27392/41871 | 1438/4438 | 0/0 | 21173/34196 | 2798/23847 | 1456/3563 | 901/3099 | 58.7 |
| baseline-10 | 10 | 0 | 22982/39239 | 1416/2242 | 0/0 | 14154/29259 | 5177/10646 | 1253/2173 | 718/1597 | 62.5 |

`baseline-10` re-measures the first ten prompts with token accounting enabled (the 30-call run predates that field), and doubles as a between-session variance control: the same prompts moved total p50 by +15.6% on a re-run, so single-session deltas below that are not effects of a change.

## Attribution at p95

- Total p95: 45178 ms (A11 recorded 38925 ms for the same 60 prompts).
- Network wait: 1999 ms (4.4%)
- Prefill / queue: 2 ms (0.0%)
- Reasoning: 43309 ms (95.9%)
- Answer generation: 9001 ms (19.9%)

## Aggregated shares (sum of all baseline calls)

- Network wait: 7.2%
- Reasoning: 69.8%
- Answer generation: 23.0%
- Remaining (TTFT parsing, disconnect): 0.0%

- Per-call reasoning share: p50 59.2% / p95 95.4%
- Calls where reasoning is more than half the elapsed time: 21/30
- Calls where network wait exceeds 10%: 13/30

Retries: the probe does not retry and records every non-200 response as an error; this run had 0 error(s) out of 30. A11 had 60/60 successful calls, so its p95 contains no retry time. The production client is built with Spring AI's default retry template, so a 429/5xx outside A11 would add backoff time that looks like generation.
- no-narrative vs matched baseline (10 calls): completion tokens p50 1180 (+22.9%), total p50 15755 ms (-20.8%), reasoning p50 9788 ms (-25.5%), generation p50 4591 ms (-34.4%).
- minimal vs matched baseline (10 calls): completion tokens p50 1220 (+27.1%), total p50 18292 ms (-8.0%), reasoning p50 13702 ms (+4.3%), generation p50 3141 ms (-55.1%).
- terse vs matched baseline (10 calls): completion tokens p50 1155 (+20.3%), total p50 16877 ms (-15.1%), reasoning p50 7238 ms (-44.9%), generation p50 4241 ms (-39.4%).
- low-effort vs matched baseline (10 calls): completion tokens p50 1456 (+51.7%), total p50 27392 ms (+37.8%), reasoning p50 21173 ms (+61.1%), generation p50 2798 ms (-60.0%).
- baseline-10 vs matched baseline (10 calls): completion tokens p50 1253 (+30.5%), total p50 22982 ms (+15.6%), reasoning p50 14154 ms (+7.7%), generation p50 5177 ms (-26.0%).

## Verdict

- Reasoning is 69.8% of all elapsed baseline time and 95.9% of the p95 sample; network wait is 7.2% in aggregate but exceeds 10% of the call in 13/30 calls, so it is a tail effect, not the median cost.
- Dropping the narrative or the optional fields cuts answer generation by up to 55.1% but leaves reasoning in place, so the best total p50 change among those variants is -20.8%. Capping output cannot fix a reasoning-dominated p95.
- `reasoning_effort: low` did not reduce reasoning on the same prompts: total p50 +37.8%, reasoning +61.1%, completion tokens +51.7%. One single-prompt probe earlier showed 997 -> 213 reasoning tokens, which is provider variance, not proof that the parameter works. `enable_thinking: false` is ignored outright.
- Retries do not contribute to A11's number (0 failures in 60 real calls), but the production client is built with Spring AI's default retry template (10 attempts, exponential backoff up to 180 s), so a 5xx run would add minutes that the evaluator would report as latency.

## Worker-level recommendation

- Bound the call instead of retrying forever: connect timeout 5 s, read timeout 120 s (the p95 is 45 s and the max 89 s, so 120 s still leaves headroom) and at most 3 attempts with 2 s / 5 s backoff. The default 10-attempt tower can hold a worker for many minutes on a provider outage.
- A timeout after partial content cannot be salvaged: extraction parses the XML only after the response completes, so there are no half-parsed fields to keep. Mark the message `retry` and let stale recovery re-claim it; the queue already drains and never duplicates, so this is safe.
- Do not lower the A11 threshold: p95 is the provider's reasoning time, not our call path. Keep 1.5× baseline and let A13 (≥99% call success) and the reported failure count cover provider jitter.
- If the p95 itself must come down, the lever is the model's reasoning budget, not the output schema: the variants above show a narrative cap buys one fifth of the time at best.
