# Claude-Mem Java Proxy Design Document

## 1. Overview

This document describes the architecture and integration design for the Java/Spring Boot implementation of Claude-Mem thin proxy.

**Key Principle**: The proxy is a **CLI wrapper**, NOT a long-running HTTP server. Each hook trigger spawns a short-lived process that handles one hook event.

---

## 2. Architecture Comparison

### TypeScript Version (Original)

```
Claude Code Hooks → worker-service.cjs (long-running HTTP server:37777)
                   │
                   └─→ Bun manages lifecycle
                       - Hook commands invoke: node worker-service.cjs hook claude-code <event>
                       - Each invocation: stdin → process → stdout → exit
```

### Java Version (This Implementation)

```
Claude Code Hooks → wrapper.js (CLI, short-lived per hook)
                   │
                   └─→ HTTP POST to Java API (localhost:37777)
                       │
                       └─→ Spring Boot Service (long-running)
                           - Processes hook events
                           - Writes context to CLAUDE.md
```

---

## 3. Claude Code Hooks Integration

### 3.1 Hook Lifecycle Events

Claude Code triggers hooks at 5 lifecycle points:

| Hook Event | When Triggered | Java API Endpoint |
|------------|----------------|------------------|
| `SessionStart` | After compaction/clear/startup | `/api/ingest/session-start` |
| `UserPromptSubmit` | User submits prompt | `/api/ingest/user-prompt` |
| `PostToolUse` | After any tool execution | `/api/ingest/tool-use` |
| `Stop` | Claude session ends | `/api/ingest/session-end` |
| `SessionEnd` | Session cleanup | `/api/ingest/session-end` |

### Request Parameters

| Hook Event | Required Parameters |
|------------|-------------------|
| `SessionStart` | `session_id`, `project_path` |
| `PostToolUse` | `session_id`, `tool_name`, `tool_input`, `cwd` |

### 3.2 Hook Communication Protocol

**Input (stdin)**:
```json
{
  "session_id": "uuid-or-string",
  "cwd": "/path/to/project",
  "tool_name": "Read|Write|Edit|...",
  "tool_input": {...},
  "tool_response": {...},
  "prompt": "user prompt text"
}
```

**Output (stdout)**:
```json
{
  "continue": true,
  "suppressOutput": true,
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": "..."
  }
}
```

**Exit Codes**:
| Code | Meaning | Usage |
|------|---------|-------|
| 0 | Success/Continue | Normal completion |
| 1 | Non-blocking error | Logged, Claude continues |
| 2 | Blocking error | Fed to Claude for handling |

---

## 4. File Structure

```
java/proxy/
├── wrapper.js           # CLI wrapper (entry point for hooks)
├── package.json        # Node.js dependencies
└── README.md           # This document

java/claude-mem-java/   # Spring Boot backend
├── src/main/java/...
└── target/*.jar
```

---

## 5. Integration Steps

### 5.1 Start Java Backend

```bash
cd /path/to/claude-mem/java/claude-mem-java
./mvnw spring-boot:run
# Or: java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar
```

Java API: `http://127.0.0.1:37777`

### 5.2 Configure Claude Code Hooks

Edit `~/.claude/settings.json` (user-level) or `.claude/settings.json` (project-level):

```json
{
  "hooks": {
    "SessionStart": [{
      "matcher": "compact",
      "hooks": [{
        "type": "command",
        "command": "/path/to/claude-mem/java/proxy/wrapper.js session-start --url http://127.0.0.1:37777"
      }]
    }],
    "PostToolUse": [{
      "matcher": "Edit|Write|Read|Bash|...",
      "hooks": [{
        "type": "command",
        "command": "/path/to/claude-mem/java/proxy/wrapper.js observation --url http://127.0.0.1:37777"
      }]
    }],
    "Stop": [{
      "hooks": [{
        "type": "command",
        "command": "/path/to/claude-mem/java/proxy/wrapper.js summarize --url http://127.0.0.1:37777"
      }]
    }]
  }
}
```

### 5.3 Make Wrapper Executable

```bash
chmod +x /path/to/claude-mem/java/proxy/wrapper.js
```

---

## 6. CLI Usage

```bash
# Session start (after compaction)
./wrapper.js session-start --url http://127.0.0.1:37777

# Record tool observation
./wrapper.js observation --url http://127.0.0.1:37777

# Summarize session (when Claude stops)
./wrapper.js summarize --url http://127.0.0.1:37777

# Help
./wrapper.js --help
```

---

## 7. Java API Endpoints

### 7.1 Session & Context Endpoints (Unified)

To reduce API calls and avoid timeout issues, session initialization and context generation
are combined into a single endpoint.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/session/start` | POST | Initialize session + generate context + CLAUDE.md updates |

**Request:**
```json
{
  "session_id": "content-session-id",
  "project_path": "/path/to/project",
  "cwd": "/path/to/project"
}
```

**Response:**
```json
{
  "context": "...",           // AI context (output to stdout)
  "updateFiles": [...],      // CLAUDE.md updates
  "session_db_id": "uuid",   // Database session ID
  "prompt_number": 1         // Prompt sequence number
}
```

### 7.2 Debug & Development Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/context/inject` | GET | Generate context only (for debugging) |
| `/api/context/generate` | POST | Generate context for specific project |
| `/api/session/{id}` | GET | Get session by content session ID |

### 7.3 Ingestion Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ingest/user-prompt` | POST | Record user prompt |
| `/api/ingest/tool-use` | POST | Enqueue tool observation |
| `/api/ingest/session-end` | POST | Complete session |

### 7.4 Stream Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/stream` | GET | SSE for Viewer UI |

### 7.3 Viewer Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/viewer/**` | GET | React UI static files |

---

## 8. Flow Diagram

### SessionStart Flow (Unified)

```
1. Claude Code triggers SessionStart hook (compaction mode)
   └─→ Runs: wrapper.js session-start --url http://127.0.0.1:37777

2. wrapper.js reads hook event from stdin
   └─→ { session_id, cwd, source: "compact", ... }

3. wrapper.js calls unified Java API (1 round-trip)
   └─→ POST /api/session/start
       Body: { session_id, project_path, cwd }

4. Java API does:
   a. Initialize/retrieve session
   b. Generate context from observations
   c. Generate CLAUDE.md content if needed
   └─→ Returns: { context, updateFiles, session_db_id, prompt_number }

5. wrapper.js processes file updates
   └─→ Writes CLAUDE.md with context tag

6. wrapper.js outputs context to stdout
   └─→ Claude Code reads and injects into AI context

7. wrapper.js exits with code 0 (fast, no timeout risk)
```

### PostToolUse Flow

```
1. Claude Code triggers PostToolUse hook
   └─→ Runs: wrapper.js observation --url http://127.0.0.1:37777

2. wrapper.js reads tool info from stdin
   └─→ { tool_name, tool_input, tool_response, ... }

3. wrapper.js calls Java API (fire-and-forget)
   └─→ POST /api/ingest/tool-use

4. Java API enqueues async processing
   └─→ AgentService.processToolUseAsync(...)

5. wrapper.js exits with code 0
   └─→ (no output needed for observations)
```

---

## 9. Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_API_URL` | `http://127.0.0.1:37777` | Java API endpoint |

---

## 10. Testing

### 10.1 Test Java API

```bash
# Health check
curl http://127.0.0.1:37777/actuator/health

# Session start
curl -X POST http://127.0.0.1:37777/api/ingest/session-start \
  -H "Content-Type: application/json" \
  -d '{"session_id": "test-123", "cwd": "/tmp", "project_path": "/tmp"}'
```

### 10.2 Test Wrapper

```bash
# With sample hook event
echo '{"session_id":"test-123","cwd":"/tmp"}' | \
  node wrapper.js session-start --url http://127.0.0.1:37777
```

---

## 11. Troubleshooting

### "Cannot connect to Java API"

```bash
# Verify Java backend is running
curl http://127.0.0.1:37777/actuator/health

# Check if port 37777 is listening
lsof -i :37777
```

### Hook not firing

- Verify command path in `~/.claude/settings.json` is absolute
- Ensure `matcher` patterns match tool names
- Check Claude Code logs

### Wrapper exits with code 2

Blocking error occurred. Check:
- Java API URL is correct and accessible
- Required fields in hook event
- Java service logs

---

## 12. Design Rationale

### Why CLI Wrapper, Not HTTP Server?

1. **Simplicity**: No port management, no lifecycle concerns
2. **Isolation**: Each hook is independent, no shared state issues
3. **Security**: Short-lived process minimizes attack surface
4. **Claude Code Integration**: Native fit for hook command pattern

### Why Java API on Port 37777?

1. **Separation of Concerns**:
   - Wrapper: CLI glue code (Node.js)
   - API: Business logic (Java/Spring Boot)
2. **Scalability**: Java backend can be scaled independently
3. **Ecosystem**: Spring Boot for enterprise-grade Java

---

## 13. Future Improvements

- [ ] Auto-restart Java service if not running
- [ ] Support both Claude Code and Cursor hooks
- [ ] Implement streaming context injection
- [ ] Add metrics/observability
