> **来源**: `docs/drafts/backend-review-findings.md` 中 **P2-24 / P2-23 / P2-48** 三条的
>   **Problem 段中的实测证据正文**，逐字迁出，源文件各留一行指针。
> **依据**: 承第 241、244、247、253、254 轮确立的规则——**实测记录是可复现的证据**
>   （每段都写明日期、端点与技术手段），⏸ 规则保护的是**决策推理**。三条的
>   `- **Status**`（为什么这么定）与压缩后的 `- **Problem**`（问题是什么）**一行未动**。
> **本文件创建后不得修改**。

---

<!-- P2-24 -->
- **Problem**：本轮从 P1-3 顺藤摸下来，发现 **V17 从未被记录为 finding**。三条实证：
  ①`ObservationFeedbackEntity` 有一个 `@Column(name = "created_at")` 的 `OffsetDateTime` 字段，
  而 `V17__observation_feedback.sql` **从未创建该列**——活体 `information_schema` 确认
  `observation_feedback` 只有 `id / observation_id / signal_type / session_db_id /
  created_at_epoch / metadata` 六列。Hibernate 对 `SELECT f FROM ObservationFeedbackEntity f`
  这类不指定列的 JPQL 会**逐个 SELECT 全部映射列**，因此**任何**触及该实体的查询都会报
  `column "created_at" does not exist`。**已修**：删除该字段与其 getter/setter，
  并在 `createdAtEpoch` 上写明不要在无迁移的情况下加回。
  ②`findByObservationIdOrderByCreatedAtDesc` 的 `@Query` 实际按 `createdAtEpoch` 排序，
  **方法名描述的列根本不存在**，且零调用方。**已修**：改名为
  `findByObservationIdOrderByCreatedAtEpochDesc`，与 P1-3 是同一类「名字与实际不符」的陷阱。
  ③**V17 声明的三项能力全部没有写入方**：活体库 `observation_feedback` **0 行**、
  `generated_by_model` 非空 **0 行**、`relevance_count <> 0` **0 行**；
  `grep setRelevanceCount` 在 `main` 源码中**零命中**。
  即「Thompson Sampling 优化的基础」目前是**纯脚手架**。
- **Severity 说明**：①是**潜伏缺陷**而非启动即崩——注入回错误映射后后端**仍能正常启动**，
  因为该表 0 行、repository 零调用方，Spring Data 不会预校验 JPQL 引用的列。
  它会在**第一次真正使用该实体时**炸掉。③是**未实现特性**而非错误行为。

<!-- P2-23 -->
- **Problem 1 —— 状态码语义错误**：第 101 个客户端被拒时，后端**没有任何
  `@ControllerAdvice` / `@ExceptionHandler`**（全仓唯一命中的是
  `config/AsyncConfig.java` 的 `AsyncUncaughtExceptionHandler`，与 MVC 异常无关），
  `IllegalStateException` 直穿到容器默认处理。活体实测（105 条并发裸 socket）：
  **恰好 100 条 `200`，第 101–105 条 `500`**。容量耗尽是「服务暂时不可用」，
  返回 500 会让任何按 5xx 告警的监控在**每次触顶时都误报为服务故障**；应为 503
  （或 429）。且 `stream()` 的 `@ApiResponse` **只声明了 200**，该分支在契约中不存在。
  活体响应 `Content-Length: 0`，**不泄漏内部信息**——问题纯粹在状态码。
- **Problem 2 —— 没有心跳，死连接要等下一次事件才被回收**：清理只发生在
  `SseEmitter` 的 `onCompletion` / `onError` / `onTimeout` 回调，以及
  `broadcast()` 捕获 `IOException` / `IllegalStateException` 时。全仓**没有任何周期性
  心跳广播**——四处 `broadcast()` 调用全部是事件驱动的
  （`IngestionController:272,365`、`SummaryGenerationService:153`、`AgentService:288`），
  `SSEBroadcaster` 自身也没有 `@Scheduled` 清理。因此**服务端毫无活动时，废弃连接会一直
  留在名单里**，直到 `claudemem.sse.timeout-ms`（默认 1800000ms = **30 分钟**）触发
  `onTimeout`。**100 个「连上就断」的客户端即可让所有新 SSE 客户端在最长 30 分钟内
  持续拿到 500**，而此时后端可能一条事件都没产生过。这正是代理与浏览器普遍掐断空闲
  SSE 连接的场景。

<!-- P2-48 -->
- **Problem**: 把 `CLAUDE.md` 里形如 `| METHOD | \`/path\` |` 的表格行逐条对拍活体 `/v3/api-docs`：
  **25 条中匹配 16 条，幻影 9 条**，9 条**逐条实测为 404**：
  `POST /api/ingest/session-start`、`POST /api/memory/save`、`POST /api/context/observations`、
  `GET /api/memory/quality-stats`（真实为 `quality-distribution`）、
  `GET|POST /api/modes/active`、`GET /api/sessions`、`GET /api/sessions/{id}`、
  `POST /api/sessions/import`（真实为 `/api/import/sessions`）；
  `/api/ingest` 下实际只有 `observation` / `session-end` / `tool-use` / `user-prompt` 四条。
  其中 `GET /api/sessions` 与幻影 MCP 工具 `__IMPORTANT`、`V17` 标为「✅ Complete」
  早已被观察到，并入既有的 `AGENTS.md` / `CLAUDE.md` 待决项；**其余 7 条是本轮首次精确计量**。
  同区域的另一处漂移：`CLAUDE.md` 的项目结构写「controller/ # 17 controllers」，
  **实测 13 个**（service 写「28+」，实测 29，属「+」的合法范围，不计）。
