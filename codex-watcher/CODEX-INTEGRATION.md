# Codex CLI Integration

**Version:** 1.0.0
**Date:** 2026-04-16
**Project:** BlueCortexCE (Java Backend)

---

## Overview

This document describes how to integrate Codex CLI with the BlueCortexCE memory system. Codex CLI sessions are monitored via transcript watching, and observations are recorded to the Java backend.

## Architecture

```
~/.codex/sessions/**/*.jsonl
        │
        ▼ (codex-watcher)
BlueCortexCE Backend (localhost:37777)
        │
        ▼
PostgreSQL Database + Context Injection
```

## Components

### 1. OpenClaw Plugin Enhancement

Located in `openclaw-plugin/`, this plugin has been enhanced with:

- **Circuit Breaker Pattern**: Prevents CPU-spinning when backend is unreachable
  - Threshold: 3 consecutive failures
  - Cooldown: 30 seconds
  - Auto-recovery probe after cooldown

- **Context Cache**: 60s TTL cache for `before_prompt_build` context injection
  - Reduces redundant API calls
  - Auto-cleared on `gateway_start`

- **Search Commands**:
  - `/claude-mem-search <query> [limit]` - Search observations
  - `/claude-mem-recent [project] [limit]` - Recent context
  - `/claude-mem-timeline <query> [depthBefore] [depthAfter]` - Timeline query

### 2. Codex Watcher

Located in `codex-watcher/`, this Node.js CLI tool monitors Codex CLI session files:

- **File Monitoring**: Uses `chokidar` to watch `~/.codex/sessions/**/*.jsonl`
- **JSONL Parsing**: Parses Codex session events into structured format
- **API Integration**: Records observations to Java backend
- **Installer**: Manages Codex transcript watch configuration

## Setup

### Prerequisites

1. Java backend running on port 37777
2. Node.js 18+

### OpenClaw Plugin

```bash
cd openclaw-plugin
npm install
npm run build
```

### Codex Watcher

```bash
cd codex-watcher
npm install
npm run build

# Install Codex watcher configuration
node dist/index.js install

# Start watching
node dist/index.js start
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CLAUDE_MEM_BACKEND_URL` | `http://127.0.0.1:37777` | Java backend URL |
| `CLAUDE_MEM_PROJECT` | `codex` | Project name for Codex sessions |

### OpenClaw Plugin Config

In `openclaw.plugin.json`:

```json
{
  "id": "claude-mem-java",
  "config": {
    "workerPort": 37777,
    "project": "openclaw"
  }
}
```

## Codex Events

| Event Type | Action | Backend API |
|------------|--------|-------------|
| `session_meta` | session_context | POST /api/session/start |
| `turn_context` | session_context | POST /api/session/start |
| `user_message` | session_init | POST /api/session/start |
| `assistant_message` | assistant_message | (logged only) |
| `function_call` | tool_use | POST /api/ingest/tool-use |
| `function_call_output` | tool_result | POST /api/ingest/tool-use |
| `turn_aborted` | session_end | POST /api/ingest/session-end |

## Commands

### Codex Watcher

```bash
# Start watching
node dist/index.js start

# Install configuration
node dist/index.js install

# Uninstall configuration
node dist/index.js uninstall

# Check status
node dist/index.js status

# Show help
node dist/index.js help
```

### OpenClaw Commands

```bash
# Check backend health
/claude-mem-status

# List projects
/claude-mem-projects

# Search observations
/claude-mem-search <query> [limit]

# Recent context
/claude-mem-recent [project] [limit]

# Timeline query
/claude-mem-timeline <query> [depthBefore] [depthAfter]
```

## Troubleshooting

### Backend Unreachable

If the backend is unreachable, the circuit breaker will:
1. After 3 failures: Open circuit (drop all calls)
2. After 30s cooldown: Probe with HALF_OPEN state
3. On success: Close circuit

Check backend health:
```bash
curl http://127.0.0.1:37777/actuator/health
```

### No Sessions Detected

1. Check Codex CLI is installed and has been used
2. Verify sessions exist: `ls ~/.codex/sessions/`
3. Run watcher with debug output

### Installation Issues

1. Ensure `~/.claude-mem/` directory is writable
2. Check Node.js permissions
3. Verify `chokidar` dependency installed

## File Structure

```
codex-watcher/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts       # Main entry point
│   ├── api.ts         # Backend API client
│   ├── events.ts      # Codex event types
│   ├── watcher.ts     # File watcher
│   └── installer.ts   # Configuration installer
└── dist/              # Compiled output
```

## Related Documents

- [OpenClaw Integration](../openclaw-plugin/OPENCLAW-INTEGRATION.md)
- [Java Backend API](../backend/src/main/java/com/ablueforce/cortexce)
