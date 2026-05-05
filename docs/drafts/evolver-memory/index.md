# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**cron 巡检 2026-05-05 18:58**：目录 **114** 个 .md；最大 45633B；所有 < 50KB ✅；backlog 余 **3** 项。新增 **`107`** Java pgvector vs Worker Chroma 双栈语义一致性深度（5大根因差异D1-D5 / 3个典型不一致场景 / BlueCortexCE纯Java无当前问题但Worker层有风险 / P0确认部署路径+P1统一embedding+P2跨栈评测+P3废弃Chroma层评估）；backlog Item #22（Java/Worker语义一致性）✅勾选；**`106`** questionComposer深度分析✅；**`105`** EvoMap核心记忆架构源码深度✅

**最后更新**：2026-05-05（**`107` Java pgvector vs Worker Chroma 双栈语义一致性深度** ✅（5大根因差异D1-D5 / 3个典型不一致场景 / BlueCortexCE纯Java无当前问题 / P0确认部署路径+P1统一embedding模型+P2跨栈评测方案+P3废弃Chroma层评估 / 详见 [`107`](./107-dual-stack-semantic-consistency-java-pgvector-vs-worker-chroma.md)；**`106` questionComposer 深度分析 + CE 上下文 Pipeline 借鉴** ✅（模板模式+策略模式+防御性+确定性4大设计模式 / _normalize规范化算法 / 确定性hash-seed选择 / CE StructuredContext模板提案 / 详见 [`106`](./106-questioncomposer-bluecortex-context-pipeline-deep-dive.md)）；**`104` Token Budget 分析：语义注入 vs 时间线预算竞争** ✅（session-init 双路径分析 / generateContext vs additionalContext 无协调 / TokenService 仅用于 footer 统计 / P1 独立上限方案 + P2 统一 TokenBudgetManager + P3 remaining space 动态计算 / 详见 [`104`](./104-token-budget-semantic-vs-timeline-analysis.md)；**`103` v1.78.9 Delta + `defaultHandler.js` + Token Budget 对比** ✅；**`102`** `learningSignals` + `ops/trigger` + `ops/skillsMonitor` 主动自我管理三模块深度 ✅（信号扩展标签化/repair/optimize/innovate/领域标签、文件信号立即唤醒 POLLING-WAKE 机制、技能自愈监控+自动修复missing node_modules/SKILL.md、成熟度L0-L4分级、CE P1信号扩展标签化+P2立即唤醒+P2技能自愈框架）；**`101`** 核心架构模式深度分析 ✅（memoryGraph.js 788行源码综合 / 7大可借鉴模式 / BlueCortexCE优先级映射）；**`100`** `evolve.js` 完整周期→Memory Graph 操作映射 ✅；**`99`** v1.47.0 `evolve.js` 安全系统深度 ✅；**`98`** v1.78.9 Minor Subsystem Additions ✅；**`97`** `issueReporter.js` + `validationReport.js` 深度 ✅）；**`96`** `forceUpdate.js` Hub 心跳驱动三通道强制更新（100行/degit+npm+manual/幂等+白名单保护/CE P3）✅；**`95`** a2aProtocol.js + a2a.js 双层深度 ✅；**`94`** v1.78.7–v1.78.9 版本差分 ✅；**`93`** directoryClient.js 深度 ✅；**`92`** `prompt.js` GEP Schema Enforcement + Token Budget ✅）

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
| **双栈语义一致性**（Java pgvector vs Worker Chroma / 5大根因 / 3场景） | [`107`](./107-dual-stack-semantic-consistency-java-pgvector-vs-worker-chroma.md) |
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
- **79–106**：基础设施深度（asset/contentHash / Ops / Hook / Config / ATP / Dual-Stack / Storage / Worker Pool / hubSearch / Heartbeat旁路 / Prompt Schema / A2A Protocol / ForceUpdate）
