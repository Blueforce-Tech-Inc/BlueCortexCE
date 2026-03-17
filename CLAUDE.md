# Claude-Mem Java (Spring Boot Port)

Java 21 / Spring Boot 3.3.13 port of the claude-mem worker service. Replaces the TypeScript/Bun/SQLite stack with PostgreSQL 16 + pgvector for vector search.

## Background & Motivation

原版 TypeScript 实现在 CLI hooks 内执行重量级处理（LLM 调用、向量嵌入等），导致超时问题。Java 版本采用 **"Thin Proxy + Fat Server"** 架构：

- **Thin Proxy (Node.js)**: 轻量级转发器，接收 hook 事件后立即退出 (< 200ms)
- **Fat Server (Java)**: 持久化后台服务，异步处理所有业务逻辑、LLM 调用和存储

### Migration Status

| Component | Status | Notes |
|-----------|--------|-------|
| Core Pipeline (Ingestion → LLM → Embedding → DB) | ✅ Complete |
| Context Generation (CLAUDE.md injection) | ✅ Complete |
| Context API (6 endpoints) | ✅ Complete | inject, generate, preview, prior-messages, recent, timeline |
| WebUI API Compatibility (11 endpoints) | ✅ Complete (11/11 tests pass) |
| SSE Streaming | ✅ Complete |
| Settings API | ✅ Complete | Full persistence to ~/.claude-mem/settings.json |
| Logs API (`/api/logs`, `/api/logs/clear`) | ✅ Complete |
| Memory API (`/api/memory/save`) | ✅ Complete | REST + MCP save_memory tool |
| Code Review V9/V10 | ✅ Verified (all P0/P1 false positives) |
| Session ID Architecture | ✅ Simplified (no SDK, no dual-ID issues) |
| SessionStart Hook (compact-only fix) | ✅ Complete |
| Pending Message Deduplication (V6) | ✅ Complete (tool_input_hash) |
| MCP Server (SSE protocol) | ✅ Complete | Spring AI MCP, 5 tools (search, timeline, get_observations, save_memory, recent) |
| Cursor IDE Integration | ✅ Complete | Thin Proxy support |
| TS Alignment (Context Injection) | ✅ Complete | JSON stdout, folder CLAUDE.md env |
| OpenClaw Plugin | ✅ Complete | Java backend version (~380 lines TypeScript) |
| Batch Session Query API | ✅ Complete | `/api/sdk-sessions/batch` endpoint + export script |
| Docker Support | ✅ Complete | Dockerfile, docker-compose.yml, GitHub Actions CI/CD |
| Anthropic LLM Support | ✅ Complete | Spring AI unified abstraction (Anthropic-compatible API) |

### Test Results Summary

| Test Suite | Status | Last Run |
|------------|--------|----------|
| WebUI Integration Tests | ✅ 11/11 PASSED | 2026-02-23 |
| Regression Tests | ✅ 20/20 PASSED | 2026-02-23 |
| Thin Proxy Tests | ✅ 28/28 PASSED | 2026-02-23 |
| MCP E2E Tests | ✅ 16/16 PASSED | 2026-02-23 |

### Context API (2026-02-15 新增)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/context/recent` | GET | 获取最近会话上下文摘要 |
| `/api/context/timeline` | GET | 获取时间线上下文（支持 anchorId/query 锚点） |

### Folder CLAUDE.md 更新时序问题修复 (2026-02-15)

| Issue | Solution |
|-------|----------|
| Java 异步处理导致 API 返回时 observation 未写入 | 添加 3 秒延迟确保异步处理完成 |
| 文件夹级 CLAUDE.md 更新时出现竞态条件 | proxy/wrapper.js 中使用 setTimeout 延迟调用 |

详见 `docs/drafts/context-injection-hooks-analysis.md`

## Related Documents

相关文档位于 `docs/drafts/`:

| Document | Description |
|----------|-------------|
| `token-economics-alignment-report.md` | **Token 经济学对齐报告** (2026-02-24，discovery_tokens 计算) |
| `java-port-feature-alignment-report.md` | **TS vs Java 功能对齐报告** (2026-02-15 更新) |
| `alignment-progress.md` | **TS vs Java 对齐进度** (2026-02-15 完成) |
| `webui-integration-progress.md` | **WebUI 集成进度跟踪** (最新，11/11 测试通过) |
| `java-mcp-server-implementation-plan.md` | **MCP Server 实现计划** (2400+ 行) |
| `mcp-server-value-analysis.md` | MCP Server 价值分析 |
| `java-session-id-analysis.md` | Session ID 架构调研 (2026-02-14) |
| `code-review-2026-02-13-v10.md` | 代码审查报告 + 批判 (P0/P1 均误报) |
| `java-rewrite-plan.md` | 整体重写计划和架构设计 |
| `java-rewrite-cookbook.md` | 详细实现步骤和代码示例 |
| `webui-java-integration-feasibility-report.md` | WebUI 集成可行性分析 |
| `java-api-endpoints-analysis.md` | API 端点对比分析 |
| `context-injection-hooks-analysis.md` | **Claude Code Hooks 上下文注入机制分析** (2026-02-15 含调试方案) |

## Next Tasks

详见 `TASK_TRACKER.md`:

### High Priority

| Task | Effort | Description |
|------|--------|-------------|
| AgentService SRP Refactor | Large | 拆分为 ObservationService, SummaryService, SessionService, TemplateService |
| Race Condition Prevention | Medium | 添加 DB 唯一约束防止并发插入重复数据 |

### Medium Priority

| Task | Effort | Description |
|------|--------|-------------|
| Caffeine Cache Layer | Medium | 添加内存缓存层减少 DB 查询 |
| N+1 Query Optimization | Medium | 优化 ContextService 中的查询 |
| Context Preview Filter Fix | Small | /api/context/preview 支持 filter 参数 |

### Low Priority

| Task | Effort | Description |
|------|--------|-------------|
| API Documentation | Small | 添加 OpenAPI/Swagger 文档 |
| Unit Test Coverage | Medium | 增加单元测试覆盖率 |
| LLM Timeout Config | Small | 添加可配置的 LLM 调用超时 |

## Architecture

| Component | Technology |
|-----------|------------|
| Runtime | Java 21 (virtual threads) |
| Framework | Spring Boot 3.3.13 |
| Database | PostgreSQL 16 + pgvector 0.8.1 |
| Migrations | Flyway |
| LLM | DeepSeek (OpenAI-compatible API) |
| Embeddings | SiliconFlow BAAI/bge-m3 (1024-dim) |
| MCP Server | Spring AI MCP Server (WebMVC) |

### Core Pipeline

```
Tool-Use Event → IngestionController → AgentService (async)
  → LlmService (DeepSeek chat completion)
  → XmlParser (extract observation XML)
  → EmbeddingService (SiliconFlow bge-m3)
  → PostgreSQL (observation + 1024-dim vector)
```

### Multi-Dimension Embeddings

The `mem_observations` table supports 3 embedding dimensions (all nullable):

- `embedding_768 vector(768)` — HNSW indexed
- `embedding_1024 vector(1024)` — HNSW indexed (default, used by bge-m3)
- `embedding_1536 vector(1536)` — HNSW indexed (max 2000 for pgvector index)
- `embedding_model_id` — tracks which model generated the embedding

## Project Structure

```
java/
├── claude-mem-java/                 # Main Spring Boot application
│   ├── src/main/java/com/claudemem/server/
│   │   ├── ClaudeMemApplication.java         # Main entry point
│   │   ├── config/
│   │   │   ├── AsyncConfig.java             # @EnableAsync with virtual threads
│   │   │   ├── SpringAiConfig.java          # ChatModel/EmbeddingModel beans
│   │   │   ├── QueueHealthIndicator.java     # Stale message health check
│   │   │   └── WebConfig.java               # Web configuration
│   │   ├── controller/
│   │   │   ├── IngestionController.java      # Hook event endpoints
│   │   │   ├── ViewerController.java         # Viewer API endpoints
│   │   │   ├── StreamController.java         # SSE streaming
│   │   │   ├── SessionController.java       # Session management
│   │   │   ├── ContextController.java       # Context retrieval
│   │   │   ├── LogsController.java          # Logs API (WebUI compatibility)
│   │   │   └── TestController.java          # Debug/test endpoints
│   │   ├── entity/
│   │   │   ├── SessionEntity.java
│   │   │   ├── ObservationEntity.java        # 3 embedding vector fields
│   │   │   ├── SummaryEntity.java
│   │   │   ├── UserPromptEntity.java
│   │   │   └── PendingMessageEntity.java
│   │   ├── repository/
│   │   │   ├── SessionRepository.java
│   │   │   ├── ObservationRepository.java    # Dimension-specific semantic search
│   │   │   ├── SummaryRepository.java
│   │   │   ├── UserPromptRepository.java
│   │   │   └── PendingMessageRepository.java
│   │   ├── service/
│   │   │   ├── AgentService.java             # Core orchestration: LLM → parse → embed → save
│   │   │   ├── LlmService.java              # DeepSeek / OpenAI-compatible API client
│   │   │   ├── EmbeddingService.java         # SiliconFlow embedding API client
│   │   │   ├── SearchService.java            # Semantic + text search with dimension routing
│   │   │   ├── SSEBroadcaster.java           # Server-Sent Events broadcasting
│   │   │   ├── ContextService.java           # Context retrieval for Claude Code
│   │   │   ├── ContextCacheService.java      # LRU cache for context
│   │   │   ├── TimelineService.java          # Timeline context (REST + MCP unified)
│   │   │   ├── ClaudeMdService.java          # CLAUDE.md generation
│   │   │   ├── RateLimitService.java         # Rate limiting per session
│   │   │   ├── TokenService.java             # Token counting
│   │   │   ├── ProjectFilterService.java     # Project path filtering
│   │   │   └── StaleMessageRecoveryTask.java # Crash recovery + deduplication
│   │   ├── util/
│   │   │   ├── XmlParser.java                # Regex XML parser for LLM output
│   │   │   ├── VectorValidator.java          # Vector validation utilities
│   │   │   └── SessionStatus.java            # Status constants (active/completed/etc)
│   │   ├── common/
│   │   │   ├── LogHelper.java                # Structured logging utilities
│   │   │   └── LogMarkers.java               # Custom log markers
│   │   ├── config/
│   │   │   ├── MdcAutoFilter.java            # MDC auto-fill filter
│   │   │   └── Constants.java               # Application-wide constants
│   │   ├── logging/
│   │   │   └── ClaudeMemLogAppender.java    # Custom log appender
│   │   └── exception/
│   │       ├── RetryableException.java
│   │       └── DataValidationException.java
│   ├── src/main/resources/
│   │   ├── application.yml                    # All configuration
│   │   ├── db/migration/
│   │   │   ├── V1__init_schema.sql          # Base schema (5 tables)
│   │   │   ├── V2__multi_dimension_embeddings.sql
│   │   │   ├── V3__add_skipped_status.sql
│   │   │   ├── V4__context_caching.sql
│   │   │   └── V5__user_prompt_project.sql  # WebUI compatibility
│   │   │   └── V6__pending_message_hash.sql  # Deduplication (tool_input_hash + index)
│   │   │   └── V7__remove_embedding_3072.sql # Remove 3072-dim (pgvector index limit)
│   │   └── prompts/
│   │       ├── init.txt                      # System prompt for memory observer
│   │       ├── observation.txt               # User prompt template for tool events
│   │       ├── summary.txt                   # Summary generation prompt
│   │       └── continuation.txt              # Continuation prompt
│   └── pom.xml                               # Maven configuration
├── proxy/                                    # Thin Proxy (CLI wrapper)
│   ├── wrapper.js                            # CLI entry point for Claude Code hooks (+x)
│   ├── proxy.js                              # HTTP proxy server (optional)
│   ├── package.json                          # Node.js dependencies (axios)
│   ├── README.md                             # Proxy design document
│   └── CLAUDE-CODE-INTEGRATION.md            # Claude Code hooks integration guide
├── scripts/
│   ├── regression-test.sh                    # API regression tests
│   ├── thin-proxy-test.sh                    # Thin proxy integration tests
│   └── webui-integration-test.sh             # WebUI API compatibility tests
├── reviews/
│   └── (Code review artifacts)
└── TASK_TRACKER.md                           # Feature tracking

```

## Configuration

Environment variables or `.env` file:

```bash
# LLM - Option 1: DeepSeek (OpenAI-compatible)
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-chat

# LLM - Option 2: Anthropic-compatible API
ANTHROPIC_API_KEY=sk-ant-xxx
ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_MODEL=claude-sonnet-4-20250514

# Embedding
SILICONFLOW_API_KEY=sk-xxx
SILICONFLOW_MODEL=BAAI/bge-m3
SILICONFLOW_DIMENSIONS=1024
SILICONFLOW_URL=https://api.siliconflow.cn/v1/embeddings

# Database
DB_USERNAME=postgres
DB_PASSWORD=123456
```

**Note:** Both LLM providers are supported via Spring AI's unified abstraction. Set either DeepSeek or Anthropic variables (not both).

Database defaults in `application.yml`:
- URL: `jdbc:postgresql://127.0.0.1/claude_mem_dev`

## Build & Run

```bash
cd backend
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
./mvnw clean package -DskipTests
java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar
```

Server starts on `http://127.0.0.1:37777`.

## Docker Deployment

### Quick Start with Docker Compose

```bash
# 1. Create .env file from template
cp docker-compose.yml docker-compose.prod.yml
cp .env.example .env

# 2. Edit .env with your configuration
vim .env

# 3. Start services
docker compose -f docker-compose.prod.yml up -d

# 4. Check health
curl http://localhost:37777/actuator/health
```

### Build Docker Image

```bash
# Build image
docker build -t claude-mem-java:latest -f Dockerfile .

# Or use pre-built image from GHCR
docker pull ghcr.io/wubuku/claude-mem-java:latest
```

### Docker Compose Services

| Service | Image | Port |
|---------|-------|------|
| claude-mem | ghcr.io/wubuku/claude-mem-java:latest | 37777 |
| postgres | pgvector/pgvector:pg16 | 5433 |

### Environment Variables (Docker)

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_PASSWORD` | PostgreSQL password | (required) |
| `SPRING_AI_OPENAI_API_KEY` | LLM API key | - |
| `SPRING_AI_OPENAI_BASE_URL` | LLM API base URL | https://api.openai.com |
| `SPRING_AI_OPENAI_CHAT_MODEL` | Chat model | gpt-4o |
| `IMAGE_NAME` | Docker image to use | ghcr.io/wubuku/claude-mem-java:latest |

### Production Features

- **Multi-stage build**: Optimized image size (~150MB)
- **Non-root user**: Security hardened
- **Health checks**: Built-in health monitoring
- **ZGC**: Low-latency garbage collector configured
- **WebUI included**: Built-in viewer interface

## API Endpoints

### Ingestion (hook events)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/ingest/session-start` | Initialize session |
| POST | `/api/ingest/user-prompt` | Record user prompt |
| POST | `/api/ingest/tool-use` | Enqueue tool-use → async LLM → observation |
| POST | `/api/ingest/observation` | Direct observation creation (with auto-embedding) |
| POST | `/api/ingest/session-end` | Complete session + async summary |

### Viewer API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/observations` | Paginated observations |
| GET | `/api/summaries` | Paginated summaries |
| GET | `/api/prompts` | Paginated user prompts |
| GET | `/api/projects` | List projects |
| GET | `/api/stats` | Database statistics |
| GET | `/api/search` | Semantic + text search |
| GET | `/api/search/by-file` | Search observations by file/folder path (TS alignment) |
| GET | `/api/processing-status` | Queue status |
| POST | `/api/memory/save` | Manual memory save (REST endpoint) |
| POST | `/api/sdk-sessions/batch` | Batch query sessions by memory session IDs |

### Context API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/context/observations` | Get relevant observations |
| POST | `/api/context/summaries` | Get relevant summaries |
| POST | `/api/context/continuation` | Get continuation suggestions |
| GET | `/api/context/recent` | Get recent session context summaries |
| GET | `/api/context/timeline` | Get timeline context (supports anchorId/query) |

### SSE Stream

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/stream` | Real-time observation/summary events |

## Key Services

### AgentService
Core orchestration service that:
1. Receives tool-use events asynchronously
2. Builds prompts from templates
3. Calls LLM (DeepSeek) via LlmService
4. Parses XML responses with XmlParser
5. Generates embeddings with EmbeddingService
6. Persists observations/summaries to PostgreSQL

### EmbeddingService
Handles embedding generation via SiliconFlow API:
- Model: BAAI/bge-m3 (1024 dimensions by default)
- Supports multiple dimensions (768, 1024, 1536)
- Vector stored in PostgreSQL with pgvector

### SearchService
Semantic search with dimension routing:
- Cosine similarity search via pgvector
- Text search fallback (LIKE/ILIKE)
- Filters by project path

### ContextService
Retrieves relevant context for Claude Code:
- Searches observations/summaries by project
- Returns formatted context for injection

### LlmService
OpenAI-compatible API client for DeepSeek:
- Chat completions
- Configurable model and base URL
- Error handling and retries

## Database Schema

Key tables:
- `mem_sessions` - Session tracking
- `mem_observations` - Observations with 4 vector columns
- `mem_summaries` - Session summaries
- `mem_user_prompts` - User prompts
- `mem_pending_messages` - Crash-recovery queue (with deduplication via tool_input_hash)

## Important Notes

- Uses Spring Boot with virtual threads (`spring.threads.virtual.enabled=true`)
- Async processing via `@Async` with virtual threads
- Flyway migrations auto-apply on startup
- SSE broadcasting for real-time updates
- Stale message recovery task runs every 5 minutes
- Rate limiting: 10 requests/60s per session
- Structured logging with MDC context propagation

## Architecture Comparison: TypeScript vs Java

| Aspect | TypeScript (Original) | Java (Port) |
|--------|---------------------|-------------|
| SDK Dependency | `@anthropic-ai/claude-agent-sdk` | None |
| LLM | Claude (SDK-managed) | DeepSeek (direct API) |
| Session ID | Dual ID (contentSessionId + memorySessionId) | Single ID (unified) |
| Resume | SDK provides | Not implemented |
| Race Conditions | DB unique constraints needed | Same, pending implementation |
| Status Enum | DB enum type | Java enum (pending) |

### Java Port Advantages

1. **No SDK complexity**: Direct API calls, no SDK state management
2. **No dual-ID issues**: `memorySessionId` set at initialization
3. **Simpler crash recovery**: Stale message queue (no SDK resume)
4. **Lower memory footprint**: No embedded SDK subprocess

## Thin Proxy Architecture

The `proxy/wrapper.js` is a **CLI wrapper**, NOT a long-running HTTP server. Each hook trigger spawns a short-lived process that handles one hook event.

```
Claude Code Hooks → wrapper.js (CLI, short-lived per hook)
                   │
                   └─→ HTTP POST to Java API (localhost:37777)
                       │
                       └─→ Spring Boot Service (long-running)
                           - Processes hook events
                           - Writes context to CLAUDE.md
```

### Hook Events → API Endpoints

| Hook Event | CLI Command | API Endpoint |
|------------|-------------|--------------|
| `SessionStart` | `wrapper.js session-start` | `/api/ingest/session-start` |
| `UserPromptSubmit` | `wrapper.js user-prompt` | `/api/ingest/user-prompt` |
| `PostToolUse` | `wrapper.js observation` | `/api/ingest/tool-use` |
| `Stop` / `SessionEnd` | `wrapper.js summarize` | `/api/ingest/session-end` |

### Exit Codes

| Code | Meaning | Usage |
|------|---------|-------|
| 0 | Success/Continue | Normal completion |
| 1 | Non-blocking error | Logged, Claude continues |
| 2 | Blocking error | Fed to Claude for handling |

### SessionStart Behavior

The `SessionStart` hook processes on **ALL** session-start events (fresh start + compact), aligned with TypeScript version behavior. Log output indicates event type:
- `Session start (fresh start)` - Normal session initialization
- `Session start (compact)` - During compaction

See `proxy/README.md` for detailed integration guide.

## MCP Server

Java 版本实现了完整的 MCP (Model Context Protocol) Server，使用 Spring AI MCP (WebMVC) 通过 SSE 协议。

### 配置

#### 方式一：CLI 命令添加 (推荐 ✅)

```bash
# 添加 SSE 类型的 MCP 服务器
claude mcp add --transport sse claude-mem-java http://127.0.0.1:37777/sse

# 查看已配置的服务器
claude mcp list

# 使用 /mcp 命令查看
/mcp
```

#### 方式二：项目级 `.mcp.json` (可能不生效)

> ⚠️ **重要提示**：项目级 `.mcp.json` 可能不会自动加载！请优先使用方式一。

项目根目录 `.mcp.json`:
```json
{
  "mcpServers": {
    "claude-mem-java": {
      "type": "sse",
      "url": "http://127.0.0.1:37777/sse"
    }
  }
}
```

如果 `/mcp` 看不到服务器，请执行：`claude mcp add --transport sse claude-mem-java http://127.0.0.1:37777/sse`

### MCP Tools

| Tool | Description |
|------|-------------|
| `__IMPORTANT` | 3-layer workflow 文档 (优先工具) |
| `search` | 语义搜索，返回 ID 列表 |
| `timeline` | 获取上下文时间线 |
| `get_observations` | 批量获取完整 observation 详情 |
| `save_memory` | 手动保存记忆 |
| `recent` | 获取最近会话上下文摘要 |

### 架构说明

MCP Server 是记忆系统的 "Layer 3"：

```
Layer 1-2: Context Injection (Hook + automatic)
  - SessionStart Hook 自动注入 timeline + summary
  - ~500-1000 tokens, 提供概览

Layer 3: MCP Server (on-demand query)
  - AI 需要完整详情时调用 MCP tools
  - get_observations 获取完整详情 (~500-1000 tokens/item)
```

### 相关文档

- `docs/drafts/java-mcp-server-implementation-plan.md` - 实现计划 (2400+ 行)
- `docs/drafts/mcp-server-value-analysis.md` - MCP 价值分析

### E2E 测试

```bash
./scripts/mcp-e2e-test.sh
```

## Cursor IDE Integration

Java 版本支持 Cursor IDE 集成，通过 Thin Proxy 架构实现。

### 集成方式

- **CursorService.java**: 处理 Cursor IDE 特定请求
- **CursorController.java**: Cursor IDE API 端点
- **proxy/CURSOR-INTEGRATION.md**: 集成文档

### Thin Proxy 支持

与 Claude Code 相同的 Thin Proxy 架构：
- wrapper.js 处理 Cursor hook 事件
- HTTP 转发到 Java 后端 (localhost:37777)
- 异步处理，快速响应 (< 200ms)

## WebUI Integration

Java 后端 API 已完全兼容 TypeScript 原版 WebUI。详见 `docs/drafts/webui-integration-progress.md`。

### API 兼容性状态

| API | Status | Notes |
|-----|--------|-------|
| `/api/observations` | ✅ | offset/limit + items/hasMore |
| `/api/summaries` | ✅ | offset/limit + items/hasMore |
| `/api/prompts` | ✅ | offset/limit + items/hasMore |
| `/api/stats` | ✅ | {worker, database} 嵌套结构 |
| `/api/projects` | ✅ | {projects: [...]} |
| `/api/processing-status` | ✅ | {isProcessing, queueDepth} |
| `/api/stream` (SSE) | ✅ | type 字段 + camelCase |
| `/api/context/preview` | ✅ | 返回纯文本 (text/plain) |
| `/api/settings` | ⏳ | 占位符实现 |

### 测试脚本

```bash
# WebUI API 兼容性测试
./scripts/webui-integration-test.sh

# 回归测试 (19 tests)
./scripts/regression-test.sh

# Thin Proxy 集成测试
./scripts/thin-proxy-test.sh
```

### 导出脚本

```bash
# 基本用法 - 搜索记忆
./scripts/export-memories.sh --query "feature"

# 带项目过滤
./scripts/export-memories.sh --project /path/to/project --output backup.json

# 自定义数量
./scripts/export-memories.sh --query "*" --limit 500
```

## OpenClaw Integration

Java 版本支持 OpenClaw Gateway，通过独立的 OpenClaw 插件连接到 Java 后端。

### 测试脚本

```bash
# OpenClaw 插件集成测试
./scripts/openclaw-plugin-test.sh
```

### 架构

```
OpenClaw Gateway
└── Claude-Mem Java Plugin (openclaw-plugin/)
    ├── HTTP Client → Java Backend (localhost:37777)
    ├── MEMORY.md Sync (workspace 目录)
    └── Observation Recording (7 个事件监听)
```

### 与 TypeScript 版本差异

| 特性 | TypeScript 版本 | Java 版本 |
|------|---------------|----------|
| 后端 | TypeScript Worker | Java Spring Boot |
| SSE 支持 | ✅ 有 | ❌ 无 (保持 Thin Proxy 简洁) |
| API 端点 | TS 格式 | Java 适配格式 |

### API 端点映射

| 功能 | TypeScript OpenClaw | Java OpenClaw 插件 |
|------|---------------------|-------------------|
| 会话初始化 | `/api/sessions/init` | `/api/session/start` |
| 记录工具使用 | `/api/sessions/observations` | `/api/ingest/tool-use` |
| 会话完成 | `/api/sessions/complete` | `/api/ingest/session-end` |
| 获取 Timeline | `/api/context/inject` | `/api/context/inject` |

### 插件文件

```
java/openclaw-plugin/
├── src/index.ts       # 插件主代码 (~380 行)
├── plugin.json        # 插件配置
├── package.json       # NPM 配置
└── tsconfig.json      # TypeScript 配置
```

### 使用方法

1. **编译插件**:
```bash
cd java/openclaw-plugin
npm install
npm run build
```

2. **配置 OpenClaw**:
在 OpenClaw 配置文件中添加插件：

```json
{
  "plugins": {
    "claude-mem-java": {
      "enabled": true,
      "config": {
        "workerPort": 37777,
        "project": "my-project",
        "syncMemoryFile": true
      }
    }
  }
}
```

3. **可用命令**:
- `/claude-mem-status` - 检查 Java 后端健康状态
- `/claude-mem-projects` - 列出已追踪的项目

### 事件监听

| 事件 | 插件行为 |
|------|----------|
| `session_start` | 初始化会话 |
| `after_compaction` | 压缩后重新初始化 |
| `before_agent_start` | 同步 MEMORY.md |
| `tool_result_persist` | 记录观察 + 同步 MEMORY.md |
| `agent_end` | 生成摘要 + 完成会话 |
| `session_end` | 清理会话跟踪 |
| `gateway_start` | 重置会话跟踪 |

详见 `openclaw-plugin/OPENCLAW-INTEGRATION.md` 和 `docs/drafts/claude-mem-openclaw-support-analysis.md`

## Development Commands

### 环境变量配置

配置文件位于 `backend/.env`:

```bash
# LLM (OpenAI-compatible - DeepSeek)
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-chat

# Embedding (SiliconFlow)
SILICONFLOW_API_KEY=sk-xxx
SILICONFLOW_MODEL=BAAI/bge-m3
SILICONFLOW_DIMENSIONS=1024
SILICONFLOW_URL=https://api.siliconflow.cn
```

### 构建 & 运行

```bash
# 进入项目目录
cd backend

# 构建前清理缓存（避免缓存问题）
./mvnw clean compile package -DskipTests

# 加载环境变量并启动服务
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 服务管理

```bash
# 健康检查
curl http://localhost:37777/actuator/health

# 查看端口占用
lsof -i :37777

# 停止服务 (精准 kill 特定端口)
lsof -ti:37777 | xargs -r kill -9

# 完整重启流程
lsof -ti:37777 | xargs -r kill -9; sleep 1; \
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs); \
java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 测试命令

```bash
# 回归测试 (19 tests) - 需要服务运行
./scripts/regression-test.sh --skip-build

# Thin Proxy 集成测试 (18 tests)
./scripts/thin-proxy-test.sh

# WebUI API 兼容性测试
./scripts/webui-integration-test.sh

# MCP Server E2E 测试
./scripts/mcp-e2e-test.sh
```

## Recent Work

**2026-03-11**: TS-Java 对齐分析 (Mar 2026 变更)

| Task | Status | Description |
|------|--------|-------------|
| MCP Tool 对齐验证 | ✅ Complete | Java MCP tools 与 TS 对齐 (5/5 tools, 无 save_observation) |
| Mode 系统分析 | ⏭️ Skip | Java 使用项目级过滤，无需 mode 系统 |
| Smart-Explore 特性 | ⏭️ Skip | 架构边界 - tree-sitter 保留在 MCP 层，不在 Java 后端 |
| 对齐文档 | ✅ Complete | `docs/drafts/2026-03-11-ts-java-merge-alignment-analysis.md` |

**关键决策**:
- **Smart-Explore 不移植到 Java**: TypeScript 版本添加了基于 tree-sitter 的代码探索工具 (smart_search, smart_unfold, smart_outline)。Java 后端是 REST 服务器，不是 MCP 工具表面。树解析保留在 TypeScript MCP 层，符合 "Thin Proxy + Fat Server" 架构。
- **Mode 系统不移植**: Java 使用项目级过滤而非 mode 系统，这是有效的架构选择。
- **MCP Tools 已对齐**: Java 5 个工具与 TypeScript 完全匹配 (save_observation 已从 TS 移除)。

详见 `docs/drafts/2026-03-11-ts-java-merge-alignment-analysis.md`

---

**2026-02-24**: Token 经济学对齐

| Task | Description |
|------|-------------|
| discovery_tokens 设置 | AgentService.saveObservation() 接收并存储 LLM usage 信息 |
| LlmService 增强 | 添加 chatCompletionWithUsage() 方法，返回 usage metadata |
| TokenService 修复 | 使用 Jackson ObjectMapper 序列化 facts (与 TS JSON.stringify 对齐) |
| Fallback 估算 | 当无 usage 数据时使用 read_tokens * 8 公式 |
| 对齐报告 | 添加 token-economics-alignment-report.md (660 行详细文档) |

详见提交 `ed3252f5`, `da4ed262`, `6480c0e5`

**2026-02-23**: V7 Migration + Concept 精确匹配对齐 TS

| Task | Description |
|------|-------------|
| V7 Migration | 移除 3072 维嵌入支持 (pgvector HNSW/IVFFlat 索引最大 2000 维) |
| Concept 精确匹配 | findByConceptContaining 改为 JSONB array 精确匹配，对齐 TS SessionSearch.ts |
| Folder Path 修复 | findByFolderPath 增加尾部斜杠精确匹配 |
| Debug 参数 | searchByFile 端点添加 debug 参数支持 |
| tool_input 类型修复 | IngestionController 支持 object 类型的 tool_input/tool_response |
| 分发脚本改进 | create-distribution.sh 添加 help 功能、完整路径 sed、排除不必要文件 |

详见提交 `d2f8c5e4`, `c4ad75c5`, V7 migration 相关提交

**2026-02-16**: Docker 生产部署支持 + GitHub Actions CI/CD

| Task | Description |
|------|-------------|
| Docker 支持 | 添加完整 Dockerfile (多阶段构建)、docker-compose.yml 生产配置 |
| GitHub Actions | 添加 Docker build & push workflow (ghcr.io/wubuku/claude-mem-java:latest) |
| WebUI 构建 | Dockerfile 集成 WebUI 构建阶段，开箱即用 |
| 生产配置 | 添加生产级配置 (ZGC、非root用户、健康检查) |

详见提交 `2577ad57`, `6703a89a`, `8400fcf7`

**2026-02-15**: TimelineService 重构 + Context API + 文件夹 CLAUDE.md 修复

| Task | Description |
|------|-------------|
| TimelineService 重构 | 消除 REST 和 MCP 层代码重复，统一业务逻辑 |
| Context API 新端点 | 实现 `/api/context/recent` 和 `/api/context/timeline` |
| Memory API | 实现 `/api/memory/save` REST 端点 |
| 文件夹 CLAUDE.md 时序修复 | 添加 3 秒延迟防止异步处理未完成 |
| MCP Timeline 工具 | 简化 MCP timeline 工具实现 |
| 调试方案文档 | 添加 stderr vs file logging 调试方案 |

详见提交 `852d8205`, `983f1533`, `a42fd6f8`

**2026-02-15**: MCP Server 实现 + Cursor IDE 集成

| Task | Description |
|------|-------------|
| MCP Server 实现 | 使用 Spring AI MCP (WebMVC) 实现完整 MCP Server，SSE 协议 |
| MCP Tools | 5 个工具: `__IMPORTANT`, `search`, `timeline`, `get_observations`, `save_memory` |
| MCP E2E 测试 | 添加 `mcp-e2e-test.sh` 测试脚本 |
| Cursor IDE 集成 | 添加 CursorService.java, CursorController.java，支持 Thin Proxy |
| Prompt 同步 | 添加 `sync-prompts.sh` 脚本同步 TS/Java prompts |

详见提交 `6309a161`, `a9cb24c6`, `e9eae259`, `979483fa`

**2026-02-14**: Hooks 配置修复（关键问题）

| Fix | Description |
|-----|-------------|
| settings.local.json matcher 移除 | `"matcher": "compact"` 导致 SessionStart 只在 compaction 触发，移除后支持所有 session-start 事件 |
| wrapper.js compact-only 检查移除 | Java thin proxy 错误地在 fresh start 时提前退出，现在对齐 TS 行为 |
| wrapper.js 权限修复 | 添加执行权限 (+x) 确保 Claude Code hooks 可执行 |
| V6 迁移: Pending Message Hash | 添加 `tool_input_hash` 列 + 索引，支持去重 |

**2026-02-14**: Structured Logging 基础设施

- 添加 `LogHelper.java` - 结构化日志工具类
- 添加 `LogMarkers.java` - 自定义日志标记
- 添加 `MdcAutoFilter.java` - MDC 自动填充过滤器
- 添加 `ClaudeMemLogAppender.java` - 自定义日志 Appender
- 迁移日志从内存缓冲到文件读取

详见提交 `d5358310`, `bad42f7f`, `660910df`

**2026-02-14**: Context Preview API 修复

- `/api/context/preview` 现在返回纯文本 (text/plain) 而非 JSON
- 添加 filter 支持到 context preview API

详见提交 `7ef42fd7`, `c0db5fe9`

**2026-02-14**: Session ID 架构调研完成

| Finding | Impact |
|---------|--------|
| Java Port 无 SDK 依赖 | 无双 ID 问题，架构简化 |
| `memorySessionId` 初始化即设置 | 无延迟捕获问题 |
| 无 Resume 场景 | 不需要 SDK 级别的状态恢复 |

详见 `docs/drafts/java-session-id-analysis.md`

**2026-02-13**: WebUI API 兼容性完成 (11/11 测试通过)

- ✅ 第九轮代码审查修复 (Constants.java 集中管理常量)
- ✅ 第十轮 API 全面验证 (从 TS 源码逐个检查)
- ✅ V10 代码审查报告分析 (所有 P0/P1 问题均为误报)
- ✅ Logs API 实现 (`/api/logs`, `/api/logs/clear`)

详见 `docs/drafts/webui-integration-progress.md`

### 代码审查验证结果

| 报告问题 | 核实结果 |
|---------|---------|
| P0-001 SQL 注入 | ❌ 误报 - Spring Data JPA 参数化绑定 |
| P0-002 内存耗尽 | ❌ 误报 - 已有 MAX_FULL_OBSERVATIONS=100 限制 |
| P0-003 输入验证缺失 | ❌ 误报 - 已有 isSafeDirectory() 验证 |
| P0-004 XXE 风险 | ❌ 不适用 - 使用正则表达式，非 XML 解析器 |
| P1-001 线程安全 | ❌ 误报 - synchronized 正确保护原子操作 |
<claude-mem-context>
# Recent Activity

### Mar 13, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #59403c | 10:15 AM | 🔵 | Java Backend Environment Configuration | ~? |
| #bf2bab | " | 🔄 | Java backend directory renamed from claude-mem-jav | ~? |

### Mar 11, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #d3d5a2 | 4:59 PM | 🔵 | MCP Tools Implementation Architecture | ~? |

### Mar 5, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #247632 | 6:32 PM | 🔵 | Committed OpenClaw Skill documentation | ~? |
| #cffda9 | 6:18 PM | 🔵 | Staged OpenClaw plugin integration documentation | ~? |
| #b86f7f | 6:16 PM | 🔵 | Git status shows pending changes and untracked fil | ~? |
| #ea832d | " | 🔵 | Updated OpenClaw Plugin Integration Documentation | ~? |
| #4670a2 | 6:08 PM | 🔵 | OpenClaw 主动搜索 Skill 配置文档 | ~? |
| #f029b1 | 1:43 PM | 🔵 | Git status shows pending changes | ~? |
| #32a197 | 11:32 AM | 🔵 | OpenClaw Gateway Integration Documentation | ~? |

### Mar 2, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #587b09 | 10:20 PM | 🔵 | Updated OpenClaw integration documentation | ~? |
| #3ca641 | 1:48 PM | 🔵 | OpenClaw Gateway Integration Documentation | ~? |
| #f2a9a8 | " | 🔵 | Updated OpenClaw plugin configuration schema | ~? |

### Feb 26, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #a8633d | 10:18 AM | 🔵 | Updated Java project CLAUDE.md documentation | ~? |
| #6642f9 | 10:12 AM | 🔵 | Updated Java CLAUDE.md documentation | ~? |
| #8425fd | 10:11 AM | 🔵 | No recent changes to CLAUDE.md | ~? |

### Feb 24, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #d247b5 | 4:37 PM | 🔵 | TokenService Implementation Details | ~? |
| #9ce37d | " | 🔵 | No uncommitted changes in TokenService | ~? |
| #c8b5c9 | 3:02 PM | 🔵 | Observation and Summary Save Methods | ~? |
| #2d6548 | 12:17 AM | 🔵 | Updated Java project CLAUDE.md documentation | ~? |
| #dda4e1 | 12:16 AM | 🔵 | Updated Java project documentation with recent cha | ~? |
| #b03d82 | " | 🔵 | Modified Java documentation file | ~? |
| #ad14dc | 12:15 AM | 🔵 | Improved Java distribution script with better CLI  | ~? |

### Feb 18, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #a647aa | 4:56 PM | 🔵 | Docker configuration and documentation added | ~? |
| #c00798 | 4:55 PM | 🔵 | Staged documentation and Java Docker files for com | ~? |

### Feb 17, 2026

| ID | Time | T | Title | Read |
|----|------|---|-------|------|
| #61b3f6 | 11:32 PM | 🔄 | Removed 3072-dimension embedding support | ~? |
| #6896f5 | 11:31 PM | 🔵 | Staged database migration and code changes | ~? |
| #701a7a | 11:15 PM | 🔵 | Staged Java export-import alignment report and Imp | ~? |
| #9947a4 | 11:11 PM | 🔵 | Added embedding vector support to observation impo | ~? |
| #39b799 | 11:09 PM | 🔵 | ImportService JSON parsing utility method | ~? |
| #588c10 | " | 🔵 | ImportService handles observation data parsing and | ~? |
| #2d29cf | " | 🔵 | Added embedding vector fields to ObservationImport | ~? |
| #1bcd3b | 11:07 PM | 🔵 | Staged Java export/import alignment documentation  | ~? |
| #aa9afb | 11:04 PM | 🔵 | Added transaction management and discovery tokens  | ~? |
| #c50d4c | 11:01 PM | 🔵 | ImportService bulk observation processing logic | ~? |
| #6aa373 | 11:00 PM | 🔵 | Added @Transactional annotation to bulk import end | ~? |
| #e89952 | 10:12 PM | 🔵 | Fixed URL encoding in export script and added test | ~? |
| #fa2c7f | 10:11 PM | 🔵 | Added comprehensive export functionality test suit | ~? |
| #be56d6 | " | 🔵 | Staged Java export script changes | ~? |
| #21bcc5 | 10:10 PM | 🔵 | Fixed URL encoding in export script search API cal | ~? |
| #d37c94 | 7:28 PM | 🔵 | Updated CLAUDE.md with batch session API and expor | ~? |
| #842206 | 7:27 PM | 🔵 | Batch Session Query API Implementation | ~? |
| #4ed714 | 7:24 PM | 🔵 | Batch session query API and export script added | ~? |
| #c8776f | " | 🔵 | Staged Java controller and repository files for co | ~? |
| #cae092 | 7:23 PM | 🔵 | Added batch session query API for export script | ~? |
| #9be4a1 | 5:39 PM | 🔵 | Fixed critical bugs in OpenClaw plugin for Java ba | ~? |
| #ded799 | " | 🔵 | Git status check shows uncommitted changes | ~? |
| #f4d7d9 | 5:37 PM | 🔵 | Updated OpenClaw plugin to parse JSON response fro | ~? |
| #d8368e | 5:30 PM | 🔵 | OpenClaw plugin support added for Java backend | ~? |
| #2190fb | " | 🔵 | OpenClaw Plugin Implementation | ~? |
</claude-mem-context>
