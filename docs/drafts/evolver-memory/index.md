# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**最后更新**：2026-04-19（新增 `19` `evolve` 循环记忆顺序与 outcome 推断）

**并列入口（Hermes / 论文线）**：[`../memory-research-hub.md`](../memory-research-hub.md)

### 架构规范（与 cron / [`AGENT.md`](./AGENT.md) 对齐）

- **短入口**：[`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 只保留**链接表**，勿把完整长文写回该路径。  
- **单文件上限**：本目录正文建议 **≤50KB**；`01`–`08` 为模块分片，`09`–`19` 为对照 / 快照短文，超标则**新建方面文件或拆分**，而非单文件堆长段。  
- **索引真源**：`09`–`19` **一句话职责表**以本页 **附录** 为准；新增编号时同步 [`../memory-research-hub.md`](../memory-research-hub.md)「按任务」表，避免多表漂移。  
- **例行自检（可选）**：`wc -c docs/drafts/evolver-memory-analysis.md` 应保持**短链体量**；分片 `wc -c docs/drafts/evolver-memory/0*.md` 任一分片若逼近 50KB 再考虑拆分。

本目录按**模块与时间线**拆成 `01`–`08`（各文件建议 ≤50KB），便于渐进阅读；**产品侧「方面」对照**见 [`09`](./09-aspect-bluecortex-bridge.md)；**本仓库代码锚点**见 [`10`](./10-aspect-bluecortex-implementation-map.md)；**HTTP / 数据平面**见 [`12`](./12-bluecortex-api-memory-surface.md)；**Java 会话 start/end** 见 [`17`](./17-session-lifecycle-java-sketch.md)；**未决课题**见 [`11`](./11-research-backlog.md)。

### 接力导航（Agent / 续写）

| 目标 | 打开 |
|------|------|
| 多线总导航 | [`../memory-research-hub.md`](../memory-research-hub.md) |
| 读出 + **写入数据平面** | [`10`](./10-aspect-bluecortex-implementation-map.md) **§3** → [`12`](./12-bluecortex-api-memory-surface.md) **§1–2** → [`14`](./14-context-output-pipeline-sketch.md) / [`16`](./16-ingestion-write-path-sketch.md) / [`17`](./17-session-lifecycle-java-sketch.md) → [`15`](./15-runtime-integration-surfaces.md) |
| **Hook → Worker 基址**（`workerHttpRequest` / 37777 与 Java 同号陷阱） | [`15`](./15-runtime-integration-surfaces.md) **§2.1** |
| **MCP 工具 vs Hook `semantic`**（`search`/`timeline` 无 `semantic` 工具名） | [`12`](./12-bluecortex-api-memory-surface.md) **§3.2**；判别 [`15`](./15-runtime-integration-surfaces.md) **§2** · **§3** |
| **EvoMap/evolver 本地源码**（`memoryGraph` / 叙事 / 适配器） | [`18`](./18-evolver-local-source-memory-architecture-snapshot.md) |
| **`evolve.js` 主循环**：记忆调用顺序、`last_action`、outcome 推断 | [`19`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) |
| 可勾选的研究项 | [`11`](./11-research-backlog.md) |
| 维护规则与 `CANONICAL` | [`AGENT.md`](./AGENT.md) |

### 附录：BlueCortexCE 对照短文 + EvoMap 快照（`09`–`19`）一句话

| 文件 | 用途 |
|------|------|
| [09](./09-aspect-bluecortex-bridge.md) | Evolver ↔ CE **方面**、P0/P1、反模式 |
| [10](./10-aspect-bluecortex-implementation-map.md) | 本仓库 **Repository/Service**、§3 **三路读出**、缺口表 |
| [11](./11-research-backlog.md) | **可勾选**课题与文件边界 |
| [12](./12-bluecortex-api-memory-surface.md) | **HTTP**、§1.1 **`semantic`**、§2 **数据平面**、**§3–§3.1** 调用方、**§3.2** MCP vs `semantic` |
| [14](./14-context-output-pipeline-sketch.md) | Java **读出**（`generateContext` / `semantic` / ICL） |
| [15](./15-runtime-integration-surfaces.md) | Worker/Java **判别**；**§2.1** Hook 基址；**§2** 集成客户端 → 默认进程；**§4** wrapper→Java；**§5** 会话首跳 |
| [16](./16-ingestion-write-path-sketch.md) | Java **ingest 写入**（`IngestionController` → `AgentService`） |
| [17](./17-session-lifecycle-java-sketch.md) | Java **`/api/session/start`** 与 session-end **一头一尾** |
| [18](./18-evolver-local-source-memory-architecture-snapshot.md) | **EvoMap/evolver 本地源码**：JSONL 事件、`getMemoryAdvice`、叙事裁剪、**remote** 适配器 |
| [19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) | **`evolve.js` 循环**：`recordOutcome`→`signal`→…→`hypothesis`→`attempt`；**`inferOutcomeEnhanced`** |

## 阅读路径

1. **通读源码级分析**：按 `01` → `08` 顺序。
2. **先找落点**：读 [01](./01-intro-toc-memory-through-curriculum.md) 开篇「架构定位」与 **§8 BlueCortexCE 借鉴建议汇总**，再按需跳转到各模块分片。
3. **按主题**：见下表「按主题入口」。
4. **旁路落地**：在 §8 或各模块读后，读 [09](./09-aspect-bluecortex-bridge.md)。

## 文档地图（顺序分片）

| 文件 | 内容范围（章节概览） |
|------|----------------------|
| [01](./01-intro-toc-memory-through-curriculum.md) | 元数据、架构定位、完整目录、§1–§11（memoryGraph → curriculum） |
| [02](./02-skilldistiller-through-evolution-v04.md) | §12–§23 及 v0.4 前后增补 |
| [03](./03-skillpublisher-through-signals-v07.md) | §24–§34（信号链 v0.7 等） |
| [04](./04-mutation-through-policy-v09.md) | §35–§43（mutation、policy、idle、git、localState 等） |
| [05](./05-sanitize-through-execution-trace-v10.md) | §44–§55（sanitize、安全隐私、Hub、executionTrace 等） |
| [06](./06-assetcalllog-through-questiongen-v12.md) | §56–§65（assetCallLog、directory、memoryGraphAdapter、questionGenerator 等） |
| [07](./07-idle-through-skillpublisher-v14.md) | §66–§75（idleScheduler、gitOps、bridge、a2a、skillPublisher v1.4 等） |
| [08](./08-llmreview-assetstore-and-roadmap-v15.md) | §77–§78（llmReview、assetStore）及 v1.5 探索方向 |

### 方面级增补（在 `01`–`08` 之外）

| 文件 | 主题 |
|------|------|
| [09](./09-aspect-bluecortex-bridge.md) | 架构/存储/检索/上下文/可观测性等：**Evolver ↔ BlueCortexCE** 与可执行优先级 |
| [10](./10-aspect-bluecortex-implementation-map.md) | **本仓库** schema、Repository、Service；**§3** 时间线 / 语义注入 / 搜索；缺口相对 `09` P0 |
| [11](./11-research-backlog.md) | 未决课题 / 决策 backlog（可勾选） |
| [12](./12-bluecortex-api-memory-surface.md) | **§1** 读出 · **§1.1** `semantic` · **§2** 写入 · **§3–§3.2** 调用方 / MCP |
| [14](./14-context-output-pipeline-sketch.md) | **`generateContext` vs `/semantic` vs ICL** 的 Java 调用链速写 |
| [15](./15-runtime-integration-surfaces.md) | **Bun Worker vs Java**；**§2.1** Hook 基址；**§4** wrapper→Java；**§5** 会话首跳（`sessions/init` ∥ `session/start`） |
| [16](./16-ingestion-write-path-sketch.md) | **Java 摄入**：`IngestionController` / `processToolUseAsync` / `saveObservation` |
| [17](./17-session-lifecycle-java-sketch.md) | **Java 会话**：`/api/session/start`（缓存 + `generateContext`）与 ingest **session-end** |
| [18](./18-evolver-local-source-memory-architecture-snapshot.md) | **EvoMap 本地**：`memory_graph.jsonl`、事件 kind、`narrativeMemory` 上限、`MEMORY_GRAPH_PROVIDER` |
| [19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) | **`evolve.js`**：记忆读写顺序、`memory_graph_state`、`inferOutcomeEnhanced` |
| **Hermes（内置型参照）** | [`../hermes-memory/index.md`](../hermes-memory/index.md)；注入 [`04`](../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md)、安全盘点 [`05`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md)、接力 [`11`](../hermes-memory/11-research-backlog.md) |

## 按主题入口

| 主题 | 建议入口 |
|------|----------|
| 架构定位（Evolver vs CE） | [01](./01-intro-toc-memory-through-curriculum.md) 开篇；§7–§8 |
| **Hermes Agent 记忆管线**（第三方参照） | [`../hermes-memory/index.md`](../hermes-memory/index.md) |
| 因果记忆图谱（JSONL） | [01](./01-intro-toc-memory-through-curriculum.md) §1；**本地源码浓缩** [18](./18-evolver-local-source-memory-architecture-snapshot.md) |
| 叙事记忆（MD） | [01](./01-intro-toc-memory-through-curriculum.md) §2；**裁剪参数** [18](./18-evolver-local-source-memory-architecture-snapshot.md) §2 |
| 信号 / learningSignals | [01](./01-intro-toc-memory-through-curriculum.md) §3–§5；[03](./03-skillpublisher-through-signals-v07.md)、[04](./04-mutation-through-policy-v09.md) |
| 进化主循环与 GEP | [01](./01-intro-toc-memory-through-curriculum.md) §6；[02](./02-skilldistiller-through-evolution-v04.md)–[04](./04-mutation-through-policy-v09.md)、[06](./06-assetcalllog-through-questiongen-v12.md)；**`evolve` 内记忆顺序 / outcome** [19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) |
| 固化、选择器、课程、蒸馏 | [01](./01-intro-toc-memory-through-curriculum.md) §9–§11；[02](./02-skilldistiller-through-evolution-v04.md) §29–§32 |
| Hub / A2A / 目录 | [04](./04-mutation-through-policy-v09.md) 起；[05](./05-sanitize-through-execution-trace-v10.md)–[07](./07-idle-through-skillpublisher-v14.md) |
| 安全、隐私、脱敏 | [04](./04-mutation-through-policy-v09.md) §43 起；[05](./05-sanitize-through-execution-trace-v10.md) §44–§51 |
| 资产与存储 | [02](./02-skilldistiller-through-evolution-v04.md)、[07](./07-idle-through-skillpublisher-v14.md)、[08](./08-llmreview-assetstore-and-roadmap-v15.md) |
| 版本历史与 TODO | [03](./03-skillpublisher-through-signals-v07.md) 中原 §33 等 |
| **方面级旁路映射** | [09](./09-aspect-bluecortex-bridge.md) |
| **CE 实现锚点 / 缺口** | [10](./10-aspect-bluecortex-implementation-map.md) |
| **CE 记忆 API / 数据平面** | [12](./12-bluecortex-api-memory-surface.md)（§1.1 `semantic` · §2 写入 · §3.2 MCP） |
| **CE 上下文产出调用链** | [14](./14-context-output-pipeline-sketch.md) |
| **CE Java 摄入 / 写入链** | [16](./16-ingestion-write-path-sketch.md) |
| **CE Java 会话 start / end** | [17](./17-session-lifecycle-java-sketch.md) |
| **运行时集成面（Worker / Java）** | [15](./15-runtime-integration-surfaces.md) |
| **待调研与决策** | [11](./11-research-backlog.md) |

若同一 § 编号在版本增补中出现多次，以分片内**版本标注**为准；完整目录列表见 [01](./01-intro-toc-memory-through-curriculum.md)。

## 其他文件

| 文件 | 用途 |
|------|------|
| [AGENT.md](./AGENT.md) | 维护约定：单文件上限、索引优先、`CANONICAL.sha256` 何时更新 |
| [misc.md](./misc.md) | 暂未归类的短摘录 |
| [staging.md](./staging.md) | 极短草稿；定稿迁入 `0x`/`09`–`19`/`11` 等或删除（与 [`11`](./11-research-backlog.md) 可勾选队列区分） |

仓库根路径 [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 为上述入口的短链接，便于旧书签。
