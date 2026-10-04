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
│  │  (Node.js)   │                                                   │
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
const event = process.argv[2];  // 'session-start', 'tool-use', 'session-end', 'user-prompt'
const data = readFromStdin();

// 每个事件各有自己的端点——并不存在统一的 '/api/ingest/' 前缀
const ENDPOINTS = {
  'session-start': '/api/session/start',   // 由 SessionController 提供，而非 IngestionController
  'tool-use':      '/api/ingest/tool-use',
  'session-end':   '/api/ingest/session-end',
  'user-prompt':   '/api/ingest/user-prompt',
};
await axios.post('http://localhost:37777' + ENDPOINTS[event], data);

// 立即退出
process.exit(0);
```

**该映射是逐事件查表，不是前缀拼接。** `session-start` 由 `SessionController` 在
`POST /api/session/start` 提供，而非 `IngestionController`：`POST /api/ingest/session-start`
返回 **404**（2026-10-03 对运行中的后端实测）。`IngestionController` 恰好暴露四个端点
——`tool-use`、`session-end`、`user-prompt`、`observation`——其中没有任何一个负责开启会话。

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
│                         │    stats, settings/modes (GET+POST),│
│                         │    timeline, processing-status,    │
│                         │    observations/batch, search/by-file,│
│                         │    sdk-sessions/batch)             │
│  ContextController      →  /api/context/*（7 个端点，含 /semantic） │
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
│    ├── EmbeddingService → 向量嵌入                         │
│    └── XmlParser (util) → 解析 LLM XML 输出 (正则)         │
│  LlmService             → 聊天补全（DeepSeek/Anthropic）    │
│                                                             │
│  SearchService          → 语义 + 文本搜索                   │
│  ContextCacheService    → 上下文缓存                        │
│  TimelineService        → 时间线上下文生成                  │
│  ClaudeMdService        → CLAUDE.md 生成                   │
│  TokenService           → Token 计数                        │
│  RateLimitService       → 按会话速率限制                    │
│  ProjectFilterService   → 项目路径过滤                     │
│  ModeService            → 记忆模式管理                      │
│  MemoryRefineService   → 记忆优化 + 抽取互斥锁              │
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
│  ObservationFeedbackRepository → 反馈跟踪 (V17)             │
└─────────────────────────────────────────────────────────────┘
```

**`projectLocks` 保护的是抽取、不是精炼，而且它不是去重机制。**
`MemoryRefineService` 持有一个 `ConcurrentHashMap<String, ReentrantLock>`，并在两处使用它，
这两处串行化的是**结构化抽取**：`tryExecuteWithProjectLock`（由
`StructuredExtractionService.reExtractForSession` 调用）以及
`deepRefineProjectMemories` 的尾部。其行为就是互斥本身——捕获真正的重复抑制**仅**来自
`AgentService` 中的 `existsBySessionAndTool` 检查。实体上虽然声明了
`@UniqueConstraint(name = "uk_session_tool_input")`（session、tool_name、
`tool_input_hash` 三列），`AgentService` 里也有一段注释写着
"Duplicate pending message detected (concurrent insert)" 的
`catch (DataIntegrityViolationException)` 看起来依赖它——但**该约束在任何已部署的
数据库里都不存在**：`application.yml` 设的是 `spring.jpa.hibernate.ddl-auto: none`，
且没有任何 Flyway 迁移创建它，故 Hibernate 永远不会生成。真正在执行去重的只有应用层
那道检查，而它是**非原子的先查后写**：实测 8 个并发的相同 tool-use 事件落了 **8 行**
而非 1 行。参见
[P2-29](drafts/backend-review-findings.md)。另外注意
`deepRefineProjectMemories` 在整个代码库中没有任何调用方，因此只有
`tryExecuteWithProjectLock` 那一处是可达的。结构化抽取**完全由事件驱动**，
参见 [抽取并非定时执行](drafts/phase-3-design/23.md)。

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
│  • status (pending/processing/processed/failed/skipped) │
│  • retry_count                                          │
│  • created_at_epoch                                     │
│                                                         │
│  PendingMessageProcessor 在启动时 + 定期运行:           │
│  1. 查找 status='pending' 的消息                        │
│  2. 处理（去重已在入队时完成）                          │
│  3. 标记为 processed 或 failed                          │
└─────────────────────────────────────────────────────────┘
```

---

### PostgreSQL + pgvector

#### 架构概览

```sql
-- 会话表 (V1 + V4, V11, V12, V13, V15, V18 迁移)
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
    total_steps INT DEFAULT 0,            -- V11: 步骤效率追踪（V12 用 IF NOT EXISTS 重复声明，实为空操作）
    avg_steps_per_task FLOAT,             -- V11（同上）
    -- 上下文缓存 (V4)
    cached_context TEXT,
    context_refreshed_at_epoch BIGINT,
    needs_context_refresh BOOLEAN DEFAULT FALSE,
    platform_source VARCHAR(50) DEFAULT 'claude'  -- V18: 多平台
);

-- 观察表 (V1 + V2, V7, V8, V11, V12, V13, V14, V16, V17, V18 迁移)
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
    created_at_epoch BIGINT NOT NULL,
    generated_by_model VARCHAR(100),  -- V17: 生成此观察的模型
    relevance_count INT DEFAULT 0,     -- V17: 为使用信号预留；目前尚无写入方（P2-24）
    platform_source VARCHAR(50) DEFAULT 'claude'  -- V18: 多平台
);

> **排序的权威列是 `created_at_epoch`，不是 `created_at`。**
> 上面的 `DEFAULT NOW()` 是 DDL 默认值，但它只对**直接 SQL 插入**的行生效。
> 应用通过 JPA 写入，而主捕获路径 `AgentService` 只设置 `createdAtEpoch`；
> 既没有 `@PrePersist` 钩子也没有 JPA auditing，于是 Hibernate 把 `created_at`
> 原样发成 NULL，默认值从未触发。只有 `ImportService` 会显式赋值该时间列。
> 活体库实测（2026-10-03）结果是**大多数行的 `created_at` 为 NULL**：
> 观测表 38,088 行中有 19,711 行（51.8%），摘要表 6,590 行中有 6,589 行（99.98%）。
>
> PostgreSQL 在 `DESC` 下把 NULL 排在**最后**，因此任何 `ORDER BY created_at DESC`
> 返回的是**最旧**的非 NULL 行，而不是最新的行。`ObservationRepository` 与
> `SummaryRepository` 里所有手写 `@Query` 正是为此才按 `created_at_epoch` 排序，
> Spring Data 的派生方法也**必须**命名为 `…OrderByCreatedAtEpochDesc`——
> 命名为 `…OrderByCreatedAtDesc` 看起来完全合理，却会静默地把结果取反。
> 详见评审记录中的 P1-3。

-- 向量索引 (HNSW, V2)
CREATE INDEX idx_obs_embedding_768 ON mem_observations
    USING hnsw (embedding_768 vector_cosine_ops);
CREATE INDEX idx_obs_embedding_1024 ON mem_observations
    USING hnsw (embedding_1024 vector_cosine_ops);
CREATE INDEX idx_obs_embedding_1536 ON mem_observations
    USING hnsw (embedding_1536 vector_cosine_ops);

-- 全文搜索索引
CREATE INDEX idx_obs_search ON mem_observations USING GIN(search_vector);

-- 来源 + 项目组合索引 (V14 + V16: 替代单列 idx_obs_source)
CREATE INDEX idx_obs_project_source ON mem_observations (project_path, source);

-- extracted_data JSONB 查询索引 (V14)
CREATE INDEX idx_obs_extracted_data_gin ON mem_observations USING GIN (extracted_data jsonb_path_ops);

-- 摘要表 (V1 + V13, V18)
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
    created_at_epoch BIGINT NOT NULL,
    platform_source VARCHAR(50) DEFAULT 'claude'  -- V18: 多平台
);

-- 用户提示表 (V1 + V5, V18)
CREATE TABLE mem_user_prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_session_id VARCHAR(255) NOT NULL REFERENCES mem_sessions(content_session_id),
    prompt_number INT NOT NULL,
    prompt_text TEXT NOT NULL,
    project_path TEXT,               -- V5: 项目过滤
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at_epoch BIGINT NOT NULL,
    platform_source VARCHAR(50) DEFAULT 'claude'  -- V18: 多平台
);

-- 待处理消息表 (V1 + V3, V6)
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

-- 观察反馈表 (V17: Thompson Sampling)
CREATE TABLE observation_feedback (
    id BIGSERIAL PRIMARY KEY,
    observation_id UUID NOT NULL REFERENCES mem_observations(id) ON DELETE CASCADE,
    signal_type VARCHAR(50) NOT NULL,  -- 'semantic_inject'、'search_hit'、'explicit_retrieval'
    session_db_id UUID,
    created_at_epoch BIGINT NOT NULL,
    metadata TEXT
);

CREATE INDEX idx_feedback_observation ON observation_feedback(observation_id);
CREATE INDEX idx_feedback_signal ON observation_feedback(signal_type);
CREATE INDEX idx_feedback_session ON observation_feedback(session_db_id);

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

**Wire format。** `mem_observations` 的四个 JSONB 列（`facts`、`concepts`、`files_read`、
`files_modified`）返回的是 **JSON 编码的字符串**，不是 JSON 数组——
`facts: "[\"allergy\",\"peanut\"]"`。`ObservationEntity` 通过标注 `@JsonProperty`、
返回 `String` 的 getter 把它们暴露出去，供 TypeScript WebUI 调用 `JSON.parse`。
客户端必须先解码这个字符串，才能当作列表读取。

`refined_from_ids` 是例外：它是 `TEXT` 列而非 JSONB，返回的是**纯逗号分隔字符串**——
`"uuid-1,uuid-2,uuid-3"`。它唯一的写入方是 `ExtractionStorageService`，用
`Collectors.joining(",")` 构造，全程没有 JSON 这一层。

---

## 数据流

### 完整事件流程

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           完整事件流程                                         │
└──────────────────────────────────────────────────────────────────────────────┘

1. 会话开始
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Session     │────▶│ PostgreSQL  │
│  Hook       │     │ session-start│    │ Controller  │     │ Session     │
└─────────────┘     └─────────────┘     └─────────────┘     │ 已创建      │
                    < 200ms             异步                └─────────────┘

2. 工具使用 (观察)
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Claude Code │────▶│ wrapper.js  │────▶│ Ingestion   │
│ PostToolUse │     │ tool-use    │     │ Controller  │
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
│ SessionStart│     │ session-start│    │ Service     │     │ 向量搜索    │
│ (下一会话)  │     │ (响应中返回)│             │     │             │
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
│ SessionEnd  │     │ session-end │     │ Controller  │     │ Summary     │
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

> **本图已于第 262 轮（2026-10-04）对照源码更正。** 它与上文那份**本来就是正确的**
> [Hook 事件流程](#hook-事件流程) 表产生了分歧——**同一份文档自相矛盾**。四处修正，逐条核实：
>
> | 图中写的 | 实际 |
> |----------|------|
> | `session-start` → **Ingestion** Controller | → **Session** Controller（`SessionController.java:47,108`） |
> | PostToolUse → `wrapper.js observation` | → `wrapper.js tool-use` |
> | 上下文注入 → `wrapper.js context-get` | **该命令不存在。** `wrapper.js` 只有四个：`session-start` / `tool-use` / `session-end` / `user-prompt`。上下文是**随 `session-start` 的响应**回到调用方的——`proxy/` 下**没有任何地方调用 `/api/context/*`** |
> | SessionEnd → `wrapper.js summarize` | → `wrapper.js session-end` |
>
> `POST /api/ingest/session-start` 仍是 404；薄代理那份逐事件映射表才是代码的真实行为。

---

## API 层

### REST API

| 层 | 路径模式 | 描述 |
|---|----------|------|
| Ingestion | `/api/ingest/*` | Hook 事件接收（tool-use、user-prompt、observation、session-end） |
| Session | `/api/session/*` | 会话生命周期（start、get、patch user） |
| Viewer | `/api/observations`, `/api/summaries`, `/api/prompts`, `/api/projects`, `/api/stats?project=...`, `/api/search`, `/api/search/by-file`, `/api/observations/batch`, `/api/settings` (GET/POST), `/api/modes` (GET/POST), `/api/timeline`, `/api/processing-status`, `/api/sdk-sessions/batch` | WebUI 数据 (15 个方法)；`/api/stats` 接受可选的 `project` 查询参数以返回项目级统计 |
| Context | `/api/context/*` | 上下文检索（generate、inject、preview、prior-messages、recent、timeline、semantic）|
| Memory | `/api/memory/*` | 记忆操作（refine、experiences、icl-prompt、quality-distribution、feedback、patch/delete observation） |
| Mode | `/api/mode/*` | 记忆模式管理（get/put、types、concepts、validation） |
| Extraction | `/api/extraction/*` | 结构化数据提取（run、{templateName}/latest、{templateName}/history） |
| Cursor | `/api/cursor/*` | Cursor IDE 集成（register、check/unregister、context、projects） |
| Import | `/api/import/*` | 数据导入（bulk、sessions、observations、summaries、prompts） |
| Stream | `/stream` | SSE 实时更新 |
| Logs | `/api/logs` | 日志访问（get、clear） |
| Health | `/api/health`, `/api/readiness`, `/api/version` | 健康和版本检查 |

> **SSE 帧不携带事件名——这是一条承重契约。**
> `SSEBroadcaster.broadcast(Object data, String eventName)` 发送的是
> `SseEmitter.event().data(data)`，没有 `.name(...)`，因此发出的帧里**没有**
> `event:` 字段。`eventName` 参数只作文档用途；真正的路由键是数据载荷**内部**的
> `"type"` 字段，`SSEBroadcaster` 的 javadoc 明确写明了这一点。因此使用
> `addEventListener('new_summary', …)` 的浏览器客户端会**完全收不到任何东西**，
> 且没有任何报错——只有 `onmessage`（或裸的 `addEventListener('message', …)`）
> 才会触发。这与下文关于 JSONB 列的失败模式相同：形状猜错的客户端得到的是沉默，
> 而不是错误。
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
| **代理** | Node.js/axios | 轻量级、快速启动 |

### 使用的 Java 21+ 特性

```java
// Records 用于 DTO —— 真实代码：ApiRequests.ToolUseRequest（dto/ApiRequests.java）
// wire 名由 @JsonProperty 指定为 snake_case；全局的
// jackson.property-naming-strategy 并不作用于 record 组件。
public record ToolUseRequest(
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("cwd") String cwd,
    @JsonProperty("tool_name") String toolName,
    @JsonProperty("tool_input") Object toolInput,
    @JsonProperty("tool_response") Object toolResponse,
    @JsonProperty("source") String source,
    @JsonProperty("extractedData") Map<String, Object> extractedData,
    @JsonProperty("prompt_number") Integer promptNumber
) {}

// instanceof 模式匹配 —— 真实代码：dto/OffsetPageRequest.java:107-114
// （instanceof 那一行本身在 109）。此处逐字引用：真实的 equals 比较**四个**字段，
// 而早前的版本只写了两个，读起来会以为 offset 与 sort 未被比较。
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Pageable that)) return false;
    return page == that.getPageNumber()
        && size == that.getPageSize()
        && offset == that.getOffset()
        && sort.equals(that.getSort());
}

// 虚拟线程（Java 21）—— 真实代码：SummaryGenerationService:83、MemoryRefineService:91
@Async  // 跑在虚拟线程上；application.yml 中 spring.threads.virtual.enabled=true
public void generateSummaryAsync(...) { ... }

// 提示词是外部资源，而不是文本块：
//   src/main/resources/prompts/{init,observation,summary,continuation}.txt
// 运行时加载。唯一在 Java 内拼装的提示词
// （SummaryGenerationService:108）用的是字符串拼接加 "\n"。
```

> **JSONB 列在 wire 上不是 JSON 数组。** `mem_observations` 的 `facts`、`concepts`、
> `files_read`、`files_modified` 存储类型是 `List<String>`，但**序列化后是一个 JSON 编码的
> 字符串**——取一条 observation 得到的是 `facts: "[\"allergy\",\"peanut\"]"`，而不是
> `facts: ["allergy","peanut"]`。getter 标注了 `@JsonProperty("facts")` 且返回 `String`
> （`ObservationEntity.getFactsJson()`），因为 TypeScript WebUI 会调用
> `JSON.parse(observation.facts)`。这是**承重的契约而不是缺陷**：**把这些字段建模为列表的
> 客户端会静默地什么都收不到。** 参见[语义搜索](#语义搜索)下的 Wire format 说明。

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
    open-in-view: false  # 关闭 Open Session In View，仓储层即为边界

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
| `SPRING_PROFILES_ACTIVE` | `prd` | Spring profile（`dev`/`prd`） |
| `CLAUDEMEM_LLM_PROVIDER` | `openai` | `openai` 或 `anthropic` |
| `SPRING_AI_ANTHROPIC_API_KEY` | — | Anthropic API 密钥（当 provider=anthropic 时） |
| `SPRING_AI_ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | Anthropic 基础 URL |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | `claude-sonnet-4-5` | Anthropic 聊天模型 |
| `CLAUDE_MEM_MODE` | `code` | 记忆模式——见下方注记 |
| `MEMORY_REFINE_ENABLED` | `true` | 启用记忆优化（自我进化） |

> **上表的 LLM 与嵌入默认值是 `dev` profile 的，而 `SPRING_PROFILES_ACTIVE` 默认却是
> `prd`。** 两者并不一致：`prd` 使用 `https://api.openai.com` 配 `gpt-4o`，以及
> `text-embedding-3-small` + 1536 维；而此处显示的是 `https://api.deepseek.com` /
> `deepseek-chat` 与 SiliconFlow `BAAI/bge-m3` + 1024 维。由于 Compose 选中的正是 `prd`，
> 只设置 API 密钥的部署**不会**得到本表中的取值。完整对照见部署指南 §5.4。
>
> **并不存在 `default` 模式。** 本表早前版本把 `code`/`default` 列为两个可选值，但只有
> `code` 是真实的模式名。实际发布的 32 个 profile 是 `code`、`code--<语言>`
> （30 个变体，含 `code--chill`）与 `email-investigation`。设置
> `CLAUDE_MEM_MODE=default` **不会**大声报错：`ModeService` 会去解析
> `<modes-dir>/default.json`，找不到后捕获异常，回退到一份**内嵌的 `code` 模式副本**
> ——同样的 6 种观测类型与 7 个概念——并以 **WARN** 级别记录
> `Failed to load mode 'default', using embedded default`。因此实际影响并不严重
> （拿到的基本就是 `code` 的效果，外加一条告警），但该取值并非受支持项：请直接用 `code`。
>
> 另需注意：**完全不设 profile** 时（即按项目自身构建说明执行 `java -jar app.jar`
> 而未设 `SPRING_PROFILES_ACTIVE`），`application-dev.yml` 与 `application-prd.yml`
> **都不会被加载**。本表中的 LLM 与嵌入取值此时仍然成立，因为 `SpringAiConfig`
> 自带 `@Value` 兜底、恰好镜像了 `dev`；但 `app.memory.extraction.enabled`
> 会落到基础配置的 `false`，而不是 `dev` 的 `true`。

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
embedding_model_id VARCHAR(255)
```

**理由**：
- 切换模型的灵活性
- 无数据丢失的迁移路径
- 查询的模型跟踪

**决策的适用范围——仅限写入侧。** 三个列按维度写入：`AgentService` 依据向量长度
switch，分别存入 `embedding_768` / `embedding_1024` / `embedding_1536`。但读取侧
并非如此：`ObservationRepository.hybridSearch` 始终与 `embedding_1024` 比较，而
`SearchService` 算出查询向量维度后只用于打印日志——它那句 "dimension-aware" 注释
描述的是代码并未实现的意图。`semanticSearch768` / `semanticSearch1024` /
`semanticSearch1536` 三个方法带有正确的分维度 SQL，但没有任何调用方。

实际后果是：只有 1024 维能让语义检索端到端工作。若把
`SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` 改为 768 维或 1536 维模型，写入依旧正确，
但每次语义查询都会因 `different vector dimensions <n> and 1024` 失败并被捕获，
退化为全文检索。该降级是可见的而非静默的：响应会返回 `strategy: "tsvector"` 与
`fellBack: true`，同时记录一条 WARN 日志。

**默认拿到哪个维度取决于 profile。** `dev` profile 默认 `BAAI/bge-m3` + 1024 维，
开箱即用。`prd` profile——`docker compose up` 选中的正是它，因为
`SPRING_PROFILES_ACTIVE` 默认 `prd`——默认 `text-embedding-3-small` + **1536** 维，
因此保持默认的 Compose 部署会写入 `embedding_1536` 并全程跑在全文检索上。
若依赖 `prd`，请显式设置 `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024`。
完整的按 profile 对照表见部署指南 §5.4。

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

**以下为估算值，不是实测值。** 后端既不强制、也不上报其中任何一项上限，
本仓库也没有任何基准测试产出这些数字。它们是规划用的参考值，保留在此是为了
给下方的扩展策略提供讨论起点，而不是可以据此做设计的既有特性。

| 资源 | 估算上限 | 实际生效的约束 |
|------|----------|----------------|
| 每个项目的观察数 | ~100 万（估算） | 无上限。单项目只受磁盘容量约束 |
| 并发会话 | ~100（估算） | **不存在并发限制。** 唯一的限流器（`RateLimitService`）是按 key 的请求频率上限 **10 次 / 60 秒**，而它唯一的调用点是 `IngestionController` 对 `POST /api/ingest/tool-use` 的保护，key 为 `tool-use:{contentSessionId}`。它限制的是**该单个端点的写入频率**，既不统计也不约束并发会话数 |
| 向量搜索延迟 | ~100ms（估算） | 无 SLO。实测值取决于嵌入维度、HNSW 参数与返回条数，这些在本仓库中均未固定 |

这个区分之所以重要：原表把「速率限制」列为并发会话一行的缓解措施，
但该措施针对的是另一个问题——它限制单个会话上报工具调用的频率，
而不是同时打开多少个会话。

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
   // 未实现 —— 仅为示意。后端不存在任何 @Cacheable 注解。
   // 已上线的最接近之物是 ContextCacheService：它保存的是**预渲染的上下文文本**，
   // 按固定频率刷新（claudemem.cache.refresh-interval-seconds，默认 60s），
   // 既不是查询缓存，也不位于 search() 之前。
   @Cacheable("observations")
   public List<Observation> search(String query) { ... }
   ```

4. **数据库分区**
   ```sql
   -- 未实现 —— 仅为示意。schema 中根本不存在 project_path_hash 列，
   -- 这段草稿凭空造了一个。另外，按高基数键（project_path）做 LIST 分区
   -- 会产生近乎「每项目一个分区」，而本库的项目数已达数千。
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

隐私标签剥离发生在代理层（`proxy/tag-stripping.js`），内容在转发到后端之前即被处理 —— `<private>…</private>`、`<claude-mem-context>`、`<system_instruction>` 与 `<system-instruction>` 块会被**整段移除**（而非替换为占位符），完全私有的提示词则被跳过。

### 网络安全

| 组件 | 绑定 | 访问 |
|------|------|------|
| 胖服务器 | 127.0.0.1:37777 | 仅本地 |
| PostgreSQL | 127.0.0.1:5433（Docker）或 :5432（原生） | 仅本地 |
| 代理 | 不适用 (CLI) | 无网络 |

端口取决于 PostgreSQL 的运行方式，两个值都正确：

- **Docker Compose** 把容器的 `5432` 发布到宿主机的 **`5433`**
  （`docker-compose.yml` 中的 `"${POSTGRES_PORT:-5433}:5432"`），正是为了不和已占用
  5432 的本地 PostgreSQL 冲突。
- **本地原生 PostgreSQL** 监听 `5432`，这正是后端默认 JDBC URL 所指向的：
  `jdbc:postgresql://127.0.0.1/claude_mem_dev`。

因此走 Docker 路径时需要用 `SPRING_DATASOURCE_URL` 指向发布出来的端口
（`jdbc:postgresql://127.0.0.1:5433/claude_mem`），否则后端默认的 5432 会去找一个
并不存在的服务器。

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
