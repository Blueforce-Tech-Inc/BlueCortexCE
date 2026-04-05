# Cortex 社区版架构

> **English Version**: [ARCHITECTURE.md](ARCHITECTURE.md)

本文档描述了 Cortex 社区版的架构，包括系统设计、组件交互、数据流和技术决策。

## 目录

- [概述](#概述)
- [架构模式：瘦代理 + 胖服务器](#架构模式瘦代理--胖服务器)
- [系统架构](#系统架构)
- [核心组件](#核心组件)
  - [瘦代理](#瘦代理)
  - [胖服务器](#胖服务器)
  - [PostgreSQL + pgvector](#postgresql--pgvector)
- [数据流](#数据流)
- [API 层](#api-层)
- [技术栈](#技术栈)
- [设计决策](#设计决策)
- [权衡取舍](#权衡取舍)
- [可扩展性考虑](#可扩展性考虑)
- [安全架构](#安全架构)

---

## 概述

Cortex 社区版是一个为 AI 助手提供增强记忆的系统，具备以下功能：

- **持久化记忆**：跨会话的上下文存储和检索
- **智能质量评估**：自动评估和优先级排序
- **上下文感知检索**：基于向量的语义搜索
- **记忆演进**：自动优化和改进

该系统旨在解决 AI 开发环境中的 **CLI Hook 超时问题**，在这些问题中，同步处理记忆操作会阻塞 AI 助手的响应循环。

---

## 架构模式：瘦代理 + 胖服务器

### 问题所在

在 AI 开发环境（如 Claude Code、Cursor IDE）中，hook 是同步执行的：

```
AI 助手 → Hook 执行 → Hook 返回 → AI 继续
                    ↑
              超时风险！
```

如果 hook 耗时过长（LLM 调用、嵌入生成、数据库写入），AI 助手将超时并失败。

### 解决方案

Cortex CE 使用 **瘦代理 + 胖服务器** 架构将 hook 执行与重量级处理解耦：

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Hook 执行层                                   │
│                                                                     │
│  ┌──────────────┐                                                   │
│  │  CLI Hook    │  ← 必须在 200ms 内完成                            │
│  │ (wrapper.js) │                                                   │
│  └──────┬───────┘                                                   │
│         │ HTTP POST                                                 │
│         ▼                                                           │
│  ┌──────────────┐                                                   │
│  │  瘦代理       │  ← 接收请求，转发，立即响应                        │
│  │  (Express)   │                                                   │
│  └──────┬───────┘                                                   │
└─────────┼───────────────────────────────────────────────────────────┘
          │
          │ HTTP (异步)
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        处理层                                         │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    胖服务器 (Spring Boot)                      │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │  │
│  │  │ LLM 服务     │  │  嵌入服务    │  │ 质量评估             │  │  │
│  │  │             │  │             │  │ & 优化              │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘  │  │
│  │                                                              │  │
│  │  ┌─────────────────────────────────────────────────────────┐│  │
│  │  │              异步处理队列                                 ││  │
│  │  └─────────────────────────────────────────────────────────┘│  │
│  └──────────────────────────────────────────────────────────────┘  │
│         │                                                           │
│         │ JDBC                                                      │
│         ▼                                                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              PostgreSQL + pgvector                            │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │  │
│  │  │   会话       │  │  观察        │  │  向量索引           │  │  │
│  │  │             │  │  + 嵌入向量  │  │  (HNSW)            │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 主要优势

| 优势 | 描述 |
|------|------|
| **快速 Hook 响应** | 代理在 200ms 内响应，避免超时 |
| **可靠处理** | 胖服务器通过重试逻辑处理故障 |
| **资源效率** | 重操作不阻塞 AI 助手 |
| **可扩展性** | 胖服务器可独立扩展 |

---

## 系统架构

### 高层视图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              客户端层                                        │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐   │
│  │ Claude Code │  │  Cursor IDE │  │  OpenClaw   │  │  自定义客户端    │   │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘   │
│         │                │                │                  │             │
│         └────────────────┴────────────────┴──────────────────┘             │
│                                    │                                        │
│         ┌──────────────────────────┼──────────────────────────┐            │
│         │                          │                          │            │
│    ┌────┴─────┐            ┌──────┴──────┐            ┌──────┴──────┐    │
│    │ Java SDK │            │  Go SDK     │            │ Python SDK  │    │
│    │(spring-  │            │ (go-sdk/)   │            │(python-sdk/ │    │
│    │integra-  │            │             │            │             │    │
│    │tion/)    │            │             │            │             │    │
│    └──────────┘            └─────────────┘            └─────────────┘    │
│                                                                          │
│                             ┌─────────────┐                              │
│                             │  JS SDK     │                              │
│                             │ (js-sdk/)   │                              │
│                             └─────────────┘                              │
│                              Hooks / API                                     │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           集成层                                             │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         瘦代理 (Node.js)                             │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐  │   │
│  │  │ wrapper.js  │  │  proxy.js   │  │  事件转发逻辑                 │  │   │
│  │  │ (CLI 入口)  │  │ (HTTP 服务器│  │  - 会话 开始/结束             │  │   │
│  │  │             │  │   可选)     │  │  - 工具使用事件               │  │   │
│  │  └─────────────┘  └─────────────┘  │  - 用户提示                   │  │   │
│  │                                    └─────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ HTTP REST / SSE
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          应用层                                              │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    胖服务器 (Spring Boot)                            │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │                     控制器层                                   │  │   │
│  │  │  Ingestion · Viewer · Context · Session · Memory · Mode      │  │   │
│  │  │  Extraction · Import · Cursor · Stream · Logs · Health · Test│  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │                      服务层                                    │  │   │
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
│  │  │                    异步处理                                     │  │   │
│  │  │  ┌───────────────────┐  ┌─────────────────────────────────┐  │  │   │
│  │  │  │ @Async 方法        │  │  陈旧消息恢复任务                │  │  │   │
│  │  │  │ (虚拟线程)        │  │  (崩溃恢复 + 去重)               │  │  │   │
│  │  │  └───────────────────┘  └─────────────────────────────────┘  │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ JDBC / JPA
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            数据层                                            │
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
│  │  │                    向量索引 (HNSW)                              │    │   │
│  │  │  • embedding_768  (vector_cosine_ops)                       │    │   │
│  │  │  • embedding_1024 (vector_cosine_ops) ← 主索引              │    │   │
│  │  │  • embedding_1536 (vector_cosine_ops)                       │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ HTTP API
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          外部服务                                            │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │   LLM 提供商     │  │ 嵌入服务         │  │     IDE 集成                 │ │
│  │  (DeepSeek /    │  │  (SiliconFlow   │  │                             │ │
│  │   Anthropic)    │  │   bge-m3)       │  │  • Claude Code              │ │
│  │                 │  │                 │  │  • Cursor IDE               │ │
│  │  • 聊天补全      │  │  • 768 维       │  │  • OpenClaw Gateway         │ │
│  │  • 摘要          │  │  • 1024 维      │  │                             │ │
│  │  • 优化          │  │  • 1536 维     │  │                             │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 核心组件

### 瘦代理

瘦代理是一个轻量级 Node.js 应用程序，负责快速事件转发。

#### 职责

| 职责 | 描述 |
|------|------|
| Hook 事件接收 | 接收来自 CLI hooks 的事件 |
| 事件转发 | 通过 HTTP 转发到胖服务器 |
| 快速响应 | 200ms 内响应以避免超时 |
| 错误处理 | 故障时优雅降级 |

#### 组件

```
proxy/
├── wrapper.js                      # CLI 入口点 (由 hooks 调用)
├── proxy.js                        # 可选的 HTTP 服务器用于本地聚合
├── tag-stripping.js                # 隐私标签剥离逻辑
├── package.json                    # 依赖项 (axios)
├── package-lock.json
├── CLAUDE-CODE-INTEGRATION.md      # Claude Code 集成文档
├── CLAUDE-CODE-INTEGRATION-zh-CN.md
├── CURSOR-INTEGRATION.md           # Cursor IDE 集成文档
├── CURSOR-INTEGRATION-zh-CN.md
├── README.md
└── java/
    └── proxy/
        └── test-full-flow.mjs      # E2E 测试脚本
```

#### Hook 事件流程

```javascript
// wrapper.js - CLI 入口点
const event = process.argv[2];  // 'session-start', 'tool-use' 等
const data = readFromStdin();

// 快速转发到胖服务器
await axios.post('http://localhost:37777/api/ingest/' + event, data);

// 立即退出
process.exit(0);
```

#### 性能要求

| 指标 | 目标 |
|------|------|
| 响应时间 | < 200ms |
| 内存占用 | < 50MB |
| 启动时间 | < 100ms |

---

### 胖服务器

胖服务器是处理所有业务逻辑的核心 Spring Boot 应用程序。

#### 职责

| 职责 | 描述 |
|------|------|
| 事件处理 | 异步处理 hook 事件 |
| LLM 集成 | 聊天补全、摘要、优化 |
| 嵌入生成 | 用于语义搜索的向量嵌入 |
| 质量评估 | 为记忆打分和排序 |
| 上下文生成 | 生成用于 AI 注入的上下文 |
| API 服务 | REST API 和 MCP 服务器 |

#### 服务架构

```
┌─────────────────────────────────────────────────────────────┐
│                      控制器层                                 │
│                                                             │
│  IngestionController    →  /api/ingest/*                   │
│  ViewerController       →  /api/* (observations, search,    │
│                         │    summaries, prompts, projects,   │
│                         │    stats, settings, modes,         │
│                         │    timeline, processing-status,    │
│                         │    sdk-sessions)                   │
│  ContextController      →  /api/context/*                  │
│  StreamController       →  /stream (SSE)                    │
│  LogsController         →  /api/logs                      │
│  HealthController       →  /api/health, /api/readiness,     │
│                         │    /api/version                    │
│  SessionController      →  /api/session/*                 │
│  MemoryController       →  /api/memory/*                  │
│  ModeController         →  /api/mode/*                     │
│  ExtractionController   →  /api/extraction/*              │
│  ImportController       →  /api/import/*                  │
│  CursorController       →  /api/cursor/*                  │
│  TestController         →  /api/test/*                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       服务层                                 │
│                                                             │
│  AgentService           → 核心编排                          │
│    ├── LlmService       → 聊天补全                         │
│    ├── EmbeddingService → 向量嵌入                         │
│    └── XmlParser (util) → 解析 LLM XML 输出 (正则)         │
│                                                             │
│  SearchService          → 语义 + 文本搜索                   │
│  ContextCacheService    → 上下文缓存                        │
│  TimelineService        → 时间线上下文生成                  │
│  ClaudeMdService        → CLAUDE.md 生成                   │
│  TokenService           → Token 计数                        │
│  RateLimitService       → 按会话速率限制                    │
│  ProjectFilterService   → 项目路径过滤                     │
│  ModeService            → 记忆模式管理                      │
│  MemoryRefineService    → 记忆优化                          │
│  StructuredExtractionService → 结构化数据提取               │
│  ExtractionStorageService → 提取结果持久化                  │
│  SessionManagementService → 会话生命周期                    │
│  SummaryGenerationService → 摘要生成                        │
│  TemplateService        → 提示模板管理                      │
│  SettingsService        → 应用设置                          │
│  ImportService          → 数据导入                          │
│  CursorService          → Cursor IDE 集成                   │
│  ExpRagService          → 实验性 RAG                        │
│  ContextService         → 上下文生成与管理                   │
│  SSEBroadcaster         → SSE 事件广播                      │
│  PendingMessageProcessor → 待处理消息队列处理                │
│  LlmQualityScorer       → 基于 LLM 的质量评分               │
│  WorktreeDetector       → Git 工作树检测                    │
│  ExperienceTemplate     → 经验检索模板                      │
│  QualityScorer          → 观察质量评分                      │
│  StaleMessageRecoveryTask → 陈旧消息崩溃恢复                │
│  ClaudeMemMcpTools (mcp)→ MCP 工具实现                     │
│                                                             │
│  事件类 (event/)                                            │
│  PendingMessageEvent + Listener + Publisher                 │
│  MemoryRefineEvent   + Listener + Publisher                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     仓储层                                   │
│                                                             │
│  SessionRepository      → 会话 CRUD                         │
│  ObservationRepository  → CRUD + 向量搜索                   │
│  SummaryRepository      → 摘要 CRUD                        │
│  UserPromptRepository   → 用户提示 CRUD                    │
│  PendingMessageRepository → 崩溃恢复队列                   │
└─────────────────────────────────────────────────────────────┘
```

#### 核心流程：观察创建

```
工具使用事件
      │
      ▼
┌─────────────────┐
│ IngestionCtrl   │  POST /api/ingest/tool-use
└────────┬────────┘
         │ @Async
         ▼
┌─────────────────┐
│  AgentService   │  编排
└────────┬────────┘
         │
         ├──────────────────┐
         │                  │
         ▼                  ▼
┌─────────────────┐  ┌─────────────────┐
│   LlmService    │  │  提示模板       │
│                 │  │                 │
│ DeepSeek API    │  │ observation.txt │
│ 聊天补全         │  │                 │
└────────┬────────┘  └─────────────────┘
         │
         ▼
┌─────────────────┐
│   XmlParser     │  从 XML 提取:
│                 │  <observation>
│  基于正则表达式   │    <facts>...</facts>
│  (非 XML 解析器) │    <concepts>...</concepts>
└────────┬────────┘  </observation>
         │
         ▼
┌─────────────────┐
│EmbeddingService │
│                 │
│ SiliconFlow API │  bge-m3 → 1024 维向量
│                 │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Observation    │
│  Repository     │  PostgreSQL + pgvector
│                 │  HNSW 索引用于相似度搜索
└─────────────────┘
```

#### 异步处理

```java
@Service
public class AgentService {

    @Async  // 虚拟线程
    public void processToolUseAsync(ToolUseEvent event) {
        // 1. 生成 LLM 响应
        String llmResponse = llmService.chatCompletion(prompt);

        // 2. 解析观察
        ObservationData data = xmlParser.parse(llmResponse);

        // 3. 生成嵌入向量
        float[] embedding = embeddingService.embed(data.getContent());

        // 4. 保存到数据库
        observationRepository.save(observation);
    }
}
```

#### 崩溃恢复

```
┌─────────────────────────────────────────────────────────┐
│                待处理消息队列                              │
│                                                         │
│  mem_pending_messages 表:                               │
│  • id (UUID)                                            │
│  • session_db_id (FK → mem_sessions.id, CASCADE)        │
│  • content_session_id                                   │
│  • message_type ('observation' / 'summarize')           │
│  • tool_name                                            │
│  • tool_input, tool_response                            │
│  • tool_input_hash ← 去重 (V6)                          │
│  • status (pending/processing/processed/failed)         │
│  • retry_count                                          │
│  • created_at_epoch                                     │
│                                                         │
│  PendingMessageProcessor 在启动时 + 定期运行:             │
│  1. 查找 status='pending' 的消息                         │
│  2. 通过 tool_input_hash 去重处理                        │
│  3. 标记为 processed 或 failed                          │
└─────────────────────────────────────────────────────────┘
```

---

### PostgreSQL + pgvector

#### 架构概览

```sql
-- 会话表 (V1 + V4, V12, V13, V15 迁移)
CREATE TABLE mem_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_session_id VARCHAR(255) UNIQUE NOT NULL,  -- V13: 替代 memory_session_id
    project_path TEXT NOT NULL,
    user_id VARCHAR(255),              -- V15: null=单用户, 非null=SDK多用户
    user_prompt TEXT,
    last_assistant_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    started_at_epoch BIGINT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_at_epoch BIGINT,
    status VARCHAR(50) DEFAULT 'active',  -- active/completed/skipped
    total_steps INT DEFAULT 0,            -- V12: 步骤效率追踪
    avg_steps_per_task FLOAT,             -- V12
    -- 上下文缓存 (V4)
    cached_context TEXT,
    context_refreshed_at_epoch BIGINT,
    needs_context_refresh BOOLEAN DEFAULT FALSE
);

-- 观察表 (V1 + V2, V8, V11, V12, V13, V14, V16 迁移)
CREATE TABLE mem_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_session_id VARCHAR(255) NOT NULL REFERENCES mem_sessions(content_session_id),  -- V13: 统一会话链接（替代 memory_session_id）
    project_path TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title TEXT,
    subtitle TEXT,
    content TEXT,
    facts JSONB,              -- ["fact1", "fact2"]
    concepts JSONB,           -- ["concept1", "concept2"]
    files_read JSONB,
    files_modified JSONB,
    source TEXT,              -- V14: tool_result/user_statement/llm_inference/manual
    extracted_data JSONB,     -- V14: 结构化键值数据
    quality_score FLOAT,      -- V11
    feedback_type VARCHAR(20),-- V11: SUCCESS/PARTIAL/FAILURE/UNKNOWN
    last_accessed_at TIMESTAMP WITH TIME ZONE, -- V11
    access_count INT DEFAULT 0, -- V11
    refined_at TIMESTAMP WITH TIME ZONE, -- V11
    refined_from_ids TEXT,    -- V11: 逗号分隔的合并来源 ID
    user_comment TEXT,        -- V11: WebUI 反馈
    feedback_updated_at TIMESTAMP WITH TIME ZONE, -- V11
    step_number INT,          -- V12
    discovery_tokens INT DEFAULT 0,
    prompt_number INT,
    content_hash VARCHAR(16), -- V8

    -- 多维嵌入向量 (V2)
    embedding_768  vector(768),
    embedding_1024 vector(1024),
    embedding_1536 vector(1536),
    embedding_model_id VARCHAR(255),

    -- 全文搜索 (V1)
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
    ) STORED,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at_epoch BIGINT NOT NULL
);

-- 向量索引 (HNSW, V2)
CREATE INDEX idx_obs_embedding_768 ON mem_observations
    USING hnsw (embedding_768 vector_cosine_ops);
CREATE INDEX idx_obs_embedding_1024 ON mem_observations
    USING hnsw (embedding_1024 vector_cosine_ops);
CREATE INDEX idx_obs_embedding_1536 ON mem_observations
    USING hnsw (embedding_1536 vector_cosine_ops);

-- 全文搜索索引
CREATE INDEX idx_obs_search ON mem_observations USING GIN(search_vector);

-- 摘要表 (V1 + V13)
CREATE TABLE mem_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_session_id VARCHAR(255) NOT NULL REFERENCES mem_sessions(content_session_id),  -- V13: 替代 memory_session_id
    project_path TEXT NOT NULL,
    request TEXT,
    investigated TEXT,
    learned TEXT,
    completed TEXT,
    next_steps TEXT,
    files_read TEXT,
    files_edited TEXT,
    notes TEXT,
    prompt_number INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at_epoch BIGINT NOT NULL
);

-- 用户提示表 (V1 + V5)
CREATE TABLE mem_user_prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_session_id VARCHAR(255) NOT NULL REFERENCES mem_sessions(content_session_id),
    prompt_number INT NOT NULL,
    prompt_text TEXT NOT NULL,
    project_path TEXT,               -- V5: 项目过滤
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at_epoch BIGINT NOT NULL
);

-- 待处理消息表 (V1 + V6)
CREATE TABLE mem_pending_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_db_id UUID NOT NULL REFERENCES mem_sessions(id) ON DELETE CASCADE,
    content_session_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(50) NOT NULL,  -- 'observation' 或 'summarize'
    tool_name TEXT,
    tool_input TEXT,
    tool_response TEXT,
    cwd TEXT,                           -- hook 时的工作目录
    last_user_message TEXT,             -- 最后的用户消息上下文
    last_assistant_message TEXT,        -- 最后的助手消息上下文
    prompt_number INT,                  -- 会话 prompt 编号
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    retry_count INT NOT NULL DEFAULT 0,
    tool_input_hash VARCHAR(64),        -- V6: 去重
    created_at_epoch BIGINT NOT NULL,
    started_processing_at_epoch BIGINT,
    completed_at_epoch BIGINT,
    failed_at_epoch BIGINT
);
```

#### 语义搜索

```sql
-- 全文 + 向量混合搜索
SELECT id, content,
       1 - (embedding_1024 <=> :query_vector) as similarity
FROM mem_observations
WHERE project_path = :project_path
  AND search_vector @@ plainto_tsquery('english', :text_query)
ORDER BY embedding_1024 <=> :query_vector
LIMIT :limit;
```

---

## 数据流

### 完整事件流程

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           完整事件流程                                         │
└──────────────────────────────────────────────────────────────────────────────┘

1. 会话开始
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Ingestion   │────▶│ PostgreSQL  │
│  Hook       │     │ session-start│    │ Controller  │     │ Session     │
└─────────────┘     └─────────────┘     └─────────────┘     │ 已创建      │
                    < 200ms             异步                └─────────────┘

2. 工具使用 (观察)
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Ingestion   │
│ PostToolUse │     │ observation │     │ Controller  │
└─────────────┘     └─────────────┘     └──────┬──────┘
                    < 200ms                    │ @Async
                                               ▼
                    ┌─────────────────────────────────────────────┐
                    │              异步处理管道                      │
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
                    │  │ XmlParser   │ 提取 facts/concepts       │
                    │  └──────┬──────┘                            │
                    │         │                                    │
                    │         ▼                                    │
                    │  ┌─────────────┐     ┌─────────────┐        │
                    │  │ 嵌入服务     │────▶│ SiliconFlow │        │
                    │  │             │     │ bge-m3 API  │        │
                    │  └──────┬──────┘     └─────────────┘        │
                    │         │                                    │
                    │         ▼                                    │
                    │  ┌─────────────┐     ┌─────────────┐        │
                    │  │ Observation │────▶│ PostgreSQL  │        │
                    │  │ Repository  │     │ + pgvector  │        │
                    │  └─────────────┘     └─────────────┘        │
                    └─────────────────────────────────────────────┘

3. 上下文注入
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Context     │────▶│ PostgreSQL  │
│ SessionStart│     │ context-get │     │ Service     │     │ 向量搜索    │
│ (下一会话)  │     │             │     │             │     │             │
└─────────────┘     └─────────────┘     └──────┬──────┘     └─────────────┘
                    < 200ms                    │                    │
                                               ▼                    │
                    ┌─────────────────────────────────────────────┐│
                    │           上下文生成                          ││
                    │                                              ││
                    │  1. 最近观察的语义搜索                        ││
                    │  2. 时间线上下文组装                          ││
                    │  3. CLAUDE.md 文件生成                       ││
                    │                                              ││
                    └─────────────────────────────────────────────┘│
                                               │                    │
                                               ▼                    │
                    ┌─────────────────────────────────────────────┐│
                    │           CLAUDE.md 注入                     ││
                    │                                              ││
                    │  # 项目上下文                                 ││
                    │  生成时间: 2026-01-15                        ││
                    │                                              ││
                    │  ## 最近工作                                 ││
                    │  - 观察 1...                                 ││
                    │  - 观察 2...                                 ││
                    │                                              ││
                    └─────────────────────────────────────────────┘│

4. 会话结束 (摘要)
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Ingestion   │────▶│ PostgreSQL  │
│ SessionEnd  │     │ summarize   │     │ Controller  │     │ Summary     │
└─────────────┘     └─────────────┘     └──────┬──────┘     │ 已保存      │
                    < 200ms                    │ @Async      └─────────────┘
                                               ▼
                    ┌─────────────────────────────────────────────┐
                    │              摘要处理管道                     │
                    │                                              │
                    │  1. 收集所有会话观察                           │
                    │  2. LLM 摘要生成                              │
                    │  3. 保存带 token 的摘要                       │
                    │  4. 更新会话状态                              │
                    │                                              │
                    └─────────────────────────────────────────────┘
```

---

## API 层

### REST API

| 层 | 路径模式 | 描述 |
|---|----------|------|
| Ingestion | `/api/ingest/*` | Hook 事件接收（tool-use、user-prompt、observation、session-end） |
| Session | `/api/session/*` | 会话生命周期（start、get、patch user） |
| Viewer | `/api/observations`, `/api/summaries`, `/api/prompts`, `/api/projects`, `/api/stats`, `/api/search`, `/api/search/by-file`, `/api/observations/batch`, `/api/settings`, `/api/modes`, `/api/timeline`, `/api/processing-status`, `/api/sdk-sessions/batch` | WebUI 数据 |
| Context | `/api/context/*` | 上下文检索 |
| Memory | `/api/memory/*` | 记忆操作（refine、experiences、icl-prompt、quality-distribution、feedback、patch/delete observation） |
| Mode | `/api/mode/*` | 记忆模式管理（get/put、types、concepts、validation） |
| Extraction | `/api/extraction/*` | 结构化数据提取（run、{templateName}/latest、{templateName}/history） |
| Cursor | `/api/cursor/*` | Cursor IDE 集成（register、check/unregister、context、projects） |
| Import | `/api/import/*` | 数据导入（bulk、sessions、observations、summaries、prompts） |
| Stream | `/stream` | SSE 实时更新 |
| Logs | `/api/logs` | 日志访问（get、clear） |
| Health | `/api/health`, `/api/readiness`, `/api/version` | 健康和版本检查 |
| Test | `/api/test/*` | 测试/调试端点（llm、embedding、all） |

### MCP 服务器

用于 AI 助手集成的模型上下文协议：

| 工具 | 描述 |
|------|------|
| `search` | 基于向量相似度的语义搜索 |
| `timeline` | 上下文时间线检索 |
| `get_observations` | 批量观察详情 |
| `save_memory` | 手动保存记忆 |
| `recent` | 最近会话摘要 |

#### MCP 传输协议

MCP 服务器支持两种传输协议：

| 协议 | 端点 | 描述 |
|------|------|------|
| **SSE**（默认） | `/sse` + `/mcp/message` | Server-Sent Events - 稳定 |
| **Streamable HTTP** | `/mcp` | 基于 HTTP 的现代协议 |

**配置**（在 `application.yml` 中）：

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: SSE  # 默认: SSE。备选: STREAMABLE（需要会话管理）
```

**环境变量覆盖**（无需编辑配置文件）：

```bash
export SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE  # 如需使用 STREAMABLE
```

---

## 技术栈

### 选择理由

| 技术 | 选择 | 理由 |
|------|------|------|
| **语言** | Java 21+ | 虚拟线程、record、模式匹配 |
| **框架** | Spring Boot 3.3.13 | 生产就绪、广泛的生态系统 |
| **数据库** | PostgreSQL 16 | ACID 合规、pgvector 扩展 |
| **向量搜索** | pgvector 0.8 | 原生 PostgreSQL 集成、HNSW 索引 |
| **迁移** | Flyway | 版本控制的架构演进 |
| **构建** | Maven | 标准 Java 工具 |
| **代理** | Node.js/Express | 轻量级、快速启动 |

### 使用的 Java 21+ 特性

```java
// Records 用于 DTO
public record ObservationDto(
    String id,
    String content,
    List<String> facts,
    List<String> concepts
) {}

// 模式匹配
if (entity instanceof ObservationEntity o) {
    return o.getContent();
}

// 虚拟线程 (Java 21)
@Async  // 启用时使用虚拟线程
public void processAsync() { ... }

// 文本块
String prompt = """
    You are a memory observer.
    Analyze the following tool use:
    %s
    """.formatted(content);
```

### Spring Boot 配置

```yaml
# application.yml（代表性摘录 — 完整配置请参见实际文件）
server:
  port: ${SERVER_PORT:37777}
  address: ${SERVER_ADDRESS:127.0.0.1}

spring:
  threads:
    virtual:
      enabled: true  # Java 21 虚拟线程

  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://127.0.0.1/claude_mem_dev}
    username: ${SPRING_DATASOURCE_USERNAME:${DB_USERNAME:postgres}}
    password: ${SPRING_DATASOURCE_PASSWORD:${DB_PASSWORD:123456}}

  jpa:
    hibernate:
      ddl-auto: none  # Flyway 处理架构
    show-sql: false

# LLM 提供商 (openai 或 anthropic) — 在 SpringAiConfig 中手动装配
claudemem:
  llm:
    provider: ${CLAUDEMEM_LLM_PROVIDER:openai}
```

**环境变量**（通过 `.env` 或 shell 设置）：

| 变量 | 默认值 | 描述 |
|------|--------|------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1/claude_mem_dev` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | `123456` | 数据库密码 |
| `SPRING_AI_OPENAI_API_KEY` | — | LLM API 密钥（DeepSeek 等） |
| `SPRING_AI_OPENAI_BASE_URL` | `https://api.deepseek.com` | LLM 基础 URL |
| `SPRING_AI_OPENAI_CHAT_MODEL` | `deepseek-chat` | 聊天模型名称 |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | — | 嵌入 API 密钥 |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | `https://api.siliconflow.cn` | 嵌入基础 URL |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | `BAAI/bge-m3` | 嵌入模型 |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | `1024` | 嵌入向量维度 |
| `CLAUDEMEM_LLM_PROVIDER` | `openai` | `openai` 或 `anthropic` |
| `SPRING_AI_ANTHROPIC_API_KEY` | — | Anthropic API 密钥（当 provider=anthropic 时） |
| `SPRING_AI_ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | Anthropic 基础 URL |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | `claude-sonnet-4-5` | Anthropic 聊天模型 |

---

## 设计决策

### 决策 1：瘦代理模式

**背景**：CLI hooks 有严格的超时要求（< 1 秒）

**决策**：将 hook 接收与重量级处理分离

**后果**：
- (+) Hooks 始终快速响应
- (+) 处理故障不影响 AI 助手
- (-) 增加部署复杂性
- (-) 最终一致性

### 决策 2：PostgreSQL + pgvector vs 专用向量数据库

**背景**：需要向量搜索能力

**考虑的选项**：
| 选项 | 优点 | 缺点 |
|------|------|------|
| pgvector | 单数据库、ACID、简单操作 | 规模有限（百万级） |
| Pinecone | 托管、高扩展 | 外部依赖、成本 |
| Milvus | 开源、高扩展 | 运维复杂性 |
| Chroma | 简单、嵌入式 | 不适合生产 |

**决策**：PostgreSQL + pgvector

**理由**：
- 单数据库的简单性
- 观察 + 向量的 ACID 保证
- 典型用例足够（< 100 万观察）
- 本地开发简单

### 决策 3：虚拟线程的异步处理

**背景**：需要非阻塞处理 LLM 调用

**考虑的选项**：
| 选项 | 优点 | 缺点 |
|------|------|------|
| 回调 | 非阻塞 | 回调地狱 |
| 响应式 (WebFlux) | 背压 | 学习曲线、复杂性 |
| @Async + 虚拟线程 | 简单、高效 | 需要 Java 21 |

**决策**：@Async + 虚拟线程

**理由**：
- 简单的编程模型
- 对 I/O 密集型任务（LLM、嵌入调用）高效
- 无响应式复杂性

### 决策 4：多维嵌入向量

**背景**：不同的嵌入模型产生不同的维度

**决策**：在同一表中支持 768、1024、1536 维度

```sql
embedding_768  vector(768),
embedding_1024 vector(1024),  -- 主索引
embedding_1536 vector(1536),
embedding_model_id VARCHAR(50)
```

**理由**：
- 切换模型的灵活性
- 无数据丢失的迁移路径
- 查询的模型跟踪

---

## 权衡取舍

| 权衡 | 选择 | 替代方案 | 原因 |
|------|------|----------|------|
| **复杂性 vs 可靠性** | 瘦代理 + 胖服务器 | 单体 | Hooks 必须快速 |
| **一致性 vs 可用性** | 最终一致性 | 强一致性 | 处理是异步的 |
| **灵活性 vs 简单性** | 多维嵌入向量 | 单维度 | 模型灵活性 |
| **规模 vs 运维** | 单 PostgreSQL | 分布式数据库 | 运维简单性 |

---

## 可扩展性考虑

### 当前限制

| 资源 | 限制 | 缓解措施 |
|------|------|----------|
| 每个项目的观察数 | ~100 万 | 分区、归档 |
| 并发会话 | ~100 | 速率限制 |
| 向量搜索延迟 | ~100ms | 索引调优、缓存 |

### 扩展策略

1. **垂直扩展**
   - 更多 CPU 用于嵌入计算
   - 更多 RAM 用于缓存
   - 更快的存储用于向量索引

2. **水平扩展**
   - 多胖服务器实例
   - 负载均衡器用于 API 层
   - PostgreSQL 读副本

3. **缓存层**
   ```java
   @Cacheable("observations")
   public List<Observation> search(String query) { ... }
   ```

4. **数据库分区**
   ```sql
   -- 按 project_path 分区用于大型部署
   CREATE TABLE mem_observations (
       ...
   ) PARTITION BY LIST (project_path_hash);
   ```

---

## 安全架构

### 认证

当前无认证（本地开发）。生产环境：

```yaml
# 未来：Spring Security
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
```

### 数据隐私

```java
// 隐私标签剥离
public String stripPrivateTags(String content) {
    return content.replaceAll("<private>.*?</private>", "[已编辑]");
}
```

### 网络安全

| 组件 | 绑定 | 访问 |
|------|------|------|
| 胖服务器 | 127.0.0.1:37777 | 仅本地 |
| PostgreSQL | 127.0.0.1:5432 | 仅本地 |
| 代理 | 不适用 (CLI) | 无网络 |

### 密钥管理

```bash
# 环境变量（不提交）
export SPRING_AI_OPENAI_API_KEY=sk-xxx
export SPRING_DATASOURCE_PASSWORD=xxx

# 或使用 .env 文件（gitignore）
cp .env.example .env
```

---

## 未来架构改进

1. **Redis 缓存层** - 热观察缓存
2. **Kafka/事件总线** - 事件驱动架构
3. **Kubernetes 部署** - 容器编排
4. **多租户** - 项目隔离
5. **GraphQL API** - 灵活查询

---

*架构文档版本 1.0*
*最后更新：2026 年*

---
*See also: [English Version](ARCHITECTURE.md)*
