# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**cron 巡检 2026-05-07 06:45**：目录 **136** 个 .md；最大 44877B；所有 < 50KB ✅；**backlog 全项已勾选 ✅**；backlog **0 项未决**；上游 v1.79.1 已覆盖（本地 checkout v1.47.0 `e72778e`，main 分支领先 56 commits）；无新增可分析源码（v1.78.10→v1.79.1 新增 `scripts/build_binaries.js` 已覆盖 + 4个重度混淆模块 explore/shield/hubVerify/integrityCheck + skillDistiller 440KB 不可分析，skillDistiller v1.47.0 可读版已在 doc 84 分析）；本地 v1.47.0 可读源码 54个 gep/*.js 已全覆盖；proxy/ATP/Adapter 子系统均已深度覆盖；**持续维护 pass，无新增分析任务**。此前 **`125`** ATP TaskPickup+Execute+Heartbeat+Adapters 深度（233+285+254+275+207+203+163+172+89L staged files / Ledger 防重+原子写+.tmp+rename / ContentHash 幂等+HMAC 签名 / sessions_spawn 隔离契约（不直接打印，保持每轮一个契约）/ heartbeat 旁路交付（无需 run() 循环）/ 共享 Ledger 双消费者防双提交 / 3态 Ledger 条目（成功/终结错误/未处理）/ hookAdapter 平台检测+coldMerge+markerKey 干净卸载 / CE P1 ObservationEntity 内容哈希幂等去重+P1 Cron 任务 Ledger 防重+P1 原子写+P2 分阶段错误返回+P2 终结错误码+P2 markerKey 追踪）；此前 **`123`** `narrativeMemory.js` Markdown 双限制叙事记忆深度 ✅ 和 **`124`** `questionGenerator.js` Hub Bounty 问题生成深度 ✅；此前 **`122`** `paths.js` 路径架构深度 ✅；此前 **`121`** `gitOps.js`+`hubReview.js` 深度 ✅ 和 **`122`** `paths.js` 路径架构深度 ✅；此前 **`117`** `deviceId.js` 节点身份识别深度 ✅ 和 **`118`** `innovation.js` 创新催化剂深度 ✅；此前 **`120`** v1.79.0/v1.79.1 Delta ✅；**`119`** `curriculum.js` Outcome-Driven Curriculum Learning 深度 ✅；**`118`** `localStateAwareness.js`+`analyzer.js` 深度 ✅；**`117`** `solidify.js` 核心深度 ✅；**`116`** `candidates.js` 三源能力候选提取深度 ✅；**`115`** `personality.js` 多层自我调优系统深度 ✅；**`114`** `selector.js` 多模态选择与漂移策略深度 ✅；**`113`** `memoryGraphAdapter.js` Local-First/Remote-Addictive 适配器模式深度 ✅；**`111`** `strategy.js` 进化策略预设深度 ✅；**`110`** `mutation.js` 核心深度 ✅；**`109`** Hook/瘦代理延迟源码级分析 ✅；**`108`** v1.78.7–v1.78.10 Delta + sync-dedup 测试 ✅；**`107`** Java pgvector vs Worker Chroma 双栈语义一致性深度 ✅；**`106`** questionComposer深度分析 ✅；**`105`** EvoMap核心记忆架构源码深度 ✅

**最后更新**：2026-05-07 06:25（**`128` 维护 pass** ✅：136个 .md / 最大 44877B / 全 < 50KB ✅ / backlog 0项 / 无新增分析任务 / 持续覆盖中）：352L纯JS `skillPublisher.js`源码深度 / sanitizeSkillName两-tier命名（hash→kebab + signal/summary回退）/ toTitleCase display format / SKILL.md六字段结构（name/summary/signals_match/content_hash/gene_id/created_at）/ content-addressable naming自动去重+版本管理 / 与 CE SummaryEntity对照 / CE P1 content_hash幂等+P1 provenance字段+P2 Gene-like StructuredExtraction+P3 Hub distribution；详见 [`126`](./126-gene-skillpublisher-memory-to-capability-pipeline-deep-dive.md)；此前 **`126` 文档架构维护** ✅：doc 01（45633B→01a+01b ~23KB/份）和 doc 07（45258B→07a+07b ~23KB/份）按 50KB 规范拆分；index-nav.md 链接已同步更新；index.md 元数据已刷新（135个 .md，最大 44877B）；此前 **`125` ATP TaskPickup+Execute+Heartbeat+Adapters 深度** ✅：212L纯JS / 6大策略（recurring_error/capability_gap/evolution_saturation/failure_streak/feature_request/perf_bottleneck）/ 双层去重（精确+模糊0.7 Jaccard word overlap）/ 3h rate limit + 每轮≤2条 + Priority 排序 / 与 questionComposer.js (ATP) 区别（内部进化求助 vs 外部市场交易）/ CE P3 主动知识请求机制（本地搜索 miss → Hub 请求）+ P3 Observation fuzzy dedup；详见 [`124`](./124-questionGenerator-js-deep-dive.md)；此前 **`123` `narrativeMemory.js` Markdown 双限制叙事记忆深度** ✅：108L纯JS / Markdown 追加写（`### [timestamp] CATEGORY - status`）/ 双重裁剪（30 entry count + 12KB size）+ 原子 rename / 8-entry rolling summary / 与 MemoryGraph 互补（机器 JSONL vs 人类 Markdown 双轨）/ CE P3 Markdown Observation Narrative + P3 双限制 SummaryEntity；详见 [`123`](./123-narrativeMemory-js-deep-dive.md)；此前 **`120` v1.79.0/v1.79.1 Delta 追加 build_binaries.js** ✅：scripts/build_binaries.js 388L 新增（bun+javascript-obfuscator+bun compile 多平台构建管线）+ Cycle Hard-Timeout Promise.race 45min / CycleTimeoutError / writeCycleProgressAtomic / Windows conhost popup 修复 #528 / dotenv 加载顺序修复 #460+#526；CE P1超时保护+P1环境变量加载顺序+P2跨平台进程管理+P3 build管线参考；详见 [`120`](./120-v1790-v1791-cycle-hard-timeout-and-windows-respawn-deep-dive.md)；此前 **`117` `deviceId.js` 节点身份识别深度** ✅：209L / 7层降级（env→文件→machine-id→IOPlatformUUID→容器ID→MAC→随机）/ 容器检测4方法 / SHA-256隐私保护原始标识符 / 双路径持久化（~/.evomap/ + 项目目录）/ CE P1节点指纹权限+P2多租户身份+ P2 CLAUDE_MEM_NODE_ID env var；详见 [`117`](./117-deviceId-js-node-identity-deep-dive.md)；**`118` `innovation.js` 创新催化剂深度** ✅：67L / 6类技能分布分析（feishu/dev/media/security/automation/data）/ 3策略启发式创新构想生成（填补空白/优化现有/元级改进）/ 最多返回3条ideas / 零LLM成本停滞突破 / CE P3停滞信号+P3技能分布分析参考；详见 [`118`](./118-innovation-js-catalyst-deep-dive.md)；此前 **`121` gitOps+hubReview** ✅；**`122` paths.js 路径架构** ✅；**`120` v1.79.0/v1.79.1 Delta** ✅；**`119` curriculum.js** ✅；**`118` localStateAwareness+analyzer** ✅；**`117` solidify.js** ✅；**`116` candidates.js** ✅；**`115` personality.js** ✅；**`114` selector.js** ✅：417L纯JS / 3-mode信号匹配+BoW Cosine+1/√Ne遗传漂移+diversity_directed drift+failed capsule双重封禁+零成本可观测性；详见 [`114`](./114-selector-js-multimode-selection-and-drift-deep-dive.md)；**`113` memoryGraphAdapter** ✅；**`111` strategy.js** ✅；**`110` mutation.js** ✅；**`109` Hook/瘦代理延迟** ✅；**`108` v1.78.7–v1.78.10 Delta** ✅；**`107` 双栈语义一致性** ✅；**`106` questionComposer** ✅；**`105` 核心记忆架构** ✅；**`104` Token Budget** ✅；**`103` v1.78.9 Delta** ✅；**`102` 主动自我管理三模块** ✅；**`101` 核心架构模式** ✅；**`100` evolve.js 完整周期** ✅；**`99` evolve.js 安全系统** ✅）

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
| v1.79.0/v1.79.1 Delta + build_binaries.js | [`120`](./120-v1790-v1791-cycle-hard-timeout-and-windows-respawn-deep-dive.md) |
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
| **`strategy.js` 进化策略预设深度**（7种预设/周期数自动切换/饱和检测） | [`111`](./111-strategy-js-evolution-presits-deep-dive.md) |
| **`deviceId.js` 节点身份识别深度**（7层降级/容器检测/SHA-256隐私/双路径持久化） | [`117`](./117-deviceId-js-node-identity-deep-dive.md) |
| **`innovation.js` 创新催化剂深度**（6类技能分布分析/3策略启发式/零LLM成本停滞突破） | [`118`](./118-innovation-js-catalyst-deep-dive.md) |
| **`113`** `memoryGraphAdapter.js` Local-First/Remote-Addictive 适配器模式深度（Local-First写/Remote-Addictive读/Graceful Degradation/10-method接口契约） | [`113`](./113-memorygraph-adapter-local-first-remote-additive-pattern-deep-dive.md) |
| **核心架构模式深度**（7大模式源码级） | [`101`](./101-core-memory-architecture-patterns-deep-dive.md) |
| **EvoMap 核心记忆架构源码深度**（7事件模型/信号管道/置信度评分/结果推断/双栈适配器/叙事记忆/BlueCortexCE对照） | [`105`](./105-core-memory-architecture-deep-dive-evolver.md) |
| **questionComposer 深度 + CE 上下文 Pipeline 借鉴**（模板模式/防御性/确定性/策略模式） | [`106`](./106-questioncomposer-bluecortex-context-pipeline-deep-dive.md) |
| v1.47.0 `evolve.js` 安全系统（竞速检测/队列上限/负载感知/修复断路器/6h缓存/mood/CWD恢复） | [`99`](./99-evolver-v147-evolvejs-safety-infrastructure.md) |
| Gene→SKILL.md transformation pipeline（两-tier命名/content-addressable/Hub发布） | [`126`](./126-gene-skillpublisher-memory-to-capability-pipeline-deep-dive.md) |
| ATP TaskPickup+Execute+Heartbeat+Adapters（Ledger防重/原子写/幂等哈希/HMAC签名/sessions_spawn隔离契约/heartbeat旁路交付/共享Ledger双消费者/markerKey干净卸载） | [`125`](./125-atp-taskpickup-execute-heartbeat-adapters-deep-dive.md) |

---

### 完整 doc 编号速查

详见 [`index-nav.md`](./index-nav.md)，核心 doc 编号：

- **01–08**：顺序分片（旧版）
- **09–17**：CE 方面 / API / Java 链路 / 快照
- **18–54**：EvoMap 本地架构 / 主循环 / Session / v1.46–v1.66
- **55–78**：v1.66–v1.78 新架构 / 信号 / A2A / Proxy / Hook
- **79–122**：基础设施深度 + mutation/strategy/memoryGraphAdapter 核心模块（GEP 安全机制 / 策略预设 / asset/contentHash / Ops / Hook / Config / ATP / Dual-Stack / hubSearch / A2A / ForceUpdate / v1.78.7–v1.79.1 Delta / Adapter Pattern / gitOps 回滚+关键文件保护 / paths 多租户Scope隔离）
