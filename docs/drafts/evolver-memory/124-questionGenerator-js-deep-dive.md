# `questionGenerator.js` — Hub Bounty 问题生成深度

**Doc**: 124  
**Cron**: 2026-05-07 00:15  
**源码**: `src/gep/questionGenerator.js` (212L, pure JS)  
**相关**: Doc 83 (Post-Solidify 管线 + questionGenerator 引用), Doc 106 (questionComposer vs questionGenerator 对比)

---

## 1. 源码结构（212L）

```javascript
// Dependencies: fs, path, ./paths
const QUESTION_STATE_FILE = 'evolution/question_generator_state.json';
const MIN_INTERVAL_MS = 3 * 60 * 60 * 1000;  // 3 hours
const MAX_QUESTIONS_PER_CYCLE = 2;
const FUZZY_THRESHOLD = 0.7;                 // word-overlap ratio
const MAX_RECENT_QUESTIONS = 20;             // dedup history size
```

---

## 2. 状态管理

**State 文件**：`evolution/question_generator_state.json`

```json
{
  "lastAskedAt": "2026-05-07T00:10:00.000Z",
  "recentQuestions": ["Question text 1", "Question text 2", ...]
}
```

**特性**：
- 持久化到磁盘（crash-safe）
- 只保留最近 20 条（超过则截断旧记录）
- `lastAskedAt` 控制全局 rate limit

---

## 3. 去重机制（双层）

### 3.1 精确去重

```javascript
if (prev === qLower) return true;
```

### 3.2 模糊去重（Word-Set Jaccard ≥ 0.7）

```javascript
var qWords = new Set(qLower.split(/\s+/)...过滤长度>2);
var pWords = new Set(prev.split(/\s+/)...);
var overlap = count(qWords ∩ pWords);
if (overlap / Math.max(qWords.size, pWords.size) > 0.7) return true;
```

**设计意图**：防止"同一问题不同表述"被重复发送。

---

## 4. 六大生成策略

| # | 触发信号 | 问题类型 | Priority |
|---|---------|---------|----------|
| S1 | `recurring_error` / `high_failure_ratio` | 递归错误寻求外部方案 | 3 |
| S2 | `capability_gap` / `unsupported_input_type` | 能力缺口（从 transcript 提取上下文） | 2 |
| S3 | `evolution_saturation` / `force_steady_state` | 饱和转向（附最近使用的 genes） | 1 |
| S4 | `consecutive_failure_streak_≥4` | 连续失败寻求替代策略 | 3 |
| S5 | `user_feature_request` | 用户功能请求寻求社区方案 | 1 |
| S6 | `perf_bottleneck` | 性能瓶颈寻求优化模式 | 2 |

### S1 — 递归错误

```javascript
// 从 recurring_errsig(3x): <detail> 提取错误详情
var errDetail = errSig.replace(/^recurring_errsig\(\d+x\):/, '').trim().slice(0, 120);
// → "Recurring error in evolution cycle that auto-repair cannot resolve: <detail>"
//   -- What approaches or patches have worked for similar issues?
```

### S2 — 能力缺口

```javascript
// 从 session transcript 搜索 "not supported | cannot | unsupported | not implemented"
var gapContext = transcript.match(/(?:not supported|cannot|unsupported|not implemented)[^\n]+/i)?.[0];
// → "Capability gap detected in agent environment: <context>"
//   -- How can this be addressed or what alternative approaches exist?
```

### S3 — 进化饱和

```javascript
// 从最近 5 个 EvolutionEvent 提取 genes_used[0]
// unique 后拼接 → "Agent evolution has saturated after exhausting genes: [gene1, gene2]"
// → "What new evolution directions, automation patterns, or capability genes would be most valuable?"
```

### S4 — 连续失败

```javascript
// 解析 consecutive_failure_streak_N
// 提取 ban_gene:<id>
// → "Agent has failed N consecutive evolution cycles (last gene: <id>).
//     The current approach is exhausted. What alternative strategies or environmental fixes?"
```

### S5 — 用户功能请求

```javascript
// 从 transcript 搜索: /\b(add|implement|create|build|i want|i need|please add)\b/i
// → "User requested a feature that may benefit from community solutions: <context>"
```

### S6 — 性能瓶颈

```javascript
// 从 transcript 搜索: /\b(slow|timeout|latency|bottleneck|high cpu|high memory)\b/i
// → "Performance bottleneck detected: <context>"
//   -- What optimization strategies or architectural patterns address this?"
```

---

## 5. 输出格式

```javascript
// Returned to caller (e.g., solidify.js → A2A fetch payload)
{
  question: string,   // 自然语言问题
  amount: number,     // bounty 金额（0 = 免费）
  signals: string[]    // 关联信号（Hub 可用于匹配）
}
```

**发送路径**：`generateQuestions()` → `solidify.js` → A2A `fetch` 消息的 `payload.questions` 字段 → Hub 创建 bounty

---

## 6. Rate Limiting 逻辑

```
if (lastAskedAt exists AND elapsed < 3 hours) → return [] (空数组)
if (candidates.length === 0) → return []
if (filtered.length === 0 after dedup) → return []
```

**安全措施**：
1. 时间维度去重（3h 全局冷却）
2. 内容维度去重（精确 + 模糊）
3. 数量上限（每轮最多 2 条）
4. Priority 排序（高优先级优先发送）

---

## 7. 与 `questionComposer.js`（ATP）的区别

| 维度 | questionGenerator.js (GEP) | questionComposer.js (ATP) |
|------|---------------------------|--------------------------|
| 场景 | Evolution cycle 内生疑问 | ATP Commerce 买家问题 |
| 触发 | 自动（signals + transcript 分析） | 自动（capability template 匹配） |
| 输出 | Hub bounty 问题 | 自然语言问题给买家 Agent |
| 频率 | ≤2 条 / 3h | 无固定频率（按需） |
| 去重 | 精确 + 模糊（0.7 Jaccard） | capability-based dedup |
| 涉及文件 | `src/gep/` | `src/atp/` |

**关键洞察**：两者都是"AI 自动生成问题"的模块，但服务不同系统。questionComposer 面向外部市场（买/卖能力），questionGenerator 面向内部进化（寻求 Hub 社区帮助解决进化困境）。

---

## 8. BlueCortexCE 借鉴

### P3: Observation Gap 主动外求机制

**现状**：CE 的 `SearchService` 完全依赖本地向量检索，无外部知识源。  
**提案**：当本地 observation 搜索命中 < K 次（`no_relevant_observations` 信号），自动生成"知识请求"：

```javascript
// 当 signals 包含 no_relevant_observations 超过 N 个 cycle
// → generateExternalQuestion({
//     context: sessionTranscript,
//     topic: topSignals[0],
//     askType: 'semantic_search',  // 不是 bounty，而是向知识库请求
//   })
```

**相关已有工作**：Doc 88 (`taskReceiver` relevant lessons 注入) 和 Doc 89 (hubSearch 两阶段) 已覆盖"从 Hub 获取外部资产"场景。questionGenerator 的思想可补充：当本地知识不足时，主动向 Hub 请求，而不是被动等待。

### P3: Question History 防重复

CE 的 ObservationEntity 可借鉴去重思想：
- 记录最近 20 个 observation summary
- 新 observation 写入前检查 fuzzy duplicate（Jaccard ≥ 0.7 → 合并而非新建）
- 防重复刷 observation 导致数据库膨胀

---

## 9. 总结

| 项目 | 值 |
|------|----|
| 模块 | `questionGenerator.js` (GEP) |
| 行数 | 212L，纯 JS |
| 核心 | 六大策略信号→自然语言问题 + 双层去重（精确+模糊）+ 3h rate limit |
| 与 questionComposer 区别 | 内部进化求助 vs 外部市场交易 |
| CE 行动项 | P3 主动知识请求机制（本地搜索 miss → Hub 请求）/ P3 Observation fuzzy dedup |

---

**Changelog**

| # | 日期 | 内容 |
|---|------|------|
| 124 | 2026-05-07 | 初稿：questionGenerator.js 212L 深度（6大策略/双层去重/3h限速/与questionComposer区别/CE P3提案） |
