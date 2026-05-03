# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**最后更新**：2026-05-03（**`76`** `selectGeneAndCapsule` 端到端决策管线 + Failed Capsule Ban 机制（`banGenesFromFailedCapsules` 双重门禁（overlap≥0.6+fail≥2）/ 连续 drift intensity `1/√Ne` 遗传学公式 / 五种 drift mode / noveltyScore 动态扩展 topN / capability gap directed drift / `buildSelectorDecision` 可解释性 / 完整管线数据流图 / CE P1（失败 observation type 降权）/ P2）；**`75`** ATP（Agent Transaction Protocol）+ Adapters 系统深度分析；**`74`** Curriculum + Mutation 闭环管线深度分析；backlog `featureFlags.js` 条目核实为不存在（已标记完成））  
**完整变更历史**：见 [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 文首 changelog（与本文 changelog 完全对齐）。

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
| **v1.48–v1.78 新增子系统深度分析**（`skill2gep.js` 逆向蒸馏 645 行（parseSkillMd / detectForgery / assembleCapsule / 双通道发布）/ `selfPR.js` 多门禁自动 PR 408 行（score+streak+risk+blast 多重门禁 + leakCheck + diffHash 去重）/ `validator/` 沙箱子系统 900+ 行（两层白名单+BLOCKED_NODE_FLAGS+隔离 env / 磁盘持久化退避 / 独立守护进程）/ CE P0/P1 借鉴路径） | [`70`](./70-new-subsystems-v148-v178-deep-dive.md) |
| **MCP Semantic Capability 产品评估**（MCP `search` vs Hook `semantic` 底层能力对齐（均走 EmbeddingService+pgvector）但产品形态不同（检索结果 vs prompt注入块）/ 无同名工具 / 安全评估：MCP返回完整ObservationEntity，潜在泄露面大于Hook / 产品建议） | [`71`](./71-MCP-semantic-capability-assessment.md) |
| **inferOutcomeEnhanced baseline/current delta + 双聚合链**（`memoryGraph.js` L551–L592：error_count delta ±0.12 + scan_ms ratio ±0.06 + clamp01 / getMemoryAdvice 双链：signal×gene 边 30天 vs gene先验 45天 + best+prior*0.12 / CE baselineMetrics 提案） | [`72`](./72-inferOutcomeEnhanced-and-dual-aggregation-chains.md) |
| **v1.48–v1.78 重大新子系统差距分析**（v1.47 → v1.78 共31个版本差距概览 / 版本里程碑对照） | [`69`](./69-v148-v178-major-new-subsystems-and-version-gap-analysis.md) |
| **`localStateAwareness` + `evolve.js` 全链路**（五类自模型快照 / 完整记忆调用链 / State+Event 双写 / Hub 饱和节流） | [`49`](./49-localStateAwareness-self-model-evolve-loop-full-integration.md) |
| **主入口 Daemon Loop + CLI 架构**（index.js 754行：6命令路由 / singleton lock / 自适应休眠三层乘数 / suicide内存保护 / OMLS主动蒸馏 / Hub心跳独立运行） | [`53`](./53-main-daemon-loop-cli-architecture.md) |
| **MemoryGraph 事件模型完整**（7种事件类型谱 / `confidence_edge`(30天半衰)+`confidence_gene_outcome`(45天半衰) 置信边 / `edgeExpectedSuccess` Laplace+衰减 / `tryParseLastEvolutionEventOutcome` JSONL解析 / `memory_graph_state.json` 完整Schema / 外部候选隔离机制 / 完整 Cycle 时序图） | [`66`](./66-memorygraph-event-model-confidence-edges-and-state-schema.md) |
| **Post-Solidify 完整管线**（executionTrace 三级脱敏轨迹构建（none/minimal/standard）/ gitOps 保护区+三种回滚模式（none/hard/stash）+ 路径穿越防护 / skillPublisher Gene→SKILL.md 市场级输出 + Hub 409冲突处理 / questionGenerator 六策略主动求援 + 模糊去重 + 速率限制 / a2a Capsule广播资格（score≥0.7+blast safe+streak≥2）+ 置信度降权（×0.6）+ JSONL解析容错 / 端到端管线全图） | [`68`](./68-post-solidify-pipeline-executiontrace-gitops-skillpublisher-questiongen-a2a.md) |
| **Gene Pool + Selector + Mutation + Strategy Presets**（Gene/Strategy 层新发现） | [`24`](./24-gene-strategy-layer.md) |
| **PRM 评分 / Epigenetic / Anti-Pattern / Innovation / Reflection**（高级模式） | [`25`](./25-advanced-patterns-prm-epigenetic-antipattern.md) |
| **自适应策略 / 候选评估 / Git 自修复 / 创新催化 / 自我感知**（运行时编排） | [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) |
| **Device Identity + Innovation Catalyst**（deviceId 7层 fallback / 容器检测 / 双路径持久化 / innovation 弱领域驱动创意） | [`41`](./41-device-identity-and-innovation-catalyst.md) |
| **Privacy Computing + Hub Ecosystem**（AES-256-GCM 密封工具 / 本地密钥管理 / 六策略问题生成 + 模糊去重 / 自动 GitHub Issue + 冷却去重 + 脱敏 / 人格 commentary 三模式） | [`43`](./43-privacy-computing-and-hub-ecosystem.md) |
| **Personality State Machine + Hub Search**（五维人格状态机 + 自然选择 + 三层突变叠加 + cap 保护 / Hub 两相搜索 + LRU 缓存 + deadline 控制 + 并行语义搜索） | [`44`](./44-personality-state-machine-and-hub-search-caching.md) |
| **Curriculum + ExecutionTrace + SkillDistiller**（三区分类课程系统 / 三级脱敏执行轨迹 / ValidationReport + content-hash / GitOps 保护与回滚 / LLM 驱动技能提炼 + Marketplace SKILL.md 生成） | [`47`](./47-curriculum-executiontrace-skill-distillation.md) |
| **Gene as Compressed Memory + 完整闭环架构**（Gene=压缩可执行记忆 / signal→Gene→outcome→边权重闭环 / Laplace平滑+半衰 / 内容寻址幂等 / Session Scope 隔离 / CE P0/P1/P2） | [`48`](./48-gene-as-compressed-memory-closed-loop-architecture.md) |
| **Hub Ecosystem Integration**（taskReceiver 三策略 ROI 评分 + capability match / hubReview review 提交 + 本地去重 / issueReporter 自动 GitHub issue + cooldown / a2a 广播资格） | [`46`](./46-hub-ecosystem-integration-taskreview-issue.md) |
| **Hub-Selector 反馈闭环**（心跳→`_latestNoveltyHint`/`_latestCapabilityGaps`→ Curriculum信号注入 + selector drift；diversity_directed / random_weighted / random 三模式） | [`63`](./63-hub-selector-feedback-loop-and-skilldistiller-validate-deep-dive.md)；**[v1.47 实际实现 + validateSynthesizedGene 11道验证门 + Hub Events 全图 35+ 信号](./64-hub-selector-feedback-and-skilldistiller-validate-v147.md)** |
| **privacyClient + crypto.js + HUB_EVENT_SIGNALS 全链路**（AES-256-GCM加密原语 / 6个API端点submit→upload→execute→poll→retrieve→decrypt / key不离本地设计 / taskReceiver PRIVACY_PARAMS解析 / 35+ HUB_EVENT_SIGNALS分类表（dialog/swarm/privacy/governance/review/knowledge）） | [`57`](./57-privacyClient-crypto-HUB_EVENT-wiring-v147.md) |
| **v1.66 新架构分析**（三层信号（Layer 1 regex / Layer 2 加权评分 / Layer 3 LLM）/ 7个新 Opportunity Signals / Plateau 检测 / 平台适配器系统 `src/adapters/` / ATP Hub market / Self-PR 多层门禁 / 主要模块精简） | [`58`](./58-v166-new-architecture-three-layer-signals-atp-selfpr.md) |
| **Capability Candidate 生命周期管线**（信号→候选提取→Hub搜索→技能提炼→悬赏提问五阶段；candidates.js三来源/candidateEval.js双池/hubSearch两阶段/skillDistiller LLM提炼/questionGenerator六策略） | [`51`](./51-capability-candidate-lifecycle-pipeline.md) |
| **信号处理与知识表示模块深度**（learningSignals.js三层标签扩展/problem-action-area-risk/geneTags+scoreTagOverlap基因评分/candidates.js三来源候选提取/FiveQuestionsShape标准化/narrativeMemory.js有界叙事记忆/paths.js SessionScope隔离/CE P0/P1/P2） | [`52`](./52-signal-processing-knowledge-representation-modules.md) |
| **Ops 模块套件 / 集中配置 / Canary 安全网 / Health Check**（运维基础设施） | [`27`](./27-ops-suite-runtime-config-canary.md) |
| **Prompt Schema / 质量门禁 / 敏感数据参数化 / 截断策略**（提示词深度） | [`28`](./28-prompt-engineering-deep-dive.md) |
| **Signal 提取 / 历史去重 / 饱和降级 / 多语言 / 工具绕行**（信号深度） | [`29`](./29-signal-extraction-history-dedup-saturation.md) |
| **signals.js v1.66 三层信号提取**（Layer 1 `_extractRegex` / Layer 2 `_extractKeywordScore` 加权评分 / Layer 3 `_extractLLM` 语义 / `_mergeSignals` 归一化合并 / v1.47→v1.66 四文件混淆）⚠️ **见 [56] 现实核查** | [`55`](./55-signals-v166-three-layer-extraction.md)；[`56`](./56-signals-reality-check-v147.md) |
| **signals.js 现实核查 + 三层架构确认**（⚠️ **Doc 56 结论错误**：signals.js v1.78 确认 Layer 2/3 真实存在 / `SIGNAL_PROFILES` 加权评分 / `execFileSync` argv 防注入 / 7 个新机会信号 / 详见 [`73`](./73-reality-check-three-layer-signals-and-new-opportunity-signals.md)） | [`56`](./56-signals-reality-check-v147.md)；[`73`](./73-reality-check-three-layer-signals-and-new-opportunity-signals.md) |
| **ATP + Adapters 系统深度分析**（ATP Hub Client 275行（placeOrder/submitDelivery/verify/settle/dispute + proxy/direct 双路由）/ Merchant Agent 商家模板 118行 / Consumer Agent 消费者模板 157行 / autoBuyer ~200行（三重预算保护 + 24h去重 + cold-start半额）/ atpTaskPickup / serviceHelper 99行 / hookAdapter 207行统一适配器 + detectPlatform + mergeJsonFile + copyHookScripts + 4平台adapter（Cursor/ClaudeCode/Codex/Kiro） / BlueCortexCE P1/P2/P3 借鉴路径） | [`75`](./75-atp-agent-transaction-protocol-and-adapters.md) |
| **v1.66 新架构分析**（三层信号（Layer 1 regex / Layer 2 加权评分 / Layer 3 LLM 语义）/ 7个新 Opportunity Signals / Plateau 检测（avgScore < 0.35 + 无改善）/ `src/adapters/` 统一跨平台 Hook / ATP Hub market（placeOrder/deliver / fastest/cheapest/auction/swarm）/ Self-PR 多层门禁（score+streak+leak+diff dedup+cooldown）/ 主要模块精简（evolve -2176行 / memoryGraph -788行 / a2aProtocol -1222行） | [`58`](./58-v166-new-architecture-three-layer-signals-atp-selfpr.md) |
| **多因子 Gene 选择 / 连续漂移 / diversity-directed drift / Failed Capsule ban**（选择器深度） | [`30`](./30-multifactor-gene-selection-continuous-drift.md)；**Selector 评分机制内部实现**（Bag-of-Words Cosine / 三策略 Pattern 匹配 / 表观遗传加成 / `1/√Ne` 漂移公式） | [`65`](./65-selector-gene-scoring-and-semantic-matching-deep-dive.md) |
| **`selectGeneAndCapsule` 端到端决策管线 + Failed Capsule Ban**（`banGenesFromFailedCapsules` 双重门禁（overlap≥0.6 + fail≥2）/ 连续 drift intensity `1/√Ne` 遗传学公式 / 五种 drift mode / `noveltyScore` 动态扩展 topN / capability gap directed drift / `buildSelectorDecision` 可解释性 / 完整管线数据流图 / CE P1（失败 observation type 降权）/ P2（探索性配置 / problem type boost / SearchResult explanation） | [`76`](./76-selectGeneAndCapsule-pipeline-and-failed-capsule-ban.md) |
| **自省自适应间隔 / 远程适配器 / 三层自调节架构**（反思与集成） | [`31`](./31-reflection-remote-adapter-local-state.md) |
| 可勾选的研究项 | [`11`](./11-research-backlog.md) |
| 维护规则与 `CANONICAL` | [`AGENT.md`](./AGENT.md) |

### 附录：BlueCortexCE 对照短文 + EvoMap 快照（`09`–`31`）一句话

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
| [20](./20-time-decay-and-fail-degradation.md) | **排序增强专题**：`decayWeight` / `edgeExpectedSuccess`（Evolver）→ CE `time_decay_score` + `fail_penalty` 翻译方案 |
| [21](./21-signal-taxonomy-and-gene-selection-memory.md) | **Signal Taxonomy**：`expandSignals` / `computeSignalKey` / Jaccard ≥ 0.34 / `getMemoryAdvice` 完整链；CE 观察类型标签化借鉴 |
| [22](./22-error-sig-norm-implementation-proposal.md) | **`extractedData.error_sig_norm` 写入提案**：规范化算法 + JSONB schema + 写入路径 + 实施检查清单 |
| [23](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md) | **State+Event 双层架构**：可变 State 文件 + 不可变 JSONL 事件、幂等 outcome 写入、自省循环（Reflection Phase） |
| [49](./49-localStateAwareness-self-model-evolve-loop-full-integration.md) | **`localStateAwareness` + `evolve.js` 全链路**：五类自模型快照模块 / 完整记忆调用链（Signal→Advice→Snapshot→Hypothesis→Attempt→Outcome→Narrative→Reflection）/ JSONL 事件序列 / State+Event 双写模式 / Hub 饱和节流 `shouldSkipHubCalls` / CE 借鉴路径 |
| [24](./24-gene-strategy-layer.md) | **Gene/Strategy 层**：Gene Pool + 多因子选择器（exact+semantic+epigenetic+learning）+ Strategy Presets（repair/optimize/innovate）+ Mutation 安全约束 + Candidates Pool |
| [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) | **高级模式**：PRM 多步骤评分 + Epigenetic Marks + Failed Capsules / Anti-Pattern Zone + Lessons/Principles Block + Innovation Catalyst + Adaptive Reflection + Prompt 工程架构 + A2A Auto-Publish |
| [26](./26-runtime-orchestration-adaptive-policy-candidates.md) | **运行时编排**：自适应策略策略 + Blast Radius 动态控制 + 候选评估管线 + Git 自修复 + 创新催化 + 本地状态感知 |
| [27](./27-ops-suite-runtime-config-canary.md) | **运维层深度**：Ops 模块套件（lifecycle / skills_monitor / cleanup / trigger / health_check）+ 集中配置 `config.js` + Canary 安全网 |
| [28](./28-prompt-engineering-deep-dive.md) | **Prompt 工程深度**：严格 Schema 定义 + 敏感数据参数化 + 技能创建质量门禁 + 截断策略精确实现 + 常见失败模式 |
| [29](./29-signal-extraction-history-dedup-saturation.md) | **Signal 提取深度**：`analyzeRecentHistory` 历史感知、频率抑制、连续修复检测、空转饱和降级、失败连击干预、多语言需求提取、工具绕行检测 |
| [30](./30-multifactor-gene-selection-continuous-drift.md) | **多因子选择深度**：四因子评分叠加、`1/√Ne` 连续漂移强度、diversity-directed drift、Failed Capsule ban、anti-pattern 惩罚 > 成功奖励 |
| [31](./31-reflection-remote-adapter-local-state.md) | **自省 / 远程适配器 / 状态感知**：自适应自省间隔、人格微调、本地优先远程同步、三层自调节架构综合 |
| [59](./59-reflection-js-module-deep-dive.md) | **`reflection.js` 源码级深度**：computeReflectionInterval 三态算法（3/5/8）、shouldReflect 双重条件（周期对齐+冷却30min）、预聚合统计（intent分布/gene频率）、5问战略复盘框架与精确JSON输出格式、`buildSuggestedMutations` 信号→参数映射（creativity/rigor/risk_tolerance）、JSONL读写机制、与innovation.js功能/参数二级互补、CE自我诊断框架与元级SummaryEntity提案 |
| [60](./60-evolver-ops-self-healing-infrastructure.md) | **Ops 自我修复基础设施**：7大 ops 模块（lifecycle/cleanup/self_repair/skills_monitor/health_check/trigger/commentary）/ Dual-mode 设计（独立CLI + npm模块）/ 进程管理+双保险清理+Git四层自愈+技能健康检查+自愈+系统资源监控+即时唤醒+人格化评语 / 与主循环外部解耦 / CE借鉴 P0/P1/P2 |
| [61](./61-sanitize-privacy-pipeline-deep-dive.md) | **脱敏 + 隐私计算管线**：sanitize.js 双模式（替换 vs 检测）/ 14种 REDACT_PATTERNS + 17种 LEAK_SCANNERS / 逆向检测 detectEnvValueLeaks / crypto.js AES-256-GCM / privacyClient 六步管线 / HUB_EVENT_SIGNALS 隐私分类表 / Solidify 集成序列 / CE 脱敏设计建议 |
| [32](./32-v146-147-multiagent-session-sse-swarm.md) | **v1.46–v1.47 深度**：多 Agent 会话格式兼容（Claude Code/Cursor/Codex/Manus）、SSE 事件流自动重连、35+ HUB_EVENT_SIGNALS（蜂群 PDRI/隐私/议会）、EvoMap-First 自适应搜索 |
| [54](./54-session-source-arch-v147.md) | **v1.47 Session Source**：四模式路由（auto/cursor/openclaw/merge）/ `readOpenClawSessions` 专用函数 / `collectTranscriptFiles` 递归 walk（depth 3）/ vibe feature 删除 |
| [33](./33-v148-v166-architecture-evolution.md) | **v1.48–v1.66 架构演变**：memoryGraph.js 移除、加权关键词评分 Layer 2、平台适配器（Cursor/Claude Code/Codex）、ATP 代理交易协议、集中配置、Self-PR 质量门禁 |
| [55](./55-signals-v166-three-layer-extraction.md) | **signals.js v1.66 三层信号提取**：Layer 1 `_extractRegex` 高频词+工具调用正则 / Layer 2 `_extractKeywordScore` 加权评分（frequency×recency×tool×domain×novelty）/ Layer 3 `_extractLLM` 语义评分 / `_mergeSignals` 四路归一化合并 / `analyzeRecentHistory` 历史频率感知 / v1.47→v1.66 四文件混淆（memoryGraphAdapter/candidates/narrativeMemory/candidateEval 均从百行纯文本压缩为单行混淆） |
| [73](./73-reality-check-three-layer-signals-and-new-opportunity-signals.md) | **三层信号提取架构现实核查 + 新机会信号**：`signals.js` v1.78.1（444行）源码确认 Layer 1/2/3 真实存在；⚠️ Doc 56 结论错误（v1.39 旧代码≠v1.48+新代码）；`SIGNAL_PROFILES` 加权评分（累积 evidence → 阈值触发）；`_extractLLM` Hub 调用（每 5 cycle 节流）；`execFileSync` argv 防注入；7 个新机会信号（`issue_already_resolved`/`openclaw_self_healed`/`empty_cycle_loop_detected`/`explore_opportunity`/`hub_search_miss_with_problem`/`plateau_pivot_required`/`plateau_pivot_suggested`）；CE 借鉴：三层提取模式 / 防注入 / 新信号 |
| **[74](./74-curriculum-mutation-closed-loop-pipeline.md)** | **Curriculum + Mutation 闭环管线**（`curriculum.js` 163行能力边界探测（aggregateOutcomes 200条滑动窗口 / identifyFrontier mastered≥0.8/failing≤0.3/frontier / generateCurriculumSignals 两层优先）/ `mutation.js` 186行5层决策树（repair>drift>opportunity>strategy>optimize / 9种Opportunity Signals含`self_healed`/`idle_cycle_detected`/`issue_already_resolved`「无为」检测 / 人格安全降级 isHighRiskMutationAllowed / strategy.js 7种preset联动 / CE P0/P1/P2） |

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
| [27](./27-ops-suite-runtime-config-canary.md) | **运维层深度**：Ops 模块套件（lifecycle / skills_monitor / cleanup / trigger / health_check）+ 集中配置 `config.js` + Canary 安全网 |
| [28](./28-prompt-engineering-deep-dive.md) | **Prompt 工程深度**：严格 Schema 定义 + 敏感数据参数化 + 技能创建质量门禁 + 截断策略精确实现 |
| [29](./29-signal-extraction-history-dedup-saturation.md) | **Signal 提取深度**：`analyzeRecentHistory` 历史感知、频率抑制、连续修复检测、空转饱和降级、失败连击干预、多语言需求提取、工具绕行检测 |
| [30](./30-multifactor-gene-selection-continuous-drift.md) | **多因子选择深度**：四因子评分叠加、`1/√Ne` 连续漂移强度、diversity-directed drift、Failed Capsule ban、anti-pattern 惩罚 > 成功奖励 |
| [31](./31-reflection-remote-adapter-local-state.md) | **自省 / 远程适配器 / 状态感知**：自适应自省间隔、人格微调、本地优先远程同步、三层自调节架构综合 |
| [59](./59-reflection-js-module-deep-dive.md) | **`reflection.js` 源码级深度**：computeReflectionInterval 三态算法（3/5/8）、shouldReflect 双重条件（周期对齐+冷却30min）、预聚合统计（intent分布/gene频率）、5问战略复盘框架与精确JSON输出格式、`buildSuggestedMutations` 信号→参数映射（creativity/rigor/risk_tolerance）、JSONL读写机制、与innovation.js功能/参数二级互补、CE自我诊断框架与元级SummaryEntity提案 |
| [60](./60-evolver-ops-self-healing-infrastructure.md) | **Ops 自我修复基础设施**：7大 ops 模块（lifecycle/cleanup/self_repair/skills_monitor/health_check/trigger/commentary）/ Dual-mode 设计（独立CLI + npm模块）/ 进程管理+双保险清理+Git四层自愈+技能健康检查+自愈+系统资源监控+即时唤醒+人格化评语 / 与主循环外部解耦 / CE借鉴 P0/P1/P2 |
| [61](./61-sanitize-privacy-pipeline-deep-dive.md) | **脱敏 + 隐私计算管线**：sanitize.js 双模式（替换 vs 检测）/ 14种 REDACT_PATTERNS + 17种 LEAK_SCANNERS / 逆向检测 detectEnvValueLeaks / crypto.js AES-256-GCM / privacyClient 六步管线 / HUB_EVENT_SIGNALS 隐私分类表 / Solidify 集成序列 / CE 脱敏设计建议 |
| [32](./32-v146-147-multiagent-session-sse-swarm.md) | **v1.46–v1.47 深度**：多 Agent 会话格式兼容（Claude Code/Cursor/Codex/Manus）、SSE 事件流自动重连、35+ HUB_EVENT_SIGNALS（蜂群 PDRI/隐私/议会）、EvoMap-First 自适应搜索 |
| [54](./54-session-source-arch-v147.md) | **v1.47 Session Source**：四模式路由（auto/cursor/openclaw/merge）/ `readOpenClawSessions` 专用函数 / `collectTranscriptFiles` 递归 walk（depth 3）/ vibe feature 删除 |
| [33](./33-v148-v166-architecture-evolution.md) | **v1.48–v1.66 架构演变**：memoryGraph.js 移除、加权关键词评分 Layer 2、平台适配器（Cursor/Claude Code/Codex）、ATP 代理交易协议、集中配置、Self-PR 质量门禁 |
| [55](./55-signals-v166-three-layer-extraction.md) | **signals.js v1.66 三层信号提取**：Layer 1 `_extractRegex` 高频词+工具调用 / Layer 2 `_extractKeywordScore` 加权评分 / Layer 3 `_extractLLM` 语义评分 / v1.47→v1.66 四文件混淆 |
| [34](./34-solidify-pipeline-end-to-end.md) | **Solidify 管线端到端**：从 state 恢复到 Hub 反馈的完整流程、PRM 多步骤评分、Content-addressable ID、ValidationReport/ExecutionTrace 标准化、CE 借鉴要点 |
| [35](./35-a2a-protocol-asset-lifecycle-feedback.md) | **A2A 协议 / 资产生命周期 / 反馈环路**：消息类型、发布资格三重门禁、Pre-publish leak check、Provenance chain、Task receiver、Hub review、CE 借鉴要点 |
| [37](./37-signal-taxonomy-gene-selection-end-to-end.md) | **Signal Taxonomy + Gene Selection 端到端**：signal 生命周期、标签扩展函数、规范化错误签名、Gene 四因子评分（exact+semantic+learning+drift）、Capsule 选择与 Ban、Mutation category 决策链、CE 借鉴要点 |
| [38](./38-env-fingerprint-capability-match.md) | **EnvFingerprint + CapabilityMatch**：环境指纹捕获（`captureEnvFingerprint`）、跨环境 GDI 测量、`envFingerprintKey` 同类判断；taskReceiver `estimateCapabilityMatch`（Jaccard + Laplace + 60/40 加权）、难度估算、承诺截止时间；CE `ObservationEntity` runtime_env 字段建议 |
| [40](./40-failure-mode-classification-and-canary.md) | **Failure Mode Classification + Canary**：`classifyFailureMode` 五级分类树（hard/soft × reasonClass）/ `runCanaryCheck` 提交前最后关卡 / `buildSoftFailureLearningSignals` 失败→信号标签 / `isValidationCommandAllowed` 命令白名单 / `compareBlastEstimate` 预估反馈；CE 健康检查 / 多级健康状态 / ApplicationRunner canary 方案 |
| [42](./42-policycheck-constraint-system-deep-dive.md) | **policyCheck.js 约束系统深度**：`isConstraintCountedPath` 路径匹配决策树（优先级 excludePrefix → includePrefix → extension）/ `computeBlastRadius`（git numstat + untracked + baseline 对比）/ `classifyBlastSeverity` 5级分类（hard_cap_breach / critical_overrun / exceeded / approaching_limit / within_limit）/ `isValidationCommandAllowed` 验证命令白名单（禁止 node -e/shell 操作符）/ 伦理模式 5种 regex 检测 / `detectDestructiveChanges` 关键文件删除/清空检测 / `checkConstraints` 主入口 |
| [41](./41-device-identity-and-innovation-catalyst.md) | **Device Identity + Innovation Catalyst**：deviceId.js 7层 fallback 设备标识 / `isContainer()` 容器检测 / 双路径持久化 / `_cachedDeviceId` 单例缓存；innovation.js 弱领域驱动创意生成 / CE instance_id 落点建议 / 功能发现借鉴 |
| [39](./39-content-addressable-asset-system.md) | **Content-addressable Asset System**：`contentHash.js`（Canonical JSON + SHA-256 + 完整性验证）+ `assetStore.js`（原子写入、基因/胶囊/候选人持久化）+ `candidates.js` / `candidateEval.js`（候选人提取与评估管线）；CE 观察去重 / 完整性验证 / 规范化 embedding 方案 |
| **[62](./62-evolver-core-design-patterns-and-ce-translation.md)** | **5 个核心设计模式提炼 + CE 翻译路径**：Append-only 事件溯源 + 状态投影 / Signal Enrichment Pipeline（Stage 1→2→3）/ Outcome 推断三层策略 / Content-Addressable Asset 生命周期 / Provider Resolution + Local-First Adapter Pattern；P0/P1/P2 翻译优先级矩阵 |
| **[69](./69-v148-v178-major-new-subsystems-and-version-gap-analysis.md)** | **v1.48–v1.78 重大新子系统**：版本差距概览（+3645/-9156行）/ skill2gep.js 逆向蒸馏（645行）+ selfPR.js 自动贡献（408行）+ validator/ 沙箱子系统（+~900行）+ portable.js + claimNudge.js + mailboxTransport.js / 核心模块精简统计 / 版本里程碑 / 接力建议 |
| **[64](./64-hub-selector-feedback-and-skilldistiller-validate-v147.md)** | **Hub-Selector 反馈闭环（v1.47 实际）+ validateSynthesizedGene 11道验证门 + shouldDistill 门禁 + capability candidate 管线核查 + Hub Events 7分类 35+ 信号全图**（`noveltyScore < 0.3` 扩展探索机制 / `diversity_directed` drift 算法 / Hub Events 注入 `signals.unshift()` 高优先级 / CE P0/P1 借鉴路径） |
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
| Hub / A2A / 目录 | [04](./04-mutation-through-policy-v09.md) 起；[05](./05-sanitize-through-execution-trace-v10.md)–[07](./07-idle-through-skillpublisher-v14.md)；**Hub 集成层**（taskReceiver + hubReview + issueReporter + a2a）[46](./46-hub-ecosystem-integration-taskreview-issue.md) |
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
| **`error_sig_norm` 落地** | [22](./22-error-sig-norm-implementation-proposal.md) |
| **PRM 多步骤评分 / Epigenetic Marks** | [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) §1–§2 |
| **自适应策略 / 候选评估 / 自修复** | [26](./26-runtime-orchestration-adaptive-policy-candidates.md) |
| **Anti-Pattern Zone / Failed Capsules** | [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) §3；**选择器 ban** [30](./30-multifactor-gene-selection-continuous-drift.md) §4 |
| **Innovation Catalyst / 停滞检测** | [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) §5；**信号级饱和降级** [29](./29-signal-extraction-history-dedup-saturation.md) §2.3 |
| **Adaptive Reflection / 自省循环** | [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) §7；**自适应间隔 + 人格微调** [31](./31-reflection-remote-adapter-local-state.md) §1 |
| **信号去重 / 连续修复 / 空转饱和** | [29](./29-signal-extraction-history-dedup-saturation.md)（`analyzeRecentHistory`、频率抑制、失败连击） |
| **多因子 Gene 选择 / 连续漂移** | [30](./30-multifactor-gene-selection-continuous-drift.md)（四因子评分、`1/√Ne`、diversity-directed drift） |
| **Signal Taxonomy 全链路 + Gene 四因子叠加** | [37](./37-signal-taxonomy-gene-selection-end-to-end.md)（extractSignals → expandSignals → scoreGene → selectGene；规范化错误签名；Capsule Ban；Mutation category 决策链） |
| **远程适配器 / 本地优先写入** | [31](./31-reflection-remote-adapter-local-state.md) §2（`memoryGraphAdapter`、withFallback） |
| **Prompt 工程架构（多层上下文注入）** | [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) §8；**深度**（Schema / 质量门禁 / 截断策略）见 [28](./28-prompt-engineering-deep-dive.md) |
| **主入口 Daemon Loop + CLI**（singleton lock / 自适应休眠 / suicide重启 / OMLS空闲调度 / Hub心跳独立运行 / 幂等双状态机） | [53](./53-main-daemon-loop-cli-architecture.md) |
| **Ops 模块套件 / 集中配置 / Canary / Health Check**（运维基础设施） | [27](./27-ops-suite-runtime-config-canary.md) |
| **Ops 自我修复基础设施**（7大模块 / Dual-mode / 进程管理+清理+自愈+健康检查+唤醒+评语 / 与主循环外部解耦） | [60](./60-evolver-ops-self-healing-infrastructure.md) |
| **敏感数据参数化 / 技能创建质量门禁** | [28](./28-prompt-engineering-deep-dive.md) §3–§4；[27](./27-ops-suite-runtime-config-canary.md) §2（配置 env override） |
| **三层自调节架构综合** | [31](./31-reflection-remote-adapter-local-state.md) §5（Signal → Selection → Reflection 三层） |
| **环境指纹 / CapabilityMatch** | [38](./38-env-fingerprint-capability-match.md)（`envFingerprintKey` 同类判断、跨环境 GDI；Jaccard+successRate 任务匹配；CE runtime_env 字段建议） |
| **远程适配器模式（本地优先 + fallback）** | [31](./31-reflection-remote-adapter-local-state.md) §2；**本地源码** [18](./18-evolver-local-source-memory-architecture-snapshot.md) §6 |
| **Solidify 管线端到端**（状态恢复→约束→验证→PRM→Capsule→发布→反馈） | [34](./34-solidify-pipeline-end-to-end.md) |
| **Content-addressable ID / Atomic write / 验证报告**（资产持久化层） | [34](./34-solidify-pipeline-end-to-end.md) §3–§5 |
| **Failure Mode Classification + Canary**（classifyFailureMode 五级分类树 / canary 健康检查 / 失败信号标签 / blast radius 预估反馈 / CE 健康检查方案） | [40](./40-failure-mode-classification-and-canary.md)（`policyCheck.js` 深度补充：hard/soft failure × reasonClass / 命令白名单 / 伦理检测） |
| **policyCheck 约束系统深度**（路径匹配决策树 / git numstat blast radius / 5级 severity / 验证命令白名单 / 伦理 regex 检测 / 关键文件破坏检测） | [42](./42-policycheck-constraint-system-deep-dive.md)（15个导出函数完整分析；配置驱动安全策略设计） |
| **Privacy Computing + Hub Ecosystem**（AES-256-GCM 密封工具 / 本地密钥管理 / 六策略问题生成 + 模糊去重 / 自动 GitHub Issue + 冷却去重 + 脱敏 / 人格 commentary 三模式） | [43](./43-privacy-computing-and-hub-ecosystem.md)（§1–§4：隐私计算管线 / 问题生成策略 / 自动报告机制 / Commentary 人格） |
| **脱敏 + 隐私计算管线**（sanitize 双模式替换 vs 检测 / 14种REDACT_PATTERNS+17种LEAK_SCANNERS / detectEnvValueLeaks 逆向检测 / crypto AES-256-GCM / HUB_EVENT_SIGNALS 隐私分类） | [61](./61-sanitize-privacy-pipeline-deep-dive.md) |
| **Personality State Machine + Hub Search**（五维人格状态机 + 自然选择 + 三层突变叠加 + cap 保护 / Hub 两相搜索 + LRU 缓存 + deadline 控制 + 并行语义搜索） | [44](./44-personality-state-machine-and-hub-search-caching.md)（§1 人格状态机 / §2 Hub 两相搜索管线） |
| [46](./46-hub-ecosystem-integration-taskreview-issue.md) | **Hub Ecosystem Integration**：`taskReceiver.js` 三策略 ROI 评分（greedy/balanced/conservative）+ capability match（Jaccard + Laplace success rate）+ commitment deadline 估算；`hubReview.js` submitHubReview（rating 推导 1/2/4/5 星 / 本地去重 + 远程去重）；`issueReporter.js` 自动 GitHub issue（failure streak ≥5 触发 / SHA-256 error_key 去重 / 冷却 24h / 脱敏）；`a2a.js` 广播资格（capsule: score≥0.7 + blast safe + streak≥2）+ confidence 下调 factor=0.6；CE 借鉴：失败自动报告 / capability-based scoring / 外部资产 confidence 降级 |
| [64](./64-hub-selector-feedback-and-skilldistiller-validate-v147.md) | **Hub-Selector 反馈闭环（v1.47 实际）+ validateSynthesizedGene 11道验证门 + shouldDistill 门禁 + capability candidate 管线核查 + Hub Events 7分类 35+ 信号全图**（`noveltyScore < 0.3` 扩展探索机制 / `diversity_directed` drift 算法 / Hub Events 注入 `signals.unshift()` 高优先级 / CE P0/P1 借鉴路径） |
| [65](./65-selector-gene-scoring-and-semantic-matching-deep-dive.md) | **Selector 基因评分与语义匹配深度**：三通道叠加（exact×1 + tag×0.6 + semantic×0.4）/ `matchPatternToSignals` 三策略（regex + 多语言别名 + 子串）/ Bag-of-Words Cosine（非 embedding）/ 表观遗传加成 / 反模式惩罚 / `1/√Ne` 漂移强度公式 / CE P0/P1/P2 路径 |
| [66](./66-memorygraph-event-model-confidence-edges-and-state-schema.md) | **MemoryGraph 事件模型完整解析**：7种 MemoryGraphEvent 完整类型谱 / `confidence_edge` (30天半衰) + `confidence_gene_outcome` (45天半衰) 置信边事件 / `edgeExpectedSuccess` Laplace+衰减公式 / `tryParseLastEvolutionEventOutcome` 从 JSONL 提取 outcome / `memory_graph_state.json` 完整 Schema（幂等保护+baseline快照）/ 外部候选隔离机制 / 完整 Cycle 时序图 / CE P0/P1/P2 路径 |
| [68](./68-post-solidify-pipeline-executiontrace-gitops-skillpublisher-questiongen-a2a.md) | **Post-Solidify 完整管线**：executionTrace.js 三级脱敏轨迹（none/minimal/standard）+ 文件路径/错误签名脱敏 / gitOps.js 保护区+三种回滚模式（none/hard/stash）+ 路径穿越防护 / skillPublisher.js Gene→SKILL.md 市场级输出+sanitizeSkillName 时间戳剥离 / questionGenerator.js 六策略主动求援+模糊去重+3小时速率限制 / a2a.js Capsule广播资格（score≥0.7+blast safe+streak≥2）+ 置信度降权（×0.6）+ JSONL解析 / 端到端管线全图 / CE P0/P1/P2 路径 |
| [63](./63-hub-selector-feedback-loop-and-skilldistiller-validate-deep-dive.md) | **Hub-Selector 反馈闭环 + validateSynthesizedGene**：Hub心跳 `_latestNoveltyHint`/`_latestCapabilityGaps` 解析链路（a2aProtocol.js 658行）/ 三重反馈（信号层→选择层→探索层）/ `noveltyScore < 0.3` 扩大探索范围 +1 机制 / `capabilityGaps`→ diversity_directed drift 覆盖度排序 / 五类漂移模式对比 / `validateSynthesizedGene` 5大门禁（结构完整性/ID唯一性/信号重叠检测/安全约束/.git-node_modules保护/危险命令净化）/ CE网络级反馈机制借鉴 |
| **Device Identity + Innovation Catalyst**（deviceId 7层 fallback 标识 / 容器检测 / 双路径持久化 / 弱领域驱动创意生成） | [41](./41-device-identity-and-innovation-catalyst.md)（`deviceId.js` + `innovation.js`） |
| **Content-addressable Asset System**（contentHash / assetStore / candidates / candidateEval；Canonical JSON + SHA-256 + 原子写入） | [39](./39-content-addressable-asset-system.md)（资产层完整管线；候选人三大来源；CE 观察去重 fingerprint / 完整性 hash 验证 / 规范化 embedding） |
| **A2A 协议 / 资产发布 / 反馈环路**（hello/publish/fetch/review/task） | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) |
| **Leak check / 脱敏**（发布前安全扫描） | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) §2.3–§2.4；**脱敏规则** [28](./28-prompt-engineering-deep-dive.md) §3 |
| **Provenance chain / 资产溯源**（parent 链） | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) §2.5, §5.3 |
| **5 个核心设计模式提炼 + CE 翻译优先级** | [62](./62-evolver-core-design-patterns-and-ce-translation.md)（Append-only + Signal Enrichment + Outcome 推断 + Content-Addressable Asset + Provider Resolution Adapter）|
| **记忆系统架构综合（总览 / 三层记忆 / 反馈环路 / 适配器模式 / 8 大设计原则）** | [36](./36-memory-architecture-synthesis.md) |
| **MemoryGraphAdapter 本地优先写入 / 远程降级** | [36](./36-memory-architecture-synthesis.md) §3 |
| **三层记忆 + 状态文件是图的缓存** | [36](./36-memory-architecture-synthesis.md) §1 |
| **Error Signature 规范化 + stableHash** | [36](./36-memory-architecture-synthesis.md) §2.2 |
| **Signal Taxonomy → 标签扩展 → 评分** | [36](./36-memory-architecture-synthesis.md) §2.3 |
| **反馈环路端到端**（signal→gene→outcome→memory） | [36](./36-memory-architecture-synthesis.md) §4 |
| **Jaccard 0.34 + 半衰衰减 + drift 1/√Ne** | [36](./36-memory-architecture-synthesis.md) §4.1–4.3 |
| **信号去重 / 饱和降级 / 失败连击** | [36](./36-memory-architecture-synthesis.md) §5 |
| **自适应反思间隔**（成功 8 / 失败 3 / 默认 5） | [36](./36-memory-architecture-synthesis.md) §6 |
| **BlueCortexCE P0/P1/P2 启示对照表** | [36](./36-memory-architecture-synthesis.md) §7 |
| **Evolver vs BlueCortexCE 本质差异（优化型 vs 记录型）** | [36](./36-memory-architecture-synthesis.md) §9 |

若同一 § 编号在版本增补中出现多次，以分片内**版本标注**为准；完整目录列表见 [01](./01-intro-toc-memory-through-curriculum.md)。

## 其他文件

| 文件 | 用途 |
|------|------|
| [AGENT.md](./AGENT.md) | 维护约定：单文件上限、索引优先、`CANONICAL.sha256` 何时更新 |
| [misc.md](./misc.md) | 暂未归类的短摘录 |
| [staging.md](./staging.md) | 极短草稿；定稿迁入 `0x`/`09`–`19`/`11` 等或删除（与 [`11`](./11-research-backlog.md) 可勾选队列区分） |

仓库根路径 [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 为上述入口的短链接，便于旧书签。
