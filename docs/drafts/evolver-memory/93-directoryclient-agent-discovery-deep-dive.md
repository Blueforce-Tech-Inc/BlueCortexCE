# `93` directoryClient.js — Agent Capability Directory API 深度分析

**模块**: `src/gep/directoryClient.js` (110行)
**定位**: Hub Ecosystem（doc #46）的核心子模块，负责 Agent 能力目录的语义/关键词搜索与 Profile 获取
**版本**: v1.47 / 本地工作树

---

## 1. 模块定位与职责

`directoryClient.js` 是 EvoMap Hub Ecosystem 的**外部 Agent 发现层**，为本地 Agent 提供从远程 Hub 目录发现合适协作 Agent 的能力。其核心价值在于：

- **语义搜索**：通过自然语言查询在 Hub 目录中搜索 Agent
- **信号搜索**：通过结构化关键词（signals）在 Hub 目录中搜索 Agent
- **Profile 获取**：获取特定 Agent 的详细能力档案
- **任务驱动发现**：结合任务标题和信号综合发现 Agent

## 2. API 端点

| 函数 | Hub 端点 | 方法 | 用途 |
|------|---------|------|------|
| `searchByQuery` | `/a2a/directory/search?q=` | GET | 自然语言语义搜索 |
| `searchBySignals` | `/a2a/directory/search?signals=` | GET | 关键词/信号搜索 |
| `getAgentProfile` | `/a2a/directory/profile/{nodeId}` | GET | 获取单个 Agent 档案 |
| `discoverForTask` | 组合上述 | — | 任务驱动的 Agent 发现 |

## 3. 核心函数分析

### 3.1 `searchByQuery(query, opts)`

通过自然语言查询在 Hub Agent 目录中做语义搜索。

```javascript
// URL: https://evomap.ai/a2a/directory/search?q=<query>&limit=<limit>
// Headers: buildHubHeaders()（来自 a2aProtocol.js）
// Timeout: 8000ms
// Returns: [{ nodeId, score, domains, reputation }, ...] | null
```

**特点**：
- 裸 `fetch` 调用，无重试机制
- 8 秒硬超时（`AbortSignal.timeout`）
- 非 2xx 响应静默返回 `null`（warn 日志）
- 支持 `limit` 参数控制返回数量

### 3.2 `searchBySignals(signals, opts)`

通过信号关键词数组搜索 Agent，比语义搜索更精确。

```javascript
// URL: https://evomap.ai/a2a/directory/search?signals=ml,nlp,python&limit=<limit>
// signals 参数以逗号拼接
```

**特点**：
- 输入校验：`!Array.isArray(signals) || signals.length === 0` → `null`
- 与 `searchByQuery` 共用同一个 Hub 端点，差异化在于 query param

### 3.3 `getAgentProfile(nodeId)`

获取指定 Agent 的详细能力档案。

```javascript
// URL: https://evomap.ai/a2a/directory/profile/<nodeId>
// Returns: { nodeId, domains, modelType, reputation, completedTasks, currentLoad, online }
```

**返回字段**：
| 字段 | 类型 | 含义 |
|------|------|------|
| `nodeId` | string | Agent 唯一标识 |
| `domains` | string[] | 能力领域标签 |
| `modelType` | string | 底层模型类型 |
| `reputation` | number | Hub 声誉分数 |
| `completedTasks` | number | 已完成任务数 |
| `currentLoad` | number | 当前负载（0–1） |
| `online` | boolean | 是否在线 |

### 3.4 `discoverForTask(task, opts)`

任务驱动的 Agent 发现入口，智能组合语义和信号搜索。

```javascript
// 优先使用自然语言查询（title）
// fallback 到信号搜索（signals 数组）
// task.signals 以逗号分割 → 数组
```

**发现策略**：
```
if (task.title exists) → searchByQuery(task.title)
else if (task.signals exists) → searchBySignals(signals)
else → null
```

## 4. 错误处理模型

```
directoryClient 错误处理 = 静默降级 + warn 日志
```

- 所有 Hub API 调用失败 → `null` + `console.warn`
- **无重试机制**：单次 fetch，失败即放弃
- **无熔断降级**：Hub 不可用时静默影响 Agent 发现
- **无本地缓存**：每次调用都是实时网络请求

## 5. 与 taskReceiver.js 的关系

```
directoryClient          → Hub Agent Directory（远程）
taskReceiver.js          → Hub Task Market（远程）
hubSearch.js             → Hub Asset Market（远程 Gene/Capsule 搜索）
```

三者是 Hub Ecosystem 的三个外部发现接口：
- `directoryClient`：发现"谁"能完成任务（Agent 发现）
- `taskReceiver`：发现"什么"任务可做（Task Market）
- `hubSearch`：发现"什么"知识可复用（Asset Market）

## 6. 在 Hub Ecosystem 中的集成位置

根据 doc 46，Hub Ecosystem 的集成架构：

```
Local Agent
    ├── taskReceiver.js ──→ Hub Task Market (/a2a/tasks/*)
    ├── hubSearch.js ─────→ Hub Asset Market (/a2a/assets/*)
    └── directoryClient.js → Hub Agent Directory (/a2a/directory/*)  ← 本模块
              ↑
              发现协作 Agent（用于 a2a P2P 协作）
```

`directoryClient` 主要服务于 doc 78 提到的 SessionHandler P2P 协作（`create/join/leave/delegate subtask`），在委托子任务前需要先发现合适的远程 Agent。

## 7. BlueCortexCE 借鉴分析

### P3 — 可选实验

| 借鉴点 | 说明 | 优先级 |
|--------|------|--------|
| Agent Profile 获取 | CE 目前无多 Agent 协作场景，暂无借鉴价值 | P3 |
| 信号搜索 | CE 的 SearchService 支持结构化过滤，与 signals 搜索理念相似 | P2 |
| 静默降级模式 | Hub API 失败不影响主流程，与 CE 的 fallback 策略一致 | P2 |

### 关键差异

- **Hub 是集中式目录**：EvoMap 的 Hub 提供全局 Agent 目录服务
- **CE 是本地记忆系统**：BlueCortexCE 不需要发现远程 Agent，只服务本地
- **架构性质不同**：directoryClient 解决"网络中找谁"，CE 解决"本地记忆怎么用"

## 8. 未覆盖的边界

| 边界 | 状态 |
|------|------|
| Hub Directory API 实际响应 schema | 无 mock/测试数据 |
| Hub 侧的 agent profile 更新机制 | 纯黑盒，仅客户端侧 |
| `buildHubHeaders()` 实际内容 | 来自 a2aProtocol.js（doc 35） |

## 9. 源码结构摘要

```
directoryClient.js (110L)
├── searchByQuery(query, opts)        — 自然语言语义搜索
├── searchBySignals(signals, opts)     — 关键词/信号搜索
├── getAgentProfile(nodeId)            — 单 Agent 档案获取
└── discoverForTask(task, opts)        — 任务驱动组合发现
```

**无内部状态**：纯函数式 API client，所有状态来自 Hub 远程。

---

**CE 行动项**：无直接行动项。CE 无多 Agent 协作场景，directoryClient 的设计思想（外部服务发现 + 静默降级）在 CE 的 MCP 工具层有间接参考价值。
