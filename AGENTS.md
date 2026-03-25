# Claude-Mem: AI Development Instructions

Claude-mem is a **Java Spring Boot** implementation of a persistent memory system for AI assistants. It provides persistent context and intelligent memory management across sessions.

## Project Structure

```
cortexce/
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

- `POST /api/ingest` - Ingest messages into memory system
- `POST /api/stream` - Stream processing
- `GET /api/memory/search` - Semantic search
- `GET /api/memory/sessions` - Get session list
- `GET /api/health` - Health check

## Database Schema

- **SessionEntity** - Session records
- **UserPromptEntity** - User prompts
- **ObservationEntity** - Agent observations
- **SummaryEntity** - Summary records
- **PendingMessageEntity** - Pending messages

## Build & Run

```bash
# Build backend
cd backend
mvn clean install -DskipTests

# Run
java -jar target/cortex-ce-*.jar

# Or with Docker
docker-compose up -d
```

## Configuration

Configuration managed via `application.properties` or environment variables:

- `SPRING_DATASOURCE_URL` - PostgreSQL connection
- `SPRING_AI_OPENAI_API-KEY` - OpenAI API Key
- `SERVER_PORT` - Service port (default 37777)

## Integration

### Claude Code
Use `proxy/wrapper.js` as CLI Hook wrapper.

### OpenClaw
Integrated via `openclaw-plugin/`, provides search Skill.

## Requirements

- **Java 21**+
- **PostgreSQL 16+** (with pgvector extension)
- **Maven 3.8+**
- Node.js (for proxy layer)

## Documentation

- `docs/` - Project documentation (stable reference)
- `docs/drafts/` - Active design documents under iteration
- `docs/archive/` - Historical snapshots (read-only, date-prefixed filenames)
- `backend/README.md` - Backend detailed documentation
- `proxy/README.md` - Proxy layer documentation

### Documentation Management Rules

**⚠️ BEFORE creating or moving any .md file**, activate `skills/doc-management/SKILL.md` and classify the document:

| Type | Location | Example |
|------|----------|---------|
| Live (periodically updated) | Appropriate dir + purpose header | `docs/performance-test-results.md` |
| Historical (point-in-time) | `docs/archive/YYYY-MM-DD_<name>.md` | Test reports, completed trackers |
| Draft (under iteration) | `docs/drafts/` | Phase design docs |
| Reference (stable) | `docs/` | API, Architecture docs |

**Never scatter documents** — no `backend/TASK_PROGRESS.md`, no root-level test reports, no duplicates.

## Key Files

- `backend/pom.xml` - Maven configuration
- `backend/src/main/resources/application.properties` - Application configuration
- `docker-compose.yml` - Docker deployment configuration
