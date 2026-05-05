# Doc 100 — `evolve.js` 完整周期 → Memory Graph 操作映射

> **角色**：将 `src/evolve.js`（v1.47.0，2175行）的 `run()` 主循环各阶段逐一映射到对应的 `memoryGraphAdapter` 写操作与状态变更。这是现有 doc 中**未显式覆盖**的完整端到端视图。
>
> **源码**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/evolve.js`
> **依赖模块**：`memoryGraphAdapter.js`（`recordOutcomeFromState` / `recordSignalSnapshot` / `recordHypothesis` / `recordAttempt`）、`solidify.js`、`reflection.js`、`selector.js`
> **前置阅读**：doc 18（memoryGraph 架构快照）、doc 19（outcome 推断链）
> **最后更新**：2026-05-05

---

## §1 概览：为什么需要这张映射表

`evolve.js run()` 不是一个简单的线性函数——它是一个**状态机 + 记忆编织器**。每一轮都：

1. 先闭合**上一轮**的 outcome（用本轮观测对比 baseline）
2. 再锚定**本轮**信号快照（供后续轮次使用）
3. 然后执行基因选择、变异、验证、solidify
4. 最后可能触发自省

理解这张图，是将 Evolver 的**主动进化记忆**模式借鉴到 BlueCortexCE **被动旁路记忆**系统的关键。

---

## §2 完整周期流程 → 记忆操作映射

### Phase 0：Preflight（L972–L1060）

**目标**：确保本轮可执行，检测并发冲突和资源饱和。

| 步骤 | 代码位置 | 记忆操作 | 文件副作用 |
|------|----------|----------|------------|
| 竞速检测 | L1000 | — | 若检测到另一 evolver 进程运行，`writeDormantHypothesis({ backoff_reason: 'another_runner_detected' })` → 中断本轮 |
| 队列上限 | L1013 | — | `readStateForSolidify()` 读取 `last_solidify`；若活跃会话数 > QUEUE_MAX，`writeDormantHypothesis({ backoff_reason: 'active_sessions_exceeded' })` → 退让 |
| 负载感知 | L1025 | — | 若 CPU 负载 > cores × 0.9，`writeDormantHypothesis({ backoff_reason: 'system_overload' })` → 退让 |
| 循环门控 | L1013 | — | `last_solidify` 不存在时挂起，直到完成一次 solidify |
| CWD 恢复 | L1077–L1091 | — | `process.cwd()` ENOENT 时 `chdir(REPO_ROOT)`；无 JSONL 写 |
| Git 检查 | L1093 | — | 无 git repo 时直接 `process.exit(1)`，不写记忆 |
| **休眠恢复** | L1100 | `readDormantHypothesis()` | 若有残留休眠假设，恢复其信号/gene/mutation 状态，清除休眠文件 |
| **维护** | L1121 | `performMaintenance()` | session log 归档（>100 个时移入 archive/） |

**关键不变量**：preflight 的任何 abort 都会写入 `dormant_hypothesis.jsonl`，下一轮 start 时恢复上下文——**记忆永不丢失**。

---

### Phase 1：观测采集（L1126–L1324）

**目标**：从会话日志提取本轮观测数据，作为后续所有记忆操作的输入。

| 步骤 | 代码位置 | 记忆操作 | 产出 |
|------|----------|----------|------|
| 会话日志扫描 | L1126 | `readRealSessionLog()` | `recentMasterLog`（ transcript 原始文本） |
| 今日日志读取 | L1127 | `readRecentLog(TODAY_LOG)` | `todayLog` |
| 记忆片段读取 | L1128 | `readMemorySnippet()` | `memorySnippet`（MEMORY.md 摘要） |
| 用户片段读取 | L1129 | `readUserSnippet()` | `userSnippet` |
| 观测构建 | L1300–L1324 | — | 构建 `observations` 对象：`evidence`（session tail / log tail）、`recent_error_count`、`scan_ms`、`today_log_tail` |
| Solidify 重试上下文 | L1324 | `readStateForSolidify()` | 若上次验证失败，注入 `last_validation_failure` 上下文 |

**`observations` 结构**（L1310–L1320）：
```javascript
observations = {
  evidence: {
    recent_session_tail: sessionLines.slice(-50).join('\n'),  // 最近50行
    today_log_tail: todayLogLines.slice(-30).join('\n'),      // 今日日志尾
  },
  recent_error_count: countErrors(recentMasterLog),           // 错误计数
  scan_ms: Date.now() - startTime,                          // 本轮扫描耗时
  // ...
}
```

---

### Phase 2：Outcome 闭合 — 上一轮 attempt 的结局（L1628）

**目标**：用**本轮观测**闭合**上一轮** attempt 的因果链。

```javascript
// L1628
recordOutcomeFromState({ signals, observations });
```

**内部操作序列**（`memoryGraph.js recordOutcomeFromState`）：

1. 读取 `memory_graph_state.json` 的 `last_action`
2. 若 `outcome_recorded === true` → **no-op**（幂等）
3. `inferOutcomeEnhanced(prevHadError, currentHasError, baselineObserved, currentObserved)`
   - 优先级：① EvolutionEvent JSON 解析 ② 信号启发式 ③ 误差计数/scan_ms 微调
4. `appendJsonl(memoryGraphPath(), { kind: 'outcome', ... })` → **追加写入 jsonl**
5. `appendJsonl(..., { kind: 'confidence_edge', ... })` → signal×gene 边置信快照
6. `appendJsonl(..., { kind: 'confidence_gene_outcome', ... })` → gene 全局 outcome 置信
7. `state.last_action.outcome_recorded = true` → 原子写回 state 文件

**失败处理**：若 `last_action` 不存在，函数直接返回 `null`（no-op）——不会阻止进化继续。

---

### Phase 3：信号快照 — 锚定本轮信号（L1638）

**目标**：将本轮信号写入图谱，供后续轮次的 `getMemoryAdvice` 使用。

```javascript
// L1638
recordSignalSnapshot({ signals, observations });
```

**写入内容**：
```javascript
{ kind: 'signal', signal: { key: signalKey, signals: [...], error_signature: errsig || null }, observed: observations }
```

**设计意图**：snapshot 在 outcome 之后写入——确保 `getMemoryAdvice` 读到的信号永远是**上一轮结束时的快照**，与本轮的 outcome 对齐。

---

### Phase 4：Hub 搜索 — 外部知识检索（L1664–L1682）

**目标**：从 Hub 获取外部能力胶囊作为备选。

```javascript
// L1664
const hubHit = await hubSearch(signals, hubSearchOpts);
// hubHit.capsules = [...]
```

**记忆操作**：Hub 搜索是**只读**，不写本地 jsonl。但 `hubSearch()` 内部会：
- 记录搜索命中/未命中到 Hub 日志（`hub_search_log.jsonl`）
- 若命中，从 Hub 下载胶囊并缓存到 `capsules.jsonl`

**idle-cycle 跳过**（L1682）：若信号含 `force_steady_state` 且无非 `saturationIndicators` 可操作项，跳过 Hub 调用，节省 API 资源。

---

### Phase 5：基因/胶囊选择（L1737）

**目标**：根据信号，从基因库中选择最优基因 + 候选胶囊组合。

```javascript
// L1737
const { selectedGene, capsuleCandidates, selector } = selectGeneAndCapsule({
  signals, genes, capsules, recentFailedCapsules, driftEnabled, cycleCount, ...
});
```

**选择依据**（`selector.js`，见 doc 65/76）：
1. 信号键 → Jaccard 匹配历史基因
2. 边成功率先 Laplace 平滑，再指数半衰（默认 30 天）
3. 与 gene 全局先验加权组合
4. **Failed Capsule Ban**：`readRecentFailedCapsules()` 中因验证失败被 ban 的胶囊直接跳过
5. `driftEnabled` 时允许选择次优基因以增加多样性

**记忆操作**：无直接写入——选择结果后续写入 hypothesis 和 attempt。

---

### Phase 6：假设记录（L1823）

**目标**：在执行前记录"我要做什么 + 为什么"。

```javascript
// L1823
const hyp = recordHypothesis({
  signals,
  mutation: mutNorm,
  personality_state: psNorm,
  selectedGene: { id: geneId, category: geneCategory },
  selector,
  driftEnabled,
  selectedBy,
  capsulesUsed,
  observations,
});
```

**写入**：`{ kind: 'hypothesis', hypothesisId, signalKey, mutation, personality, gene, capsules, observed }`

**为什么需要**：hypothesis 是 attempt 的"前置声明"——若 attempt 失败，hypothesis 提供因果上下文（哪个信号/基因/mutation 组合导致了失败）。

---

### Phase 7：尝试执行记录（L1843）

**目标**：在执行前记录 attempt 元数据（baseline 观测 + outcome_recorded=false）。

```javascript
// L1843
const { actionId, signalKey } = recordAttempt({
  signals,
  mutation: mutNorm,
  personality_state: psNorm,
  selectedGene: { id: geneId, category: geneCategory },
  selector,
  driftEnabled,
  selectedBy,
  hypothesisId: hyp.hypothesisId,
  capsulesUsed,
  observations,
});
```

**关键**：此调用**覆写** `memory_graph_state.json` 的 `last_action`：
```javascript
state.last_action = {
  action_id: actionId,
  signal_key: signalKey,
  baseline_observed: observations,  // ← 当前观测作为下一轮 outcome 的 baseline
  had_error: hasErrorSignal(signals),
  outcome_recorded: false,          // ← 下一轮需要闭合
  ...
};
```

---

### Phase 8：Solidify 验证与提交（L1891–L1911）

**目标**：执行代码变更、运行验证命令、提交 solidification。

```javascript
// L1891
const solidifyResult = await solidify({ ... });
```

**记忆操作**（`solidify.js` 内部）：
1. `appendEventJsonl({ type: 'EvolutionEvent', subtype: 'solidify_start', ... })`
2. 基因/胶囊验证通过后：`upsertGene()` / `upsertCapsule()` 写入 `genes.json` / `capsules.json`
3. 若验证失败：`appendFailedCapsule()` → `failed_capsules.json`（最多200条，超出裁剪至100）
4. `appendEventJsonl({ type: 'EvolutionEvent', subtype: 'solidify_complete', outcome: { status, score } })`

**与 `memoryGraph.jsonl` 的关系**：solidify 写 `events.jsonl`（EvolutionEvent），而非 `memory_graph.jsonl`（MemoryGraphEvent）。两者是**平行存储**：

| 文件 | 存储内容 | 写入模块 |
|------|----------|----------|
| `memory_graph.jsonl` | MemoryGraphEvent（signal/hypothesis/attempt/outcome/confidence_edge） | `memoryGraph.js` |
| `events.jsonl` | EvolutionEvent（solidify_start/solidify_complete） | `solidify.js` |

---

### Phase 9：休眠假设处理（L1911）

**目标**：若 solidify 因验证失败而中断，将状态写入休眠假设，供下一轮恢复。

```javascript
// L1911
if (solidifyState.pending_dormant) {
  writeDormantHypothesis({ ... });
}
```

**与 Phase 0 休眠恢复的呼应**：Phase 0 恢复的假设 → Phase 9 写入的假设，形成**跨轮次状态延续**。

---

### Phase 10：Reflection 自省（L1699–L1707）

**目标**：周期性复盘近期表现，生成策略建议。

```javascript
// L1699（在 Phase 2 outcome 闭合之后、Phase 4 Hub 搜索之前）
if (shouldReflect({ cycleCount, recentEvents })) {
  const ctx = buildReflectionContext({ recentEvents });
  recordReflection({ context: ctx, signals });
}
```

**触发条件**：
- 周期对齐：`cycleCount % computeReflectionInterval(recentEvents) === 0`
- 冷却：距上次 reflection > 30 分钟

**记忆操作**：`recordReflection()` 追加写入 `reflection.jsonl`，包含：
- 近期 10 周期统计（成功率、intent 分布、gene 使用频率）
- 5问战略复盘（input/output/invariants/params/failure_points）
- `buildSuggestedMutations()` 输出的人格参数调整建议

**与 outcome 的关系**：reflection 读 `memory_graph.jsonl` 尾部 2000 行事件，是 outcome 闭合后的**二次分析**。

---

## §3 双文件存储：memory_graph.jsonl vs events.jsonl

| 维度 | `memory_graph.jsonl` | `events.jsonl` |
|------|---------------------|----------------|
| **事件类型** | MemoryGraphEvent（signal/hypothesis/attempt/outcome/confidence_*） | EvolutionEvent（solidify_start/solidify_complete） |
| **写入模块** | `memoryGraph.js` | `solidify.js` |
| **用途** | 信号驱动的基因选择 + 置信推断 | 代码变更历史 + 验证记录 |
| **读取时机** | 每轮 `getMemoryAdvice()` 读尾部 512KB | solidify 流程内部 |
| **容量管理** | 无限追加（512KB 尾读优化） | 无限追加（纯追加） |

---

## §4 完整周期状态流转图

```
上一轮 attempt (last_action: outcome_recorded=false, baseline_observed=X)
         │
         ▼
Phase 2: recordOutcomeFromState(signals, observations)   ← 用本轮观测闭合上一轮
         │                                           ✓ outcome_recorded=true
         │                                           ✓ memory_graph.jsonl += outcome + confidence_edge
         ▼
Phase 3: recordSignalSnapshot(signals, observations)     ← 锚定本轮信号
         │                                           ✓ memory_graph.jsonl += signal
         ▼
Phase 4: hubSearch(signals)                              ← 外部知识（只读）
         │
         ▼
Phase 5: selectGeneAndCapsule(signals, genes)             ← 选择最优基因+胶囊
         │
         ▼
Phase 6: recordHypothesis(...)                           ← 记录执行计划
         │                                           ✓ memory_graph.jsonl += hypothesis
         ▼
Phase 7: recordAttempt(...)                             ← 记录执行尝试
         │                                           ✓ memory_graph.jsonl += attempt
         │                                           ✓ state.json last_action = { outcome_recorded=false, baseline_observed=observations }
         ▼
Phase 8: solidify(...)                                   ← 验证+提交
         │                                           ✓ events.jsonl += EvolutionEvent
         │                                           ✓ genes.json / capsules.json += updated assets
         │                                           ✗ failed_capsules.json += failed capsule (if validation fails)
         ▼
Phase 9: writeDormantHypothesis (if solidify interrupted) ← 跨轮次状态延续
         │
         ▼
Phase 10: shouldReflect → recordReflection               ← 周期性复盘（可选）
         │                                           ✓ reflection.jsonl += reflection
         ▼
下一轮 start
         │
         ▼
Phase 0 (preflight): readDormantHypothesis()           ← 恢复休眠状态（如有）
         │
         ▼
Phase 2: recordOutcomeFromState ← 回到循环开头，闭合本轮 attempt
```

---

## §5 BlueCortexCE 翻译路径

### 5.1 等价的周期映射

| Evolver Phase | CE 等价操作 | 备注 |
|---------------|------------|------|
| Phase 2 outcome 闭合 | `ObservationService.saveObservation(type='cycle_complete', outcome)` | CE 没有 attempt/hypothesis，但可以在 cron 触发时写入"周期完成观察" |
| Phase 3 signal 快照 | `ObservationService.saveObservation(type='signal_snapshot', ...)` | CE 的 SessionStart/Summary 注入块 |
| Phase 7 attempt 记录 | — | CE 没有"执行前记录"——是主动进化的产物 |
| Phase 6 hypothesis | — | 同上 |
| Phase 8 solidify | `contextService.generate()` 的 verify 阶段 | 对应 CE 的 ContextService.verify() |
| Phase 10 reflection | `SummaryService.saveSummary()` | 对应 CE 的 LLM Summary |

### 5.2 关键差异

| 方面 | Evolver | BlueCortexCE |
|------|---------|--------------|
| **触发方式** | 主动 daemon 轮询 | 被动 Hook 注入 |
| **attempt 记录** | 有（执行前） | 无 |
| **hypothesis 记录** | 有（执行前） | 无 |
| **outcome 闭合** | 显式（inferOutcomeEnhanced） | 隐式（SearchService 自然包含历史） |
| **因果链** | signal→hypothesis→attempt→outcome | observation→embedding→search |

### 5.3 建议的 CE 等价实现

CE 的 cron 巡检可以借鉴 Phase 2 的思路：**在触发新的 context 生成前，先闭合上一轮的结果**：

```java
// CE 等价：cron 触发时先记录上一轮 outcome
public void onCronTrigger() {
    // 1. 读取上一轮的 pending context 请求
    PendingContextRequest pending = pendingRepo.findTopByOrderByCreatedAtDesc();
    if (pending != null && !pending.isOutcomeRecorded()) {
        // 2. 用当前状态闭合上一轮
        boolean hasError = checkCurrentErrorState();
        String outcome = hasError ? "failed" : "success";
        observationService.saveObservation(
            type = "cycle_outcome",
            outcome = outcome,
            previousRequestId = pending.getId()
        );
        pending.setOutcomeRecorded(true);
        pendingRepo.save(pending);
    }
    // 3. 然后生成新的 context...
}
```

---

## §6 相关文档

- 完整 memoryGraph 架构：[`18-evolver-local-source-memory-architecture-snapshot.md`](./18-evolver-local-source-memory-architecture-snapshot.md)
- Outcome 推断链详情：[`19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md)
- Gene/Strategy 层：[`24-gene-strategy-layer.md`](./24-gene-strategy-layer.md)
- Evolver 核心设计模式：[`62-evolver-core-design-patterns-and-ce-translation.md`](./62-evolver-core-design-patterns-and-ce-translation.md)
- `evolve.js` 安全系统：[`99-evolver-v147-evolvejs-safety-infrastructure.md`](./99-evolver-v147-evolvejs-safety-infrastructure.md)
- 目录入口：[`index.md`](./index.md)
