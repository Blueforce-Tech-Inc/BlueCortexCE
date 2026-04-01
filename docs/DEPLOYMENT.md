# Cortex Community Edition Deployment Guide

> **中文版**: [DEPLOYMENT-zh-CN.md](DEPLOYMENT-zh-CN.md)

This guide provides comprehensive instructions for deploying Cortex Community Edition in production environments.

## Table of Contents

- [1. System Requirements](#1-system-requirements)
- [2. Docker Deployment](#2-docker-deployment)
- [3. Production Configuration](#3-production-configuration)
- [4. Database Migration](#4-database-migration)
- [5. Environment Variables](#5-environment-variables)
- [6. Monitoring and Logging](#6-monitoring-and-logging)
- [7. Troubleshooting](#7-troubleshooting)

## 1. System Requirements

### Hardware Requirements

- **CPU**: 2+ cores
- **Memory**: 4GB+ RAM
- **Disk**: 20GB+ SSD

### Software Requirements

- **Java**: 21 or higher
- **PostgreSQL**: 16 with pgvector extension
- **Docker**: 20.10+ (for container deployment)
- **Docker Compose**: 2.0+ (optional)

## 2. Docker Deployment

### Using Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd BlueCortexCE

# Copy environment template and edit
cp .env.docker .env
vim .env

# Start all services (uses pre-built image from GHCR)
docker compose up -d

# Check health
curl http://localhost:37777/api/health
```

### Using a Custom Image

```bash
# Use specific version
IMAGE_NAME=ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:sha-abc123 docker compose up -d

# Build and use local image
git submodule update --init --recursive
docker build -t cortex-ce:local .
IMAGE_NAME=cortex-ce:local docker compose up -d
```

### Manual Docker Deployment

```bash
# Build the image (initialize webui submodule first)
git submodule update --init --recursive
docker build -t cortex-ce:latest .

# Run the container
docker run -d \
  -p 37777:37777 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/claude_mem \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=your_password \
  -e SPRING_AI_OPENAI_API_KEY=sk-your-key \
  cortex-ce:latest
```

## 3. Production Configuration

### Database Configuration

Database schema is managed by Flyway migrations. The application connects using:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/claude_mem
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
```

### JVM Options

Default JVM options in docker-compose.yml:

```bash
JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
```

## 4. Database Migration

Database migrations run automatically on application startup via Flyway. Migration scripts are located in `backend/src/main/resources/db/migration/`.

To run migrations manually:

```bash
cd backend
mvn flyway:migrate
```

## 5. Environment Variables

### Required Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_NAME` | PostgreSQL database name | `claude_mem` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | - |
| `SPRING_AI_OPENAI_API_KEY` | OpenAI/DeepSeek API key | - |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | Embedding API key (e.g. SiliconFlow) | - |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile (`dev`/`prd`) | `prd` |
| `SERVER_ADDRESS` | Server bind address | `0.0.0.0` |
| `SPRING_AI_OPENAI_BASE_URL` | OpenAI compatible API endpoint | `https://api.openai.com` |
| `SPRING_AI_OPENAI_CHAT_MODEL` | Chat model | `gpt-4o` |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | Embedding API endpoint | `https://api.openai.com` |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding model | `text-embedding-3-small` |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | Embedding dimensions | `1536` |
| `CLAUDE_MEM_MODE` | Application mode | `code` |
| `MEMORY_REFINE_ENABLED` | Enable memory refinement (self-evolution) | `true` |
| `CLAUDEMEM_LOG_DIR` | Log directory | `~/.claude-mem/logs` |
| `JAVA_OPTS` | JVM options | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` |

### Anthropic Compatible API (Claude, GLM, etc.)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_AI_ANTHROPIC_API_KEY` | **Yes** | - | Anthropic API key (alias: `ANTHROPIC_API_KEY`) |
| `SPRING_AI_ANTHROPIC_BASE_URL` | No | `https://api.anthropic.com` | API base URL (alias: `ANTHROPIC_BASE_URL`) |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | No | `claude-sonnet-4-5` | Model name (alias: `ANTHROPIC_MODEL`) |
| `CLAUDEMEM_LLM_PROVIDER` | No | `openai` | LLM provider (`openai` or `anthropic`) |

See `.env.docker` for a complete template.

## 6. Monitoring and Logging

### Health Check

```bash
# Custom health endpoint (recommended)
curl http://localhost:37777/api/health

# Spring Boot Actuator
curl http://localhost:37777/actuator/health
```

### Metrics

```
GET /actuator/metrics
```

### Log Configuration

Logs are persisted in the `claude-mem-logs` Docker volume at `/app/logs`.

Configure logging levels via environment variable or `application.properties`:

```properties
logging.level.root=INFO
logging.level.com.ablueforce.cortexce=DEBUG
```

## 7. Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Check PostgreSQL is running: `docker compose logs -f postgres`
   - Verify connection credentials in `.env`
   - Check healthcheck: `docker compose ps`

2. **Out of Memory**
   - Increase JVM heap via `JAVA_OPTS`
   - Check memory usage with `/actuator/metrics`

3. **Slow Response**
   - Check database query performance
   - Review connection pool settings
   - Monitor CPU and memory usage

### Docker Registry Issues (China/Corporate Firewall)

If pulling images fails, use mirror registries:

```bash
docker pull docker.1ms.run/library/eclipse-temurin:21-jdk
docker tag docker.1ms.run/library/eclipse-temurin:21-jdk eclipse-temurin:21-jdk
```

See [DOCKER_README.md](../DOCKER_README.md) for detailed mirror configuration.

---

*See also: [Chinese Version](DEPLOYMENT-zh-CN.md)*
