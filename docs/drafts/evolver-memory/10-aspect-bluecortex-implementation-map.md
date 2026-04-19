# BlueCortexCE 实现映射（Evolver 对照用）

> **角色**：把 [`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md) 中的优先级与 Evolver 概念，**锚定到本仓库路径**，便于实现与 code review；不替代 `docs/ARCHITECTURE-zh-CN.md` 的全貌说明。  
> **最后更新**：2026-04-19（§3 MCP 注 + `12` §3.2）

---

## 1. Schema 与迁移（真源）

| 主题 | 迁移文件（示例） | 说明 |
|------|------------------|------|
| 观察主表、`tsvector` | `backend/src/main/resources/db/migration/V1__init_schema.sql` | 基线；后续列由多版迁移叠加 |
| 多维向量、HNSW | `V2__multi_dimension_embeddings.sql` | `embedding_768` / `1024` / `1536`、`embedding_model_id` |
| 内容哈希去重 | `V8__add_observation_content_hash.sql` | `content_hash VARCHAR(16)` + 索引 |
| 质量与反馈 | `V11__observation_quality.sql` 等 | `quality_score`、`feedback_type` 等 |
| 来源与结构化载荷 | `V14__observation_source_and_extracted_data.sql` | `source`、`extracted_data`（JSONB） |
| 使用反馈 | `V17__observation_feedback.sql` 等 | `relevance_count`、`generated_by_model` 等 |
| 多平台 | `V18__add_platform_source.sql` | `platform_source` |

实体映射：`backend/src/main/java/com/ablueforce/cortexce/entity/ObservationEntity.java`。

---

## 2. 已实现能力与代码锚点

下列与 Evolver 文档中的「稳定键 / 向量检索 / 混合检索 / 异步重处理 / 质量门」**部分同构**，可直接在 PR 中引用。

| 能力 | 说明 | 主要位置 |
|------|------|----------|
| **内容哈希 + 短窗去重** | 对 title/narrative/facts/concepts 拼接后 SHA-256，取 16 字符；30s 内同 `project_path` 重复则返回已有行 | `AgentService.saveObservation`、`ObservationRepository.findDuplicateByContentHash` |
| **嵌入写入** | 按当前 `embeddingService` 维度写入 `embedding_*` | `AgentService.generateEmbedding` |
| **语义检索** | `embedding_* IS NOT NULL` + `<=>` + `created_at_epoch > :minEpoch` | `ObservationRepository.semanticSearch768` 等 |
| **混合检索（向量 + 全文）** | CTE 合并语义与 `tsvector`，去重后按分数排序 | `ObservationRepository.hybridSearch` |
| **质量分** | LLM 结构化打分等 | `LlmQualityScorer.java`；与 `quality_score` 列配合 |
| **异步队列（胖路径）** | 待处理消息持久化、重试 | `mem_pending_messages`；`AgentService.processPendingMessage`、`PendingMessageRepository` |
| **上下文拼装** | 项目级 context 生成、缓存刷新 | `ContextService.generateContext*`；`ContextCacheService`；`SessionController` / `ContextController` 调用链 |

---

## 3. 三条读出路径：时间线注入、语义注入、搜索 API

易与 Evolver「叙事 MD + 因果图」混谈；本仓库中 **默认时间线注入**、**可选的按 query 语义注入**、**搜索列表** 分流实现。

| 路径 | 行为 | 主要代码 | 与 Evolver 类比 |
|------|------|----------|-----------------|
| **时间线注入（默认）** | 按 **type + concept** 过滤，取最近 N 条观察，与 **session summary** 混排；**不**走 `SearchService` | `ContextService.generateContext*` → `ObservationRepository.findByTypeAndConcepts`（及多项目/worktree） | 「裁剪 narrative + 时间序事件」，**非**当前用户 query 的语义检索 |
| **按 query 的语义注入** | **Java**：`q` → **`SearchService.search`**（pgvector）→ 段落（V17） | `ContextController` → `POST /api/context/semantic` | 接近「按当前问题检索再写入 prompt」 |
| **（同上路由，Worker）** | **Node worker**：同名路由 → **`SearchManager` + Chroma** | `webui/.../SearchRoutes.ts` | 与 Java **同源路径、异存储**；Claude Code Hook 默认走此栈，见 [`12`](./12-bluecortex-api-memory-surface.md) **§1.1**；各客户端默认进程见 [`15`](./15-runtime-integration-surfaces.md) **§2** |
| **搜索 API（列表）** | **Java**：与语义注入 **共用** `SearchService`；返回结构化结果 | `ViewerController` → `GET /api/search` | 「按需检索」；Worker 另有并行实现时需对照 `SearchManager` |

**注（MCP）**：**`mcp-server`** 的 **`search` / `timeline`** 工具委托 Worker **`GET /api/search`**、**`/api/timeline`**（`TOOL_ENDPOINT_MAP`），**不是** **`POST /api/context/semantic`**（按当前 prompt 生成「可注入 Markdown 块」仍走 §1 / Hook）。**`smart_search` / `smart_outline` / `smart_unfold`** 在 MCP 进程内 **本地** 解析代码，**不经** Worker 记忆 HTTP。工具级对照表见 [`12-bluecortex-api-memory-surface.md`](./12-bluecortex-api-memory-surface.md) **§3.2**。

**结论**：[`09`](./09-aspect-bluecortex-bridge.md) 中「摘要 + 向量命中」：**Java `generateContext`** 仍以 **时间线** 为主；**带当前问题的语义块**在 Claude Code 默认路径上常由 **`session-init` → Bun Worker `POST /api/context/semantic`**（Chroma；`CLAUDE_MEM_SEMANTIC_INJECT`，见 [`12`](./12-bluecortex-api-memory-surface.md) §2）。**OpenClaw 插件**则多直连 **Java**（见 [`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md)）。**双栈一致性、token 预算**，见 [`11`](./11-research-backlog.md)。

---

## 4. 缺口与调研方向（相对 Evolver / `09` P0）

| Evolver / `09` 项 | 本仓库现状 | 后续可落地位置（建议） |
|---------------------|------------|-------------------------|
| **错误栈规范化签名**（`normalizeErrorSignature` 类） | `content_hash` 基于叙事字段，**不**等价于「栈路径/行号归一」后的 errsig | 在解析工具输出失败时写入 `extracted_data.error_signature` 或专用列；去重/聚类读该键 |
| **显式时间半衰排序** | `minEpoch` 限制窗口；`hybridSearch` 用向量距离与 `ts_rank`，**未**见统一 `exp(-λ·age)` | 排序在 Service 层对结果列表加权，或 SQL 中增加时间项（注意与 HNSW 序的配合） |
| **轻量因果链**（parent / trigger） | `refined_from_ids` 等偏「合并溯源」 | 若引入 `parent_observation_id`，需迁移 + `ObservationEntity` + 写入路径（ingestion/API） |
| **signal key 风格的查询归一化** | 检索入口为自然语言 + 向量 | 在构造 `hybridSearch` 的 `query` 前增加与写入侧一致的规范化（仅对 error 类观察优先） |

以上**不要求**一次做完；优先与 [`09`](./09-aspect-bluecortex-bridge.md) 的 **P0**（签名语义对齐、排序因子、非阻塞）对齐。可接力课题见 [`11-research-backlog.md`](./11-research-backlog.md)。

---

## 5. 相关阅读顺序

1. 方面优先级与反模式：[`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md)  
2. Evolver 模块细节：`01`–`08` 分片  
3. 产品级架构与数据流：`docs/ARCHITECTURE-zh-CN.md`  
4. 待调研/决策队列：[`11-research-backlog.md`](./11-research-backlog.md)  
5. HTTP 与数据平面：[`12-bluecortex-api-memory-surface.md`](./12-bluecortex-api-memory-surface.md)（**§1.1** `semantic` · **§2** 写入 · **§3.1–§3.2** Hook/MCP）  
6. Java **产出**链速写（`generateContext` / `semantic` / ICL）：[`14-context-output-pipeline-sketch.md`](./14-context-output-pipeline-sketch.md)  
7. Java **摄入 / 写入**链速写（`IngestionController` → `AgentService`）：[`16-ingestion-write-path-sketch.md`](./16-ingestion-write-path-sketch.md)  
8. Java **会话 start / end** 速写：[`17-session-lifecycle-java-sketch.md`](./17-session-lifecycle-java-sketch.md)  
9. **Bun Worker vs Java** 集成面（**§2** 客户端、**§5** 会话首跳）：[`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md)  
10. Hermes 参照与 **CE 记忆注入面、`/api/context` 端点**（与上文「上下文拼装」互补）：[`../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md`](../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md)  
11. **上下文安全缺口**（对照 Hermes 扫描）：[`../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md)
