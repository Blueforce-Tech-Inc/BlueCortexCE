# Evolver ↔ BlueCortexCE：按「方面」的对照与可借鉴动作（演进稿）

> **文档角色**：在既有 `01`–`08` 模块级分析之上，按任务说明中的**方面**（架构概览、存储、检索、上下文、可观测性等）做一页式对照，便于旁路型记忆产品直接落地，而不重复粘贴源码细节。  
> **BlueCortexCE 依据**：仓库内 `docs/ARCHITECTURE.md` / `docs/ARCHITECTURE-zh-CN.md` 与 `mem_observations` 等迁移脚本；**Evolver 依据**：本地 `EvoMap/evolver` 源码（如 `src/gep/memoryGraph.js`）及本目录 `01`–`08` 分片。

**最后更新**：2026-04-19

---

## 1. 架构概览

| 方面 | Evolver | BlueCortexCE（旁路记忆） |
|------|---------|---------------------------|
| 定位 | GEP 驱动的**自进化**运行时：基因/胶囊/突变/验证闭环 | **外挂记忆服务**：会话与观察落库，经 API/SDK 注入宿主智能体上下文 |
| 记忆在系统中的角色 | 与进化协议**强耦合**（signal→hypothesis→attempt→outcome） | 与宿主**解耦**：观察可检索、可排序，不驱动代码突变 |
| 信任边界 | 本地/联邦 Hub、策略与验证链 | 多项目隔离、`project_path`、质量分与反馈字段 |

**可借鉴动作**：把 Evolver 的「因果链事件」思想，翻译为 BlueCortexCE 的**观察类型 + source + extracted_data** 上的轻量因果元数据（不必实现 GEP）。

---

## 2. 存储设计

| 方面 | Evolver | BlueCortexCE |
|------|---------|----------------|
| 主形态 | `memory_graph.jsonl` **append-only**；状态侧写 `memory_graph_state.json` | PostgreSQL 表 **`mem_observations`**（及 `mem_sessions` 等），**ACID**、可迁移 |
| 模式 | 行即事件，长文件尾部追加 | 行即观察，B 树 + 向量索引 |
| 内容寻址 / 哈希 | 信号键、错误签名归一化（见 `memoryGraph.js` 中 `normalizeErrorSignature` / `stableHash`） | `content_hash`、多列 `embedding_*` 与 `embedding_model_id` |

**可借鉴动作**：对「重复错误/相似栈」类观察，可引入 **规范化签名 + 哈希键**（Evolver 已有成熟套路），在写入前折叠或关联，减少向量空间噪声；实现上放在后端或 ingestion 管道，而非改存储引擎为 JSONL。

---

## 3. 检索机制

| 方面 | Evolver | BlueCortexCE |
|------|---------|----------------|
| 查询 | 典型为**尾部扫描 + 内存聚合**（如 tail 窗口） | **SQL**：时间/项目过滤 + **pgvector**（HNSW，`<=>`）+ **tsvector** 全文 |
| 语义 | Jaccard、衰减、边期望成功等图启发式（见 `01` §1） | 多维度 embedding 列，按模型维度选列检索 |
| 冷启动 | 文件即存在即可 append | 需迁移、索引与 embedding 回填 |

**可借鉴动作**：Evolver 的 **signal key 稳定化** 可与 BlueCortexCE 的 **semantic + lexical 混合检索**并行：检索前将 query 侧做同类归一化，提高跨会话复现率。

---

## 4. 上下文管理与叙事

| 方面 | Evolver | BlueCortexCE |
|------|---------|----------------|
| 叙事 | `narrativeMemory` MD、裁剪与摘要（`01` §2） | Session 摘要、观察列表注入 prompt；**relevance_count** 等使用反馈 |
| 体积控制 | `trimNarrative` 等显式裁剪 | 服务端/插件侧 token 预算与 top-k |

**可借鉴动作**：借鉴「**摘要 + 近期事件**」双层加载策略，在 WebUI/SDK 层定义与 Evolver 类似的**预算常量**与降级路径（先摘要后向量命中）。

---

## 5. 并发、一致性与演进

| 方面 | Evolver | BlueCortexCE |
|------|---------|----------------|
| 并发 | 单进程文件 append；原子写 JSON 用 tmp+rename 模式（多处模块） | 数据库事务、连接池 |
| 演进 | 协议与基因版本演进 | **Flyway 迁移**（V1…V18+），列演进可追踪 |

**可借鉴动作**：Evolver 的 **writeJsonAtomic** 思路已体现在 DB 事务中；文档侧只需强调：**旁路服务以迁移为唯一 schema 真源**，与 Evolver 的文件型演进对照写进发布说明即可。

---

## 6. 可观测性与审计

| 方面 | Evolver | BlueCortexCE |
|------|---------|----------------|
| 轨迹 | executionTrace、assetCallLog 等（`05`–`07`） | API 日志、可选导出；观察带 **step_number**、**platform_source** |
| 联邦 | Hub / A2A / directory（多分片） | 当前以单租户后端为主，可预留「同步通道」概念 |

**可借鉴动作**：若产品需要「为何检索到这条观察」，可在观察上增加**轻量 trace id** 或复用 `extracted_data` 记录检索特征；不必一次上联邦。

---

## 7. 本轮结论与下一步（供 `staging` 接力）

1. **优先落地**：错误签名归一化 + 稳定键（存储仍用 Postgres）。  
2. **次优先**：prompt 注入策略上明确「摘要 vs 向量命中」两层（对齐 narrative 思想）。  
3. **文档**：后续 Evolver 新版本模块若出现，优先在对应 `0x` 分片增补，并在此文件**仅增加一行对照**，避免再次单体膨胀。
