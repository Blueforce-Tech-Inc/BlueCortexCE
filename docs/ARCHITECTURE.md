# Cortex Community Edition Architecture

This document describes the architecture of Cortex Community Edition, including system design, component interactions, data flow, and technical decisions.

## Table of Contents

- [Overview](#overview)
- [Architecture Pattern: Thin Proxy + Fat Server](#architecture-pattern-thin-proxy--fat-server)
- [System Architecture](#system-architecture)
- [Core Components](#core-components)
  - [Thin Proxy](#thin-proxy)
  - [Fat Server](#fat-server)
  - [PostgreSQL + pgvector](#postgresql--pgvector)
- [Data Flow](#data-flow)
- [API Layers](#api-layers)
- [Technology Stack](#technology-stack)
- [Design Decisions](#design-decisions)
- [Trade-offs](#trade-offs)
- [Scalability Considerations](#scalability-considerations)
- [Security Architecture](#security-architecture)

---

## Overview

Cortex Community Edition is a memory-enhanced system for AI assistants that provides:

- **Persistent Memory**: Cross-session context storage and retrieval
- **Intelligent Quality Assessment**: Automatic evaluation and prioritization
- **Context-Aware Retrieval**: Vector-based semantic search
- **Memory Evolution**: Automatic refinement and optimization

The system is designed to solve the **CLI Hook Timeout Problem** in AI development environments, where synchronous processing of memory operations would block the AI assistant's response loop.

---

## Architecture Pattern: Thin Proxy + Fat Server

### The Problem

In AI development environments (like Claude Code, Cursor IDE), hooks are executed synchronously:

```
AI Assistant → Hook Execution → Hook Returns → AI Continues
                    ↑
              Timeout Risk!
```

If a hook takes too long (LLM calls, embedding generation, database writes), the AI assistant will timeout and fail.

### The Solution

Cortex CE uses a **Thin Proxy + Fat Server** architecture to decouple hook execution from heavy processing:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Hook Execution Layer                         │
│                                                                     │
│  ┌──────────────┐                                                   │
│  │  CLI Hook    │  ← Must complete in < 200ms                       │
│  │ (wrapper.js) │                                                   │
│  └──────┬───────┘                                                   │
│         │ HTTP POST                                                 │
│         ▼                                                           │
│  ┌──────────────┐                                                   │
│  │  Thin Proxy  │  ← Receives request, forwards, responds immediately│
│  │  (Express)   │                                                   │
│  └──────┬───────┘                                                   │
└─────────┼───────────────────────────────────────────────────────────┘
          │
          │ HTTP (async)
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Processing Layer                             │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Fat Server (Spring Boot)                   │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │  │
│  │  │ LLM Service │  │  Embedding  │  │ Quality Assessment  │  │  │
│  │  │             │  │  Service    │  │ & Refinement        │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘  │  │
│  │                                                              │  │
│  │  ┌─────────────────────────────────────────────────────────┐│  │
│  │  │              Async Processing Queue                      ││  │
│  │  └─────────────────────────────────────────────────────────┘│  │
│  └──────────────────────────────────────────────────────────────┘  │
│         │                                                           │
│         │ JDBC                                                      │
│         ▼                                                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              PostgreSQL + pgvector                            │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │  │
│  │  │  Sessions   │  │Observations │  │  Vector Indexes     │  │  │
│  │  │             │  │ + Embeddings│  │  (HNSW)             │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Benefits

| Benefit | Description |
|---------|-------------|
| **Fast Hook Response** | Proxy responds in < 200ms, avoiding timeout |
| **Reliable Processing** | Fat server handles failures with retry logic |
| **Resource Efficiency** | Heavy operations don't block AI assistant |
| **Scalability** | Fat server can be scaled independently |

---

## System Architecture

### High-Level View

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                    │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐   │
│  │ Claude Code │  │  Cursor IDE │  │  OpenClaw   │  │  Custom Client  │   │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘   │
│         │                │                │                  │             │
│         └────────────────┴────────────────┴──────────────────┘             │
│                                    │                                        │
│                              Hooks / API                                    │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Integration Layer                                  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         Thin Proxy (Node.js)                         │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐  │   │
│  │  │ wrapper.js  │  │  proxy.js   │  │  Event Forwarding Logic     │  │   │
│  │  │ (CLI entry) │  │ (HTTP server│  │  - Session Start/End        │  │   │
│  │  │             │  │  optional)  │  │  - Tool Use Events          │  │   │
│  │  └─────────────┘  └─────────────┘  │  - User Prompts             │  │   │
│  │                                    └─────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ HTTP REST / SSE
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Application Layer                                   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Fat Server (Spring Boot)                          │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │                     Controllers                               │  │   │
│  │  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ │  │   │
│  │  │  │ Ingestion  │ │  Viewer    │ │  Context   │ │   MCP      │ │  │   │
│  │  │  │ Controller │ │ Controller │ │ Controller │ │Controller  │ │  │   │
│  │  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘ │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │                      Services                                 │  │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐│  │   │
│  │  │  │ Agent   │ │ Search  │ │Context  │ │Timeline │ │ Embed   ││  │   │
│  │  │  │ Service │ │ Service │ │ Service │ │ Service │ │ Service ││  │   │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘│  │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐            │  │   │
│  │  │  │  LLM    │ │ ClaudeMd│ │ Token   │ │ Rate    │            │  │   │
│  │  │  │ Service │ │ Service │ │ Service │ │ Limit   │            │  │   │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘            │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │                    Async Processing                           │  │   │
│  │  │  ┌───────────────────┐  ┌─────────────────────────────────┐  │  │   │
│  │  │  │ @Async Methods    │  │  Stale Message Recovery Task    │  │  │   │
│  │  │  │ (Virtual Threads) │  │  (Crash Recovery + Dedup)       │  │  │   │
│  │  │  └───────────────────┘  └─────────────────────────────────┘  │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ JDBC / JPA
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Data Layer                                        │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL 16 + pgvector 0.8                      │   │
│  │                                                                      │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │   │
│  │  │ mem_sessions│  │mem_observations│ │mem_summaries│  │mem_prompts │  │   │
│  │  │             │  │              │  │             │  │            │  │   │
│  │  │ • id        │  │ • id         │  │ • id        │  │ • id       │  │   │
│  │  │ • project   │  │ • session_id │  │ • session_id│  │ • session  │  │   │
│  │  │ • status    │  │ • content    │  │ • content   │  │ • content  │  │   │
│  │  │ • start/end │  │ • facts      │  │ • tokens    │  │ • tokens   │  │   │
│  │  │ • tokens    │  │ • concepts   │  │             │  │            │  │   │
│  │  │             │  │ • embedding  │  │             │  │            │  │   │
│  │  │             │  │   (1024-dim) │  │             │  │            │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘  │   │
│  │                                                                      │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │                    Vector Indexes (HNSW)                     │    │   │
│  │  │  • embedding_768  (vector_cosine_ops)                       │    │   │
│  │  │  • embedding_1024 (vector_cosine_ops) ← Primary             │    │   │
│  │  │  • embedding_1536 (vector_cosine_ops)                       │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ HTTP API
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          External Services                                   │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │   LLM Provider  │  │Embedding Service│  │     IDE Integrations        │ │
│  │  (DeepSeek /    │  │  (SiliconFlow   │  │                             │ │
│  │   Anthropic)    │  │   bge-m3)       │  │  • Claude Code              │ │
│  │                 │  │                 │  │  • Cursor IDE               │ │
│  │  • Chat Complet │  │  • 768-dim      │  │  • OpenClaw Gateway         │ │
│  │  • Summarizat'n │  │  • 1024-dim     │  │                             │ │
│  │  • Refinement   │  │  • 1536-dim     │  │                             │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Core Components

### Thin Proxy

The Thin Proxy is a lightweight Node.js application responsible for fast event forwarding.

#### Responsibilities

| Responsibility | Description |
|----------------|-------------|
| Hook Event Reception | Receive events from CLI hooks |
| Event Forwarding | Forward to Fat Server via HTTP |
| Quick Response | Respond within 200ms to avoid timeout |
| Error Handling | Graceful degradation on failures |

#### Components

```
proxy/
├── wrapper.js          # CLI entry point (called by hooks)
├── proxy.js            # Optional HTTP server for local aggregation
├── package.json        # Dependencies (axios)
└── CLAUDE-CODE-INTEGRATION.md  # Integration documentation
```

#### Hook Event Flow

```javascript
// wrapper.js - CLI entry point
const event = process.argv[2];  // 'session-start', 'tool-use', etc.
const data = readFromStdin();

// Quick forward to Fat Server
await axios.post('http://localhost:37777/api/ingest/' + event, data);

// Exit immediately
process.exit(0);
```

#### Performance Requirements

| Metric | Target |
|--------|--------|
| Response Time | < 200ms |
| Memory Footprint | < 50MB |
| Startup Time | < 100ms |

---

### Fat Server

The Fat Server is the core Spring Boot application handling all business logic.

#### Responsibilities

| Responsibility | Description |
|----------------|-------------|
| Event Processing | Process hook events asynchronously |
| LLM Integration | Chat completion, summarization, refinement |
| Embedding Generation | Vector embeddings for semantic search |
| Quality Assessment | Score and prioritize memories |
| Context Generation | Generate context for AI injection |
| API Serving | REST API and MCP Server |

#### Service Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Controller Layer                        │
│                                                             │
│  IngestionController    →  /api/ingest/*                    │
│  ViewerController       →  /api/observations, /api/search   │
│  ContextController      →  /api/context/*                   │
│  StreamController       →  /api/stream (SSE)                │
│  LogsController         →  /api/logs                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       Service Layer                          │
│                                                             │
│  AgentService           → Core orchestration                │
│    ├── LlmService       → Chat completion                   │
│    ├── EmbeddingService → Vector embeddings                 │
│    └── XmlParser        → Parse LLM XML output              │
│                                                             │
│  SearchService          → Semantic + text search            │
│  ContextService         → Context retrieval                 │
│  TimelineService        → Timeline context generation       │
│  ClaudeMdService        → CLAUDE.md generation              │
│  TokenService           → Token counting                    │
│  RateLimitService       → Per-session rate limiting         │
│  ProjectFilterService   → Project path filtering            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Repository Layer                         │
│                                                             │
│  SessionRepository      → CRUD for sessions                 │
│  ObservationRepository  → CRUD + vector search              │
│  SummaryRepository      → CRUD for summaries                │
│  UserPromptRepository   → CRUD for user prompts             │
│  PendingMessageRepository → Crash recovery queue            │
└─────────────────────────────────────────────────────────────┘
```

#### Core Pipeline: Observation Creation

```
Tool-Use Event
      │
      ▼
┌─────────────────┐
│ IngestionCtrl   │  POST /api/ingest/tool-use
└────────┬────────┘
         │ @Async
         ▼
┌─────────────────┐
│  AgentService   │  Orchestration
└────────┬────────┘
         │
         ├──────────────────┐
         │                  │
         ▼                  ▼
┌─────────────────┐  ┌─────────────────┐
│   LlmService    │  │  PromptTemplate │
│                 │  │                 │
│ DeepSeek API    │  │ observation.txt │
│ Chat Completion │  │                 │
└────────┬────────┘  └─────────────────┘
         │
         ▼
┌─────────────────┐
│   XmlParser     │  Extract from XML:
│                 │  <observation>
│  Regex-based    │    <facts>...</facts>
│  (no XML parser)│    <concepts>...</concepts>
└────────┬────────┘  </observation>
         │
         ▼
┌─────────────────┐
│EmbeddingService │
│                 │
│ SiliconFlow API │  bge-m3 → 1024-dim vector
│                 │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Observation    │
│  Repository     │  PostgreSQL + pgvector
│                 │  HNSW index for similarity
└─────────────────┘
```

#### Async Processing

```java
@Service
public class AgentService {

    @Async  // Virtual thread
    public void processToolUseAsync(ToolUseEvent event) {
        // 1. Generate LLM response
        String llmResponse = llmService.chatCompletion(prompt);

        // 2. Parse observation
        ObservationData data = xmlParser.parse(llmResponse);

        // 3. Generate embedding
        float[] embedding = embeddingService.embed(data.getContent());

        // 4. Save to database
        observationRepository.save(observation);
    }
}
```

#### Crash Recovery

```
┌─────────────────────────────────────────────────────────┐
│                Pending Message Queue                     │
│                                                         │
│  mem_pending_messages table:                            │
│  • id                                                   │
│  • content_session_id                                   │
│  • tool_name                                            │
│  • tool_input_hash  ← Deduplication                     │
│  • payload (JSON)                                       │
│  • created_at                                           │
│  • status (pending/processed/failed)                    │
│                                                         │
│  StaleMessageRecoveryTask runs every 5 minutes:         │
│  1. Find messages with status='pending' and age > 5min  │
│  2. Re-process with deduplication                       │
│  3. Mark as processed or failed                         │
└─────────────────────────────────────────────────────────┘
```

---

### PostgreSQL + pgvector

#### Schema Overview

```sql
-- Sessions table
CREATE TABLE mem_sessions (
    id VARCHAR(36) PRIMARY KEY,
    project_path VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,  -- active/completed/skipped
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    read_tokens INTEGER DEFAULT 0,
    write_tokens INTEGER DEFAULT 0,
    discovery_tokens INTEGER DEFAULT 0
);

-- Observations table (with multi-dimension embeddings)
CREATE TABLE mem_observations (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) REFERENCES mem_sessions(id),
    project_path VARCHAR(500) NOT NULL,
    folder_path VARCHAR(500),
    file_path VARCHAR(500),
    content TEXT NOT NULL,
    facts JSONB,           -- ["fact1", "fact2"]
    concepts JSONB,        -- ["concept1", "concept2"]
    quality_score FLOAT DEFAULT 0.5,

    -- Multi-dimension embeddings
    embedding_768  vector(768),
    embedding_1024 vector(1024),  -- Primary (bge-m3)
    embedding_1536 vector(1536),
    embedding_model_id VARCHAR(50),

    created_at TIMESTAMP NOT NULL,
    UNIQUE (session_id, created_at)
);

-- Vector indexes (HNSW)
CREATE INDEX idx_embedding_768 ON mem_observations
    USING hnsw (embedding_768 vector_cosine_ops);

CREATE INDEX idx_embedding_1024 ON mem_observations
    USING hnsw (embedding_1024 vector_cosine_ops);

CREATE INDEX idx_embedding_1536 ON mem_observations
    USING hnsw (embedding_1536 vector_cosine_ops);
```

#### Semantic Search

```sql
-- Cosine similarity search
SELECT id, content,
       1 - (embedding_1024 <=> :query_vector) as similarity
FROM mem_observations
WHERE project_path = :project_path
ORDER BY embedding_1024 <=> :query_vector
LIMIT :limit;
```

---

## Data Flow

### Complete Event Flow

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Complete Event Flow                                 │
└──────────────────────────────────────────────────────────────────────────────┘

1. SESSION START
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Ingestion   │────▶│ PostgreSQL  │
│  Hook       │     │ session-start│    │ Controller  │     │ Session     │
└─────────────┘     └─────────────┘     └─────────────┘     │ Created     │
                    < 200ms             Async                └─────────────┘

2. TOOL USE (Observation)
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Ingestion   │
│  PostToolUse│     │ observation │     │ Controller  │
└─────────────┘     └─────────────┘     └──────┬──────┘
                    < 200ms                    │ @Async
                                               ▼
                    ┌─────────────────────────────────────────────┐
                    │              Async Pipeline                  │
                    │                                              │
                    │  ┌─────────────┐                            │
                    │  │ AgentService│                            │
                    │  └──────┬──────┘                            │
                    │         │                                    │
                    │         ▼                                    │
                    │  ┌─────────────┐     ┌─────────────┐        │
                    │  │ LlmService  │────▶│ DeepSeek    │        │
                    │  │             │     │ API         │        │
                    │  └──────┬──────┘     └─────────────┘        │
                    │         │                                    │
                    │         ▼                                    │
                    │  ┌─────────────┐                            │
                    │  │ XmlParser   │ Extract facts/concepts     │
                    │  └──────┬──────┘                            │
                    │         │                                    │
                    │         ▼                                    │
                    │  ┌─────────────┐     ┌─────────────┐        │
                    │  │ Embedding   │────▶│ SiliconFlow │        │
                    │  │ Service     │     │ bge-m3 API  │        │
                    │  └──────┬──────┘     └─────────────┘        │
                    │         │                                    │
                    │         ▼                                    │
                    │  ┌─────────────┐     ┌─────────────┐        │
                    │  │ Observation │────▶│ PostgreSQL  │        │
                    │  │ Repository  │     │ + pgvector  │        │
                    │  └─────────────┘     └─────────────┘        │
                    └─────────────────────────────────────────────┘

3. CONTEXT INJECTION
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Context     │────▶│ PostgreSQL  │
│ SessionStart│     │ context-get │     │ Service     │     │ Vector      │
│ (next sess) │     │             │     │             │     │ Search      │
└─────────────┘     └─────────────┘     └──────┬──────┘     └─────────────┘
                    < 200ms                    │                    │
                                               ▼                    │
                    ┌─────────────────────────────────────────────┐│
                    │           Context Generation                 ││
                    │                                              ││
                    │  1. Semantic search for recent observations  ││
                    │  2. Timeline context assembly                ││
                    │  3. CLAUDE.md file generation               ││
                    │                                              ││
                    └─────────────────────────────────────────────┘│
                                               │                    │
                                               ▼                    │
                    ┌─────────────────────────────────────────────┐│
                    │           CLAUDE.md Injection               ││
                    │                                              ││
                    │  # Project Context                           ││
                    │  Generated: 2026-01-15                       ││
                    │                                              ││
                    │  ## Recent Work                              ││
                    │  - Observation 1...                          ││
                    │  - Observation 2...                          ││
                    │                                              ││
                    └─────────────────────────────────────────────┘│

4. SESSION END (Summary)
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Ingestion   │────▶│ PostgreSQL  │
│  SessionEnd │     │ summarize   │     │ Controller  │     │ Summary     │
└─────────────┘     └─────────────┘     └──────┬──────┘     │ Saved       │
                    < 200ms                    │ @Async      └─────────────┘
                                               ▼
                    ┌─────────────────────────────────────────────┐
                    │              Summary Pipeline                │
                    │                                              │
                    │  1. Collect all session observations        │
                    │  2. LLM summarization                       │
                    │  3. Save summary with tokens                │
                    │  4. Update session status                   │
                    │                                              │
                    └─────────────────────────────────────────────┘
```

---

## API Layers

### REST API

| Layer | Path Pattern | Description |
|-------|--------------|-------------|
| Ingestion | `/api/ingest/*` | Hook event reception |
| Viewer | `/api/observations`, `/api/search` | WebUI data |
| Context | `/api/context/*` | Context retrieval |
| Stream | `/api/stream` | SSE real-time updates |
| Logs | `/api/logs` | Log access |

### MCP Server

Model Context Protocol for AI assistant integration:

| Tool | Description |
|------|-------------|
| `search` | Semantic search with vector similarity |
| `timeline` | Context timeline retrieval |
| `get_observations` | Batch observation details |
| `save_memory` | Manual memory save |
| `recent` | Recent session summaries |

#### MCP Transport Protocols

The MCP Server supports two transport protocols:

| Protocol | Endpoint | Description |
|----------|----------|-------------|
| **Streamable HTTP** (default) | `/mcp` | Modern HTTP-based protocol |
| **SSE** | `/sse` + `/mcp/message` | Server-Sent Events - stable |

**Configuration** (in `application.yml`):

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE  # or: SSE
```

**Environment Variable Override** (no config file edit needed):

```bash
export SPRING_AI_MCP_SERVER_PROTOCOL=SSE  # if you prefer SSE
```

---

## Technology Stack

### Rationale

| Technology | Choice | Rationale |
|------------|--------|-----------|
| **Language** | Java 17+ | Virtual threads, records, pattern matching |
| **Framework** | Spring Boot 3.2+ | Production-ready, extensive ecosystem |
| **Database** | PostgreSQL 16 | ACID compliance, pgvector extension |
| **Vector Search** | pgvector 0.8 | Native PostgreSQL integration, HNSW indexes |
| **Migrations** | Flyway | Version-controlled schema evolution |
| **Build** | Maven | Standard Java tooling |
| **Proxy** | Node.js/Express | Lightweight, fast startup |

### Java 17+ Features Used

```java
// Records for DTOs
public record ObservationDto(
    String id,
    String content,
    List<String> facts,
    List<String> concepts
) {}

// Pattern matching
if (entity instanceof ObservationEntity o) {
    return o.getContent();
}

// Virtual threads (Java 21)
@Async  // Uses virtual threads when enabled
public void processAsync() { ... }

// Text blocks
String prompt = """
    You are a memory observer.
    Analyze the following tool use:
    %s
    """.formatted(content);
```

### Spring Boot Configuration

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true  # Java 21 virtual threads

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL}
      chat:
        options:
          model: ${OPENAI_MODEL}

  datasource:
    url: jdbc:postgresql://localhost:5432/cortexce
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: none  # Flyway handles schema
    show-sql: false
```

---

## Design Decisions

### Decision 1: Thin Proxy Pattern

**Context**: CLI hooks have strict timeout requirements (< 1 second)

**Decision**: Separate hook reception from heavy processing

**Consequences**:
- (+) Hooks always respond quickly
- (+) Processing failures don't affect AI assistant
- (-) Additional deployment complexity
- (-) Eventual consistency

### Decision 2: PostgreSQL + pgvector vs Dedicated Vector DB

**Context**: Need vector search capabilities

**Options Considered**:
| Option | Pros | Cons |
|--------|------|------|
| pgvector | Single DB, ACID, simple ops | Limited scale (millions) |
| Pinecone | Managed, high scale | External dependency, cost |
| Milvus | Open source, high scale | Operational complexity |
| Chroma | Simple, embedded | Not production-ready |

**Decision**: PostgreSQL + pgvector

**Rationale**:
- Simplicity of single database
- ACID guarantees for observations + vectors
- Sufficient for typical use case (< 1M observations)
- Easy local development

### Decision 3: Async Processing with Virtual Threads

**Context**: Need non-blocking processing for LLM calls

**Options Considered**:
| Option | Pros | Cons |
|--------|------|------|
| Callbacks | Non-blocking | Callback hell |
| Reactive (WebFlux) | Backpressure | Learning curve, complexity |
| @Async + Virtual Threads | Simple, efficient | Java 21 required |

**Decision**: `@Async` with virtual threads

**Rationale**:
- Simple programming model
- Efficient for I/O-bound tasks (LLM, embedding calls)
- No reactive complexity

### Decision 4: Multi-Dimension Embeddings

**Context**: Different embedding models produce different dimensions

**Decision**: Support 768, 1024, 1536 dimensions in same table

```sql
embedding_768  vector(768),
embedding_1024 vector(1024),  -- Primary
embedding_1536 vector(1536),
embedding_model_id VARCHAR(50)
```

**Rationale**:
- Flexibility to switch models
- Migration path without data loss
- Model tracking for queries

---

## Trade-offs

| Trade-off | Choice | Alternative | Reason |
|-----------|--------|-------------|--------|
| **Complexity vs Reliability** | Thin Proxy + Fat Server | Monolith | Hooks must be fast |
| **Consistency vs Availability** | Eventual consistency | Strong consistency | Processing is async |
| **Flexibility vs Simplicity** | Multi-dimension embeddings | Single dimension | Model flexibility |
| **Scale vs Operations** | Single PostgreSQL | Distributed DB | Operational simplicity |

---

## Scalability Considerations

### Current Limits

| Resource | Limit | Mitigation |
|----------|-------|------------|
| Observations per project | ~1M | Partitioning, archival |
| Concurrent sessions | ~100 | Rate limiting |
| Vector search latency | ~100ms | Index tuning, caching |

### Scaling Strategies

1. **Vertical Scaling**
   - More CPU for embedding computation
   - More RAM for caching
   - Faster storage for vector indexes

2. **Horizontal Scaling**
   - Multiple Fat Server instances
   - Load balancer for API layer
   - Read replicas for PostgreSQL

3. **Caching Layer**
   ```java
   @Cacheable("observations")
   public List<Observation> search(String query) { ... }
   ```

4. **Database Partitioning**
   ```sql
   -- Partition by project_path for large deployments
   CREATE TABLE mem_observations (
       ...
   ) PARTITION BY LIST (project_path_hash);
   ```

---

## Security Architecture

### Authentication

Currently no authentication (local development). For production:

```yaml
# Future: Spring Security
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
```

### Data Privacy

```java
// Privacy tags stripping
public String stripPrivateTags(String content) {
    return content.replaceAll("<private>.*?</private>", "[REDACTED]");
}
```

### Network Security

| Component | Binding | Access |
|-----------|---------|--------|
| Fat Server | localhost:37777 | Local only |
| PostgreSQL | localhost:5432 | Local only |
| Proxy | N/A (CLI) | No network |

### Secrets Management

```bash
# Environment variables (not committed)
export OPENAI_API_KEY=sk-xxx
export DB_PASSWORD=xxx

# Or use .env file (gitignored)
cp .env.example .env
```

---

## Future Architecture Improvements

1. **Redis Cache Layer** - Hot observation caching
2. **Kafka/Event Bus** - Event-driven architecture
3. **Kubernetes Deployment** - Container orchestration
4. **Multi-tenancy** - Project isolation
5. **GraphQL API** - Flexible querying

---

*Architecture documentation version 1.0*
*Last updated: 2026*
