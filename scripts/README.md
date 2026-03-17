# Claude-Mem Java Test Scripts

This directory contains test and development scripts for the Claude-Mem Java implementation.

## Quick Reference

| Script | Purpose | Prerequisites |
|--------|---------|---------------|
| `start.sh` | Start Java backend (dev) | Java 21+ |
| `start-all.sh` | Start Java + Thin Proxy | Java 21+, Node.js |
| `deploy-webui.sh` | Deploy WebUI to Java | Node.js |
| `sync-resources.sh` | Sync TS resources to Java | jq |
| `test-llm-provider.sh` | Test LLM/embedding APIs | Running backend |
| `regression-test.sh` | E2E regression tests | PostgreSQL + backend |
| `docker-e2e-test.sh` | Docker deployment tests | Docker |
| `docker-compose-test.sh` | Docker Compose tests | Docker |
| `mcp-e2e-test.sh` | MCP Server tests | Running backend |
| `thin-proxy-test.sh` | Thin Proxy tests | Node.js + backend |
| `webui-integration-test.sh` | WebUI API tests | Running backend |
| `openclaw-plugin-test.sh` | OpenClaw plugin tests | Node.js + backend |
| `export-memories.sh` | Export memory data | jq + running backend |
| `export-test.sh` | Export function tests | jq + running backend |
| `seed-diverse-data.sh` | Generate test data | Running backend |

---

## Startup Scripts

### `start.sh`

Start the Java backend service with dev profile.

**Usage:**

```bash
# Start with existing build
./start.sh

# Build and start
./start.sh --build
```

**Features:**
- Loads environment variables from `.env` file
- Kills existing process on port 8080
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

## Configuration

Override defaults via environment variables:

```bash
export SERVER_URL=http://127.0.0.1:37777
export DB_HOST=127.0.0.1
export DB_NAME=claude_mem_dev
export DB_USER=postgres
export DB_PASSWORD=123456
```

## Prerequisites Summary

| Tool | Required For |
|------|--------------|
| Java 21+ | All scripts |
| Node.js 18+ | start-all.sh, deploy-webui.sh, thin-proxy-test.sh, openclaw-plugin-test.sh |
| Docker | docker-e2e-test.sh, docker-compose-test.sh |
| PostgreSQL 16 + pgvector | regression-test.sh |
| jq | sync-resources.sh, export-memories.sh, export-test.sh |
| curl | All test scripts |
