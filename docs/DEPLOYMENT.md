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

# Build with arguments
docker build \
  -t ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main \
  --build-arg JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=80.0" \
  .
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

Flyway Community Edition does not support automatic rollback. Recommended strategy:

```bash
# 1. Backup database
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem > backup_$(date +%Y%m%d).sql

# 2. Manual rollback SQL
psql -U postgres -d claude_mem < rollback_V8.sql

# 3. Update Flyway history
DELETE FROM flyway_schema_history WHERE version = '8';
```

---

## 5. Environment Variables

### 5.1 Core Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | No | `prd` | Spring profile (`dev`/`prd`) |
| `SERVER_PORT` | No | `37777` | HTTP service port |
| `SERVER_ADDRESS` | No | `127.0.0.1` (local JAR); hardcoded `0.0.0.0` in Docker | Bind address |

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
| `SPRING_AI_OPENAI_API_KEY` | **Yes** | - | OpenAI API key |
| `SPRING_AI_OPENAI_BASE_URL` | No | `https://api.openai.com` | API base URL |
| `SPRING_AI_OPENAI_CHAT_MODEL` | No | `gpt-4o` | Chat model name |

#### Anthropic Compatible API (Claude, GLM, etc.)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_AI_ANTHROPIC_API_KEY` | No | - | Anthropic API key — required only when `CLAUDEMEM_LLM_PROVIDER=anthropic` (alias: `ANTHROPIC_API_KEY`) |
| `SPRING_AI_ANTHROPIC_BASE_URL` | No | `https://api.anthropic.com` | API base URL (alias: `ANTHROPIC_BASE_URL`) |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | No | `claude-sonnet-4-5` | Model name (alias: `ANTHROPIC_MODEL`) |
| `CLAUDEMEM_LLM_PROVIDER` | No | `openai` | LLM provider (`openai`/`anthropic`) |

### 5.4 Embedding Model Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | **Yes** | - | Embedding API key |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | No | `https://api.openai.com` | Embedding API URL |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | No | `text-embedding-3-small` | Embedding model name |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | No | `1536` | Embedding dimensions (768/1024/1536) |

### 5.5 Runtime Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CLAUDE_MEM_MODE` | No | `code` | Memory mode (`code`/`default`) |
| `CLAUDEMEM_LOG_DIR` | No | `~/.claude-mem/logs` | Log directory |
| `MEMORY_REFINE_ENABLED` | No | `true` | Enable memory refinement (self-evolution) |
| `JAVA_OPTS` | No | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` | JVM options |

### 5.6 Data Persistence Paths (Docker Compose)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `POSTGRES_DATA_PATH` | No | `postgres_data` | PostgreSQL data volume path (Docker Compose host path) |
| `LOGS_PATH` | No | `claude-mem-logs` | Application logs volume path (Docker Compose host path) |

### 5.7 Configuration Examples

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
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"},
    "staleMessageQueue": {
      "status": "UP",
      "details": {
        "staleCount": 0,
        "threshold": 5
      }
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

#### Log Configuration

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.claudemem: INFO
    org.springframework.web: WARN
    org.hibernate.SQL: WARN
  file:
    name: ${CLAUDEMEM_LOG_DIR}/claude-mem.log
    max-size: 10MB
    max-history: 30
```

#### Viewing Logs

```bash
# Docker logs
docker compose logs -f claude-mem

# Real-time log tail
docker compose logs -f --tail=100 claude-mem

# Log file
docker exec claude-mem-java tail -f /app/logs/claude-mem.log

# Search logs
docker exec claude-mem-java grep "ERROR" /app/logs/claude-mem.log
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
| `stale.message.queue.count` | Stale message count | > 5 |

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
| `Port 37777 already in use` | Port conflict | Change `SERVER_PORT` or stop conflicting service |

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
curl http://localhost:37777/actuator/health

# Create test session
curl -X POST http://localhost:37777/api/ingest/session-start \
  -H "Content-Type: application/json" \
  -d '{
    "contentSessionId": "test-session-001",
    "projectPath": "/tmp/test-project",
    "source": "manual"
  }'

# Record observation
curl -X POST http://localhost:37777/api/ingest/observation \
  -H "Content-Type: application/json" \
  -d '{
    "contentSessionId": "test-session-001",
    "observation": "Test observation for deployment verification"
  }'

# Search observations
curl "http://localhost:37777/api/search?query=test&limit=10"

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

**Last Updated**: 2026-04-26
**Version**: 0.1.0-beta
