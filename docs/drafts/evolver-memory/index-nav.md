# Evolver 记忆系统分析（详细导航）

**完整变更历史（changelog 条目）**：见 [`changelog-entries.md`](./changelog-entries.md)（与本文 changelog 编号完全对齐）。

---

## 接力导航（按 doc 编号）

| 文件 | 内容（精简摘要） |
|------|----------------|
| [01a](./01a-overview-architecture-memoryGraph-narrative-signals-personality-learningSignals.md) | 元数据、架构定位、§1–§5（memoryGraph / narrativeMemory / signals / personality / learningSignals） |
| [01b](./01b-overview-evolve-hermes-bluecortexce-solidify-selector-curriculum.md) | §6–§11（evolve.js / Hermes / CE借鉴 / solidify / selector / curriculum） |
| [02](./02-skilldistiller-through-evolution-v04.md) | §12–§23 及 v0.4 前后增补 |
| [03](./03-skillpublisher-through-signals-v07.md) | §24–§34（信号链 v0.7 等） |
| [04](./04-mutation-through-policy-v09.md) | §35–§43（mutation、policy、idle、git、localState 等） |
| [05](./05-sanitize-through-execution-trace-v10.md) | §44–§55（sanitize、安全隐私、Hub、executionTrace 等） |
| [06](./06-assetcalllog-through-questiongen-v12.md) | §56–§65（assetCallLog、directory、memoryGraphAdapter、questionGenerator 等） |
| [07a](./07a-modules-idle-localstate-gitops-bridge-assets-v14.md) | §66–§70（idleScheduler / localStateAwareness / gitOps / bridge / assets v1.4） |
| [07b](./07b-modules-a2a-privacy-assets-candidates-skillpublisher-v14.md) | §71–§75（privacyClient / a2a / candidates / skillPublisher v1.4） |
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
| [92](./92-prompt-js-schema-enforcement-and-token-budget.md) | `prompt.js` GEP Schema Enforcement + Token Budget 深度（616行 / 5-Mandatory-Object / JSON-Only / 分层截断 / 条件注入 / 宪法伦理 / Skill质量门） |
| [93](./93-directoryclient-agent-discovery-deep-dive.md) | `directoryClient.js` Agent Capability Directory API（110行 / 语义+信号搜索 / Profile获取 / 静默降级 / Hub三发现接口） |
| [94](./94-v1789-version-delta-and-regression-guards.md) | v1.78.7–v1.78.9 版本差分与回归测试（dotenv加载顺序#526 / MemoryGraph轮转#519 / AGENT_SESSIONS_DIR#527） |
| [96](./96-forceupdate-hub-heartbeat-driven-version-migration.md) | `forceUpdate.js` Hub心跳驱动三通道强制更新（100行 / degit+npm+manual / 版本校验三段semver / 白名单保护 / CE P3） |
| [95](./95-a2aProtocol-and-a2a-deep-dive.md) | `a2aProtocol.js` + `a2a.js` 双层深度（1221+173行 / NodeId 7层fallback / HMAC签名 / 双传输引擎 / 心跳+Hub反馈 / SSE+轮询降级 / Hub DID+信用+审计） |
| [96](./96-forceupdate-hub-heartbeat-driven-version-migration.md) | `forceUpdate.js` Hub心跳驱动三通道强制更新（100行 / degit+npm+manual / 版本校验三段semver / 白名单保护 / CE P3） |
| [97](./97-issue-reporter-and-validation-report-deep-dive.md) | `issueReporter.js` + `validationReport.js` 深度（GitHub Issue自动报告机制 / SHA-256错误签名去重+24h冷却 / ValidationReport标准化+环境指纹+Content-addressable asset_id） |
| [98](./98-v1789-minor-subsystem-additions.md) | v1.78.9 Minor Subsystem Additions（featureFlags.js三层覆盖 / dmHandler.js / skillUpdater.js备份策略 / taskMonitor.js环形缓冲区+心跳元数据） |
| [99](./99-evolver-v147-evolvejs-safety-infrastructure.md) | v1.47.0 `evolve.js` 安全系统深度（竞速检测/队列上限/负载感知/循环门控/修复断路器/6h skills缓存/mood awareness/CWD恢复/Auto-update clawhub/Dormant假设恢复；CE P1–P3行动项） |
| [100](./100-evolvejs-complete-cycle-memory-graph-mapping.md) | `evolve.js` 完整周期→Memory Graph 操作映射（10阶段完整映射 / memory_graph.jsonl vs events.jsonl 双文件存储 / 完整状态流转图 / CE cron等价格式实现） |
| [101](./101-core-memory-architecture-patterns-deep-dive.md) | 核心架构模式深度分析（memoryGraph.js 788行源码综合 / 7大可借鉴模式：append-only双层分离 / stable signal key / 时间衰减+Laplace平滑 / dormant hypothesis中断恢复 / narrative+graph双重历史 / content-addressable asset / execution trace脱敏 / BlueCortexCE优先级P0–P3映射） |
| [102](./102-learningSignals-ops-trigger-skillsMonitor-selfManagement-deep-dive.md) | 主动自我管理三模块深度（learningSignals信号扩展标签化→action:repair/optimize/innovate标签+领域标签 / ops/trigger.js WAKE文件立即唤醒polling-wake机制 / ops/skillsMonitor.js v2.0技能自愈监控missing node_modules+SKILL.md自动修复 / 成熟度L0–L4分级 / CE P1信号扩展标签化+P2立即唤醒+P2技能自愈） |
| [103](./103-v1789-delta-defaultHandler-tokenBudget-comparison.md) | v1.78.9 Delta（index.js+paths.js dotenv#526修复/genes.json+201/胶囊+4） + `atp/defaultHandler.js`（69行/ATP订单fallback/三态开关）+ Token Budget对比（CE固定50条 vs Evolver 20000chars+分层保护） |
| [104](./104-token-budget-semantic-vs-timeline-analysis.md) | Token Budget 分析：语义注入 vs 时间线预算竞争（session-init.ts 双路径独立注入无协调 / ContextService.generateContext 无全局字符上限 / TokenService 仅用于 footer 统计非预算管理 / P1 独立上限方案 + P2 统一 TokenBudgetManager + P3 remaining space 动态计算） |
| [106](./106-questioncomposer-bluecortex-context-pipeline-deep-dive.md) | questionComposer.js 深度 + BlueCortexCE 上下文生成 Pipeline 借鉴（133行 / 模板模式+策略模式+防御性+确定性4大设计模式 / capability→TEMPLATE规范化映射 / hash-seeded确定性选择 / _normalize规范化算法 / CE StructuredContext模板提案 / 4个具体行动项） |
| [107](./107-dual-stack-semantic-consistency-java-pgvector-vs-worker-chroma.md) | Java pgvector vs Worker Chroma 双栈语义一致性（5大根因差异D1-D5：embedding模型/混合策略/去重策略/时间窗口/异常处理 / 3个典型不一致场景 / BlueCortexCE纯Java无当前问题 / P0确认部署路径+P1统一embedding+P2跨栈评测+P3废弃Chroma） |
| [108](./108-v17810-v1787-delta-sync-dedup-new-obfuscated-modules.md) | v1.78.7–v1.78.10 Delta（v1.78.7: +201 genes/+4 capsules/+3个重度混淆模块（explore~65KB/shield~65KB/hubVerify~25KB，hex-encoded packer不可分析，env变量暗示arXiv探索/安全防护/Hub验证）/ v1.78.8: 全模块bump / v1.78.9: evolveSessionsDir.test.js 170L回归测试#527 / v1.78.10: index.js+58行CLI改进 / sync-dedup.test.js 192L端到端测试2个failure mode / 全模块31文件版本bump / 演进趋势：混淆模块+测试覆盖） |
| [109](./109-hook-thin-proxy-latency-analysis.md) | Hook/瘦代理延迟源码级分析（Evolver三种Hook延迟特征：signal-detect<50ms O(n)关键词扫描+1.5s安全阀 / session-start<100ms JSONL读+Kiro 30min dedup / session-end<7s git diff同步子进程+Hub curl双路径 / CE 200ms约束交叉验证：CE瘦代理架构wrapper.js→HTTP ACK→@Async完全符合 / ⚠️ generateContext同步LLM调用+Worker Bun Chroma潜在风险 / 4项实测建议 / backlog Item ✅勾选） |
| [110](./110-mutation-js-core-deep-dive.md) | `mutation.js` 核心深度（204L纯JS / 信号分类决策树（hasErrorishSignal/hasOpportunitySignal/优先级：error→drift→opportunity→strategy→optimize）/ 两层安全门禁（high-risk personality→innovate downgrade to optimize + high-risk mutation人格授权rigor≥0.6+risk_tol≤0.5）/ 8种Safety Signal机制 / 风险等级默认值（repair/low, optimize/low, innovate/medium, high需授权）/ isValidMutation 8-field验证 / normalizeMutation幂等规范化 / BlueCortexCE P1观察风险分级+P1 Safety Signal记录+P2人格驱动注入策略） |
| [111](./111-strategy-js-evolution-presets-deep-dive.md) | `strategy.js` 进化策略预设深度（131L纯JS / 7种预设（balanced/innovate/harden/repair-only/early-stabilize/steady-state/auto）+ 参数（repair+optimize+innovate权重/repairLoopThreshold/label/description）/ 周期数≤5→early-stabilize自动切换（先修后创原则）/ 饱和信号→steady-state / FORCE_INNOVATION向后兼容（优先于自动检测）/ 双路径evolution_state.json读取 / strategy↔mutation联动：strategy.innovate≥0.5触发innovate类别 / BlueCortexCE P2策略驱动注入+P3周期感知模式+P3策略感知搜索排序） |
| [113](./113-memorygraph-adapter-local-first-remote-additive-pattern-deep-dive.md) | `memoryGraphAdapter.js` Local-First/Remote-Addictive 适配器模式（203L纯JS / 10-method Adapter Interface Contract / Local-First写（本地JSONL→异步远程推送→Source of Truth）/ Remote-Addictive读（getAdvice 优先远程KG→降级本地）/ 5s超时+AbortController+Bearer Token / Graceful Degradation / Open-Closed扩展性 / BlueCortexCE P2多存储适配器提案） |
| [114](./114-selector-js-multimode-selection-and-drift-deep-dive.md) | `selector.js` 多模态选择与漂移策略深度（417L纯JS / 3-mode信号匹配regex/alias/substring / BoW Cosine纯JS无依赖 / scoreGeneLearning四层history+epigenetic+anti-pattern+clamp / `1/√Ne`群体遗传学连续漂移 / diversity_directed drift / failed capsule双重条件封禁 / buildSelectorDecision零成本可观测性 / CE P2–P3行动项） |
| [115](./115-personality-js-multi-layer-self-tuning-deep-dive.md) | `personality.js` 多层自我调优系统深度（379L纯JS / 5维状态空间rigor/creativity/verbosity/risk_tolerance/obedience / 三层突变（Natural Selection→Triggered→Reflection-driven）/ personalityScore Laplace平滑+小样本惩罚 / chooseBestKnownPersonality历史最优小步靠近 / 每轮≤2参数×±0.2防跳变 / CE P2 ModeService五维状态+P2人格驱动注入策略 / 源码证据） |
| [116](./116-candidates-js-three-source-capability-extraction-deep-dive.md) | `candidates.js` 三源能力候选提取深度（208L纯JS / Transcript工具调用频率≥3 / Signal信号→能力映射10类 / Failed Capsule聚类按problem:*标签≥2次 / Five-Questions Shape标准结构 / stableHash去重幂等ID / CE P2 Observation能力缺口发现+P2候选表+P3 Gene-as-Capability / 源码证据） |
| [117](./117-solidify-js-core-deep-dive.md) | `solidify.js` 核心深度（1344L纯JS / 8维PRM过程评分（signal×0.05+selection×0.10+mutation×0.05+blast×0.15+constraint×0.25+validation×0.25+protocol×0.10+canary×0.05）/ 三层验证门禁PolicyCheck→Canary→LLM Review / FailedCapsule rollback前diff零丢失捕获 / 表观遗传Marks+基因学习适配 / Anti-pattern opt-in发布 / LessonL轻量失败知识化 / Hub Task自动完成 / CE P1多维质量评分+P1上下文三层验证+P1失败Context保存+P2表观遗传+P2结构化failure_reason） |
| [118](./118-localStateAwareness-and-analyzer-deep-dive.md) | `localStateAwareness.js`+`analyzer.js` 深度（244L+60L / captureLocalState五维状态自发现快照注入session-init / analyzeFailures元学习从MEMORY.md提取失败模式 / 两模块在session-init→solidify→narrative链路中位置 / CE P2状态快照注入+P3失败模式预注入） |
| [119](./119-curriculum-js-outcome-driven-learning-deep-dive.md) | `curriculum.js` Outcome-Driven Curriculum Learning 深度（163L纯JS / 从 outcomes 自动推导学习路径 / 三分类 mastered(≥80%,≥3次)/failing(≤30%)/frontier(中间) / `|rate-0.5|` 前沿优先选择最不确定技能 / `curriculum_target:gap:X` 来自candidates能力缺口 / `curriculum_target:frontier:Y` 来自frontier分析 / 进度追踪环形缓冲50条 / level 1-5 每5次成功升级 / `curriculum_state.json` 原子写入 / 无需预定义课程——从真实outcome数据自动发现学习路径 / CE P3 Outcome驱动课程学习提案） |
| [120](./120-v1790-v1791-cycle-hard-timeout-and-windows-respawn-deep-dive.md) | v1.79.0/v1.79.1 Delta 深度（+89L index.js daemon 重写 / +127L `cycleHardTimeout.test.js` 回归测试 / +167L `spawnReplacementProcess.test.js` 单元测试 / +201 genes / +4 capsules / Cycle Hard-Timeout Issue #19：Promise.race 45min 硬超时 + CycleTimeoutError + progressTicker 30s 刷新 + writeCycleProgressAtomic / Windows Respawn Fix #528：spawn(detached) 在 Windows 开 cmd 弹窗 → 默认跳过，opt-in via EVOLVER_SUICIDE_WINDOWS=true / dotenv 加载顺序修复 #460+#526：cwd/.env 优先于 getRepoRoot 缓存 / CE P1 超时保护+P1 环境变量加载顺序+P2 跨平台进程管理） |
| [105](./105-core-memory-architecture-deep-dive-evolver.md) | EvoMap 核心记忆架构源码深度（memoryGraph.js 完整解析 / 7种 MemoryGraphEvent 种类 / JSONL 追加+原子写 / Session Scope 隔离 / 4语言信号提取 / History-aware 去重+饱和检测 / expandSignals 标签化 / Laplace+半衰置信度评分 / 无标签结果推断 / 双栈适配器 / Narrative Memory / Reflection 自适应间隔 / localStateAwareness 五维自省 / BlueCortexCE 对照表） |

---

## 按主题入口

| 主题 | 建议入口 |
|------|----------|
| 架构定位（Evolver vs CE） | [01a](./01a-overview-architecture-memoryGraph-narrative-signals-personality-learningSignals.md) 开篇 + [01b](./01b-overview-evolve-hermes-bluecortexce-solidify-selector-curriculum.md) §7–§8 |
| **Hermes Agent 记忆管线** | [`../hermes-memory/index.md`](../hermes-memory/index.md) |
| 因果记忆图谱（JSONL） | [01a](./01a-overview-architecture-memoryGraph-narrative-signals-personality-learningSignals.md) §1；[18](./18-evolver-local-source-memory-architecture-snapshot.md) |
| 叙事记忆（MD） | [01a](./01a-overview-architecture-memoryGraph-narrative-signals-personality-learningSignals.md) §2；[18](./18-evolver-local-source-memory-architecture-snapshot.md) §2 |
| 信号 / learningSignals | [01a](./01a-overview-architecture-memoryGraph-narrative-signals-personality-learningSignals.md) §3–§5 |
| 进化主循环与 GEP | [01b](./01b-overview-evolve-hermes-bluecortexce-solidify-selector-curriculum.md) §6；[19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) |
| 固化、选择器、课程、蒸馏 | [01b](./01b-overview-evolve-hermes-bluecortexce-solidify-selector-curriculum.md) §9–§11 |
| Hub / A2A / 目录 | [46](./46-hub-ecosystem-integration-taskreview-issue.md) + [88](./88-taskreceiver-workerpool-privacy-capability-deep-dive.md) + [89](./89-hubsearch-two-phase-semantic-and-dual-cache-deep-dive.md) |
| 安全、隐私、脱敏 | [61](./61-sanitize-privacy-pipeline-deep-dive.md)；[43](./43-privacy-computing-and-hub-ecosystem.md) |
| 资产与存储 | [87](./87-assetstore-contenthash-asset-lifecycle-deep-dive.md)；[39](./39-content-addressable-asset-system.md) |
| 版本历史与 TODO | [69](./69-v148-v178-major-new-subsystems-and-version-gap-analysis.md) |
| **方面级旁路映射** | [09](./09-aspect-bluecortex-bridge.md) |
| **CE 实现锚点 / 缺口** | [10](./10-aspect-bluecortex-implementation-map.md) |
| **CE 记忆 API / 数据平面** | [12](./12-bluecortex-api-memory-surface.md) |
| **CE 上下文产出调用链** | [14](./14-context-output-pipeline-sketch.md)；[106](./106-questioncomposer-bluecortex-context-pipeline-deep-dive.md)（questionComposer模板模式借鉴） |
| **CE Java 摄入 / 写入链** | [16](./16-ingestion-write-path-sketch.md) |
| **CE Java 会话 start / end** | [17](./17-session-lifecycle-java-sketch.md) |
| **运行时集成面（Worker / Java）** | [15](./15-runtime-integration-surfaces.md) |
| **待调研与决策** | [11](./11-research-backlog.md) |
| **`error_sig_norm` 落地** | [22](./22-error-sig-norm-implementation-proposal.md) |
| **Hub Search-First + 两阶段搜索** | [89](./89-hubsearch-two-phase-semantic-and-dual-cache-deep-dive.md) |
| **taskReceiver Worker Pool + Capability Match** | [88](./88-taskreceiver-workerpool-privacy-capability-deep-dive.md) |
| **A2A Protocol / Agent-to-Agent 通信** | [95](./95-a2aProtocol-and-a2a-deep-dive.md) |
| **Hub Agent Directory / directoryClient** | [93](./93-directoryclient-agent-discovery-deep-dive.md) |
| **Hub Ecosystem Integration** | [46](./46-hub-ecosystem-integration-taskreview-issue.md) |
| **Personality State Machine + 三层自我调优** | [44](./44-personality-state-machine-and-hub-search-caching.md) §1；[115](./115-personality-js-multi-layer-self-tuning-deep-dive.md)（自然选择+触发突变+反思驱动 / Laplace平滑 / 每轮≤2参数×±0.2） |
| **Hub Search 缓存 + deadline 控制** | [44](./44-personality-state-machine-and-hub-search-caching.md) §2 |
| **PRM 多步骤评分 / Epigenetic Marks** | [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) §1–§2 |
| **Adaptive Reflection / 自省循环** | [59](./59-reflection-js-module-deep-dive.md)；[31](./31-reflection-remote-adapter-local-state.md) §1；[118](./118-localStateAwareness-and-analyzer-deep-dive.md)（`captureLocalState` 五维状态快照 / `analyzeFailures` 元学习失败模式提取） |
| **主动自我管理**（signal扩展标签化 / 文件立即唤醒 / 技能自愈） | [102](./102-learningSignals-ops-trigger-skillsMonitor-selfManagement-deep-dive.md) |
| **Signal Taxonomy 全链路 + Gene 四因子叠加** | [37](./37-signal-taxonomy-gene-selection-end-to-end.md) |
| **多因子 Gene 选择 / 连续漂移** | [30](./30-multifactor-gene-selection-continuous-drift.md)；[65](./65-selector-gene-scoring-and-semantic-matching-deep-dive.md) |
| **Solidify 管线端到端** | [34](./34-solidify-pipeline-end-to-end.md) |
| **GEP Mutation 构建引擎 + 安全门禁** | [110](./110-mutation-js-core-deep-dive.md) |
| **GEP 策略预设 + 自动检测** | [111](./111-strategy-js-evolution-presets-deep-dive.md) |
| **Memory Graph 适配器模式（Local-First / Remote-Addictive）** | [113](./113-memorygraph-adapter-local-first-remote-additive-pattern-deep-dive.md) |
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
| **ForceUpdate 版本迁移（Hub心跳驱动）** | [96](./96-forceupdate-hub-heartbeat-driven-version-migration.md) |
| **v1.79 Cycle Hard-Timeout + Windows Respawn** | [120](./120-v1790-v1791-cycle-hard-timeout-and-windows-respawn-deep-dive.md)（Promise.race 45min 硬超时 / CycleTimeoutError / progressTicker 30s / Windows cmd popup 修复 / dotenv 加载顺序） |
| **Ops 自我修复基础设施** | [60](./60-evolver-ops-self-healing-infrastructure.md) |
| **三层自调节架构综合** | [31](./31-reflection-remote-adapter-local-state.md) §5 |
| **环境指纹 / CapabilityMatch** | [38](./38-env-fingerprint-capability-match.md) |
| **MemoryGraph 事件模型完整** | [66](./66-memorygraph-event-model-confidence-edges-and-state-schema.md) |
| **MemoryGraph 闭环反馈架构** | [50](./50-memory-graph-closed-loop-architecture.md) |
| **Gene as Compressed Memory** | [48](./48-gene-as-compressed-memory-closed-loop-architecture.md) |
| **Capability Candidate 生命周期管线** | [51](./51-capability-candidate-lifecycle-pipeline.md)；[116](./116-candidates-js-three-source-capability-extraction-deep-dive.md)（三源提取：转录频率≥3+信号映射10类+失败聚类≥2次 / Five-Questions Shape / stableHash去重） |
| **Curriculum + Mutation 闭环管线** | [74](./74-curriculum-mutation-closed-loop-pipeline.md) |
| **ATP + Adapters 系统** | [75](./75-atp-agent-transaction-protocol-and-adapters.md) |
| **ATP Execute + AutoDeliver + SelfRepair** | [81](./81-atp-execute-autodeliver-memorygraph-adapter-selfrepair.md) |
| **ATP 商家端子系统** | [83](./83-atp-merchant-side-task-pickup-autobuyer-and-agent-templates.md) |
| **ATP Heartbeat 旁路交付机制** | [91](./91-atp-heartbeatsignalshandler-deep-dive.md) |
| **skillDistiller.js 完整管线** | [84](./84-skilldistiller-full-pipeline-deep-dive.md) |
| **Solidify PRM + Epigenetic** | [82](./82-solidify-prm-process-scoring-and-epigenetic-marks.md)；[117](./117-solidify-js-core-deep-dive.md)（`solidify.js` 1344行源码完整解析：8维PRM评分+三层验证门禁+FailedCapsule零丢失+表观遗传Marks+Anti-pattern发布+LessonL+Hub Task自动完成） |
| **GEP Prompt Schema Enforcement + Token Budget** | [92](./92-prompt-js-schema-enforcement-and-token-budget.md) |
| **v1.78.7–v1.78.9 版本差分 + 回归测试护栏** | [94](./94-v1789-version-delta-and-regression-guards.md) |
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

| [121](./121-gitOps-hubReview-deep-dive.md) | `gitOps.js`+`hubReview.js` 深度（230L+206L / gitOps 三种回滚策略hard/stash/none + 关键文件保护名单 + 路径遍历双重防护normAbs.startsWith(normRepo) + DIFF_SNAPSHOT 8000截断 / hubReview 非阻塞评审提交（outcome×constraint双因素4档评分 + hub_review_history.json防重复 + 10s超时） / CE P1关键文件保护+P1路径遍历防护+P3复用资产评审） |
| [122](./122-paths-js-path-architecture-deep-dive.md) | `paths.js` 路径架构+Session Scope隔离深度（133L纯JS / 集中路径管理+多租户Scope隔离（EVOLVER_SESSION_SCOPE）/ 三层路径遍历防护白名单字符+长度+禁止点点 / 自身目录优先防止误用父仓库 / getWorkspaceRoot三层fallback / 与doc 80 config集中化互补构成配置中枢 / CE P1多租户路径隔离+P1路径遍历防护+P2集中PathConfig） |
| [123](./123-narrativeMemory-js-deep-dive.md) | `narrativeMemory.js` Markdown 双限制叙事记忆深度（108L纯JS / Markdown 追加写（`### [timestamp] CATEGORY - status`）/ 双重裁剪30entry+12KB + 原子 rename / 8-entry rolling summary / 与 MemoryGraph 互补（机器 JSONL vs 人类 Markdown 双轨）/ CE P3 Markdown Observation Narrative + P3 双限制 SummaryEntity） |
| [124](./124-questionGenerator-js-deep-dive.md) | `questionGenerator.js` Hub Bounty 问题生成深度（212L纯JS / 6大策略信号→自然语言问题（recurring_error/capability_gap/saturation/failure_streak/feature_request/perf_bottleneck）/ 双层去重精确+模糊0.7 Jaccard / 3h限速+≤2条/轮 / 与 questionComposer.js (ATP) 区别（内部进化求助 vs 外部市场交易）/ CE P3 主动知识请求+P3 Observation fuzzy dedup） |
| [125](./125-atp-taskpickup-execute-heartbeat-adapters-deep-dive.md) | ATP TaskPickup+Execute+Heartbeat+Adapters 深度（233+285+254+275+207+203+163+172+89L staged files / atpTaskPickup Ledger防重+原子写+冷却5min / atpExecute ContentHash幂等+HMAC签名+分阶段错误返回 / heartbeatSignalsHandler 旁路交付+共享Ledger双消费者+3态Ledger / hubClient proxy/direct双路由 / hookAdapter 平台检测+coldMerge+markerKey干净卸载 / kiro.js ClaudeCode Codex Cursor四平台适配器 / CE P1 ObservationEntity内容哈希幂等+P1 Cron Ledger防重+P1原子写+P2分阶段错误+P2终结错误码+P2markerKey卸载） |
| [126](./126-gene-skillpublisher-memory-to-capability-pipeline-deep-dive.md) | Gene→SKILL.md transformation pipeline深度（352L纯JS `skillPublisher.js`源码 / sanitizeSkillName两-tier命名（hash→kebab + signal/summary回退）/ toTitleCase display name / SKILL.md六字段结构（name/summary/signals_match/content_hash/gene_id/created_at）/ content-addressable naming自动去重+版本管理 / 与 BlueCortexCE SummaryEntity对照 / CE P1 content_hash幂等+P1 provenance字段+P2 Gene-like StructuredExtraction+P3 Hub distribution） |
| [117](./117-deviceId-js-node-identity-deep-dive.md) | `deviceId.js` 节点身份识别深度（209L / 7层降级：EVOMAP_DEVICE_ID env→~/.evomap/文件→项目本地文件→/etc/machine-id→IOPlatformUUID→Docker容器ID→MAC→随机 / 容器检测4方法（/.dockerenv + cgroup + /run/.containerenv + hostname hex）/ SHA-256隐私保护原始标识符（从不暴露MAC/UUID原值）/ 双路径持久化0o600权限 / CE P1节点指纹权限+P2多租户身份+P2 CLAUDE_MEM_NODE_ID env var） |
| [118](./118-innovation-js-catalyst-deep-dive.md) | `innovation.js` 创新催化剂深度（67L / 6类技能分布分析：feishu/dev/media/security/automation/data / 3策略启发式：填补空白（最少2类）+ 优化现有（技能>50）+ 元级改进（始终）/ 最多返回3条ideas / 零LLM成本停滞突破 / 被 stagnation signal 触发调用 / CE P3停滞信号+P3技能分布分析参考） |
