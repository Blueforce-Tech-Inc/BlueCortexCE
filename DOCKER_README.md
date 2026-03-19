# Cortex Community Edition Docker Setup

## Quick Start

```bash
# 1. Copy environment template
cp .env.docker .env

# 2. Edit .env with your actual API keys
vim .env

# 3. Start services (uses pre-built image from ghcr.io)
docker compose up -d

# 4. Check health
curl http://localhost:37777/actuator/health

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
IMAGE_NAME=ghcr.io/blueforce-tech-inc/cortex-ce:sha-abc123 docker compose up -d

# Use local image (after building locally)
IMAGE_NAME=cortex-ce:local docker compose up -d
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Database with pgvector |
| CortexCE | 37777 | REST API & MCP Server |

## Configuration

### Required Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `postgres` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database name | `cortexce` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `OPENAI_API_KEY` | OpenAI/DeepSeek API key | - |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | SiliconFlow API key (embedding) | - |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_BASE_URL` | OpenAI compatible API endpoint | `https://api.deepseek.com` |
| `OPENAI_MODEL` | Chat model | `deepseek-chat` |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding model | `BAAI/bge-m3` |

## Commands

```bash
# Start all services
docker compose up -d

# Rebuild and start
docker compose up -d --build

# Stop services
docker compose down

# View logs
docker compose logs -f cortexce

# View database logs
docker compose logs -f postgres

# Reset everything (including data)
docker compose down -v
```

## Troubleshooting

### Check service health

```bash
curl http://localhost:37777/actuator/health
```

### Access PostgreSQL

```bash
docker compose exec postgres psql -U postgres -d cortexce
```

### Rebuild after dependency changes

```bash
docker compose build --no-cache cortexce
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

**Important**: Before building, initialize the webui submodule:

```bash
# Initialize submodules (webui is a submodule)
git submodule update --init --recursive

# Build the Docker image
docker build -t cortex-ce:latest .

# Run with environment variables
docker run -d \
  -p 37777:37777 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/cortexce \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e OPENAI_API_KEY=your-api-key \
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
- PostgreSQL: `15432`
- Java API: `38888`

## Production Notes

- Change default database password in `.env`
- Consider adding TLS/SSL for production
- The app runs as non-root user inside container
- Logs are persisted in `cortexce-logs` volume

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
