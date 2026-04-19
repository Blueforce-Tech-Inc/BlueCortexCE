# Evolver ↔ BlueCortexCE：方面对照与落地优先级

> **角色**：在 `01`–`08` 模块长文之上，按「方面」压缩为可执行结论；不重复源码摘录。  
> **Evolver**：`EvoMap/evolver`（如 `src/gep/memoryGraph.js`）。  
> **BlueCortexCE**：旁路记忆；架构见仓库 `docs/ARCHITECTURE-zh-CN.md`（瘦代理 + 胖服务器、PostgreSQL + pgvector、`mem_observations` 等）。  
> **本仓库落地映射**（类/迁移/SQL 锚点）：[`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md)。  
> **多线总导航**（Hermes / Evolver / 论文）：[`../memory-research-hub.md`](../memory-research-hub.md)  
> **CE 落地短文族索引**（`09`–`17` 一句话表）：[`index.md`](./index.md) 附录；会话首跳见 [`15`](./15-runtime-integration-surfaces.md) **§5**。

**最后更新**：2026-04-19（§6 → `12` §3.1）

---

## 1. 设计思想：可翻译 vs 不可照搬

| 思想 | Evolver 中的形态 | 翻译到 BlueCortexCE |
|------|-------------------|---------------------|
| **因果可审计** | `memory_graph.jsonl` 中 signal→hypothesis→attempt→outcome | 观察上增加轻量**因果元数据**（如 `parent_observation_id`、`trigger_signal`），不必实现整条 GEP 链 |
| **稳定键与去重** | `computeSignalKey`、`normalizeErrorSignature`、`stableHash` | 写入前对错误栈/异常类做**规范化签名 + 哈希**，合并重复观察或提高检索复现率 |
| **叙事与事件双层** | `narrativeMemory`（MD）+ 图谱事件 | **会话摘要**（短）+ **近期向量命中**（相关）；显式 token 预算与降级顺序 |
| **信号驱动优先级** | learningSignals、plateau、recurring_error 等 | **观察类型 / 质量分 / 使用反馈**驱动排序与「是否值得推广」；非基因突变 |
| **非阻塞旁路** | assetCallLog、审计 JSONL 等 try/catch 静默 | 与 CE 一致：**Hook 快路径**、日志与审计**不得阻塞**主请求（见架构文档中的 Hook 时延约束） |
| **联邦与 Hub** | hubSearch、A2A、directory | CE 当前以单租户为主：仅预留「多实例同步 / 外部降权」**概念**，不必实现 Evolver 全栈联邦 |

**不应照搬**：Gene/Capsule 突变、Personality 自然选择、Hub 积分经济——与旁路记忆产品边界不符，除非产品路线明确变更。

---

## 2. 与 BlueCortexCE 架构的对应关系

| CE 概念 | Evolver 中相近概念 | 借鉴要点 |
|---------|-------------------|----------|
| 瘦代理（快速 ACK） | 本地 append、异步写日志 | 保持「先响应、后重处理」；重逻辑在胖服务器与队列 |
| `mem_observations` | memoryGraph 事件行、assetCallLog 行 | **ACID** 与多维索引优于纯 JSONL；保留「append-only 语义」时可通过**只追加新行 + 不可变 content_hash** 表达 |
| 多列 embedding / 全文 | 尾部扫描 + Jaccard 等启发式 | CE 已强在 **SQL + pgvector + tsvector**；补充 **query 侧归一化**（对齐 signal key 思想） |
| Session / summary | narrativeMemory、trim | 对齐 **trimNarrative**：摘要长度上限、块优先级截断（见各分片中 `prompt.js` / strategy 讨论） |
| 项目隔离 `project_path` | cwd 哈希、deviceId | 稳定 **租户/工作区** 维度；节点 ID、环境变量覆盖等见 `06`–`07` 中 deviceId 借鉴表 |

---

## 3. 分方面对照（简表）

### 3.1 存储

- **Evolver**：JSONL 追加 + 部分 `memory_graph_state.json`；原子写 JSON 多用 tmp+rename。
- **CE**：常见 **两条并行链**（非自动双写）：**(1) Java**：PostgreSQL `mem_observations` + pgvector，经 ingestion / 瘦代理进库（[`15`](./15-runtime-integration-surfaces.md) §4）；**(2) Bun Worker**：本地 SQLite + Chroma（[`12`](./12-bluecortex-api-memory-surface.md) §2）。产品级「schema 真源」以 `docs/ARCHITECTURE-zh-CN.md` 为准，对照 Evolver 时须**先分清栈**。
- **借鉴**：**规范化签名列或 `extracted_data` 字段**承载 errsig 归一化；**内容哈希**去重。不把生产存储改回「单文件 JSONL」。

### 3.2 检索

- **Evolver**：窗口扫描、图边权重、衰减。
- **CE**：过滤 + 向量序 + 全文。
- **借鉴**：排序公式中增加 **时间衰减**、**重复失败降权**（见 [01](./01-intro-toc-memory-through-curriculum.md) §8）；对「相似错误」用签名键做 **pre-filter**。

### 3.3 上下文注入

- **Evolver**：GEP prompt 块、策略链、reflection。
- **CE**：context generate API、插件注入。
- **实现侧注**（避免误解）：**Java `generateContext` / 部分 inject** 以 **type/concept + 时间线** 为主。**同名** **`POST /api/context/semantic`** 在 **Java** 走 `SearchService`（pgvector），在 **Bun Worker** 走 Chroma（`SearchRoutes`）；Claude Code **`session-init` Hook** 默认经 **Bun Worker** 调用语义注入（`CLAUDE_MEM_SEMANTIC_INJECT`）。**OpenClaw Java 插件**多直连 **Spring**（配置项 `workerPort` 实为后端端口；`/actuator/health`）。进程判别与端口见 [`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md)；路径总表见 [`10`](./10-aspect-bluecortex-implementation-map.md) §3、[`12`](./12-bluecortex-api-memory-surface.md)。
- **借鉴**：**两层加载**——先 session 摘要，再 top-k 观察；**失败 streak / 饱和** 时切换策略（search-first、减少重复检索），对应关系见 `02`–`04` 中 hubSearch、evolve 讨论。**双栈一致性 / token 预算** 见 [`11-research-backlog.md`](./11-research-backlog.md)。

### 3.4 可观测与审计

- **Evolver**：executionTrace、assetCallLog、validation 报告。
- **CE**：API 日志、`step_number`、`platform_source`。
- **借鉴**：对「为何命中这条观察」增加可选 **request_id / retrieval_profile** 写入 `extracted_data`；**append-only 审计日志**（可单独表或对象存储）对齐 assetCallLog 思路。

### 3.5 安全与隐私

- **Evolver**：sanitize、crypto、隐私密封执行。
- **CE**：脱敏在管道与导出层实现。
- **借鉴**：**写入前脱敏**与 **导出脱敏** 分层；敏感字段不进向量或单独列策略。联邦相关仅在多租户同步场景参考 `05`–`07`。
- **Hermes 对照（出口 / 扫描）**：内置型 `_scan_memory_content`、`<memory-context>` 消毒等与 CE 现状差异见 [`../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md)；可勾选项见 [`../hermes-memory/11-research-backlog.md`](../hermes-memory/11-research-backlog.md)。

---

## 4. 可执行优先级（P0 / P1 / P2）

与 [01](./01-intro-toc-memory-through-curriculum.md) **§8** 及各分片「BlueCortexCE 借鉴点」表格一致方向，压缩为实施序列：

| 优先级 | 动作 | 说明 |
|--------|------|------|
| **P0** | 错误/栈 **规范化签名 + 稳定哈希** | 减少重复观察与向量噪声；对齐 `memoryGraph.js` |
| **P0** | 检索排序：**时间衰减** + **重复模式降权** | 对齐图谱边权重与「历史抑制」思想 |
| **P0** | **非阻塞**审计与辅助写入 | 对齐 assetCallLog / Hook 时延要求 |
| **P1** | 观察 **归因字段**（可选 `parent_id` / `cause_type`） | 轻量因果，非完整 GEP |
| **P1** | Context **摘要优先、向量次之** 的预算与降级 | 对齐 narrative + trim |
| **P1** | **Search-first / 两阶段**（轻量过滤再昂贵嵌入） | 对齐 hubSearch 与 `02` 中策略 |
| **P2** | Laplace 等 **统计平滑** 用于质量/成功率 | 若产品层有「成功率」展示 |
| **P2** | 多语言信号检测、固定上限裁剪 | 与 CE 国际化与 summary 表策略对齐 |
| **P2** | 外部来源结果 **置信度降权** | 对齐 A2A 外部资产 ×0.6 等（仅当存在多源检索） |

---

## 5. 反模式（避免）

1. **把 GEP 循环写进数据库**：旁路记忆不需要 hypothesis/mutation 表；用少量元数据表达「从哪类信号衍生」即可。
2. **用 JSONL 替代 Postgres 做主存储**：失去查询与并发优势；JSONL 仅适合 **可选审计导出**。
3. **同步阻塞 Hook**：与 CE 架构目标冲突。
4. **无差别实现 Hub/A2A**：成本高；先完成单租户体验与 **client_id / project** 隔离。

---

## 6. 深入阅读索引（模块 → 方面）

| 若关心… | 优先读 |
|---------|--------|
| 事件 kind、signal key | [01](./01-intro-toc-memory-through-curriculum.md) §1 |
| 叙事与裁剪 | [01](./01-intro-toc-memory-through-curriculum.md) §2 |
| 信号与学习 | [01](./01-intro-toc-memory-through-curriculum.md) §3–§5，[03](./03-skillpublisher-through-signals-v07.md) |
| 两阶段搜索、缓存 | [02](./02-skilldistiller-through-evolution-v04.md) Hub 相关节，[06](./06-assetcalllog-through-questiongen-v12.md) |
| 执行轨迹与审计 JSONL | [05](./05-sanitize-through-execution-trace-v10.md)–[06](./06-assetcalllog-through-questiongen-v12.md) |
| 本地/远程适配、降级 | [06](./06-assetcalllog-through-questiongen-v12.md) memoryGraphAdapter |
| 空闲调度与后台任务 | [07](./07-idle-through-skillpublisher-v14.md) idleScheduler |
| Hermes 内置记忆、围栏、Prefetch | [`../hermes-memory/index.md`](../hermes-memory/index.md)；注入 [`04`](../hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md)、安全 [`05`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md) |
| CE 上下文 **产出** 调用链（`generateContext` vs `semantic`） | [`14-context-output-pipeline-sketch.md`](./14-context-output-pipeline-sketch.md) |
| **`POST /api/context/semantic`：JSON 契约与双栈差异** | [`12`](./12-bluecortex-api-memory-surface.md) **§1.1** |
| **Hook → Worker 基址**（`workerHttpRequest`、37777 与 Java 同号） | [`15`](./15-runtime-integration-surfaces.md) **§2.1** |
| **集成客户端 → 默认 Worker 还是 Java** | [`15`](./15-runtime-integration-surfaces.md) **§2** · 记忆 HTTP 调用方 [`12`](./12-bluecortex-api-memory-surface.md) **§3** |
| **Hook / MCP / Cursor 等 → Worker 的细路径** | [`12`](./12-bluecortex-api-memory-surface.md) **§3.1**（`workerHttpRequest` 索引） |
| **Bun Worker vs Java**（谁监听 37777、OpenClaw 命名陷阱） | [`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md) |
| **会话首跳**（`POST /api/sessions/init` Worker vs `POST /api/session/start` Java） | [`15`](./15-runtime-integration-surfaces.md) **§5**；数据平面旁注 [`12`](./12-bluecortex-api-memory-surface.md) §2 |
| **Java 摄入 / 写入**（wrapper → `IngestionController`） | [`16-ingestion-write-path-sketch.md`](./16-ingestion-write-path-sketch.md) |
| **Java 会话 start**（`/api/session/start`） | [`17-session-lifecycle-java-sketch.md`](./17-session-lifecycle-java-sketch.md) |
| Evo-Memory 论文（Refine / WriteBack / 基准） | [`../evo-memory-paper-analysis/index.md`](../evo-memory-paper-analysis/index.md) |

---

## 7. 下一步（产品/文档）

1. **实现层**：在 CE 后端或 ingestion 管道落地 P0（签名哈希、排序因子、非阻塞审计）；优先对照 [`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md) 中的**已实现 vs 缺口**表选题。
2. **文档层**：Evolver 新版本模块出现时，在对应 `0x` 分片增补；**本文件只追加一行优先级或交叉引用**，保持方面文独立、短小。新分析线优先在 [`../memory-research-hub.md`](../memory-research-hub.md) 登记入口。
3. **验证**：对照 `docs/ARCHITECTURE-zh-CN.md` 数据流，确认每条 P0 不破坏瘦代理延迟预算。
