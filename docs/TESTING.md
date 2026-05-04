# Testing Guide

> 中文版: [docs/TESTING-zh-CN.md](./TESTING-zh-CN.md)

## Overview

This document describes the testing approach for Cortex Community Edition.

## Test Categories

### 1. End-to-End Tests

Located in `scripts/` directory:

| Script | Description | Lines |
|--------|-------------|-------|
| `regression-test.sh` | Core functionality regression tests | 1535 |
| `thin-proxy-test.sh` | Thin proxy integration tests | 775 |
| `mcp-e2e-test.sh` | MCP server end-to-end tests (SSE mode) | 555 |
| `mcp-streamable-e2e-test.sh` | MCP server end-to-end tests (Streamable HTTP mode) | 292 |
| `docker-compose-test.sh` | Docker Compose deployment tests | 546 |
| `docker-e2e-test.sh` | Docker standalone E2E tests | 701 |
| `webui-integration-test.sh` | WebUI integration tests | 229 |

### 2. Phase 3 Acceptance Tests

Located in `scripts/` directory:

| Script | Description | Lines |
|--------|-------------|-------|
| `phase3-acceptance-test.sh` | Phase 3 userId isolation + extraction feature acceptance tests (15 test functions) | 714 |

**Prerequisites:** Backend running on port 37777 with a clean test project.

```bash
# From project root
./scripts/phase3-acceptance-test.sh
```

### 3. SDK and Demo Integration Tests

Located in `scripts/` directory:

| Script | Description |
|--------|-------------|
| Script | Description | Lines |
|--------|-------------|-------|
| `go-sdk-e2e-test.sh` | Go SDK end-to-end tests | 809 |
| `go-sdk-unit-test.sh` | Go SDK unit tests (all submodules: root + dto + eino + genkit + langchaingo) | 76 |
| `java-sdk-e2e-test.sh` | Java SDK end-to-end tests | 569 |
| `js-sdk-e2e-test.sh` | JavaScript SDK end-to-end tests | 415 |
| `python-sdk-e2e-test.sh` | Python SDK end-to-end tests | 643 |
| `python-demo-e2e-test.sh` | Python Flask demo E2E tests | 490 |
| `demo-v14-test.sh` | Demo v14 feature tests | 196 |
| `demo-v15-test.sh` | Demo v15 feature tests | 149 |
| `demo-v15-extraction-test.sh` | Demo v15 extraction feature tests | 460 |
| `evo-memory-e2e-test.sh` | Evolutionary memory E2E tests | 350 |
| `openclaw-plugin-test.sh` | OpenClaw plugin integration tests | 580 |
| `codex-watcher-test.sh` | Codex CLI watcher integration tests | 215 |
| `export-test.sh` | Export functionality end-to-end tests | 225 |
| `folder-claudemd-test.sh` | Folder CLAUDE.md update feature tests | 104 |
| `js-demo-e2e-test.sh` | JS/TS Express demo E2E acceptance tests | 479 |
| `evo-memory-value-test.sh` | Evolutionary memory business value demonstration tests | 585 |

#### 3.5 Additional Test Utilities

| Script | Description | Lines |
|--------|-------------|-------|
| `seed-diverse-data.sh` | Seeds diverse test data for WebUI testing (various types, concepts, content) | 231 |
| `test-llm-provider.sh` | LLM provider connectivity and response validation tests | 196 |
| `run-all-e2e.sh` | Orchestrator — runs all 10 local E2E suites in one pass (excludes Docker suites and test-llm-provider.sh) | 154 |

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

### Server Not Running

```bash
# Start the server
cd backend
./mvnw spring-boot:run
```

### Test Failures

1. Check server logs
2. Verify database connection
3. Confirm API keys are set
4. Review test output for specific errors

---

## Changelog

| Date | Change |
|------|--------|
| 2026-05-04 | Section 6: Fixed 4 environment variable errors — removed fictitious `DB_HOST` and `SPRING_AI_MCP_SERVER_PROTOCOL`, corrected `DB_USER`→`DB_USERNAME` and `DB_PASS`→`DB_PASSWORD`, corrected `DB_NAME` default `claude_mem_dev`→`claude_mem` (matches docker-compose.yml); EN+ZH in sync |
| 2026-05-03 | Added `go-sdk-unit-test.sh` and `codex-watcher-test.sh` to Section 3 SDK table (10→12 scripts); added missing `python-sdk-e2e-test.sh` to table (EN/ZH in sync) |
| 2026-05-02 | Added missing 'Run Docker Deployment Tests' subsection (5th subsection in Section 5); aligned EN/ZH subsection structure |
| 2026-04-26 | Added Section 3: SDK and Demo Integration Tests (10 scripts); fixed section numbering gap (was missing ### 3, now 1–8 sequential); note: `python-sdk-e2e-test.sh` existed but was omitted |
| 2026-04-03 | Added Phase 3 acceptance test section; added webui-integration-test.sh and docker-e2e-test.sh to E2E table |
