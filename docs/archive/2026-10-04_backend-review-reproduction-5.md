# Backend Review — Reproduction Transcripts, batch 5

> **Archived**: 2026-10-04 (round 244) from [`../drafts/backend-review-findings.md`](../drafts/backend-review-findings.md).
> **Contents**: the `Evidence` / `Reproduction` / `规模` sections of P1-1, P2-8, P2-28,
> P2-29 (two sections), P2-32 and P2-34, moved **verbatim**.
> **Why this batch**: batch 4 freed headroom, but round 244's P2-40 pushed the file back
> over `MAX_LINES=1000` for the eleventh time. Same rule as batch 4 and the reasoning is
> unchanged: a measured transcript is **reproducible evidence**, not the reasoning behind a
> decision, so **Scope / Problem / Status stay in the working file** and each moved section
> is replaced by a one-line pointer. Every transcript names its date, endpoint and
> technique, so it is reproducible on demand.
> **Continues** batch 4 (`2026-10-04_backend-review-reproduction-4.md`) and provenance
> batches 1-3, all of which are immutable.
> **Verbatim equality against `git show HEAD` was verified before the source lines were removed.**
> **Do not modify this file.**

- **Reproduction** (deterministic, verified 2026-10-02): 现有 `CortexSessionContextBridgeAdvisorTest.adviseStream_whenConversationIdSet_...` 用 `Flux.just(response)`，在订阅线程同步发射，因此恰好绕开跨线程场景。改用 `Flux.just(response).subscribeOn(Schedulers.boundedElastic())` 后实测：
  ```
  PROBE callingThread    = main
  PROBE emitterThread    = boundedElastic-1
  PROBE visibleOnEmitter = false      <-- 后果 1
  PROBE activeAfter      = true       <-- 后果 2：调用线程仍处于激活态
  PROBE sessionIdAfter   = conv-x     <-- 且绑定的是上一个会话
  ```
- **Options**: (a) 改为 Reactor Context 传播——bridge 用 `contextWrite` 写入会话信息，`CortexSessionContext` 暴露一个 `ThreadLocalAccessor` 并在 starter 中启用 `Hooks.enableAutomaticContextPropagation()`；(b) 退回「只在调用线程的装配窗口内持有上下文」并明确声明流式下不做工具捕获；(c) 把流式会话标识改为显式参数贯穿 `ToolCallingManager`。
- **Status**: ⏸已记录，本轮**不修复**。三个选项都改变并发语义：(a) 会给使用方应用引入全局 Reactor hook 与额外 ThreadLocal 开销，(b) 是功能回退，(c) 需要改 Spring AI 的工具调用链。本轮已做的最小处置是**如实记录限制**：在 `cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md` 的 Design Notes / 设计笔记 中写明该限制与规避方式（需要流式 + `@Tool` 捕获时用同步 `.call()`，或按 conversation id 显式调用 client），避免用户误以为流式下自动捕获可用。
  **复审触发条件**：出现下列任一情况即重新评估——(1) 有用户报告流式下工具观察缺失或串号；(2) 项目决定引入 Reactor 自动上下文传播；(3) Spring AI 版本升级改变了 `StreamAdvisorChain` 的订阅时机（若 `nextStream` 改为在调用线程内完成订阅与执行，本问题自然消失）。
  **第 189 轮后补记（2026-10-03，第 190 轮 Java SDK 轮）——本条记录的后果清单不完整，漏掉了第三条。**
  上述两条后果讲的是 `@Tool` **自动捕获**（`CortexToolAspect`）与上下文泄漏，但 `CortexMemoryTools`
  的两个**读**工具受影响的方式不同：它们不跳过，而是**静默回落到别的项目**。
  `resolveProjectPath()`（`CortexMemoryTools.java:197-205`）在 `CortexSessionContext` 取不到值时
  直接返回构造时传入的 `defaultProjectPath`，该值来自 `cortex.mem.project-path`
  （`CortexMemAutoConfiguration.java:121-122`，未配置则为**空串**），**全程无日志**。
  两种结局都不自我暴露：配置了 `project-path` 时，Agent 拿到的是**另一个项目**的记忆并当成
  当前对话的历史；未配置时，工具发出空项目，而 `retrieveExperiences` **不校验 project**
  （只 `requireNonBlank(request.task())`），后端于是返回 `200` 加空列表，工具报告
  「No relevant past experiences found」——与该项目确实没有历史**无法区分**。
  活体实测（2026-10-03，后端 37777）：`POST /api/memory/experiences` 传 `project: ""` 返回
  `200 []`，同一请求传真实项目路径返回 5 条经验。`buildICLPrompt` 同理。
  受影响的方法：`searchMemories`、`getMemoryContext`（仅这两个调用 `resolveProjectPath()`；
  `updateMemory` / `deleteMemory` 按 id 操作，不涉及项目）。
  双语 README 的 P1-1 段落已由「两个后果」改为「三个后果」并补入上述实测。

- **Evidence**（对真实库 `claude_mem_dev` 实测，非源码推断）：以含 3568 行
  `embedding_1024` 数据的真实项目执行 `hybridSearch` 形状的查询，768 维与 1536 维
  查询向量均抛出 `DataException: different vector dimensions 768 and 1024` /
  `1536 and 1024`；1024 维正常返回。
- **实际影响有限且可见**：随附的 `BAAI/bge-m3` 为 1024 维，是唯一开箱可用的配置，
  真实项目中 768/1536 列均为空（实测样本 22559 行中两列皆 0，唯一的非 1024 记录是
  `/tmp/test4` 这条三列同时有值的合成测试夹具）。异常被 `SearchService:74` 捕获后退化为
  全文检索，API 响应如实返回 `strategy: "tsvector"` 与 `fellBack: true`，并记录一条
  WARN。**不是静默失败**，故定为 P2 而非 P1。
- **未修的原因**：正确修复需为 `hybridSearch` 补 768/1536 变体，或在 `SearchService`
  按维度分流；且需先决定同一项目内混合维度数据（既有 1024 又有 768 记录）如何处理。
  这属于 Backend 轮次的设计决策，本轮代码方向为 Java SDK，按轮换纪律不在本轮动手。
- **Status**: ⏸ 已记录不修（2026-10-02，第 173 轮 Java SDK 轮发现）。文档方向已在同轮
  修正 `docs/ARCHITECTURE.md` / `docs/ARCHITECTURE-zh-CN.md` 的 ADR 4：原「Decision 4:
  Multi-Dimension Embeddings」读起来像三种维度端到端可用，现已明确限定为**仅写入侧**，
  并写明退化行为与可观测信号。

- **Reproduction**（2026-10-03，活体 37777，嵌入密钥失效的状态下）：

  | 端点 | HTTP | 响应体 |
  |------|------|--------|
  | `GET /api/test/llm` | 200 | `{"status":"success", ...}` |
  | `GET /api/test/embedding` | **500** | `{"status":"error","message":"Embedding failed: 401 - ...Token is invalid."}` |
  | `GET /api/test/all` | **200** | 内含**同一个** `embedding.status = "error"` |

  同一故障，一边 500 一边 200，实测复现。
- **Status**: ⏸ **记录不修** —— 让 `/all` 传播子状态码属**对外契约变更**
  （监控与脚本会看到不同状态码），且需同步修改只声明 200 的 Swagger 注解，
  按既定纪律留待项目决策。**文档层已先行更正**：`docs/API.md` 与
  `docs/API-zh-CN.md` 的 Test All 章节现明写「该端点恒返回 200」、给出两种真实
  响应示例（健康 / 嵌入故障各一），并直接告诉巡检脚本应读嵌套 `status` 而非状态码。
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-2.md`](../archive/2026-10-04_backend-review-provenance-2.md)（第 238 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
- **Reproduction**（2026-10-03，活体 37777）:

  | 探针 | 做法 | 结果 |
  |------|------|------|
  | 静默丢弃 | 先直插一行 `status='processing'`、三元组与随后请求一致，再 POST 一次带**全新** `tool_response` 的 tool-use | HTTP **200** `{"status":"accepted"}`，但库里**只有那行预置数据**，新结果**未落库** |
  | 原子性 | 8 个线程并发 POST **完全相同**的 tool-use | 8 个 200，库里**落了 8 行**；`created_at_epoch` 全部落在 **1 ms** 窗口内，且都在最后一行写入后 **58 ms** 才转 `failed`——即 8 次去重检查都在任何一次落库转 `failed` 之前跑完了 |
  | 命名绕行 | 同 session 先 `Read` 后 `read`，input 相同 | 两行都在，input 哈希相同（`45ff9481fce2…`） |

  关于「窗口」要如实补一句：**是否丢弃取决于时序**。上面第一行之所以稳定复现，
  是因为预置行卡在 `processing`；而在本机（嵌入密钥失效、处理毫秒级失败）
  真正背靠背连发两次时，前一条往往已转 `failed`、去重条件不再命中，
  两次**都会**留下。生产环境嵌入/LLM 正常时处理耗时以秒计，窗口远宽于此——
  但这个「取决于时序」本身正是缺陷的一部分：**同一段代码的行为不可预测**。
- **规模**: 排除本次探针后 `mem_pending_messages` 共 **10,682** 行、
  distinct `tool_input_hash` 仅 **7,305**。其中 **2,682 行（25.1%）** 的
  `tool_input` 是空对象 `{}`，**共享同一个哈希**——对这些客户端而言去重键
  实际退化成 `(session, tool_name)`。最大的一组是单个 session 内 `exec`
  工具的 **208 行、全部 `failed`**。全表状态分布：`processed` 7,295 /
  `failed` 3,282 / `skipped` 105。
- **Status**: ⏸ **记录不修** —— 三条修法都改对外行为：
  (a) 把 `tool_response` 纳入哈希 → 幂等重发不再被吸收，削弱崩溃恢复保护；
  (b) 规范化 `tool_name` → 存量数据里 `readFile`/`write_file` 这类异名需迁移；
  (c) 补 `uk_session_tool_input` 唯一约束 → **会直接打断「失败后重试」这条
  合法路径**：应用层检查刻意忽略 `failed` 行，而唯一索引不忽略，于是重试会撞
  约束异常返回 500。要同时做对，需要重新设计「什么算同一次调用」，
  属设计决策，按既定纪律留待项目决策。**文档层已先行更正**：
  `cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md` 现明写
  捕获路径的这一静默丢弃形态。**SDK 与后端代码一字未改。**
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-2.md`](../archive/2026-10-04_backend-review-provenance-2.md)（第 238 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
- **规模**: 排除本次探针后 `mem_pending_messages` 共 **10,682** 行、
  distinct `tool_input_hash` 仅 **7,305**。其中 **2,682 行（25.1%）** 的
  `tool_input` 是空对象 `{}`，**共享同一个哈希**——对这些客户端而言去重键
  实际退化成 `(session, tool_name)`。最大的一组是单个 session 内 `exec`
  工具的 **208 行、全部 `failed`**。全表状态分布：`processed` 7,295 /
  `failed` 3,282 / `skipped` 105。
- **Status**: ⏸ **记录不修** —— 三条修法都改对外行为：
  (a) 把 `tool_response` 纳入哈希 → 幂等重发不再被吸收，削弱崩溃恢复保护；
  (b) 规范化 `tool_name` → 存量数据里 `readFile`/`write_file` 这类异名需迁移；
  (c) 补 `uk_session_tool_input` 唯一约束 → **会直接打断「失败后重试」这条
  合法路径**：应用层检查刻意忽略 `failed` 行，而唯一索引不忽略，于是重试会撞
  约束异常返回 500。要同时做对，需要重新设计「什么算同一次调用」，
  属设计决策，按既定纪律留待项目决策。**文档层已先行更正**：
  `cortex-mem-spring-integration/README.md` 与 `README-zh-CN.md` 现明写
  捕获路径的这一静默丢弃形态。**SDK 与后端代码一字未改。**
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-2.md`](../archive/2026-10-04_backend-review-provenance-2.md)（第 238 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
- **Reproduction**（2026-10-04）:

  | 事实 | 证据 |
  |------|------|
  | 默认绑回环 | `application.yml:3` 源码 |
  | 该默认值确实生效 | 活体进程 `lsof` 显示 `TCP 127.0.0.1:37777 (LISTEN)` |
  | 对外确实不可达 | 同机 `curl http://10.166.1.125:37777/api/health` → **HTTP=000**（连接失败） |
  | 两个 Dockerfile 都没设 `SERVER_ADDRESS` | 逐文件读，两者只有 `EXPOSE 37777` |
  | compose 显式绕开了 | `docker-compose.yml` 有 `SERVER_ADDRESS: 0.0.0.0` |
  | 两个 healthcheck 不一致 | 根：`http://localhost:37777/…` 且无 `ENV SERVER_PORT`；backend：`http://localhost:${SERVER_PORT}/…` 且有 `ENV SERVER_PORT=37777` |

  **未验证的部分（如实标注）**：本机**没有 Docker**（`which docker` 无输出），
  因此上述容器内行为是**源码与配置层面的推断 + 宿主机 bind 行为的实测**，
  **没有真的构建镜像跑一遍**。修法很直接且与 `backend/Dockerfile` 已有的正确写法一致：
  两个 Dockerfile 都加 `ENV SERVER_ADDRESS=0.0.0.0`；根 `Dockerfile` 再把 healthcheck
  改成 `${SERVER_PORT}` 并补 `ENV SERVER_PORT=37777`。
- **Status**: ⏸ **记录不修** —— 改 Dockerfile 属**部署产物变更**，且本机无 Docker
  **无法验证修复效果**；按既定纪律，不把未验证的改动当作已完成的修复提交。
  修法已在上文写明，留待有 Docker 环境的轮次或项目方实施。
  **文档层无需改动**：部署指南的 compose 片段经**逐键逐值对拍**与真实
  `docker-compose.yml` **完全一致**（差异只有为可读性新增的注释与键序分组，
  无任何键、值或默认值不同），**没有发现错误陈述**。
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
- **Reproduction**（2026-10-04，活体 37777，`?lines=3`）:

  ```json
  {"exists": true, "files": ["claude-mem-2026-10-04.log"],
   "logs": "[2026-10-04 01:53:16.718] [INFO ] [SERVIC] …",
   "path": "/Users/yangjiefeng/.claude-mem/logs",
   "returnedLines": 3, "totalLines": 302}
  ```

  6 个键，其中 **`files` 在注解示例里没有**。
- **对比**：`docs/API.md:2055-2065` 与 `docs/API-zh-CN.md:2039-2047` 的示例
  **六个键齐全**、用的是绝对路径，中文版前文还解释了 `files` 数组的语义
  （今天优先、不足才回落昨天）。**两版人工文档都正确，无需改动。**
- **Status**: ⏸ **记录不修** —— 改 `@ApiResponse` 的示例即改**对外 OpenAPI 契约**
  （沿用 P2-11 / P2-22 / P2-25 的同一判断）。**文档层无需更正**：
  人工撰写的两版 API 文档本来就是对的。
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
