# Evolver 记忆系统分析（详细导航）

**完整变更历史**：见 [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 文首 changelog（与本文完全对齐）。

---

## 接力导航（按 doc 编号）

| 文件 | 内容（精简摘要） |
|------|----------------|
| [01](./01-intro-toc-memory-through-curriculum.md) | 元数据、架构定位、完整目录、§1–§11（memoryGraph → curriculum） |
| [02](./02-skilldistiller-through-evolution-v04.md) | §12–§23 及 v0.4 前后增补 |
| [03](./03-skillpublisher-through-signals-v07.md) | §24–§34（信号链 v0.7 等） |
| [04](./04-mutation-through-policy-v09.md) | §35–§43（mutation、policy、idle、git、localState 等） |
| [05](./05-sanitize-through-execution-trace-v10.md) | §44–§55（sanitize、安全隐私、Hub、executionTrace 等） |
| [06](./06-assetcalllog-through-questiongen-v12.md) | §56–§65（assetCallLog、directory、memoryGraphAdapter、questionGenerator 等） |
| [07](./07-idle-through-skillpublisher-v14.md) | §66–§75（idleScheduler、gitOps、bridge、a2a、skillPublisher v1.4 等） |
| [08](./08-llmreview-assetstore-and-roadmap-v15.md) | §77–§78（llmReview、assetStore）及 v1.5 探索方向 |
| [09](./09-aspect-bluecortex-bridge.md) | Evolver ↔ CE 方面、P0/P1、反模式 |
| [10](./10-aspect-bluecortex-implementation-map.md) | 本仓库 schema、Repository、Service；§3 三路读出；缺口表 |
| [11](./11-research-backlog.md) | 可勾选课题与文件边界 |
| [12](./12-bluecortex-api-memory-surface.md) | HTTP、§1.1 `semantic`、§2 数据平面、§3–§3.2 调用方/MCP |
| [14](./14-context-output-pipeline-sketch.md) | Java `generateContext` vs `/semantic` vs ICL 调用链 |
| [15](./15-runtime-integration-surfaces.md) | Bun Worker vs Java；§2.1 Hook 基址；§4 wrapper→Java；§5 会话首跳 |
| [16](./16-ingestion-write-path-sketch.md) | Java ingest：`IngestionController` / `processToolUseAsync` / `saveObservation` |
| [17](./17-session-lifecycle-java-sketch.md) | Java `/api/session/start` 与 ingest session-end |
| [18](./18-evolver-local-source-memory-architecture-snapshot.md) | EvoMap 本地：`memory_graph.jsonl`、事件 kind、`narrativeMemory` 上限 |
| [19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) | `evolve.js`：记忆读写顺序、`inferOutcomeEnhanced` |
| [20](./20-time-decay-and-fail-degradation.md) | 时间半衰 / 重复失败降权 |
| [21](./21-signal-taxonomy-and-gene-selection-memory.md) | Signal Taxonomy / `expandSignals` / Jaccard / `getMemoryAdvice` |
| [22](./22-error-sig-norm-implementation-proposal.md) | `normalizeErrorSignature` 实现提案 |
| [23](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md) | State+Event 双层 / 自省循环 / localStateAwareness |
| [24](./24-gene-strategy-layer.md) | Gene Pool + Selector + Mutation + Strategy Presets |
| [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) | PRM 评分 / Epigenetic / Anti-Pattern / Innovation / Reflection |
| [26](./26-runtime-orchestration-adaptive-policy-candidates.md) | 自适应策略 / 候选评估 / Git 自修复 / 创新催化 / 自我感知 |
| [27](./27-ops-suite-runtime-config-canary.md) | Ops 模块套件 + 集中配置 `config.js` + Canary 安全网 |
| [28](./28-prompt-engineering-deep-dive.md) | Prompt Schema / 质量门禁 / 敏感数据参数化 / 截断策略 |
| [29](./29-signal-extraction-history-dedup-saturation.md) | Signal 提取 / 历史去重 / 饱和降级 / 多语言 / 工具绕行 |
| [30](./30-multifactor-gene-selection-continuous-drift.md) | 多因子 Gene 选择 / 连续漂移 / diversity-directed drift / Failed Capsule ban |
| [31](./31-reflection-remote-adapter-local-state.md) | 自省自适应间隔 / 远程适配器 / 三层自调节架构 |
| [32](./32-v146-147-multiagent-session-sse-swarm.md) | v1.46–v1.47 多 Agent 会话 / SSE 事件流 / 蜂群 PDRI |
| [33](./33-v148-v166-architecture-evolution.md) | v1.48–v1.66 架构演变 |
| [34](./34-solidify-pipeline-end-to-end.md) | Solidify 管线端到端（PRM / Validation / Canary / Leak check） |
| [35](./35-a2a-protocol-asset-lifecycle-feedback.md) | A2A 协议 / 资产生命周期 / 反馈环路 |
| [36](./36-memory-architecture-synthesis.md) | 记忆系统架构综合（三层记忆 / 反馈环路 / 8 大设计原则） |
| [37](./37-signal-taxonomy-gene-selection-end-to-end.md) | Signal Taxonomy + Gene Selection 端到端 |
| [38](./38-env-fingerprint-capability-match.md) | EnvFingerprint + CapabilityMatch + taskReceiver estimateCapabilityMatch |
| [39](./39-content-addressable-asset-system.md) | Content-addressable Asset System |
| [40](./40-failure-mode-classification-and-canary.md) | Failure Mode Classification + Canary |
| [41](./41-device-identity-and-innovation-catalyst.md) | Device Identity + Innovation Catalyst |
| [42](./42-policycheck-constraint-system-deep-dive.md) | policyCheck.js 约束系统深度 |
| [43](./43-privacy-computing-and-hub-ecosystem.md) | Privacy Computing + Hub Ecosystem |
| [44](./44-personality-state-machine-and-hub-search-caching.md) | Personality State Machine + Hub Search（人格状态机 + Hub 两相搜索） |
| [45](./45-idleScheduler-OMLS-and-llmReview.md) | IdleScheduler OMLS + LLM Review |
| [46](./46-hub-ecosystem-integration-taskreview-issue.md) | Hub Ecosystem：taskReceiver 三策略 ROI + hubReview + issueReporter + a2a |
| [47](./47-curriculum-executiontrace-skill-distillation.md) | Curriculum + ExecutionTrace + SkillDistiller |
| [48](./48-gene-as-compressed-memory-closed-loop-architecture.md) | Gene as Compressed Memory + 完整闭环 |
| [49](./49-localStateAwareness-self-model-evolve-loop-full-integration.md) | `localStateAwareness` + `evolve.js` 全链路 |
| [50](./50-memory-graph-closed-loop-architecture.md) | MemoryGraph 闭环反馈架构（六类事件时序 / Laplace+半衰 / Ban 规则） |
| [51](./51-capability-candidate-lifecycle-pipeline.md) | Capability Candidate 生命周期管线 |
| [52](./52-signal-processing-knowledge-representation-modules.md) | 信号处理与知识表示模块深度 |
| [53](./53-main-daemon-loop-cli-architecture.md) | 主入口 Daemon Loop + CLI 架构（index.js 754行） |
| [54](./54-session-source-arch-v147.md) | v1.47 Session Source 四模式路由 |
| [55](./55-signals-v166-three-layer-extraction.md) | signals.js v1.66 三层信号提取（Layer 1/2/3） |
| [56](./56-signals-reality-check-v147.md) | signals.js 现实核查（⚠️ Doc 56 结论错误，见 73） |
| [57](./57-privacyClient-crypto-HUB_EVENT-wiring-v147.md) | privacyClient + crypto.js + HUB_EVENT_SIGNALS 全链路 |
| [58](./58-v166-new-architecture-three-layer-signals-atp-selfpr.md) | v1.66 新架构（三层信号 / ATP / Self-PR） |
| [59](./59-reflection-js-module-deep-dive.md) | `reflection.js` 源码级深度（computeReflectionInterval / 5问框架） |
| [60](./60-evolver-ops-self-healing-infrastructure.md) | Ops 自我修复基础设施（7大模块 / Dual-mode） |
| [61](./61-sanitize-privacy-pipeline-deep-dive.md) | 脱敏 + 隐私计算管线深度 |
| [62](./62-evolver-core-design-patterns-and-ce-translation.md) | 5 核心设计模式 + CE 翻译优先级矩阵 |
| [63](./63-hub-selector-feedback-loop-and-skilldistiller-validate-deep-dive.md) | Hub-Selector 反馈闭环 + validateSynthesizedGene |
| [64](./64-hub-selector-feedback-and-skilldistiller-validate-v147.md) | Hub-Selector 反馈闭环（v1.47 实际）+ 11道验证门 |
| [65](./65-selector-gene-scoring-and-semantic-matching-deep-dive.md) | Selector 基因评分机制内部实现（BoW Cosine / 表观遗传加成 / `1/√Ne`） |
| [66](./66-memorygraph-event-model-confidence-edges-and-state-schema.md) | MemoryGraph 事件模型完整解析 |
| [67](./67-small-supporting-modules-analyzer-bridge-assets-assetCallLog.md) | 小型支撑模块分析（analyzer/bridge/assets/assetCallLog） |
| [68](./68-post-solidify-pipeline-executiontrace-gitops-skillpublisher-questiongen-a2a.md) | Post-Solidify 完整管线 |
| [69](./69-v148-v178-major-new-subsystems-and-version-gap-analysis.md) | v1.48–v1.78 重大新子系统 + 版本差距分析 |
| [70](./70-new-subsystems-v148-v178-deep-dive.md) | v1.48–v1.78 新增子系统深度（skill2gep/selfPR/validator/portable） |
| [71](./71-MCP-semantic-capability-assessment.md) | MCP Semantic Capability 产品评估 |
| [72](./72-inferOutcomeEnhanced-and-dual-aggregation-chains.md) | inferOutcomeEnhanced baseline/current delta + 双聚合链 |
| [73](./73-reality-check-three-layer-signals-and-new-opportunity-signals.md) | 三层信号提取架构现实核查 + 7 个新机会信号 |
| [74](./74-curriculum-mutation-closed-loop-pipeline.md) | Curriculum + Mutation 闭环管线 |
| [75](./75-atp-agent-transaction-protocol-and-adapters.md) | ATP + Adapters 系统 |
| [76](./76-selectGeneAndCapsule-pipeline-and-failed-capsule-ban.md) | `selectGeneAndCapsule` 决策管线 + Failed Capsule Ban |
| [77](./77-idleScheduler-contentHash-OMLS-adaptive-memory-scheduling.md) | IdleScheduler + ContentHash OMLS 自适应调度 |
| [78](./78-v178-proxy-subsystem-architecture.md) | v1.78 Proxy 子系统架构 |
| [79](./79-evolver-infrastructure-modules-deep-dive.md) | 基础设施模块深度（paths/assetCallLog/deviceId/innovation） |
| [79b](./79b-evolver-ops-infrastructure-modules-deep-dive.md) | 运维基础设施模块深度（health_check/trigger/commentary/cleanup/lifecycle） |
| [79c](./79c-evolver-hook-adapter-system-deep-dive.md) | Hook 适配系统深度 |
| [80](./80-architecture-evolution-v147-v178-config-centralization.md) | v1.47→v1.78.5 架构演进 + `config.js` 集中配置 |
| [81](./81-atp-execute-autodeliver-memorygraph-adapter-selfrepair.md) | ATP Execute + AutoDeliver + MemoryGraphAdapter + SelfRepair |
| [82](./82-solidify-prm-process-scoring-and-epigenetic-marks.md) | Solidify PRM Process Scoring + Epigenetic Marks + Gene Learning Adaptation |
| [83](./83-atp-merchant-side-task-pickup-autobuyer-and-agent-templates.md) | ATP 商家端子系统（pickup/autoBuyer/questionComposer/Agent模板） |
| [84](./84-skilldistiller-full-pipeline-deep-dive.md) | skillDistiller.js 完整管线深度 |
| [85](./85-narrative-memory-ops-commentary-innovation-modules.md) | Narrative Memory + Ops Supporting Modules |
| [86](./86-dual-stack-semantic-architecture.md) | 双栈语义架构：Worker Chroma vs Java pgvector |
| [87](./87-assetstore-contenthash-asset-lifecycle-deep-dive.md) | assetStore.js + contentHash.js 资产持久化与内容寻址 |
| [88](./88-taskreceiver-workerpool-privacy-capability-deep-dive.md) | taskReceiver Worker Pool 原子操作 + Privacy 检测 + Capability Match 全算法 |
| [89](./89-hubsearch-two-phase-semantic-and-dual-cache-deep-dive.md) | hubSearch 两阶段搜索 + 语义增强 + 双层 LRU 缓存 |
| [90](./90-executiontrace-gitops-deep-dive.md) | executionTrace.js + gitOps.js 源码级深度（431行） |
| [91](./91-atp-heartbeatsignalshandler-deep-dive.md) | heartbeatSignalsHandler.js ATP 心跳旁路交付（254行 / 三重保护 / Ledger防重 / 终态错误分离） |

---

## 按主题入口

| 主题 | 建议入口 |
|------|----------|
| 架构定位（Evolver vs CE） | [01](./01-intro-toc-memory-through-curriculum.md) 开篇；§7–§8 |
| **Hermes Agent 记忆管线** | [`../hermes-memory/index.md`](../hermes-memory/index.md) |
| 因果记忆图谱（JSONL） | [01](./01-intro-toc-memory-through-curriculum.md) §1；[18](./18-evolver-local-source-memory-architecture-snapshot.md) |
| 叙事记忆（MD） | [01](./01-intro-toc-memory-through-curriculum.md) §2；[18](./18-evolver-local-source-memory-architecture-snapshot.md) §2 |
| 信号 / learningSignals | [01](./01-intro-toc-memory-through-curriculum.md) §3–§5 |
| 进化主循环与 GEP | [01](./01-intro-toc-memory-through-curriculum.md) §6；[19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) |
| 固化、选择器、课程、蒸馏 | [01](./01-intro-toc-memory-through-curriculum.md) §9–§11 |
| Hub / A2A / 目录 | [46](./46-hub-ecosystem-integration-taskreview-issue.md) + [88](./88-taskreceiver-workerpool-privacy-capability-deep-dive.md) + [89](./89-hubsearch-two-phase-semantic-and-dual-cache-deep-dive.md) |
| 安全、隐私、脱敏 | [61](./61-sanitize-privacy-pipeline-deep-dive.md)；[43](./43-privacy-computing-and-hub-ecosystem.md) |
| 资产与存储 | [87](./87-assetstore-contenthash-asset-lifecycle-deep-dive.md)；[39](./39-content-addressable-asset-system.md) |
| 版本历史与 TODO | [69](./69-v148-v178-major-new-subsystems-and-version-gap-analysis.md) |
| **方面级旁路映射** | [09](./09-aspect-bluecortex-bridge.md) |
| **CE 实现锚点 / 缺口** | [10](./10-aspect-bluecortex-implementation-map.md) |
| **CE 记忆 API / 数据平面** | [12](./12-bluecortex-api-memory-surface.md) |
| **CE 上下文产出调用链** | [14](./14-context-output-pipeline-sketch.md) |
| **CE Java 摄入 / 写入链** | [16](./16-ingestion-write-path-sketch.md) |
| **CE Java 会话 start / end** | [17](./17-session-lifecycle-java-sketch.md) |
| **运行时集成面（Worker / Java）** | [15](./15-runtime-integration-surfaces.md) |
| **待调研与决策** | [11](./11-research-backlog.md) |
| **`error_sig_norm` 落地** | [22](./22-error-sig-norm-implementation-proposal.md) |
| **Hub Search-First + 两阶段搜索** | [89](./89-hubsearch-two-phase-semantic-and-dual-cache-deep-dive.md) |
| **taskReceiver Worker Pool + Capability Match** | [88](./88-taskreceiver-workerpool-privacy-capability-deep-dive.md) |
| **Hub Ecosystem Integration** | [46](./46-hub-ecosystem-integration-taskreview-issue.md) |
| **Personality State Machine** | [44](./44-personality-state-machine-and-hub-search-caching.md) §1 |
| **Hub Search 缓存 + deadline 控制** | [44](./44-personality-state-machine-and-hub-search-caching.md) §2 |
| **PRM 多步骤评分 / Epigenetic Marks** | [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) §1–§2 |
| **Adaptive Reflection / 自省循环** | [59](./59-reflection-js-module-deep-dive.md)；[31](./31-reflection-remote-adapter-local-state.md) §1 |
| **Signal Taxonomy 全链路 + Gene 四因子叠加** | [37](./37-signal-taxonomy-gene-selection-end-to-end.md) |
| **多因子 Gene 选择 / 连续漂移** | [30](./30-multifactor-gene-selection-continuous-drift.md)；[65](./65-selector-gene-scoring-and-semantic-matching-deep-dive.md) |
| **Solidify 管线端到端** | [34](./34-solidify-pipeline-end-to-end.md) |
| **Content-addressable ID / Atomic write / 验证报告** | [34](./34-solidify-pipeline-end-to-end.md) §3–§5；[87](./87-assetstore-contenthash-asset-lifecycle-deep-dive.md) |
| **Failure Mode + Canary** | [40](./40-failure-mode-classification-and-canary.md)；[42](./42-policycheck-constraint-system-deep-dive.md) |
| **policyCheck 约束系统深度** | [42](./42-policycheck-constraint-system-deep-dive.md) |
| **Privacy Computing + Hub Ecosystem** | [43](./43-privacy-computing-and-hub-ecosystem.md)；[61](./61-sanitize-privacy-pipeline-deep-dive.md) |
| **5 个核心设计模式提炼 + CE 翻译优先级** | [62](./62-evolver-core-design-patterns-and-ce-translation.md) |
| **记忆系统架构综合** | [36](./36-memory-architecture-synthesis.md) |
| **主入口 Daemon Loop + CLI** | [53](./53-main-daemon-loop-cli-architecture.md) |
| **Ops 模块套件 / 集中配置 / Canary** | [27](./27-ops-suite-runtime-config-canary.md) |
| **IdleScheduler OMLS 自适应调度** | [77](./77-idleScheduler-contentHash-OMLS-adaptive-memory-scheduling.md) |
| **v1.78 新增 Proxy 子系统** | [78](./78-v178-proxy-subsystem-architecture.md) |
| **Ops 自我修复基础设施** | [60](./60-evolver-ops-self-healing-infrastructure.md) |
| **三层自调节架构综合** | [31](./31-reflection-remote-adapter-local-state.md) §5 |
| **环境指纹 / CapabilityMatch** | [38](./38-env-fingerprint-capability-match.md) |
| **MemoryGraph 事件模型完整** | [66](./66-memorygraph-event-model-confidence-edges-and-state-schema.md) |
| **MemoryGraph 闭环反馈架构** | [50](./50-memory-graph-closed-loop-architecture.md) |
| **Gene as Compressed Memory** | [48](./48-gene-as-compressed-memory-closed-loop-architecture.md) |
| **Capability Candidate 生命周期管线** | [51](./51-capability-candidate-lifecycle-pipeline.md) |
| **Curriculum + Mutation 闭环管线** | [74](./74-curriculum-mutation-closed-loop-pipeline.md) |
| **ATP + Adapters 系统** | [75](./75-atp-agent-transaction-protocol-and-adapters.md) |
| **ATP Execute + AutoDeliver + SelfRepair** | [81](./81-atp-execute-autodeliver-memorygraph-adapter-selfrepair.md) |
| **ATP 商家端子系统** | [83](./83-atp-merchant-side-task-pickup-autobuyer-and-agent-templates.md) |
| **ATP Heartbeat 旁路交付机制** | [91](./91-atp-heartbeatsignalshandler-deep-dive.md) |
| **skillDistiller.js 完整管线** | [84](./84-skilldistiller-full-pipeline-deep-dive.md) |
| **Solidify PRM + Epigenetic** | [82](./82-solidify-prm-process-scoring-and-epigenetic-marks.md) |
| **Post-Solidify 完整管线** | [68](./68-post-solidify-pipeline-executiontrace-gitops-skillpublisher-questiongen-a2a.md) |
| **Hub-Selector 反馈闭环** | [63](./63-hub-selector-feedback-loop-and-skilldistiller-validate-deep-dive.md)；[64](./64-hub-selector-feedback-and-skilldistiller-validate-v147.md) |
| **v1.48–v1.78 新增子系统深度** | [70](./70-new-subsystems-v148-v178-deep-dive.md) |
| **双栈语义架构** | [86](./86-dual-stack-semantic-architecture.md) |
| **基础设施模块深度** | [79](./79-evolver-infrastructure-modules-deep-dive.md)；[79b](./79b-evolver-ops-infrastructure-modules-deep-dive.md)；[79c](./79c-evolver-hook-adapter-system-deep-dive.md) |
| **Device Identity + Innovation Catalyst** | [41](./41-device-identity-and-innovation-catalyst.md) |
| **Content-addressable Asset System** | [39](./39-content-addressable-asset-system.md) |
| **A2A 协议 / 资产发布 / 反馈环路** | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) |
| **Leak check / 脱敏** | [61](./61-sanitize-privacy-pipeline-deep-dive.md) |
| **Leak check / 脱敏（发布前安全扫描）** | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) §2.3–§2.4；[28](./28-prompt-engineering-deep-dive.md) §3 |
| **Provenance chain / 资产溯源** | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) §2.5, §5.3 |
| **Evolver vs BlueCortexCE 本质差异（优化型 vs 记录型）** | [36](./36-memory-architecture-synthesis.md) §9 |

---

## 其他文件

| 文件 | 用途 |
|------|------|
| [AGENT.md](./AGENT.md) | 维护约定：单文件上限、索引优先 |
| [misc.md](./misc.md) | 暂未归类的短摘录 |
| [staging.md](./staging.md) | 极短草稿；定稿后迁入对应分片或删除 |

仓库根路径 [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 为短链接入口，便于旧书签。
