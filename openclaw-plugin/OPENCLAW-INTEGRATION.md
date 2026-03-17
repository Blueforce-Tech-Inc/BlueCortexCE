# OpenClaw Manual Integration Guide

This document describes how to integrate Claude-Mem Java backend with OpenClaw Gateway.

> ⚠️ **Experimental Feature - Not Yet Verified**
>
> This plugin is based on OpenClaw official SDK type definitions. Event names and API calls are aligned with official specifications.
> However, it has **not been tested in a real OpenClaw Gateway environment**.
>
> Before using:
> 1. Ensure OpenClaw Gateway is properly installed and configured
> 2. Place the compiled plugin in `~/.openclaw/extensions/cortexce/` directory
> 3. Use `openclaw plugins doctor` to check if plugin loads correctly
> 4. If issues arise, please submit an issue

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
cd ~/.cortexce/openclaw-plugin

# Install dependencies
npm install

# Build
npm run build
```

This generates `dist/index.js`.

### 2. Start Java Backend

```bash
cd ~/.cortexce

# Set API keys
export OPENAI_API_KEY=your_api_key
export SILICONFLOW_API_KEY=your_embedding_key

# Start backend
java -jar target/cortexce-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev &
```

Verify backend is running:
```bash
curl http://127.0.0.1:37777/actuator/health
# Should return: {"status":"UP",...}
```

---

## Installation Steps

### Step 1: Copy Plugin Files

```bash
# Create plugin directory
mkdir -p ~/.openclaw/extensions/cortexce

# Copy plugin configuration
cp ~/.cortexce/openclaw-plugin/openclaw.plugin.json ~/.openclaw/extensions/cortexce/

# Copy built plugin
cp ~/.cortexce/openclaw-plugin/dist/index.js ~/.openclaw/extensions/cortexce/
```

### Step 2: Install Skills (Optional - for active search)

```bash
# Copy search skill to OpenClaw
cp -r ~/.cortexce/openclaw-plugin/skills/claude-mem-search ~/.openclaw/skills/
```

Or for project-level:
```bash
cp -r ~/.cortexce/openclaw-plugin/skills/claude-mem-search /path/to/your-project/skills/
```

### Step 3: Enable Plugin

```bash
openclaw plugins enable cortexce

# Verify
openclaw plugins list
# Should display cortexce
```

### Step 4: Verify Installation

Check plugin status:
```bash
openclaw plugins doctor
```

---

## Configuration

### Plugin Configuration

Edit `~/.openclaw/extensions/cortexce/openclaw.plugin.json`:

```json
{
  "id": "cortexce",
  "name": "Claude-Mem CortexCE",
  "version": "0.1.0",
  "description": "Memory persistence for Claude Code sessions",
  "entry": "index.js",
  "enabled": true,
  "config": {
    "apiUrl": "http://localhost:37777"
  }
}
```

### Skill Configuration

For project-level skill, create `.openclaw/settings.json`:

```json
{
  "skills": {
    "paths": ["/path/to/your-project/skills"]
  }
}
```

---

## Usage

### Automatic Memory Capture

Once the plugin is enabled, it automatically:
1. Listens to OpenClaw lifecycle events
2. Records tool usage as Observations
3. Syncs MEMORY.md to workspace

### Active Memory Search

Use the installed skill:

```
@claude-mem-search query <search_term>
```

Or via command:

```bash
# Search by query
curl -s "http://127.0.0.1:37777/api/search?project=PROJECT_PATH&query=SEARCH_QUERY&limit=5"

# Search by discovery
curl -s "http://127.0.0.1:37777/api/search?project=PROJECT_PATH&type=discovery&limit=5"

# Search by concept
curl -s "http://127.0.0.1:37777/api/search?project=PROJECT_PATH&concept=architecture&limit=5"
```

---

## API Reference

### Health Check

```bash
curl http://127.0.0.1:37777/actuator/health
```

### Search

```bash
curl -s "http://127.0.0.1:37777/api/search?project=PROJECT_PATH&query=SEARCH_QUERY&limit=5"
```

### Get Context Timeline

```bash
curl -s "http://127.0.0.1:37777/api/context/timeline?project=PROJECT_PATH&anchorId=OBSERVATION_ID&depthBefore=3&depthAfter=3"
```

### Recent Context

```bash
curl -s "http://127.0.0.1:37777/api/context/recent?project=PROJECT_PATH&limit=3"
```

---

## Directory Structure

After installation:

```
~/.cortexce/
├── backend/                    # Spring Boot backend
│   └── target/
│       └── cortexce-0.1.0-SNAPSHOT.jar
├── openclaw-plugin/            # OpenClaw plugin source
│   ├── skills/
│   │   └── claude-mem-search/
│   ├── dist/
│   │   └── index.js
│   └── openclaw.plugin.json
└── ...

~/.openclaw/
├── extensions/
│   └── cortexce/              # Plugin installation directory
│       ├── index.js
│       └── openclaw.plugin.json
└── skills/
    └── claude-mem-search/     # Search skill (optional)
```

---

## Troubleshooting

### Plugin Not Loading

```bash
# Check plugin status
openclaw plugins list

# Check doctor output
openclaw plugins doctor

# Check logs
openclaw logs
```

### Cannot Connect to Java API

```bash
# Verify service is running
curl http://127.0.0.1:37777/actuator/health

# Check port
lsof -i :37777
```

### Skill Not Available

```bash
# Verify skill is installed
ls ~/.openclaw/skills/claude-mem-search/

# Check skill path in settings
cat ~/.openclaw/settings.json
```

---

## Uninstallation

```bash
# Disable plugin
openclaw plugins disable cortexce

# Remove files
rm -rf ~/.openclaw/extensions/cortexce
rm -rf ~/.openclaw/skills/claude-mem-search
```

---

## Limitations

- **Experimental**: This plugin has not been tested in production
- **No MCP**: Uses REST API instead of MCP protocol
- **Limited Events**: Only 7 lifecycle events are captured
