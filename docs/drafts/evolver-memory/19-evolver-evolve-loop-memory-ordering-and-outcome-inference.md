# EvoMap/evolver：`evolve.js` 主循环中的记忆顺序与 outcome 推断

> **角色**：补充 [`18`](./18-evolver-local-source-memory-architecture-snapshot.md)——说明 **`src/evolve.js`** 如何在**一轮进化**里编排 `memoryGraphAdapter` 的读写，以及 **`inferOutcomeEnhanced`** 如何闭合「上一轮 attempt → outcome」。  
> **本地源码根路径**：`/Users/yangjiefeng/Documents/EvoMap/evolver`  
> **最后更新**：2026-04-19

---

## 1. 与 `18` 的分工

| 主题 | 主要落点 |
|------|----------|
| JSONL 事件 kind、`getMemoryAdvice`、远程适配器 | [`18`](./18-evolver-local-source-memory-architecture-snapshot.md) |
| **单轮 `evolve` 内调用顺序**、`last_action` 状态机、**outcome 打分逻辑** | **本文** |

---

## 2. 主循环中的调用顺序（`evolve.js`）

在同一轮逻辑中（构建 `observations` 含 `evidence`、`recent_error_count`、`scan_ms` 等之后），**顺序**为：

1. **`recordOutcomeFromState({ signals, observations })`** —— 用**当前** signals/observations **闭合上一轮** `recordAttempt` 留在 `memory_graph_state.json` 里的 `last_action`（若存在且未记 outcome）。失败则 **抛错并拒绝继续进化**（注释：无因果记忆则不允许进化）。  
2. **`recordSignalSnapshot({ signals, observations })`** —— 将**本轮**信号写成图谱上的 `kind: 'signal'` 事件。同样 **失败即中止**。  
3. （中间：`hubSearch`、`getMemoryAdvice` 等 **只读** 推理，不写 JSONL 事件链的前两步已完成「收尾 + 快照」。）  
4. **`recordHypothesis({ ... })`** —— 在选定基因/突变后写入 **`kind: 'hypothesis'`**，得到 `hypothesisId`。  
5. **`recordAttempt({ ... hypothesisId, ... })`** —— 写入 **`kind: 'attempt'`**，并 **覆写** `memory_graph_state.json` 的 **`last_action`**（`outcome_recorded: false`，并保存 `baseline_observed` 等）。

**要点**：**先 outcome 再 snapshot** —— 这样 outcome 对应的是**上一轮的 attempt** 与**本轮开始时的观测**对比；snapshot 则锚定**本轮**用于后续 advice/hub。

---

## 3. `last_action` 状态机（`memoryGraph.js`）

- **`recordAttempt`**：`appendJsonl` attempt 事件后，**原子写** `memory_graph_state.json`，字段包括 `action_id`、`signal_key`、`hypothesis_id`、`had_error`（**`log_error` 是否在 signals 中**）、`baseline_observed`、`outcome_recorded: false` 等。  
- **`recordOutcomeFromState`**：若不存在 `last_action` 或已 `outcome_recorded`，则可能 **no-op**；否则计算推断结果，追加 **`kind: 'outcome'`**，并可能追加 **confidence_*** 事件，然后把 `last_action.outcome_recorded` 置 **true** 写回状态文件。

---

## 4. `inferOutcomeEnhanced`（推断链摘要）

优先级（概念上）：

1. **证据里的结构化 outcome**：从 `currentObserved.evidence` 的 `recent_session_tail` / `today_log_tail` 拼接文本中，**逆向扫描**最近行，尝试 **JSON 解析** 含 `"type":"EvolutionEvent"` 的行，读出 `outcome.status` / `score`（见 `tryParseLastEvolutionEventOutcome`）。  
2. **否则** 使用 **`inferOutcomeFromSignals(prevHadError, currentHasError)`**：由上一轮 `had_error`（基线）与当前是否含 **`log_error`** 组合得到 `success`/`failed` 与基础分。  
3. **启发式微调**：若存在 **`recent_error_count`** 的前后值，按差分微调分数（有界）；若存在 **`scan_ms`** 前后值，按变化比例微调分数（有界），最后 **clamp 到 [0,1]**。

---

## 5. 设计思想（可翻译到 CE 的抽象）

- **因果闭合**：图谱 **append-only**，但用**小状态文件**解决「本轮结束才能标记 outcome」的时序问题。  
- **失败即停**：`evolve` 将若干写入视为**硬依赖**（与旁路「永不阻塞主路径」不同——此处是 **进化进程** 的产品边界）。  
- **多层证据**：优先采信日志里显式 **EvolutionEvent** outcome，再退回收敛式 **信号启发式**，与 **09** 中「稳定键 / 质量门」叙述可对照。

---

## 6. 相关文档

- 图谱与适配器总览：[`18-evolver-local-source-memory-architecture-snapshot.md`](./18-evolver-local-source-memory-architecture-snapshot.md)  
- Evolver ↔ CE 方面：[`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md)  
- 目录索引：[`index.md`](./index.md)
