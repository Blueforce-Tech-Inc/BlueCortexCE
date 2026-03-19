# Testing Guide

## Overview

This document describes the testing approach for Cortex Community Edition.

## Test Categories

### 1. End-to-End Tests

Located in `scripts/` directory:

| Script | Description | Lines |
|--------|-------------|-------|
| `regression-test.sh` | Core functionality regression tests | 873 |
| `thin-proxy-test.sh` | Thin proxy integration tests | 766 |
| `mcp-e2e-test.sh` | MCP server end-to-end tests | 606 |
| `docker-compose-test.sh` | Docker Compose deployment tests | 546 |
| `webui-integration-test.sh` | WebUI integration tests | 229 |

### 2. Git Submodule Setup (WebUI)

The project uses a git submodule for WebUI. Before building, initialize the submodule:

```bash
# From project root
git submodule update --init --recursive

# Or use the helper script
../update-webui-submodule.sh
```

### 3. Running Tests

#### Prerequisites

- PostgreSQL 16 + pgvector running on localhost:5432
- Java 21+
- Required API keys in `.env`

#### Run Regression Tests

```bash
cd java/scripts
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

### 3. Test Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_URL` | http://127.0.0.1:37777 | Server URL |
| `DB_HOST` | 127.0.0.1 | Database host |
| `DB_NAME` | claude_mem_dev | Database name |
| `DB_USER` | postgres | Database user |
| `DB_PASS` | 123456 | Database password |
| `OPENAI_API_KEY` | - | OpenAI API key |
| `DEEPSEEK_API_KEY` | - | DeepSeek API key |
| `SPRING_AI_MCP_SERVER_PROTOCOL` | SSE | MCP protocol (SSE or STREAMABLE) |

### 4. MCP Protocol Auto-Detection

The MCP E2E test scripts (`mcp-e2e-test.sh` and `mcp-streamable-e2e-test.sh`) **automatically detect** which protocol your server is running:

- **SSE mode**: `/sse` returns 200, `/mcp` returns 404
- **STREAMABLE mode**: `/mcp` returns 200, `/sse` returns 404

The unified script runs the appropriate tests automatically. No manual protocol selection needed!

- Test session ID: `e2e-regression-{timestamp}`
- Test project: `/tmp/claude-mem-test-{pid}`

### 5. CI/CD Integration

GitHub Actions workflows are configured in `.github/workflows/`:

- `build.yml` - Maven build and test
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
cd java/backend
./mvnw spring-boot:run
```

### Test Failures

1. Check server logs
2. Verify database connection
3. Confirm API keys are set
4. Review test output for specific errors
