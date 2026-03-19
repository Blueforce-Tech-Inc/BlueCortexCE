# Claude Code Manual Integration Guide

This document describes how to manually integrate Claude-Mem Java Thin Proxy with Claude Code.

---

## Prerequisites

### 1. Install Dependencies

```bash
cd ~/.cortexce/proxy
npm install
```

### 2. Start Java Backend

```bash
cd ~/.cortexce

# Set API keys (loaded from .env file)
export OPENAI_API_KEY=your_api_key
export SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key

# Start backend (dev profile auto loads .env)
java -jar target/cortexce-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev &
```

Verify backend is running:
```bash
curl http://127.0.0.1:37777/actuator/health
# Should return: {"status":"UP",...}
```

### 3. Verify Thin Proxy

```bash
cd ~/.cortexce/scripts
./thin-proxy-test.sh
# Should all pass
```

---

## Configure Claude Code Hooks

Claude Code supports three configuration file locations:

| Config File | Scope | Description |
|-------------|-------|-------------|
| `.claude/settings.json` | Project | Project-specific settings |
| `.claude/settings.local.json` | Project | Local overrides (not committed) |
| `~/.claude/settings.json` | Global | User-wide settings |

### Option 1: Project-Level Configuration (Recommended)

Create or edit `.claude/settings.local.json`:

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

### Hook Description

| Hook | Trigger |
|------|---------|
| SessionStart | When Claude Code starts |
| PostToolUse | After tool execution (Edit/Write/Read/Bash) |
| SessionEnd | When session ends |
| UserPromptSubmit | When user submits prompt |

---

## Optional: Configure MCP Server

The Java backend supports two MCP transport protocols:

| Protocol | Endpoint | Description |
|----------|----------|-------------|
| **SSE** (default) | `/sse` | Server-Sent Events - stable, well-tested |
| **Streamable HTTP** | `/mcp` | Modern HTTP-based protocol, better for multi-instance |

### Configure MCP Protocol

The MCP protocol is configured in `backend/src/main/resources/application.yml`:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: SSE          # or: STREAMABLE
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        streamable-http:
          mcp-endpoint: /mcp
```

**⚠️ Important**: You can override ANY Spring Boot configuration property via environment variables!

Spring Boot's relaxed binding converts environment variables to property names:
- `SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE`
- `SPRING_AI_MCP_SERVER_STREAMABLE_HTTP_MCP_ENDPOINT=/mcp`

### Using SSE Protocol (Default)

```bash
# SSE is the default - no extra config needed
# Add MCP server with SSE transport:
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse
```

### Using Streamable HTTP Protocol

```bash
# Option 1: Environment variable (no config file edit needed!)
export SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE
export SPRING_AI_MCP_SERVER_STREAMABLE_HTTP_MCP_ENDPOINT=/mcp

# Option 2: Edit application.yml and rebuild
# Set: protocol: STREAMABLE

# Add MCP server with HTTP transport:
claude mcp add --transport http cortexce http://127.0.0.1:37777/mcp
```

> **💡 Tip**: The unified test script `scripts/mcp-e2e-test.sh` auto-detects which protocol your server is using and runs the appropriate tests. You don't need to manually select the test script.

Verify:
```bash
claude mcp list
```

Or enter `/mcp` in Claude Code input box.

---

## Verify Integration

1. Start a new Claude Code session
2. Perform some editing operations
3. Check if observations are saved:

```bash
curl http://127.0.0.1:37777/api/observations?session_id=<session_id>
```

---

## Troubleshooting

### Cannot Connect to Java API

```bash
# Check if service is running
curl http://127.0.0.1:37777/actuator/health

# Check port usage
lsof -i :37777
```

### Hook Not Triggered

- Verify `.claude/settings.local.json` path is correct
- Verify `matcher` matches tool names
- Check Claude Code logs

### Database Connection Failed

- Confirm `DB_PASSWORD` is set in `.env`
- Confirm `POSTGRES_PORT` in `.env` (default 5433)
- Wait for PostgreSQL health check to pass

---

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DATABASE_URL` | PostgreSQL connection | Yes |
| `DATABASE_USERNAME` | DB username | Yes |
| `DATABASE_PASSWORD` | DB password | Yes |
| `OPENAI_API_KEY` | OpenAI API key | Yes* |
| `OPENAI_BASE_URL` | API endpoint | Yes* |
| `OPENAI_CHAT_MODEL` | Chat model | Yes* |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | Embedding API key | Yes* |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | Embedding endpoint | Yes* |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding model | Yes* |

*Required only if using respective services

---

## API Reference

### Health Check

```bash
curl http://127.0.0.1:37777/actuator/health
```

### Create Observation

```bash
curl -X POST http://127.0.0.1:37777/api/observations \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test-session",
    "project_path": "/path/to/project",
    "content": "Test observation"
  }'
```

### Search

```bash
curl "http://127.0.0.1:37777/api/search?query=test&project=/path/to/project"
```

For complete API docs, see [API.md](../docs/API.md)
