# Claude-Mem Java (Spring Boot Port)

A Java 21 / Spring Boot 3.3.13 port of the claude-mem worker service. Replaces the TypeScript/Bun/SQLite stack with PostgreSQL 16 + pgvector for vector search.

## Architecture

| Component | Technology |
|-----------|-----------|
| Runtime | Java 21 (virtual threads) |
| Framework | Spring Boot 3.3.13 |
| Database | PostgreSQL 16 + pgvector 0.8.1 |
| Migrations | Flyway |
| LLM | DeepSeek (OpenAI-compatible API) |
| Embeddings | SiliconFlow BAAI/bge-m3 (1024-dim) |

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
- `embedding_1536 vector(1536)` — HNSW indexed
- `embedding_model_id` — tracks which model generated the embedding

Note: `embedding_3072` was added in V2 and dropped in V7 (pgvector HNSW limit: max 2000 dims).

## Prerequisites

- Java 21+
- PostgreSQL 16 with pgvector extension
- Maven (wrapper included)

## Configuration

Copy `.env.example` to `.env` (or set environment variables):

```bash
# LLM (OpenAI-compatible)
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-chat

# Embedding
SPRING_AI_OPENAI_EMBEDDING_API_KEY=sk-xxx
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1/embeddings
```

Database defaults in `application.yml`:
- URL: `jdbc:postgresql://127.0.0.1/claude_mem_dev`
- User: `postgres` / Password: `123456`

Override with `DB_USERNAME` and `DB_PASSWORD` env vars.

## Build & Run

```bash
# Load env vars
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)

# Build
./mvnw clean package -DskipTests

# Run (Flyway auto-applies migrations)
java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar
```

Server starts on `http://127.0.0.1:37777`.

## API Endpoints

### Ingestion (hook events)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/session/start` | Initialize session |
| POST | `/api/ingest/user-prompt` | Record user prompt |
| POST | `/api/ingest/tool-use` | Enqueue tool-use → async LLM → observation |
| POST | `/api/ingest/observation` | Direct observation creation (with auto-embedding) |
| POST | `/api/ingest/session-end` | Complete session |

### Viewer API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/observations` | Paginated observations |
| POST | `/api/observations/batch` | Batch observation retrieval |
| GET | `/api/summaries` | Paginated summaries |
| GET | `/api/prompts` | Paginated user prompts |
| GET | `/api/projects` | List projects |
| GET | `/api/stats` | Database statistics |
| GET | `/api/search` | Semantic + text search |
| GET | `/api/search/by-file` | Search observations by file path |
| GET | `/api/timeline` | Timeline context retrieval |
| GET | `/api/processing-status` | Queue status |
| GET | `/api/settings` | Get settings |
| POST | `/api/settings` | Save settings |
| GET | `/api/modes` | Get active mode configuration |
| POST | `/api/modes` | Switch active mode |
| POST | `/api/sdk-sessions/batch` | Batch SDK session operations |

### SSE Stream

| Method | Path | Description |
|--------|------|-------------|
| GET | `/stream` | Real-time observation/summary events |

## End-to-End Test

Tested on 2026-02-11 against PostgreSQL 16.8 + pgvector 0.8.1, DeepSeek (deepseek-chat), SiliconFlow (BAAI/bge-m3).

### Step 1: Health Check

```bash
curl -s http://127.0.0.1:37777/actuator/health | python3 -m json.tool
# {"status": "UP", "components": {"db": {"status": "UP"}, ...}}
```

### Step 2: Create Session

```bash
curl -s -X POST http://127.0.0.1:37777/api/session/start \
  -H 'Content-Type: application/json' \
  -d '{
    "session_id": "test-e2e-002",
    "project_path": "/tmp/test-project",
    "user_prompt": "Implement a REST API for user management"
  }' | python3 -m json.tool
# {"status": "ok", "session_db_id": "42bfc719-...", "content_session_id": "..."}
```

### Step 3: Send Tool-Use Event (triggers LLM + embedding pipeline)

```bash
curl -s -X POST http://127.0.0.1:37777/api/ingest/tool-use \
  -H 'Content-Type: application/json' \
  -d '{
    "session_id": "test-e2e-002",
    "session_db_id": "<SESSION_DB_ID from step 2>",
    "tool_name": "Write File",
    "tool_input": "src/main/java/com/acme/users/UserController.java",
    "tool_response": "Created UserController.java with REST endpoints for user management.",
    "cwd": "/Users/me/project",
    "prompt_number": 1
  }' | python3 -m json.tool
# {"status": "accepted"}
```

Wait ~15-20 seconds for async processing (LLM call + embedding generation).

### Step 4: Verify Observation in Database

```bash
psql -h 127.0.0.1 -U postgres -d claude_mem_dev \
  -c "SELECT id, title, type, embedding_model_id, (embedding_1024 IS NOT NULL) AS has_embed_1024
      FROM mem_observations ORDER BY created_at_epoch DESC LIMIT 5;"
```

Expected: 1 row with `type=feature`, `embedding_model_id=BAAI/bge-m3`, `has_embed_1024=t`.

### Step 5: Semantic Search

```bash
curl -s "http://127.0.0.1:37777/api/search?project=/tmp/test-project&query=REST+API+user+controller&limit=5" \
  | python3 -m json.tool
```

Expected: `count: 1`, `strategy: "semantic"`, observation returned with similarity score.

### Step 6: End Session

```bash
curl -s -X POST http://127.0.0.1:37777/api/ingest/session-end \
  -H 'Content-Type: application/json' \
  -d '{"session_id": "test-e2e-002"}' | python3 -m json.tool
# {"status": "ok"}
```

### Test Results

All steps passed:

- ✅ Flyway V1 + V2 migrations applied successfully
- ✅ Session uses `content_session_id` for observation/summary linkage
- ✅ Tool-use event triggered async LLM call (DeepSeek deepseek-chat)
- ✅ LLM returned structured XML observation (725 prompt tokens, 287 completion tokens)
- ✅ Observation saved with `embedding_1024` via SiliconFlow BAAI/bge-m3
- ✅ Semantic search returned the observation using cosine similarity
- ✅ Session completed successfully
- ✅ Stats endpoint: 1 session, 1 observation, 1 project

## Project Structure

```
src/main/java/com/ablueforce/cortexce/
├── ClaudeMemApplication.java        # Main entry point
├── config/
│   ├── AsyncConfig.java             # @EnableAsync with virtual threads
│   ├── AppSettings.java             # Application settings POJO
│   ├── Constants.java               # Shared constants
│   ├── ExtractionConfig.java        # Structured extraction configuration
│   ├── ModeConfig.java              # Memory mode configuration
│   ├── MdcAutoFilter.java           # MDC logging filter
│   ├── QueueHealthIndicator.java    # Health check for pending queue
│   ├── SpringAiConfig.java          # Spring AI integration config
│   └── WebConfig.java               # CORS and web config
├── controller/
│   ├── IngestionController.java     # Hook event endpoints
│   ├── ViewerController.java        # Viewer API endpoints
│   ├── StreamController.java        # SSE streaming
│   ├── ContextController.java       # Context generation endpoints
│   ├── SessionController.java       # Session management endpoints
│   ├── MemoryController.java        # Memory API endpoints
│   ├── ModeController.java          # Memory mode endpoints
│   ├── ExtractionController.java    # Structured extraction endpoints
│   ├── LogsController.java          # Log access endpoints
│   ├── HealthController.java        # Health check endpoint
│   ├── ImportController.java        # Data import endpoints
│   ├── CursorController.java        # Cursor IDE integration endpoints
│   └── TestController.java          # Test/debug endpoints
├── entity/
│   ├── SessionEntity.java
│   ├── ObservationEntity.java       # 3 embedding vector fields (768/1024/1536)
│   ├── SummaryEntity.java
│   ├── UserPromptEntity.java
│   └── PendingMessageEntity.java
├── repository/
│   ├── SessionRepository.java
│   ├── ObservationRepository.java   # Dimension-specific semantic search
│   ├── SummaryRepository.java
│   ├── UserPromptRepository.java
│   └── PendingMessageRepository.java
├── service/
│   ├── AgentService.java            # Core orchestration: LLM → parse → embed → save
│   ├── LlmService.java             # DeepSeek / OpenAI-compatible API client
│   ├── EmbeddingService.java        # SiliconFlow embedding API client
│   ├── SearchService.java           # Semantic + text search with dimension routing
│   ├── SSEBroadcaster.java          # Server-Sent Events broadcasting
│   ├── ContextService.java          # Context generation and management
│   ├── ContextCacheService.java     # Context caching
│   ├── TimelineService.java         # Timeline context generation
│   ├── ClaudeMdService.java         # CLAUDE.md file generation
│   ├── TokenService.java            # Token counting
│   ├── RateLimitService.java        # Per-session rate limiting
│   ├── ProjectFilterService.java    # Project path filtering
│   ├── ModeService.java             # Memory mode management
│   ├── MemoryRefineService.java     # Memory refinement/evolution
│   ├── StructuredExtractionService.java  # Structured data extraction
│   ├── SessionManagementService.java # Session lifecycle management
│   ├── SummaryGenerationService.java # Summary generation
│   ├── TemplateService.java         # Prompt template management
│   ├── SettingsService.java         # Application settings
│   ├── ImportService.java           # Data import
│   ├── CursorService.java           # Cursor IDE integration
│   ├── ExpRagService.java           # Experimental RAG
│   ├── PendingMessageProcessor.java # Pending message queue processing
│   ├── LlmQualityScorer.java        # LLM-based quality scoring
│   ├── QualityScorer.java           # Observation quality scoring
│   ├── WorktreeDetector.java        # Git worktree detection
│   ├── ExperienceTemplate.java      # Experience retrieval templates
│   └── StaleMessageRecoveryTask.java # Crash recovery for stale messages
├── util/
│   ├── XmlParser.java               # Regex XML parser for LLM output
│   ├── VectorValidator.java         # Vector embedding validation
│   └── SessionStatus.java           # Session status enum/utility

src/main/resources/
├── application.yml                  # All configuration
├── db/migration/
│   ├── V1__init_schema.sql          # Base schema (5 tables)
│   ├── V2__multi_dimension_embeddings.sql  # Multi-dim vectors + model tracking
│   ├── V3__add_skipped_status.sql
│   ├── V4__context_caching.sql
│   ├── V5__user_prompt_project.sql
│   ├── V6__pending_message_hash.sql
│   ├── V7__remove_embedding_3072.sql
│   ├── V8__add_observation_content_hash.sql
│   ├── V11__observation_quality.sql
│   ├── V12__step_efficiency.sql
│   ├── V13__unify_session_id_on_content_session.sql
│   ├── V14__observation_source_and_extracted_data.sql
│   ├── V15__add_user_id_to_sessions.sql
│   └── V16__composite_source_index.sql
└── prompts/
    ├── init.txt                     # System prompt for memory observer
    ├── observation.txt              # User prompt template for tool events
    ├── summary.txt                  # Summary generation prompt
    └── continuation.txt             # Continuation prompt
```
