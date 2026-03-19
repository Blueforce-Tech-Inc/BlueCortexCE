# Cursor IDE Manual Integration Guide

This document describes how to integrate Claude-Mem Java Port with Cursor IDE.

---

## Overview

Cursor IDE supports two integration methods:
1. **Hooks** - Execute scripts when specific events trigger
2. **MCP (Model Context Protocol)** - Provide tools for AI to call

The Java Port backend implements `CursorService` to manage project registration and context files, but requires manual Cursor IDE configuration.

---

## Prerequisites

### 1. Start Java Backend

```bash
cd ~/.cortexce

# Set API keys
export OPENAI_API_KEY=your_api_key
export SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key

# Start backend
java -jar target/cortexce-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev &
```

Verify backend is running:
```bash
curl http://127.0.0.1:37777/actuator/health
# Should return: {"status":"UP",...}
```

### 2. Install Thin Proxy Dependencies

```bash
cd ~/.cortexce/proxy
npm install
```

---

## Option 1: Configure via Hooks

### Step 1: Register Project

Register your project with the backend:

```bash
curl -X POST http://127.0.0.1:37777/api/cursor/register \
  -H "Content-Type: application/json" \
  -d '{
    "project_path": "/path/to/your/project"
  }'
```

### Step 2: Configure Cursor Settings

Edit `~/.cursor/settings.json` or project-level `.cursor/settings.json`:

```json
{
  "cursor": {
    "rules": [
      {
        "pattern": "**/*",
        "content": "Memory: Use ~/.cortexce/proxy/wrapper.js to query context"
      }
    ]
  },
  "automaticToolExecution": {
    "enabled": true
  }
}
```

### Step 3: Add Hook Commands

Edit `.cursor/rules.md` or configure in settings:

```markdown
# Memory Integration

When working on this project, Claude can query relevant context from memory using:

- Session init: node ~/.cortexce/proxy/wrapper.js cursor session-init --url http://127.0.0.1:37777
- Context query: node ~/.cortexce/proxy/wrapper.js cursor context --url http://127.0.0.1:37777
- Observation: node ~/.cortexce/proxy/wrapper.js cursor observation --url http://127.0.0.1:37777
```

---

## Option 2: Configure via MCP

### Step 1: Add MCP Server (Default: STREAMABLE)

```bash
# STREAMABLE protocol (default):
claude mcp add --transport http cortexce http://127.0.0.1:37777/mcp

# Or SSE protocol (alternative):
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse
```

### Step 2: Verify Configuration

```bash
claude mcp list
```

You should see `cortexce` in the list.

---

## Verification

### Check if Service is Running

```bash
curl http://127.0.0.1:37777/actuator/health
```

### Check Registered Projects

```bash
curl http://127.0.0.1:37777/api/cursor/projects
```

---

## Troubleshooting

### Backend Not Responding

- Verify backend is running: `curl http://127.0.0.1:37777/actuator/health`
- Check port 37777 is not blocked

### MCP Not Working

- Verify MCP server is registered: `claude mcp list`
- Check Cursor logs for errors

### Memory Not Being Retrieved

- Verify project is registered: `curl http://127.0.0.1:37777/api/cursor/projects`
- Check that context files exist in `~/.cortexce/context/`

---

## Additional Configuration

### Environment Variables

| Variable | Description |
|----------|-------------|
| `CURSOR_CONTEXT_DIR` | Directory for context files (default: `~/.cortexce/context`) |
| `CURSOR_MAX_TOKENS` | Max tokens for context (default: 4000) |

### Context File Format

Context files are stored at:
```
~/.cortexce/context/{project_hash}/
├── session-{session_id}.md
├── observations.md
└── summary.md
```
