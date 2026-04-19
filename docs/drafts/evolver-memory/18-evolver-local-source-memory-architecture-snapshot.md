# EvoMap/evolver：本地源码「记忆」架构快照（可核实）

> **角色**：对**本机仓库** `EvoMap/evolver`（默认路径见下）中**与记忆直接相关**的实现做一次**可引用的浓缩**，便于与 `01`–`08` 分片及 [`09`](./09-aspect-bluecortex-bridge.md) 对照；**不**替代逐文件精读。  
> **本地根路径（本次分析）**：`/Users/yangjiefeng/Documents/EvoMap/evolver`  
> **最后更新**：2026-04-19（§7 链至 `19`）

---

## 1. 规范与范围

- **文档体量**：本文件独立成篇，远小于 50KB；长文仍分布在 `01`–`08` 与 `09`–`17`（CE 对照）。  
- **源码范围**：核心为 `src/gep/memoryGraph.js`、`narrativeMemory.js`、`memoryGraphAdapter.js`；测试见 `test/memoryGraph.test.js`。

---

## 2. 存储与文件布局

| 产物 | 说明 | 代码锚点 |
|------|------|----------|
| **追加写 JSONL** | 默认路径：`getEvolutionDir()` 下 **`memory_graph.jsonl`**；可被 **`MEMORY_GRAPH_PATH`** 覆盖 | `memoryGraphPath()` |
| **状态文件** | **`memory_graph_state.json`**：配合 `recordOutcomeFromState` 追踪 `last_action`、防重复记 outcome | `memoryGraphStatePath()`、`readJsonIfExists` / `writeJsonAtomic` |
| **写入语义** | **append-only** JSONL；大文件读尾部 **512KB** 窗口 + 行解析（`tryReadMemoryGraphEvents`） | `tryReadMemoryGraphEvents` |

**叙事 Markdown**（与图谱并行、非 JSONL）：`narrativeMemory.js` 写入 **`getNarrativePath()`**，条目标题为 `### [timestamp] ...`，并 **`trimNarrative`**：**最多约 30 条**、**总长约 12KB** 上限。

---

## 3. 事件模型（`MemoryGraphEvent`）

`type` 均为 **`MemoryGraphEvent`**；**`kind`** 区分阶段（节选）：

| `kind` | 作用（概念） |
|--------|----------------|
| `signal` | 当前信号快照 + `signal.key` + 可选 **`error_signature`** |
| `hypothesis` | 选定基因/突变前的假设文本与元数据（含 `mutation`、`personality`、`capsules`） |
| `attempt` | 一次执行/动作尝试（与 `hypothesis` 链衔接） |
| `outcome` | 由 **状态机 + 观测**推断成功/失败与分数；可关联 `hypothesis.id` |
| `confidence_edge` / `confidence_gene_outcome` | outcome 后追加的**可解释置信快照**（Laplace + 半衰） |
| `external_candidate` | 外部资产进入候选（**不参与** outcome 聚合逻辑） |

**Outcome 推断**：`recordOutcomeFromState` 读取 `memory_graph_state.json` 的 `last_action`，结合当前 signals 是否含 **`log_error`** 等，经 **`inferOutcomeEnhanced`** 写入 `outcome.status` / `score`；同一 action **防重复**（`last.outcome_recorded`）。

---

## 4. 信号键、错误签名与匹配

- **`computeSignalKey(signals)`**：对信号列表归一（含 **`errsig:`** → **`errsig_norm:<stableHash>`**），排序去重后拼接，得到稳定 **`signal.key`**。  
- **`normalizeErrorSignature`**：去路径、十六进制、数字泛化，截断，用于跨运行可比。  
- **`getMemoryAdvice`**：从最近 **≤2000 行** 事件聚合 **`kind === 'outcome'`** 的 `(signal_key, gene_id)` 边；用 **Jaccard ≥ 0.34** 找历史相似信号键；边成功率先 **Laplace 平滑**，再乘 **指数半衰**（默认边 **30 天**、gene 先验 **45 天**）；与 gene 全局先验加权组合后排序，并可 **ban** 低效基因（非 drift 模式下多条件抑制）。

---

## 5. 适配器边界（本地 / 远程）

`memoryGraphAdapter.js`：**默认 `MEMORY_GRAPH_PROVIDER=local`** 直接委托 `memoryGraph.js`。**`remote`** 时：

- **读增强**：`getAdvice` → `POST {remote}/kg/advice`，失败则 **回落本地** `getMemoryAdvice`。  
- **写路径**：**始终先写本地 JSONL**，再 `remoteCall('/kg/ingest', …).catch(() => {})` **异步同步**（注释明确本地为 **source of truth**）。

环境变量（节选）：`MEMORY_GRAPH_REMOTE_URL`、`MEMORY_GRAPH_REMOTE_KEY`、`MEMORY_GRAPH_REMOTE_TIMEOUT_MS`（默认 5000）。

---

## 6. 与仓库内其它文档的读法

| 需求 | 建议 |
|------|------|
| Evolver **叙事与裁剪**叙事线 | [`01`](./01-intro-toc-memory-through-curriculum.md) §2 与本文 §2 |
| **联邦 / 远端适配**叙述 | [`06`](./06-assetcalllog-through-questiongen-v12.md) `memoryGraphAdapter` 相关节；本文 §5 |
| **BlueCortexCE 可翻译点** | [`09`](./09-aspect-bluecortex-bridge.md) P0/P1 与 [`10`](./10-aspect-bluecortex-implementation-map.md) 缺口表 |

---

## 7. 相关文档

- **`evolve.js` 内读写顺序与 outcome 推断链**：[`19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md)  
- 目录总索引：[`index.md`](./index.md)  
- 方面对照：[`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md)  
- CE 实现锚点：[`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md)
