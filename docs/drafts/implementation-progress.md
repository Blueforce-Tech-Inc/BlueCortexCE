# Go SDK & Java SDK 实施进度

## Phase A: Java SDK 补充 ✅ 完成

### A1: P0 功能 ✅
- SearchRequest.java + search() — GET /api/search
- ObservationsRequest.java + listObservations() — GET /api/observations
- getObservationsByIds() — POST /api/observations/batch

### A2: P1 功能 ✅
- getVersion() — GET /api/version
- getProjects() — GET /api/projects
- getStats() — GET /api/stats
- getModes() — GET /api/modes
- getSettings() — GET /api/settings

### A3: Demo 更新 ✅
- SearchController: /demo/search
- ObservationsController: /demo/observations
- ManagementController: /demo/manage

**Java SDK 现有 20 个 API 方法**（从 15 增加到 20）

## Phase B: Go SDK 实施 ✅ 完成

### B1: 核心包 ✅
- client.go — 25 个 API 方法接口
- client_impl.go — HTTP 客户端 + Option 模式
- client_methods.go — 全部方法实现
- error.go — APIError 类型 + 错误判断函数
- dto/ — 8 个 DTO 文件
- README.md — 完整的 SDK 文档

### B2: 集成层 ✅
- eino/README.md — Eino Retriever 集成
- langchaingo/README.md — LangChainGo Memory 集成
- genkit/README.md — Genkit Retriever 集成

### B3: Demo 项目 ✅
- basic/main.go — 纯 SDK 使用 Demo
- eino/main.go — Eino Retriever Demo
- langchaingo/main.go — LangChainGo Memory Demo
- genkit/main.go — Genkit Retriever Demo
- http-server/main.go — HTTP 服务示例

## Phase C: E2E 验收测试 ✅ 完成

### C1: Java SDK Demo E2E 测试 ✅
- 脚本：`scripts/java-sdk-e2e-test.sh`
- 14 个严格测试：原有 + P0 + P1 API + 链路验证

### C2: Go SDK Demo E2E 测试 ✅
- 脚本：`scripts/go-sdk-e2e-test.sh`
- 13 个严格测试：Demo 端点 + Backend 直接访问

### C3: Go SDK 单元测试 ✅
- 18 个单元测试全部通过
- Wire format 测试覆盖
- Error handling 测试

## Phase D: 代码审查与改进 🔄 进行中

### 已修复问题
- [x] Java SearchController: `type` 参数名改为 `observationType`（Java 保留字问题）
- [x] Go SDK README.md 缺失 → 已创建
- [x] 集成层 README.md 缺失 → 已创建
- [x] Go SDK 18 个单元测试全部通过
- [x] Go SDK Demo 编译错误修复（5 个 demo 全部修复）：
  - eino demo: NewRetriever() 参数位置错误（project 应为位置参数，非 option）
  - eino demo: ObservationRequest 字段名错误（Type/Content → ToolName/ToolInput/ToolResponse）
  - eino demo: Experience 显示字段错误（Type/Content → Task/Outcome）
  - langchaingo demo: NewMemory() 参数位置错误 + 使用了不存在的 WithMemorySessionID
  - genkit demo: ObservationRequest 字段名错误 + 错误被 `_=` 忽略
  - basic/eino/genkit demo: StartSession 失败后 nil pointer dereference

### 待审查项
- [x] Go SDK Logger 接口完整性（已确认：nopLogger + Option 模式完善）
- [x] Java SDK SearchRequest DTO 字段验证（已确认：Builder 模式 + 默认值正确）
- [x] Go SDK Demo 代码质量（已修复上述编译错误，已验证 go vet + unit tests）
- [x] Go SDK `APIError.Body` 字段声明但未使用（Message 已包含 response body）
- [x] Go SDK `doFireAndForget` 对所有管理操作统一行为（与 Java SDK 一致，设计意图）
- [x] Go http-server 输入验证一致性（/search + /observations 添加 project 必填校验）
- [x] Java Demo 控制器异常处理（SearchController/ObservationsController/ManagementController 添加 try-catch）
- [x] Go E2E 测试结构完整性（summary 报告移至所有测试之后）
- [x] Java SDK healthCheck() 端点一致性（/actuator/health → /api/health，与 Go SDK 和后端一致）
- [x] Go SDK 测试覆盖完整性（新增 11 个测试覆盖 P1 API + fire-and-forget 重试 + error helpers）
- [x] Go http-server demo `/chat` 端点添加 project/message 必填校验（与 /search, /observations, /experiences 一致）
- [x] Go SDK dto 包 wire format 文档一致性（search.go, observations.go, observation.go 添加 wire format 注释）
- [x] Java SDK 单元测试扩展（15 个新测试覆盖 P0/P1 API + Extraction + Observation Management）
- [x] Go http-server `/quality` 端点添加 project 必填校验（与 /search, /observations, /experiences, /iclprompt 一致）
- [x] Go 集成层（eino/genkit）添加 WithRetrieverUserID 选项，支持用户级记忆（与 langchaingo Memory 一致）
- [x] Go genkit RetrieverInput 添加 UserID 字段，支持按调用覆盖用户 ID
- [x] Java MemoryController getMemoryHealth() 逻辑 bug 修复（size >= 0 永远为 true → 改为 size > 0）
- [x] Java MemoryController extraction 端点添加 try-catch 异常处理（与其他控制器一致）
- [x] Go http-server /extraction/latest 和 /extraction/history 移除硬编码默认 project，改为必填校验（与所有其他端点一致）
- [x] Go E2E 测试 extraction 端点添加 project 参数（匹配新的必填校验）
- [x] Go http-server /feedback 拆分复合校验为独立检查（与 /iclprompt 一致）
- [x] Go http-server /session/user 拆分复合校验为独立检查
- [x] Go http-server /ingest/prompt 新增 project/session_id/prompt 必填校验
- [x] Go http-server /ingest/session-end 新增 project/session_id 必填校验
- [x] Go E2E 测试 tests 28-29 硬编码 project 替换为 $PROJECT 变量
- [x] Go E2E 测试 test 34 修复 DELETE 204 No Content 响应处理

- [x] Go SDK ExperienceRequest.Project 和 ICLPromptRequest.Project 添加 omitempty（与 Java SDK 行为一致，空项目不发送到后端）
- [x] 新增 2 个单元测试验证空项目省略行为
- [x] Go SDK APIError.Unwrap() 行为验证（新增 11 个测试：Unwrap sentinel errors、errors.As 提取、未知状态码、空响应/malformed JSON 边界）
- [x] 确认 errors.Is() 通过 Unwrap() 链正确匹配 sentinel errors（ErrInternal 仅匹配 500，不交叉匹配 503）
- Go SDK 单元测试从 47 → 58 个，全部通过

### 第二十九轮（2026-03-25 09:01）
- [x] Java SDK ICLPromptRequest.Builder maxChars 默认值修复（round 12 遗留问题）
  - Builder 默认 maxChars=4000 导致 round 12 的实现修复无效（实现检查 null 但 Builder 永远不产生 null）
  - 移除 Builder 硬编码默认值，改为 null（让后端决定默认值）
  - 移除 2 个便利构造函数（强制 4000 的 2-arg 和 3-arg 版本）
  - 新增 2 个测试：defaultMaxChars_omitsFromRequest + explicitMaxChars_includedInRequest
- [x] Java SDK 单元测试 42 → 44 个，全部通过
- [x] Go SDK 58 测试通过、go vet 干净、5/5 examples 编译通过
- [x] 回归测试 46/46 PASS

### 第三十轮（2026-03-25 09:31）— Java Backend 代码审查
- [x] Java IngestionController.handleSessionEnd() 添加 session_id 必填校验（之前传递 null 到 completeSessionAsync 导致静默失败，与 handleUserPrompt 不一致）
- [x] Java IngestionController.handleToolUse() 移除冗余 null 检查（blank 校验后 contentSessionId 已保证非 null）
- [x] Java RateLimitService.isValidIpAddress() 支持 IPv6（之前仅验证 IPv4，X-Forwarded-For 中的 IPv6 客户端地址被静默拒绝，fallback 到 getRemoteAddr）
- [x] Java Demo BUILD SUCCESS、Go SDK 58 测试通过、go vet 干净、回归测试 46/46 PASS

### 测试覆盖策略
- **单元测试**：88 个 wire format + API + error + retry + context cancellation + backoff + omitempty + Unwrap/As + lifecycle + header + 集成层测试（全部通过）
- **E2E 测试**：Java 25 个 + Go 26 个（验证端到端链路）
- **教训**：新增测试必须严格匹配已有 wire format 定义

### 已验证项
- ✅ Go SDK 63 单元测试 PASS
- ✅ Go SDK examples: 5/5 编译通过
- ✅ Go vet 干净
- ✅ Java Demo 编译通过
- ✅ Java SDK BUILD SUCCESS（含 healthCheck 修复）
- ✅ 回归测试 46/46 PASS
- ✅ Backend 服务运行中

## 进度日志

- 2026-03-24 18:12: 开始 Phase A
- 2026-03-24 18:30: Phase A 完成 ✅
- 2026-03-24 18:31: Phase B 开始
- 2026-03-24 18:40: Phase B 完成 ✅
- 2026-03-24 18:42: Phase C 开始 — E2E 验收测试
- 2026-03-24 18:45: Phase C 完成 ✅
- 2026-03-24 18:57: Phase D 开始 — 代码审查与改进
- 2026-03-24 18:58: 修复 Java SearchController type 参数问题
- 2026-03-24 19:00: 创建 Go SDK 完整 README.md
- 2026-03-24 19:02: Go SDK 18 测试通过
- 2026-03-24 19:05: 创建集成层 README
- 2026-03-24 19:31: Phase D 代码审查第二轮 — 修复 Go SDK Demo 编译错误（5 个 demo）
  - 修复 eino: NewRetriever() 参数 + ObservationRequest 字段 + Experience 字段
  - 修复 langchaingo: NewMemory() 参数 + 移除不存在的 WithMemorySessionID
  - 修复 genkit: ObservationRequest 字段 + 错误处理
  - 修复 basic/eino/genkit: nil pointer dereference (log.Fatalf)
  - go vet 干净、18 单元测试通过、Java Demo 编译通过
- 2026-03-24 20:01: Phase D 代码审查第三轮 — SDK API 一致性修复
  - 修复 Go SDK: GetLatestExtraction/GetExtractionHistory 移除冗余 templateName query param
  - 修复 Java SDK: 同上（CortexMemClientImpl.java 同样存在此问题）
  - templateName 已在 URL 路径中传递 (/api/extraction/{templateName}/...)，query param 冗余
  - go vet 干净、18 单元测试通过、Java SDK BUILD SUCCESS、Demo 编译通过
- 2026-03-24 20:31: Phase D 代码审查第四轮 — APIError 清理 + Demo 竞态修复
  - Go SDK: 移除 APIError.Body 未使用字段（Message 已包含原始响应体）
  - Go SDK: 再次确认 GetLatestExtraction 冗余 templateName query param 已修复
  - Java SDK: 同步修复 getLatestExtraction 冗余 templateName query param
  - 4 个集成 Demo（basic/eino/genkit/langchaingo）添加 time.Sleep(500ms) 避免 fire-and-forget 竞态
  - go vet 干净、18 单元测试通过、5/5 Demo 编译通过、Java SDK BUILD SUCCESS
- 2026-03-24 21:01: Phase D 代码审查第五轮 — 输入验证 + 异常处理 + E2E测试修复
  - Go http-server: /search 和 /observations 端点添加 project 参数缺失验证（与 /experiences 一致）
  - Java Demo: SearchController、ObservationsController、ManagementController 添加 try-catch 异常处理，避免 backend 宕机时返回原始 500 栈追踪
  - Go E2E 测试: 修复 summary 报告在 supplementary tests (15-26) 之前打印的结构性 bug，统一最终报告
  - go vet 干净、18 单元测试通过、basic/http-server Demo 编译通过、Java Demo BUILD SUCCESS
- 2026-03-24 21:31: Phase D 代码审查第六轮 — TriggerRefinement 一致性 + 单元测试扩展
  - Go SDK: TriggerRefinement 改用 doRequestNoContent（与 SubmitFeedback/RecordObservation 一致，消除手动 status 检查冗余代码）
  - Go SDK: 新增 3 个单元测试 — UpdateObservation_WireFormat（验证 PATCH 方法 + camelCase + pointer omitempty）、DeleteObservation（验证 DELETE 方法 + 路径）、GetExtractionHistory_PathAndParams（验证 template/history 路径 + query params）
  - Go SDK 单元测试从 18 → 21 个，全部通过
  - go vet 干净、Java SDK BUILD SUCCESS
- 2026-03-24 22:01: Phase D 代码审查第七轮 — Java SDK healthCheck 修复 + Go SDK 测试扩展
  - Java SDK: healthCheck() 修复 — 从 /actuator/health 改为 /api/health（与 Go SDK 和后端一致）
  - Java SDK: CortexMemClient.java JavaDoc 修正 — 移除 "/actuator/health" 歧义描述
  - Go SDK: 新增 11 个单元测试 — UpdateSessionUserId、GetProjects、GetStats、GetStats_EmptyProject、GetModes、GetSettings、IsRateLimited、IsInternal、FireAndForget_RetryOnFailure、FireAndForget_ExhaustsRetries、HealthCheck_Success、HealthCheck_Unhealthy
  - Go SDK 单元测试从 21 → 32 个，全部通过
  - go vet 干净、Java SDK BUILD SUCCESS
- 2026-03-24 22:31: Phase D 代码审查第八轮 — Java SDK 测试补充 + Go Demo + DTO 文档
  - Go http-server demo: /chat 端点添加 project/message 必填校验（与其他端点一致）
  - Go SDK dto 包: search.go, observations.go 添加 wire format 注释；observation.go ObservationUpdate 添加 wire format 注释
  - Java SDK: 新增 15 个单元测试 — search、listObservations、getObservationsByIds、getVersion、getProjects、getStats(2)、getModes、getSettings、getLatestExtraction(2)、getExtractionHistory、updateObservation、deleteObservation、updateSessionUserId
  - Java SDK 单元测试从 14 → 29 个（总 39 含 DtoTest），全部通过
  - go vet 干净、32 Go 测试通过、basic/http-server Demo 编译通过、回归测试 46/46 PASS
- 2026-03-24 23:01: Phase D 代码审查第九轮 — http-server 验证 + 集成层 userID 支持
  - Go http-server: /quality 端点添加 project 必填校验（与 /search, /observations, /experiences, /iclprompt 一致）
  - Go eino: 新增 WithRetrieverUserID 选项，Retrieve 方法传递 userID 到 ExperienceRequest
  - Go genkit: 新增 WithRetrieverUserID 选项 + RetrieverInput.UserID 字段，Retrieve 方法支持按调用覆盖 userID
  - go vet 干净、32 Go 测试通过、http-server Demo 编译通过、Java SDK BUILD SUCCESS、回归测试 46/46 PASS
- 2026-03-24 23:31: Phase D 代码审查第十轮 — fire-and-forget 语义修复 + http-server 改进
  - Go SDK: 修复 doFireAndForget 上下文取消 bug — 之前在 backoff 期间 context 被取消时返回 ctx.Err()，违反 fire-and-forget 契约（绝不返回错误）。现在吞掉取消错误并返回 nil
  - Go SDK: 新增 TestFireAndForget_ContextCancellation 测试（33 个单元测试，全部通过）
  - Go http-server demo: http.Server 添加 ReadTimeout/WriteTimeout/IdleTimeout 超时配置
  - Go http-server demo: 添加 SIGINT/SIGTERM 优雅关闭
  - go vet 干净、33 Go 测试通过、5/5 examples 编译通过、Java SDK BUILD SUCCESS、回归测试 46/46 PASS
- 2026-03-25 00:01: Phase D 代码审查第十一轮 — http-server API 一致性改进
  - Go http-server demo: 添加 writeJSON/writeJSONError/checkMethod 辅助函数，统一 JSON 错误响应格式
  - 之前所有错误响应使用 text/plain (http.Error)，与 success 的 application/json 不一致，API 消费者无法可靠解析错误
  - 现在所有响应（包括错误）统一返回 JSON: {"error": "..."} 格式
  - /health 端点添加 method 检查（之前不检查，与其他端点不一致）
  - /iclprompt 验证风格统一：从 compound check (project=="" || task=="") 改为 separate checks（与其他端点一致）
  - go vet 干净、33 Go 测试通过、http-server 编译通过
- 2026-03-25 00:31: Phase D 代码审查第十二轮 — Java SDK 客户端默认值修复
  - Java SDK CortexMemClientImpl.retrieveExperiences(): 修复空 project 发送 "" 而非省略参数的 bug（与 Go SDK 和 search/listObservations 一致）
  - Java SDK CortexMemClientImpl.buildICLPrompt(): 移除硬编码 maxChars=4000 默认值，让后端决定默认值（避免 SDK 与后端默认值不同步）
  - 同步修复 project 为空时发送 "" 的问题（与 retrieveExperiences 一致）
  - SDK BUILD SUCCESS、Demo BUILD SUCCESS、go vet 干净、33 Go 测试通过
- 2026-03-25 01:01: Phase D 代码审查第十三轮 — ObservationUpdate null 序列化修复 + http-server 扩展
  - Java SDK: ObservationUpdate 添加 @JsonInclude(NON_NULL) 注解（已由 round 12 同期提交的 fc7b057 引入）
  - Java SDK: 新增 DtoTest.observationUpdate_omitsNullFields 测试验证 Jackson 序列化行为（同上）
  - Go SDK http-server: 从 12 个端点扩展到 22 个端点（已由 e5cfc20 提交）
  - E2E 测试: 从 26 个增加到 36 个（新增 tests 27-36 覆盖 batch extraction/refine/feedback 等）
  - 审查结论: Java SDK 与 Go SDK API 覆盖度一致（20 方法 vs 25 方法），wire format 对齐正确
  - go vet 干净、33 Go 测试通过、5/5 examples 编译通过、Java SDK BUILD SUCCESS（40 测试通过）、回归测试 46/46 PASS
- 2026-03-25 01:31: Phase D 代码审查第十四轮 — 逻辑 bug 修复 + 输入验证一致性
  - Java MemoryController: getMemoryHealth() 修复 `experiences.size() >= 0` → `size() > 0`（size() 永远 >= 0，之前的条件判断无意义）
  - Java MemoryController: getLatestExtraction() 和 getExtractionHistory() 添加 try-catch + ResponseEntity 包装（与 SearchController/ObservationsController/ManagementController 一致）
  - Go http-server: /extraction/latest 和 /extraction/history 移除硬编码默认 project (`/tmp/go-demo-project`)，改为 project 必填校验（与 /search, /observations, /experiences, /iclprompt, /quality, /refine 一致）
  - Go E2E 测试: extraction 端点测试添加 project 参数，匹配新的必填校验
  - go vet 干净、33 Go 测试通过、http-server 编译通过、Java Demo BUILD SUCCESS、回归测试 46/46 PASS
- 2026-03-25 02:31: Phase D 代码审查第十六轮 — Go SDK Error Helper 完善
  - Go SDK error.go: 新增 5 个缺失的错误辅助函数 — IsForbidden、IsUnprocessable、IsBadGateway、IsServiceUnavailable、IsGatewayTimeout
  - 之前这些 sentinel error 已声明但无对应的 helper，与 IsNotFound/IsBadRequest/IsRateLimited/IsInternal 不一致
  - 新增 5 个单元测试覆盖所有新 helper（含交叉验证：502/503/504 同时匹配 IsInternal）
  - Go SDK 单元测试从 33 → 38 个，全部通过
  - go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（40 测试通过）
- 2026-03-25 02:01: Phase D 代码审查第十五轮 — 输入验证一致性 + E2E 测试修复
  - Go http-server: /feedback 拆分复合校验为独立检查（observation_id, feedback_type），与 /iclprompt 一致
  - Go http-server: /session/user 同样拆分复合校验（session_id, user_id）
  - Go http-server: /ingest/prompt 新增 project/session_id/prompt 必填校验
  - Go http-server: /ingest/session-end 新增 project/session_id 必填校验
  - Go E2E 测试: tests 28-29 硬编码 /tmp/go-demo-project → $PROJECT 变量（与 round 14 的 http-server 修复一致）
  - Go E2E 测试: test 34 修复 DELETE 204 No Content 响应处理（检查 HTTP 状态码而非响应体）
  - go vet 干净、33 Go 测试通过、5/5 examples 编译通过、Java SDK BUILD SUCCESS、回归测试 46/46 PASS
- 2026-03-25 02:31: Phase D 代码审查第十六轮 — Go SDK Error Helper 完善
  - Go SDK error.go: 新增 5 个缺失的错误辅助函数 — IsForbidden、IsUnprocessable、IsBadGateway、IsServiceUnavailable、IsGatewayTimeout
  - 新增 5 个单元测试覆盖所有新 helper（含交叉验证：502/503/504 同时匹配 IsInternal）
  - Go SDK 单元测试从 33 → 38 个，全部通过
  - go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（40 测试通过）
- 2026-03-25 03:01: Phase D 代码审查第十七轮 — GetObservationsByIds wire format 修复
  - Go SDK: GetObservationsByIds 返回类型从 []dto.Observation 修正为 *dto.BatchObservationsResponse
  - 后端返回 {"observations":[], "count":0} 而非原始数组，之前返回类型不匹配导致 JSON 反序列化失败
  - 新增 BatchObservationsResponse DTO（observations + count 字段）
  - 更新 Client 接口签名 + client_methods.go 实现 + client_test.go 测试（mock 响应从 raw array 改为正确格式）
  - 移除 E2E 测试中的 "known issue" 注释（bug 已修复）
  - go vet 干净、38 Go 测试通过、http-server/basic examples 编译通过、Java SDK BUILD SUCCESS
- 2026-03-25 03:31: Phase D 代码审查第十八轮 — 输入验证漏洞 + E2E 测试 Bug + Java Demo 异常处理
  - Go http-server: /experiences 端点添加 task 必填校验（是唯一缺少 task 校验的端点，与 /iclprompt 不一致）
  - Go E2E 测试: 修复 /experiences 测试使用 query=test 而非 task=test 的 bug（task 参数始终为空，API 调用实际无效）
  - Java demo SessionLifecycleController: /start 和 /lifecycle 端点添加 try-catch（sessionStartClient.startSession() 在 backend 宕机时会抛异常，是唯一缺少错误处理的控制器）
  - go vet 干净、38 Go 测试通过、http-server 编译通过、Java Demo BUILD SUCCESS、回归测试 46/46 PASS
- 2026-03-25 04:01: Phase D 代码审查第十九轮 — Java Demo 全面异常处理补齐
  - 发现并修复 4 个之前遗漏的 Java Demo 控制器缺少 try-catch：
    - ChatController: /chat 端点两个分支（conversationId 和 CortexSessionContext）均无异常处理，AI 模型不可用时返回原始 500 栈追踪
    - ToolsController: /demo/tool 端点 fileReadTool.readFile() 可抛 IOException
    - MemoryController: /memory/experiences、/memory/icl、/memory/quality、/memory/refine、/memory/icl/truncated、/memory/experiences/filtered 共 6 个方法无异常处理
    - SessionLifecycleController: /prompt、/tool、/end 3 个方法无异常处理
  - 所有方法统一使用 ResponseEntity 包装，与 SearchController/ObservationsController/ManagementController 一致
  - Java Demo BUILD SUCCESS、go vet 干净、38 Go 测试通过、40 Java 测试通过、回归测试 46/46 PASS
- 2026-03-25 04:31: Phase D 代码审查第二十轮 — Java SDK healthCheck 响应体验证修复
  - Java SDK healthCheck() 修复：之前仅检查 HTTP 200 状态码，不验证响应体
  - 后端可返回 200 + {"status":"degraded"}，此时 Java SDK 会错误地返回 true
  - Go SDK 已正确验证响应体中 status=="ok"，Java SDK 现在与 Go SDK 行为一致
  - 新增 healthCheck 解析 response JSON，验证 status=="ok"
  - 测试修复：healthCheck_returnsTrueWhenOk 改为返回 {"status":"ok"} JSON body
  - 新增 2 个测试：healthCheck_returnsFalseWhenDegraded、healthCheck_returnsFalseWhenNullBody
  - Java SDK 单元测试从 40 → 42 个，全部通过
  - Java SDK BUILD SUCCESS、go vet 干净、38 Go 测试通过、5/5 Go examples 编译通过、Java Demo BUILD SUCCESS
- 2026-03-25 05:01: Phase D 代码审查第二十一轮 — Go SDK HTTP 客户端健壮性改进
  - Go SDK doRequest: Content-Type: application/json 改为仅在有请求体时设置（GET/DELETE 请求不再发送无意义的 Content-Type 头）
  - Go SDK TriggerRefinement: 从手动 url.QueryEscape 拼接 URL 改为使用 params map 模式（与 Search/ListObservations/GetQualityDistribution 一致）
  - 新增 doRequestNoContentWithParams 辅助方法，支持带 query params 的无响应体请求
  - 移除 client_methods.go 中不再需要的 net/url import
  - go vet 干净、38 Go 测试通过、basic/http-server examples 编译通过
- 2026-03-25 05:31: Phase D 代码审查第二十二轮 — Go http-server 中间件改进
  - Go http-server demo: 添加 panic recovery 中间件 — handler panic 时返回 500 JSON 而非崩溃
  - Go http-server demo: 添加 request logging 中间件 — 记录 method、path、duration
  - 中间件链: requestLogger → recovery → mux
  - go vet 干净、38 Go 测试通过、http-server/basic examples 编译通过、Java SDK BUILD SUCCESS（42 测试通过）
- 2026-03-25 06:01: Phase D 代码审查第二十三轮 — http-server 安全加固 + Go SDK 测试补充
  - Go http-server: readJSON 添加 maxRequestBodySize (1MB) 限制 — 防止恶意客户端发送超大 JSON body 导致 OOM
  - Go http-server: /extraction/history limit 参数从 fmt.Sscanf 改为 strconv.Atoi，添加边界校验 (1-100)
  - Go SDK: 新增 TestSessionStartRequest_WireFormat — 验证 session_start 使用 project_path（非 cwd），与 session_end/user_prompt 使用 cwd 的差异
  - Go SDK 单元测试从 38 → 39 个，全部通过
  - go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS、回归测试 46/46 PASS
- 2026-03-25 06:31: Phase D 代码审查第二十四轮 — Go SDK 补充缺失测试
  - Go SDK: 新增 5 个单元测试：
    - TestRetrieveExperiences_PathAndBody: 验证 POST 路径 + wire format (task/project/count/source) + requiredConcepts camelCase
    - TestBuildICLPrompt_PathAndBody: 验证 POST 路径 + wire format + maxChars camelCase + experienceCount 解析
    - TestGetQualityDistribution_Path: 验证 GET 路径 + project query param
    - TestSearch_OmitsEmptyParams: 验证零值字段不发送到 query string
    - TestStartSession_ErrorHandling: 验证 503 映射到 IsServiceUnavailable + IsInternal
  - Go SDK 单元测试从 39 → 44 个，全部通过
  - go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（42 测试通过）、回归测试 46/46 PASS
- 2026-03-25 07:31: Phase D 代码审查第二十六轮 — Go SDK RetryBackoff 可配置化
  - Go SDK: 新增 RetryBackoff 字段到 ClientConfig（默认 500ms，与 Java SDK CortexMemProperties.retry.backoff 一致）
  - Go SDK: 新增 WithRetryBackoff option 函数
  - Go SDK: doFireAndForget 使用 c.config.RetryBackoff 替代硬编码 500ms
  - 之前 Java SDK 允许通过配置调整 retry backoff，Go SDK 不支持，存在功能不对等
  - 新增 TestFireAndForget_CustomBackoff 测试：使用 100ms backoff 验证线性递增延迟 (100ms, 200ms)
  - Go SDK 单元测试从 46 → 47 个，全部通过
  - go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（42 测试通过）
- 2026-03-25 07:01: Phase D 代码审查第二十五轮 — 测试质量改进 + 错误辅助函数
  - Go http-server: /extraction/history limit 参数从静默忽略无效值改为返回 400 错误（之前 limit=abc 会静默使用默认值 5，消费者无法区分有效/无效输入）
  - Go SDK: TestRetrieveExperiences_PathAndBody 修复 — 请求添加 RequiredConcepts 字段，使 wire format 断言不再空转（之前请求不含该字段，断言 required_concepts!=nil 恒为真但无意义）
  - Go SDK: 新增 2 个复合错误辅助函数 — IsClientError（4xx）、IsServerError（5xx），支持按类别而非单个状态码检查
  - Go SDK: 新增 2 个单元测试覆盖新 helper（含交叉验证：400 匹配 IsClientError 但不匹配 IsServerError，500 反之）
  - Go SDK 单元测试从 44 → 46 个，全部通过
  - go vet 干净、http-server/basic examples 编译通过、Java SDK BUILD SUCCESS（42 测试通过）、回归测试 46/46 PASS
- 2026-03-25 08:31: Phase D 代码审查第二十八轮 — APIError.Unwrap() 验证 + 边界测试
  - Go SDK: 新增 11 个单元测试验证 APIError.Unwrap() 链式匹配行为：
    - Unwrap_IsNotFound: 验证 404 通过 errors.Is(err, ErrNotFound) 匹配
    - Unwrap_IsRateLimited: 验证 429 通过 errors.Is 匹配
    - Unwrap_IsServiceUnavailable: 验证 503 匹配 ErrServiceUnavailable 但不匹配 ErrInternal（Unwrap 返回精确 sentinel）
    - AsExtractsStatusCode: 验证 errors.As 可提取 APIError.StatusCode 和 Message
    - Unwrap_UnknownStatusCode: 验证 418 不匹配任何 sentinel，但 errors.As 仍可提取
    - Unwrap_AllSentinelErrors[11 子测试]: 表驱动测试验证所有 11 个 sentinel error 可达
    - NilUnwrap: 确认 nil APIError 不 panic
    - MalformedJSONResponse: 确认非 JSON 响应正确返回解析错误
    - EmptyResponse: 确认空响应体正确返回解析错误
  - 关键发现：ErrInternal 仅映射到 500，不匹配 503/504 等其他 5xx（通过 Unwrap 链）。IsInternal() helper 则匹配所有 >= 500。用户应使用 helper 而非 errors.Is 检查 5xx 范围
  - Go SDK 单元测试从 47 → 58 个，全部通过
  - go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（42 测试通过）、回归测试 46/46 PASS
- 2026-03-25 09:01: Phase D 代码审查第二十九轮 — Java SDK ICLPromptRequest.Builder maxChars 默认值修复
  - Java SDK ICLPromptRequest.Builder 默认 maxChars=4000 导致 round 12 的实现修复无效（实现检查 null 但 Builder 永远不产生 null）
  - 移除 Builder 硬编码默认值，改为 null（让后端决定默认值）
  - 移除 2 个便利构造函数（强制 4000 的 2-arg 和 3-arg 版本）
  - 新增 2 个测试：defaultMaxChars_omitsFromRequest + explicitMaxChars_includedInRequest
  - Java SDK 单元测试 42 → 44 个，全部通过
  - Go SDK 58 测试通过、go vet 干净、5/5 examples 编译通过、回归测试 46/46 PASS
- 2026-03-25 09:31: Phase D 代码审查第三十轮 — Java Backend 代码审查
  - Java IngestionController.handleSessionEnd() 添加 session_id 必填校验（之前传递 null 到 completeSessionAsync 导致静默失败，与 handleUserPrompt 不一致）
  - Java IngestionController.handleToolUse() 移除冗余 null 检查（blank 校验后 contentSessionId 已保证非 null）
  - Java RateLimitService.isValidIpAddress() 支持 IPv6（之前仅验证 IPv4，X-Forwarded-For 中的 IPv6 客户端地址被静默拒绝，fallback 到 getRemoteAddr）
  - Java Demo BUILD SUCCESS、Go SDK 58 测试通过、go vet 干净、回归测试 46/46 PASS
- 2026-03-25 10:01: Phase D 代码审查第三十一轮 — Go E2E 测试脚本修复
  - Go E2E 测试脚本: 修复 stale "Uncovered methods" 清单（tests 27-36 已覆盖全部 25 个 Go SDK 方法，但清单仍列出 8 个"未覆盖"方法）
  - Go E2E 测试脚本: 移除重复的 final summary（test 36 后和脚本末尾各打印一次）
  - Go E2E 测试脚本: 新增 comprehensive Final Coverage Summary 列出全部 25 个方法及其覆盖方式
  - Go E2E 测试脚本: 最终报告移至脚本末尾（唯一真实来源）
  - Go SDK 59 测试通过、go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（44 测试通过）
- 2026-03-25 11:01: Phase D 代码审查第三十二轮 — Go SDK HTTP 客户端健壮性改进
  - Go SDK Close(): 修复资源泄漏 — 之前返回 nil 不清理 idle connections，现在调用 Transport.CloseIdleConnections()
  - Go SDK doRequest(): 添加 Accept: application/json 请求头（HTTP 最佳实践，明确告知服务器期望的响应格式）
  - Go SDK doFireAndForget(): 添加中间重试日志（之前仅在最终失败时记录，现在每次重试都会 warn，与 Java SDK 行为一致）
  - 新增 4 个单元测试：Close_CleansUpIdleConnections、DoRequest_SetsAcceptHeader、DoRequest_NoContentTypeForGet、DoRequest_SetsContentTypeForPost
  - Go SDK 单元测试从 59 → 63 个，全部通过
  - go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS
- 2026-03-25 11:31: Phase D 代码审查第三十三轮 — 集成层健壮性改进 + 单元测试补充
  - Eino Retriever: 添加 nil client panic guard、空 query guard（返回 nil 而非发起无意义请求）、logger + WithRetrieverLogger、命名未使用的 opts 参数
  - Genkit Retriever: 添加 nil client panic guard、空 query guard、logger + WithRetrieverLogger、error 日志
  - LangChainGo Memory: 修复关键 bug — LoadMemoryVariables 之前静默吞掉所有错误（包括 429/503/network），现在记录到 stderr 并返回空 memory（graceful degradation）；添加 nil client panic guard、logger + WithMemoryLogger
  - 新增 25 个集成层单元测试：eino 7 个 + genkit 8 个 + langchaingo 10 个
  - 测试覆盖：nil client panic、默认值、选项应用、空输入、成功路径、错误路径（含日志验证）、per-call 覆盖
  - Go SDK 总计 88 个单元测试（63 core + 25 integration），全部通过
  - go vet 干净、5/5 examples 编译通过、回归测试 46/46 PASS

### 第三十四轮（2026-03-25 12:01）— Java Backend 控制器健壮性改进
- [x] SessionController.startSession() 返回正确的 HTTP 状态码（之前总是 200，现在验证失败返回 400，异常返回 500）
- [x] SessionController.getSession() 未找到返回 404（之前返回 200 + error 字段）
- [x] SessionController 两个方法都改为返回 ResponseEntity<Map> 而非原始 Map（与其他控制器一致）
- [x] MemoryController.updateObservation() 修复 content/narrative 字段无法清空的 bug（之前 explicit null 被 val != null 检查跳过，现在显式 null 会清空字段）
- [x] MemoryController.buildICLPrompt() maxChars 类型转换安全化（instanceof Number 替代直接 Number 强转，防止 ClassCastException）
- [x] IngestionController.handleUserPrompt() prompt_number 类型转换安全化（同样修复）
- [x] Java Backend BUILD SUCCESS、Go SDK tests PASS、go vet 干净、回归测试 46/46 PASS

### 第三十五轮（2026-03-25 12:31）— Java Backend 控制器输入验证补全
- [x] MemoryController.triggerRefine() 添加 project 参数必填校验（之前 null/blank 会传递到 eventPublisher，与 retrieveExperiences/buildICLPrompt 不一致）
- [x] MemoryController.retrieveExperiences() 添加 task 参数必填校验（之前 null 会传递到 expRagService 导致下游 NPE 风险）
- [x] MemoryController.retrieveExperiences() 修复 (Integer) 强转 ClassCastException（Jackson 可能反序列化为 Long，改为 ((Number).intValue())）
- [x] MemoryController.buildICLPrompt() 添加 task 参数必填校验（同样修复）
- [x] SessionController.parseProjectsParam() 修复重复 Javadoc 注释（之前有两个 /** 块，第一个是孤立的旧注释）
- [x] Java Backend BUILD SUCCESS、Go SDK 88 测试通过、go vet 干净、回归测试 46/46 PASS

### 第三十六轮（2026-03-25 13:01）— updateObservation null 清除一致性修复
- [x] Java Backend updateObservation() `title`/`subtitle`/`source` 字段修复 null 清除行为（round 34 只修复了 content/narrative，这三个字段仍用 `!= null` 检查跳过显式 null，无法清空）
- [x] 统一所有字符串字段处理模式：containsKey 检查 → val==null 则清空 → instanceof String 验证（与 facts/concepts/extractedData 列表/Map 字段一致）
- [x] Go http-server `/observation/patch` 端点修复：请求结构体从 `string` 改为 `*string`，nil 检查替代空字符串检查（用户发送 `{"title":""}` 现在可以清空 title，之前被 `!= ""` 检查跳过）
- [x] Java Backend BUILD SUCCESS、Go SDK 88 测试通过、go vet 干净、5/5 examples 编译通过

### 第三十七轮（2026-03-25 13:31）— Go SDK HTTP 超时配置可配置化
- [x] Go SDK: 新增 `Timeout` 和 `ConnectTimeout` 字段到 ClientConfig（匹配 Java SDK CortexMemProperties 的 connectTimeout=10s + readTimeout=30s）
- [x] Go SDK: 新增 `WithTimeout` 和 `WithConnectTimeout` option 函数
- [x] Go SDK: NewClient() 现在从 timeout 配置自动构建 http.Client + http.Transport（含 DialContext、TLSHandshakeTimeout、IdleConnTimeout 等合理默认值）
- [x] Go SDK: 之前 Timeout=30s 硬编码且无法配置 ConnectTimeout，Java SDK 支持独立配置两者，存在功能不对等
- [x] Go SDK README.md: 新增完整的 Option 文档表格（含所有 8 个选项的默认值和描述）
- [x] 新增 4 个单元测试：WithTimeout_AppliedToClient、WithConnectTimeout_AppliedToClient、WithTimeout_ExpiresOnSlowServer、DefaultTimeouts_MatchJavaSDK
- [x] Go SDK 单元测试 63 → 67 个（core），全部通过
- [x] go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（44 测试通过）

### 第三十八轮（2026-03-25 14:01）— SDK HTTP 客户端可观测性 + 配置健壮性
- [x] Go SDK doRequest(): 添加 `User-Agent: cortex-mem-go/1.0.0` 请求头（服务器端日志可识别 SDK 客户端及其版本，与 Java SDK 行为一致）
- [x] Go SDK NewClient(): 添加 MaxRetries 配置校验（MaxRetries < 1 时 clamp 到 1，防止无效配置导致零次请求或负数循环）
- [x] Java SDK RestClient: 添加 `User-Agent: cortex-mem-java/1.0.0` 默认请求头（与 Go SDK 对称）
- [x] 新增 4 个单元测试：TestDoRequest_SetsUserAgent、TestDoRequest_SetsUserAgentOnPost、TestMaxRetries_ZeroClampsToOne、TestMaxRetries_NegativeClampsToOne
- [x] Go SDK 单元测试 67 → 71 个（core），全部通过
- [x] go vet 干净、5/5 examples 编译通过、Java SDK BUILD SUCCESS（44 测试通过）、回归测试 46/46 PASS

### 第三十九轮（2026-03-25 14:31）— Java Backend 类型校验一致性修复
- [x] Java MemoryController.updateObservation() `validateStringList` 改为严格模式：非字符串元素返回 null 触发 400（之前静默丢弃非字符串元素，与其他字段类型校验不一致）
- [x] Java IngestionController.handleObservation() `safeGetStringList` 改为严格模式：非字符串元素返回 null（与 MemoryController 一致）
- [x] IngestionController.handleObservation() 添加 null 检查：facts/concepts/files_read/files_modified 无效时返回 400（之前静默接受无效数据）
- [x] Java Backend BUILD SUCCESS、Go SDK 71 测试通过、go vet 干净、5/5 examples 编译通过、回归测试 46/46 PASS

### 第四十轮（2026-03-25 15:01）— Java Demo ResponseEntity 一致性修复
- [x] Java Demo MemoryController.getMemoryHealth() 返回类型从 Map 改为 ResponseEntity<Map>（之前错误时 HTTP 200 + "status":"error"，客户端无法通过 HTTP 状态码判断失败）
- [x] Java Demo SessionLifecycleController.startSession() 同样修复（Map → ResponseEntity<Map>）
- [x] Java Demo SessionLifecycleController.fullLifecycle() 同样修复（Map → ResponseEntity<Map>）+ 添加 try-catch 异常处理（之前工具调用失败会导致未捕获异常）
- [x] Java Demo ChatController.chat() 返回类型从 String 改为 ResponseEntity<String>（之前错误时 HTTP 200 + "Error:" 前缀文本，现在返回 HTTP 500）
- [x] Java Demo ToolsController.runToolWithCapture() 同样修复（String → ResponseEntity<String>）
- [x] 修复前 5 个控制器中有 5 个方法在错误时返回 HTTP 200，现在全部控制器统一返回正确的 HTTP 状态码
- [x] Java Demo BUILD SUCCESS、Go SDK 88 测试通过、go vet 干净、5/5 examples 编译通过、回归测试 46/46 PASS

### 第四十一轮（2026-03-25 15:31）— Demo 全面 ResponseEntity + http-server 字段覆盖补齐
- [x] Java Demo ProjectsController.listProjects() 从 Map<String, Object> 改为 ResponseEntity<Map<String, Object>>（round 40 遗漏的最后一个 controller）
- [x] Java Demo SessionLifecycleController.fullLifecycle() 添加 catch 异常处理（try-finally 中缺少 catch，fileReadTool.readFile() 抛 IOException 时直接 500 原始栈追踪）
- [x] Go http-server `/observation/patch` 端点扩展：从只支持 title/content/source 增加到支持全部 6 个 dto.ObservationUpdate 字段（新增 facts/concepts/extractedData）
  - 之前 http-server demo 只暴露了 ObservationUpdate 的一半能力，消费者无法通过 demo 更新 facts/concepts/extractedData
  - slice/map 字段同样使用 nil 检查区分"未提供"（跳过）和"提供空值"（清空）
- [x] Java Demo BUILD SUCCESS、Go SDK 71 测试通过、go vet 干净、5/5 examples 编译通过、回归测试 46/46 PASS

### 第四十二轮（2026-03-25 16:01）— Java Demo MemoryController 空错误体修复
- [x] Java Demo MemoryController: 4 个方法错误时返回空 body（`.build()`）→ 修复为 JSON 错误响应
  - `getExperiences()`: 返回类型 `ResponseEntity<List<Experience>>` → `ResponseEntity<?>`，错误时返回 `{"error":"..."}`
  - `getQuality()`: 返回类型 `ResponseEntity<QualityDistribution>` → `ResponseEntity<?>`，错误时返回 JSON
  - `getExperiencesFiltered()`: 同上
  - `getExtractionHistory()`: 返回类型 `ResponseEntity<List<Map>>` → `ResponseEntity<?>`，错误时返回 JSON
  - 与 round 40-41 修复的其他控制器一致：所有控制器在错误时都返回有意义的 JSON 响应体
- [x] Java Demo BUILD SUCCESS、Go SDK 88 测试通过、go vet 干净、回归测试 46/46 PASS

### 第四十三轮（2026-03-25 16:31）— Java Backend 控制器一致性修复
- [x] ContextController.injectContext() 返回类型从 Map 改为 ResponseEntity<Map>，添加 try-catch（之前无异常处理，backend 崩溃时返回原始 500 栈追踪，与所有其他控制器不一致）
- [x] ContextController.generateContext() 返回类型从 Map 改为 ResponseEntity<Map>（之前错误时返回 HTTP 200 + error 字段，客户端无法通过状态码判断失败）
- [x] ContextController.isSafeDirectory() 修复路径遍历检查逻辑 bug（之前 normalizedPath.startsWith(resolvedPath) 对绝对路径恒为 true，对相对路径恒为 false，检查完全无效）
- [x] TestController.testLlm() 和 testEmbedding() 返回类型从 Map 改为 ResponseEntity<Map>（之前错误时返回 HTTP 200 + "status":"error"，与所有其他控制器不一致）
- [x] ModeController.setActiveMode() 修复 body(null)（之前异常时返回空 body，改为有意义的错误响应）
- [x] Java Backend BUILD SUCCESS、Go SDK 88 测试通过、go vet 干净、回归测试 46/46 PASS

### 第四十四轮（2026-03-25 17:31）— ImportController/CursorController/LogsController 修复
- [x] ImportController.bulkImport(): session 和 summary 导入错误现在正确追踪到 ImportStats（之前异常被 catch 但仅 log，调用方收到不准确的 error 计数）。新增 ImportStats.addError() 方法。
- [x] CursorController.updateContext() 和 updateContextCustom(): 未注册项目返回 404（之前返回 200 + success=false，与 round 34 修复的 SessionController 不一致）
- [x] LogsController.getLogs(): lines 参数校验为 1-10000 范围（之前负数/零可导致空 subList，极大值可导致 OOM）
- [x] Java Backend BUILD SUCCESS、Go SDK 测试通过、go vet 干净、5/5 examples 编译通过、回归测试 46/46 PASS
