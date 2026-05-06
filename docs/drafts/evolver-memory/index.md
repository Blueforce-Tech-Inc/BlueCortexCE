# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**cron 巡检 2026-05-06 13:56**：目录 **126** 个 .md；最大 45633B；所有 < 50KB ✅；**backlog 全项已勾选 ✅**；backlog **0 项未决**；上游新增 **v1.79.0**+**v1.79.1**（`93e44a3`）—— Cycle Hard-Timeout（#19）+ Windows Respawn Fix（#528）+ dotenv 加载顺序（#460/#526）+ +201 genes + +4 capsules。新增 **`120`** v1.79.0/v1.79.1 Delta（+89L index.js daemon 重写 / +127L cycleHardTimeout.test.js / +167L spawnReplacementProcess.test.js / Cycle Hard-Timeout: Promise.race 45min 硬超时 + CycleTimeoutError + progressTicker 30s + writeCycleProgressAtomic / Windows Respawn Fix: spawn(detached) 开 cmd 弹窗 → 默认跳过 opt-in EVOLVER_SUICIDE_WINDOWS=true / dotenv: cwd/.env 优先于 getRepoRoot 缓存 / CE P1 超时保护+P1 环境变量加载顺序+P2 跨平台进程管理；详见 [`120`](./120-v1790-v1791-cycle-hard-timeout-and-windows-respawn-deep-dive.md)）。此前 **`119`** `curriculum.js` Outcome-Driven Curriculum Learning 深度 ✅；**`118`** `localStateAwareness.js`+`analyzer.js` 深度 ✅；**`117`** `solidify.js` 核心深度 ✅；**`116`** `candidates.js` 三源能力候选提取深度 ✅；**`115`** `personality.js` 多层自我调优系统深度 ✅；**`114`** `selector.js` 多模态选择与漂移策略深度 ✅；**`113`** `memoryGraphAdapter.js` Local-First/Remote-Addictive 适配器模式深度 ✅；**`111`** `strategy.js` 进化策略预设深度 ✅；**`110`** `mutation.js` 核心深度 ✅；**`109`** Hook/瘦代理延迟源码级分析 ✅；**`108`** v1.78.7–v1.78.10 Delta + sync-dedup 测试 ✅；**`107`** Java pgvector vs Worker Chroma 双栈语义一致性深度 ✅；**`106`** questionComposer深度分析 ✅；**`105`** EvoMap核心记忆架构源码深度 ✅

**最后更新**：2026-05-06 13:56（**`120` v1.79.0/v1.79.1 Delta** ✅：Cycle Hard-Timeout Promise.race 45min 硬超时 + CycleTimeoutError + progressTicker 30s + writeCycleProgressAtomic / Windows Respawn Fix: spawn(detached) 开 cmd 弹窗 → 默认跳过 opt-in EVOLVER_SUICIDE_WINDOWS=true / dotenv cwd/.env 优先于 getRepoRoot 缓存 / CE P1 超时保护+P1 环境变量加载顺序+P2 跨平台进程管理；详见 [`120`](./120-v1790-v1791-cycle-hard-timeout-and-windows-respawn-deep-dive.md)；此前 **`119` `curriculum.js` Outcome-Driven Curriculum Learning** ✅；**`118` localStateAwareness+analyzer** ✅；**`117` `solidify.js` 核心深度** ✅；**`116` `candidates.js` 三源能力候选提取** ✅；**`115` `personality.js` 多层自我调优** ✅；**`114` `selector.js` 多模态选择与漂移策略** ✅：417L纯JS / 3-mode信号匹配（regex/alias/substring）+ BoW Cosine无外部依赖 + scoreGeneLearning四层 + `1/√Ne`群体遗传学连续漂移 + diversity_directed drift + failed capsule双重条件封禁 + buildSelectorDecision零成本可观测性 / CE P2–P3行动项；详见 [`114`](./114-selector-js-multimode-selection-and-drift-deep-dive.md)；此前 **`109` Hook/瘦代理延迟源码级分析** ✅；**`108` v1.78.7–v1.78.10 Delta + sync-dedup 测试** ✅；**`107` Java pgvector vs Worker Chroma 双栈语义一致性深度** ✅；**`106` questionComposer 深度分析** ✅；**`105` 核心记忆架构源码深度** ✅；**`104` Token Budget 分析** ✅；**`103` v1.78.9 Delta** ✅；**`102` 主动自我管理三模块深度** ✅；**`101` 核心架构模式深度分析** ✅；**`100` `evolve.js` 完整周期→Memory Graph 操作映射** ✅；**`99` v1.47.0 `evolve.js` 安全系统深度** ✅）

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
- **79–122**：基础设施深度 + mutation/strategy/memoryGraphAdapter 核心模块（GEP 安全机制 / 策略预设 / asset/contentHash / Ops / Hook / Config / ATP / Dual-Stack / hubSearch / A2A / ForceUpdate / v1.78.7–v1.79.1 Delta / Adapter Pattern / gitOps 回滚+关键文件保护 / paths 多租户Scope隔离）
