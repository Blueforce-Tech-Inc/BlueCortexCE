# Cortex Community Edition

> 🧠 Memory-Enhanced Agent System with Persistent Context

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2+-brightgreen.svg)](https://spring.io/projects/spring-boot)

[中文](README-zh-CN.md) | English

Cortex Community Edition (cortexce) is a memory-enhanced system that provides persistent context and intelligent memory management for AI assistants. Through quality scoring, intelligent refinement, and context-aware retrieval, it helps AI assistants accumulate experience and learn across sessions.

---

## Key Features

- 🧠 **Persistent Memory** - Save and reuse context across sessions
- 🔄 **Intelligent Quality Assessment** - Rule-based and LLM-based quality scoring
- 🎯 **Context-Aware Retrieval** - Vector-based semantic search
- 📊 **Memory Evolution** - Automatic refinement, merging, and optimization
- 🔌 **RESTful API** - Simple and easy-to-use HTTP interface
- 🐘 **PostgreSQL + pgvector** - Scalable vector storage solution

---

## Quick Start

### Prerequisites

- Java 17 or higher
- PostgreSQL 16+ (with pgvector extension)
- Maven 3.8+ (for building)

### Installation

1. **Clone the repository**

```bash
git clone https://github.com/yourusername/cortexce.git
cd cortexce
```

2. **Configure database**

```bash
# Create PostgreSQL database
createdb cortexce

# Enable pgvector extension
psql -d cortexce -c "CREATE EXTENSION vector;"
```

3. **Configure environment**

```bash
cp .env.example .env
# Edit .env file with your configuration
```

4. **Build the project**

```bash
mvn clean install
```

5. **Run the service**

```bash
java -jar backend/target/cortexce-backend-*.jar
```

The service will start at `http://localhost:37777`.

---

## Architecture

Cortex adopts a "Thin Proxy + Fat Server" architecture to solve CLI Hook timeout issues:

```
┌─────────────────┐
│   CLI Hook      │
│  (wrapper.js)   │
└────────┬────────┘│         ┌──────────────────┐
         │ HTTP    │         │  Fat Server      │
         ▼         │         │  (Spring Boot)   │
┌─────────────────┐│         │                  │
│  Thin Proxy     │├────────▶│  - LLM Processing│
│  (Express)      ││         │  - Embedding     │
│  - Fast Response││         │  - Refinement    │
│  - Forwarding   ││         │  - Quality Score │
└─────────────────┘│         └──────────────────┘
                   │                   │
                   │                   ▼
                   │         ┌──────────────────┐
                   │         │  PostgreSQL      │
                   │         │  + pgvector      │
                   │         └──────────────────┘
```

### Core Components

- **Thin Proxy**: Lightweight proxy for fast CLI Hook response
- **Fat Server**: Core business logic with async processing
- **PostgreSQL + pgvector**: Persistent storage and vector retrieval

---

## API Documentation

### Core Endpoints

#### 1. Health Check

```http
GET /api/health
```

#### 2. Create Observation

```http
POST /api/observations
Content-Type: application/json

{
  "session_id": "session-123",
  "project_path": "/path/to/project",
  "content": "Observation content...",
  "embedding": [0.1, 0.2, ...]
}
```

#### 3. Semantic Search

```http
GET /api/search?query=search+content&project=/path/to/project&limit=10
```

#### 4. Memory Refinement

```http
POST /api/refine
Content-Type: application/json

{
  "project_path": "/path/to/project"
}
```

For complete API documentation, see [API.md](docs/API.md)

---

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL connection string | `jdbc:postgresql://localhost:5432/cortexce` |
| `DATABASE_USER` | Database username | `postgres` |
| `DATABASE_PASSWORD` | Database password | - |
| `LLM_API_KEY` | LLM API key | - |
| `LLM_BASE_URL` | LLM API endpoint | - |
| `EMBEDDING_MODEL` | Embedding model name | `text-embedding-3-small` |

For complete configuration, see [`.env.example`](.env.example)

---

## Development Guide

### Project Structure

```
cortexce/
├── backend/           # Spring Boot main application
│   ├── src/main/java/
│   └── src/test/java/
├── proxy/             # Thin Proxy (Node.js)
├── openclaw-plugin/   # OpenClaw integration plugin
├── scripts/           # Utility scripts
└── docs/              # Documentation
```

### Build and Test

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package
mvn package

# Run
java -jar backend/target/cortexce-backend-*.jar
```

For detailed development guide, see [DEVELOPMENT.md](docs/DEVELOPMENT.md)

---

## Deployment Guide

### Docker Deployment

```bash
# Build image
docker build -t ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main .

# Run container
docker run -d \
  -p 37777:37777 \
  -e DATABASE_URL=... \
  -e LLM_API_KEY=... \
  ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main
```

For detailed deployment guide, see [DEPLOYMENT.md](docs/DEPLOYMENT.md)

---

## End User Quick Setup Guide

This section guides you through integrating Claude Code with Cortex CE memory system.

### Prerequisites

- **Docker and Docker Compose** installed
- **Node.js** installed (needed to run `node` command)
- **Claude Code** installed

### Expected Directory Structure

This guide assumes you will create a deployment directory `.cortexce` under your home directory:

```
~/.cortexce/                         # Deployment directory
├── .env                            # Environment configuration
├── docker-compose.yml               # Docker Compose configuration
└── proxy/                          # Claude Code Hooks proxy
    ├── wrapper.js                   # Main entry script
    ├── package.json
    └── ...
```

### Step 1: Create Working Directory and Configure Environment Variables

Create the working directory and `.env` file:

```bash
mkdir -p ~/.cortexce
cat > ~/.cortexce/.env << 'EOF'
# Database configuration
DB_PASSWORD=your_secure_password

# LLM configuration (Spring AI OpenAI format)
SPRING_AI_OPENAI_API_KEY=your_openai_key
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-chat

# Embedding model configuration (Spring AI OpenAI format)
SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_siliconflow_key
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024
EOF
```

### Step 2: Copy Required Files

Assuming you have cloned the repository to `[git directory]`, navigate to your working directory and copy files:

```bash
cd ~/.cortexce

# Copy docker-compose.yml
cp [git directory]/docker-compose.yml .

# Copy proxy directory
cp -r [git directory]/proxy .

# Install proxy dependencies
cd proxy && npm install
```

### Step 3: Start Services

```bash
cd ~/.cortexce

# Start services
docker compose up -d

# Or specify image
IMAGE_NAME=ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main docker compose up -d

# For ARM64 Macs (Apple Silicon), the image will be pulled automatically
# For Intel Macs, you can force AMD64 platform:
# docker pull --platform linux/amd64 ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main
```

Wait for services to start, verify health:

```bash
curl http://localhost:37777/actuator/health
```

Should return `{"status":"UP",...}`.

### Step 4: Configure Claude Code Hooks

Edit the project-level configuration file `.claude/settings.local.json` (create if it doesn't exist):

```json
{
  "hooks": {
    "SessionStart": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js session-start --url http://127.0.0.1:37777",
        "async": true
      }]
    }],
    "PostToolUse": [{
      "matcher": "Edit|Write|Read|Bash",
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js tool-use --url http://127.0.0.1:37777",
        "async": true
      }]
    }],
    "SessionEnd": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js session-end --url http://127.0.0.1:37777",
        "async": true
      }]
    }],
    "UserPromptSubmit": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js user-prompt --url http://127.0.0.1:37777",
        "async": true
      }]
    }]
  }
}
```

#### Hook Description

| Hook | Trigger |
|------|---------|
| SessionStart | When Claude Code starts |
| PostToolUse | After tool execution (Edit/Write/Read/Bash) |
| SessionEnd | When session ends |
| UserPromptSubmit | When user submits prompt |

#### Optional: Enable Folder CLAUDE.md Update (skip if not needed)

To automatically update subfolder `CLAUDE.md` files (inject relevant memories), you can add `--enable-folder-claudemd` parameter (note: this may add some noise, not recommended unless needed).

### Step 5: Configure MCP Server (for active memory retrieval)

```bash
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse
```

Verify configuration:

```bash
claude mcp list
```

Or enter `/mcp` in Claude Code input box to view.

### Common Commands

```bash
cd ~/.cortexce

# Start services
docker compose up -d

# Restart services
docker compose restart

# View logs
docker compose logs -f cortex-ce

# Stop services
docker compose down

# Complete reset (including data)
docker compose down -v
```

### Troubleshooting

#### Cannot Connect to Java API

```bash
# Check if service is running
curl http://localhost:37777/actuator/health

# Check port usage
lsof -i :37777
```

#### Hook Not Triggered

- Verify `.claude/settings.local.json` path is correct
- Verify `matcher` matches tool names
- Check Claude Code logs

#### Database Connection Failed

- Confirm `DB_PASSWORD` is set in `.env`
- Confirm `POSTGRES_PORT` in `.env` (default 5433, to avoid conflict with local PostgreSQL)
- Wait for PostgreSQL health check to pass (first startup needs time)

#### No Data in WebUI

- Check `/stream` SSE endpoint:

```bash
curl -N http://localhost:37777/stream
```

- Verify database has data:

```bash
docker exec cortex-ce-postgres psql -U postgres -d claude_mem -c "SELECT COUNT(*) FROM mem_observations;"
```

---

## Contributing

We welcome all forms of contribution!

- 🐛 Report bugs: [Submit Issue](https://github.com/yourusername/cortexce/issues)
- 💡 Propose features: [Feature Request](https://github.com/yourusername/cortexce/issues)
- 📝 Improve documentation: Fork and submit PR
- 🔧 Contribute code: Please read [CONTRIBUTING.md](CONTRIBUTING.md)

---

## Acknowledgments

This project is inspired by the concept of persistent memory systems. Thanks to all researchers and developers who have contributed to the development of AI memory systems.

Special thanks to:
- The persistent memory systems research community
- PostgreSQL and pgvector teams
- Spring Boot community

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

---

## Contact

- GitHub Issues: [https://github.com/yourusername/cortexce/issues](https://github.com/yourusername/cortexce/issues)
- Documentation: [https://docs.cortexce.ai](https://docs.cortexce.ai) (Coming soon)
