# Cortex Community Edition Docker Setup

> 中文版: [DOCKER_README-zh-CN.md](./DOCKER_README-zh-CN.md)

## Quick Start

```bash
# 1. Copy environment template
cp .env.docker .env

# 2. Edit .env with your actual API keys
vim .env

# 3. Start services (uses pre-built image from ghcr.io)
docker compose up -d

# 4. Check health
curl http://localhost:37777/api/health

# 5. View logs
docker compose logs -f
```

## Using Custom Image

By default, the compose file uses the pre-built image from GitHub Container Registry:

```bash
# Default image
docker compose up -d
```

To use a custom image version:

```bash
# Use specific version
IMAGE_NAME=ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:sha-abc123 docker compose up -d

# Use local image (after building locally)
IMAGE_NAME=cortex-ce:local docker compose up -d
```

## Services

| Service | Host Port | Container Port | Description |
|---------|-----------|----------------|-------------|
| PostgreSQL | 5433 | 5432 | Database with pgvector |
| CortexCE | 37777 | 37777 | REST API & MCP Server |

## Configuration

### Required Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_PASSWORD` | Database password (see `.env.docker` template) | - |
| `SPRING_AI_OPENAI_API_KEY` | OpenAI/DeepSeek API key | - |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | Embedding API key (e.g. SiliconFlow) | - |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_NAME` | PostgreSQL database name | `claude_mem` |
| `DB_USERNAME` | Database username | `postgres` |
| `POSTGRES_PORT` | PostgreSQL port on host | `5433` |
| `POSTGRES_DATA_PATH` | PostgreSQL data volume (host path) | `postgres_data` |
| `IMAGE_NAME` | Docker image to use | `ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main` |
| `SPRING_PROFILES_ACTIVE` | Spring profile (`dev`/`prd`) | `prd` |
| `SPRING_AI_OPENAI_BASE_URL` | OpenAI compatible API endpoint | `https://api.openai.com` |
| `SPRING_AI_OPENAI_CHAT_MODEL` | Chat model | `gpt-4o` |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | Embedding API endpoint | `https://api.openai.com` |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding model | `text-embedding-3-small` |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | Embedding dimensions | `1536` |
| `CLAUDE_MEM_MODE` | Application mode (`code`/`default`) | `code` |
| `CLAUDEMEM_LLM_PROVIDER` | LLM provider (`openai`/`anthropic`) | `openai` |
| `SPRING_AI_ANTHROPIC_API_KEY` | Anthropic API key (when provider=anthropic) | - |
| `SPRING_AI_ANTHROPIC_BASE_URL` | Anthropic API endpoint | `https://api.anthropic.com` |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | Anthropic model name | `claude-sonnet-4-5` |
| `MEMORY_REFINE_ENABLED` | Enable memory refinement (self-evolution) | `true` |
| `LOGS_PATH` | Application logs volume (host path) | `claude-mem-logs` |
| `JAVA_OPTS` | JVM options | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` |
| `SERVER_PORT` | Application port on host | `37777` |

> **Note:** `SERVER_ADDRESS` is set to `0.0.0.0` by the Docker Compose `docker-compose.yml` environment variable and cannot be overridden from the host — the application must bind to `0.0.0.0` inside the container to be accessible via mapped ports.

> **Embedding Provider Note:** The default embedding settings above (`api.openai.com`, `text-embedding-3-small`, 1536 dimensions) match Docker Compose's `prd` profile defaults. **SiliconFlow is the recommended embedding provider** (see `.env.docker` for the recommended values: `https://api.siliconflow.cn`, `BAAI/bge-m3`, 1024 dimensions). For local development, `backend/.env.example` also uses SiliconFlow defaults.

## Commands

```bash
# Start all services
docker compose up -d

# Rebuild and start
docker compose up -d --build

# Stop services
docker compose down

# View logs
docker compose logs -f claude-mem

# View database logs
docker compose logs -f postgres

# Reset everything (including data)
docker compose down -v
```

## Troubleshooting

### Check service health

```bash
curl http://localhost:37777/api/health
```

### Access PostgreSQL

```bash
docker compose exec postgres psql -U postgres -d claude_mem
```

### Rebuild after dependency changes

```bash
docker compose build --no-cache claude-mem
```

### Network Issues (China/Corporate Firewall)

If you encounter Docker registry connection issues (e.g., `connection refused` when pulling images), use these workarounds:

#### Option 1: Pull from Mirror Registry and Re-tag

Pull base images from mirror registries and re-tag them:

```bash
# Pull base images from mirror
docker pull docker.1ms.run/library/eclipse-temurin:21-jdk
docker pull docker.1ms.run/library/eclipse-temurin:21-jre

# Re-tag to standard names
docker tag docker.1ms.run/library/eclipse-temurin:21-jdk eclipse-temurin:21-jdk
docker tag docker.1ms.run/library/eclipse-temurin:21-jre eclipse-temurin:21-jre

# Pull pgvector from mirror
docker pull docker.1ms.run/pgvector/pgvector:pg16
docker tag docker.1ms.run/pgvector/pgvector:pg16 pgvector/pgvector:pg16
```

Other mirror registries you can try:
- `docker.1ms.run`
- `docker.xuanyuan.me`
- `m.daocloud.io/docker.io`

#### Option 2: Configure OrbStack Proxy (macOS)

If you're using OrbStack, configure the network proxy:

```bash
# Set proxy (replace with your proxy address)
orb config set network_proxy http://127.0.0.1:9981

# Verify configuration
orb config show | grep network_proxy
```

#### Option 3: Configure Docker Desktop Proxy

For Docker Desktop, add proxy configuration to `~/.docker/daemon.json`:

```json
{
  "proxies": {
    "default": {
      "httpProxy": "http://127.0.0.1:9981",
      "httpsProxy": "http://127.0.0.1:9981",
      "noProxy": "localhost,127.0.0.1"
    }
  }
}
```

Then restart Docker Desktop.

#### Option 4: Use Registry Mirrors

Add registry mirrors to your Docker configuration:

**OrbStack (`~/.orbstack/config/docker.json`):**
```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

**Docker Desktop (`~/.docker/daemon.json`):**
```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

## Development

For local development with hot-reload, consider using:
- Run PostgreSQL in Docker, app directly on host
- Use Maven hot-reload for Java development

## Building with Dockerfile

The Dockerfile uses a multi-stage build process:

1. **Stage 1 (java-builder)**: Builds the Spring Boot JAR
2. **Stage 2 (runtime)**: Minimal JRE image with the application

**Important**: Before building, initialize the webui submodule and pre-build WebUI resources:

```bash
# Initialize submodules (webui is a submodule)
git submodule update --init --recursive

# Pre-build WebUI resources
./scripts/prebuild-webui.sh

# Build the Docker image
docker build -t cortex-ce:latest .

# Run with environment variables
# NOTE: host.docker.internal requires Linux with Docker 20.10+.
# For macOS/Windows, use Docker Compose instead (docker compose up -d).
docker run -d \
  -p 37777:37777 \
  -e SPRING_PROFILES_ACTIVE=prd \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/claude_mem \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e SPRING_AI_OPENAI_BASE_URL=https://api.openai.com \
  -e SPRING_AI_OPENAI_API_KEY=your-api-key \
  -e SPRING_AI_OPENAI_EMBEDDING_API_KEY=your-embedding-key \
  -e SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.openai.com \
  -e SPRING_AI_OPENAI_EMBEDDING_MODEL=text-embedding-3-small \
  -e SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1536 \
  cortex-ce:latest
```

## End-to-End Testing

The project includes comprehensive E2E test scripts for Docker deployment.

### Run Full E2E Test

```bash
cd scripts

# Run complete test suite (builds image, starts containers, runs tests, cleans up)
./docker-e2e-test.sh --cleanup

# Skip image build (use existing image)
./docker-e2e-test.sh --skip-build --cleanup

# Keep containers running after tests (for debugging)
./docker-e2e-test.sh --keep-running
```

### Test Coverage

The E2E test suite validates:

1. **Health Endpoint** - Application health check
2. **Session Creation** - Create new memory session
3. **Observation Ingestion** - Store observations via API
4. **Observation Retrieval** - Query stored observations
5. **Search Endpoint** - Vector and text search
6. **Stats Endpoint** - Database statistics
7. **Projects Endpoint** - List projects
8. **Session Completion** - Close session
9. **Database Persistence** - Direct DB verification
10. **Container Restart** - Data persistence after restart
11. **WebUI Static Files** - WebUI accessibility

### Docker Compose Test

For testing with Docker Compose:

```bash
cd scripts
./docker-compose-test.sh --cleanup
```

### Test Ports

The test scripts use non-conflicting ports to avoid interference with local development:
- `docker-e2e-test.sh`: PostgreSQL `15432`, Java API `38888`
- `docker-compose-test.sh`: PostgreSQL `15433`, Java API `38889`

## Production Notes

- Change default database password in `.env`
- Consider adding TLS/SSL for production
- The app runs as non-root user inside container
- Logs are persisted in `claude-mem-logs` volume

## Data Persistence

Data is persisted via Docker volumes:

- `postgres_data` — PostgreSQL data directory
- `claude-mem-logs` — Application log directory

## Repository Structure

```
BlueCortexCE/
├── Dockerfile              # Multi-stage Docker build
├── docker-compose.yml     # Docker Compose configuration
├── .env.docker           # Environment template
├── backend/              # Java Spring Boot application
│   ├── src/
│   ├── pom.xml
│   └── ...
├── proxy/                # Claude Code wrapper (Node.js)
├── scripts/              # Deployment and test scripts
├── docs/                 # Documentation
└── webui/                # WebUI (submodule: claude-mem repo)
```

## Environment Variables Example

Copy `.env.docker` to `.env` and configure your keys:

```bash
cp .env.docker .env
```

`.env.docker` template contents:

```bash
# Database
DB_NAME=claude_mem
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password_here

# Spring Profile
SPRING_PROFILES_ACTIVE=prd

# OpenAI / DeepSeek
SPRING_AI_OPENAI_API_KEY=your_api_key_here
SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key_here

# Optional: Switch to Anthropic
# CLAUDEMEM_LLM_PROVIDER=anthropic
# SPRING_AI_ANTHROPIC_API_KEY=your_anthropic_key_here
```

## Security Recommendations

- Always change `DB_PASSWORD` to a strong password in production
- Never commit API keys to version control
- Use `SPRING_PROFILES_ACTIVE=prd` in production
- Consider restricting external access to port 5433 (PostgreSQL)
- The application runs as a non-root user inside the container
- Logs are persisted via the `claude-mem-logs` Docker volume

---

> 中文版: [DOCKER_README-zh-CN.md](./DOCKER_README-zh-CN.md)
