# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**最后更新**：2026-04-25（新增 **`63` Hub-Selector 反馈闭环**（`_latestNoveltyHint`/`_latestCapabilityGaps` 解析链路 / Hub心跳→a2aProtocol→ Curriculum信号注入 / diversity_directed+random_weighted+random 三模式漂移 / noveltyScore 探索范围调节（<0.3 扩大 +1）/ `validateSynthesizedGene` 5类门禁（结构/唯一性/重叠检测/安全约束/命令白名单）/ CE网络级反馈机制 + 旁路验证门禁 + 探索性上下文注入方案）；**`58` v1.66 新架构分析**（三层信号（Layer 1 `_extractRegex` / Layer 2 加权评分 / Layer 3 LLM 语义）/ 新增 7 个 Opportunity Signals / Plateau 检测（avgScore < 0.35 + 无改善触发）/ 平台适配器系统 `src/adapters/`（hookAdapter + Cursor/Claude Code/Codex 三平台）/ ATP（Agent Trading Protocol）Hub market 机制（placeOrder/deliver）/ Self-PR 自动提交（多层门禁：score+streak+leak+diff dedup+cooldown）/ 主要模块精简（evolve.js -2176行 / memoryGraph.js -788行 / a2aProtocol.js -1222行）/ CE P0/P1/P2 借鉴路径）；**`57` privacyClient + crypto.js + HUB_EVENT_SIGNALS 全链路**（crypto.js AES-256-GCM 加密原语（iv/authTag/pack布局）/ privacyClient.js 6个API端点（submit→upload→execute→poll→retrieve→decrypt完整管线）/ key不离本地设计原则 / taskReceiver.js PRIVACY_PARAMS 解析块 / evolve.js 35+ HUB_EVENT_SIGNALS 完整分类表（dialog/governance/swarm/privacy/review/knowledge） / CE P0/P1/P2借鉴路径）；**`56` signals.js 现实核查（⚠️ Doc 55 准确性修正）**（signals.js 实际为 v1.39/v1.47 单层regex，非 doc 55 声称的 v1.66 三层提取 / git log确认最后更新为 fbca5ab v1.39.0 / 实际单层架构：analyzeRecentHistory历史抑制+饱和检测+失败连击+多语言 / Layer 2/3/SIGNAL_PROFILES 均不存在 / CE借鉴路径：history-aware suppression + saturation detection）；**`55` signals.js v1.66 三层信号提取架构（Layer 1 regex高频词+工具调用 / Layer 2 加权关键词评分（frequency×recency×tool×domain×novelty）/ Layer 3 LLM语义评分 / `_mergeSignals` 四路归一化合并 / v1.47→v1.66 四文件混淆：`memoryGraphAdapter`/`candidates`/`narrativeMemory`/`candidateEval` 均从百行纯文本压缩为单行混淆）；**`53` main daemon loop + CLI 架构**（index.js 754行全面解析：6个CLI命令路由 / singleton lock / 自适应休眠三层乘数叠加 / suicide内存保护 / OMLS空闲窗口+主动蒸馏 / Hub心跳独立运行 / 幂等双状态机 / CE P0/P1/P2借鉴路径）；**`52` 信号处理与知识表示模块深度分析**（`learningSignals.js` 三层标签扩展（problem/action/area/risk）/ `geneTags` + `scoreTagOverlap` 基因选择评分 / `candidates.js` 三来源候选提取（高频工具调用/信号映射/失败胶囊聚类）/ Five Questions Shape 标准化 / `narrativeMemory.js` 有界叙事记忆（30条+12KB上限+8条摘要）/ `paths.js` Session Scope 隔离 / CE P0/P1/P2 借鉴路径）；**`51` Capability Candidate 生命周期管线**（五阶段：提取→评估→Hub匹配→技能提炼→悬赏提问 / 三来源候选提取 / 两阶段Hub搜索+LRU缓存 / 六策略问题生成+模糊去重 / CE P0/P1/P2 借鉴路径）；**`50` MemoryGraph 闭环反馈架构**（六类事件时序 / outcome 推断三层策略 / 边权重 Laplace+半衰衰减 / Jaccard 信号规范化匹配 / Ban 规则 / Dormant Hypothesis 中断恢复 / Idle Gating 完整条件 / Session Scope 多租户隔离 / 完整 cycle 调用链）；**`49` localStateAwareness + evolve.js Full Integration**（`localStateAwareness.js` 五类快照模块（Node Identity / Env Config / Evolution State / Memory State / Skills）+ 自模型在 prompt 中的注入机制；`evolve.js` 完整记忆调用链（Signal提取→Advice→Snapshot→Hypothesis→Attempt→Outcome→Narrative→Reflection）+ JSONL 事件序列；State+Event 双写模式 + outcome 锚定 last_action；Hub 饱和节流 `shouldSkipHubCalls`；CE 借鉴路径）；**`48` Gene as Compressed Memory + 完整闭环架构**（三区分类课程系统 / 三级脱敏执行轨迹 / 标准化 ValidationReport / GitOps 保护与回滚 / LLM 驱动技能提炼管线 + Marketplace SKILL.md 生成 / CE 高/中/低优先级借鉴路径）；**`46` Hub Ecosystem Integration**（taskReceiver ROI 评分 + capability match / hubReview 使用后 review 提交 + 本地去重 / issueReporter 自动 GitHub issue + cooldown + 脱敏 / a2a 广播资格 + confidence 下调 / **directoryClient.js Hub 目录 API 客户端**（语义搜索 + 信号搜索 + Agent Profile + discoverForTask）/ CE 借鉴路径）；**`45` idleScheduler + llmReview**（OMLS 启发式自适应休眠调度 / LLM 驱动代码评审 / 自适应调度 + LLM 评审协同 / CE 借鉴路径）；**`43` Privacy Computing + Hub Ecosystem**（AES-256-GCM 密封工具 / 本地密钥管理 / 隐私块嵌入 / 六策略问题生成 + 模糊去重 / 自动 GitHub Issue + 冷却去重 + 脱敏 / 人格commentary三模式）；**`44` Personality State Machine + Hub Search**（五维人格状态机 + 自然选择 + 三层突变叠加 / Hub两相搜索管线 + LRU缓存 + deadline控制 + 语义并行）；**`42` policyCheck.js 约束系统深度分析**，覆盖 isConstraintCountedPath 路径匹配决策树 / computeBlastRadius / classifyBlastSeverity 5级分类 / 验证命令白名单 / 伦理模式 5种 regex 检测；**`41` Device Identity + Innovation Catalyst**，覆盖 deviceId.js 7层 fallback 设备标识 / 容器检测 / 双路径持久化 / envFingerprint 关系）

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
| **`localStateAwareness` + `evolve.js` 全链路**（五类自模型快照 / 完整记忆调用链 / State+Event 双写 / Hub 饱和节流） | [`49`](./49-localStateAwareness-self-model-evolve-loop-full-integration.md) |
| **主入口 Daemon Loop + CLI 架构**（index.js 754行：6命令路由 / singleton lock / 自适应休眠三层乘数 / suicide内存保护 / OMLS主动蒸馏 / Hub心跳独立运行） | [`53`](./53-main-daemon-loop-cli-architecture.md) |
| **MemoryGraph 闭环反馈架构**（六类事件时序 / outcome 推断三层策略 / 边权重 Laplace+半衰衰减 / Jaccard 信号规范化匹配 / Ban 规则 / Dormant Hypothesis 中断恢复 / Idle Gating 完整条件 / Session Scope 多租户隔离 / 完整 cycle 调用链） | [`50`](./50-memory-graph-closed-loop-architecture.md) |
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
| **signals.js 现实核查**（signals.js 实际为 v1.39/v1.47 单层regex；doc 55 Layer 2/3/SIGNAL_PROFILES 不存在；git log fbca5ab 确认；实际：analyzeRecentHistory历史抑制+饱和检测+失败连击+多语言+基因ban） | [`56`](./56-signals-reality-check-v147.md) |
| **v1.66 新架构分析**（三层信号（Layer 1 regex / Layer 2 加权评分 / Layer 3 LLM 语义）/ 7个新 Opportunity Signals / Plateau 检测（avgScore < 0.35 + 无改善）/ `src/adapters/` 统一跨平台 Hook / ATP Hub market（placeOrder/deliver / fastest/cheapest/auction/swarm）/ Self-PR 多层门禁（score+streak+leak+diff dedup+cooldown）/ 主要模块精简（evolve -2176行 / memoryGraph -788行 / a2aProtocol -1222行） | [`58`](./58-v166-new-architecture-three-layer-signals-atp-selfpr.md) |
| **多因子 Gene 选择 / 连续漂移 / diversity-directed drift / Failed Capsule ban**（选择器深度） | [`30`](./30-multifactor-gene-selection-continuous-drift.md) |
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
