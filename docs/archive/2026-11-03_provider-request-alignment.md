# Provider request alignment audit (round 11)

> **Purpose**: decide whether the LongCat-vs-MiniMax comparison is measuring the models or the
> adapters. Evidence captured from the wire, not from the code that is supposed to send the request.
> **Type**: historical snapshot (point-in-time measurement).
> **Date**: 2026-11-03.

## Method

A local capture proxy (`127.0.0.1:8899`) forwarded `/openai/*` to
`https://api.longcat.chat/openai` and `/anthropic/*` to `https://api.minimaxi.com/anthropic`,
writing the request body and the upstream response to disk. The evaluator was pointed at the
proxy through `OPENAI_BASE_URL` / `ANTHROPIC_BASE_URL`, one real-history sample
(`real-f6a1b89e41`), `EVAL_CONSISTENCY_SAMPLES=1`, `EVAL_KEY_REPEAT=1`. Authorization headers
were masked before the body was written; the forwarded request was unchanged.

## Request field comparison (after the fix)

| field | LongCat-2.5 (`/openai/v1/chat/completions`) | MiniMax-M3 (`/anthropic/v1/messages`) |
|---|---|---|
| `model` | `LongCat-2.5` | `MiniMax-M3` |
| `temperature` | `0.0` | `0.0` |
| `max_tokens` | `4096` | `4096` |
| `top_p` | absent | absent |
| `stop` / `stop_sequences` | absent | absent |
| `stream` | `false` | `false` |
| system prompt | `messages[0].role=system`, 15,963 chars | top-level `system`, 15,963 chars |
| system prompt sha256 | `1f398da1…87a995` | `1f398da1…87a995` |
| user turn | 1,965 chars, sha256 `1ae9428c…96f47` | 1,965 chars, sha256 `1ae9428c…96f47` |
| user turn count | 1 | 1 |
| extra top-level keys | `extra_body` (`{}`) | `anthropic-version`, `anthropic-beta` |

The two prompts are **byte-identical**. The Anthropic adapter neither truncates nor rewrites the
system prompt; the only structural difference is where the system text is carried (a `system`
field versus a `system` role message), which is a protocol requirement, not a semantic change.

## What was actually unequal, and the fixes

1. **Output budget.** The OpenAI-compatible request sent no `max_tokens`, while Anthropic always
   sent `max_tokens=1024`. A truncated LongCat answer could then look like a model error. Fixing
   it at 1024 in turn truncated LongCat with `finish_reason=length` (its `reasoning_content`
   counts against the same budget), so both providers now send `MAX_OUTPUT_TOKENS = 4096`
   (`GoldenSetEvaluator.buildModel`). The captured post-fix requests above both carry 4096 and
   both finish normally (`stop` / `end_turn`).
2. **Token accounting.** The Anthropic response reports `input_tokens: 1` with the real prompt
   sitting in `cache_read_input_tokens: 4329`; the OpenAI response reports `prompt_tokens: 4252`.
   Spring AI 1.1.2 exposes only `promptTokens`, so MiniMax's provider-reported total understates
   a call by roughly 4.3k tokens. Any cross-provider cost table built from that field compares
   two different quantities. The evaluator now estimates the prompt once with a shared
   tokenizer (`jtokkit`, GPT-4 encoding, 4,3xx tokens for this sample) and adds the
   provider-reported completion tokens, so both providers' cost numbers are the same quantity.

## Conclusion

Request-side alignment holds: same prompt bytes, same temperature, single user turn, no implicit
stop sequence, equal output budget. The remaining asymmetries are protocol-level (system field
placement) or measurement-level (usage accounting, now corrected). The model-quality comparison
is therefore valid, and the type plateau cannot be explained by an adapter that quietly
drops or rewrites instructions.

`MiniMax-M3` also reports no `reasoning_content`, while LongCat returns it and pays for it inside
the completion budget — that is a model property, recorded here so it is not re-litigated as an
adapter bug.
