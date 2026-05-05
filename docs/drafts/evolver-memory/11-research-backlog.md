# 研究 / 决策 backlog（可接力）

> **角色**：给后续人类或 Agent 的**短队列**——可勾选、可补链接；**不**重复 [`09`](./09-aspect-bluecortex-bridge.md) 的 P0/P1 定义本身。  
> **最后更新**：2026-05-05（**`104` Token Budget 分析** ✅：session-init.ts 双路径独立注入（SDK agent init → Timeline / UserPromptSubmit → Semantic）/ ContextService.generateContext 无全局字符上限（只有数量上限）/ TokenService.calculateEconomics() 仅用于 footer 统计非预算管理 / `CLAUDE_MEM_SEMANTIC_INJECT` 默认 false（已确认）/ P1 独立上限方案（SEMANTIC_MAX_CHARS=2000）+ P2 统一 TokenBudgetManager + P3 remaining space 动态计算 / 详见 [`104`](./104-token-budget-semantic-vs-timeline-analysis.md)；**`100` `evolve.js` 完整周期→Memory Graph 操作映射** ✅：10阶段完整映射（preflight竞速→outcome闭合→snapshot→hubSearch→selection→hypothesis→attempt→solidify→dormant→reflection）/ memory_graph.jsonl vs events.jsonl 双文件存储对比 / 完整状态流转图（Phase 2 outcome 闭合本轮attempt，Phase 7 attempt 为下一轮outcome 提供baseline观测）/ CE cron 巡检等价格式实现提案；详见 [`100`](./100-evolvejs-complete-cycle-memory-graph-mapping.md)；**`99` v1.47.0 `evolve.js` 安全系统深度** ✅：2175行完整源码分析；覆盖竞速检测（`ps aux`）、队列上限（QUEUE_MAX默认10）、系统负载感知（CPU×0.9）、循环门控（solidify完成前不开启新cycle）、修复循环断路器（5次repair失败→FORCE_INNOVATION）、CWD ENOENT恢复、6小时skills缓存（mtime验证）、mood.json情绪感知、git-sync检测、clawhub auto-update、Dormant假设恢复（中断状态持久化）；CE P1队列上限+P1负载感知+P1修复断路器+P2缓存mtime+P2归档+P3休眠恢复+P3 CWD验证；详见 [`99`](./99-evolver-v147-evolvejs-safety-infrastructure.md)；**`97` `issueReporter.js` + `validationReport.js` 深度分析** ✅：`src/gep/issueReporter.js`（262行）自动 GitHub Issue 机制（failure_loop_detected OR recurring_error+high_failure_ratio + streak≥5 / SHA-256 errorKey去重 + 24h cooldown + `issue_reporter_state.json` 双保险 / 多分区 Issue体含环境+错误签名+事件表+日志片段+redactString脱敏 / `validationReport.js`（55行）标准化 ValidationReport 类型（env_fingerprint双重记录+stdout/stderr双字段兼容+Content-addressable asset_id / solidify.js 补遗：FailedCapsule rollback前diff保留 / 三路Hub Task自动完成 / Anti-pattern opt-in发布 / Gene library SHA-256 versioning / CE P0 ValidationReport+P1错误告警去重冷却+P1失败Diff保留+P2飞书告警格式化）；：`src/gep/directoryClient.js`（110行）完整源码分析：searchByQuery（语义搜索）/searchBySignals（信号搜索）/getAgentProfile（Agent档案7字段）/discoverForTask（组合发现）/静默降级（无重试/无熔断/无缓存）/Hub三发现接口关系（directoryClient=Agent发现/taskReceiver=Task发现/hubSearch=Asset发现）/CE P3可选借鉴；**`94` v1.78.7–v1.78.9 版本差分与回归测试护栏** ✅：dotenv加载顺序修复#526（+32行critical bug fix）+MemoryGraph轮转回归测试#519（167行gzip+保留计数+幂等rotation）+AGENT_SESSIONS_DIR覆盖回归测试#527（170行修复Windows/非标准OpenClaw静默失败）；backlog ✅）（`80` v1.78.5 版本增量同步（Claude Code `tool_input.*` signal-detect 修复）+ Doc 56 时间戳勘误 ✅（v1.47 结论正确，v1.78 三层架构已引入，链接到 doc 73）/ Doc 24 Gene/Strategy CE翻译全覆盖 ✅/ OMLSA自适应休眠 ✅ + LLM驱动代码评审 ✅（均已在doc 45覆盖）；backlog 架构与产品区已清空4项；v8.8: selectGeneAndCapsule决策管线+Failed Capsule Ban（`76`）✅ | [`11`](./evolver-memory/11-research-backlog.md)；backlog ✅；v8.9: v1.78.5版本同步 ✅）（4级强度 / 跨平台空闲检测 / `sleep_multiplier`/`should_distill`×`should_reflect` 联动 / CE P0提案）+ ContentHash 内容寻址系统（`canonicalize`/`computeAssetId`/`verifyAssetId` / CE P2 提案）；`73` 三层信号提取架构现实核查 + 新机会信号；`72` inferOutcomeEnhanced baseline/current delta + 双聚合链；`71` MCP Semantic Capability 产品评估；`70` v1.48–v1.78 新增子系统深度分析；backlog ✅）

---

- [x] **主动自我管理三模块深度分析** ✅（`102`）：`learningSignals.js`（89行）信号扩展标签化（problem:reliability→action:repair / problem:performance→action:optimize / problem:stagnation→action:innovate，5类问题域+4类行动标签+3类领域标签）；`ops/trigger.js`（33行）WAKE文件立即唤醒机制（polling-wake / .tmp+rename防竞态 / 无cron延迟）；`ops/skillsMonitor.js`（143行 v2.0）技能自愈监控（missing node_modules→npm install / missing SKILL.md→stub生成 / 忽略列表机制）；三模块构成"检测→分类→自愈"闭环；CE P1信号扩展标签化（ObservationEntity.signal_tags字段）/ P2立即唤醒（巡检+事件双触发）/ P2技能自愈框架；详见 [`102`](./102-learningSignals-ops-trigger-skillsMonitor-selfManagement-deep-dive.md)

## 使用方式

- 完成一项：在条目前打 `[x]`，可选补一行「结论链接」（PR、commit、或 `0x`/`10` 增补说明）。
- 条目过长：迁到独立 `16+*.md` 或写入对应 `0x` 分片，此处只保留一行指针。

---

## 架构与产品

- [x] **Gene/Strategy 层对 BlueCortexCE 的借鉴** ✅：Strategy presets（repair/optimize/innovate 比例）→ "观察注入策略"（doc 24 §2.3）/ 多因子 Gene selector → `SearchService` 增强（doc 24 §3.4）/ Mutation safety → 观察风险分级（doc 24 §4.3）/ Bag-of-words fallback → 轻量语义备选（doc 24 §6.2）/ Candidates pool → CE暂无对应；详见 [`24`](./24-gene-strategy-layer.md)
- [x] **MCP 是否暴露与 Hook 对齐的 `semantic` 能力** ✅：源码确认（`ClaudeMemMcpTools.java`），MCP `search` 工具**支持语义搜索**（`embeddingService.embed(query)` → pgvector 混合策略），但**无**名为 `semantic` 的同名工具；MCP `search` ≠ Hook `semantic` 注入块——前者是通用检索（返回 `{observations, strategy, fell_back}`），后者专用于「prompt 拼注入块」；两者底层均走 EmbeddingService → SearchService，**基础设施对齐，产品形态不同**；详见 doc 12 §3.2 + `ClaudeMemMcpTools.java` L84–L103。
- [x] **Hook 是否调用 `semantic`**：`session-init` 在 `CLAUDE_MEM_SEMANTIC_INJECT=true`（默认）且 `prompt≥20` 时调用 **worker** `POST /api/context/semantic`（`webui/src/cli/handlers/session-init.ts`）。见 [`12`](./12-bluecortex-api-memory-surface.md) §3。
- [x] **Java（pgvector）与 Worker（Chroma）语义结果一致性** ✅：5大根因差异D1-D5（embedding模型不同/混合策略不同/去重策略不同/时间窗口不同/异常处理不同）+ 3个典型不一致场景（模型差异导致top-k不同 / Chroma多doc去重丢失 / 时间窗口差异）；BlueCortexCE当前纯Java架构无一致性问题，但Worker层有风险；P0确认部署路径+P1统一embedding模型+P2跨栈评测+P3废弃Chroma层评估；详见 [`107`](./107-dual-stack-semantic-consistency-java-pgvector-vs-worker-chroma.md)。
- [x] **语义注入与时间线并存的 token 预算** ✅：session-init.ts 双路径分析完成（SDK agent init → Timeline context / UserPromptSubmit → Semantic `additionalContext`，两者独立注入无协调）；ContextService.generateContext 无全局字符上限（仅数量上限）/ TokenService 仅用于 footer 统计非预算管理；`CLAUDE_MEM_SEMANTIC_INJECT` 默认 false（已确认）；P1 独立上限方案 + P2 统一 TokenBudgetManager + P3 remaining space 动态计算；详见 [`104`](./104-token-budget-semantic-vs-timeline-analysis.md)。
- [x] **错误类观察的 `extracted_data` 约定** ✅：是否统一 `error_signature`（栈归一化）字段名与归一规则，并与 `content_hash` 去重策略分工。（对齐 Evolver `normalizeErrorSignature` 思想）
  - **源码验证完成**：`memoryGraph.js` §27 定义 `normalizeErrorSignature`：Windows/Unix路径→`<path>`、十六进制→`<hex>`、数字→`<n>`，截断220字符后 `stableHash`。（提案见 [`22`](./22-error-sig-norm-implementation-proposal.md)）
- [x] **`inferOutcomeEnhanced` baseline vs current delta 机制 + 双聚合链**（`72`）：`memoryGraph.js` L551–L592 源码确认：`recent_error_count` delta → ±0.12（`delta/50` clamp）/ `scan_ms` ratio → ±0.06（`ratio` clamp）/ 真实证据优先（`tryParseLastEvolutionEventOutcome`）/ `clamp01(score)` 边界保护；`getMemoryAdvice` 双链：`(signal, gene)` 边 30 天半衰 vs gene 先验 45 天半衰，`best + prior*0.12` 混合策略；BlueCortexCE 借鉴：ObservationEntity 新增 `baselineMetrics` JSONB / 双链搜索排序 / clamp01；详见 [`72`](./72-inferOutcomeEnhanced-and-dual-aggregation-chains.md)。
- [x] **三层信号提取架构现实核查（`73`）** ✅：⚠️ **Doc 56 结论错误需修正**：`src/gep/signals.js` v1.78.1（444行）确认 Layer 1/2/3 真实存在；`SIGNAL_PROFILES` 加权关键词评分（累积 evidence → 阈值触发）；`_extractLLM` Hub 调用（每 5 cycle 一次，节流）；`_mergeSignals` 三路合并 + observability；`execFileSync` argv 防命令注入；新增 7 个机会信号（`issue_already_resolved`/`openclaw_self_healed`/`empty_cycle_loop_detected`/`explore_opportunity`/`hub_search_miss_with_problem`/`plateau_pivot_required`/`plateau_pivot_suggested`）；详见 [`73`](./73-reality-check-three-layer-signals-and-new-opportunity-signals.md)。

## 实现与数据

- [x] **Worker SQLite + Chroma 与 Java Postgres + pgvector 的关系** ✅：两套独立向量系统，SQLite 是唯一共同数据源，无自动双写；详见 [`86`](./86-dual-stack-semantic-architecture.md)（ChromaSync.ts 470行 / Worker ChromaDB vs Java pgvector 双栈分析 / `ensureBackfilled` 增量同步 / 无跨栈自动一致性保障）。
- [x] **时间半衰 / 重复失败降权** ✅：decayWeight 指数半衰（30天边/45天基因）+ edgeExpectedSuccess Laplace平滑 / CE 翻译方案（ObservationEntity 新增 decayScore 字段 / SearchService 排序加权 / clamp01 边界保护）；详见 [`20`](./20-time-decay-and-fail-degradation.md)。
- [x] **Hook / 瘦代理延迟** ✅（`109`）：源码级确认 Evolver 三种 Hook（signal-detect 2s/session-start 3s/session-end 8s）hot path 均满足 < 200ms 原则（除 session-end git diff 在大仓库可能超 200ms，但 Stop 事件不阻塞 AI）；CE 瘦代理架构（wrapper.js → HTTP ACK → @Async）完全符合 200ms 约束；潜在风险：generateContext 同步路径 LLM 调用 + Worker Bun Chroma 操作；实测建议已记录；详见 [`109`](./109-hook-thin-proxy-latency-analysis.md)。
- [x] **taskMonitor.js 心跳元数据 + 环形缓冲区借鉴** ✅：`getHeartbeatMeta()` 每次心跳上报聚合指标而非原始数据 / 环形缓冲区有界100样本防止无限增长 / 订阅模型事件驱动 / CE 可参考观察统计心跳上报；详见 [`98`](./98-v1789-minor-subsystem-additions.md) §4。

## Evolver 侧（外部源码）

- [x] **EvoMap/evolver v1.78.1→v1.78.5 版本增量同步**（`80` 新增 ✅）：v1.78.5 较 v1.78.1 共 4 个新 commit；其中关键修复：`cbc4870 fix(adapters): read tool_input.* in signal-detect for Claude Code (#522)`——Claude Code 的 PostToolUse payload 将工具参数嵌套在 `tool_input.*` 下（原只读顶级字段导致 Edit 事件信号检测静默返回 `{}`）；修复后优先读 `ti.content`/`ti.new_string`/`ti.file_content` 及 `tr.filePath`，fallback 兼容旧格式；修复对 Cursor/Codex/Kiro 等其他平台无影响。详见 [`79c`](./79c-evolver-hook-adapter-system-deep-dive.md) Hook 适配系统深度分析。
- [x] **v1.47→v1.78.5 架构演进总览 + config.js 集中配置模式**（`80` 新增 ✅）：⚠️ 本地工作树实际在 v1.47.0（`e72778e`），docs 原误标为 v1.78.5；v1.78.5 代码在 git 未检出；核心发现：evolve.js 从 ~2500L 瘦身为 ~300L（-2176行），+3670/-9156行净减；新增 ATP/adapters/ops/validator 四大子系统；`src/config.js`（215行）集中所有 magic number + env override，5类配置组（Network/Solidify/Evolution/Gene/Ops/Self-PR/LeakCheck），CE P0 行动项：创建 `MemoryConfig.java` 集中配置；详见 [`80`](./80-architecture-evolution-v147-v178-config-centralization.md)。
- [x] **`skill2gep.js`（645行）完整源码分析：逆向蒸馏管道（parseSkillMd / detectForgery / assembleCapsule / 双通道发布）
  - ✅ `selfPR.js`（408行）完整源码分析：多门禁自动 PR 贡献（score/streak/risk/blask 多重门禁 + leakCheck + diffHash 去重）
  - ✅ `validator/` 子系统（~900行）完整源码分析：sandboxExecutor 两层白名单 + BLOCKED_NODE_FLAGS + 隔离 env + stakeBootstrap 磁盘持久化退避 + 独立守护进程
  - ✅ `portable.js` / `claimNudge.js` / `mailboxTransport.js` 综合分析
  - 详见 [`70`](./70-new-subsystems-v148-v178-deep-dive.md)；backlog 版本同步状态 ✅

- [x] **`reflection.js` 模块深度分析**（`59` 新增）：computeReflectionInterval 三态算法（3/5/8）/ shouldReflect 双重条件（周期对齐+冷却30min）/ 预聚合统计（intent分布/gene频率）/ 5问战略复盘框架与精确JSON输出格式 / `buildSuggestedMutations` 信号→参数映射 / JSONL读写机制 / 与innovation.js功能/参数二级互补 / CE自我诊断框架与元级SummaryEntity提案。详见 [`59`](./59-reflection-js-module-deep-dive.md)。
- [x] **ATP（Agent Transaction Protocol）+ Adapters 系统深度分析**（`75` 新增）：ATP Hub Client 275行（proxy/direct 双路由 / 10个 API 端点）/ Merchant Agent 商家模板 118行 / Consumer Agent 消费者模板 157行 / autoBuyer ~200行（三重预算保护 + 24h去重 + cold-start半额）/ hookAdapter 207行（detectPlatform + mergeJsonFile + copyHookScripts + 4平台adapter）/ BlueCortexCE P1（hookAdapter 跨平台适配）/ P2（ATP Market 能力采购）/ P3（资源控制备选）；详见 [`75`](./75-atp-agent-transaction-protocol-and-adapters.md)。
- [x] **EvoMap/evolver 版本差分** ✅：v1.78.5→v1.78.10 共 4 个版本（v1.78.7 / v1.78.8 / v1.78.9 / v1.78.10）；3个新增重度混淆模块（explore.js~65KB / shield.js~65KB / hubVerify.js~25KB，hex-encoded packer，代码不可读）；+201 genes / +4 capsules；3个回归测试（memoryGraphRotation.test.js / evolveSessionsDir.test.js / sync-dedup.test.js）；详见 [`108`](./108-v17810-v1787-delta-sync-dedup-new-obfuscated-modules.md)。
- [x] **`featureFlags.js`（114行 v1.78.9）**：源码分析完成；三重覆盖语义（本地文件 > home目录文件 > code default）；文件级 `0o600` 权限；懒加载+单次缓存；注释与实现有偏差（声称 Local env 覆盖但未检查 process.env）；详见 [`98`](./98-v1789-minor-subsystem-additions.md) §1。
- [x] **Doc 56 时间戳勘误** ✅：在 doc 56 文件开头添加时间戳说明，澄清「本文结论对 v1.47 正确，v1.78 已引入三层架构」，并链接到 doc 73。

- [x] **`src/proxy/` 子系统 v1.78 新增源码分析**（`78` 新增 ✅）：EvoMapProxy 中间代理层 250L / MailboxStore 持久化 415L（JSONL+Mem索引 / prototype pollution safeAssign / UUID v7 / .tmp+rename 原子写）/ SyncEngine 双向同步 316L（Outbound 批量 flush MAX_BATCH=50+10次重试 / Inbound Cursor分页 10s活跃/60s空闲轮询）/ LifecycleManager 322L（hello/hb/reauth / hello_rate_limit / envFingerprint）/ REST Routes 463L（/mailbox /asset /task /session /dm）/ SessionHandler 141L P2P协作（create/join/leave/delegate subtask ≤16KB）/ hookAdapter 多平台检测 + deepMerge原子写 / ClaudeCode(163L)/Codex(172L)/Kiro(203L)/Cursor(89L) 适配器 / CE P0（原子写+Cursor分页+批量重试+safeAssign）/ P1（分层API路由+批量确认）/ P2（Proxy中间层思想+多平台Adapter模式）。详见 [`78`](./78-v178-proxy-subsystem-architecture.md)。
- [x] **自适应策略策略借鉴**（`45` 新增）：Evolver 每周期动态计算执行策略（repair streak / failure streak / blast radius），CE `ContextService` 可参考实现注入策略动态切换。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §1。
- [x] **候选评估管线借鉴**（`45` 新增）：Evolver 从会话转录提取重复模式（≥3次），生成 Five Questions Shape 候选。CE 可参考实现高频观察模式自动发现。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §2。
- [x] **Git 自修复借鉴**（`26` 已覆盖）：Evolver `self_repair.js` 在进化前自动修复 Git 异常（abort rebase/merge、删除 stale index.lock、可选 hard reset）。CE 可参考实现写入前自检（数据库连接、事务状态）。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §3。
- [x] **`policyCheck.js` 约束系统深度分析**（`42` 新增）：`isConstraintCountedPath` 路径匹配决策树（excludePrefix → includePrefix → extension 优先级）、`computeBlastRadius`（git numstat + untracked 行数统计 + baseline 对比）、`classifyBlastSeverity` 5级分类（hard_cap_breach / critical_overrun / exceeded / approaching_limit / within_limit）、验证命令白名单（`isValidationCommandAllowed` 禁止 `node -e`/shell 操作符）、伦理模式检测（5 种 regex 模式）、`detectDestructiveChanges` 关键文件删除/清空检测。详见 [`42`](./42-policycheck-constraint-system-deep-dive.md)。

- [x] **OMLS 启发式自适应休眠调度借鉴** ✅（`45`）：`idleScheduler.js` `getScheduleRecommendation()` 返回 `idle_seconds`/`intensity`/`sleep_multiplier`/`should_distill`；CE 巡检 cron 可参考实现：低活跃→2小时、中活跃→30分钟、高活跃→15分钟；详见 [`45`](./45-idleScheduler-OMLS-and-llmReview.md) §1。
  - **源码级深度**（`77`）：157行完整源码分析：4级强度（`signal_only/normal/aggressive/deep`）× `sleep_multiplier`（3/1/0.5/0.25）× 三联动标志；跨平台空闲检测（Windows PowerShell `GetLastInputInfo` / macOS `ioreg` / Linux `xprintidle`）；`idle_schedule_state.json` 持久化；详见 [`77`](./77-idleScheduler-contentHash-OMLS-adaptive-memory-scheduling.md) §1。
- [x] **LLM 驱动代码评审借鉴** ✅（`45`）：`llmReview.js` 在 solidify 流程中做 LLM 评审（approved/issues/score/reasoning），与 policyCheck 形成「规则门禁 + 语义评审」双层；CE 可在 ValidationReport 基础上叠加；详见 [`45`](./45-idleScheduler-OMLS-and-llmReview.md) §2。

## 安全与上下文出口（Hermes 对照）

- [x] **统一围栏 / 写入扫描** ✅：对照 `../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md`；hermes-memory 全部 backlog 已勾选（v12.2），CE 侧 P0/P1 缺口已记录于 doc 76（无 memory-context fence / 无注入扫描）；本项 cross-reference 完成，CE 安全缺口见 hermes doc 76；backlog 全项已勾选，无剩余未决项。

---

## 与其它文件的边界

| 文件 | 放什么 |
|------|--------|
| [`09`](./09-aspect-bluecortex-bridge.md) | 已定型的方面对照、优先级、反模式 |
| [`10`](./10-aspect-bluecortex-implementation-map.md) | 本仓库**已实现**与**缺口**的代码锚点 |
| [`12`](./12-bluecortex-api-memory-surface.md) | **HTTP**、**§1.1 `semantic`**、**§2** 数据平面、**§3–§3.1** 调用方、**§3.2** MCP 工具分流 |
| [`14`](./14-context-output-pipeline-sketch.md) | **Java** 侧上下文产出链（非 worker） |
| [`15`](./15-runtime-integration-surfaces.md) | Worker/Java 判别；**§2.1** Hook Worker 基址；**§2** 各集成客户端默认进程；**§4** wrapper→Java；**§5** 会话首跳 |
| [`16`](./16-ingestion-write-path-sketch.md) | **Java** 侧瘦代理摄入 / 观察写入链 |
| [`17`](./17-session-lifecycle-java-sketch.md) | **Java** 侧 `/api/session/start` 与 session-end 对照 |
| [`18`](./18-evolver-local-source-memory-architecture-snapshot.md) | **EvoMap/evolver 本地** `memoryGraph` / 叙事 / 适配器（非 CE 仓库） |
| [`19`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) | **`evolve` 主循环** 与 outcome 推断（非 CE 仓库） |
| [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) | **运行时编排**：自适应策略、候选评估、Git 自修复、创新催化、自我感知 |
| **本文件** | 未决课题、可选实验、待勾选 |
| [`staging.md`](./staging.md) | 极短草稿，定稿即删或迁入上列 |
| [`../memory-research-hub.md`](../memory-research-hub.md) | Evolver / Hermes / 论文线 **总导航** |
