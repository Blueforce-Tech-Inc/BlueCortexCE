# Evolver / EvoMap 记忆系统分析

**目标**：理解 EvoMap `evolver` 的记忆与进化相关设计，为 BlueCortexCE（旁路记忆）提炼可翻译、可落地的思想（非照搬 GEP 运行时）。

**数据来源**：本地 `EvoMap/evolver/` 源码；本仓库见 `docs/ARCHITECTURE-zh-CN.md` 等。

**最后更新**：2026-04-23（新增 `29` Signal 提取深度；`30` 多因子选择深度；`31` 自省/远程适配器；`32` v1.46–v1.47 多 Agent；`33` v1.48–v1.66 架构演变；**`34` Solidify 管线端到端**；**`35` A2A 协议 / 资产生命周期 / 反馈环路**）

**文档结构**：完整目录、接力导航与 **≤50KB / 短入口** 约定见 [`evolver-memory/index.md`](./evolver-memory/index.md) 文首「架构规范」；本文件仅作**链接入口**。

## 入口

| 说明 | 路径 |
|------|------|
| **记忆研究总导航**（Hermes / Evolver / 论文） | [docs/drafts/memory-research-hub.md](./memory-research-hub.md) |
| CE 上下文 **Java 调用链**（`generateContext` / `semantic` / ICL） | [docs/drafts/evolver-memory/14-context-output-pipeline-sketch.md](./evolver-memory/14-context-output-pipeline-sketch.md) |
| **Bun Worker vs Java**（端口、**§2** 集成客户端、Chroma/pgvector、OpenClaw 配置名） | [docs/drafts/evolver-memory/15-runtime-integration-surfaces.md](./evolver-memory/15-runtime-integration-surfaces.md) |
| **Hook → Worker 基址**（`workerHttpRequest`、`settings.json`、`clearPortCache`） | [docs/drafts/evolver-memory/15-runtime-integration-surfaces.md](./evolver-memory/15-runtime-integration-surfaces.md) **§2.1** |
| **会话首跳**（`POST /api/sessions/init` Worker vs `POST /api/session/start` Java） | [docs/drafts/evolver-memory/15-runtime-integration-surfaces.md](./evolver-memory/15-runtime-integration-surfaces.md) **§5** |
| **Java 摄入 / 写入链**（`IngestionController`、`processToolUseAsync`） | [docs/drafts/evolver-memory/16-ingestion-write-path-sketch.md](./evolver-memory/16-ingestion-write-path-sketch.md) |
| **Java 会话 start / end**（`SessionController`、`/api/session/start`） | [docs/drafts/evolver-memory/17-session-lifecycle-java-sketch.md](./evolver-memory/17-session-lifecycle-java-sketch.md) |
| 总索引（阅读顺序、按主题跳转） | [docs/drafts/evolver-memory/index.md](./evolver-memory/index.md) |
| **EvoMap/evolver 本地源码**（`memoryGraph` / 叙事 / 适配器；路径可改） | [docs/drafts/evolver-memory/18-evolver-local-source-memory-architecture-snapshot.md](./evolver-memory/18-evolver-local-source-memory-architecture-snapshot.md) |
| **`evolve.js` 主循环**（记忆顺序、`outcome` 推断） | [docs/drafts/evolver-memory/19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md](./evolver-memory/19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) |
| **State+Event 双层 / 自省循环 / localStateAwareness** | [docs/drafts/evolver-memory/23-evolver-state-event-dual-layer-and-self-awareness-loop.md](./evolver-memory/23-evolver-state-event-dual-layer-and-self-awareness-loop.md) |
| **Gene Pool + Selector + Mutation + Strategy Presets**（Gene/Strategy 层） | [docs/drafts/evolver-memory/24-gene-strategy-layer.md](./evolver-memory/24-gene-strategy-layer.md) |
| **高级模式**（PRM 评分 / Epigenetic / Anti-Pattern / Innovation Catalyst / Reflection） | [docs/drafts/evolver-memory/25-advanced-patterns-prm-epigenetic-antipattern.md](./evolver-memory/25-advanced-patterns-prm-epigenetic-antipattern.md) |
| **运行时编排**（自适应策略 / 候选评估 / Git 自修复 / 创新催化 / 自我感知） | [docs/drafts/evolver-memory/26-runtime-orchestration-adaptive-policy-candidates.md](./evolver-memory/26-runtime-orchestration-adaptive-policy-candidates.md) |
| **运维层深度**（Ops 套件 / 集中配置 / Canary 安全网 / Health Check） | [docs/drafts/evolver-memory/27-ops-suite-runtime-config-canary.md](./evolver-memory/27-ops-suite-runtime-config-canary.md) |
| **Prompt 工程深度**（Schema / 质量门禁 / 敏感数据参数化 / 截断策略） | [docs/drafts/evolver-memory/28-prompt-engineering-deep-dive.md](./evolver-memory/28-prompt-engineering-deep-dive.md) |
| **Signal 提取深度**（`analyzeRecentHistory` / 频率抑制 / 连续修复 / 空转饱和 / 失败连击 / 多语言 / 工具绕行） | [docs/drafts/evolver-memory/29-signal-extraction-history-dedup-saturation.md](./evolver-memory/29-signal-extraction-history-dedup-saturation.md) |
| **多因子选择深度**（四因子评分 / `1/√Ne` 连续漂移 / diversity-directed drift / Failed Capsule ban） | [docs/drafts/evolver-memory/30-multifactor-gene-selection-continuous-drift.md](./evolver-memory/30-multifactor-gene-selection-continuous-drift.md) |
| **自省 / 远程适配器 / 状态感知**（自适应间隔 / 人格微调 / 本地优先远程 / 三层自调节） | [docs/drafts/evolver-memory/31-reflection-remote-adapter-local-state.md](./evolver-memory/31-reflection-remote-adapter-local-state.md) |
| **v1.46–v1.47 多 Agent 会话 / SSE 事件流 / 蜂群 PDRI / EvoMap-First** | [docs/drafts/evolver-memory/32-v146-147-multiagent-session-sse-swarm.md](./evolver-memory/32-v146-147-multiagent-session-sse-swarm.md) |
| **v1.48–v1.66 架构演变**（加权关键词评分 / 平台适配器 / ATP / 集中配置 / Self-PR） | [docs/drafts/evolver-memory/33-v148-v166-architecture-evolution.md](./evolver-memory/33-v148-v166-architecture-evolution.md) |
| **Solidify 管线端到端**（PRM / Content-addressable / ValidationReport / Canary / Leak check） | [docs/drafts/evolver-memory/34-solidify-pipeline-end-to-end.md](./evolver-memory/34-solidify-pipeline-end-to-end.md) |
| **A2A 协议 / 资产生命周期 / 反馈环路**（发布 / 获取 / Review / Task receiver） | [docs/drafts/evolver-memory/35-a2a-protocol-asset-lifecycle-feedback.md](./evolver-memory/35-a2a-protocol-asset-lifecycle-feedback.md) |
| **Signal Taxonomy**（`expandSignals` / Jaccard ≥ 0.34 / `getMemoryAdvice` / Laplace 平滑） | [docs/drafts/evolver-memory/21-signal-taxonomy-and-gene-selection-memory.md](./evolver-memory/21-signal-taxonomy-and-gene-selection-memory.md) |
| 方面级对照：Evolver ↔ BlueCortexCE | [docs/drafts/evolver-memory/09-aspect-bluecortex-bridge.md](./evolver-memory/09-aspect-bluecortex-bridge.md) |
| 本仓库实现映射（迁移、Repository、§3 三路读出） | [docs/drafts/evolver-memory/10-aspect-bluecortex-implementation-map.md](./evolver-memory/10-aspect-bluecortex-implementation-map.md) |
| 记忆 HTTP + 数据平面（**§1.1** `semantic` · §2 · **§3.2** MCP） | [docs/drafts/evolver-memory/12-bluecortex-api-memory-surface.md](./evolver-memory/12-bluecortex-api-memory-surface.md) |
| Hermes 参照 + **CE 注入面与 `/api/context`** | [docs/drafts/hermes-memory/index.md](./hermes-memory/index.md)；[04 对照表](./hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md) |
| 研究 / 决策 backlog | [docs/drafts/evolver-memory/11-research-backlog.md](./evolver-memory/11-research-backlog.md) |
| 文档维护约定（体量、索引、staging） | [docs/drafts/evolver-memory/AGENT.md](./evolver-memory/AGENT.md) |
