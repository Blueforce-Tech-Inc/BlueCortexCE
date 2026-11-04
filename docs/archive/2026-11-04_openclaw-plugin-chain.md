# OpenClaw plugin and proxy against a real backend (2026-11-04 19:52)

- Date: 2026-11-04
- Scope: the calls the OpenClaw plugin actually makes (`session_start`, `before_prompt_build`,
  `tool_result_persist`, `session_end`, and the five commands), plus the proxy's real
  Claude Code hook payloads. No mocks on the backend side: the worker ran the built jar
  against `cortexce_f1_source` (V25 schema, 79,500 observations in `/f1-project`) with the
  real LongCat provider, and the plugin was the rebuilt `openclaw-plugin/dist/index.js`.

## 1. Plugin call chain (16/16 checks)

The harness injected a real OpenClaw plugin API (`on`, `registerCommand`, `logger`,
`pluginConfig`, `runtime.channel`) into the plugin, so every check below drove the same
code path the host uses.

| check | observed |
|---|---|
| `session_start` accepted | hook returned |
| context injection | `appendSystemContext` 3,928 chars, starting `# f1-project recent context …` |
| duplicate `tool_result_persist` delivery | both calls awaited, no throw |
| `session_end` accepted | hook returned |
| `claude-mem-status` | `Status: UP` |
| `claude-mem-projects` | project list returned |
| `claude-mem-search` (`中文检索样本 记录`) | `Search results for: …` with real rows |
| `claude-mem-recent` | rows returned, not `Failed` |
| `claude-mem-timeline` | rows returned, not `Failed` |
| backend down: status | `Claude-Mem Java backend unreachable at port 37999` |
| backend down: status latency | 2 ms |
| backend down: search | `Search failed or no results for: anything` |
| backend down: search latency | under 2,000 ms |
| backend down: injection | returned `undefined` instead of throwing |
| backend down: injection latency | under 2,000 ms |
| backend down: warning | visible in the plugin logger |

Result: `SUMMARY pass=16 fail=0`.

## 2. Duplicate delivery is deduplicated in the backend

The same `Write` tool result was delivered twice to `tool_result_persist`. The backend
queue held one row and one observation, and the real provider extracted it once:

```sql
SELECT p.tool_name, p.status, count(*)
FROM mem_pending_messages p JOIN mem_sessions s ON s.id = p.session_db_id
WHERE s.project_path = 'openclaw-g2-provider'
GROUP BY 1, 2;
```

```
 tool_name |  status   | count
-----------+-----------+-------
 Write     | processed |     1
```

`mem_observations` for `openclaw-g2-provider` also has exactly **1** row. The second
delivery did not enqueue a second job; dedupe is keyed on
`content_session_id + tool_name + tool_input_hash`.

## 3. No provider (degraded backend) still works

A second worker on port 37,802 ran with `SPRING_AI_OPENAI_API_KEY` empty, against the same
database. Observed output from the plugin hooks:

```json
{
  "contextChars": 3928,
  "contextHead": "# f1-project recent context, 2026-11-04 7:52pm cst\n\n📊 50 observations …",
  "searchHead": "Search results for: 中文检索样本 记录",
  "searchHasResults": true,
  "warn": []
}
```

- context injection is provider-independent: it returned the same 3,928-char project
  context and injected nothing else;
- search fell back to PostgreSQL `tsvector` and still returned rows;
- observation extraction could not run, so the queue row ended `failed` and the summarize
  job ended `skipped` — no exception reached the host:

```sql
SELECT p.tool_name, p.status, p.failure_kind, s.content_session_id
FROM mem_pending_messages p JOIN mem_sessions s ON s.id = p.session_db_id
WHERE s.content_session_id = 'openclaw-np-host-1';
```

```
 tool_name |  status  | failure_kind | content_session_id
-----------+----------+--------------+---------------------
 Read      | failed   | UNKNOWN      | openclaw-np-host-1
           | skipped  |              | openclaw-np-host-1
```

## 4. Proxy real hook payloads

`scripts/hook-payload-e2e.sh` fed real Claude Code hook JSON through
`proxy/wrapper.js` into a live backend. PASS:

- the prompt and cwd from `UserPromptSubmit` were persisted;
- a repeated `PostToolUse` payload produced one queue row (dedupe);
- `SessionEnd` completed the session and a summary was written
  (`✓ Summary saved for conversation …`);
- an unreachable service made the proxy exit 0 with a readable message instead of
  failing the host hook.

## 5. Defect found and fixed

`GET /api/context/inject` can answer HTTP 200 with a human-readable placeholder,
`[Error generating context for …]`, when the database is unavailable or the schema does
not match. The plugin injected that string into the model's system prompt. The hook now
drops any context starting with that placeholder (JSON and raw bodies), caches only real
context, and returns without injecting otherwise.

Regression coverage is in the committed plugin suite:

- `test/context-error.test.mjs` — JSON placeholder, raw placeholder, real context + 60 s cache;
- `test/backend-down.test.mjs` — dead backend: readable status/search text and silent injection;
- `scripts/verify-local.sh` step 8b runs `npm ci` + `npm test` and fails on any skipped test.

The suite reports 7 tests, 7 pass, **0 skipped**.

## Limitations

- The plugin has no local queue of its own: when the worker is down, events are dropped
  after a warning (the backend's durable queue only helps once a request reaches it).
- With no provider, observations are queued and marked `failed`; the plugin does not
  retry them. Context injection and lexical search remain available.
- The 60 s context cache is module-level, so a host restart is required to see a context
  change sooner.
