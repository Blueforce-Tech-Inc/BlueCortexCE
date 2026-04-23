# 研究 / 决策 backlog（可接力）

> **角色**：给后续人类或 Agent 的**短队列**——可勾选、可补链接；**不**重复 [`09`](./09-aspect-bluecortex-bridge.md) 的 P0/P1 定义本身。  
> **最后更新**：2026-04-19 18:29（`24` Gene/Strategy 层新增：Gene Pool + selector + mutation + strategy presets）

---

## 使用方式

- 完成一项：在条目前打 `[x]`，可选补一行「结论链接」（PR、commit、或 `0x`/`10` 增补说明）。
- 条目过长：迁到独立 `16+*.md` 或写入对应 `0x` 分片，此处只保留一行指针。

---

## 架构与产品

- [ ] **Gene/Strategy 层对 BlueCortexCE 的借鉴**：
  - Strategy presets（repair/optimize/innovate 比例）→ "观察注入策略"控制注入比例
  - 多因子 Gene selector（exact + semantic + epigenetic + learning）→ `SearchService` 增强：exact signal filter + bag-of-words fallback + capability gap boost
  - Mutation safety → "观察风险分级"（error=low / capability_gap=medium / user_feature_request=high）
  - 详见 [`24`](./24-gene-strategy-layer.md)
- [ ] **MCP 是否暴露与 Hook 对齐的 `semantic` 能力**：产品与安全评估（现状：MCP **无**同名工具，见 [`12`](./12-bluecortex-api-memory-surface.md) **§3.2**）。
- [x] **Hook 是否调用 `semantic`**：`session-init` 在 `CLAUDE_MEM_SEMANTIC_INJECT=true`（默认）且 `prompt≥20` 时调用 **worker** `POST /api/context/semantic`（`webui/src/cli/handlers/session-init.ts`）。见 [`12`](./12-bluecortex-api-memory-surface.md) §3。
- [ ] **Java（pgvector）与 Worker（Chroma）语义结果一致性**：同名路由、异存储；全 Java / 混合部署下的对齐、评测与文档。HTTP 契约与字段对照见 [`12`](./12-bluecortex-api-memory-surface.md) **§1.1**；实现锚点另见 [`10`](./10-aspect-bluecortex-implementation-map.md) §3。
- [ ] **语义注入与时间线并存的 token 预算**：`additionalContext` 与主上下文拼接策略、关闭开关与延迟预算。
- [ ] **错误类观察的 `extracted_data` 约定**：是否统一 `error_signature`（栈归一化）字段名与归一规则，并与 `content_hash` 去重策略分工。（对齐 Evolver `normalizeErrorSignature` 思想）
  - **源码验证完成**：`memoryGraph.js` §27 定义 `normalizeErrorSignature`：Windows/Unix路径→`<path>`、十六进制→`<hex>`、数字→`<n>`，截断220字符后 `stableHash`。
  - **BlueCortexCE 落点**：`ObservationEntity.extractedData` JSONB 已有，dedup 用 `contentHash`（精确哈希），两类机制可共存：`extractedData.error_sig_norm` 存规范化签名用于"同类错误聚合"检索；`content_hash` 保持精确去重。
  - **实施路径**：参考 [`21`](./21-signal-taxonomy-and-gene-selection-memory.md) §2 的 `normalizeErrorSignature` 实现；在 `AgentService.saveObservation` 路径对 `type=error` 观察写入规范化签名。
  - **✅ 提案完成**：见 [`22`](./22-error-sig-norm-implementation-proposal.md)（规范化算法 + JSONB schema + 写入路径 + 实施检查清单）
- [ ] **`inferOutcomeEnhanced` 的 baseline vs current delta 机制**：Evolver 用 `recent_error_count` 差值和 `scan_ms` 差值微调 outcome score（各 ±0.12 / ±0.06）。BlueCortexCE 暂无对应机制；若引入"观察质量评分"，可参考此 delta 启发式。另见 PRM 多步骤评分 [`25`](./25-advanced-patterns-prm-epigenetic-antipattern.md) §1（8 阶段加权合成）。
  - 源码：`memoryGraph.js` §560–§590，`clamp01(score)` 保证边界。
- [ ] **双聚合链（signal×gene 边 vs gene 先验）**：Evolver `getMemoryAdvice` 维护两条独立衰减链：`(signal_key, gene_id)` 边（30天半衰）和 `gene_id` 先验（45天半衰），加权组合 `best + prior*0.12` 或纯先验 `prior*0.4`。BlueCortexCE `SearchService` 暂无此双链机制；若引入"历史检索成功率"，可参考此分层衰减设计。

## 实现与数据

- [ ] **Worker SQLite + Chroma 与 Java Postgres + pgvector 的关系**：当前为**并行链路**、**无**代码级自动双写（见 [`12`](./12-bluecortex-api-memory-surface.md) §2）。若产品需要单一真源或跨栈一致检索，需单独设计。
- [ ] **时间半衰 / 重复失败降权**：在 `SearchService` 结果集或 SQL 层落地 [`09`](./09-aspect-bluecortex-bridge.md) §3.2 的排序增强，并定义与现有 `minEpoch` 的关系。详细翻译方案见 [`20-time-decay-and-fail-degradation.md`](./20-time-decay-and-fail-degradation.md)。
- [ ] **Hook / 瘦代理延迟**：对关键路径做一次实测，与 `docs/ARCHITECTURE-zh-CN.md` 中的预算描述交叉验证。

## Evolver 侧（外部源码）

- [ ] **EvoMap/evolver 版本差分**：若本地仓库更新，在对应 `01`–`08` 分片增补差异摘要，**不在此文件**堆长文。

- [ ] **自适应策略策略借鉴**：Evolver 每周期动态计算执行策略（repair streak / failure streak / blast radius），CE `ContextService` 可参考实现注入策略动态切换。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §1。
- [ ] **候选评估管线借鉴**：Evolver 从会话转录提取重复模式（≥3次），生成 Five Questions Shape 候选。CE 可参考实现高频观察模式自动发现。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §2。
- [ ] **Git 自修复借鉴**：Evolver 在进化前自动修复 Git 异常。CE 可参考实现写入前自检（数据库连接、事务状态）。详见 [`26`](./26-runtime-orchestration-adaptive-policy-candidates.md) §3。
- [x] **`policyCheck.js` 约束系统深度分析**（`42` 新增）：`isConstraintCountedPath` 路径匹配决策树（excludePrefix → includePrefix → extension 优先级）、`computeBlastRadius`（git numstat + untracked 行数统计 + baseline 对比）、`classifyBlastSeverity` 5级分类（hard_cap_breach / critical_overrun / exceeded / approaching_limit / within_limit）、验证命令白名单（`isValidationCommandAllowed` 禁止 `node -e`/shell 操作符）、伦理模式检测（5 种 regex 模式）、`detectDestructiveChanges` 关键文件删除/清空检测。详见 [`42`](./42-policycheck-constraint-system-deep-dive.md)。

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
