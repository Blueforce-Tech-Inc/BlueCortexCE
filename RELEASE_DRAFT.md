# v0.1.0-beta Release Notes

## Overview

**Cortex Community Edition (cortexce)** is a Java Spring Boot port of the Claude-Mem memory system, providing persistent context and intelligent memory management for AI assistants.

This is the **initial beta release** (v0.1.0-beta).

---

## What's New

### Core Features

- **Persistent Memory System** - Save and reuse context across sessions
- **Intelligent Quality Assessment** - Automatically evaluate memory quality
- **Vector-based Semantic Search** - PostgreSQL + pgvector for similarity search
- **RESTful API** - Simple HTTP interface compatible with Claude Code hooks

### Architecture

- **Thin Proxy + Fat Server** architecture to solve CLI Hook timeout issues
- **Spring Boot 3.3** with Java 21 virtual threads
- **PostgreSQL 16** with pgvector for vector storage

### Integration

- **Claude Code Hooks** integration via thin proxy
- **MCP (Model Context Protocol) Server** - 5 tools (search, timeline, get_observations, save_memory, recent)
- **Cursor IDE** integration support
- **OpenClaw Plugin** for Java backend

### API Compatibility

- WebUI API compatibility (11 endpoints)
- Context API (inject, generate, preview, prior-messages, recent, timeline)
- SSE streaming support

---

## Getting Started

### Prerequisites

- Java 21+
- PostgreSQL 16+ with pgvector extension
- Maven 3.8+

### Quick Start

```bash
# 1. Clone and build
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd claude-mem/java/backend

# 2. Configure environment
cp src/main/resources/application.yml.example src/main/resources/application.yml
# Edit .env with your API keys

# 3. Build
./mvnw clean package -DskipTests

# 4. Run
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar
```

### Docker

```bash
# Using docker-compose
docker compose -f docker-compose.yml up -d

# Or pull pre-built image
docker pull ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:0.1.0-beta
```

---

## Test Results

| Test Suite | Status |
|------------|--------|
| WebUI Integration Tests | ✅ 11/11 PASSED |
| Regression Tests | ✅ 20/20 PASSED |
| Thin Proxy Tests | ✅ 28/28 PASSED |
| MCP E2E Tests | ✅ 16/16 PASSED |

---

## Known Issues

- No Redis integration (deferred to future)
- No resume capability (SDK not ported)
- AgentService needs refactoring (SRP)

---

## What's Next

See [TASK_TRACKER.md](./TASK_TRACKER.md) for upcoming features:

- **High Priority**: AgentService SRP Refactor, Race Condition Prevention
- **Medium Priority**: Caffeine Cache, N+1 Query Optimization
- **Low Priority**: API Documentation, Unit Tests

---

## Links

- Documentation: https://docs.claude-mem.ai
- GitHub: https://github.com/Blueforce-Tech-Inc/BlueCortexCE
- Docker Hub: ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce

---

## Contributors

Thanks to all contributors who made this release possible!

---

## Full Changelog

Compare with previous commits: https://github.com/Blueforce-Tech-Inc/BlueCortexCE/compare/...v0.1.0-beta
