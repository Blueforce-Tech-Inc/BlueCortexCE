# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**最后更新**：2026-04-24（新增 **`47` Curriculum + ExecutionTrace + SkillDistiller**（三区分类课程系统 / 三级脱敏执行轨迹 / 标准化 ValidationReport / GitOps 保护与回滚 / LLM 驱动技能提炼管线 + Marketplace SKILL.md 生成 / CE 高/中/低优先级借鉴路径）；**`46` Hub Ecosystem Integration**（taskReceiver ROI 评分 + capability match / hubReview 使用后 review 提交 + 本地去重 / issueReporter 自动 GitHub issue + cooldown + 脱敏 / a2a 广播资格 + confidence 下调 / **directoryClient.js Hub 目录 API 客户端**（语义搜索 + 信号搜索 + Agent Profile + discoverForTask）/ CE 借鉴路径）；**`45` idleScheduler + llmReview**（OMLS 启发式自适应休眠调度 / LLM 驱动代码评审 / 自适应调度 + LLM 评审协同 / CE 借鉴路径）；**`43` Privacy Computing + Hub Ecosystem**（AES-256-GCM 密封工具 / 本地密钥管理 / 隐私块嵌入 / 六策略问题生成 + 模糊去重 / 自动 GitHub Issue + 冷却去重 + 脱敏 / 人格commentary三模式）；**`44` Personality State Machine + Hub Search**（五维人格状态机 + 自然选择 + 三层突变叠加 / Hub两相搜索管线 + LRU缓存 + deadline控制 + 语义并行）；**`42` policyCheck.js 约束系统深度分析**，覆盖 isConstraintCountedPath 路径匹配决策树 / computeBlastRadius / classifyBlastSeverity 5级分类 / 验证命令白名单 / 伦理模式 5种 regex 检测；**`41` Device Identity + Innovation Catalyst**，覆盖 deviceId.js 7层 fallback 设备标识 / 容器检测 / 双路径持久化 / envFingerprint 关系）

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
| **Gene Pool + Selector + Mutation + Strategy Presets**（Gene/Strategy 层新发现） | [`24`](./24-gene-strategy-layer.md) |
| **PRM 评分 / Epigenetic / Anti-Pattern / Innovation / Reflection**（高级模式） | [`25`](./25-advanced-patterns-prm-epigenetic-antipattern.md) |
| **自适应策略 / 候选评估 / Git 自修复 / 创新催化 / 自我感知**（运行时编排） | [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) |
| **Device Identity + Innovation Catalyst**（deviceId 7层 fallback / 容器检测 / 双路径持久化 / innovation 弱领域驱动创意） | [`41`](./41-device-identity-and-innovation-catalyst.md) |
| **Privacy Computing + Hub Ecosystem**（AES-256-GCM 密封工具 / 本地密钥管理 / 六策略问题生成 + 模糊去重 / 自动 GitHub Issue + 冷却去重 + 脱敏 / 人格 commentary 三模式） | [`43`](./43-privacy-computing-and-hub-ecosystem.md) |
| **Personality State Machine + Hub Search**（五维人格状态机 + 自然选择 + 三层突变叠加 + cap 保护 / Hub 两相搜索 + LRU 缓存 + deadline 控制 + 并行语义搜索） | [`44`](./44-personality-state-machine-and-hub-search-caching.md) |
| **Curriculum + ExecutionTrace + SkillDistiller**（三区分类课程系统 / 三级脱敏执行轨迹 / ValidationReport + content-hash / GitOps 保护与回滚 / LLM 驱动技能提炼 + Marketplace SKILL.md 生成） | [`47`](./47-curriculum-executiontrace-skill-distillation.md) |
| **Hub Ecosystem Integration**（taskReceiver 三策略 ROI 评分 + capability match / hubReview review 提交 + 本地去重 / issueReporter 自动 GitHub issue + cooldown / a2a 广播资格） | [`46`](./46-hub-ecosystem-integration-taskreview-issue.md) |
| **Ops 模块套件 / 集中配置 / Canary 安全网 / Health Check**（运维基础设施） | [`27`](./27-ops-suite-runtime-config-canary.md) |
| **Prompt Schema / 质量门禁 / 敏感数据参数化 / 截断策略**（提示词深度） | [`28`](./28-prompt-engineering-deep-dive.md) |
| **Signal 提取 / 历史去重 / 饱和降级 / 多语言 / 工具绕行**（信号深度） | [`29`](./29-signal-extraction-history-dedup-saturation.md) |
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
| [23](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md) | **State+Event 双层架构**：可变 State 文件 + 不可变 JSONL 事件、幂等 outcome 写入、自省循环（Reflection Phase）、localStateAwareness 自模型 |
| [24](./24-gene-strategy-layer.md) | **Gene/Strategy 层**：Gene Pool + 多因子选择器（exact+semantic+epigenetic+learning）+ Strategy Presets（repair/optimize/innovate）+ Mutation 安全约束 + Candidates Pool |
| [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) | **高级模式**：PRM 多步骤评分 + Epigenetic Marks + Failed Capsules / Anti-Pattern Zone + Lessons/Principles Block + Innovation Catalyst + Adaptive Reflection + Prompt 工程架构 + A2A Auto-Publish |
| [26](./26-runtime-orchestration-adaptive-policy-candidates.md) | **运行时编排**：自适应策略策略 + Blast Radius 动态控制 + 候选评估管线 + Git 自修复 + 创新催化 + 本地状态感知 |
| [27](./27-ops-suite-runtime-config-canary.md) | **运维层深度**：Ops 模块套件（lifecycle / skills_monitor / cleanup / trigger / health_check）+ 集中配置 `config.js` + Canary 安全网 |
| [28](./28-prompt-engineering-deep-dive.md) | **Prompt 工程深度**：严格 Schema 定义 + 敏感数据参数化 + 技能创建质量门禁 + 截断策略精确实现 + 常见失败模式 |
| [29](./29-signal-extraction-history-dedup-saturation.md) | **Signal 提取深度**：`analyzeRecentHistory` 历史感知、频率抑制、连续修复检测、空转饱和降级、失败连击干预、多语言需求提取、工具绕行检测 |
| [30](./30-multifactor-gene-selection-continuous-drift.md) | **多因子选择深度**：四因子评分叠加、`1/√Ne` 连续漂移强度、diversity-directed drift、Failed Capsule ban、anti-pattern 惩罚 > 成功奖励 |
| [31](./31-reflection-remote-adapter-local-state.md) | **自省 / 远程适配器 / 状态感知**：自适应自省间隔、人格微调、本地优先远程同步、三层自调节架构综合 |
| [32](./32-v146-147-multiagent-session-sse-swarm.md) | **v1.46–v1.47 深度**：多 Agent 会话格式兼容（Claude Code/Cursor/Codex/Manus）、SSE 事件流自动重连、35+ HUB_EVENT_SIGNALS（蜂群 PDRI/隐私/议会）、EvoMap-First 自适应搜索 |

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
| [32](./32-v146-147-multiagent-session-sse-swarm.md) | **v1.46–v1.47 深度**：多 Agent 会话格式兼容（Claude Code/Cursor/Codex/Manus）、SSE 事件流自动重连、35+ HUB_EVENT_SIGNALS（蜂群 PDRI/隐私/议会）、EvoMap-First 自适应搜索 |
| [33](./33-v148-v166-architecture-evolution.md) | **v1.48–v1.66 架构演变**：memoryGraph.js 移除、加权关键词评分 Layer 2、平台适配器（Cursor/Claude Code/Codex）、ATP 代理交易协议、集中配置、Self-PR 质量门禁 |
| [34](./34-solidify-pipeline-end-to-end.md) | **Solidify 管线端到端**：从 state 恢复到 Hub 反馈的完整流程、PRM 多步骤评分、Content-addressable ID、ValidationReport/ExecutionTrace 标准化、CE 借鉴要点 |
| [35](./35-a2a-protocol-asset-lifecycle-feedback.md) | **A2A 协议 / 资产生命周期 / 反馈环路**：消息类型、发布资格三重门禁、Pre-publish leak check、Provenance chain、Task receiver、Hub review、CE 借鉴要点 |
| [37](./37-signal-taxonomy-gene-selection-end-to-end.md) | **Signal Taxonomy + Gene Selection 端到端**：signal 生命周期、标签扩展函数、规范化错误签名、Gene 四因子评分（exact+semantic+learning+drift）、Capsule 选择与 Ban、Mutation category 决策链、CE 借鉴要点 |
| [38](./38-env-fingerprint-capability-match.md) | **EnvFingerprint + CapabilityMatch**：环境指纹捕获（`captureEnvFingerprint`）、跨环境 GDI 测量、`envFingerprintKey` 同类判断；taskReceiver `estimateCapabilityMatch`（Jaccard + Laplace + 60/40 加权）、难度估算、承诺截止时间；CE `ObservationEntity` runtime_env 字段建议 |
| [40](./40-failure-mode-classification-and-canary.md) | **Failure Mode Classification + Canary**：`classifyFailureMode` 五级分类树（hard/soft × reasonClass）/ `runCanaryCheck` 提交前最后关卡 / `buildSoftFailureLearningSignals` 失败→信号标签 / `isValidationCommandAllowed` 命令白名单 / `compareBlastEstimate` 预估反馈；CE 健康检查 / 多级健康状态 / ApplicationRunner canary 方案 |
| [42](./42-policycheck-constraint-system-deep-dive.md) | **policyCheck.js 约束系统深度**：`isConstraintCountedPath` 路径匹配决策树（优先级 excludePrefix → includePrefix → extension）/ `computeBlastRadius`（git numstat + untracked + baseline 对比）/ `classifyBlastSeverity` 5级分类（hard_cap_breach / critical_overrun / exceeded / approaching_limit / within_limit）/ `isValidationCommandAllowed` 验证命令白名单（禁止 node -e/shell 操作符）/ 伦理模式 5种 regex 检测 / `detectDestructiveChanges` 关键文件删除/清空检测 / `checkConstraints` 主入口 |
| [41](./41-device-identity-and-innovation-catalyst.md) | **Device Identity + Innovation Catalyst**：deviceId.js 7层 fallback 设备标识 / `isContainer()` 容器检测 / 双路径持久化 / `_cachedDeviceId` 单例缓存；innovation.js 弱领域驱动创意生成 / CE instance_id 落点建议 / 功能发现借鉴 |
| [39](./39-content-addressable-asset-system.md) | **Content-addressable Asset System**：`contentHash.js`（Canonical JSON + SHA-256 + 完整性验证）+ `assetStore.js`（原子写入、基因/胶囊/候选人持久化）+ `candidates.js` / `candidateEval.js`（候选人提取与评估管线）；CE 观察去重 / 完整性验证 / 规范化 embedding 方案 |
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
| **Ops 模块套件 / 集中配置 / Canary / Health Check**（运维基础设施） | [27](./27-ops-suite-runtime-config-canary.md) |
| **敏感数据参数化 / 技能创建质量门禁** | [28](./28-prompt-engineering-deep-dive.md) §3–§4；[27](./27-ops-suite-runtime-config-canary.md) §2（配置 env override） |
| **三层自调节架构综合** | [31](./31-reflection-remote-adapter-local-state.md) §5（Signal → Selection → Reflection 三层） |
| **环境指纹 / CapabilityMatch** | [38](./38-env-fingerprint-capability-match.md)（`envFingerprintKey` 同类判断、跨环境 GDI；Jaccard+successRate 任务匹配；CE runtime_env 字段建议） |
| **远程适配器模式（本地优先 + fallback）** | [31](./31-reflection-remote-adapter-local-state.md) §2；**本地源码** [18](./18-evolver-local-source-memory-architecture-snapshot.md) §6 |
| **Solidify 管线端到端**（状态恢复→约束→验证→PRM→Capsule→发布→反馈） | [34](./34-solidify-pipeline-end-to-end.md) |
| **Content-addressable ID / Atomic write / 验证报告**（资产持久化层） | [34](./34-solidify-pipeline-end-to-end.md) §3–§5 |
| **Failure Mode Classification + Canary**（classifyFailureMode 五级分类树 / canary 健康检查 / 失败信号标签 / blast radius 预估反馈 / CE 健康检查方案） | [40](./40-failure-mode-classification-and-canary.md)（`policyCheck.js` 深度补充：hard/soft failure × reasonClass / 命令白名单 / 伦理检测） |
| **policyCheck 约束系统深度**（路径匹配决策树 / git numstat blast radius / 5级 severity / 验证命令白名单 / 伦理 regex 检测 / 关键文件破坏检测） | [42](./42-policycheck-constraint-system-deep-dive.md)（15个导出函数完整分析；配置驱动安全策略设计） |
| **Privacy Computing + Hub Ecosystem**（AES-256-GCM 密封工具 / 本地密钥管理 / 六策略问题生成 + 模糊去重 / 自动 GitHub Issue + 冷却去重 + 脱敏 / 人格 commentary 三模式） | [43](./43-privacy-computing-and-hub-ecosystem.md)（§1–§4：隐私计算管线 / 问题生成策略 / 自动报告机制 / Commentary 人格） |
| **Personality State Machine + Hub Search**（五维人格状态机 + 自然选择 + 三层突变叠加 + cap 保护 / Hub 两相搜索 + LRU 缓存 + deadline 控制 + 并行语义搜索） | [44](./44-personality-state-machine-and-hub-search-caching.md)（§1 人格状态机 / §2 Hub 两相搜索管线） |
| [46](./46-hub-ecosystem-integration-taskreview-issue.md) | **Hub Ecosystem Integration**：`taskReceiver.js` 三策略 ROI 评分（greedy/balanced/conservative）+ capability match（Jaccard + Laplace success rate）+ commitment deadline 估算；`hubReview.js` submitHubReview（rating 推导 1/2/4/5 星 / 本地去重 + 远程去重）；`issueReporter.js` 自动 GitHub issue（failure streak ≥5 触发 / SHA-256 error_key 去重 / 冷却 24h / 脱敏）；`a2a.js` 广播资格（capsule: score≥0.7 + blast safe + streak≥2）+ confidence 下调 factor=0.6；CE 借鉴：失败自动报告 / capability-based scoring / 外部资产 confidence 降级 |
| **Device Identity + Innovation Catalyst**（deviceId 7层 fallback 标识 / 容器检测 / 双路径持久化 / 弱领域驱动创意生成） | [41](./41-device-identity-and-innovation-catalyst.md)（`deviceId.js` + `innovation.js`） |
| **Content-addressable Asset System**（contentHash / assetStore / candidates / candidateEval；Canonical JSON + SHA-256 + 原子写入） | [39](./39-content-addressable-asset-system.md)（资产层完整管线；候选人三大来源；CE 观察去重 fingerprint / 完整性 hash 验证 / 规范化 embedding） |
| **A2A 协议 / 资产发布 / 反馈环路**（hello/publish/fetch/review/task） | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) |
| **Leak check / 脱敏**（发布前安全扫描） | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) §2.3–§2.4；**脱敏规则** [28](./28-prompt-engineering-deep-dive.md) §3 |
| **Provenance chain / 资产溯源**（parent 链） | [35](./35-a2a-protocol-asset-lifecycle-feedback.md) §2.5, §5.3 |
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
