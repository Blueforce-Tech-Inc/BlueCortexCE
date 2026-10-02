# Claude-Mem Java Test Scripts

This directory contains test and development scripts for the Claude-Mem Java implementation.

## Quick Reference

| Script | Purpose | Prerequisites |
|--------|---------|---------------|
| `start.sh` | Start Java backend (dev) | Java 21+ |
| `start-all.sh` | Start Java + Thin Proxy | Java 21+, Node.js |
| `deploy-webui.sh` | Deploy WebUI to Java | Node.js |
| `prebuild-webui.sh` | Stage WebUI files for Docker build | Node.js |
| `sync-resources.sh` | Sync TS resources to Java | jq |
| `test-llm-provider.sh` | Test LLM/embedding APIs | Running backend |
| `regression-test.sh` | E2E regression tests | PostgreSQL + backend |
| `phase3-acceptance-test.sh` | Structured extraction acceptance | Running backend + PostgreSQL |
| `run-all-e2e.sh` | Run the local E2E suites in one pass | Running backend + Node.js |
| `java-sdk-e2e-test.sh` | Java SDK Demo E2E acceptance | Backend + Java Demo (37778) |
| `go-sdk-unit-test.sh` | Go SDK unit tests (all submodules) | Go 1.25+ |
| `go-sdk-e2e-test.sh` | Go SDK Demo E2E acceptance | Backend + Go Demo (37779) |
| `python-sdk-e2e-test.sh` | Python SDK E2E acceptance | Backend + Python 3 |
| `python-demo-e2e-test.sh` | Python Demo E2E acceptance | Backend + Python Demo (37780) |
| `js-sdk-e2e-test.sh` | JS/TS SDK unit + live backend checks | Backend + Node.js |
| `js-demo-e2e-test.sh` | JS Demo E2E acceptance | Backend + JS Demo (37781) |
| `docker-e2e-test.sh` | Docker deployment tests | Docker |
| `docker-compose-test.sh` | Docker Compose tests | Docker |
| `mcp-e2e-test.sh` | MCP Server tests (SSE) | Running backend |
| `mcp-streamable-e2e-test.sh` | MCP Server tests (Streamable HTTP) | Running backend |
| `thin-proxy-test.sh` | Thin Proxy tests | Node.js + backend |
| `webui-integration-test.sh` | WebUI API tests | Running backend |
| `openclaw-plugin-test.sh` | OpenClaw plugin tests | Node.js + backend |
| `folder-claudemd-test.sh` | Folder CLAUDE.md hook flow | Node.js + backend |
| `codex-watcher-test.sh` | Codex CLI watcher E2E | Node.js + backend |
| `demo-v14-test.sh` | Java Demo V14 feature checks | Backend + Java Demo (37778) |
| `demo-v15-test.sh` | Java Demo extraction endpoint checks | Backend + Java Demo (37778) |
| `demo-v15-extraction-test.sh` | Extraction (userId, ICL isolation) | Backend with extraction enabled |
| `evo-memory-e2e-test.sh` | Evo-Memory refinement E2E | Running backend |
| `evo-memory-value-test.sh` | Evo-Memory value demonstration | Running backend |
| `performance-test.sh` | Performance and stress tests | Running backend |
| `export-memories.sh` | Export memory data | jq + running backend |
| `export-test.sh` | Export function tests | jq + running backend |
| `seed-diverse-data.sh` | Generate test data | Running backend |
| `create-distribution.sh` | Package a release distribution | `zip` |
| `code-fingerprint.sh` | Code-scope fingerprint (acceptance baseline) | git |
| `doc-growth-check.sh` | Report documents over the growth threshold | git |

### Demo Ports

SDK/Demo E2E scripts talk to a Demo server on a dedicated port; the backend stays on `37777`.

| Demo | Port | Start command |
|------|------|---------------|
| Java | 37778 | `cd examples/cortex-mem-demo && mvn spring-boot:run -Plocal` |
| Go | 37779 | `cd go-sdk/cortex-mem-go/examples/http-server && PORT=37779 go run .` |
| Python | 37780 | `cd python-sdk/cortex-mem-python/examples/http-server && PORT=37780 python3 app.py` |
| JS/TS | 37781 | `cd js-sdk/cortex-mem-js && PORT=37781 npx tsx examples/http-server/app.ts` |

Each Demo E2E script accepts a `DEMO_BASE` override (default `http://127.0.0.1:<port>`).

---

## Startup Scripts

### `start.sh`

Start the Java backend service with dev profile.

**Usage:**

```bash
# Start with existing build in the foreground
./start.sh

# Start in the background for scheduled checks
./start.sh --background

# Build and start in the foreground
./start.sh --build

# Build and start in the background
./start.sh --build --background
```

**Features:**
- Loads environment variables from `.env` file
- Uses the dedicated backend port 37777 and stops an existing process on that port
- Optional Maven build before starting

### `start-all.sh`

Start both Java backend and Thin Proxy for full stack development.

**Usage:**

```bash
# Start both services
./start-all.sh

# Build Java and start
./start-all.sh --build

# Start without proxy
./start-all.sh --no-proxy
```

**Services Started:**
- Java Backend: http://127.0.0.1:37777
- Thin Proxy: http://127.0.0.1:37778

---

## Deployment Scripts

### `deploy-webui.sh`

Deploy WebUI bundle files to Java Spring Boot static resources.

**Usage:**

```bash
# Deploy existing files (may be outdated)
./deploy-webui.sh

# Build WebUI then deploy (recommended)
./deploy-webui.sh --build

# Force rebuild then deploy
./deploy-webui.sh --rebuild

# Show help
./deploy-webui.sh --help
```

**Source:** `plugin/ui`  
**Target:** `claude-mem-java/src/main/resources/static`

### `sync-resources.sh`

Synchronize resources from TypeScript version to Java version.

**Usage:**

```bash
# Sync both modes and prompts
./sync-resources.sh

# Sync only mode files
./sync-resources.sh --modes

# Sync only prompt files
./sync-resources.sh --prompts

# Skip confirmation
./sync-resources.sh --force
```

**Resources Synced:**
- Mode files: `plugin/modes/*.json` → `java/.../resources/modes/`
- Prompt files: Extracted from modes → `java/.../resources/prompts/`

---

## Test Scripts

### `regression-test.sh`

End-to-end regression test suite that verifies core functionality after code changes.

**Design Principles:**
- **Idempotent**: Safe to run multiple times without conflicts
- **No Auto-Cleanup**: Test data persists after runs for debugging
- **Explicit Cleanup**: Use `--cleanup` flag when you want to remove test data

**Prerequisites:**
- PostgreSQL 16 + pgvector running on localhost:5433 (or 15433 for test)
- DeepSeek API (or configured LLM)
- SiliconFlow API (or configured embedding provider)
- Java 21+

**Usage:**

```bash
# Run full regression test suite (builds first)
./regression-test.sh

# Skip Maven build (use existing JAR)
./regression-test.sh --skip-build

# Cleanup test data when done
./regression-test.sh --cleanup

# Show help
./regression-test.sh --help
```

**Test Coverage:**

| Test | Description |
|------|-------------|
| 1 | Health check endpoint |
| 1b | Health check - Message Queue details |
| 2 | Session creation |
| 3 | Direct observation ingestion |
| 4 | Observation retrieval |
| 5 | Semantic search |
| 6 | Stats endpoint |
| 7 | Projects endpoint |
| 8 | Processing status |
| 8b | SSE stream endpoint |
| 9 | Session completion |
| 9b | Session summary generation |
| 11 | Hybrid search |
| 12 | Timeline endpoint |
| 13 | Deprecated endpoint warning |
| 14 | UserPromptSubmit endpoint |
| 15 | Prior Messages endpoint |
| 17 | Search by file endpoint |
| 18 | Unified session start |

### `phase3-acceptance-test.sh`

Acceptance suite for Phase 3 structured extraction: userId propagation, template-driven
extraction, ICL user isolation, and extraction history.

**Prerequisites:**
- Backend running on port 37777
- PostgreSQL accessible

**Usage:**

```bash
# With extraction disabled (default) — non-extraction assertions only
bash scripts/phase3-acceptance-test.sh

# Full extraction acceptance (required by the maintenance baseline)
EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh
```

`EXTRACTION_ENABLED=true` changes the backend runtime configuration; see
`docs/structured-extraction.md` for the template lifecycle and the DLQ behaviour.

### `run-all-e2e.sh`

Runs the local E2E suites in one pass and prints a per-suite summary.

**Coverage:** `regression-test.sh`, `thin-proxy-test.sh`, `webui-integration-test.sh`,
`mcp-e2e-test.sh`, `mcp-streamable-e2e-test.sh` (skipped unless the server exposes
Streamable HTTP), `export-test.sh`, `openclaw-plugin-test.sh`, `folder-claudemd-test.sh`,
`evo-memory-e2e-test.sh`, `evo-memory-value-test.sh`.

Docker suites and `test-llm-provider.sh` are intentionally excluded — run them separately.

**Usage:**

```bash
# Run every suite; exit non-zero if any suite fails
bash scripts/run-all-e2e.sh

# Pass --skip-build to regression-test.sh and thin-proxy-test.sh
bash scripts/run-all-e2e.sh --skip-build

# Stop at the first failing suite
bash scripts/run-all-e2e.sh --fail-fast
```

**Environment:** `SERVER_URL` (default `http://127.0.0.1:37777`).

### SDK and Demo E2E Tests

Each script verifies returned content, not just "response is non-empty".

| Script | Chain |
|--------|-------|
| `java-sdk-e2e-test.sh` | script → Java Demo API (37778) → Java SDK → backend |
| `go-sdk-e2e-test.sh` | script → Go Demo API (37779) → Go SDK → backend |
| `python-sdk-e2e-test.sh` | script → Python SDK → backend |
| `python-demo-e2e-test.sh` | script → Python Demo API (37780) → Python SDK → backend |
| `js-sdk-e2e-test.sh` | script → JS/TS SDK unit tests + live backend probes |
| `js-demo-e2e-test.sh` | script → JS Demo API (37781) → JS SDK → backend |

**Usage:**

```bash
# Java SDK Demo must already run on 37778
bash scripts/java-sdk-e2e-test.sh

# Go SDK Demo must already run on 37779
bash scripts/go-sdk-e2e-test.sh

# Python SDK (no Demo required — the script sets PYTHONPATH itself)
bash scripts/python-sdk-e2e-test.sh

# Python Demo must already run on 37780
bash scripts/python-demo-e2e-test.sh

# JS/TS SDK: unit tests plus direct backend probes
bash scripts/js-sdk-e2e-test.sh

# JS Demo must already run on 37781
bash scripts/js-demo-e2e-test.sh
```

**Environment:**
- `DEMO_BASE` — Demo base URL (default `http://127.0.0.1:<demo port>`)
- `EXTRACTION_ENABLED=true` — also run the extraction scenarios (skipped when unset)

`go-sdk-unit-test.sh` runs the Go unit tests for the root module *and* every submodule
(`eino`, `genkit`, `langchaingo`), which `go test ./...` from the root module does not cover:

```bash
bash scripts/go-sdk-unit-test.sh
bash scripts/go-sdk-unit-test.sh -v   # verbose
```

### `docker-e2e-test.sh`

End-to-end test suite for Docker deployment.

**Usage:**

```bash
# Run full Docker E2E test (builds images, runs tests)
./docker-e2e-test.sh --cleanup

# Skip image build (use existing images)
./docker-e2e-test.sh --skip-build --cleanup

# Keep containers running after tests
./docker-e2e-test.sh --keep-running

# Show help
./docker-e2e-test.sh --help
```

**Ports Used:**
- PostgreSQL: 15432 (non-default to avoid conflicts)
- Java API: 38888 (non-default to avoid conflicts)

**Test Coverage:**
1. Health endpoint
2. Session creation
3. Observation ingestion
4. Observation retrieval
5. Search endpoint
6. Stats endpoint
7. Projects endpoint
8. Session completion
9. Database persistence
10. Container restart
11. WebUI static files

**Network Issues (China/Corporate Firewall):**

If you encounter Docker registry connection issues, use these mirror registries:

```bash
# Pull base images from mirror
docker pull docker.1ms.run/library/eclipse-temurin:21-jdk
docker pull docker.1ms.run/library/eclipse-temurin:21-jre
docker tag docker.1ms.run/library/eclipse-temurin:21-jdk eclipse-temurin:21-jdk
docker tag docker.1ms.run/library/eclipse-temurin:21-jre eclipse-temurin:21-jre

# Pull pgvector from mirror
docker pull docker.1ms.run/pgvector/pgvector:pg16
docker tag docker.1ms.run/pgvector/pgvector:pg16 pgvector/pgvector:pg16
```

### `docker-compose-test.sh`

Test docker-compose.yml deployment for production readiness.

**Usage:**

```bash
./docker-compose-test.sh --cleanup
./docker-compose-test.sh --skip-build --cleanup
```

**Ports Used:**
- PostgreSQL: 15433
- Java API: 38889

**Test Coverage:**
1. Health endpoint
2. Session creation
3. Observation ingestion
4. Observation retrieval
5. Search endpoint
6. Stats endpoint
7. Projects endpoint
8. Session completion

### `mcp-e2e-test.sh`

MCP Server end-to-end tests for Spring AI MCP Server (WebMVC/SSE).

**Usage:**

```bash
# Test default server
./mcp-e2e-test.sh

# Test custom server URL
./mcp-e2e-test.sh http://localhost:8080
```

**Test Coverage:**
1. Server health check
2. MCP initialization (SSE handshake)
3. Tools list verification
4. search tool
5. timeline tool
6. get_observations tool
7. save_memory tool
8. Error handling
9. REST API compatibility
10. recent tool

### `mcp-streamable-e2e-test.sh`

MCP acceptance over the Streamable HTTP transport (`POST {server}/mcp`). If the server is
running in SSE mode the script reports how to switch instead of failing obscurely.

**Usage:**

```bash
# Default server
bash scripts/mcp-streamable-e2e-test.sh

# Custom server URL (positional argument)
bash scripts/mcp-streamable-e2e-test.sh http://127.0.0.1:37777
```

**Environment:** `SERVER_URL`, `MCP_TEST_PROJECT` (default `/tmp/mcp-e2e-test-streamable`).

### `thin-proxy-test.sh`

Thin Proxy integration tests for Claude Code hooks.

**Prerequisites:**
- Java backend running on http://127.0.0.1:37777
- wrapper.js npm dependencies installed

**Usage:**

```bash
# Run all tests (requires Java backend)
./thin-proxy-test.sh
```

**Test Coverage:**
- wrapper.js syntax and help
- Java API connectivity
- SessionStart/PostToolUse/SessionEnd hooks
- CLAUDE.md update flow
- Prior messages retrieval
- Worktree detection
- Transcript parsing
- Cursor IDE integration
- Privacy tags stripping

### `webui-integration-test.sh`

WebUI API compatibility tests.

**Usage:**

```bash
# Test default server
./webui-integration-test.sh

# Test custom server
BASE_URL=http://localhost:8080 ./webui-integration-test.sh
```

**Test Coverage:**
- Pagination API (offset/limit, items/hasMore)
- Projects API format
- Stats API structure
- Processing Status API
- Context Preview API
- Entity field naming (snake_case)

### `openclaw-plugin-test.sh`

OpenClaw plugin integration tests.

**Prerequisites:**
- Java backend running on http://127.0.0.1:37777
- Node.js 18+
- OpenClaw plugin built

**Usage:**

```bash
./openclaw-plugin-test.sh
```

**Test Coverage:**
- Plugin directory structure
- package.json / plugin.json validation
- TypeScript compilation
- Java backend health check
- Java backend API endpoints
- Plugin code syntax check
- MEMORY.md sync functionality
- Event handling simulation
- Config parameters
- Command simulation
- API endpoint mapping

### `folder-claudemd-test.sh`

Simulates the real PostToolUse hook flow to verify the `--enable-folder-claudemd` option.

**Usage:**

```bash
bash scripts/folder-claudemd-test.sh
```

### `codex-watcher-test.sh`

Codex CLI watcher E2E: builds `codex-watcher`, feeds a Codex session transcript, and
verifies the observations reach the backend.

**Usage:**

```bash
# Build codex-watcher and start the backend if needed
bash scripts/codex-watcher-test.sh

# Reuse a running backend and skip the build
bash scripts/codex-watcher-test.sh --skip-backend --skip-build
```

### `demo-v14-test.sh` / `demo-v15-test.sh`

Feature checks against the Java Demo controllers.

**Prerequisites:** backend on 37777 and the Java Demo on 37778
(`cd examples/cortex-mem-demo && mvn spring-boot:run -Plocal`).

```bash
bash scripts/demo-v14-test.sh    # V14 endpoints (source attribution, extractedData, update/delete)
bash scripts/demo-v15-test.sh    # Phase 3 extraction endpoints exposed by the Demo
```

### `demo-v15-extraction-test.sh`

Validates userId support, structured extraction, ICL user isolation, and extraction history
against the backend API directly (no Demo required).

**Prerequisites:** backend on 37777 with extraction enabled.

**Usage:**

```bash
BACKEND_URL=http://127.0.0.1:37777 bash scripts/demo-v15-extraction-test.sh
```

### `evo-memory-e2e-test.sh` / `evo-memory-value-test.sh`

- `evo-memory-e2e-test.sh` — creates observations across quality levels, simulates
  SUCCESS/PARTIAL/FAILURE feedback, triggers refinement, and verifies quality-based retrieval.
- `evo-memory-value-test.sh` — demonstrates the business value of quality scoring,
  refinement, experience reuse, and feedback inference.

**Prerequisites:** running backend (LLM and embedding providers configured).

```bash
bash scripts/evo-memory-e2e-test.sh
bash scripts/evo-memory-value-test.sh
```

### `performance-test.sh`

Performance and stress suite (ingestion, search, and retrieval latency) against a running
backend on 37777.

```bash
bash scripts/performance-test.sh
```

### `test-llm-provider.sh`

Test LLM and embedding provider configuration.

**Usage:**

```bash
# Run all tests
./test-llm-provider.sh

# Test LLM only
./test-llm-provider.sh --llm

# Test embedding only
./test-llm-provider.sh --embedding

# Check server health
./test-llm-provider.sh --health

# Show current config
./test-llm-provider.sh --config
```

### `export-test.sh`

Test export functionality.

**Prerequisites:**
- Java backend running on localhost:37777
- Some observations/sessions in the database

**Usage:**

```bash
./export-test.sh
```

**Test Coverage:**
- Export without project filter
- Export with project filter
- Batch session API
- Export output format

---

## Utility Scripts

### `export-memories.sh`

Export memories from Java backend to JSON file.

**Usage:**

```bash
# Export all memories
./export-memories.sh

# Export with search query
./export-memories.sh --query "feature implementation"

# Export specific project
./export-memories.sh --project /path/to/project --output backup.json

# Limit results
./export-memories.sh --limit 500
```

**Output Format:**

```json
{
  "exportedAt": "2024-01-15T10:30:00Z",
  "exportedAtEpoch": 1705315800000,
  "query": "*",
  "project": "/path/to/project",
  "totalObservations": 100,
  "totalSessions": 10,
  "totalSummaries": 5,
  "totalPrompts": 50,
  "observations": [...],
  "sessions": [...],
  "summaries": [...],
  "prompts": [...]
}
```

### `seed-diverse-data.sh`

Generate diverse test data for WebUI testing.

**Usage:**

```bash
# Ensure Java backend is running on port 37777
./seed-diverse-data.sh
```

**Data Generated:**
- 12 observations (bugfix, feature, refactor, discovery, decision, change types)
- 5 summaries
- Various concepts: gotcha, how-it-works, pattern, trade-off, etc.

---

## Maintenance Utilities

### `code-fingerprint.sh`

Prints the deterministic fingerprint of the code scope used by the scheduled maintenance
task to decide whether a full acceptance run is required. Documentation, reports, and local
memory files are excluded by design; the algorithm lives only in this script.

**Usage:**

```bash
bash scripts/code-fingerprint.sh
```

**Output:**

```
CODE_FINGERPRINT_VERSION=1
CODE_FINGERPRINT=<sha256>
CODE_RECORD_COUNT=<n>
```

### `doc-growth-check.sh`

Read-only check of the append-heavy project documents (line/byte thresholds). Exit code `0`
means every document is below the threshold; exit code `2` means compaction or archival is
required — it is not a script failure. Thresholds can be overridden with `MAX_LINES` and
`MAX_BYTES`.

**Usage:**

```bash
bash scripts/doc-growth-check.sh
```

### `create-distribution.sh`

Packages the backend JAR, scripts, and documentation into a release directory.

**Usage:**

```bash
bash scripts/create-distribution.sh --help
bash scripts/create-distribution.sh <target_dir>
```

### `prebuild-webui.sh`

Copies the WebUI submodule output where the Docker build expects it (git submodules are not
resolved automatically during `docker build`).

**Usage:**

```bash
bash scripts/prebuild-webui.sh            # stage WebUI resources
bash scripts/prebuild-webui.sh --clean    # remove the copied files
```

---

## Configuration

Override defaults via environment variables:

```bash
# Backend under test (used by the E2E and regression suites)
export SERVER_URL=http://127.0.0.1:37777

# PostgreSQL credentials used by regression-test.sh
# Note: the variable is DB_PASS, not DB_PASSWORD
export DB_HOST=127.0.0.1
export DB_NAME=claude_mem_dev
export DB_USER=postgres
export DB_PASS=123456
```

The Docker suites (`docker-e2e-test.sh`, `docker-compose-test.sh`) read the same `DB_*`
variables but default to their own throwaway databases (`claude_mem_test` and
`claude_mem_compose_test`) and a generated password, so they never touch the dev database.

## Prerequisites Summary

| Tool | Required For |
|------|--------------|
| Java 21+ | All Java scripts |
| Node.js 18+ | start-all.sh, deploy-webui.sh, prebuild-webui.sh, thin-proxy-test.sh, openclaw-plugin-test.sh, folder-claudemd-test.sh, codex-watcher-test.sh, js-sdk-e2e-test.sh, js-demo-e2e-test.sh |
| Go 1.25+ | go-sdk-unit-test.sh, go-sdk-e2e-test.sh |
| Python 3 | python-sdk-e2e-test.sh, python-demo-e2e-test.sh |
| Docker | docker-e2e-test.sh, docker-compose-test.sh |
| PostgreSQL 16 + pgvector | regression-test.sh, phase3-acceptance-test.sh |
| jq | sync-resources.sh, export-memories.sh, export-test.sh |
| curl | All test scripts |
| git | code-fingerprint.sh, doc-growth-check.sh |
