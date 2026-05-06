# `narrativeMemory.js` — Markdown 双限制叙事记忆深度

**Doc**: 123  
**Cron**: 2026-05-07 00:10  
**源码**: `src/gep/narrativeMemory.js` (108L, pure JS, no external deps)  
**相关**: Doc 82 (§Narrative Memory + Ops Supporting Modules), Doc 50 (MemoryGraph 闭环), Doc 105 (核心记忆架构)

---

## 1. 源码结构（108L）

```javascript
// Dependencies: fs, path, ./paths (getNarrativePath, getEvolutionDir)
const MAX_NARRATIVE_ENTRIES = 30;   // entry count limit
const MAX_NARRATIVE_SIZE  = 12000;   // char limit (12KB)
const MAX_RECENT_ENTRIES = 8;       // loadNarrativeSummary returns last 8
```

### 1.1 `recordNarrative({ gene, signals, mutation, outcome, blast, capsule })`

单次叙事记录，构建 Markdown entry 并追加：

```
### [2026-05-07 00:10] REPAIR - success
- Gene: fix_timeout_handler | Score: 0.82 | Scope: 2 files, 45 lines
- Signals: [error_timeout, recurring_errsig(3x): connection reset]
- Why: Timeout handler race condition — added circuit breaker
- Strategy:
  1. Detect: error pattern matching
  2. Isolate: fail-fast on timeout
- Result: Capsule summary here...
```

**Entry 字段**：

| 字段 | 来源 | 截断 |
|------|------|------|
| timestamp | `new Date().toISOString()` | `T`→` `, 19char |
| category | mutation.category OR gene.category OR `unknown` | — |
| status | outcome.status OR `unknown` | — |
| score | outcome.score.toFixed(2) OR `?` | — |
| files/lines | blast.files / blast.lines | — |
| signalsSummary | signals[0:4] comma-joined | — |
| rationale | mutation.rationale | 200chars |
| strategy | gene.strategy[0:3] enumerated | — |
| capsuleSummary | capsule.summary | 200chars |

**写入流程**：

```
read existing → prepend header if empty
→ append entry → trimNarrative (dual: count + size)
→ write tmp → rename atomic
```

### 1.2 `trimNarrative(content)` — 双重裁剪

```
Phase 1: while entries > 30  → shift oldest entries
Phase 2: if total > 12KB     → keep last (entries-5) entries
Fallback: if still > 12KB    → last 12KB of content (lose header alignment)
```

### 1.3 `loadNarrativeSummary(maxChars=4000)` — 读取

- Returns last **8 entries** OR up to `maxChars` chars
- Aligns to first `### [` in truncated result (avoids mid-entry cut)
- Returns empty string on error or if no valid entries

---

## 2. 设计特点

### 2.1 双限制裁剪（Count + Size）

| 维度 | 上限 | 触发 |
|------|------|------|
| Entry 数量 | 30 条 | trimNarrative Phase 1 |
| 文件大小 | 12KB | trimNarrative Phase 2 |

双重限制确保：
- 不会因单条 entry 过长而超过 size limit（Phase 2 作为安全网）
- 不会因 size limit 过小而丢失所有 history（Phase 1 保证至少 25 条）

### 2.2 Markdown 优先于 JSON — 人类可读性

与 `memoryGraph.jsonl`（JSONL 机器格式）不同，`narrativeMemory.md`：
- **完全人类可读**：不需要解析器即可理解 evolution 历史
- **可直接嵌入 prompt**：Markdown 本身就是有效文本
- **富文本结构**：`###` 标题 + bullet list，AI 可直接消费
- **无 Schema 约束**：Entry 字段可自由变化

### 2.3 原子写（tmp + rename）

```javascript
const tmp = narrativePath + '.tmp';
fs.writeFileSync(tmp, trimmed, 'utf8');
fs.renameSync(tmp, narrativePath);
```

与 `assetStore.js` 的 `writeJsonAtomic` 一致，防止 crash 导致损坏。

### 2.4 Markdown Header 自动补全

首次写入时，若文件不存在或为空，写入：

```markdown
# Evolution Narrative

A chronological record of evolution decisions and outcomes.
```

确保文件始终是有结构的 Markdown。

---

## 3. 与 MemoryGraph 的关系

| 维度 | MemoryGraph | NarrativeMemory |
|------|------------|-----------------|
| 格式 | JSONL（机器） | Markdown（人机双读） |
| 内容 | 7 种事件类型 | Gene/Mutation/Outcome 决策记录 |
| 用途 | 结构化查询/图遍历 | Prompt 注入/快速人类回顾 |
| 写入时机 | 每次 evolution cycle | 每次 cycle（同步） |
| 粒度 | 原子事件（signal/attempt） | 完整决策（gene+outcome+blast） |
| 存储 | `memory_graph.jsonl` | `narrative.md` |

**互补关系**：
- MemoryGraph = 细粒度事件日志（信号检测、基因选择、尝试结果）
- NarrativeMemory = 粗粒度决策摘要（为什么选这个基因、结果如何、影响范围）

`loadNarrativeSummary()` → 直接注入 AI prompt context（无需解析）。

---

## 4. BlueCortexCE 借鉴

### P3: Markdown Observation Narrative（可选）

**现状**：CE 所有 observation 都是结构化 JSONB，无人类可读历史层。  
**提案**：引入 `observation_narrative.md`：

```markdown
### [2026-05-07 00:10] tool_use - success
- Tool: read | Session: sess_xxx | Duration: 234ms
- Signals: [file_read, path_traversal_check]
- Content preview: "...const x = 1; // line 50..."
```

**优点**：
- Developer 可直接查看 observation 历史（类 git log）
- Prompt 注入时 Markdown 天然友好（无需 JSON 解析）
- 独立于数据库，可单独备份/迁移

**实现代价**：P3（需改写 `ObservationService` 写入路径）

### P3: 双限制裁剪（Entry Count + Total Size）

CE 的 SummaryEntity 可借鉴双重限制：
- 保留最近 50 条 summary entries
- 总大小不超过 8KB（自动截断最早 entries）
- 避免 summary 无限膨胀

---

## 5. 总结

| 项目 | 值 |
|------|----|
| 模块 | `narrativeMemory.js` |
| 行数 | 108L，纯 JS，无外部依赖 |
| 核心 | Markdown 追加写 + 双重裁剪（30 entry / 12KB）+ 原子 rename |
| 设计亮点 | 人类可读 Markdown vs 机器 JSONL 双轨；8-entry rolling summary |
| CE 行动项 | P3 Markdown Observation Narrative / P3 双限制 SummaryEntity |

---

**Changelog**

| # | 日期 | 内容 |
|---|------|------|
| 123 | 2026-05-07 | 初稿：narrativeMemory.js 108L 深度分析（Markdown 双限制叙事 / 原子写 / 与 MemoryGraph 互补 / CE P3 提案） |
