# `86` 双栈语义架构：Worker（Chroma） vs Java（pgvector）

**分析目标**：澄清 Worker 层和 Java 层的语义搜索是否共享同一向量存储，理解数据一致性问题。  
**数据来源**：`webui/src/services/sync/ChromaSync.ts`（470行）、`webui/src/services/sync/ChromaMcpManager.ts`、`backend/src/.../ObservationRepository.java`、`SearchRoutes.ts`（Worker `/api/context/semantic`）。  
**最后更新**：2026-05-04

---

## 1. 架构全景：两套独立的向量系统

```
┌─────────────────────────────────────────────────────────────┐
│  Hook (session-init.ts)                                     │
│  POST /api/context/semantic  ──────────────────────────────┐ │
└────────────────────────────────────────────────────────────│ │
                                                             ▼ │
┌─────────────────────────────────────────────────────────────┐
│  Worker (Bun)  port=37777                                  │
│  SearchRoutes.handleSemanticContext()                       │
│  → SearchManager.search() → ChromaSync.queryChroma()        │
│                                                             │
│  ChromaSync ──MCP (stdio)──► chroma-mcp server             │
│                                     │                       │
│  SessionStore (SQLite) ◄── read ────┘                       │
│       │                                                      │
│       └── write observations/summaries/prompts               │
│       │                                                      │
│       └── ChromaSync.ensureBackfilled() ──sync──► ChromaDB  │
│                                                             │
│  ChromaDB collection: cm__{project}                         │
│  每条 observation 分成多个文档（narrative/text/fact/...）   │
│  每字段一个向量，独立检索                                     │
└─────────────────────────────────────────────────────────────┘

                    ┌──────────────────── (无自动同步) ──────────

┌─────────────────────────────────────────────────────────────┐
│  Java Backend (Spring Boot)  port=37777                     │
│  ContextController.POST /api/context/semantic                │
│  → EmbeddingService → ObservationRepository.semanticSearch*  │
│                                                             │
│  PostgreSQL + pgvector                                       │
│  embedding_768 / embedding_1024 / embedding_1536             │
│  (三维 embedding 字段，按模型选择)                           │
│                                                             │
│  数据来源：AgentService → ObservationEntity (JDBC)         │
└─────────────────────────────────────────────────────────────┘
```

**核心结论**：Worker（Chroma）和 Java（pgvector）是**两套独立的向量系统**，无自动双写。SQLite 是两者唯一的共同数据源。

---

## 2. Worker 栈：ChromaDB via MCP

### 2.1 ChromaSync 职责

`ChromaSync.ts`（470行）将 SQLite 数据同步到 ChromaDB：

| 方法 | 作用 |
|------|------|
| `syncObservation()` | 单条 observation → Chroma（分字段 doc） |
| `syncSummary()` | 单条 session summary → Chroma（分字段 doc） |
| `syncUserPrompt()` | 单条 user prompt → Chroma（整条 doc） |
| `ensureBackfilled()` | 增量回填：比对 sqlite_id，已存在则跳过 |
| `queryChroma()` | 语义检索，返回 dedup by sqlite_id |

### 2.2 Chroma 集合命名与分文档策略

```
Collection name: cm__{project}   (如 cm__claude-mem)

每条 observation 分成多个独立向量文档：
  obs_{id}_narrative     → narrative 文本
  obs_{id}_text          → text 文本（legacy）
  obs_{id}_fact_0        → 第0条 fact
  obs_{id}_fact_1        → 第1条 fact
  ...

每条 summary 分成多个独立向量文档：
  summary_{id}_request    → request 字段
  summary_{id}_investigated → investigated 字段
  summary_{id}_learned    → learned 字段
  summary_{id}_completed  → completed 字段
  summary_{id}_next_steps → next_steps 字段
  summary_{id}_notes      → notes 字段

queryChroma() 返回时做 dedup：同一 sqlite_id 只保留排名最高的 doc
```

### 2.3 ChromaMcpManager 单例与 #761 修复

`ChromaMcpManager` 是 chroma-mcp 进程的单一管理器（singleton），解决 **Issue #761**：

- **Root cause**：连接错误时，代码重置了 `connected` 和 `client` 但没有关闭 `transport`，导致 chroma-mcp 子进程变成 zombie
- **Fix**：错误处理链路上调用 `this.transport.close()`，`this.connected = false`，`this.transport = null`
- **验证**：测试文件 `chroma-vector-sync.test.ts` 包含回归测试验证这三步

### 2.4 智能增量回填 `ensureBackfilled()`

```
1. getExistingChromaIds()  ──批量──► ChromaDB metadata only（快）
   返回 {observations: Set<id>, summaries: Set<id>, prompts: Set<id>}

2. SQLite WHERE id NOT IN (existing)  ──只取缺失的──►

3. 分字段格式化 → ChromaDoc[]

4. 批量 addDocuments(BATCH_SIZE=100)
   若遇 "already exist" 错误 → delete+add 原子补救
```

---

## 3. Java 栈：pgvector 语义搜索

### 3.1 三维 embedding 字段

```sql
-- ObservationRepository.java 三条语义查询：

semanticSearch768()    -- embedding_768 字段（M5 model）
semanticSearch1024()   -- embedding_1024 字段（bge-m3 default）
semanticSearch1536()   -- embedding_1536 字段（其他模型）
```

### 3.2 语义注入路径（Java）

```
Hook → POST /api/context/semantic (Worker)
       ↓
SearchRoutes.handleSemanticContext()
       ↓
SearchManager.search({type: 'observations', format: 'json'})
       ↓
ChromaSync.queryChroma() → ChromaDB

（不是 Java pgvector！）
```

> ⚠️ **注意**：`/api/context/semantic` 是 Worker 端点，调用 ChromaDB。Java 的 pgvector 路径是 `ContextController.POST /api/context/semantic`（同名路由但不同服务器）。

---

## 4. 一致性分析：当前状态

### 4.1 数据写入路径

| 操作 | Worker 写入 | Java 写入 | 共同数据源 |
|------|------------|----------|-----------|
| 新 observation | SQLite（SessionStore）→ ChromaDB（async） | PostgreSQL（AgentService） | 无直接同步 |
| 新 summary | SQLite → ChromaDB（async） | PostgreSQL | 无直接同步 |
| 新 user prompt | SQLite → ChromaDB（async） | 不写入 | 无 |

### 4.2 语义查询路径

| 端点 | 服务器 | 向量引擎 | 数据来源 |
|------|--------|---------|---------|
| Worker `/api/context/semantic` | Bun Worker | ChromaDB | SQLite |
| Java `/api/context/semantic` | Java Spring | pgvector | PostgreSQL |
| Worker `/api/search` | Bun Worker | ChromaDB | SQLite |
| Java MCP `search` | Java Spring | pgvector | PostgreSQL |

### 4.3 关键风险：语义结果可能不同

由于两栈独立，以下情况会导致语义检索结果差异：

1. **维度不匹配**：Chroma 返回的向量可能与 pgvector 使用不同的 embedding 模型（Worker 用 chroma-mcp 内嵌 embedding；Java 用 Spring AI `EmbeddingModel`）
2. **去重逻辑不同**：Chroma query 做 sqlite_id dedup（取排名最高的字段 doc）；Java pgvector 基于完整 observation entity 检索
3. **更新/删除不同步**：修改或删除 observation 时，ChromaDB 和 pgvector 可能不一致
4. **时间窗口差异**：`ensureBackfilled()` 异步执行，可能存在 ChromaDB 滞后

---

## 5. Research Backlog 结论

### ✅ 已完成：Java（pgvector）与 Worker（Chroma）语义结果一致性

**源码核实完成**（2026-05-04）：

1. **两栈完全独立**：Worker 用 ChromaDB（chroma-mcp MCP server），Java 用 pgvector，无代码级自动双写
2. **共同数据源**：SQLite（Worker 端）和 PostgreSQL（Java 端）各自从原始会话事件写入，理论上数据同源（若写入链路相同）
3. **存储一致 ≠ 语义一致**：两栈各自的 embedding 模型、向量维度、去重策略不同，语义检索结果可能不同
4. **SQLite 不是两栈共同存储**：Worker 用 SQLite 存储 observations，Java 用 PostgreSQL，两者没有自动同步机制

**结论**：语义结果一致性**无保障**，需要在产品层面设计跨栈同步协议或统一向量存储后端。

### CE P1 行动项

| 优先级 | 行动项 |
|--------|--------|
| **P0** | 确认 CE 产品层面需要哪个向量系统作为"事实来源"（当前建议：Java pgvector 为持久化层，Worker Chroma 为可选缓存） |
| **P1** | 若需 Worker 语义检索与 Java 一致：设计跨栈同步协议（Worker 写入时同步到 Java，或反过来） |
| **P2** | 若允许不一致：明确文档告知用户两栈检索结果可能不同 |
