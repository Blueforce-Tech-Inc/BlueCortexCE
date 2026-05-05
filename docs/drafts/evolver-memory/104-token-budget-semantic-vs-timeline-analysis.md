# `104` Token Budget 分析：语义注入 vs 时间线并存的预算竞争

**分析目标**：澄清 BlueCortexCE 当前 timeline context（`generateContext`）和 semantic context（`additionalContext`）并用时的 token 预算协调问题，对照 Evolver `EXEC_CONTEXT_CAP=20000` 分层保护机制，提炼 CE 的改进方案。

**数据来源**：
- `webui/src/cli/handlers/session-init.ts` — UserPromptSubmit hook，`additionalContext` 注入路径
- `backend/.../ContextService.java` — `generateContext` timeline 生成
- `backend/.../ContextController.java` — `/api/context/semantic` 端点
- `webui/src/services/worker/http/routes/SearchRoutes.ts` — Worker 层语义搜索
- `docs/drafts/evolver-memory/92-prompt-js-schema-enforcement-and-token-budget.md` — Evolver EXEC_CONTEXT_CAP

**最后更新**：2026-05-05

---

## 1. 当前架构：两条独立注入路径

```
┌──────────────────────────────────────────────────────────────┐
│  UserPromptSubmit hook (session-init.ts)                      │
│                                                               │
│  ① SDK Agent Init:                                           │
│     POST /sessions/{id}/init                                 │
│     → AgentService.sessionsInit()                             │
│     → ContextService.generateContext()  ← Timeline context    │
│     → 观察 + 摘要 + Prior Messages（时间顺序）                │
│     → 成为 SDK Agent 的 system/user context                  │
│                                                               │
│  ② Semantic Injection:                                        │
│     POST /api/context/semantic { q: prompt, limit: 5 }       │
│     → SearchManager.search() → ChromaDB                       │
│     → 返回 "## Relevant Past Work (semantic match)\n..."     │
│     → additionalContext (hookSpecificOutput)                   │
│     → 随 UserPromptSubmit 返回给 Claude Code                  │
└──────────────────────────────────────────────────────────────┘

最终：LLM 看到  (Timeline context from ①)  +  (Semantic context from ②)
       ↓                                    ↓
       基于会话历史                    基于本次 prompt 检索
       时间顺序排列                    语义相似度排序
       ObservationEntity              ChromaDB 命中
```

**关键事实**：
- `CLAUDE_MEM_SEMANTIC_INJECT` 默认 **关闭**（`SettingsDefaultsManager.ts` L134: `'false'`）
- 开启时，每个 prompt 都触发两次独立调用（SDK init + semantic search）
- 两次调用的结果在 LLM 侧拼接，**没有任何预算协调**

---

## 2. 两条路径的预算特征

| 维度 | Timeline Context (`generateContext`) | Semantic Context (`additionalContext`) |
|------|---------------------------------------|--------------------------------------|
| **数据源** | `ObservationEntity` + `SummaryEntity` + `PriorMessages` | ChromaDB（Worker SQLite 同步） |
| **排序方式** | 时间倒序（recent first） | 向量相似度 |
| **数量控制** | `config.totalObservationCount`（默认？） | `CLAUDE_MEM_SEMANTIC_INJECT_LIMIT`（默认 5） |
| **Token 估算** | `TokenService.calculateEconomics()` 仅用于渲染 footer 统计 | **无** token 估算 |
| **截断机制** | 观察内容内部 `truncate(s, 100/200/500)` | 上限 20 条（SearchRoutes.ts 硬截断） |
| **Header/Footer 保护** | 有（Timeline header/footer） | **无** |
| **关闭开关** | `ContextConfig` 可配置各段数量 | `CLAUDE_MEM_SEMANTIC_INJECT` 全局开关 |

---

## 3. 核心问题：双注入时的预算竞争

### 3.1 问题描述

当 `CLAUDE_MEM_SEMANTIC_INJECT=true` 时，同一个 LLM prompt 会接收到：

1. **Timeline context** — 大段历史记录（可配置到数十条 observation + 多个 summary + prior messages）
2. **Semantic context** — `## Relevant Past Work` 块（每次 query 返回，最多 5 条）

两者**在 LLM 侧拼接**，没有：
- 共享的字符上限（EXEC_CONTEXT_CAP）
- 动态剩余空间计算
- 优先级保护（header/footer 优先）

### 3.2 具体场景

```
场景：用户在一个有 200 条观察记录的项目中提问

Timeline context（generateContext）：
  ## Session Timeline
  - [Observation] bugfix: 修复了 X 登录问题
  - [Observation] feature: 添加了 Y 功能
  - [Summary] 完成了 Z 模块重构
  ...（20 条 observations + 5 summaries）≈ 8000 chars

Semantic context（additionalContext）：
  ## Relevant Past Work (semantic match)
  - 2个月前修复了类似登录问题 (relevance=0.91)
  - 3周前添加过Y功能的相关探索 (relevance=0.87)
  ...（5 条）≈ 1500 chars

合并后：≈ 9500 chars → 可能超过 LLM context 预算
```

**没有机制**知道"Timeline 已经占用了 8000 chars，Semantic 注入应该降级或跳过"。

### 3.3 对照 Evolver 的分层保护

```
Evolver prompt.js 架构：

┌─────────────────────────────────────────────┐
│  GEP Prompt  (< EXEC_CONTEXT_CAP = 20000)   │
│                                             │
│  [Header] ← PROTECTED: 基因块 schema 头      │
│  [Strategy Block] ← PROTECTED: 策略指令     │
│  [Mutation] ← PROTECTED: 变异指令            │
│                                             │
│  ── dynamic remaining space ────────────── │
│                                             │
│  [Execution Context] ← 动态截断区域          │
│   - Narrative Memory                         │
│   - Execution Trace                          │
│   - 剩余空间不足时优先截断此区               │
│                                             │
│  [Footer] ← PROTECTED: 基因 schema 尾       │
│  [Constitutional Constraints] ← PROTECTED   │
└─────────────────────────────────────────────┘
```

**CE 当前缺失的分层保护**：
1. Semantic context **无** `remaining space` 感知
2. Timeline 和 Semantic 都作为"内容块"追加，无优先级
3. 无统一的 `EXEC_CONTEXT_CAP` 约束

---

## 4. 现有相关代码锚点

### 4.1 Timeline context 生成

```java
// ContextService.generateContext()
List<ObservationEntity> observations =
    observationRepository.findByTypeAndConcepts(
        projectPath, types, concepts, conceptsEmpty,
        config.getTotalObservationCount()  // ← 数量硬上限
    );

String timelineContext = renderTimeline(project, timeline, observations, config);
// 输出示例：
// ## Session Timeline
// [2026-05-05] 🔧 bugfix: 修复了 X 问题
// [2026-05-04] 📝 decision: 采用 Y 方案
// ...
```

**截断方式**：观察内容内部 `truncate(s, 100/200/500)`，但**整体输出无字符上限**。

### 4.2 Semantic context 生成

```typescript
// SearchRoutes.ts handleSemanticContext()
const limit = Math.min(Math.max(parseInt(String(req.body?.limit || req.query.limit || '5'), 10) || 5, 1), 20);

const result = await this.searchManager.search({
  query, type: 'observations', project, limit: String(limit), format: 'json'
});

// 返回格式：
// "## Relevant Past Work (semantic match)\n"
// "- [date] icon type: content..."  （每条 truncate 为固定格式）
```

**无 token 估算**：不调用 `TokenService`，直接返回字符串。

### 4.3 TokenService 的实际使用范围

```java
// ContextService.java L558
TokenService.TokenEconomics economics = tokenService.calculateEconomics(observations);

// 仅用于渲染 footer（readonly 统计）：
// 📊 50 observations | 📖 10,000 read tokens | 💰 77,072 saved (89%)
```

**用途**：仅为用户展示 token 节省统计，**不影响 context 生成逻辑**。

---

## 5. BlueCortexCE 改进方案

### 5.1 分层 Token Budget（参照 Evolver）

| Layer | 内容 | 保护级别 | CE 当前 |
|-------|------|----------|---------|
| Header | Timeline 标题块 | **P0** 固定 | ✅ 有 |
| Core | Observation 内容（时间顺序） | **P1** 动态截断 | ⚠️ 有数量，无字符上限 |
| Footer | Token 统计 / Summary 块 | **P0** 固定 | ✅ 有 |
| Semantic | `## Relevant Past Work` 块 | **P2** 条件注入 | ❌ 无预算感知 |

### 5.2 具体行动项

#### P1（推荐尽快实现）

**方案 A：共享字符上限 + Semantic 条件跳过**

```java
// ContextController.java 或 SessionInitHandler
public class TokenBudgetManager {
    static final int TOTAL_CONTEXT_CAP = 15000;  // 保守估计，留 3000 给 prompt
    static final int SEMANTIC_RESERVE = 2000;    // Semantic 最多用 2000 chars

    public String combineWithBudget(String timeline, String semantic) {
        int remaining = TOTAL_CONTEXT_CAP - estimateTokenChars(timeline);
        if (remaining < SEMANTIC_RESERVE) {
            // Semantic 注入会挤压 timeline，降级或跳过
            log.debug("Skipping semantic injection: insufficient budget (remaining={})", remaining);
            return timeline;
        }
        // Semantic 在 reserve 内安全注入
        return timeline + "\n\n" + semantic;
    }
}
```

**方案 B：Semantic 独立预算（更轻量）**

```typescript
// session-init.ts
const SEMANTIC_MAX_CHARS = 2000;  // 独立上限，与 timeline 分开计数

if (semanticResult.context.length > SEMANTIC_MAX_CHARS) {
  semanticResult.context = semanticResult.context.substring(0, SEMANTIC_MAX_CHARS) + '\n... [semantic truncated]';
}
```

#### P2（中期）

**双层截断区域**（参照 Evolver）：
- `EXEC_CONTEXT_CAP = 15000` 作为硬上限
- Header/Footer 固定输出
- Execution Context 区域（Timeline + Semantic）动态分配剩余空间
- Semantic 结果按 relevance 排序，超出空间从底部截断

#### P3（可选）

**统一 TokenBudgetService**：
- 所有 context 生成路径（timeline、semantic、ICL）都经过同一个 budget manager
- 支持 `additionalContext` 与主 context 的动态比例调整
- 与 Evolver `prompt.js` L108–L117 的 `allowedExecutionLength` 对齐

---

## 6. 与 Research Backlog 的关联

本文档对应 backlog 条目：
> **语义注入与时间线并存的 token 预算**：`additionalContext` 与主上下文拼接策略、关闭开关与延迟预算。

**现状确认**：
- ✅ `CLAUDE_MEM_SEMANTIC_INJECT` 关闭开关存在（默认 false）
- ❌ 两条注入路径**无预算协调**
- ❌ Semantic context **无** token 上限
- ❌ Timeline context **无** 全局字符上限（只有数量上限）

**已覆盖**：
- Evolver token budget 机制：doc 92 §3（EXEC_CONTEXT_CAP=20000 / 分层截断 / Header/Footer 保护）
- Dual-stack semantic architecture（两栈独立无双写）：doc 86

---

## 7. 下一步

1. **P1 实施**：在 `SessionInitHandler` 或 `ContextController` 引入 `SEMANTIC_MAX_CHARS = 2000` 独立上限（方案 B，最小改动）
2. **P1 实施**：在 `ContextService.generateContext()` 引入 `MAX_CONTEXT_CHARS = 12000` 硬上限
3. **P2 设计**：统一 `TokenBudgetManager`，支持 `remaining space` 动态计算
4. **评测**：实际测量 20 条 observation timeline + 5 条 semantic 的字符数分布

---

## Changelog

- **2026-05-05** (`104`) 初稿完成：分析 session-init.ts 双路径注入 / ContextService timeline / SearchRoutes semantic / TokenService 仅用于 footer 统计 / 参照 Evolver EXEC_CONTEXT_CAP 提出 P1/P2/P3 行动项
