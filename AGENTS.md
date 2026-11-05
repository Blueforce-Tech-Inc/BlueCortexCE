# Claude-Mem: AI Development Instructions

Claude-mem is a **Java Spring Boot** implementation of a persistent memory system for AI assistants. It provides persistent context and intelligent memory management across sessions.

## Project Structure

```
cortexce/
├── .agents/              # Agent configuration and project-local skills
│   └── skills/           # Agent Skills
├── backend/              # Java Spring Boot backend service
│   └── src/main/java/com/ablueforce/cortexce/
│       ├── controller/   # REST API controllers
│       ├── service/      # Business logic services
│       ├── entity/       # JPA entities
│       ├── repository/   # Data access layer
│       ├── config/       # Spring configuration
│       ├── mcp/          # MCP tool integration
│       └── util/         # Utility classes
├── proxy/                # Node.js proxy layer (wrapper.js)
├── openclaw-plugin/      # OpenClaw plugin integration
│   └── skills/           # Agent Skills
├── docs/                 # Project documentation
└── scripts/              # Scripts and tools
```

## Architecture

**Core Components**:

- **Worker Service** (`backend/`) - Spring Boot 3.3 application, running on port 37777
- **Database** - PostgreSQL + pgvector for vector storage
- **Proxy Layer** (`proxy/wrapper.js`) - Node.js middleware for CLI Hook forwarding
- **OpenClaw Plugin** (`openclaw-plugin/`) - OpenClaw integration plugin
- **Search Skill** - HTTP API for searching historical records

**5 Lifecycle Hooks**: SessionStart → UserPromptSubmit → PostToolUse → Summary → SessionEnd

**Key Services**:

- `ContextService.java` - Core context management service
- `AgentService.java` - Agent lifecycle management
- `SearchService.java` - Vector search service
- `EmbeddingService.java` - Embedding vector generation
- `ModeService.java` - Memory mode management

## API Endpoints

- `POST /api/ingest/tool-use` - Ingest tool-use events into memory system
  (`/user-prompt`, `/observation`, `/session-end` are the sibling routes; there is
  no handler on the bare `POST /api/ingest` path)
- `GET /stream` - SSE stream of memory events
- `GET /api/search` - Search observations (semantic when an embedding model is
  configured, PostgreSQL full-text fallback otherwise)
- `GET /api/sessions` - List sessions
- `GET /api/health` - Health check

> Full, verified endpoint list: `docs/API.md` / `docs/API-zh-CN.md`. Those files are
> checked against the running server by `scripts/check-api-doc-surface.py`.

## Database Schema

- **SessionEntity** - Session records
- **UserPromptEntity** - User prompts
- **ObservationEntity** - Agent observations
- **SummaryEntity** - Summary records
- **PendingMessageEntity** - Pending messages

## Build & Run

```bash
# Build backend (runs the test suite; DB-gated tests skip unless the
# MEMSCOPE_* variables in docs/TESTING.md are set)
cd backend
mvn clean install

# Run
java -jar target/cortex-ce-*.jar

# Or with Docker
docker-compose up -d
```

## Configuration

Configuration is loaded from `backend/src/main/resources/application.yml`
(profile overrides: `application-dev.yml`, `application-prd.yml`) or environment variables:

- `SPRING_DATASOURCE_URL` - PostgreSQL connection
- `SPRING_AI_OPENAI_API_KEY` - OpenAI API Key
- `SERVER_PORT` - Service port (default 37777)

## Integration

### Claude Code
Use `proxy/wrapper.js` as CLI Hook wrapper.

### OpenClaw
Integrated via `openclaw-plugin/`, provides search Skill.

## Requirements

- **Java 21**+ (the test suite is verified on JDK 21 and JDK 25)
- **PostgreSQL 16+** (with pgvector extension)
- **Maven 3.8+**
- Node.js (for proxy layer)

## Documentation

- **`docs/PROJECT-STATE.md`** - Current verified state and handover: test counts,
  gate command, schema decisions, known boundaries. Read this first when resuming work.
- `docs/PROJECT-STATE-zh-CN.md` - Chinese version of the same snapshot.
- `docs/` - Project documentation (stable reference)
- `docs/drafts/` - Active design documents under iteration
- `docs/archive/` - Historical snapshots (read-only, date-prefixed filenames)
- `backend/README.md` - Backend detailed documentation
- `proxy/README.md` - Proxy layer documentation

### Documentation Management Rules

**⚠️ BEFORE creating or moving any .md file**, activate `.agents/skills/doc-management/SKILL.md` and classify the document:

| Type | Location | Example |
|------|----------|---------|
| Live (periodically updated) | Appropriate dir + purpose header | `docs/performance-test-results.md` |
| Historical (point-in-time) | `docs/archive/YYYY-MM-DD_<name>.md` | Test reports, completed trackers |
| Draft (under iteration) | `docs/drafts/` | Phase design docs |
| Reference (stable) | `docs/` | API, Architecture docs |

**Never scatter documents** — no `backend/TASK_PROGRESS.md`, no root-level test reports, no duplicates.

## Key Files

- `backend/pom.xml` - Maven configuration
- `backend/src/main/resources/application.yml` - Application configuration
- `docker-compose.yml` - Docker deployment configuration

## ⚠️ WebUI 子模块 API 契约

WebUI 通过 git 子模块引入（`https://github.com/Blueforce-Tech-Inc/claude-mem.git`），路径 `webui/`。

**修改任何 API 响应字段名前，必须先检查 WebUI 是否引用。**

### 不可修改的 camelCase 字段（WebUI 硬契约）

| 端点 | 字段 | WebUI 引用 |
|------|------|-----------|
| `/api/observations` | `hasMore` | `src/ui/viewer/hooks/usePagination.ts` |
| `/api/summaries` | `hasMore` | 同上 |
| `/api/prompts` | `hasMore` | 同上 |
| `/api/session/start` | `updateFiles` | `proxy/proxy.js` |
| `/api/context/inject` | `updateFiles` | `proxy/proxy.js` |
| `/api/settings` | `CLAUDE_MEM_*` | `src/ui/viewer/hooks/useSettings.ts` |

### 可安全修改为 snake_case 的字段

| 端点 | 字段 | 原因 |
|------|------|------|
| `/api/modes` | `observation_types/observation_concepts` | WebUI 从本地 ModeManager 读取（`ModeManager.getInstance().getActiveMode()`），不读 API 响应 |
| `/api/settings` | `modeName`/`modeDescription` | WebUI 与 proxy 均不引用；`ViewerController` 直接 `response.put` 产出 |

### 检查方式

```bash
# 修改前搜索 WebUI 引用
# 注意 updateFiles 的消费方在 proxy/proxy.js，不在 webui/src/ ——
# 只扫 webui/ 会得到零命中，看起来"没人用"，而它其实是硬契约。
grep -rn "hasMore\|updateFiles\|observationTypes" webui/src/ proxy/
```
