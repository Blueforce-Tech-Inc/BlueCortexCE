# Cortex Community Edition Deployment Guide

> **中文版**: [DEPLOYMENT-zh-CN.md](DEPLOYMENT-zh-CN.md)

This guide provides comprehensive instructions for deploying Cortex Community Edition in production environments, including Docker deployment, configuration management, monitoring, and troubleshooting.

## Table of Contents

- [1. System Requirements](#1-system-requirements)
- [2. Docker Deployment](#2-docker-deployment)
- [3. Production Configuration](#3-production-configuration)
- [4. Database Migration](#4-database-migration)
- [5. Environment Variables](#5-environment-variables)
- [6. Monitoring and Logging](#6-monitoring-and-logging)
- [7. Troubleshooting](#7-troubleshooting)
- [8. Backup and Recovery](#8-backup-and-recovery)

---

## 1. System Requirements

### 1.1 Hardware Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 cores | 4+ cores |
| Memory | 4 GB | 8+ GB |
| Disk | 20 GB | 50+ GB SSD |
| Network | 100 Mbps | 1 Gbps |

### 1.2 Software Requirements

| Software | Version | Description |
|----------|---------|-------------|
| Docker | ≥ 20.10 | Container runtime |
| Docker Compose | ≥ 2.20 | Container orchestration |
| PostgreSQL | 16 + pgvector 0.8.1 | Database (included in Docker deployment) |

### 1.3 Port Requirements

| Port | Service | Description |
|------|---------|-------------|
| 37777 | Claude-Mem Java | HTTP API service |
| 5433 | PostgreSQL | Database service (configurable) |

---

## 2. Docker Deployment

### 2.1 Quick Start (Recommended)

Deploy with a single command using Docker Compose:

```bash
# 1. Clone the repository
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd BlueCortexCE

# 2. Create environment configuration file
cat > .env << 'EOF'
# Database configuration
DB_NAME=claude_mem
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password_here

# LLM configuration (OpenAI compatible API)
SPRING_AI_OPENAI_API_KEY=your_api_key_here
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-chat

# Embedding model configuration
SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_api_key
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024

# Runtime configuration
SPRING_PROFILES_ACTIVE=prd
SERVER_PORT=37777
EOF

# 3. Start services
docker compose up -d

# 4. View logs
docker compose logs -f claude-mem

# 5. Health check
curl http://localhost:37777/api/health
```

### 2.2 Using Pre-built Images

Pull pre-built images from GitHub Container Registry:

```bash
# Pull latest version
docker pull ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main

# Pull specific version
docker pull ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:v0.1.0-beta
```

### 2.3 Custom Build

Build the image from source:

```bash
# Build the image
docker build -t ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main .
```

### 2.4 Docker Compose Service Details

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: cortex-ce-postgres
    environment:
      POSTGRES_DB: ${DB_NAME:-claude_mem}
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      # DB_PASSWORD must be set in .env (see .env.docker template)
      POSTGRES_PASSWORD: ${DB_PASSWORD:?Database password is required — set DB_PASSWORD in your .env file}
    volumes:
      - ${POSTGRES_DATA_PATH:-postgres_data}:/var/lib/postgresql/data
    ports:
      - "${POSTGRES_PORT:-5433}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-postgres}"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - claude-mem-network
    restart: unless-stopped

  claude-mem:
    image: ${IMAGE_NAME:-ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main}
    container_name: claude-mem-java
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      # Database configuration
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${DB_NAME:-claude_mem}
      SPRING_DATASOURCE_USERNAME: ${DB_USERNAME:-postgres}
      # Uses DB_PASSWORD — must be set in .env (see .env.docker template)
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:?Database password is required — set DB_PASSWORD in your .env file}

      # Server configuration
      SERVER_PORT: 37777
      SERVER_ADDRESS: 0.0.0.0
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prd}

      # LLM configuration
      SPRING_AI_OPENAI_API_KEY: ${SPRING_AI_OPENAI_API_KEY:-}
      SPRING_AI_OPENAI_BASE_URL: ${SPRING_AI_OPENAI_BASE_URL:-https://api.openai.com}
      SPRING_AI_OPENAI_CHAT_MODEL: ${SPRING_AI_OPENAI_CHAT_MODEL:-gpt-4o}

      # Embedding configuration
      SPRING_AI_OPENAI_EMBEDDING_API_KEY: ${SPRING_AI_OPENAI_EMBEDDING_API_KEY:-}
      SPRING_AI_OPENAI_EMBEDDING_BASE_URL: ${SPRING_AI_OPENAI_EMBEDDING_BASE_URL:-https://api.openai.com}
      SPRING_AI_OPENAI_EMBEDDING_MODEL: ${SPRING_AI_OPENAI_EMBEDDING_MODEL:-text-embedding-3-small}
      SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS: ${SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS:-1536}

      # JVM configuration
      JAVA_OPTS: ${JAVA_OPTS:--XX:+UseZGC -XX:MaxRAMPercentage=75.0}

      # Runtime configuration
      CLAUDE_MEM_MODE: ${CLAUDE_MEM_MODE:-code}
      CLAUDEMEM_LOG_DIR: /app/logs
      CLAUDEMEM_LLM_PROVIDER: ${CLAUDEMEM_LLM_PROVIDER:-openai}

      # Anthropic API (optional — set CLAUDEMEM_LLM_PROVIDER=anthropic to use)
      SPRING_AI_ANTHROPIC_API_KEY: ${SPRING_AI_ANTHROPIC_API_KEY:-}
      SPRING_AI_ANTHROPIC_BASE_URL: ${SPRING_AI_ANTHROPIC_BASE_URL:-https://api.anthropic.com}
      SPRING_AI_ANTHROPIC_CHAT_MODEL: ${SPRING_AI_ANTHROPIC_CHAT_MODEL:-claude-sonnet-4-5}

      MEMORY_REFINE_ENABLED: ${MEMORY_REFINE_ENABLED:-true}
    volumes:
      - ${LOGS_PATH:-claude-mem-logs}:/app/logs
    ports:
      - "${SERVER_PORT:-37777}:37777"
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:37777/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    networks:
      - claude-mem-network

networks:
  claude-mem-network:
    driver: bridge

volumes:
  postgres_data:
  claude-mem-logs:
```

---

## 3. Production Configuration

### 3.1 JVM Configuration

Recommended production JVM parameters:

```bash
# ZGC garbage collector (low latency)
JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"

# Or G1GC garbage collector (throughput-oriented)
JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitiatingHeapOccupancyPercent=45"

# Full configuration example
JAVA_OPTS="-XX:+UseZGC \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heap_dump.hprof \
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m"
```

### 3.2 Database Connection Pool Configuration

Configure in `application.yml` (defaults are optimized):

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 25        # Maximum connections
      minimum-idle: 5              # Minimum idle connections
      connection-timeout: 10000    # Connection timeout (ms)
      idle-timeout: 300000         # Idle timeout (5 minutes)
      max-lifetime: 1680000        # Max connection lifetime (28 minutes)
```

### 3.3 Security Configuration

#### 3.3.1 Network Security

```bash
# Allow local access only (default)
SERVER_ADDRESS=127.0.0.1

# Allow all interfaces (configure firewall)
SERVER_ADDRESS=0.0.0.0
```

#### 3.3.2 Database Security

```bash
# Use a strong password
DB_PASSWORD=$(openssl rand -base64 32)

# Restrict database access
# Remove port mapping in docker-compose.yml, keep internal network only
# ports:
#   - "5433:5432"  # Remove this line
```

#### 3.3.3 API Key Management

```bash
# Use environment variables (recommended)
export SPRING_AI_OPENAI_API_KEY="sk-xxx"

# Or use Docker Secrets
docker secret create openai_api_key ./secrets/openai_key.txt
```

### 3.4 Performance Optimization

#### 3.4.1 Application Layer Optimization

```yaml
# application-prd.yml
spring:
  threads:
    virtual:
      enabled: true              # Enable virtual threads

  jpa:
    hibernate:
      ddl-auto: none            # Disable auto DDL in production

  flyway:
    enabled: true               # Enable database migration
    locations: classpath:db/migration
```

#### 3.4.2 Database Optimization

```sql
-- Connect to PostgreSQL and execute optimization
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '768MB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;
ALTER SYSTEM SET work_mem = '2621kB';
ALTER SYSTEM SET min_wal_size = '1GB';
ALTER SYSTEM SET max_wal_size = '4GB';

-- Restart database to apply
SELECT pg_reload_conf();
```

---

## 4. Database Migration

### 4.1 Migration Strategy

Claude-Mem uses **Flyway** for database version management:

> **This table is a reading aid, not the source of truth.** The authoritative list is
> whatever is in `backend/src/main/resources/db/migration/` — Flyway applies those files
> automatically (§4.2) and this table is not validated against them. Version numbers
> skip on purpose: **V9 and V10 do not exist**. If a migration lands here without a row,
> nothing breaks; if a row is wrong, trust the directory listing over this table.

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__init_schema.sql` | Initial schema (5 core tables) |
| V2 | `V2__multi_dimension_embeddings.sql` | Multi-dimension embedding support |
| V3 | `V3__add_skipped_status.sql` | Add skipped status |
| V4 | `V4__context_caching.sql` | Context caching |
| V5 | `V5__user_prompt_project.sql` | User prompt project association |
| V6 | `V6__pending_message_hash.sql` | Message deduplication |
| V7 | `V7__remove_embedding_3072.sql` | Remove 3072-dimension embedding |
| V8 | `V8__add_observation_content_hash.sql` | Content hash index |
| V11 | `V11__observation_quality.sql` | Observation quality scoring |
| V12 | `V12__step_efficiency.sql` | Step efficiency tracking |
| V13 | `V13__unify_session_id_on_content_session.sql` | Unify session ID to content_session |
| V14 | `V14__observation_source_and_extracted_data.sql` | Observation source and extracted data |
| V15 | `V15__add_user_id_to_sessions.sql` | Add user_id to sessions table |
| V16 | `V16__composite_source_index.sql` | Composite source index |
| V17 | `V17__observation_feedback.sql` | Observation feedback tracking + extended columns |
| V18 | `V18__add_platform_source.sql` | Add platform_source column for multi-platform tracking |
| V19 | `V19__viewer_session_indexes.sql` | Indexes for the WebUI session catalog and feed queries |

### 4.2 Migration Execution

#### Automatic Migration (Recommended)

Migrations run automatically on application startup:

```bash
# Flyway configuration (application.yml)
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

#### Manual Migration

```bash
# View migration status
docker exec claude-mem-java java -jar app.jar --spring.flyway.info=true

# Trigger migration manually
docker exec claude-mem-java java -jar app.jar --spring.flyway.migrate=true
```

### 4.3 Migration Verification

```bash
# Connect to database
docker exec -it cortex-ce-postgres psql -U postgres -d claude_mem

# View migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

# Verify table structure
\dt mem_*

# Verify indexes
\di mem_*
```

### 4.4 Rollback Strategy

Flyway Community Edition does not support automatic rollback, and **this repository
ships no rollback scripts** — each inverse DDL has to be written against that
specific migration in `backend/src/main/resources/db/migration/`.

For most situations the safe path is to restore the backup taken in step 1
rather than to hand-undo a migration:

```bash
# 1. Back up first — always do this before touching schema
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem > backup_$(date +%Y%m%d).sql

# 2. Restore if the migration needs to be undone
docker exec -i cortex-ce-postgres psql -U postgres claude_mem < backup_20260101_000000.sql
```

If you really do need to reverse a migration, work from the migration file itself —
it is the only record of what was applied. The example below is what reversing
`V8__add_observation_content_hash.sql` would involve, and the two warnings are why
this is not a mechanical procedure:

```sql
-- The inverse of V8, per backend/src/main/resources/db/migration/V8__add_observation_content_hash.sql
ALTER TABLE mem_observations DROP CONSTRAINT IF EXISTS fk_obs_memory_session;
DROP INDEX IF EXISTS idx_obs_content_hash;
ALTER TABLE mem_observations DROP COLUMN IF EXISTS content_hash;
DELETE FROM flyway_schema_history WHERE version = '8';
```

> **Do not apply the above without checking the current code first.** V8's
> `content_hash` column is not historical — `ObservationRepository.findDuplicateByContentHash`
> and `AgentService` use it for observation deduplication, so dropping the column
> breaks ingestion. And V8's `fk_obs_memory_session` constraint was already dropped
> by `V13__unify_session_id_on_content_session.sql`, which replaced it with the
> `content_session_id` model — so the first statement is a no-op and the schema no
> longer matches what V8 produced. Prefer restoring a backup.

---

## 5. Environment Variables

### 5.1 Core Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | No | `prd` **under Docker Compose only** | Spring profile (`dev`/`prd`). `docker-compose.yml:52` is what defaults it to `prd`; a native JAR run that leaves it unset has **no active profile** — see the third state in §5.4 |
| `SERVER_PORT` | No | `37777` | HTTP service port |
| `SERVER_ADDRESS` | No | `127.0.0.1` (local JAR); hardcoded `0.0.0.0` in Docker | Bind address |
| `CORTEXCE_API_TOKEN` | No | empty (no auth) | Optional bearer token (`cortexce.security.api-token`). When set, writes under `/api/**` and `GET /api/logs/**` require `Authorization: Bearer <token>`; missing/wrong tokens get 401 with the unified error envelope. **Set this before exposing the service beyond localhost.** |

### 5.2 Database Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | Yes | - | Database connection URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | **Yes** | - | Database password |
| `DB_NAME` | No | `claude_mem` | Database name |
| `DB_USERNAME` | No | `postgres` | Database username (Docker Compose) |
| `DB_PASSWORD` | **Yes** | - | Database password — see `.env.docker` template (Docker Compose) |
| `POSTGRES_PORT` | No | `5433` | PostgreSQL host port (Docker Compose: `host:container`) |

### 5.3 LLM Configuration

#### OpenAI Compatible API (DeepSeek, Moonshot, etc.)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_AI_OPENAI_API_KEY` | No | - | OpenAI API key — required only when `CLAUDEMEM_LLM_PROVIDER=openai` (the default) (alias: `OPENAI_API_KEY`) |
| `SPRING_AI_OPENAI_BASE_URL` | No | `https://api.openai.com` | API base URL (alias: `OPENAI_BASE_URL`) |
| `SPRING_AI_OPENAI_CHAT_MODEL` | No | `gpt-4o` | Chat model name (alias: `OPENAI_MODEL`) |

#### Anthropic Compatible API (Claude, GLM, etc.)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_AI_ANTHROPIC_API_KEY` | No | - | Anthropic API key — required only when `CLAUDEMEM_LLM_PROVIDER=anthropic` (alias: `ANTHROPIC_API_KEY`) |
| `SPRING_AI_ANTHROPIC_BASE_URL` | No | `https://api.anthropic.com` | API base URL (alias: `ANTHROPIC_BASE_URL`) |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | No | `claude-sonnet-4-5` | Model name (alias: `ANTHROPIC_MODEL`) |
| `CLAUDEMEM_LLM_PROVIDER` | No | `openai` | LLM provider (`openai`/`anthropic`) |

> **The two chat API keys are alternatives, not a pair.** An earlier revision of
> this table marked `SPRING_AI_OPENAI_API_KEY` as unconditionally **required**
> while giving the Anthropic key its "only when `provider=anthropic`" qualifier —
> the mirror-image row said something different about the same mechanism. Only
> the key matching the active provider is needed: `SpringAiConfig` registers
> `openAiChatModel` and `anthropicChatModel` under the same
> `@ConditionalOnProperty(name = "api-key")` pattern, each returning `null` when
> its key is blank or its provider does not match, and the `chatClient` bean
> (`@ConditionalOnMissingBean`) takes the first non-null `ChatModel`. Its own
> startup warning says as much: *"Configure spring.ai.openai.api-key **or**
> spring.ai.anthropic.api-key."* With `provider=anthropic`, an anthropic-only
> deployment starts and serves LLM calls without an OpenAI key.
>
> The **embedding** key below is genuinely always required, and the asymmetry
> there is real: embeddings have no provider switch, so they always go through
> the OpenAI-compatible endpoint regardless of which chat provider is active.

### 5.4 Embedding Model Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | **Yes** | - | Embedding API key (alias: `EMBEDDING_API_KEY`) |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | No | `https://api.openai.com` | Embedding API URL (alias: `EMBEDDING_BASE_URL`) |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | No | `text-embedding-3-small` | Embedding model name (alias: `EMBEDDING_MODEL`) |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | No | `1536` | Embedding dimensions (768/1024/1536) (alias: `EMBEDDING_DIMENSIONS`) |

> **The "Default" column is profile-specific.** Both tables above state the `prd`
> profile's values, which is what `docker compose up` selects — `SPRING_PROFILES_ACTIVE`
> defaults to `prd`. The `dev` profile ships different ones:
>
> | Variable | `prd` default | `dev` default |
> |----------|---------------|---------------|
> | `SPRING_AI_OPENAI_BASE_URL` | `https://api.openai.com` | `https://api.deepseek.com` |
> | `SPRING_AI_OPENAI_CHAT_MODEL` | `gpt-4o` | `deepseek-chat` |
> | `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | `https://api.openai.com` | `https://api.siliconflow.cn` |
> | `SPRING_AI_OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | `BAAI/bge-m3` |
> | `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | `1536` | `1024` |
>
> Set a variable explicitly and the profile no longer matters; leave it unset and
> the profile decides. Section 5.8's development example relies on this — it pins
> all five values rather than inheriting them.
>
> **There is a third state, and it is the one a native JAR run lands in.**
> `prd` is the default **only under Docker Compose** (`docker-compose.yml:52`:
> `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prd}`). Start the JAR
> natively without that variable and Spring logs *"No active profile set,
> falling back to 1 default profile: \"default\""* — and then **neither table
> above applies**, because `application.yml` itself defines no `spring.ai.openai.*`
> keys. What you get instead is the `@Value` fallback inside `SpringAiConfig`,
> which happens to match the `dev` column:
>
> | Variable | value actually used with no profile |
> |----------|--------------------------------------|
> | `SPRING_AI_OPENAI_BASE_URL` | `https://api.deepseek.com` |
> | `SPRING_AI_OPENAI_CHAT_MODEL` | `deepseek-chat` |
> | `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | `https://api.siliconflow.cn` |
> | `SPRING_AI_OPENAI_EMBEDDING_MODEL` | `BAAI/bge-m3` |
> | `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | `1024` |
>
> **And with no profile, most of those variables stop working.** Measured on
> 2026-10-20 by starting the JAR with no profile: setting
> `SPRING_AI_OPENAI_BASE_URL` and `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` **did**
> take effect, but `SPRING_AI_OPENAI_CHAT_MODEL`,
> `SPRING_AI_OPENAI_EMBEDDING_MODEL` and `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS`
> were **silently ignored** — and so were the short aliases `OPENAI_BASE_URL` /
> `OPENAI_MODEL`. The reason is in the property names: `SpringAiConfig` reads the
> model and the dimensions from `spring.ai.openai.chat.options.model` and
> `spring.ai.openai.embedding.options.dimensions`, while Spring's relaxed binding
> maps `SPRING_AI_OPENAI_CHAT_MODEL` to `spring.ai.openai.chat.model` and
> `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` to `spring.ai.openai.embedding.dimensions`
> — one segment short, so the value never reaches the field. The two base URLs are
> read from `spring.ai.openai.base-url` / `spring.ai.openai.embedding.base-url`,
> which *do* match, which is why they still work. Under `prd` and `dev` this does
> not bite, because those files spell the variables out themselves
> (`model: ${SPRING_AI_OPENAI_CHAT_MODEL:...}`) — **it is the profile that maps
> the names**, not the code. So: **set `SPRING_PROFILES_ACTIVE` explicitly, or
> expect the model names to be ignored.**
>
> The short aliases above are defined in `application-prd.yml` only. The `dev`
> profile reads the `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` names
> but has no `EMBEDDING_*` equivalents, so under `dev` those must be spelled out
> in full.
>
> **Changing the dimension has a consequence worth knowing before you try it.**
> Writes route by dimension, so a 768- or 1536-dim model is stored correctly. The
> read path does not: semantic search always compares against the `embedding_1024`
> column, so a mismatch raises `different vector dimensions <n> and 1024` and the
> query falls back to full-text search. It is not silent — the search response
> reports `strategy: "tsvector"` with `fellBack: true` — but the only outward sign
> of a misconfigured embedding setup will be that no query ever uses the vector
> index. `BAAI/bge-m3` at 1024 dimensions is the configuration the search path is
> built around.

### 5.5 Runtime Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CLAUDE_MEM_MODE` | No | `code` | Memory mode (`code`, `code--<lang>`, `email-investigation`, etc.) |
| `CLAUDE_MEM_MODES_DIR` | No | *(empty)* | Directory of custom mode profiles. Empty means the JAR-embedded `classpath:modes/` in production and `plugin/modes/` during development. |
| `CLAUDEMEM_LOG_DIR` | No | `~/.claude-mem/logs` | Log directory |
| `MEMORY_REFINE_ENABLED` | No | `true` | Enable memory refinement (self-evolution) |
| `JAVA_OPTS` | No | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` | JVM options |

#### Data Retention (Round 20)

`RetentionService` ages out user prompts, observations, summaries and rolled log
files. Every switch defaults to the safe side: the job is **disabled**, a run is
**dry-run**, and every window is `0` (**keep forever**). No row or file is removed
until an operator opts in.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CORTEXCE_RETENTION_ENABLED` | No | `false` | Master switch for the scheduled cleanup |
| `CORTEXCE_RETENTION_CRON` | No | `0 0 3 * * *` | When the job runs (only used when enabled) |
| `CORTEXCE_RETENTION_DRY_RUN` | No | `true` | Count and log what would be deleted; no writes |
| `CORTEXCE_RETENTION_PROMPTS_DAYS` | No | `0` | Prompt retention; `0` keeps forever |
| `CORTEXCE_RETENTION_OBSERVATIONS_DAYS` | No | `0` | Observation retention; `0` keeps forever |
| `CORTEXCE_RETENTION_SUMMARIES_DAYS` | No | `0` | Summary retention; `0` keeps forever |
| `CORTEXCE_RETENTION_LOGS_DAYS` | No | `0` | Rolled log file retention; `0` keeps forever |
| `CORTEXCE_RETENTION_PROJECT` | No | *(empty)* | Exact `project_path` scope; empty means every project |
| `CORTEXCE_RETENTION_LOG_DIR` | No | `LOG_DIR` or `~/.claude-mem/logs` | Where rolled log files live |
| `CORTEXCE_RETENTION_LOG_PREFIX` | No | `claude-mem` | Only files whose name starts with this prefix are eligible |

Rollout is two steps: enable with `DRY_RUN=true` and read the
`Retention dry run: would delete prompts=… observations=… summaries=… logFiles=…`
line, then set `DRY_RUN=false`. Rows are selected by `created_at_epoch < cutoff`,
so a row exactly on the boundary is kept. When `PROJECT` is set the predicate is
part of both the count and the `DELETE`, so a scoped run cannot reach another
project's rows. Log cleanup never deletes the active file (its mtime is newer
than the cutoff) and ignores files outside the configured directory or prefix.
Filesystem cleanup only applies to rolled files, not the database.

#### Search Rerank (Round 21)

The optional rerank reorders the first few hybrid results with the chat model. It
ships **disabled**, and every failure path (missing key, timeout, non-2xx,
unparseable body) returns the fused order unchanged, so enabling it can add
latency but cannot lose a result. Measured on 90 real queries, top-5 with
thinking disabled: hit@1 0.833 → 0.867, sequential p95 3.7 s. See
`docs/archive/2026-11-04_rerank-real.md`.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CORTEXCE_SEARCH_RERANK_ENABLED` | No | `false` | Master switch; off keeps the fused ranking |
| `CORTEXCE_SEARCH_RERANK_TOP_N` | No | `5` | How many leading results are reordered |
| `CORTEXCE_SEARCH_RERANK_TIMEOUT_MS` | No | `8000` | Per-call timeout before falling back |
| `CORTEXCE_SEARCH_RERANK_THINKING` | No | `false` | `true` uses provider reasoning (33.5 s p95, 2.2% empty rankings) |

#### Evo-Memory and Structured Extraction Tuning

`MEMORY_REFINE_ENABLED` switches refinement on, but not what it does — the three
`MEMORY_REFINE_*` thresholds below decide which observations are touched, and they are
independent. `MEMORY_QUALITY_THRESHOLD` is listed first because it is the one quality
knob that is *not* part of refinement: it gates experience retrieval only. Structured
extraction (Phase 3) is a separate feature, disabled by default, with its own three
batch controls.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MEMORY_QUALITY_THRESHOLD` | No | `0.6` | Minimum quality an observation needs to be picked up as an ICL experience by `POST /api/memory/experiences` (ExpRagService). Not a filter on search or context retrieval. A separate literal `0.6` in `MemoryRefineService.findStaleObservations` gates refine candidates and is not driven by this value. |
| `MEMORY_REFINE_DELETE_THRESHOLD` | No | `0.3` | Quality below which refinement deletes an observation (prune) |
| `MEMORY_REFINE_STALE_DAYS` | No | `30` | Days without access before an observation counts as stale |
| `MEMORY_REFINE_COOLDOWN_DAYS` | No | `7` | Days before a refined observation may be refined again |
| `EXTRACTION_ENABLED` | No | `false` | Enable Phase 3 structured extraction |
| `EXTRACTION_MAX_CANDIDATES` | No | `100` | Candidate observations considered per run. Despite the "initial run" name this applies to **every** run — there is no extraction watermark, so the newest 100 matching observations are re-sent each time and anything older is never extracted |
| `EXTRACTION_BATCH_SIZE` | No | `20` | Observations per extraction batch, applied per user |
| `EXTRACTION_MAX_BATCHES` | No | `10` | Batches per user per run. No effect at the shipped defaults — the 100-candidate cap already limits a user to 5 batches of 20. Takes effect only if `EXTRACTION_MAX_CANDIDATES` is raised above `EXTRACTION_BATCH_SIZE × EXTRACTION_MAX_BATCHES` |

`MEMORY_REFINE_DELETE_THRESHOLD` is the destructive one: during a refinement run
every candidate that *has* a quality score below it is deleted rather than
rewritten. Records with no score at all are exempt and go to the rewrite path
instead. Raising the threshold is the safe direction; lowering it removes data.

**The backend's own rate limiter is configured separately, and none of its keys
appear in `application.yml`.** `RateLimitService` reads them straight from
`@Value` defaults, so they are live and tunable but invisible in the table above:

| Variable | Default | Description |
|----------|---------|-------------|
| `CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS` | `10` | Requests allowed per window |
| `CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS` | `60` | Window length in seconds. It is a **fixed** window that resets wholesale when it expires, not a sliding one — see the note below |
| `CLAUDEMEM_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS` | `300` | How often idle window entries are evicted |

**It guards one endpoint, not the API.** The only call site is
`IngestionController`, which applies it to `POST /api/ingest/tool-use` keyed by
content session id. The other three ingest endpoints (`user-prompt`,
`session-end`, `observation`) and every read endpoint are unlimited. Verified
live: 10 tool-use calls on one session succeed and the 11th returns

```json
{"retry_after": "60", "error": "Rate limit exceeded"}
```

while 13 `GET /api/search` calls in the same period all return `200`. The
`retry_after` value is **the number of seconds left in the current window**
(`RateLimitService.getRemainingSeconds()` = `windowSeconds - (now - windowStart)`),
so it is `60` only when the rejected request lands in the same second the
window opened — one second later it reads `59`. Backing off for that many
seconds is still correct: the window expires and the counter resets. The
`cleanup-interval` is a memory control: the service keeps at most 10,000 window
entries, so a short interval bounds growth when many distinct sessions are
seen.

> **The window is fixed, not sliding, and the limit can be doubled at a boundary.**
> `RateLimitService.SlidingWindow.tryIncrement` resets the whole counter when
> `now - windowStart >= windowSeconds` — the window does not age out request by
> request. Measured live on 2026-10-20 on one session: 1 call at t+0.1s, 9 more
> at t+58.1s (all `200`, counter now 10), then **10 more at t+60.6s, all `200`** —
> the counter had reset at the boundary, and the previous ten were only 2.5
> seconds old, so a true sliding window would have rejected every one of them.
> 20 requests therefore passed within a couple of seconds of each other — 2.5 s
> in that run, 3.3 s when the same probe was repeated — and the 21st (same
> session) returned `429`. The gap is a few seconds, not 60, and that is the
> point: treat "10 requests per window" as a per-window cap, not as a hard
> ceiling on any 60-second span. The class is still named
> `SlidingWindow` and the variable is still documented as a sliding window in
> the backend's own javadoc — the two names describe an algorithm the code does
> not implement.

This limiter is unrelated to the `429` an LLM provider can return — see the
troubleshooting table above.

### 5.6 Data Persistence Paths (Docker Compose)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `POSTGRES_DATA_PATH` | No | `postgres_data` | PostgreSQL data volume path (Docker Compose host path) |
| `LOGS_PATH` | No | `claude-mem-logs` | Application logs volume path (Docker Compose host path) |
### 5.7 Multi-User Deployments

`userId` is a **session-level** attribute, set when a session starts
(`POST /api/session/start`, field `user_id`) or attached later
(`PATCH /api/session/{sessionId}/user`). It is not a property of an observation:
`mem_observations` has no user column, so a user cannot be read off a stored
record.

**Some endpoints honour `userId` and two common ones do not.** Measured against
a project containing one observation recorded under `userId: alice`:

| Endpoint | `userId` accepted? | Result |
|----------|--------------------|--------|
| `POST /api/memory/experiences` | yes | `alice` → 1 experience, `bob` → 0 |
| `POST /api/memory/icl-prompt` | yes | scoped the same way |
| `GET /api/extraction/{templateName}/latest`, `/history` | yes | scoped the same way |
| `GET /api/search` | **no** | returns alice's observation, and cannot be narrowed |
| `GET /api/observations`, `POST /api/observations/batch` | **no** | returns every observation in the project |

The ICL surface is properly isolated: an agent asked for alice's prompt gets
alice's experiences, and a wrong or unknown id returns an empty result rather
than someone else's. `GET /api/search` returns the whole project regardless,
because there is no user to filter on.

If you are serving more than one user from one project, treat `/api/search` and
`/api/observations` as **project-wide, not user-scoped**. Give each user their
own project path if you need per-user isolation on those endpoints, or do not
expose them to end users — the four SDKs surface both, so they are reachable
from any client holding an API key.


### 5.8 Configuration Examples

#### Development Environment

```bash
# .env.dev
SPRING_PROFILES_ACTIVE=dev
SERVER_ADDRESS=127.0.0.1

# DeepSeek LLM
SPRING_AI_OPENAI_API_KEY=sk-xxx
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-chat

# SiliconFlow Embedding
SPRING_AI_OPENAI_EMBEDDING_API_KEY=sk-xxx
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024

# Database
DB_PASSWORD=dev_password_123
```

#### Production Environment

```bash
# .env.prd
SPRING_PROFILES_ACTIVE=prd
SERVER_ADDRESS=0.0.0.0

# Anthropic LLM
SPRING_AI_ANTHROPIC_API_KEY=sk-ant-xxx
SPRING_AI_ANTHROPIC_BASE_URL=https://api.anthropic.com
SPRING_AI_ANTHROPIC_CHAT_MODEL=claude-sonnet-4-5
CLAUDEMEM_LLM_PROVIDER=anthropic

# Database
DB_PASSWORD=$(openssl rand -base64 32)

# JVM
JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError"
```

---

## 6. Monitoring and Logging

### 6.1 Health Check

#### Spring Boot Actuator

```bash
# Custom health endpoint (recommended)
curl http://localhost:37777/api/health

# Spring Boot Actuator health check
curl http://localhost:37777/actuator/health

# Detailed information
curl http://localhost:37777/actuator/health | jq

# Response example
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {"database": "PostgreSQL", "validationQuery": "isValid()"}
    },
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"},
    "messageQueue": {
      "status": "UP",
      "details": {"pending": 0, "processing": 0, "stale": 0, "last_processed": "2026-05-04T03:08:25.906Z"}
    }
  }
}
```

#### Health Check Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall health status |
| `/actuator/info` | Application info |
| `/actuator/metrics` | Metrics data |

### 6.2 Application Logs

#### Where the files live and how they are bounded

The service writes structured logs to `${CLAUDEMEM_LOG_DIR}` (default
`~/.claude-mem/logs`):

| File | Written by | Bound |
|------|------------|-------|
| `claude-mem-YYYY-MM-DD.log` | `ClaudeMemLogAppender`; also what `GET /api/logs` reads | rotates at `cortexce.logging.max-file-size` (100 MB) |
| `claude-mem-YYYY-MM-DD.<timestamp>.log` | rotated parts of the file above | deleted oldest-first until the directory fits `cortexce.logging.total-size-cap` (1 GB) |

Rotation keeps the **date-named file as the active, newest file**: when it reaches the
limit it is renamed to a timestamped part and a fresh active file starts. That is why
`/api/logs` keeps returning recent lines after a rotation.

```yaml
cortexce:
  logging:
    max-file-size: ${CORTEXCE_LOG_MAX_FILE_SIZE:100MB}
    total-size-cap: ${CORTEXCE_LOG_TOTAL_SIZE_CAP:1GB}
```

The cleanup runs at startup and after every rotation, so the directory can exceed the
cap by at most one active file (≤ `max-file-size`) between rotations.

#### When to clean, and what

- **Nothing has to be deleted for log growth.** Rotation plus the total-size cap bound
  the directory with no operator action. On the reference machine, 105 unbounded daily
  files had reached 1.5 GB before this existed.
- **Age-based deletion** is the optional second policy. Enable the existing retention
  job (`cortexce.retention.enabled=true`, `cortexce.retention.logs-days=N`) when a policy
  requires removing log files older than N days even when they fit the size cap. The
  same scheduler and dry-run mode handle prompts, observations and summaries.
- **What to clean manually**: `~/.claude-mem/logs/claude-mem-*.log` only. The other
  entries in that directory (`settings.json`, `.env`, `cursor-projects.json`,
  `transcript-watch.json`) are small configuration/state files, not caches; deleting
  them loses settings.
- **Database rows** are the other growing store; use `cortexce.retention.*` (start with
  `dry-run=true` and read the counts) rather than deleting rows by hand.

#### Viewing logs

```bash
# Docker logs
docker compose logs -f claude-mem

# Real-time log tail
docker compose logs -f --tail=100 claude-mem

# Structured file (newest lines live in the date-named file)
tail -f ~/.claude-mem/logs/claude-mem-$(date +%F).log

# Search logs
grep "ERROR" ~/.claude-mem/logs/claude-mem-$(date +%F).log
```

#### Structured Logging

Claude-Mem uses structured log format (JSON):

```json
{
  "timestamp": "2026-03-13T10:15:30.123Z",
  "level": "INFO",
  "logger": "com.claudemem.server.service.AgentService",
  "message": "Observation saved successfully",
  "context": {
    "sessionId": "abc-123",
    "observationId": "xyz-789",
    "project": "/path/to/project"
  }
}
```

### 6.3 Prometheus Monitoring (Optional)

#### Add Dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

#### Configure Endpoint

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

#### Prometheus Configuration

```yaml
# prometheus.yml
# NOTE: Requires micrometer-registry-prometheus dependency and
# spring.boot.actuator.prometheus.metrics.export.enabled=true
scrape_configs:
  - job_name: 'claude-mem-java'
    static_configs:
      - targets: ['localhost:37777']
    metrics_path: '/actuator/prometheus'
```

### 6.4 Key Metrics

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `jvm.memory.used.percent` | JVM memory usage | > 85% |
| `process.cpu.usage` | CPU usage | > 80% |
| `hikaricp.connections.active` | Active DB connections | > 20 |
| `http.server.requests` | HTTP request latency | P99 > 1s |
| `hikaricp.connections.pending` | Pending DB connection requests | > 5 |

---

## 7. Troubleshooting

### 7.1 Common Issues

#### 7.1.1 Service Won't Start

**Symptoms**: Container fails to start or exits immediately

**Diagnostic Steps**:

```bash
# 1. View container logs
docker compose logs claude-mem

# 2. Check configuration
docker compose config

# 3. Verify environment variables
docker compose exec claude-mem env | grep SPRING

# 4. Check dependent services
docker compose ps
```

**Common Causes**:

| Error | Cause | Solution |
|-------|-------|----------|
| `Connection refused` | Database not ready | Wait for database health check to pass |
| `Authentication failed` | Database password wrong | Check `DB_PASSWORD` |
| `Port 37777 already in use` | Port conflict | Stop the conflicting service. Changing `SERVER_PORT` alone does not work on the published image: its health check is pinned to port 37777, so the app would listen elsewhere while Docker still probes 37777 and marks the container unhealthy |

#### 7.1.2 Database Connection Failed

**Symptoms**: Application logs show database connection errors

**Diagnostic Steps**:

```bash
# 1. Check database status
docker compose exec postgres pg_isready -U postgres

# 2. Test connection
docker compose exec postgres psql -U postgres -d claude_mem -c "SELECT 1"

# 3. Check network
docker compose exec claude-mem ping postgres

# 4. View connection pool status
curl http://localhost:37777/actuator/health | jq '.components.db'
```

**Solution**:

```bash
# Restart database
docker compose restart postgres

# Check connection pool configuration
# application.yml - adjust hikari.maximum-pool-size
```

#### 7.1.3 Out of Memory

**Symptoms**: Service crashes, logs show `OutOfMemoryError`

**Diagnostic Steps**:

```bash
# 1. View memory usage
docker stats claude-mem-java

# 2. Check heap dumps
docker exec claude-mem-java ls -lh /app/logs/

# 3. Analyze heap dump (requires MAT tool)
docker cp claude-mem-java:/app/logs/heap_dump.hprof ./
```

**Solution**:

```bash
# Increase memory limit
JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=85.0"

# Or set fixed heap size
JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseZGC"
```

#### 7.1.4 LLM API Call Failed

**Symptoms**: Observation generation fails, logs show API errors

**Diagnostic Steps**:

```bash
# 1. Check API key
docker compose exec claude-mem env | grep API_KEY

# 2. Test API connection
curl -H "Authorization: Bearer $SPRING_AI_OPENAI_API_KEY" \
  https://api.deepseek.com/v1/models

# 3. Check error logs
docker compose logs claude-mem | grep -i "llm\|api\|error"
```

**Common Errors**:

| Error Code | Cause | Solution |
|-----------|-------|----------|
| 401 | Invalid API key | Check `SPRING_AI_OPENAI_API_KEY` |
| 429 | Rate limit | Reduce request frequency or upgrade plan |
| 500 | API service error | Wait or switch to backup API |

### 7.2 Performance Issues

#### 7.2.1 High Response Latency

**Diagnostic Steps**:

```bash
# 1. Check database slow queries
docker compose exec postgres psql -U postgres -d claude_mem -c \
  "SELECT * FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10"

# 2. Check JVM threads
docker exec claude-mem-java jstack 1 > thread_dump.txt

# 3. Monitor HTTP requests
curl http://localhost:37777/actuator/metrics/http.server.requests
```

**Optimization Tips**:

- Enable database query cache
- Optimize SQL queries (avoid N+1)
- Adjust connection pool size
- Increase JVM memory

#### 7.2.2 Slow Database Queries

**Diagnostic Steps**:

```bash
# 1. View execution plan
EXPLAIN ANALYZE SELECT * FROM mem_observations WHERE project_path = '/path';

# 2. Check index usage
SELECT * FROM pg_stat_user_indexes WHERE schemaname = 'public';

# 3. View table statistics
SELECT * FROM pg_stats WHERE tablename = 'mem_observations';
```

**Optimization Tips**:

```sql
-- Rebuild indexes
REINDEX TABLE mem_observations;

-- Update statistics
ANALYZE mem_observations;

-- Add missing indexes (example)
CREATE INDEX idx_obs_custom ON mem_observations(column_name);
```

### 7.3 Log Analysis

#### Error Log Filtering

```bash
# Extract ERROR level logs
docker compose logs claude-mem | grep "ERROR"

# Filter by time range
docker compose logs claude-mem --since="2026-03-13T10:00:00" --until="2026-03-13T11:00:00"

# Keyword search
docker compose logs claude-mem | grep -i "failed\|exception\|error"
```

#### Audit Logs

```bash
# View session creation logs
docker exec claude-mem-java grep "Session created" /app/logs/claude-mem.log

# View observation generation logs
docker exec claude-mem-java grep "Observation saved" /app/logs/claude-mem.log
```

---

## 8. Backup and Recovery

### 8.1 Data Backup

#### Database Backup

```bash
# Full backup
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem > backup_$(date +%Y%m%d_%H%M%S).sql

# Compressed backup
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz

# Schema only
docker exec cortex-ce-postgres pg_dump -U postgres --schema-only claude_mem > schema.sql

# Data only
docker exec cortex-ce-postgres pg_dump -U postgres --data-only claude_mem > data.sql
```

#### Automated Backup Script

```bash
#!/bin/bash
# backup.sh

BACKUP_DIR="/backups/claude-mem"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=30

# Create backup directory
mkdir -p $BACKUP_DIR

# Execute backup
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem | gzip > $BACKUP_DIR/backup_$DATE.sql.gz

# Clean up old backups
find $BACKUP_DIR -name "backup_*.sql.gz" -mtime +$RETENTION_DAYS -delete

echo "Backup completed: backup_$DATE.sql.gz"
```

#### Cron Scheduled Tasks

```bash
# Backup daily at 2 AM
0 2 * * * /path/to/backup.sh >> /var/log/claude-mem-backup.log 2>&1
```

#### Backup Verification Drill (Scheduled)

`scripts/backup-restore-drill.sh` turns the manual dump/restore check into an
unattended inspection. One run performs the whole loop:

1. `pg_dump -Fc` the live database.
2. SHA-256 the dump, prove `pg_restore --list` can read the archive, confirm it
   contains `TABLE DATA public mem_observations`, and re-hash before restoring.
3. Restore into a throwaway database.
4. Compare row counts for `mem_sessions`, `mem_observations`, `mem_user_prompts`,
   `mem_summaries`, `mem_pending_messages` and `mem_memory_scopes`.
5. Replay one lexical retrieval query (`ZH_QUERY`, default `检索`) on the copy and
   compare both the hit count and the top hit's title. `RETRIEVAL_COLUMN=auto` uses
   `search_tokens` when the schema has it and `search_vector` otherwise, so the same
   script works before and after the Chinese-lexical migration.
6. `INTEGRITY_AUDIT=1` runs `scripts/data-integrity-audit.py` on the copy and fails
   the run on any violation (`--apply` is never used here: the copy is throwaway).
7. `REBUILD_HNSW=1` drops and rebuilds the vector index on the copy, verifies it is
   valid, and records the build time and index bytes. This is the same operation a
   backfill deployment performs, proved on restored data.
8. Write a JSON report, drop the throwaway database, delete the dump (unless
   `KEEP_DUMP=1`) and prune kept dumps past `DUMP_KEEP_DAYS`.

While the loop runs, a sampler records disk usage every `DISK_SAMPLE_SECONDS`
(default 2) — source database + restored copy + dump file, and the container
filesystem's used bytes. The report keeps the peak of each (`disk_peak`), because
a drill on a nearly full volume fails during restore and only the filesystem
number shows how close the run came.

**How often**: weekly as a pure inspection (`VALIDATE_FLYWAY=0`, no jar needed),
plus once immediately before and after any schema migration, and after any
storage/container incident. A nightly run with `KEEP_DUMP=1` doubles as the real
backup: the dump it keeps is the dump it just proved restorable.

**Where**: reports go to `${HOME}/.claude-mem/backup-drill/reports/backup-drill-<stamp>.json`
and the newest 30 are kept (`REPORT_DIR`, `REPORT_KEEP`). Dumps go to `DUMP_DIR`;
with `KEEP_DUMP=1`, dumps older than `DUMP_KEEP_DAYS` (default 30) are pruned.

```bash
# Nightly at 02:10 — produce the backup, verify it, keep the dump.
10 2 * * * cd /opt/cortexce && SOURCE_DB=claude_mem DUMP_DIR=/backups/cortexce KEEP_DUMP=1 \
  ./scripts/backup-restore-drill.sh >> /var/log/cortexce-backup.log 2>&1

# Weekly at 03:15 — inspection only, dump discarded.
15 3 * * 1 cd /opt/cortexce && SOURCE_DB=claude_mem \
  ./scripts/backup-restore-drill.sh >> /var/log/cortexce-backup-drill.log 2>&1
```

Exit codes are stage-specific, so a scheduler or alerting hook can tell
"container down" from "restore produced a bad copy":

| Exit | Stage | Action |
|------|-------|--------|
| 0 | pass | Nothing. Check the report for the bytes and timings. |
| 2 | precondition | Container not reachable, source DB missing, or `VALIDATE_FLYWAY=1` without a jar. Fix the environment; no backup was taken. |
| 3 | dump | `pg_dump` failed — disk full, permissions, or a concurrent schema change. Free space / fix credentials, rerun. |
| 4 | checksum / archive | The dump is unreadable or changed on disk. Treat as a storage failure; do not overwrite the previous good backup. |
| 5 | restore | The dump is readable but cannot be rebuilt — usually a Postgres version mismatch or an incomplete archive. |
| 6 | row-count parity | A table's row count differs. Keep the dump (`KEEP=1` also keeps the copy) and compare manually before trusting it. |
| 7 | retrieval | The lexical query disagrees (hit count or probe title). With a stable source this points at a dump/restore problem, not at the data. |
| 8 | Flyway validation | The restored schema does not validate. Do not deploy this database copy. |
| 10 | integrity audit | The copy restores but violates an invariant (orphans, scope mismatch, embedding-state drift). Keep the copy with `KEEP=1` and read the audit report named in the drill report. |
| 11 | HNSW rebuild | The vector index could not be rebuilt on the copy (memory too low for the graph, or the index definition drifted). The copy is dropped; rerun with a larger `HNSW_WORK_MEM`. |
| 9 | unexpected | Anything unhandled; the log above names the command. |

On any non-zero exit, keep the dump and the report, and do **not** delete the last
known-good backup. `KEEP=1` retains the throwaway database for inspection; rerun
with `KEEP_DUMP=1` if the dump was already deleted. If the same exit repeats on
consecutive runs, restore the newest dump into a scratch database by hand and
compare — the report already contains the expected counts.

Measured on 2026-11-04 against a 100,000-observation database (158 MB): dump
3.5 MB in 0.5 s, checksum 0.3 s, restore 4.7–6.3 s, counts 0.6–1.0 s, retrieval
0.4–0.5 s — 8–10 s end to end. `VALIDATE_FLYWAY=1` adds about 3.2 s (jar boot).

Measured on 2026-11-05 against the 1,000,198-observation / 18 GB database with
1M vectors and the HNSW index: dump **526 s** (4.74 GB, ≈3.8×), checksum 31 s,
restore **2,448 s (40.8 min)**, counts 1.7 s, retrieval 7.6 s (1,000,000 hits
matched on both sides), integrity 5.9 s (9 checks, 0 findings), HNSW rebuild
**550 s** (7,582,621,696 B, 7,581 B/row) — **59.6 min end to end**. Disk peak was
**39.5 GB (36.8 GiB)**: source 19.6 GB + restored copy 15.2 GB + dump 4.7 GB,
against an 18 GB live database (≈2.7×). Budget that much free space before
running the loop on a production host.

The restore is mostly index-build time: `pg_restore` replays `CREATE INDEX ...
USING hnsw` with whatever `maintenance_work_mem` the server has. At the default
64 MB it printed `hnsw graph no longer fits into maintenance_work_mem after
898,013 tuples` and spent ~40 of the 41 restore minutes on that one index; the
same build at 4 GB took 9.2 min. Raise `maintenance_work_mem` before restoring,
or dump without the vector index and rebuild once afterwards with
`REBUILD_HNSW=1`. Full report: `docs/archive/2026-11-05_ops-loop-1m.md`.

**The drill does not hard-code a host-specific value.** `RESTORE_WORK_MEM` and
`RESTORE_WORKERS` are applied to the throwaway database with `ALTER DATABASE ...
SET` immediately before `pg_restore`, then reset; both are unset by default,
which means "use whatever the server currently has". `HNSW_WORK_MEM` and
`HNSW_WORKERS` behave the same for the rebuild stage. Every report records the
**effective** values under `restore_guc` and `hnsw_rebuild`, so a timing can be
attributed to a setting instead of to a guess about the host.

```bash
# Restore a 1M-vector copy without spilling the graph (~9 min instead of ~41).
SOURCE_DB=claude_mem RESTORE_WORK_MEM=4GB RESTORE_WORKERS=2 \
  REBUILD_HNSW=1 HNSW_WORK_MEM=4GB HNSW_WORKERS=2 \
  ./scripts/backup-restore-drill.sh
```

The cost of leaving the defaults in place is measured, on the same clustered
1024-d data the rebuild benchmark uses:

| Rows | `maintenance_work_mem` / workers | HNSW build | vs default |
|------|----------------------------------|------------|------------|
| 100,000 | 64 MB / 0 (stock) | 561.6 s | — |
| 100,000 | 1 GB / 2 | 95.5 s | **5.9× faster** |
| 300,000 | 2 GB / 2 | 106.2 s | fits the 2.3 GB graph |
| 300,000 | 4 GB / 2 | 110.2 s | more memory does not help once it fits |
| 1,000,000 | 4 GB / 2 | 584.6 s | fits the 7.8 GB graph |

The only decision that matters is "does the graph fit": once
`maintenance_work_mem` is at least the index size, extra memory buys nothing
(300k: 106 s vs 110 s). Below it, pgvector spills and the build becomes I/O
bound — 64 MB at 100k is 5.9× slower, and 64 MB at 1M made the restore take
2,448 s where the 4 GB rebuild took 550 s. Size the value from the measured
constant **≈8,192 bytes per vector row**, not from the machine's RAM.

#### Vector Index Rebuild After a Bulk Backfill

A bulk import with vectors present is dominated by graph maintenance: pgvector
adds every row to the HNSW graph, which measured **38–53 rows/s at 1M rows**
(~19–26 ms per row) against **900–1,300 rows/s with no graph** — a 20–30×
throughput loss for the same data. The documented recipe is therefore: load the
rows first, then build the graph once.

```bash
# 1. Bulk load (proxy import or SQL) — the index on embedding_1024 must not exist yet
# 2. Build it once, with enough memory for the graph to stay in RAM
SOURCE_DB=claude_mem WORK_MEM=8GB WORKERS=2 ./scripts/rebuild-vector-index.sh
# 3. Verify: the script checks indisvalid, prints the build time and the index bytes
```

Measured build cost (dedicated container, `m=16, ef_construction=64`):

| Rows | Index bytes | `maintenance_work_mem` | Build |
|------|-------------|------------------------|-------|
| 100k | 781 MB | 1 GB | 36.9 s |
| 300k | 2,344 MB | 2 GB | 70.5 s |
| 1M | 7.2–7.8 GB | 8 GB (4 GB spills after ~898k rows) | 801.6 s |

The index is **~8,192 bytes per vector row**, so `maintenance_work_mem` must
exceed the final index size or the build spills to disk — Postgres then logs
`hnsw graph no longer fits into maintenance_work_mem after N tuples` and the
build slows markedly. Plan for 1 GB / 8 GB / 24 GB at 100k / 1M / 3M rows. Above
roughly 2M rows a single graph no longer fits on a 16–32 GB host; shard the
vector table by scope instead. Measured at full 3M capacity: 8 × 375,000-row
tables build in **89.7–106.8 s each (781.9 s total)** at a 4 GB budget, 3.0 GB
per index and 22.9 GiB overall, with **no spill** — 261 µs/row versus 550–802 µs
for the single 1M graph. Query p50 is **78 ms** on a 375k shard versus 84 ms on
the 1M graph (p95 151 vs 176 ms), so a scope-scoped query stays flat while a
cross-scope query pays fan-out over N shards. Full report and the trade-off
table: `docs/archive/2026-11-05_vector-sharding-3m.md`.

**Re-running is safe.** `scripts/rebuild-vector-index.sh` treats the index state
as the source of truth:

| State found | Action |
|-------------|--------|
| valid index, `FORCE=0` | print `skip: … already valid` and exit 0 — no rebuild |
| invalid index (left by an interrupted build) | drop it, then rebuild |
| no index | build |
| build fails or is killed | drop the partial index, exit 3 |

`CONCURRENTLY=1` (the default) keeps INSERTs flowing while the graph is built;
`CONCURRENTLY=0` is faster but takes a SHARE lock, so writes wait for the whole
build. `scripts/test-vector-index-rebuild.sh` proves all of it against a
throwaway database and is the script to rerun after any change to the build
path. At 120,000 rows (`WORK_MEM=1GB`, 2 workers):

| Case | Result |
|------|--------|
| first build | **48 s**, index valid, 983,048,192 B (8,192 B/row) |
| rerun | `skip: … already valid` in **1 s** — no build |
| interrupt with `pg_terminate_backend` | **exit 3**, partial index dropped, state `absent` |
| rerun after the interruption | valid index in **50 s** |
| concurrent build (`CONCURRENTLY=1`) | **918 writes, 0 failures**, p50 48 ms, p95 84 ms, max 206 ms (docker-exec round trip included), index valid |

#### Data Integrity Audit (Scheduled)

`scripts/data-integrity-audit.py` is a read-only inspection of the rows the
application assumes are consistent. It reports nine classes, each with a sample
and an executable repair statement:

| Check | What it catches |
|-------|-----------------|
| `orphan_observations` / `orphan_summaries` | `conversation_id` has no parent in `mem_sessions` |
| `scope_project_mismatch` | a project-scope row whose `scope_key` is another project (it would appear in that project's search) |
| `conversation_project_mismatch` | an observation's `project_path` disagrees with its conversation |
| `embedding_state_mismatch` | `ready` without a vector (invisible to semantic search) or a vector behind `failed`/`skipped` (never retried) |
| `duplicate_hash_unsuppressed` | equal `content_hash` in the same conversation inside the 30-second dedupe window |
| `stuck_processing` | `processing` older than `--stuck-minutes` (default 15) |
| `pending_backlog` | pending count above `--backlog-max` (100) or head older than `--backlog-age-minutes` (60) |
| `summary_snapshot_missing_obs` | a summary input snapshot names an observation that no longer exists |

The default is **dry-run**: nothing is written and the repair SQL is printed and
stored in the JSON report. `--apply` runs only the safe repairs (embedding state,
stuck rows); `--apply-destructive` also deletes orphan/duplicate rows and rewrites
scope links, and must be reviewed first. Exit codes: `0` clean, `1` findings,
`2` could not inspect (container, database, or schema problem).

```bash
# Daily, read-only, on the live database
python3 scripts/data-integrity-audit.py --database claude_mem \
  --json "$HOME/.claude-mem/integrity-audit/audit-$(date +%Y%m%d_%H%M%S).json"

# Weekly, on the copy the backup drill just restored (stage exit 10 on findings)
SOURCE_DB=claude_mem INTEGRITY_AUDIT=1 ./scripts/backup-restore-drill.sh
```

**How often**: daily on the live database (sub-second at 100k rows) because
`stuck_processing` and `pending_backlog` are leading indicators, and weekly on
the restored copy through `INTEGRITY_AUDIT=1` to prove the backup also carries
consistent data. Run once after every restore and after any manual SQL surgery.

**Where**: reports go next to the backup reports,
`${HOME}/.claude-mem/backup-drill/reports/integrity-drill-<stamp>.json` when run
by the drill, or `${HOME}/.claude-mem/integrity-audit/` from cron; prune with
`find ... -mtime +30 -delete`. Every report embeds `repair_sql`, so the
investigation does not depend on this document staying in sync.

**On findings**: take a dump first, read the sample ids and the `detail` field, and
run the repair SQL with `--apply` on a restored copy before touching production.
Orphan and duplicate deletes are destructive; `conversation_project_mismatch` and
`summary_snapshot_missing_obs` are review-only (the audit deliberately refuses to
guess which side is right), so fix them by hand or regenerate the summary.
`pending_backlog` is not repaired by the audit: check the worker log and provider
health instead. The detection is verified against seeded dirty rows by
`python3 scripts/test-data-integrity-audit.py`, which must pass after any schema
change.

### 8.2 Data Recovery

#### Full Recovery

```bash
# 1. Stop application
docker compose stop claude-mem

# 2. Restore database
gunzip -c backup_20260313_020000.sql.gz | \
  docker exec -i cortex-ce-postgres psql -U postgres claude_mem

# 3. Start application
docker compose start claude-mem

# 4. Verify recovery
curl http://localhost:37777/actuator/health
```

#### Table-Level Recovery

```bash
# Restore observations table only
docker exec -i cortex-ce-postgres psql -U postgres claude_mem << EOF
TRUNCATE TABLE mem_observations CASCADE;
EOF

gunzip -c backup.sql.gz | grep -A 1000000 "COPY mem_observations" | \
  docker exec -i cortex-ce-postgres psql -U postgres claude_mem
```

### 8.3 Disaster Recovery

#### Disaster Recovery Plan

1. **Data Backup Strategy**:
   - Daily full backup (retain 30 days)
   - Weekly incremental backup (retain 12 weeks)
   - Offsite backup (recommended)

2. **Recovery Time Objective (RTO)**: < 1 hour

3. **Recovery Point Objective (RPO)**: < 24 hours

#### Quick Recovery Procedure

```bash
#!/bin/bash
# disaster_recovery.sh

# 1. Stop all services
docker compose down

# 2. Clean data volumes (use with caution!)
# NOTE: Volume name uses COMPOSE_PROJECT_NAME prefix (default: directory name).
# Run `docker volume ls | grep postgres` to find the actual volume name.
docker volume rm ${COMPOSE_PROJECT_NAME:-bluecortexce}_postgres_data

# 3. Start services
docker compose up -d postgres

# 4. Wait for database to be ready
sleep 10

# 5. Restore data
gunzip -c /backups/latest.sql.gz | \
  docker exec -i cortex-ce-postgres psql -U postgres claude_mem

# 6. Start application
docker compose up -d claude-mem

# 7. Verify
curl http://localhost:37777/actuator/health
```

---

## Appendix

### A. Docker Common Commands

```bash
# Service management
docker compose up -d                  # Start services
docker compose down                   # Stop services
docker compose restart claude-mem     # Restart application
docker compose logs -f claude-mem     # View logs

# Image management
docker compose pull                   # Pull latest images
docker compose build                  # Build images
docker images | grep claude-mem       # List images

# Container management
docker compose ps                     # View status
docker stats claude-mem-java          # Resource monitoring
docker exec -it claude-mem-java bash  # Enter container

# Cleanup
docker system prune -a               # Clean unused resources
docker volume prune                  # Clean unused volumes
```

### B. Database Common Queries

```sql
-- View table sizes
SELECT
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- View active connections
SELECT
  pid,
  usename,
  application_name,
  client_addr,
  state,
  query_start,
  query
FROM pg_stat_activity
WHERE datname = 'claude_mem';

-- View index usage
SELECT
  schemaname,
  tablename,
  indexname,
  idx_scan,
  idx_tup_read,
  idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- View recent migrations
SELECT
  version,
  description,
  type,
  script,
  installed_on,
  execution_time
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;
```

### C. API Quick Test

```bash
# Health check
curl http://localhost:37777/api/health

# Create test session
curl -X POST http://localhost:37777/api/session/start \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test-session-001",
    "project_path": "/tmp/test-project"
  }'

# Record observation
curl -X POST http://localhost:37777/api/ingest/observation \
  -H "Content-Type: application/json" \
  -d '{
    "content_session_id": "test-session-001",
    "project_path": "/tmp/test-project",
    "title": "Deployment verification",
    "content": "Test observation for deployment verification"
  }'

# Search observations (project is required)
curl "http://localhost:37777/api/search?project=/tmp/test-project&query=test&limit=10"

# View statistics
curl http://localhost:37777/api/stats
```

### D. Performance Benchmark

```bash
# Install wrk
# macOS: brew install wrk
# Linux: apt-get install wrk

# Health check benchmark
wrk -t4 -c100 -d30s http://localhost:37777/actuator/health

# Search API benchmark
wrk -t4 -c50 -d30s \
  -s scripts/bench_search.lua \
  http://localhost:37777/api/search
```

---

## Contact and Support

- **GitHub Issues**: https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues
- **Documentation**: https://github.com/Blueforce-Tech-Inc/BlueCortexCE/docs
- **Community**: (Coming soon)

---

**Last Updated**: 2026-05-04
**Version**: 0.1.0-beta
