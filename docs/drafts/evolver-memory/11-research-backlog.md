# 研究 / 决策 backlog（可接力）

> **角色**：给后续人类或 Agent 的**短队列**——可勾选、可补链接；**不**重复 [`09`](./09-aspect-bluecortex-bridge.md) 的 P0/P1 定义本身。  
> **最后更新**：2026-05-03（`77` 完成：Doc 56 时间戳勘误 + Doc 24 Gene/Strategy CE翻译全覆盖标记✅ + OMLSA/llmReview backlog勾选✅；`73` 三层信号提取架构现实核查 + 新机会信号；`72` inferOutcomeEnhanced baseline/current delta + 双聚合链；`71` MCP Semantic Capability 产品评估；`70` v1.48–v1.78 新增子系统深度分析；backlog ✅）

---

## 使用方式

- 完成一项：在条目前打 `[x]`，可选补一行「结论链接」（PR、commit、或 `0x`/`10` 增补说明）。
- 条目过长：迁到独立 `16+*.md` 或写入对应 `0x` 分片，此处只保留一行指针。

---

## 架构与产品

- [x] **Gene/Strategy 层对 BlueCortexCE 的借鉴** ✅：Strategy presets（repair/optimize/innovate 比例）→ "观察注入策略"（doc 24 §2.3）/ 多因子 Gene selector → `SearchService` 增强（doc 24 §3.4）/ Mutation safety → 观察风险分级（doc 24 §4.3）/ Bag-of-words fallback → 轻量语义备选（doc 24 §6.2）/ Candidates pool → CE暂无对应；详见 [`24`](./24-gene-strategy-layer.md)
- [x] **MCP 是否暴露与 Hook 对齐的 `semantic` 能力** ✅：源码确认（`ClaudeMemMcpTools.java`），MCP `search` 工具**支持语义搜索**（`embeddingService.embed(query)` → pgvector 混合策略），但**无**名为 `semantic` 的同名工具；MCP `search` ≠ Hook `semantic` 注入块——前者是通用检索（返回 `{observations, strategy, fell_back}`），后者专用于「prompt 拼注入块」；两者底层均走 EmbeddingService → SearchService，**基础设施对齐，产品形态不同**；详见 doc 12 §3.2 + `ClaudeMemMcpTools.java` L84–L103。
- [x] **Hook 是否调用 `semantic`**：`session-init` 在 `CLAUDE_MEM_SEMANTIC_INJECT=true`（默认）且 `prompt≥20` 时调用 **worker** `POST /api/context/semantic`（`webui/src/cli/handlers/session-init.ts`）。见 [`12`](./12-bluecortex-api-memory-surface.md) §3。
- [ ] **Java（pgvector）与 Worker（Chroma）语义结果一致性**：同名路由、异存储；全 Java / 混合部署下的对齐、评测与文档。HTTP 契约与字段对照见 [`12`](./12-bluecortex-api-memory-surface.md) **§1.1**；实现锚点另见 [`10`](./10-aspect-bluecortex-implementation-map.md) §3。
- [ ] **语义注入与时间线并存的 token 预算**：`additionalContext` 与主上下文拼接策略、关闭开关与延迟预算。
- [ ] **错误类观察的 `extracted_data` 约定**：是否统一 `error_signature`（栈归一化）字段名与归一规则，并与 `content_hash` 去重策略分工。（对齐 Evolver `normalizeErrorSignature` 思想）
  - **源码验证完成**：`memoryGraph.js` §27 定义 `normalizeErrorSignature`：Windows/Unix路径→`<path>`、十六进制→`<hex>`、数字→`<n>`，截断220字符后 `stableHash`。
  - **BlueCortexCE 落点**：`ObservationEntity.extractedData` JSONB 已有，dedup 用 `contentHash`（精确哈希），两类机制可共存：`extractedData.error_sig_norm` 存规范化签名用于"同类错误聚合"检索；`content_hash` 保持精确去重。
  - **实施路径**：参考 [`21`](./21-signal-taxonomy-and-gene-selection-memory.md) §2 的 `normalizeErrorSignature` 实现；在 `AgentService.saveObservation` 路径对 `type=error` 观察写入规范化签名。
  - **✅ 提案完成**：见 [`22`](./22-error-sig-norm-implementation-proposal.md)（规范化算法 + JSONB schema + 写入路径 + 实施检查清单）
- [x] **`inferOutcomeEnhanced` baseline vs current delta 机制 + 双聚合链**（`72`）：`memoryGraph.js` L551–L592 源码确认：`recent_error_count` delta → ±0.12（`delta/50` clamp）/ `scan_ms` ratio → ±0.06（`ratio` clamp）/ 真实证据优先（`tryParseLastEvolutionEventOutcome`）/ `clamp01(score)` 边界保护；`getMemoryAdvice` 双链：`(signal, gene)` 边 30 天半衰 vs gene 先验 45 天半衰，`best + prior*0.12` 混合策略；BlueCortexCE 借鉴：ObservationEntity 新增 `baselineMetrics` JSONB / 双链搜索排序 / clamp01；详见 [`72`](./72-inferOutcomeEnhanced-and-dual-aggregation-chains.md)。
- [x] **三层信号提取架构现实核查（`73`）** ✅：⚠️ **Doc 56 结论错误需修正**：`src/gep/signals.js` v1.78.1（444行）确认 Layer 1/2/3 真实存在；`SIGNAL_PROFILES` 加权关键词评分（累积 evidence → 阈值触发）；`_extractLLM` Hub 调用（每 5 cycle 一次，节流）；`_mergeSignals` 三路合并 + observability；`execFileSync` argv 防命令注入；新增 7 个机会信号（`issue_already_resolved`/`openclaw_self_healed`/`empty_cycle_loop_detected`/`explore_opportunity`/`hub_search_miss_with_problem`/`plateau_pivot_required`/`plateau_pivot_suggested`）；详见 [`73`](./73-reality-check-three-layer-signals-and-new-opportunity-signals.md)。

## 实现与数据

- [ ] **Worker SQLite + Chroma 与 Java Postgres + pgvector 的关系**：当前为**并行链路**、**无**代码级自动双写（见 [`12`](./12-bluecortex-api-memory-surface.md) §2）。若产品需要单一真源或跨栈一致检索，需单独设计。
- [ ] **时间半衰 / 重复失败降权**：在 `SearchService` 结果集或 SQL 层落地 [`09`](./09-aspect-bluecortex-bridge.md) §3.2 的排序增强，并定义与现有 `minEpoch` 的关系。详细翻译方案见 [`20-time-decay-and-fail-degradation.md`](./20-time-decay-and-fail-degradation.md)。
- [ ] **Hook / 瘦代理延迟**：对关键路径做一次实测，与 `docs/ARCHITECTURE-zh-CN.md` 中的预算描述交叉验证。

## Evolver 侧（外部源码）

- [x] **EvoMap/evolver 版本同步 + 新增子系统源码分析**（`70`）：
  - ✅ `skill2gep.js`（645行）完整源码分析：逆向蒸馏管道（parseSkillMd / detectForgery / assembleCapsule / 双通道发布）
  - ✅ `selfPR.js`（408行）完整源码分析：多门禁自动 PR 贡献（score/streak/risk/blask 多重门禁 + leakCheck + diffHash 去重）
  - ✅ `validator/` 子系统（~900行）完整源码分析：sandboxExecutor 两层白名单 + BLOCKED_NODE_FLAGS + 隔离 env + stakeBootstrap 磁盘持久化退避 + 独立守护进程
  - ✅ `portable.js` / `claimNudge.js` / `mailboxTransport.js` 综合分析
  - 详见 [`70`](./70-new-subsystems-v148-v178-deep-dive.md)；backlog 版本同步状态 ✅

- [x] **`reflection.js` 模块深度分析**（`59` 新增）：computeReflectionInterval 三态算法（3/5/8）/ shouldReflect 双重条件（周期对齐+冷却30min）/ 预聚合统计（intent分布/gene频率）/ 5问战略复盘框架与精确JSON输出格式 / `buildSuggestedMutations` 信号→参数映射 / JSONL读写机制 / 与innovation.js功能/参数二级互补 / CE自我诊断框架与元级SummaryEntity提案。详见 [`59`](./59-reflection-js-module-deep-dive.md)。
- [x] **ATP（Agent Transaction Protocol）+ Adapters 系统深度分析**（`75` 新增）：ATP Hub Client 275行（proxy/direct 双路由 / 10个 API 端点）/ Merchant Agent 商家模板 118行 / Consumer Agent 消费者模板 157行 / autoBuyer ~200行（三重预算保护 + 24h去重 + cold-start半额）/ hookAdapter 207行（detectPlatform + mergeJsonFile + copyHookScripts + 4平台adapter）/ BlueCortexCE P1（hookAdapter 跨平台适配）/ P2（ATP Market 能力采购）/ P3（资源控制备选）；详见 [`75`](./75-atp-agent-transaction-protocol-and-adapters.md)。
- [ ] **EvoMap/evolver 版本差分**：若本地仓库更新，在对应 `01`–`08` 分片增补差异摘要，**不在此文件**堆长文。
- [x] **`featureFlags.js`（114行 v1.78 新增）**：源码核实 `src/featureFlags.js` 在 origin/main v1.78.1 **不存在**，该条目为 stale 信息。✅ 核实完成（2026-05-03，doc 75）。
- [x] **Doc 56 时间戳勘误** ✅：在 doc 56 文件开头添加时间戳说明，澄清「本文结论对 v1.47 正确，v1.78 已引入三层架构」，并链接到 doc 73。

- [x] **自适应策略策略借鉴**（`45` 新增）：Evolver 每周期动态计算执行策略（repair streak / failure streak / blast radius），CE `ContextService` 可参考实现注入策略动态切换。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §1。
- [x] **候选评估管线借鉴**（`45` 新增）：Evolver 从会话转录提取重复模式（≥3次），生成 Five Questions Shape 候选。CE 可参考实现高频观察模式自动发现。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §2。
- [x] **Git 自修复借鉴**（`26` 已覆盖）：Evolver `self_repair.js` 在进化前自动修复 Git 异常（abort rebase/merge、删除 stale index.lock、可选 hard reset）。CE 可参考实现写入前自检（数据库连接、事务状态）。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §3。
- [x] **`policyCheck.js` 约束系统深度分析**（`42` 新增）：`isConstraintCountedPath` 路径匹配决策树（excludePrefix → includePrefix → extension 优先级）、`computeBlastRadius`（git numstat + untracked 行数统计 + baseline 对比）、`classifyBlastSeverity` 5级分类（hard_cap_breach / critical_overrun / exceeded / approaching_limit / within_limit）、验证命令白名单（`isValidationCommandAllowed` 禁止 `node -e`/shell 操作符）、伦理模式检测（5 种 regex 模式）、`detectDestructiveChanges` 关键文件删除/清空检测。详见 [`42`](./42-policycheck-constraint-system-deep-dive.md)。

- [x] **OMLS 启发式自适应休眠调度借鉴** ✅（`45`）：`idleScheduler.js` `getScheduleRecommendation()` 返回 `idle_seconds`/`intensity`/`sleep_multiplier`/`should_distill`；CE 巡检 cron 可参考实现：低活跃→2小时、中活跃→30分钟、高活跃→15分钟；详见 [`45`](./45-idleScheduler-OMLS-and-llmReview.md) §1。
- [x] **LLM 驱动代码评审借鉴** ✅（`45`）：`llmReview.js` 在 solidify 流程中做 LLM 评审（approved/issues/score/reasoning），与 policyCheck 形成「规则门禁 + 语义评审」双层；CE 可在 ValidationReport 基础上叠加；详见 [`45`](./45-idleScheduler-OMLS-and-llmReview.md) §2。

## 安全与上下文出口（Hermes 对照）

- [ ] **统一围栏 / 写入扫描**：对照 [`../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md)；勾选项与接力见 [`../hermes-memory/11-research-backlog.md`](../hermes-memory/11-research-backlog.md)（避免在本文件重复列验收细节）。

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
