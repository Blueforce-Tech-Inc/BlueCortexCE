# Cortex Community Edition

> 🧠 **Your AI assistant works hard every session. Then you close the window — and it forgets everything.**
>
> Cortex CE gives Claude Code (and any MCP-compatible AI tool) persistent memory across sessions. Built on a stable "Thin Proxy + Async Backend" architecture — so your session never blocks, never stalls, never loses work.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![Website](https://img.shields.io/badge/website-cortex.ablueforce.com-blue)](https://cortex.ablueforce.com)

[中文](README-zh-CN.md) | English

---

## Why Cortex CE?

| | **Cortex CE** |
|---|---|
| Hook behavior | Thin proxy — returns in <1s, always |
| Session blocking | Fully async — session never waits |
| Multi-client | Claude Code, Cursor, VS Code, any MCP client |
| License | **MIT — no deployment restrictions** |
| Storage | PostgreSQL + pgvector (production-grade) |

---

## Key Features

- 🧠 **Persistent Memory** — Save and reuse context across sessions and projects
- ⚡ **Non-blocking Architecture** — Thin proxy hook returns in <1s; all heavy lifting runs async
- 🔍 **Hybrid Search** — Full-text (PostgreSQL tsvector) + vector (pgvector)
- 🔌 **Multi-client MCP Support** — Works with Claude Code, Cursor, and any MCP tool
- 🐘 **Production-grade Storage** — PostgreSQL + pgvector
- 🔓 **MIT Licensed** — Free for personal and commercial use

---

## Quick Start

### Option 1: Docker (Recommended — fastest way to get started)

```bash
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd BlueCortexCE
cp .env.example .env
# Edit .env with your configuration

docker compose up -d

# Verify service is running
curl http://localhost:37777/api/health
```

**Up and running in 5 minutes!** 🎉

### Option 2: Manual (Java 21+, PostgreSQL 16+)

```bash
# Prerequisites
- Java 21+
- PostgreSQL 16+ with pgvector extension
- Maven 3.8+

# Clone and build
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd BlueCortexCE/backend
mvn clean install

# Run
java -jar target/cortex-ce-*.jar
```

---

## Architecture

```
┌─────────────────┐
│   CLI Hook      │  ← returns {"queued":true} in <1s
│  (wrapper.js)   │    never blocks your session
└────────┬────────┘
         │ HTTP (fire-and-forget)
         ▼
┌─────────────────┐         ┌──────────────────┐
│  Thin Proxy     │         │  Fat Server      │
│  (Express)      │────────▶│  (Spring Boot)   │
└─────────────────┘         │  - LLM Processing│
                            │  - Embedding     │
                            └────────┬─────────┘
                                     │
                            ┌────────▼─────────┐
                            │  PostgreSQL      │
                            │  + pgvector      │
                            └──────────────────┘
```

### Core Components

- **Thin Proxy**: Lightweight Node.js proxy for fast CLI Hook response (<1s)
- **Fat Server**: Spring Boot backend with async LLM/embedding processing
- **PostgreSQL + pgvector**: Unified storage for vectors, relations, and full-text search

---

## Common Pain Points vs Our Solution

| Common Pain Point | Our Solution |
|-------------------|--------------|
| Hook timeout | **Thin Proxy architecture** — always returns <1s |
| Complex ops | **Single PostgreSQL** — vector + relations + FTS |
| SDK dependency | **No SDK** — direct API calls |
| Data loss on crash | **Crash Recovery** — pending queue + auto-retry |
| Limited dimensions | **Multi-dimension vectors** — 768/1024/1536 |

---

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_PASSWORD` | PostgreSQL password | - |
| `SPRING_AI_OPENAI_API_KEY` | LLM API key | - |
| `SPRING_AI_OPENAI_BASE_URL` | LLM API endpoint | - |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding model | `BAAI/bge-m3` |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | Vector dimensions | `1024` |
| `MEMORY_REFINE_ENABLED` | Enable memory evolution | `true` |

For complete configuration, see [`.env.example`](.env.example)

---

## API Overview

### Core Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/health` | GET | Health check |
| `/api/ingest/tool-use` | POST | Record tool execution |
| `/api/ingest/session-end` | POST | Session end processing |
| `/api/memory/experiences` | POST | Retrieve experiences (ExpRAG) |
| `/api/memory/icl-prompt` | POST | Build ICL prompt |
| `/api/memory/refine` | POST | Trigger memory refinement |

For complete API documentation, see [API.md](docs/API.md)

---

## IDE Integrations

### Claude Code

Integrated via CLI Hooks. See [End User Quick Setup Guide](#end-user-quick-setup-guide) above.

### Cursor IDE

See [Cursor Integration Guide](proxy/CURSOR-INTEGRATION.md)

### OpenClaw

See [OpenClaw Integration Guide](openclaw-plugin/OPENCLAW-INTEGRATION.md)

---

## Development

### Build & Test

```bash
mvn compile    # compile
mvn test       # run tests
mvn package    # build jar
```

### Project Structure

```
cortexce/
├── backend/           # Spring Boot application (Java 21)
├── proxy/             # Thin Proxy (Node.js)
├── openclaw-plugin/   # OpenClaw integration
└── docs/              # Documentation
```

---

## Troubleshooting

### Service not responding
```bash
curl http://localhost:37777/api/health
lsof -i :37777  # check port usage
```

### Hook not triggering
- Verify `.claude/settings.local.json` path is correct
- Check that `matcher` matches the tool names you're using

### Database connection failed
- Confirm `DB_PASSWORD` is set in `.env`
- Default `POSTGRES_PORT` is `5433` (to avoid conflict with local PostgreSQL)

### No memories appearing
```bash
curl -N http://localhost:37777/stream
docker exec cortex-ce-postgres psql -U postgres -d claude_mem \
  -c "SELECT COUNT(*) FROM mem_observations;"
```

---

## Roadmap

### ✅ Completed (Cortex CE)
- [x] Core thin proxy + async backend architecture
- [x] PostgreSQL + pgvector hybrid search
- [x] Claude Code hooks integration
- [x] MCP SSE server
- [x] Docker deployment

### 🚧 In Progress (Cortex CE)
- [ ] One-command installer
- [ ] WebUI improvements

### 🔒 Enterprise Features (Coming Soon)
- Team shared memory layer
- Multi-tenant support
- Hosted cloud option

---

## Contributing

We welcome all forms of contribution!

### 🐛 Report Bugs
- [GitHub Issues](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)

### 💡 Feature Requests
- [GitHub Discussions](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/discussions)

### 🔧 Code Contributions
```bash
# 1. Fork the project
# 2. Create a feature branch
git checkout -b feature/amazing-feature

# 3. Commit your changes
git commit -m 'Add amazing feature'

# 4. Push to GitHub
git push origin feature/amazing-feature

# 5. Submit a Pull Request
```

### 📝 Documentation
- Improve API docs
- Translate to other languages
- Fix typos

---

## License

MIT License — free for personal and commercial use. See [LICENSE](LICENSE) for details.

---

## Contact

- 🌐 Website: [https://cortex.ablueforce.com](https://cortex.ablueforce.com)
- 💬 GitHub Discussions: [https://github.com/Blueforce-Tech-Inc/BlueCortexCE/discussions](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/discussions)
- 🐛 Issues: [https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)

---

*Built with ❤️ by [Blueforce Tech Inc.](https://ablueforce.com)*
