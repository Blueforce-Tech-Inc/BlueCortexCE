# 研究 / 决策 backlog（可接力）

> **角色**：给后续人类或 Agent 的**短队列**——可勾选、可补链接；**不**重复 [`09`](./09-aspect-bluecortex-bridge.md) 的 P0/P1 定义本身。  
> **最后更新**：2026-04-19（链至 `12` §1.1）

---

## 使用方式

- 完成一项：在条目前打 `[x]`，可选补一行「结论链接」（PR、commit、或 `0x`/`10` 增补说明）。
- 条目过长：迁到独立 `16+*.md` 或写入对应 `0x` 分片，此处只保留一行指针。

---

## 架构与产品

- [x] **Hook 是否调用 `semantic`**：`session-init` 在 `CLAUDE_MEM_SEMANTIC_INJECT=true`（默认）且 `prompt≥20` 时调用 **worker** `POST /api/context/semantic`（`webui/src/cli/handlers/session-init.ts`）。见 [`12`](./12-bluecortex-api-memory-surface.md) §3。
- [ ] **Java（pgvector）与 Worker（Chroma）语义结果一致性**：同名路由、异存储；全 Java / 混合部署下的对齐、评测与文档。HTTP 契约与字段对照见 [`12`](./12-bluecortex-api-memory-surface.md) **§1.1**；实现锚点另见 [`10`](./10-aspect-bluecortex-implementation-map.md) §3。
- [ ] **语义注入与时间线并存的 token 预算**：`additionalContext` 与主上下文拼接策略、关闭开关与延迟预算。
- [ ] **错误类观察的 `extracted_data` 约定**：是否统一 `error_signature`（栈归一化）字段名与归一规则，并与 `content_hash` 去重策略分工。（对齐 Evolver `normalizeErrorSignature` 思想）

## 实现与数据

- [ ] **Worker SQLite + Chroma 与 Java Postgres + pgvector 的关系**：当前为**并行链路**、**无**代码级自动双写（见 [`12`](./12-bluecortex-api-memory-surface.md) §2）。若产品需要单一真源或跨栈一致检索，需单独设计。
- [ ] **时间半衰 / 重复失败降权**：在 `SearchService` 结果集或 SQL 层落地 [`09`](./09-aspect-bluecortex-bridge.md) §3.2 的排序增强，并定义与现有 `minEpoch` 的关系。
- [ ] **Hook / 瘦代理延迟**：对关键路径做一次实测，与 `docs/ARCHITECTURE-zh-CN.md` 中的预算描述交叉验证。

## Evolver 侧（外部源码）

- [ ] **EvoMap/evolver 版本差分**：若本地仓库更新，在对应 `01`–`08` 分片增补差异摘要，**不在此文件**堆长文。

## 安全与上下文出口（Hermes 对照）

- [ ] **统一围栏 / 写入扫描**：对照 [`../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md)；勾选项与接力见 [`../hermes-memory/11-research-backlog.md`](../hermes-memory/11-research-backlog.md)（避免在本文件重复列验收细节）。

---

## 与其它文件的边界

| 文件 | 放什么 |
|------|--------|
| [`09`](./09-aspect-bluecortex-bridge.md) | 已定型的方面对照、优先级、反模式 |
| [`10`](./10-aspect-bluecortex-implementation-map.md) | 本仓库**已实现**与**缺口**的代码锚点 |
| [`12`](./12-bluecortex-api-memory-surface.md) | **HTTP**、**§1.1 `semantic` 契约**、**§2 写入/数据平面**、双栈、调用方 |
| [`14`](./14-context-output-pipeline-sketch.md) | **Java** 侧上下文产出链（非 worker） |
| [`15`](./15-runtime-integration-surfaces.md) | Worker/Java 判别、**§4** wrapper→Java、**§5** 会话首跳（`sessions/init` ∥ `session/start`） |
| [`16`](./16-ingestion-write-path-sketch.md) | **Java** 侧瘦代理摄入 / 观察写入链 |
| [`17`](./17-session-lifecycle-java-sketch.md) | **Java** 侧 `/api/session/start` 与 session-end 对照 |
| **本文件** | 未决课题、可选实验、待勾选 |
| [`staging.md`](./staging.md) | 极短草稿，定稿即删或迁入上列 |
| [`../memory-research-hub.md`](../memory-research-hub.md) | Evolver / Hermes / 论文线 **总导航** |
