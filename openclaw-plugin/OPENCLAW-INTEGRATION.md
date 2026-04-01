# OpenClaw Integration Guide

This document describes how to integrate Claude-Mem Java backend with OpenClaw Gateway.

> **Verified** — Plugin loaded and tested successfully in OpenClaw Gateway (2026-03-31).

---

## Integration Architecture Overview

Claude-Mem integration with OpenClaw consists of **two layers**:

```
┌─────────────────────────────────────────────────────────────────────┐
│               Claude-Mem + OpenClaw Integration Architecture        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Layer 1: Memory Capture (Plugin - Automatic)                       │
│  ├── OpenClaw plugin listens to 7 lifecycle events                 │
│  ├── Automatically records tool usage as Observations              │
│  ├── Automatically syncs MEMORY.md to workspace                    │
│  └── No user action required                                        │
│                                                                      │
│  Layer 2: Active Search (Skill - On Demand)                        │
│  ├── AgentSkills compatible SKILL.md file                          │
│  ├── Agent automatically determines if memory search is needed      │
│  ├── Uses REST API to call Java backend for semantic search         │
│  └── No MCP protocol needed (OpenClaw founder opposes MCP)          │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Comparison with Other IDE Integrations

| IDE | Layer 1: Memory Capture | Layer 2: Active Search | Search Method |
|-----|------------------------|----------------------|---------------|
| **Claude Code** | Hooks + wrapper.js | MCP Server | MCP Protocol |
| **Cursor IDE** | Hooks + wrapper.js | MCP Server | MCP Protocol |
| **TRAE** | .rules system injection | MCP Server | MCP Protocol |
| **OpenClaw** | Plugin (this doc) | **Skill** (this doc) | REST API |

> **Note**: OpenClaw founder explicitly stated dislike of MCP protocol, so **AgentSkills + REST API** is used for active search.

---

## Prerequisites

### 1. Build OpenClaw Plugin

```bash
# Enter openclaw-plugin directory
cd /path/to/your/BlueCortexCE/openclaw-plugin

# Install dependencies
npm install

# Build
npm run build
```

This generates `dist/index.js`.

### 2. Start Java Backend

Ensure Java backend is running at `localhost:37777`. Multiple startup methods available:

```bash
# Method 1: Run JAR directly
cd /path/to/your/BlueCortexCE/backend
export OPENAI_API_KEY=your_api_key
export SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key
java -jar target/cortex-ce-0.1.0-beta.jar --spring.profiles.active=dev &

# Method 2: Docker
docker compose -f /path/to/your/BlueCortexCE/docker-compose.yml up -d
```

Verify backend is running:
```bash
curl http://127.0.0.1:37777/actuator/health
# Should return: {"status":"UP",...}
```

---

## Layer 1: Plugin Installation (Memory Capture)

OpenClaw supports three plugin installation methods. Choose one.

### Two Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `openclaw.plugin.json` | Inside plugin directory | **Plugin manifest**: defines plugin ID, name, config schema (does NOT accept user config values) |
| OpenClaw main config | `~/.openclaw/openclaw.json` | **User config**: enables plugin, provides actual config values |

```
┌─────────────────────────────────────────────────────────────────────┐
│  openclaw.plugin.json (inside plugin dir - do not modify)           │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ configSchema: {                                               │  │
│  │   workerPort: { type: "number", default: 37777 }  ← defines    │  │
│  │ }                                                             │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                          ↓ defines structure                        │
├─────────────────────────────────────────────────────────────────────┤
│  OpenClaw Main Config File (user modifiable)                        │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ plugins.entries."claude-mem-java".config: {                   │  │
│  │   workerPort: 37777  ← provides actual value (must match)    │  │
│  │ }                                                             │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Method 1: Auto-Discovery (Recommended, Simplest)

Copy the built plugin to the OpenClaw extensions directory. OpenClaw will automatically discover and load it.

```bash
# Create target directory
mkdir -p ~/.openclaw/extensions/claude-mem-java

# Copy necessary files
cp /path/to/your/BlueCortexCE/openclaw-plugin/openclaw.plugin.json ~/.openclaw/extensions/claude-mem-java/
cp /path/to/your/BlueCortexCE/openclaw-plugin/dist/index.js ~/.openclaw/extensions/claude-mem-java/
```

**Advantages**: No need to modify OpenClaw config file, uses defaults from `configSchema`.

**Verify**:
```bash
openclaw plugins list          # Should show claude-mem-java
openclaw plugins doctor        # Check for errors
```

---

### Method 2: Config File Specification

Specify plugin path and config values in OpenClaw config file.

**Config file location**: `~/.openclaw/openclaw.json`

```json
{
  "plugins": {
    "allow": ["claude-mem-java"],
    "entries": {
      "claude-mem-java": {
        "enabled": true,
        "config": {
          "workerPort": 37777,
          "project": "my-project",
          "syncMemoryFile": true
        }
      }
    }
  }
}
```

**`plugins.allow`**: Explicitly declares trusted plugin IDs, suppresses "untracked local code" warning at Gateway startup.

**Advantages**: Can override default config values, suitable for custom configuration scenarios.

**Note**: `load.paths` points to directory containing `openclaw.plugin.json`.

---

### Method 3: CLI Installation

Use OpenClaw CLI commands to install.

```bash
# Install from local directory
openclaw plugins install /path/to/your/BlueCortexCE/openclaw-plugin

# Enable after installation
openclaw plugins enable claude-mem-java

# Restart Gateway
openclaw gateway restart
```

**Advantages**: OpenClaw automatically manages plugin files.

---

### Configuration Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `workerPort` | number | 37777 | Java backend port |
| `project` | string | "openclaw" | Project name for memory tracking |
| `syncMemoryFile` | boolean | true | Whether to sync MEMORY.md file |

---

### Installation Method Comparison

| Method | Complexity | Use Case |
|--------|------------|----------|
| Method 1: Auto-Discovery | Simple | Development/testing, default config |
| Method 2: Config File | Medium | Custom config, multi-environment |
| Method 3: CLI Install | Medium | Production, version management |

---

## Layer 2: Skill Configuration (Active Search)

Enables OpenClaw Agent to **proactively search** historical memory when needed, without user manually invoking commands.

### How Skills Work

```
┌─────────────────────────────────────────────────────────────────────┐
│              OpenClaw AgentSkills Progressive Disclosure             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Level 1: Trigger Detection (~100 tokens)                            │
│  ├── Reads SKILL.md name + description                             │
│  ├── Determines if user question needs memory search               │
│  └── E.g.: "上次我们", "之前是怎么", "search memory"                │
│                                                                      │
│  Level 2: Full Skill Content (on-demand)                            │
│  ├── Loads complete SKILL.md when Agent determines search needed   │
│  ├── Gets three-step workflow, API endpoints, curl examples         │
│  └── Agent automatically executes search logic                       │
│                                                                      │
│  Level 3: Reference Files (on-demand)                               │
│  └── Scripts or data files loaded as needed                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Install Skill

**Method 1: Global Skill (Recommended)**

```bash
# Create global skills directory
mkdir -p ~/.openclaw/skills

# Copy Skill files (from project directory)
cp -r /path/to/your/BlueCortexCE/openclaw-plugin/skills/claude-mem-search ~/.openclaw/skills/
```

**Method 2: Project-Level Skill**

```bash
# Create skills directory in project root
mkdir -p /path/to/your-project/skills

# Copy Skill files
cp -r /path/to/your/BlueCortexCE/openclaw-plugin/skills/claude-mem-search /path/to/your-project/skills/
```

### Skill File Locations

```
~/.openclaw/skills/claude-mem-search/    # Global (available to all projects)
└── SKILL.md                              # AgentSkills compatible format

# or

/path/to/project/skills/claude-mem-search/  # Project-level (only that project)
└── SKILL.md
```

### Verify Skill Installation

```bash
# Check Skill file exists
ls -la ~/.openclaw/skills/claude-mem-search/SKILL.md

# Restart OpenClaw Gateway to apply changes
openclaw gateway restart
```

### Trigger Keywords

When user asks these questions, Agent will **automatically activate** the search skill (no manual command needed):

- **Chinese**: "上次我们怎么做...", "之前是怎么...", "搜索记忆...", "查找之前..."
- **English**: "what did we do before", "last time we...", "search memory", "recall when..."

---

## Skill File Source

The Skill file is located in this project:

| Location | Description |
|----------|-------------|
| Source directory | `openclaw-plugin/skills/claude-mem-search/SKILL.md` |
| After install | `~/.openclaw/skills/claude-mem-search/SKILL.md` |

**Note**: Do not duplicate the full SKILL.md content in this integration doc — AgentSkills system automatically loads Skill file content on-demand. Just ensure the Skill file is correctly installed to the above location.

See [SKILL.md](skills/claude-mem-search/SKILL.md) for the complete three-step memory retrieval workflow and API examples.

---

## Available Commands

The plugin registers two commands:

### /claude-mem-status

Check Java backend health status and session statistics.

```bash
/claude-mem-status
```

Example response:
```
Claude-Mem Java Backend Status
Status: UP
Port: 37777
Active sessions: 2
```

### /claude-mem-projects

List all tracked projects.

```bash
/claude-mem-projects
```

Example response:
```
Claude-Mem Projects
  - my-project
  - openclaw
  - workspace-abc
```

---

## Event Listening

Plugin listens to 7 OpenClaw Gateway lifecycle events:

| Event | When | Plugin Action |
|-------|------|--------------|
| `session_start` | User starts new session (`/new`, `/reset`) | Initialize claude-mem session |
| `after_compaction` | After context compaction | Re-initialize session |
| `before_agent_start` | Before Agent executes | Sync MEMORY.md + track workspace |
| `tool_result_persist` | After tool execution | Record observation + sync MEMORY.md |
| `agent_end` | Agent execution ends | Generate summary + complete session |
| `session_end` | Session ends | Clean up session tracking |
| `gateway_start` | Gateway starts | Reset session tracking |

---

## MEMORY.md Sync Mechanism

### Sync Flow

```
1. before_agent_start event triggers
       ↓
2. Plugin calls /api/context/inject to get timeline
       ↓
3. Writes to workspaceDir/MEMORY.md
       ↓
4. Agent reads MEMORY.md on startup for context
```

### Sync Timing

| Event | Sync | Description |
|-------|------|-------------|
| `before_agent_start` | Yes | Get context before Agent starts |
| `tool_result_persist` | Yes | Update after each tool use |
| `session_start` | No | Only initialize session |
| `agent_end` | No | Only summarize and complete |

---

## API Endpoint Mapping

Plugin calls Java backend API via HTTP:

| Function | Plugin Call Endpoint |
|----------|---------------------|
| Session init | `/api/session/start` |
| Record tool use | `/api/ingest/tool-use` |
| Session complete | `/api/ingest/session-end` |
| Get Timeline | `/api/context/inject` |

---

## Comparison with TypeScript Version

| Feature | TypeScript Version | Java Version |
|---------|-------------------|--------------|
| Backend | TypeScript Worker | Java Spring Boot |
| SSE Support | Yes | No |
| MEMORY.md | Yes | Yes |
| Observation Records | Yes | Yes |
| Commands | Differs | `/claude-mem-status`, `/claude-mem-projects` |

### Why No SSE in Java Version?

Java version follows **Thin Proxy** architecture philosophy:
- Thin Proxy = CLI mode, runs and exits, no persistent connections
- SSE requires long-running process, conflicts with Thin Proxy philosophy
- Keeps it lightweight, fast, resource-friendly

**Alternative**: Users can view observation records via WebUI (localhost:37777) or MCP tools.

---

## Troubleshooting

### "Claude-Mem Java backend unreachable"

```bash
# Check if Java backend is running
curl http://127.0.0.1:37777/actuator/health

# Check port
lsof -i :37777
```

### MEMORY.md Not Syncing

- Confirm `syncMemoryFile` is set to `true`
- Check OpenClaw logs for errors
- Confirm Java backend responds to `/api/context/inject` requests

### Observations Not Saved

- Check if `tool_result_persist` event is triggering
- Confirm tool names don't start with `memory_` (these are filtered)
- Check Java backend logs

---

## Directory Structure

```
BlueCortexCE/                          # Project root
├── backend/                          # Spring Boot backend
│   ├── src/main/java/...
│   ├── target/
│   │   └── cortex-ce-0.1.0-beta.jar  # Build output
│   └── .env                          # API Keys config
├── openclaw-plugin/                  # OpenClaw plugin
│   ├── src/index.ts                  # Plugin main code
│   ├── openclaw.plugin.json          # Plugin config
│   ├── package.json                  # NPM config
│   ├── skills/
│   │   └── claude-mem-search/        # Skill files
│   │       └── SKILL.md
│   └── dist/                         # Build output
└── proxy/                            # Claude Code Thin Proxy
    └── wrapper.js                    # CLI Wrapper
```

---

## Quick Test Commands

### Test Backend Connection

```bash
# Using /claude-mem-status command
/claude-mem-status
```

### Test Project List

```bash
# Using /claude-mem-projects command
/claude-mem-projects
```

### Test API Endpoints

```bash
# Health check
curl http://127.0.0.1:37777/actuator/health

# Project list
curl http://127.0.0.1:37777/api/projects

# Timeline injection
curl "http://127.0.0.1:37777/api/context/inject?projects=openclaw"
```

---

## Future Enhancements

- [ ] Add SSE observation stream support (requires standalone service)
- [ ] Support more messaging channels (Telegram/Discord)
- [ ] Add WebUI embedded view

---

## Related Documents

| Document | Description |
|----------|-------------|
| `backend/README.md` | Java backend documentation |
| `proxy/CLAUDE-CODE-INTEGRATION.md` | Claude Code integration guide |
