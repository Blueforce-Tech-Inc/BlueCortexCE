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

#### Prerequisites

- PostgreSQL 16 + pgvector running on localhost:5432
- Java 21+
- Required API keys in `.env`

> **PostgreSQL port**: `5432` above assumes a native install or the `docker run -p 5432:5432`
> used in Troubleshooting. This project's own `docker compose up -d` publishes the database on
> host port **5433** instead (`"${POSTGRES_PORT:-5433}:5432"` in `docker-compose.yml`), and
> that path also needs `SPRING_DATASOURCE_URL` pointed at the published port. Check which one
> you actually have before concluding the database is down — see `docs/DEPLOYMENT.md`.

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

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_URL` | http://127.0.0.1:37777 | Server URL |
| `DB_NAME` | claude_mem | Database name |
| `DB_USERNAME` | postgres | Database username |
| `DB_PASSWORD` | - | Database password |
| `SPRING_AI_OPENAI_API_KEY` | - | OpenAI/DeepSeek API key |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | - | Embedding API key |

### 7. MCP Protocol Auto-Detection

The MCP E2E test scripts (`mcp-e2e-test.sh` and `mcp-streamable-e2e-test.sh`) **automatically detect** which protocol your server is running:

- **SSE mode**: `/sse` returns 200, `/mcp` returns 404
- **STREAMABLE mode**: `/mcp` returns 200, `/sse` returns 404

The unified script runs the appropriate tests automatically. No manual protocol selection needed!

- Test session ID: `e2e-regression-{timestamp}`
- Test project: `/tmp/claude-mem-test-{pid}`

### 8. CI/CD Integration

GitHub Actions workflows are configured in `.github/workflows/`:

- `docker.yml` - Docker image build and push

## Best Practices

1. **Idempotent**: Tests can be run multiple times safely
2. **No Auto Cleanup**: Test data persists for debugging
3. **Use `--cleanup`**: Remove test data when done
4. **Check Logs**: Review test outputs for failures

## Troubleshooting

### PostgreSQL Connection Failed

```bash
# Check PostgreSQL status
docker ps | grep postgres

# Start PostgreSQL
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=123456 pgvector/pgvector:pg16
```

If you started the backend with this project's `docker compose up -d`, PostgreSQL is
**already running on 5433** and your data is in that container — the `docker run` above
would start a second, *empty* database on 5432 and make the problem look worse. Point
`SPRING_DATASOURCE_URL` at `jdbc:postgresql://127.0.0.1:5433/claude_mem` instead.

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
| 2026-10-03 | "Server Not Running" pointed only at `./mvnw spring-boot:run`, which does **not** read `.env` — no dotenv dependency and no `spring.config.import` exist in the Spring Boot config, so a user whose keys live in a `.env` file gets a service that boots and then fails on the first LLM/embedding call. Documented `scripts/start.sh` (loads `.env`, pins 37777, `--build`/`--background`) and the fact that `start.sh` reads `backend/.env` while `start-all.sh` and `docker compose` read the repo-root `.env` — a user who created only the root `.env` gets a keyless backend from `start.sh`. EN+ZH in sync |
| 2026-10-02 | Documented the PostgreSQL port split (`:5433`) in Prerequisites and in "PostgreSQL Connection Failed" — a user who started the backend with `docker compose up -d` and then followed the troubleshooting `docker run -p 5432:5432` would start a second, *empty* database on 5432 while their data sat in the compose container on 5433. Verified `run-all-e2e.sh` really does run 10 local suites and `phase3-acceptance-test.sh` really does define 15 test functions, so both counts were left unchanged; EN+ZH in sync |
| 2026-05-04 | Section 6: Fixed 4 environment variable errors — removed fictitious `DB_HOST` and `SPRING_AI_MCP_SERVER_PROTOCOL`, corrected `DB_USER`→`DB_USERNAME` and `DB_PASS`→`DB_PASSWORD`, corrected `DB_NAME` default `claude_mem_dev`→`claude_mem` (matches docker-compose.yml); EN+ZH in sync |
| 2026-05-03 | Added `go-sdk-unit-test.sh` and `codex-watcher-test.sh` to Section 3 SDK table (10→12 scripts); added missing `python-sdk-e2e-test.sh` to table (EN/ZH in sync) |
| 2026-05-02 | Added missing 'Run Docker Deployment Tests' subsection (5th subsection in Section 5); aligned EN/ZH subsection structure |
| 2026-04-26 | Added Section 3: SDK and Demo Integration Tests (10 scripts); fixed section numbering gap (was missing ### 3, now 1–8 sequential); note: `python-sdk-e2e-test.sh` existed but was omitted |
| 2026-04-03 | Added Phase 3 acceptance test section; added webui-integration-test.sh and docker-e2e-test.sh to E2E table |
