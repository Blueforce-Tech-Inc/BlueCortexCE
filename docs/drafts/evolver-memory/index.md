# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**cron 巡检 2026-05-06 07:53**：目录 **124** 个 .md；最大 45633B；所有 < 50KB ✅；**backlog 全项已勾选 ✅**；backlog **0 项未决**；上游无增量（v1.47.0 `e72778e` 无新 commit）。新增 **`117`** `solidify.js` 核心深度（1344L纯JS / 8维PRM过程评分（constraint×0.25+validation×0.25最高权重）/ 三层验证门禁PolicyCheck→Canary→LLM Review / FailedCapsule rollback前diff零丢失捕获 / 表观遗传Marks+基因学习适配 / Anti-pattern opt-in发布 / LessonL轻量失败知识化 / CE P1多维质量评分+P1上下文三层验证+P1失败Context保存+P2表观遗传 ✅）和 **`118`** `localStateAwareness.js`+`analyzer.js` 深度（244L+60L / captureLocalState五维状态自发现快照 / analyzeFailures元学习从MEMORY.md提取失败模式 / CE P2状态快照注入+P3失败模式预注入 ✅）。此前已完成：**`116`** `candidates.js` 三源能力候选提取深度 ✅；**`115`** `personality.js` 多层自我调优系统深度（379L纯JS / 5维人格状态 / 三层突变机制（自然选择→触发→反思驱动）/ personalityScore Laplace平滑+小样本惩罚 / 每轮≤2参数×±0.2防跳变 / CE P2 ModeService五维状态+P2人格驱动注入策略 ✅）✅。此前已完成：**`114`** `selector.js` 多模态选择与漂移策略深度 ✅。此前已完成：**`110`** `mutation.js` 核心深度（204L纯JS / 两层安全门禁（high-risk personality→innovate downgrade / high-risk mutation人格授权）/ 8种Safety Signal机制 / isValidMutation 8-field验证 / BlueCortexCE P1观察风险分级+P1 Safety Signal+P2人格驱动策略调整）✅；**`111`** `strategy.js` 进化策略预设深度（131L纯JS / 7种预设（balanced/innovate/harden/repair-only/early-stabilize/steady-state/auto）/ 周期数≤5→early-stabilize自动切换 / 饱和信号→steady-state / FORCE_INNOVATION向后兼容 / BlueCortexCE P2策略驱动注入+P3周期感知+P3策略感知搜索）✅；**`108`** v1.78.7–v1.78.10 Delta + sync-dedup 测试 + 3个新增混淆模块（+201 genes / +4 capsules / 3个重度混淆模块 explore~65KB/shield~65KB/hubVerify~25KB hex-encoded不可分析 / v1.78.10 sync-dedup.test.js 192L端到端测试2个failure mode / 演进趋势：混淆模块+测试覆盖）；backlog Item #22（Java/Worker语义一致性）✅勾选；**`107`** Java pgvector vs Worker Chroma 双栈语义一致性深度✅；**`106`** questionComposer深度分析✅；**`105`** EvoMap核心记忆架构源码深度✅

**最后更新**：2026-05-06 05:59（**`114` `selector.js` 多模态选择与漂移策略深度** ✅：417L纯JS / 3-mode信号匹配（regex/alias/substring）+ BoW Cosine无外部依赖 + scoreGeneLearning四层（history outcome±0.12~0.22 / epigenetic platform加成 / anti-pattern惩罚）+ `1/√Ne`群体遗传学连续漂移 + diversity_directed drift（capability gaps引导探索）+ failed capsule双重条件封禁 + buildSelectorDecision human-readable reason[]零成本可观测性 / CE P2–P3行动项；详见 [`114`](./114-selector-js-multimode-selection-and-drift-deep-dive.md)；此前 **`112` README npm downloads badge** ✅：仅 docs commit，gep 核心无增量，BlueCortexCE无行动项；backlog **0 项未决** / 118 doc ✅ / 架构合规✅ / 上游仅 1 新 docs commit ✅；此前已完成：**`109` Hook/瘦代理延迟源码级分析** ✅：Evolver 三种 Hook 延迟特征（signal-detect < 50ms / session-start < 100ms / session-end < 7s）/ CE 200ms 约束交叉验证 / 潜在风险：generateContext 同步路径 + Worker Bun Chroma / 实测建议已记录 / backlog Item ✅勾选；详见 [`109`](./109-hook-thin-proxy-latency-analysis.md)；**`108` v1.78.7–v1.78.10 Delta + sync-dedup 测试 + 3个新增混淆模块** ✅（v1.78.7: +201 genes/+4 capsules/+3个重度混淆模块（explore~65KB/shield~65KB/hubVerify~25KB，hex-encoded packer，代码不可读，env变量暗示arXiv探索/安全防护/Hub验证）/ v1.78.8: 全模块bump / v1.78.9: evolveSessionsDir.test.js 170L回归测试#527 / v1.78.10: index.js+58行CLI改进 / sync-dedup.test.js 192L端到端测试2个failure mode / 全模块31个文件版本bump / BlueCortexCE无直接行动项（混淆代码不可分析）；详见 [`108`](./108-v17810-v1787-delta-sync-dedup-new-obfuscated-modules.md)；**`107` Java pgvector vs Worker Chroma 双栈语义一致性深度** ✅（5大根因差异D1-D5 / 3个典型不一致场景 / BlueCortexCE纯Java无当前问题 / P0确认部署路径+P1统一embedding模型+P2跨栈评测方案+P3废弃Chroma层评估 / 详见 [`107`](./107-dual-stack-semantic-consistency-java-pgvector-vs-worker-chroma.md)；**`106` questionComposer 深度分析 + CE 上下文 Pipeline 借鉴** ✅（模板模式+策略模式+防御性+确定性4大设计模式 / _normalize规范化算法 / 确定性hash-seed选择 / CE StructuredContext模板提案 / 详见 [`106`](./106-questioncomposer-bluecortex-context-pipeline-deep-dive.md)）；**`104` Token Budget 分析：语义注入 vs 时间线预算竞争** ✅（session-init 双路径分析 / generateContext vs additionalContext 无协调 / TokenService 仅用于 footer 统计 / P1 独立上限方案 + P2 统一 TokenBudgetManager + P3 remaining space 动态计算 / 详见 [`104`](./104-token-budget-semantic-vs-timeline-analysis.md)；**`103` v1.78.9 Delta + `defaultHandler.js` + Token Budget 对比** ✅；**`102`** `learningSignals` + `ops/trigger` + `ops/skillsMonitor` 主动自我管理三模块深度 ✅（信号扩展标签化/repair/optimize/innovate/领域标签、文件信号立即唤醒 POLLING-WAKE 机制、技能自愈监控+自动修复missing node_modules/SKILL.md、成熟度L0-L4分级、CE P1信号扩展标签化+P2立即唤醒+P2技能自愈框架）；**`101`** 核心架构模式深度分析 ✅（memoryGraph.js 788行源码综合 / 7大可借鉴模式 / BlueCortexCE优先级映射）；**`100`** `evolve.js` 完整周期→Memory Graph 操作映射 ✅；**`99`** v1.47.0 `evolve.js` 安全系统深度 ✅）

**完整导航**（详细 doc 编号表 + 按主题入口）：[`index-nav.md`](./index-nav.md)

**完整变更历史（changelog 条目）**：见 [`changelog-entries.md`](./changelog-entries.md)（含 #96–#36 所有详细分析摘要）

**并列入口（Hermes / 论文线）**：[`../memory-research-hub.md`](../memory-research-hub.md)

---

### 架构规范

- **短入口**：本文仅保留 changelog 与链接入口；完整导航见 [`index-nav.md`](./index-nav.md)。
- **单文件上限**：本目录正文建议 **≤50KB**；超标则**新建方面文件或拆分**。
- **索引真源**：[`index-nav.md`](./index-nav.md) 为完整编号表；changelog 以本文 changelog 为准。

**例行自检**：`wc -c docs/drafts/evolver-memory/*.md` 任一分片若逼近 50KB 考虑拆分。

---

### 快速跳转

| 目标 | 打开 |
|------|------|
| 完整导航（按 doc 编号 + 按主题） | [`index-nav.md`](./index-nav.md) |
| 总 changelog（详细条目） | [`./changelog-entries.md`](./changelog-entries.md) |
| BlueCortexCE 方面对照 | [`09`](./09-aspect-bluecortex-bridge.md) |
| CE 实现锚点 / 缺口 | [`10`](./10-aspect-bluecortex-implementation-map.md) |
| 研究 backlog（可勾选） | [`11`](./11-research-backlog.md) |
| CE 记忆 HTTP / 数据平面 | [`12`](./12-bluecortex-api-memory-surface.md) |
| CE Java 摄入 / 写入链 | [`16`](./16-ingestion-write-path-sketch.md) |
| a2aProtocol.js + a2a.js 双层架构 | [`95`](./95-a2aProtocol-and-a2a-deep-dive.md) |
| GEP Prompt Schema Enforcement + Token Budget | [`92`](./92-prompt-js-schema-enforcement-and-token-budget.md) |
| `forceUpdate.js` Hub 心跳驱动三通道强制更新 | [`96`](./96-forceupdate-hub-heartbeat-driven-version-migration.md) |
| v1.78.9 Minor Subsystem Additions（featureFlags / dmHandler / skillUpdater / taskMonitor） | [`98`](./98-v1789-minor-subsystem-additions.md) |
| v1.47.0 `evolve.js` 安全系统深度（竞速检测/队列上限/负载感知/修复断路器/6h缓存/mood/CWD恢复） | [`99`](./99-evolver-v147-evolvejs-safety-infrastructure.md) |
| Hub Search-First（两阶段搜索+双层缓存） | [`89`](./89-hubsearch-two-phase-semantic-and-dual-cache-deep-dive.md) |
| ATP Heartbeat 旁路交付机制 | [`91`](./91-atp-heartbeatsignalshandler-deep-dive.md) |
| taskReceiver Worker Pool 原子操作 + Capability Match | [`88`](./88-taskreceiver-workerpool-privacy-capability-deep-dive.md) |
| Hub Ecosystem（taskReceiver + hubReview + issueReporter） | [`46`](./46-hub-ecosystem-integration-taskreview-issue.md) |
| 5 核心设计模式 + CE 翻译优先级 | [`62`](./62-evolver-core-design-patterns-and-ce-translation.md) |
| 记忆系统架构综合（8 大设计原则） | [`36`](./36-memory-architecture-synthesis.md) |
| **EvoMap/evolver 本地源码**（memoryGraph / 叙事 / 适配器） | [`18`](./18-evolver-local-source-memory-architecture-snapshot.md) |
| `evolve.js` 主循环 | [`19`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) |
| `evolve.js` 完整周期→Memory Graph 映射（10阶段） | [`100`](./100-evolvejs-complete-cycle-memory-graph-mapping.md) |
| **主动自我管理**（signal扩展标签化/文件唤醒/技能自愈） | [`102`](./102-learningSignals-ops-trigger-skillsMonitor-selfManagement-deep-dive.md) |
| v1.78.9 Delta + defaultHandler + Token Budget 对比 | [`103`](./103-v1789-delta-defaultHandler-tokenBudget-comparison.md) |
| **Token Budget 分析：语义注入 vs 时间线预算竞争** | [`104`](./104-token-budget-semantic-vs-timeline-analysis.md) |
| **v1.78.7–v1.78.10 Delta + 3个新增混淆模块 + sync-dedup 测试** | [`108`](./108-v17810-v1787-delta-sync-dedup-new-obfuscated-modules.md) |
| **双栈语义一致性**（Java pgvector vs Worker Chroma / 5大根因 / 3场景） | [`107`](./107-dual-stack-semantic-consistency-java-pgvector-vs-worker-chroma.md) |
| **Hook/瘦代理延迟源码级分析**（3种Hook时延特征/CE 200ms约束验证/实测建议） | [`109`](./109-hook-thin-proxy-latency-analysis.md) |
| **`mutation.js` 核心深度**（两层安全门禁/风险等级/验证规范化） | [`110`](./110-mutation-js-core-deep-dive.md) |
| **`strategy.js` 进化策略预设深度**（7种预设/周期数自动切换/饱和检测） | [`111`](./111-strategy-js-evolution-presets-deep-dive.md) |
| **`113`** `memoryGraphAdapter.js` Local-First/Remote-Addictive 适配器模式深度（Local-First写/Remote-Addictive读/Graceful Degradation/10-method接口契约） | [`113`](./113-memorygraph-adapter-local-first-remote-additive-pattern-deep-dive.md) |
| **核心架构模式深度**（7大模式源码级） | [`101`](./101-core-memory-architecture-patterns-deep-dive.md) |
| **EvoMap 核心记忆架构源码深度**（7事件模型/信号管道/置信度评分/结果推断/双栈适配器/叙事记忆/BlueCortexCE对照） | [`105`](./105-core-memory-architecture-deep-dive-evolver.md) |
| **questionComposer 深度 + CE 上下文 Pipeline 借鉴**（模板模式/防御性/确定性/策略模式） | [`106`](./106-questioncomposer-bluecortex-context-pipeline-deep-dive.md) |
| v1.47.0 `evolve.js` 安全系统（竞速检测/队列上限/负载感知/修复断路器/6h缓存/mood/CWD恢复） | [`99`](./99-evolver-v147-evolvejs-safety-infrastructure.md) |

---

### 完整 doc 编号速查

详见 [`index-nav.md`](./index-nav.md)，核心 doc 编号：

- **01–08**：顺序分片（旧版）
- **09–17**：CE 方面 / API / Java 链路 / 快照
- **18–54**：EvoMap 本地架构 / 主循环 / Session / v1.46–v1.66
- **55–78**：v1.66–v1.78 新架构 / 信号 / A2A / Proxy / Hook
- **79–113**：基础设施深度 + mutation/strategy/memoryGraphAdapter 核心模块（GEP 安全机制 / 策略预设 / asset/contentHash / Ops / Hook / Config / ATP / Dual-Stack / hubSearch / A2A / ForceUpdate / v1.78.7–v1.78.10 Delta / Adapter Pattern）
