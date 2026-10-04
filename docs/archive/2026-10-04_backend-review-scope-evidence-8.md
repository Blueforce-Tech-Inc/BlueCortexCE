# CortexCE backend-review-findings 范围与实测记录归档 — 第 8 批

> **归档日期**: 2026-10-04（第 254 轮）
> **用户已决策**（本轮问卷）：**允许把 ⏸ 条目的 Scope 与 Evidence 一并迁入归档，
>   仅保留 Problem 与 Status 的决策推理**。这是 ⏸ 规则的一次经用户批准的放宽，
>   与第 241/244/247/253/254 轮「只迁 Evidence」的先例不同——**Scope 也含大量
>   文件行号与实测细节，属可复现的证据而非决策推理**。
> **保留在工作文件中的**: 每条的 `- **Problem**`（问题是什么）与 `- **Status**`
>   （为什么这么定、为什么不修）——**决策推理一行未动**。
> **迁出内容**: 全部 **52 段** `- **Scope**` 与 `- **Evidence**`，逐字迁出，
>   源文件各留一行指针。
> **逐字校验**: 每段以 `- **Scope**` / `- **Evidence` 起始、以下一条 `- **` 之前的
>   全部行为止，不截断中间内容；迁出后回读逐段断言原文仍在。
> **本文件创建后不得修改**。

---

<!-- P2-24 -->
- **Scope**: `entity/ObservationFeedbackEntity.java`（已修）、
  `repository/ObservationFeedbackRepository.java`（已修）、
  以及 V17 迁移所声明的三项能力（**均未实现**）。

<!-- P1-1 -->
- **Scope**: `cortex-mem-spring-integration/cortex-mem-spring-ai/.../advisor/CortexSessionContextBridgeAdvisor.java:79-99`（`adviseStream`）配合 `context/CortexSessionContext.java:14` 的 `ThreadLocal<SessionInfo>`；消费方为 `aspect/CortexToolAspect.java:43`（仅判断 `isActive()`）。

<!-- P2-8 -->
- **Scope**: 写入侧 `backend/.../service/AgentService.java:533-535`（`switch (vector.length)`，
  `case 768/1024/1536` 分别调用 `setEmbedding768/1024/1536`）；读取侧
  `backend/.../repository/ObservationRepository.java:268` `hybridSearch`（SQL 硬编码
  `embedding_1024`）与 `backend/.../service/SearchService.java:57-59`。

<!-- P2-10 -->
- **Scope**: `backend/.../controller/IngestionController.java` — `handleObservation`
  （第 319 行 `if (projectPath == null || projectPath.isBlank())` → 400）与
  `handleToolUse`（第 119 行）、`handleUserPrompt`（第 229 行）、`handleSessionEnd`
  三个端点。后三者从 `body.cwd()` 取值后**不做任何校验**。

<!-- P2-13 -->
- **Scope**: `cortex-mem-spring-integration/cortex-mem-spring-ai/.../context/CortexSessionContext.java`
  （`SessionInfo` 仅 `sessionId` / `projectPath` / `promptCounter`，两个 `begin()` 重载都不接受 userId）、
  `.../advisor/CortexMemoryAdvisor.java:119-123`（`buildICLPrompt` 只设 `.project(...)`，
  全文 `userId` 出现 **0** 次）、`.../tools/CortexMemoryTools.java:70-75, 108-112`。

<!-- P2-14 -->
- **Scope**: `ObservationRepository.java:619-634`（`findNewObservations(project, sources,
  sinceEpoch, limit)`，javadoc 写「Find new observations since a given epoch for
  **incremental extraction**」）；调用方为 `StructuredExtractionService.java:211`，
  它用的是 `findBySourceIn(projectPath, sources, initialRunMaxCandidates)`；
  `V16__composite_source_index.sql:11` 把 `findNewObservations` 列为新建复合索引服务的查询之一。

<!-- P2-15 -->
- **Scope**: `mcp/ClaudeMemMcpTools.java:249-258`（`findByContentSessionId("manual-memories")
  .orElseGet(() -> sessionRepository.save(...))`），整段被外层 `catch (Exception)`（:290）覆盖。

<!-- P2-16 -->
- **Scope**: `cortex-mem-spring-integration/cortex-mem-client/.../CortexMemClientImpl.java`
  （`executeWithRetry` / `executeWithRetryReturn` 均以
  `throw new RuntimeException(operation + " failed: " + describe(e), e)` 收尾；
  `describe()` 只回传后端 `error` 字段文本，**不含状态码**）；
  整个 `cortex-mem-client` 模块 **18 个类中没有任何错误类型**。

<!-- P2-17 -->
- **Scope**: `ExtractionConfig.java:29`（`maxBatchesPerTemplate = 10`，绑定
  `EXTRACTION_MAX_BATCHES`）与 `StructuredExtractionService.java:234-235` 的批处理循环。

<!-- P2-18 -->
- **Scope**: `StructuredExtractionService.java:135-160`（`doReExtractForSession`），
  入口为 `SessionController.java:327`（`PATCH /api/session/{id}/user`）。

<!-- P2-19 -->
- **Scope**: `CortexMemClientImpl.java:231`（`triggerRefinement`）与 `:432`
  （`triggerExtraction`），二者都走 `executeWithRetrySilent`（`:775-799`）。

<!-- P2-20 -->
- **Scope**: 全部声明为 `int`/`long`/`Integer`/`Long` 的 `@RequestParam`，共 **22** 个、
  分布在 **11** 个端点（`ViewerController` 13 处、`ContextController` 7 处、
  `LogsController` 1 处、`ExtractionController` 1 处）。完整清单见
  `docs/API.md` 的「Query Parameter Conventions」一节。

<!-- P2-21 -->
- **Scope**: `CortexMemHealthIndicator.health()` 的 `catch` 分支与 `withException(e)` 详情，
  配合 `CortexMemClientImpl.java:339-360` 的 `healthCheck()`。

<!-- P2-22 -->
- **Scope**: `backend/.../controller/CursorController.java:143`（`@ApiResponse` 的
  `@Schema(example = ...)`）。

<!-- P2-23 -->
- **Scope**: `backend/.../controller/StreamController.java:60-73` 配合
  `backend/.../service/SSEBroadcaster.java:26-34`（`add()` 抛
  `IllegalStateException`）与 `Constants.MAX_SSE_CONNECTIONS = 100`。

<!-- P2-25 -->
- **Scope**: `ApiRequests.ICLPromptRequest.maxChars`（`ApiRequests.java:147`）的
  `@Schema(description = "Max prompt length (0 = backend default ~4000)")`，
  该描述原样出现在 `/v3/api-docs` 与 Swagger UI 中。

<!-- P2-26 -->
- **Scope**: `go-sdk/cortex-mem-go/dto/observation.go` 的 `ObservationUpdate` 三个字段：
  `Facts []string`、`Concepts []string` 均带 `,omitempty`，`ExtractedData map[string]any` 同。
  客户端路径 `client_methods.go:211 UpdateObservation`。

<!-- P2-27 -->
- **Scope**: `dto.py` 的 `ObservationUpdate.is_empty()` 与 `to_wire()`，两处都有一句
  `if attr == "extracted_data" and isinstance(val, dict) and not val: continue`；
  另含类 docstring 中一句**对 Go 的事实性错误描述**（已修，见下）。

<!-- P2-28 -->
- **Scope**: `TestController.testAll()`（`TestController.java:118-123`）。
  `testLlm()` 与 `testEmbedding()` 各自会返回 `ResponseEntity.status(500)`
  （第 67、106 行），但 `testAll` 只取 `.getBody()` 塞进 Map，
  **状态码被丢弃**，自身恒返 `200 OK`。

<!-- P2-29 -->
- **Scope**: `AgentService.calculateToolInputHash()`（`AgentService.java:466-482`）、
  `AgentService.handleToolUse()` 去重分支（第 147-158 行）、
  `PendingMessageRepository.existsBySessionAndTool()`（第 38-45 行）、
  `PendingMessageEntity` 的 `@UniqueConstraint`（第 8-11 行）。

<!-- P2-31 -->
- **Scope**: `go-sdk/cortex-mem-go/dto/experience.go:38` 与 `:46` 的
  `MaxChars int \`json:"maxChars,omitempty"\``。

<!-- P2-32 -->
- **Scope**: 根 `Dockerfile`（`HEALTHCHECK` 行与文件头注释里的 `docker run` 示例）、
  `backend/Dockerfile`（`HEALTHCHECK` 行）、`backend/src/main/resources/application.yml:3`。

<!-- P2-33 -->
- **Scope**: `go-sdk/cortex-mem-go/examples/http-server/main.go:470` 与 `:771`
  的路由注册。

<!-- P2-34 -->
- **Scope**: `LogsController.getLogs()` 的 `@ApiResponse` 示例
  （`LogsController.java:81-82`）。**人类撰写的文档是对的**，错的只有注解。

<!-- P2-35 -->
- **Scope**: `CortexToolAspect.interceptToolExecution()`
  （`CortexToolAspect.java:60` 的 `joinPoint.proceed()` **在 try 块之外**，
  try 只包住第 62-73 行的捕获调用）。

<!-- P2-35 -->
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-35）。

<!-- P2-36 -->
- **Scope**: `eino/retriever.go:37`（`WithRetrieverCount`）、
  `genkit/retriever.go:54`（`WithRetrieverCount`）、
  `langchaingo/memory.go:32`（`WithMemoryMaxChars`）、`dto/experience.go:12,38`。

<!-- P2-36 -->
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-36）。

<!-- P2-37 -->
- **Scope**: `examples/cortex-mem-demo/.../ChatController.java:61`（`@GetMapping("/chat")`）、
  `go-sdk/cortex-mem-go/examples/http-server/main.go:170`、
  `python-sdk/cortex-mem-python/examples/http-server/app.py:195`、
  `js-sdk/cortex-mem-js/examples/http-server/app.ts:122`。

<!-- P2-37 -->
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-37）。

<!-- P2-39 -->
- **Scope**: `ImportController.bulkImport()`（`@Transactional` + 逐行 `try/catch`）、
  `ImportService.importSession()`（同样 `@Transactional`，REQUIRED 并入外层）、
  `mem_sessions.content_session_id varchar(255)` 与 `status varchar(50)`。

<!-- P2-39 -->
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-39）。

<!-- P2-40 -->
- **Scope**: 后端 `ViewerController` 的 `GET /api/summaries` 与 `GET /api/prompts`；
  四家 SDK 的全部公开方法（Go `client.go`/`client_methods.go`、Python `client.py`、
  JS `client.ts`、Java `CortexMemClient`）。

<!-- P2-40 -->
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-40）。

<!-- P2-41 -->
- **Scope**: 后端 `ViewerController` 的观测列表响应与 `platformSource` 查询参数；
  四家 SDK 的 `Observation` 响应 DTO 与列表请求参数。

<!-- P2-41 -->
- **Evidence**: 已逐字迁入 [`2026-10-04_backend-review-evidence-5.md`](../archive/2026-10-04_backend-review-evidence-5.md)（第 253 轮）。

<!-- P2-42 -->
- **Scope**: `js-sdk/cortex-mem-js/tsconfig.json` 的 `exclude`（含 `"**/*.test.ts"`），
  以及 `package.json` 里 `lint` 脚本就是 `tsc --noEmit`。

<!-- P2-43 -->
- **Scope**: `python-sdk/cortex-mem-python/cortex_mem/client.py` 的
  `update_observation()` kwargs 分支，与同文件 `dto.py` 的 `ObservationUpdate.to_wire()`。

<!-- P2-44 -->
- **Scope**: `cortex-mem-spring-integration/cortex-mem-spring-ai` 的
  `CortexSessionContextBridgeAdvisor.adviseCall/adviseStream` 与
  `CortexSessionContext.begin/end`。**与 P1-1 无关**：P1-1 是流式下 ThreadLocal 跨线程丢失，
  本条是**单线程内的嵌套**，两条路径独立。

<!-- P2-45 -->
- **Scope**: 后端 `SessionController.startSession` 与 `ApiRequests.SessionStartRequest`；
  四家 SDK 的会话启动请求模型。

<!-- P2-46 -->
- **Scope**: `scripts/phase3-acceptance-test.sh`（第 45–50 行 `cleanup()`、第 206–222 行 Test 6）。

<!-- P2-46 -->
- **Evidence**: 已逐字迁入 [`2026-10-04_backend-review-evidence-5.md`](../archive/2026-10-04_backend-review-evidence-5.md)（第 253 轮）。

<!-- P2-47 -->
- **Scope**: `docs/API.md:204-205, 218-219`、`docs/API-zh-CN.md:198-199, 212-213`；
  后端 `SessionController.startSession` 与 `service/WorktreeDetector`。

<!-- P2-49 -->
- **Scope**: `scripts/start.sh:55`、`start-all.sh:99`、`phase3-acceptance-test.sh:671`、
  `thin-proxy-test.sh:31`（均为注释或提示文案）；文档侧 `DEVELOPMENT.md` 已于第 253 轮修正。

<!-- P2-50 -->
- **Scope**: `CursorService.readRegistryUnlocked` 与调用方 `registerProject` /
  `unregisterProject`；`CursorController` 的 `/api/cursor/register` 两处映射。

<!-- P2-50 -->
- **Evidence**: 已逐字迁入 [`2026-10-04_backend-review-evidence-5.md`](../archive/2026-10-04_backend-review-evidence-5.md)（第 253 轮）。

<!-- P2-51 -->
- **Scope**: `SessionController.startSession` 的多项目分支判定、`parseProjectsParam`；
  `API.md` / `API-zh-CN.md` 的 `projects` 字段说明、`ApiRequests` 的 `@Schema`。

<!-- P2-51 -->
- **Evidence**: 已逐字迁入 [`2026-10-04_backend-review-evidence-6.md`](../archive/2026-10-04_backend-review-evidence-6.md)（第 254 轮）。

<!-- P2-52 -->
- **Scope**: `cortex-mem-spring-integration/*/target/test-classes/`；受影响的是
  `cortex-mem-client`（4 个）、`cortex-mem-spring-ai`（2 个）、`cortex-mem-starter`（3 个）等。

<!-- P2-52 -->
- **Evidence**: 已逐字迁入 [`2026-10-04_backend-review-evidence-7.md`](../archive/2026-10-04_backend-review-evidence-7.md)（第 254 轮）。

<!-- P2-48 -->
- **Scope**: `CLAUDE.md`（本地文件，**未纳入版本控制**）。`AGENTS.md` 已跟踪，**无幻影**（表内 0 条端点）。

<!-- P2-48 -->
- **Evidence**: 已逐字迁入 [`2026-10-04_backend-review-evidence-5.md`](../archive/2026-10-04_backend-review-evidence-5.md)（第 253 轮）。
