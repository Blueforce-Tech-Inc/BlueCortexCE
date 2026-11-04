# Testing Guide

> 中文版: [docs/TESTING-zh-CN.md](./TESTING-zh-CN.md)

## Overview

This document describes the testing approach for Cortex Community Edition.

## Test Categories

### 1. End-to-End Tests

Located in `scripts/` directory:

| Script | Description |
|--------|-------------|
| `regression-test.sh` | Core functionality regression tests |
| `thin-proxy-test.sh` | Thin proxy integration tests |
| `mcp-e2e-test.sh` | MCP server end-to-end tests (SSE mode) |
| `mcp-streamable-e2e-test.sh` | MCP server end-to-end tests (Streamable HTTP mode) |
| `docker-compose-test.sh` | Docker Compose deployment tests |
| `docker-e2e-test.sh` | Docker standalone E2E tests |
| `webui-integration-test.sh` | WebUI integration tests |

### 2. Phase 3 Acceptance Tests

Located in `scripts/` directory:

| Script | Description |
|--------|-------------|
| `phase3-acceptance-test.sh` | Phase 3 userId isolation + extraction feature acceptance tests (15 test functions) |

**Prerequisites:** Backend running on port 37777 with a clean test project.

```bash
# From project root
./scripts/phase3-acceptance-test.sh
```

### 3. SDK and Demo Integration Tests

Located in `scripts/` directory:

| Script | Description |
|--------|-------------|
| `go-sdk-e2e-test.sh` | Go SDK end-to-end tests |
| `go-sdk-unit-test.sh` | Go SDK unit tests (all submodules: root + dto + eino + genkit + langchaingo) |
| `java-sdk-e2e-test.sh` | Java SDK end-to-end tests |
| `js-sdk-e2e-test.sh` | JavaScript SDK end-to-end tests |
| `python-sdk-e2e-test.sh` | Python SDK end-to-end tests |
| `python-demo-e2e-test.sh` | Python Flask demo E2E tests |
| `demo-v14-test.sh` | Demo v14 feature tests |
| `demo-v15-test.sh` | Demo v15 feature tests |
| `demo-v15-extraction-test.sh` | Demo v15 extraction feature tests |
| `evo-memory-e2e-test.sh` | Evolutionary memory E2E tests |
| `openclaw-plugin-test.sh` | OpenClaw plugin integration tests |
| `codex-watcher-test.sh` | Codex CLI watcher integration tests |
| `export-test.sh` | Export functionality end-to-end tests |
| `folder-claudemd-test.sh` | Folder CLAUDE.md update feature tests |
| `js-demo-e2e-test.sh` | JS/TS Express demo E2E acceptance tests |
| `evo-memory-value-test.sh` | Evolutionary memory business value demonstration tests |

#### 3.5 Additional Test Utilities

| Script | Description |
|--------|-------------|
| `seed-diverse-data.sh` | Seeds diverse test data for WebUI testing (various types, concepts, content) |
| `test-llm-provider.sh` | LLM provider connectivity and response validation tests |
| `soak-test.py` | Manual one-hour mixed-rate soak (real provider, real PostgreSQL): fixed-rate ingest + search, a Prometheus sample every 5 minutes, and a mechanical verdict on heap growth, thread growth, pool saturation, failed rows and stuck `processing` rows. Never runs in CI. |
| `eval-retrieval-quality.py` | Manual retrieval-quality harness (real embeddings, disposable database): hit@1 / hit@5 / MRR for every candidate-generation rule, with the production SQL copied verbatim. Never runs in CI. |
| `upgrade-rehearsal.sh` | Manual old-database upgrade rehearsal: stops Flyway at V12, seeds pre-V13 rows with embeddings and Chinese text, upgrades in place to V25, checks row counts / ids / V13 links / Chinese search, then restores the V12 dump as the rollback proof. Never runs in CI. |
| `hook-payload-e2e.sh` | Manual hook E2E through `proxy/wrapper.js` with real Claude Code payloads (UserPromptSubmit / PostToolUse / SessionEnd): field mapping, persistence, duplicate PostToolUse dedupe, summary generation, and graceful failure against an unreachable service. Never runs in CI. |
| `webui-browser-e2e.sh` | Browser E2E at the viewer level: boots the real backend on a disposable database, seeds 60 observations, then drives headless Chromium (Playwright) to prove the bundled viewer renders cards, reads `files_modified`, pages through the whole feed via `hasMore`, merges an SSE broadcast without a refresh, shows a record created after load once refreshed, and keeps a settings toggle after change → save → refresh. Playwright is a test-only dependency. Runs as step 9 of `verify-local.sh` (SKIP when Playwright is absent) and in the `browser-e2e` CI job, where a missing install fails instead of skipping. |
| `check-viewer-bundle.sh` | Compares the viewer committed under `backend/.../static` with a fresh build of `webui/src`, built in a temp copy so the submodule stays untouched. Exit 0 match, 1 drift (prints per-file hashes and rebuild steps), 2 `webui/node_modules` missing. Called at the start of step 9 of `verify-local.sh`. |
| `run-all-e2e.sh` | Orchestrator — runs the 10 local E2E suites defined here in one pass (excludes Docker suites and test-llm-provider.sh). **Suite 5/10, `mcp-streamable-e2e-test.sh`, is conditional**: it is skipped unless the server exposes Streamable HTTP on `/mcp` (a `POST` of an `initialize` request to `/mcp` must answer 200). The backend's default MCP transport is SSE, so on a default install this orchestrator runs **9** suites and reports 1 skipped — that is expected, not a failure. |

**Prerequisites:** Same as regression tests (backend running, database configured).

```bash
# Run a specific SDK test
./scripts/go-sdk-e2e-test.sh

# Run all demo tests
./scripts/demo-v15-test.sh
```

### 4. Git Submodule Setup (WebUI)

The project uses a git submodule for WebUI. Before building, initialize the submodule:

```bash
# From project root
git submodule update --init --recursive
```

### 5. Running Tests

#### Preferred entry point: one command

```bash
./scripts/verify-local.sh
```

From a clean database, this script proves the test base is green without
hand-run steps:

1. locates a running PostgreSQL container that actually provides pgvector
   (decided by `pg_available_extensions`, never by the image tag). A missing
   container is a failure, not a silent skip.
2. creates a uniquely named disposable database for this run
   (`cortexce_verify_<timestamp>_<pid>_flyway_rehearsal`), migrates it, and
   drops it on exit. A pre-existing shared database is never reused: this round
   produced false failures because a shared database had been migrated by an
   unrelated project (V47–V49 tenancy, `tenant_id NOT NULL`). The
   `_flyway_rehearsal` suffix is required by the DB-gated tests.
3. enables the `vector` extension and asserts it is usable.
4. boots the application once so Flyway migrates, then asserts the recorded
   version equals the newest file in `backend/src/main/resources/db/migration`
   and that `flyway_schema_history` contains no failed migration.
5. runs L1–L4 in one `mvn -B clean install`, with `MEMSCOPE_*` pointed at that
   database.
6. runs `python3 scripts/check-test-results.py --require-zero-skips
   --min-tests 528 --require-layers --require-baseline`. The floor is a hard
   **must-not-go-below** value (currently 528 executed tests); the baseline adds
   a per-layer ratchet so no layer can quietly shrink.

The gate fails when any test is skipped, when the total executed count drops
below 528, when a layer executed no tests, or when any of L1–L4 executed fewer
tests than `scripts/test-baseline.json` records. Raise both the floor and the
baseline deliberately (`--write-baseline`) only after adding tests.
7. runs both documentation consistency checks: the checker self-test, then the
   published EN/ZH API references against the live server.
8. runs the proxy E2E against that server and fails if `node --test` reported
   any skipped test (this is the proxy-forwarding part of L3).
9. verifies the committed viewer bundle is byte-identical to a fresh build of
   `webui/src` (`scripts/check-viewer-bundle.sh`, built in a temp copy so the
   submodule is never modified), then drives that bundle in a real Chromium
   (Playwright): page rendering, `files_modified` on the card, `hasMore`
   pagination, an SSE-only row written while the page stays open, refresh
   visibility, and a settings toggle round trip. When Playwright/Chromium or
   `webui/node_modules` is missing the step prints `SKIP` and continues;
   `REQUIRE_BROWSER_E2E=1` turns that SKIP into a failure, and the dedicated CI
   job always requires the browser run.

Every step prints its name. On failure the script prints `STEP FAILED: <step>`,
the command's own reason, and exits non-zero; it never falls through to a later
step that could mask the failure. Overrides: `PG_CONTAINER`, `PG_PORT`,
`PG_USER`, `PG_PASSWORD`, `TEST_DB` (pins an existing name instead of
generating one), `VERIFY_KEEP_DB` (keeps the generated database for
inspection), `VERIFY_MIN_TESTS`, `VERIFY_APP_PORT`, `VERIFY_E2E_PORT`,
`VERIFY_BROWSER_E2E_PORT`, `REQUIRE_BROWSER_E2E`, `VERIFY_LOG_DIR`.

The step-by-step instructions below are kept so a single failing step can be
reproduced in isolation.

#### The four layers

The suite is split into four layers. `check-test-results.py` infers the layer
from the class name (`com.ablueforce.cortexce.layers.L2*` → L2, `L3*` → L3,
`L4*` → L4, everything else → L1) and `--require-layers` fails when a layer
executed no tests at all, so deleting a layer cannot look green.

| Layer | What it proves | Executed by | Single-layer re-run |
|-------|----------------|-------------|---------------------|
| **L1** unit + integration (488 tests) | Services, controllers, entities, repository contracts, and the model-dependent chains with offline doubles. | Every Surefire class outside `.layers.*` | `mvn -B test -Dtest='!L2HttpRegressionTest,!L3AcceptanceTest,!L4EndToEndTest'` |
| **L2** HTTP regression (7 tests) | A real started application (`RANDOM_PORT`): every operation published in `/v3/api-docs` is called once; missing parameters → 400, legacy paths → 404, malformed UUIDs → 400, `limit`/`offset` clamping, `{items,hasMore}` envelopes. | `L2HttpRegressionTest` | `mvn -B test -Dtest=L2HttpRegressionTest` |
| **L3** acceptance (6 tests) | Documented capabilities become assertions: hook lifecycle, the three ingest writes, retrieval branches, context inject, settings/modes fields, SSE events, the MCP tool catalog plus one call. Each assertion names its source (`docs/API.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`) in the class javadoc. Proxy forwarding is exercised by step 8 of `verify-local.sh`. | `L3AcceptanceTest` + proxy `npm test` | `mvn -B test -Dtest=L3AcceptanceTest` (then `cd proxy && npm test`) |
| **L4** end-to-end (1 test) | One full user path against a real server and the dedicated database: session start → prompt → tool-use → search → context inject → session end → summary, asserting the database terminal state and the SSE events. Waiting is condition-based polling, never `sleep`. | `L4EndToEndTest` | `mvn -B test -Dtest=L4EndToEndTest` |

L2–L4 need the `MEMSCOPE_*` variables (see below); without them they skip
rather than fail locally, and the CI gate rejects any skip:

```bash
export MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:5432/cortexce_flyway_rehearsal"
export MEMSCOPE_TEST_DB_USER=postgres MEMSCOPE_TEST_DB_PASSWORD=123456
export MEMSCOPE_FLYWAY_TEST_URL="$MEMSCOPE_TEST_DB_URL"
export MEMSCOPE_FLYWAY_TEST_USER="$MEMSCOPE_TEST_DB_USER"
export MEMSCOPE_FLYWAY_TEST_PASSWORD="$MEMSCOPE_TEST_DB_PASSWORD"
cd backend
```

#### Prerequisites

- A running PostgreSQL 16 container with the pgvector extension **available**
  (verify with `pg_available_extensions`, not by the image name — see the
  database-setup block in [Running Tests](#running-tests))
- Java 21+
- Required API keys in `.env`

> **PostgreSQL port**: `5432` above is the host port published by the local
> container used in this guide. This project's own `docker compose up -d` publishes
> the database on host port **5433** instead (`"${POSTGRES_PORT:-5433}:5432"` in
> `docker-compose.yml`); either path works as long as `SPRING_DATASOURCE_URL` and
> the `MEMSCOPE_*` URLs point at the same published port. Check what is actually
> running before concluding the database is down — see `docs/DEPLOYMENT.md`.

#### Run Regression Tests

```bash
cd scripts
./regression-test.sh
```

**Options:**

| Option | Description |
|--------|-------------|
| `--skip-build` | Skip Maven build (assume JAR exists) |
| `--cleanup` | Remove test data after tests complete |
| `--parallel` | Run independent tests in parallel |
| `--verbose` | Show detailed output |
| `--help, -h` | Show help message |

**Example:**

```bash
# Run tests with existing JAR
./regression-test.sh --skip-build

# Run tests and cleanup after
./regression-test.sh --cleanup

# Run all tests with verbose output
./regression-test.sh --verbose --parallel
```

#### Run Thin Proxy Tests

```bash
./thin-proxy-test.sh
```

#### Run MCP Tests

```bash
./mcp-e2e-test.sh
```

#### Run Docker Deployment Tests

```bash
# Docker Compose deployment tests
./scripts/docker-compose-test.sh

# Docker standalone E2E tests
./scripts/docker-e2e-test.sh
```

### 6. Test Environment Variables

The test scripts and the backend read **different** variables for the same
settings — exporting `DB_USERNAME` does not make the test scripts use it, and
`scripts/README.md` says so in as many words ("Note: the variable is DB_PASS,
not DB_PASSWORD").

| Variable | Read by | Default | Description |
|----------|----------|---------|-------------|
| `SERVER_URL` | test scripts | http://127.0.0.1:37777 | Server URL. `mcp-e2e-test.sh` defaults to `http://localhost:37777`; `phase3-acceptance-test.sh` reads `BACKEND_URL` (same default) instead |
| `DB_NAME` | `regression-test.sh` and the Docker suites | `claude_mem_dev` | Database name. `regression-test.sh` defaults to `claude_mem_dev`, `docker-compose-test.sh` to `claude_mem_compose_test`, `docker-e2e-test.sh` to `claude_mem_test`. `claude_mem` is the **docker-compose** default for the application's own database, not a test-suite default |
| `DB_HOST` | `regression-test.sh` | 127.0.0.1 | PostgreSQL host the scripts connect to |
| `DB_USER` | `regression-test.sh` and the Docker suites | postgres | PostgreSQL user the scripts connect with |
| `DB_PASS` | `regression-test.sh` and the Docker suites | `123456` | PostgreSQL password the scripts connect with; the two Docker suites default to `testpassword123` |
| `DB_USERNAME` | **the backend only** | postgres | The backend's own variable — `application.yml` reads `${SPRING_DATASOURCE_USERNAME:${DB_USERNAME:postgres}}`. The test scripts do **not** read it |
| `DB_PASSWORD` | **the backend only** | `123456` | The backend's own variable — `application.yml` reads `${SPRING_DATASOURCE_PASSWORD:${DB_PASSWORD:123456}}`. The test scripts do **not** read it |
| `SPRING_AI_OPENAI_API_KEY` | the backend, the two Docker suites, `java-sdk-e2e-test.sh` | - | OpenAI/DeepSeek API key |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | the backend, the two Docker suites | - | Embedding API key |
| `MEMSCOPE_TEST_DB_URL` | `mvn test` (`ImportServiceDatabaseTest`, `MemoryScopeServiceDatabaseTest`, `ObservationRepositoryScopeQueryTest`, `OfflineAiChainIntegrationTest`, `LlmProviderFailureTest`, `L2HttpRegressionTest`, `L3AcceptanceTest`, `L4EndToEndTest`) | - | JDBC URL of a disposable PostgreSQL + pgvector database already migrated to the latest Flyway version. Unset ⇒ the 48 DB-gated tests skip. `ImportServiceDatabaseTest`, `OfflineAiChainIntegrationTest`, `LlmProviderFailureTest` and the three layer tests additionally require the database name to match `*_flyway_rehearsal` |
| `MEMSCOPE_TEST_DB_USER` / `MEMSCOPE_TEST_DB_PASSWORD` | same | postgres | Credentials for `MEMSCOPE_TEST_DB_URL` |
| `MEMSCOPE_FLYWAY_TEST_URL` | `ConversationScopeFlywayDatabaseTest` | - | JDBC URL of a disposable `*_flyway_rehearsal` database; the test runs the migrations itself |
| `MEMSCOPE_FLYWAY_TEST_USER` / `MEMSCOPE_FLYWAY_TEST_PASSWORD` | same | postgres | Credentials for `MEMSCOPE_FLYWAY_TEST_URL` |

```bash
# Reuse the PostgreSQL container that is already running on this machine and
# create a dedicated database inside it. All commands are copy-paste from the
# project root; adjust the four variables if your container/port/credentials
# differ (find them with `docker ps` and `docker inspect`).
PG_CONTAINER=postgresql            # running container that has pgvector available
PG_PORT=5432                       # host port published by that container
PG_USER=postgres
PG_PASSWORD=123456
TEST_DB=cortexce_flyway_rehearsal  # must end in _flyway_rehearsal

# 1. Confirm the container and that pgvector is actually available.
#    Do not infer this from the image tag: postgres:16 images may or may not
#    ship pgvector, so query pg_available_extensions.
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -i postgres
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -tAc \
  "SELECT default_version FROM pg_available_extensions WHERE name='vector'"

# 2. Create a dedicated database and enable the extension. Never reuse a
#    business database for this; the tests truncate tables. The DROP line makes
#    this block re-runnable from a clean state.
docker exec "$PG_CONTAINER" psql -U "$PG_USER" \
  -c "DROP DATABASE IF EXISTS $TEST_DB WITH (FORCE)"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -c "CREATE DATABASE $TEST_DB"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" \
  -c "CREATE EXTENSION IF NOT EXISTS vector"

# 3. Migrate to V20 by booting the backend once against that database.
#    Flyway runs at startup; stop the process right after the health check.
#    (Skip `package` if target/cortex-ce-*.jar already exists.)
cd backend
mvn -B -DskipTests package
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
SPRING_DATASOURCE_USERNAME="$PG_USER" SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
SERVER_PORT=37888 java -jar target/cortex-ce-*.jar &
APP_PID=$!
until curl -sf http://127.0.0.1:37888/api/health >/dev/null; do sleep 1; done
kill "$APP_PID" && wait "$APP_PID" 2>/dev/null || true

# Expected: the newest version in db/migration (currently 24)
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"

# 4. Run the full suite against the dedicated database: 508 tests, 0 skipped.
MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
MEMSCOPE_TEST_DB_USER="$PG_USER" MEMSCOPE_TEST_DB_PASSWORD="$PG_PASSWORD" \
MEMSCOPE_FLYWAY_TEST_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
MEMSCOPE_FLYWAY_TEST_USER="$PG_USER" MEMSCOPE_FLYWAY_TEST_PASSWORD="$PG_PASSWORD" \
mvn -B clean install
```

> The database must already be at the latest Flyway version: these tests assert
> the V20 conversation-scope schema. `V20` migrations are not run by the tests
> that use `MEMSCOPE_TEST_DB_URL`; `ConversationScopeFlywayDatabaseTest` and the
> V20 conflict rehearsal create and migrate their own disposable databases.
>
> CI runs the same suite and then `python3 scripts/check-test-results.py
> --require-zero-skips --min-tests 508 --require-layers --require-baseline`, so a build
> that reports success while any DB-gated test was skipped, or while a whole layer
> disappeared, or while a layer executed fewer tests than the recorded baseline,
> fails. CI provisions its own `pgvector/pgvector:pg16`
> service container instead of reusing a local one.

#### Offline verification of the three LLM-dependent chains

Three chains need a model: automatic observation extraction, automatic session
summary, and semantic search. `backend/src/test/java/com/ablueforce/cortexce/offline/`
supplies both model beans as test-scoped `@TestConfiguration` beans
(`OfflineAiTestModels`), so **no API key and no outbound network are needed**.
Production configuration is untouched: `SpringAiConfig` still builds the real
`ChatModel` / `EmbeddingModel` (and the `ChatClient` over it) exactly as before;
the doubles exist only in test sources and only when a test imports them.

| Chain | Test | What it asserts |
|-------|------|-----------------|
| Observation extraction | `ingestProducesObservation` | `enqueueToolUse` → `processPendingMessage` writes one `mem_observations` row with the fake model's title/facts and a 1024-dim embedding; the queue row ends `processed` |
| Session summary | `sessionEndProducesSummary` | `enqueueSessionSummaryJob` → `processSummaryJob` writes one `mem_summaries` row, the job ends `processed` with `result_summary_id`, the session ends `completed` |
| Semantic search | `embeddingAvailableSearchUsesVectorBranch` | `ViewerController.search` returns `strategy=hybrid`, `fell_back=false` and the row; the same query without a vector returns `tsvector` and **zero** rows, so the hit cannot come from full-text |

The doubles are pure functions of the prompt / embedded text: fixed XML for the
observation and summary prompts, SHA-256-expanded 1024-dim vectors for any text.
The tests never sleep and never wait on the `@Async` event listener — they call
the two worker entry points synchronously and assert the queue state, so repeated
runs are identical. They run only when `MEMSCOPE_TEST_DB_URL` matches
`*_flyway_rehearsal`, exactly like the other DB-backed tests.

```bash
# Same MEMSCOPE_* variables as step 4; runs the offline chain proof alone.
MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:5432/cortexce_flyway_rehearsal" \
MEMSCOPE_TEST_DB_USER=postgres MEMSCOPE_TEST_DB_PASSWORD=123456 \
mvn -B -Dtest=OfflineAiChainIntegrationTest test
```

#### Provider failure behaviour (401/403, 429, timeout, unusable, empty)

`LlmService.chatCompletionWithUsage` no longer collapses every failure into an
empty string. It returns an `LlmResponse` whose `failure` field is a
`FailureKind`, and the observation/summary workers apply one policy to it:

| Kind | Source | Retry |
|------|--------|-------|
| `AUTHENTICATION` | 401/403, `invalid api key`, `permission_denied` (the whole cause chain is inspected, so an HTTP wrapper cannot hide it) | **no** — terminal on the first attempt |
| `RATE_LIMIT` | 429, `rate limit`, `too many requests`, `quota exceeded` | bounded (`claudemem.max-retries`, default 3) |
| `TIMEOUT` | `SocketTimeoutException`, `TimeoutException`, `timed out` | bounded |
| `UNAVAILABLE` | 5xx, `ConnectException`, `connection refused/reset`, Spring AI `TransientAiException` | bounded |
| `INVALID_RESPONSE` | null `ChatResponse` or blank content | **no** — repeating the prompt cannot help |
| `UNKNOWN` | anything unrecognised | **no** (treated as permanent) |

What the workers guarantee, asserted in `LlmProviderFailureTest` by calling the
worker entry points synchronously (no sleeps):

- **Observable.** A failed job ends `failed`, with `retry_count` and
  `failed_at_epoch` written; it never ends `processed`.
- **Bounded.** A transient failure consumes the configured budget and then stops;
  auth and deterministic unusable answers fail immediately instead of burning
  three identical retries (`AgentService.isRetryableException`,
  `SummaryJobService`).
- **No pollution.** The dedup query excludes `failed`, so the next normal ingest
  still succeeds, and the conversation stays `active` rather than being
  corrupted by the attempt.

`chatCompletion(...)` keeps its old signature and behaviour for callers that
only need text; the failure metadata is additive, so no public endpoint changed.

```bash
# Same MEMSCOPE_* variables; runs the failure matrix alone.
mvn -B -Dtest=LlmProviderFailureTest test
```

#### Manual real-provider smoke (never part of CI)

`scripts/real-llm-smoke.sh` spends real quota, so it is a manual step and is
not referenced by `verify-local.sh` or any workflow. It sources
`backend/.env` without printing values, refuses to run when `CI` is set, starts
the application with the real beans, and uses at most five calls (2 chat + 3
embedding) with a 30 s client timeout and no retry:

1. `GET /api/test/embedding` — one embedding call, asserts the returned width
   equals `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` (1024) and that
   `mem_observations.embedding_1024` is `vector(1024)`;
2. `GET /api/test/llm` — one chat call;
3. `POST /api/ingest/tool-use` → the worker's real chat call plus its embedding,
   then polls `mem_observations` (the row exists only if `XmlParser` accepted
   the answer);
4. `GET /api/search` — one embedding call, asserts `strategy=hybrid` and a hit;
5. `GET /api/context/inject`, then reads `mem_pending_messages` back to prove
   the job did not end in `failed`.

If either probe fails (401/402/403/429/timeout), it reports the HTTP status and
provider error with keys redacted, skips the remaining calls, and exits non-zero.
`SMOKE_ONLY=embedding` runs step 1 alone (one call), which is the cheapest way
to check a new embedding provider.

Provider selection is explicit; nothing is guessed from the key:

| `.env` / shell | Meaning |
|---------------|---------|
| `CLAUDEMEM_LLM_PROVIDER` | `openai` (default) or `anthropic`; picks the chat bean |
| `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` | OpenAI-compatible chat (`openai` branch) |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL` | Anthropic-protocol chat (`anthropic` branch), e.g. MiniMax at `https://api.minimaxi.com/anthropic` |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY`, `..._BASE_URL`, `..._MODEL`, `..._DIMENSIONS` | Embedding provider, independent of the chat provider |
| `SPRING_AI_OPENAI_EMBEDDING_SEND_DIMENSIONS` | `false` (default) omits the OpenAI-only `dimensions` field; set `true` only for models that actually accept it (`text-embedding-3-*`) |

These plain names are mapped to Spring properties in `application.yml`
(`spring.ai.openai.api-key/base-url/chat.options.model`,
`spring.ai.anthropic.api-key/base-url/chat.options.model`,
`spring.ai.openai.embedding.*`) because Spring Boot does not relax-bind them
on its own. Override the branch for one run with
`SMOKE_PROVIDER=anthropic`. A 402 `insufficient_balance_error` means
authentication succeeded but the account cannot pay for generation; it is not a
protocol or configuration failure.

**MiniMax re-check, 2026-11-02:** an earlier updated key still returned HTTP 402
`insufficient_balance_error` (code 1008). After the key was updated again,
`SMOKE_PROVIDER=anthropic SMOKE_SKIP_PROBES=1` passed end to end on `MiniMax-M3`:
HTTP 200 and the answer was accepted by `XmlParser` (observation title
`Fixed PaymentService authorization failure for missing order IDs`); the stored
vector is **1024-d**; `GET /api/search` returned `strategy=hybrid`,
`fell_back=false`, `hits=1` in **0.131 s**; `/api/context/inject` returned the
observation title and `updateFiles`; the queue ended **`processed:1`** with no
failed/pending rows. Queue admission → observation persistence took **2.72 s**
(chat + ingest embedding); chat latency on its own is not instrumented. The
smoke spent **3 real calls** (1 chat + 2 embeddings), inside the 5-call cap. The
startup log confirmed the branch was real: `Creating Anthropic-compatible
ChatModel: baseUrl=https://api.minimaxi.com/anthropic, model=MiniMax-M3` and
`Creating ChatClient with model: AnthropicChatModel`. The switch was re-verified
by two restarts against the V20 rehearsal DB with both keys present:
`provider=openai` created only `OpenAiChatModel` (LongCat) and
`provider=anthropic` created only `AnthropicChatModel` (MiniMax), both healthy,
with no `BeanDefinitionOverrideException`; the restart checks spent no calls.

#### Switching between the two protocols

Both protocols are supported and selected by configuration only — no code
change:

| | OpenAI-compatible (default) | Anthropic-compatible |
|---|---|---|
| Switch | `CLAUDEMEM_LLM_PROVIDER=openai` | `CLAUDEMEM_LLM_PROVIDER=anthropic` |
| Key | `OPENAI_API_KEY` | `ANTHROPIC_API_KEY` |
| Base URL | `OPENAI_BASE_URL`, e.g. `https://api.longcat.chat/openai` | `ANTHROPIC_BASE_URL`, e.g. `https://api.minimaxi.com/anthropic` |
| Model | `OPENAI_MODEL`, e.g. `LongCat-2.5` | `ANTHROPIC_MODEL`, e.g. `MiniMax-M3` |
| Wire request | `POST {base}/v1/chat/completions` | `POST {base}/v1/messages` |
| Embedding | unchanged, always the OpenAI-compatible embedding branch | unchanged, same branch |

Practical notes:

- Restart the application after changing the provider or its keys; the
  `ChatModel` is built once at startup. Hot switching is not implemented.
- Confirm what is active in the startup log: `Creating OpenAI-compatible
  ChatModel: baseUrl=…, model=…` versus `Creating Anthropic-compatible
  ChatModel: baseUrl=…, model=…`. In a non-`prod` profile,
  `GET /api/test/llm` is the live connectivity check.
- `GET /api/version` reports application/JDK/Spring versions only; it does not
  name the chat provider.
- The two branches are mutually exclusive at **bean-registration** level:
  `claudemem.llm.provider` decides which `@Bean` is registered, so the other
  branch has no bean definition at all (an empty key additionally suppresses
  the selected branch). `SpringAiConfigProviderBranchTest` pins this offline
  in a real `ApplicationContext` — with both keys present, exactly one
  `ChatModel` exists and `getBean(ChatModel.class)` is unambiguous — and
  fixes the fact that embeddings do not follow the chat provider.
- Do not include a trailing `/v1` in the base URL; the SDK appends the protocol
  path itself.



### 7. MCP Protocol Auto-Detection

The MCP E2E test scripts (`mcp-e2e-test.sh` and `mcp-streamable-e2e-test.sh`) **automatically detect** which protocol your server is running:

- **SSE mode**: `/sse` returns 200, `/mcp` returns 404
- **STREAMABLE mode**: `/mcp` returns 200, `/sse` returns 404

The unified script runs the appropriate tests automatically. No manual protocol selection needed!

- Test session ID: `e2e-regression-{timestamp}`
- Test project: `/tmp/claude-mem-test-{pid}`

### 8. CI/CD Integration

GitHub Actions workflows are configured in `.github/workflows/`:

- `docker.yml` — Docker image build and push.
- `backend-tests.yml` — **Quality Gates**, the check that decides whether the test
  base is actually green:
  - `backend` (matrix: JDK 21 and JDK 25) starts a `pgvector/pgvector:pg16`
    service container published on host port **5433** (GitHub's runner already
    owns 5432), migrates it to the newest migration with the application's own
    Flyway configuration, runs `mvn -B clean install` (no `-DskipTests`), and
    then runs `python3 scripts/check-test-results.py --require-zero-skips
    --min-tests 508 --require-layers --require-baseline`. A skipped DB test, a
    total test-count drop, or any layer shrinking below the recorded baseline
    fails the job, so "BUILD SUCCESS with skipped tests" cannot pass CI.
  - On JDK 21 only, the same job boots the server on 37777 and runs
    `scripts/check-api-doc-surface.py` (API reference vs. live REST/actuator/MCP
    surface, EN and ZH must agree) plus the proxy E2E (`npm ci && npm test` with
    `PROXY_E2E_*`). The proxy test refuses to skip when `GITHUB_ACTIONS=true`.
  - `webui` checks out the submodule, runs `npm install --no-audit --no-fund`
    and `npm run typecheck`. The upstream Bun test suite is **deliberately out
    of scope**: it belongs to the WebUI fork, and gating this repository on an
    upstream test's Bun compatibility would block unrelated work. Our own
    contract with the viewer is pinned in the backend instead
    (`WebUiWireContractTest`: `hasMore`, `updateFiles`, `CLAUDE_MEM_*`). The job
    never modifies the submodule.

## Best Practices

1. **Idempotent**: Tests can be run multiple times safely
2. **No Auto Cleanup**: Test data persists for debugging
3. **Use `--cleanup`**: Remove test data when done
4. **Check Logs**: Review test outputs for failures

## Troubleshooting

### PostgreSQL Connection Failed

```bash
# Check what is actually running
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -i postgres

# Reuse an existing container instead of starting a second, empty one:
# see "Running Tests" for creating a dedicated database inside it.
docker exec <container> psql -U postgres -tAc \
  "SELECT default_version FROM pg_available_extensions WHERE name='vector'"
```

If you started the backend with this project's `docker compose up -d`, PostgreSQL is
**already running on 5433** and your data is in that container — starting another
container would create a second, *empty* database on a different port and make the
problem look worse. Point `SPRING_DATASOURCE_URL` at
`jdbc:postgresql://127.0.0.1:5433/claude_mem` instead.

### Server Not Running

```bash
# Preferred: loads .env for you, frees port 37777, waits until healthy
./scripts/start.sh

# Equivalent manual route — but note that it does NOT read .env
cd backend
./mvnw spring-boot:run
```

> **`.env` is not auto-loaded.** Nothing in the Spring Boot configuration
> (no dotenv dependency, no `spring.config.import`) reads `.env` for you, so
> `./mvnw spring-boot:run` starts the service with whatever happens to be
> exported in your shell. If the API keys live only in a `.env` file, the
> service will still boot and then fail on the first LLM or embedding call.
> Either `export` the variables first, or use `scripts/start.sh`, whose first
> step is to load `.env`.
>
> **The two startup scripts do not read the same file.** `scripts/start.sh`
> changes into `backend/` and reads `backend/.env`; `scripts/start-all.sh`
> changes into `scripts/` and reads `../.env`, i.e. the repo-root `.env` that
> `docker compose` uses (templates: `.env.docker`, `.env.example`). If you
> followed the compose instructions and only created the root `.env`, then
> `start.sh` will start the backend with no keys at all.
>
> `scripts/start.sh --build` rebuilds the JAR first, and `--background` starts
> it detached and polls `/api/health` until it is up. Either way the backend
> listens on **37777**, not 8080.

### Test Failures

1. Check server logs
2. Verify database connection
3. Confirm API keys are set
4. Review test output for specific errors

---

## Changelog

| Date | Change |
|------|--------|
| 2026-11-04 | **Round 18: one error shape, an optional token, and a data-backed pool rejection.** *Candidate pool:* the round-17 root cause (relevant rows outside the fused top-40) was tested rather than assumed. `eval-retrieval-quality.py` gained `--pool-depths`; 90 real queries at `LIMIT 40/100/200` gave hit@1 **0.833 / 0.833 / 0.833**, hit@5 **0.911 / 0.911 / 0.911**, MRR after the 7-day decay **0.866 / 0.867 / 0.868** — all deltas inside the 0.011 one-query noise floor. Latency at **1,000,198 rows** was 2.00 / 2.11 / 2.32 ms p95, well inside the 50 ms budget, so latency was not the blocker; the change was rejected on quality (`docs/archive/2026-11-04_retrieval-pool-round18.md`). *Optional auth:* `cortexce.security.api-token` (env `CORTEXCE_API_TOKEN`) is empty by default so local usage is unchanged; when set, writes under `/api/**` and `GET /api/logs/**` require `Authorization: Bearer <token>`, compared with `MessageDigest.isEqual` over padded buffers, and missing/wrong credentials answer 401 with `WWW-Authenticate` and the unified envelope. `ApiTokenAuthFilterTest` (5) covers unconfigured passthrough, missing 401, wrong 401, correct pass, and the scope rule; `L2ApiTokenAuthTest` (6) proves the wiring, including a write reaching validation with a valid token. Docs state that the token **must be configured before exposing the service**. *Error envelope:* the three shapes became one — `{"error": {"code", "message", "details"?}, "message"}` — with `GlobalExceptionHandler` rendering framework failures and `ErrorResponses` used by 44 handler sites; the top-level `message` mirror keeps `webui/src/ui/viewer/utils/save-error.ts` working (it reads a string field). HTTP status semantics are unchanged. `check-api-doc-surface.py` now validates the live error body against the envelope and checks every documented error example in both languages, with a self-test that rejects a bare string, a missing mirror, and an unknown code. *SSE:* `L2SseContractTest` pins `initial_load` / `processing_status` / `new_prompt` / `new_observation` / `new_summary` names plus their payload fields, so a rename cannot silently reach the Viewer. Suite 514 → **528** tests (L1 504 / L2 17 / L3 6 / L4 1), 0 skipped; EN+ZH in sync |
| 2026-11-04 | **Round 17: observable, sized and bounded.** *Prometheus:* `micrometer-registry-prometheus` plus a `CortexMetrics` `MeterBinder` expose `cortexce.queue.depth{status=pending|processing|failed|stale}`, `cortexce.embedding.backlog`, `cortexce.worker.processing`, `cortexce.llm.calls{outcome,failure_kind}`, `cortexce.llm.retries{failure_kind}`, `cortexce.search.requests{path}` and `cortexce.search.duration{path}` (vector/lexical/hybrid/filter/recent, with percentiles); queue gauges read the table at scrape time and publish NaN instead of failing the scrape. `SpringAiConfig` attaches a `RetryMetricsListener` (`onError` with `retryCount < maxAttempts` means "will retry") and `LlmService.classify` is public so retries and failures share one failure-kind taxonomy. `/actuator` exposes only health/metrics/info/prometheus; env/beans/configprops return 404. An L2 test boots the app, scrapes the endpoint, asserts the names and labels, and checks that one ingest moves the pending gauge and one search moves `path=hybrid`. *Preflight:* `scripts/migration-preflight.sh` reads row count, heap and index size, prints rewrite vs phased cost, lock level and one of DIRECT / MAINTENANCE WINDOW / PHASED ONLINE; `--calibrate` clones the table with all indexes and measures both paths. Calibrated on 100,001 rows: full rewrite 7,201 ms vs model 7,655 (**+6.3%**), phased 11,815 ms vs 11,590 (**−1.9%**); coefficients 6,657 / 998 / 10,189 / 1,400 ms per 100k. On the 1M database it projects **76.6 s locked** and **115.9 s phased** and prints MAINTENANCE WINDOW REQUIRED. *Capacity 1M:* at 1,000,198 rows heap 976.8 MB, indexes 1,033.2 MB (V24 token GIN 167 MB, project/scope btrees 122–132 MB); no path crossed the 2× p95 knee (recent 3.32, deep_page 3.05, type_filter 2.94, query 5.94 ms); ingest 56.82 rows/s with 0 duplicate hashes and 0 stuck. A 20k-row 1024-d HNSW probe measured **7.8 kB/row** (→7.8 GB at 1M) and **0.88 ms/row** build (→~15 min; the graph exceeded the default 64 MB `maintenance_work_mem`), so the recommended single-database limit is **1M fully embedded (~13.8 GB)**, with ANALYZE 0.94 s and VACUUM ANALYZE 12.6 s. *Retrieval round 17:* from the shipped 0.833 baseline, intent/type boosting lost 0.133 (the title verb is a weak type proxy), body/title embedding rerank of the top-20 lost 0.155 / 0.377, and a generic bilingual synonym map (37 terms, fired on 62/90) gained one query to **0.844** — inside the ±1-query embedding noise, so it was not shipped. Synonym + near-duplicate merge + 7-day decay reaches **0.867 / 0.933 / 0.894**, but the merge hides a relevant row for 76/90 queries, so it stays a product decision. 5 of the 15 baseline misses have no relevant row in the 40-candidate pool (coverage floor). Suite 511 → **514** tests (L1 497 / L2 10 / L3 6 / L4 1), 0 skipped; EN+ZH in sync |
| 2026-11-04 | **Round 16: multi-node, recoverable, operable.** *type_filter knee:* the 29.38 ms p95 at 100k came from `memory_scope_id IN (SELECT ... WHERE a OR b)`: the planner could not turn the scope column into an index condition, so it built a bitmap over `type`, hash-joined the scope set and sorted (`Bitmap Heap Scan -> Hash Join -> Sort -> Limit`). Rewriting the scope predicate as an OR of two scalar subqueries (`InitPlan` with a constant scope id) lets the planner use one ordered index range and stop at `LIMIT`; V25 adds `idx_observations_scope_type_created (memory_scope_id, type, created_at_epoch DESC, id DESC)`. At 100k rows type_filter p95 fell **29.38 → 6.58 ms** (with index 0.14 ms, without 17.33 ms, `Limit -> Index Scan`); a DB test asserts the index exists and the query shape is index-driven with `enable_seqscan=off`. *V24 on existing rows:* `ADD COLUMN ... GENERATED ... STORED` is a full rewrite under `ACCESS EXCLUSIVE` — **4337 ms** on 100k (offline), the plain GIN **731 ms** under `SHARE` blocked a concurrent insert for **209 ms**; `CREATE INDEX CONCURRENTLY` removes that block (909 ms), a phased variant (plain column 0.89 ms, 10×10k backfill 7187 ms, concurrent index 734 ms) keeps the longest lock at **0.89 ms**. Recommendation: maintenance window ≤100k, phased ≥1M. *Two instances:* both workers drained one shared queue of 60 jobs (50/10 claims), **0 duplicate content hashes, 0 stuck, 0 pending, 0 stale recoveries**; a 70 s provider call outlived the 1-minute stale threshold, so `ProcessingHeartbeat` now renews `started_processing_at_epoch` for this instance's in-flight ids every 5 s (`heartbeatProcessing`), and a crashed instance stops beating and stays recoverable — a DB test pins the lease refresh and the post-finish no-op. *Backup/restore:* `scripts/backup-restore-drill.sh` dumped a 137 MB / 100k-row database (**478 ms**, **4.0 MiB** compressed), restored it (**5744 ms**), booted the jar to run Flyway validation (**2176 ms**), compared counts and schema history, and required the Chinese `检索` query to return the same **100,000** hits and a probe observation to match; V25's index and V24's tokens survive the restore. *Retrieval:* corpus 60 → **90 queries** (83 mixed / 7 Chinese; the transcripts contain no English-only requests), each carrying its language label, its `asked_at` time and real observation timestamps, and the harness now hides observations that did not exist at the request's turn (future leakage removed). Hybrid hit@1 **0.778** / hit@5 **0.900** / MRR **0.821** versus vector 0.700 / 0.889 / 0.767. A 7-day recency half-life on the fused score reached hit@1 **0.833** / hit@5 **0.911** / MRR **0.866** (30-day 0.811 / 0.900 / 0.849), so it ships as `cortexce.search.recency-half-life-days:7` and a DB test proves the newer duplicate outranks the 30-day-old copy. Near-duplicate merge (newest of each normalized title) scored 0.789 / 0.922 / 0.842 but hid a relevant row for **76 of 90** queries and was rejected. Suite 508 → **511** tests (L1 497 / L2 7 / L3 6 / L4 1), 0 skipped; EN+ZH in sync |
| 2026-11-04 | **Round 15: Chinese lexical search stops returning nothing, and a provider call is bounded.** `english` tsvector returned 0 rows for 60 of 60 Chinese queries because a run of CJK characters is one lexeme. V24 adds `cortex_tokens()` (overlapping character bigrams for CJK, whole ASCII identifiers) into a generated `search_tokens tsvector` (title A / content B / file paths C) with a GIN index — no compiled extension, because `pg_bigm`/`zhparser`/`pgroonga` are absent from the image. `pg_trgm` was measured before rejection: `word_similarity` hit@5 **0.20**, `LIKE ANY(bigrams)` **7.63 s** at 100k rows versus **0.33 s** for token coverage. 60-query measurement: lexical hit@1 **0.767** / hit@5 **0.867** / MRR **0.805** (was 0 / 0 / 0 with 60 empty); production hybrid (weighted RRF, lexical weight 2.0) hit@1 **0.733** / hit@5 **0.883** / MRR **0.799** against vector 0.617 / 0.817 / 0.696; additive fusion froze hit@5 at 0.817, so the weight was swept (1.0–3.0, k 10/60, pool 40) and 2.0/60/40 won. `scripts/degraded-search-e2e-test.sh` boots the jar under `env -i` with no embedding key, asserts `No EmbeddingModel configured`, writes a Chinese observation, and requires `strategy=tsvector`, `fell_back=false`, the row first, and 0 rows for an absent token. The LongCat branch now builds its own JDK client: connect **5 s**, read **120 s**, **3 attempts**, backoff **2 s / 5 s**, retrying timeouts/429/5xx/resets only, with `HttpTimeoutException` classified `TIMEOUT`/retry; a never-answering server fails in **< 6 s** with exactly 3 requests at an 800 ms read timeout. A11 is unaffected (0 retries in its 60 calls; p95 38.9 s stays under the 120 s read bound), and a real smoke completed chat + ingest + hybrid search through the new client. At **100,000 rows** (`2026-11-04_load-test-100k.md`) type_filter is the first path to cross the 2× knee (p95 **29.38 ms** versus 4.94 at 1k), while recent 2.53 / deep_page 2.36 / hybrid query 5.16 do not; ingest holds 51.45 rows/s with 0 duplicate hashes and 0 stuck. Suite 502 → **508** tests (L1 494 / L2 7 / L3 6 / L4 1), 0 skipped; EN+ZH in sync |
| 2026-11-04 | **Round 14: every verification gets its own database, and the three unmeasured pages are measured.** `verify-local.sh` now creates `cortexce_verify_<timestamp>_<pid>_flyway_rehearsal` for each run and drops it on exit (`WITH (FORCE)`), so no run can inherit another project's schema — in round 13, 65 DB-gated tests failed against the shared database because V47–V49 had added tenancy columns with `tenant_id NOT NULL`. `TEST_DB` pins a named database and `VERIFY_KEEP_DB=1` keeps the generated one; both are documented in step 2. The load harness (`scripts/load-test.py`) now records p99 and max, reports the strategy the service actually used, toggles the V23 indexes in the same database, computes the 2× degradation knee and names the report after the row count. At **50,000 observations** (`2026-11-04_load-test-50k.md`): recent p50/p95/p99 **8.59 / 31.05 / 33.73 ms**, deep_page 1.46 / 1.98 / 2.50, type_filter 12.01 / 14.99 / 20.56, hybrid query 5.92 / 7.41 / 8.32; with the index 0.04–0.05 ms versus 12.5–13.7 ms without it (`Limit -> [Gather Merge ->] Sort -> Seq Scan`); ingest 200 posted / 200 persisted at **45.75 rows/s**, 0 duplicate hashes, 0 stuck; the knee lands at 50k for `recent` and `type_filter` and is not crossed for the other two. Retrieval relevance is measured for the first time: **60 real transcript queries / 126 temporally attributed observations**, real `BAAI/bge-m3` embeddings (`scripts/build-retrieval-corpus.py`, `scripts/eval-retrieval-quality.py`). Vector hit@1 **0.600** / hit@5 **0.817** / MRR **0.688**; tsvector **0 / 0 / 0 with 60/60 empty** results; hybrid is **identical to vector** because `search_vector` is built with the `english` configuration and `plainto_tsquery` is conjunctive, so a Chinese query matches no term. Identifier-OR (MRR 0.565), RRF, query truncation (0.595 / 0.512) and a `simple` analyzer were measured; none beats vector, so production fusion is unchanged and `scripts/check-retrieval-quality.py` replays the committed baseline as step 6c (hybrid hit@1 ≥ 0.50, hit@5 ≥ 0.75, MRR ≥ 0.63, ≥ 40 queries, hybrid MRR within 0.02 of vector). LongCat's p95 38,925 ms is attributed by a streaming probe replaying the exact A11 prompts: **network 7.2% / reasoning 69.8% / answer generation 23.0%** of aggregated elapsed time, reasoning **95.9% of the p95**, and **0 retries in 30 baseline and 60 A11 calls**. Dropping `<narrative>`/optional fields moves answer generation only (generation p50 −34% / −55% / −39%) and total p50 −20.8% / −8.0% / −15.1%; `reasoning_effort: low` did not help (total p50 +37.8%, reasoning +61.1%) and `enable_thinking: false` is ignored. The report therefore recommends bounding the client (connect 5 s / read 120 s, 3 attempts with 2 s / 5 s backoff) and marking a timed-out message `retry`, keeping A11 at 1.5× baseline and letting A13 cover provider jitter. Suite unchanged at 502 tests / 0 skipped; EN+ZH in sync |
| 2026-11-04 | **Round 13: the provider-failure count is a gate, and the base is measured under load.** The six r12 “all attempts returned no content” failures were a dropped content block, not a provider outage: the Anthropic response carried a `thinking` block first, Spring AI emits one `Generation` per block, and the adapter read only `getResult()`, so the text block was discarded. `ChatResponseText.extract` now joins every text block and ignores non-text ones, pinned by 6 unit tests; the clean run has 0 failures in 60 calls. A13 gates provider call success at ≥99% as at most `max(1, floor(calls × (1 - rate)))` failed calls per run (`callSuccessRate` in both gate files), while every report still prints the failure count next to the latency. A12 was paid back instead of waived: the candidate lists are comma-separated single lines, only rank 1 contributes identifiers, `<title_budget>` is an empty element, the duplicate prompt paragraphs were collapsed and the narrative is one sentence, so MiniMax p95 tokens fell 6,093 → **5,544** (limit 6,072) and p95 latency 6,615 ms (limit 8,472); LongCat 7,128 tokens / 38,925 ms with 0 failures (the 99% floor still allows 1). The output budget moved 4096 → 8192 because LongCat counts `reasoning_content` against it and one sample spent the whole budget on reasoning. New real-load evidence: `--mode soak` (10k observations, search p50/p95 by path, V23 index on/off plans, queue throughput), `--mode concurrency` (20 sessions × 10 events with concurrent session-end — this exposed a lost update where `ContextCacheService` wrote a stale `active` status over a completed session, fixed with `@DynamicUpdate` on `SessionEntity`, plus a 20-job × 8-worker claim test) and `--mode fault` (pool exhaustion, embedding reject/timeout, SIGKILL mid-processing: 0 lost, 0 duplicate, 0 stuck, stale recovery drained 10 rows). Suite 493 → **502** tests / 0 skipped; `--min-tests` 493 → 502. EN+ZH in sync |
| 2026-11-03 | **Round 12: identifiers as observations; gates may only rise.** `TraceSignals` ranks touched artifacts by changed lines (ties keep edit-stat order; extensionless build files such as `backend/Dockerfile` are parsed) and exposes `<candidate_artifacts>`, `<candidate_identifiers>`, `<title_subject_candidate>`, `<title_identifier_required>` and `<title_budget>`. Rank 1 equals the golden identifier on **60/60** real samples and the candidate list contains it on 60/60. The title contract requires the exact identifier as the last token, and the budget states how many characters remain for the verb and object under the 100-character validator. Real set (60 samples): identifier/subject **97.67% / 97.67%** MiniMax and **100% / 100%** LongCat (90% target met); auditable type 89.47% / 83.33%; action 97.67% / 89.58%. The one MiniMax miss omitted a file basename (100% of misses); no class, configuration-key or endpoint miss. Floors `identifier 0.90` / `subject 0.90` are written into `scripts/eval-gates.json` (raised, never lowered), with `eval-gates-floor.json`, `check-eval-gates.py` (step 6b) and the replay monotonicity test; the same guard now covers `test-baseline.json` through `test-baseline-floor.json`. Latency: MiniMax successful-call p95 8,157 ms (within A11), but six provider failures inflate the aggregate to 14,357 ms; MiniMax p95 tokens 6,093 vs 6,072 (+0.35%) after the three-sentence narrative cap and prompt trim, while LongCat passes both. Suite 485 → **493** tests / 0 skipped; `--min-tests` 485 → 493. EN+ZH in sync |
| 2026-11-03 | **Round 11: evidence before “capability boundary”.** A capture proxy compared the real request bodies of both providers: system prompt byte-identical (15,963 chars, same sha), one user turn, `temperature 0`, no `top_p`/stop, and `max_tokens 4096` on both after the LongCat truncation fix; Anthropic reports cached prompt tokens separately, so evaluation now uses one `jtokkit` estimate for both. Type no longer rests on prose: `TraceSignals` computes file-kind counts, rename-only, added public API, security text and defect evidence and injects them as facts (never a label); the path pattern now rejects an extension that is a prefix of a longer one (`index.h` from `index.html`, `Object.c` from `Object.create`). The labels were cross-checked against those signals: 0 contradictions, then 12 reviewed (10 relabelled, 2 kept as rule overrides) and pinned in `TYPE_OVERRIDES` so regeneration reproduces them. Real set (60 samples): type raw 79.2% MiniMax / 72.1% LongCat, **auditable 83.7% / 81.6%** (six `bugfix` labels have no defect or security evidence the model can see); action 97.9% / 88.4%; identifier and subject 56.25% / 74.42%, so the 90% target is recorded as unmet with cluster evidence, and those two floors are ratcheted at the measured values in `GoldenSetRealReplayTest` while action and auditable type gate at 80%. Suite 474 → **485** tests / 0 skipped; `--min-tests` 474 → 485. EN+ZH in sync |
| 2026-11-03 | **Round 9: metric reachability and reproducibility.** Identifiers remain below 100% only because of model copying: every miss was attributed, none was a truncated title or a label absent from the trace, so the exclusion count is **0** and the residual is recorded as a capability boundary (no threshold lowered). Subject scoring now requires the token to occur in the trace; unreachable labels leave the A6b denominator and are listed, while the legacy subject metric stays as a diagnostic. Three full repetitions at `temperature 0` measured per-metric variance (type σ 2.75pp LongCat / 3.27pp MiniMax) and named the **18/87** (LongCat) and **13/87** (MiniMax) samples that changed type or action class; the disagreements span every category, so extraction now samples **3 times and takes the majority vote by default** (`cortexce.extraction.consistency-samples`, `1` disables it; cost is 3× latency and tokens). Added `scripts/gen-real-golden.py`, which mechanically derives **36 samples from real commits** and is scored separately with `EVAL_GOLDEN=real` (identifiers LongCat 81.8%, MiniMax 61.1%). Added p50/p95 latency and token baselines with A11/A12 at **p95 ≤ 1.5× baseline**. Suite 462 → **472** tests / 0 skipped; `--min-tests` 423 → 472. EN+ZH in sync |
| 2026-11-03 | **Audit gap closure and a per-layer baseline ratchet.** The gate now runs `--min-tests 423 --require-baseline` against `scripts/test-baseline.json` (L1 409 / L2 7 / L3 6 / L4 1), so a layer cannot shrink even if the total stays above the floor. New database tests: an 8-worker claim race (exactly one winner), queue depth including `processing`, failed counts/reasons, batch-vs-single session lookup equivalence, the V23 index set, and a V20 legacy-conflict rehearsal (readable `TECHNICAL_BUCKET_CONFLICT` / `OBSERVATION_WITHOUT_SCOPE`, documented cleanup, re-run succeeds). The API contract probe now pins error bodies and the real liveness/readiness shapes (`/api/health` is `{service,status,timestamp}`; checks live on `/api/readiness`). `verify-local.sh` prefers the dedicated `claude-mem-test-pg` container because a shared `postgresql` container can be saturated by unrelated clients. Suite 360 → **423** tests / 0 skipped; EN+ZH in sync |
| 2026-11-02 | **Golden-set output quality evaluation and final MiniMax re-check.** Added a manual-only, CI-refusing evaluation harness (`scripts/eval-model-quality.sh` + `GoldenSetEvaluator`, cap `EVAL_MAX_CALLS`, default 20) over a hand-authored golden set of **12 real-looking tool-use traces** spanning bug-fix, refactor, test-add, config-change, security-fix, docs-update and two low-value traces that must be skipped, each with expected observation/skip, type, title keywords and facts/concepts keywords. Scoring is pure offline logic (`GoldenSetScorer`) covered by 6 unit tests. LongCat run: **12/12 calls, 0 failures, 0 format errors; contract 92%, skip accuracy 92% (TP/FP/FN/TN 2/1/0/9), type 100%, title full-hit 44%, facts 54%/94%, concepts 57%/83%**; the one false positive is a small timezone regression test that was skipped, and title misses are paraphrase misses, not malformed output. Report: `docs/archive/2026-11-02_model-output-quality-eval.md`. MiniMax was re-checked after the key was updated again and **passed end to end**: HTTP 200, answer accepted by `XmlParser`, stored vector 1024-d, `GET /api/search` `strategy=hybrid`/`fell_back=false`/`hits=1` in 0.131 s, `/api/context/inject` returned the observation title and `updateFiles`, queue `processed:1`; **3 real calls**, queue admission → persistence 2.72 s. `EVAL_PROVIDER=anthropic` then ran the same 12 samples on `MiniMax-M3` (**12 calls**): contract 83%, skip accuracy 83% (TP/FP/FN/TN **0/0/2/10** — it produced observations for both low-value traces), type 90% (one `test-add` called `bugfix`), title full-hit 20%, facts 65%/90%, concepts 52%/80%, 0 format errors; report `docs/archive/2026-11-02_model-output-quality-eval-minimax.md`. Suite 354 → **360** tests / 0 skipped (6 offline scorer tests); `--min-tests` 354 → 360. EN+ZH in sync |
| 2026-11-02 | **Dual-protocol providers, LongCat real chain, embedding request fix.** LongCat (`OpenAI-compatible`, `https://api.longcat.chat/openai`, `LongCat-2.5`) completed the full real path: extraction chat → `XmlParser` accepted the answer → observation persisted with a **1024-dimension** vector → `GET /api/search` returned `strategy=hybrid`, `fell_back=false`, 1 hit (0.11 s) → `/api/context/inject` contained the observation title and `updateFiles` → the queue row ended `processed`, not `failed`. The embedding probe returns **1024 dimensions in 0.29 s** with `BAAI/bge-m3`. Two fixes came out of this: (1) the OpenAI-only `dimensions` field was rejected by SiliconFlow (`400 code 20015`), so it is now opt-in through `spring.ai.openai.embedding.send-dimensions` (default `false`); (2) the extraction prompts told a skipping model to say nothing while the parser expected `<skip>`, so a plain-text skip was classified as malformed — both prompts now require `<skip>reason</skip>`, and `<subtitle>`/`<narrative>` in the templates were closed. `OPENAI_API_KEY` is now mapped into `spring.ai.openai.api-key`, so the plain name really takes effect. The openai/anthropic switch is now decided at **bean-registration** level (`@ConditionalOnProperty` on `claudemem.llm.provider`), not by a method returning null: with both keys present, the old arrangement still exposed two `ChatModel` definitions and `getBean(ChatModel.class)` raised `NoUniqueBeanDefinitionException` — caught by the new context-level test. Added `SpringAiConfigProviderBranchTest` (5 tests: mutual exclusion, registration-level single bean in a real context, empty-key suppression, embedding independent of the chat provider) and `XmlParserTest` (4 tests: fenced observation, `<skip>`, untagged text rejection, unclosed tags). Documented the openai/anthropic switch table, restart requirement and how to confirm the active branch. MiniMax was re-attempted with an updated `ANTHROPIC_API_KEY`: that key still returned **`402 insufficient_balance_error` (code 1008)**, so no answer was parseable and the Anthropic chain did not run on that attempt; the later key re-check that passed end to end is recorded in the entry above. The Anthropic bean itself was confirmed active from the startup log, and a two-restart switch check (`provider=openai` → only `OpenAiChatModel`; `provider=anthropic` → only `AnthropicChatModel`, both healthy) passed without `BeanDefinitionOverrideException`. Suite 345 → 354 tests / 0 skipped; `--min-tests` 345 → 354. EN+ZH in sync |
| 2026-11-02 | Added test-scoped offline doubles for the three model-dependent chains (`OfflineAiTestModels`: `ChatModel` + `EmbeddingModel`) and `OfflineAiChainIntegrationTest` (observation extraction, session summary, semantic search on the vector branch). No production configuration changed. Verified 319 tests / 0 failures / 0 skipped against `cortexce_flyway_rehearsal`, and 319 / 22 skipped with the `MEMSCOPE_*` variables unset. The CI gate's `--min-tests` moved from 300 to 319; EN+ZH in sync |
| 2026-11-02 | Section 8: documented the `backend-tests.yml` Quality Gates job (DB-backed suite on JDK 21/25 with zero-skip gate, API doc surface, proxy E2E) and the `webui` typecheck job, including why the upstream Bun suite is out of scope and where the viewer contract is pinned (`WebUiWireContractTest`). Added the re-runnable `DROP DATABASE ... WITH (FORCE)` step to the Section 6 block. Re-verified the full suite locally against the already-running `postgres:16` container (vector `0.8.2`): 316 tests / 0 failures / 0 skipped on both JDK 21 and JDK 25; EN+ZH in sync |
| 2026-11-02 | Section 6: replaced "run a new `docker run -p 5432:5432 ...`" with a copy-paste procedure that reuses an already running PostgreSQL container, creates a dedicated `cortexce_flyway_rehearsal` database inside it, verifies pgvector with `pg_available_extensions`, migrates to V20, and runs `mvn -B clean install` with the `MEMSCOPE_*` variables. Corrected the earlier assumption that a locally running container lacks pgvector: `postgres:16` on this machine reports vector `0.8.2`; the image tag is not evidence. Verified 313 tests / 0 failures / 0 skipped against that database (316 after adding `WebUiWireContractTest`); EN+ZH in sync |
| 2026-10-20 | Section 6: the table listed the **backend's** `DB_USERNAME` / `DB_PASSWORD` as if the test scripts read them — the scripts read `DB_USER` / `DB_PASS`, as `scripts/README.md` states outright ("Note: the variable is DB_PASS, not DB_PASSWORD"); it also gave `DB_NAME` the docker-compose default `claude_mem` instead of the suites' `claude_mem_dev`, and omitted `DB_HOST`, which `regression-test.sh:41` still reads. The table now names the reader of each variable and its real default, and restores the `DB_HOST` / `DB_USER` / `DB_PASS` rows. This **supplements, and does not rewrite**, the 2026-05-04 entry below: that entry changed those names for the backend's sake without noticing the table is about test variables, so `DB_HOST` was dropped as "fictitious" while the scripts kept reading it; EN+ZH in sync |
| 2026-10-03 | "Server Not Running" pointed only at `./mvnw spring-boot:run`, which does **not** read `.env` — no dotenv dependency and no `spring.config.import` exist in the Spring Boot config, so a user whose keys live in a `.env` file gets a service that boots and then fails on the first LLM/embedding call. Documented `scripts/start.sh` (loads `.env`, pins 37777, `--build`/`--background`) and the fact that `start.sh` reads `backend/.env` while `start-all.sh` and `docker compose` read the repo-root `.env` — a user who created only the root `.env` gets a keyless backend from `start.sh`. EN+ZH in sync |
| 2026-10-02 | Documented the PostgreSQL port split (`:5433`) in Prerequisites and in "PostgreSQL Connection Failed" — a user who started the backend with `docker compose up -d` and then followed the troubleshooting `docker run -p 5432:5432` would start a second, *empty* database on 5432 while their data sat in the compose container on 5433. Verified `run-all-e2e.sh` really does run 10 local suites and `phase3-acceptance-test.sh` really does define 15 test functions, so both counts were left unchanged; EN+ZH in sync |
| 2026-05-04 | Section 6: Fixed 4 environment variable errors — removed fictitious `DB_HOST` and `SPRING_AI_MCP_SERVER_PROTOCOL`, corrected `DB_USER`→`DB_USERNAME` and `DB_PASS`→`DB_PASSWORD`, corrected `DB_NAME` default `claude_mem_dev`→`claude_mem` (matches docker-compose.yml); EN+ZH in sync |
| 2026-05-03 | Added `go-sdk-unit-test.sh` and `codex-watcher-test.sh` to Section 3 SDK table (10→12 scripts); added missing `python-sdk-e2e-test.sh` to table (EN/ZH in sync) |
| 2026-05-02 | Added missing 'Run Docker Deployment Tests' subsection (5th subsection in Section 5); aligned EN/ZH subsection structure |
| 2026-04-26 | Added Section 3: SDK and Demo Integration Tests (10 scripts); fixed section numbering gap (was missing ### 3, now 1–8 sequential); note: `python-sdk-e2e-test.sh` existed but was omitted |
| 2026-04-03 | Added Phase 3 acceptance test section; added webui-integration-test.sh and docker-e2e-test.sh to E2E table |
