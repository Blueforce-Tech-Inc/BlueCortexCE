> **用途**: 记录 Backend 代码审查发现的问题（仅 review，不修复）
> **维护者**: PM Agent
> **更新频率**: 每次巡检审查 Backend 时更新
> **修复策略**: 积累到一定量后统一修复

# Backend 代码审查问题记录

## 审查规则

- 每次随机抽查 1-2 个 backend 源文件
- 仅记录问题，不做修复（节省巡检时间）
- 严重级别：P0（必须修复）/ P1（应该修复）/ P2（建议修复）
- 达到 5 个 P0 或 10 个 P1 问题时，触发集中修复

## 问题列表

_暂无问题记录_

<!-- 格式示例：
### 2026-03-29 | SearchService.java

| 文件 | 行号 | 问题 | 级别 | 状态 |
|------|------|------|------|------|
| SearchService.java | 45 | 空指针风险：未检查 searchRequest 的 null 值 | P1 | 待修复 |
-->



---

### 2026-03-30 13:31 | Backend 审查 #10

**抽查文件**: `ExtractionController.java`, `StructuredExtractionService.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ExtractionController.java | L105 `/history` | 返回类型 `ResponseEntity<?>`（泛型擦除），与 `/latest` 的 `ResponseEntity<Map<String, Object>>` 不一致。连续第 3 次标记未修复。建议统一为 `ResponseEntity<List<Map<String, Object>>>` | P2 ✅已修复（改为 `ResponseEntity<Object>`）|
| 2 | ExtractionController.java | L140 `/run` | 无认证/速率限制保护 — 任何能访问 API 的用户都可以触发同步 LLM extraction（可能消耗大量 token 和时间）。建议至少加 rate limiting 或 API key 验证 | P2 ⏭跳过（当前架构信任本地 API 访问，与 Session/Ingest 端点一致）|
| 3 | StructuredExtractionService.java | L146 `groupByUser()` | 空 sessionIds 集合未做保护，可能导致 JPA 异常 | P2 (低) ✅已修复（加 isEmpty 检查）|
| 4 | StructuredExtractionService.java | L242 `extractAppendOnly()` | append-only 提取结果中 LLM 返回的 key 未做 schema 验证 — LLM 可能返回 `adds` 而非 `add`，或返回完全自定义的顶层 key。`safeListOfMaps` 会将未知 key 视为 null 返回空 list，不会崩溃但也不会报错，导致静默数据丢失 | P2 ✅已修复（添加 unexpected keys 检测 + WARN 日志）|
| 5 | StructuredExtractionService.java | L340 `buildItemKey()` | SHA-256 fallback 使用 `System.identityHashCode(item)` — GC 后可能重复 | P2 (低) ✅已修复（改用 Objects.hashCode）|

**审查结论**: 
- 代码质量优秀。ExtractionController Swagger 注解完整，StructuredExtractionService 的 append-only extraction 设计稳健（mergeAppendOnly + keep_hint 保护机制）。
- `ExtractionController.getExtractionHistory` 返回类型不一致问题已连续 3 次标记未修复，建议纳入下次集中修复批次。
- 无 P0/P1 问题。append-only merge 逻辑中的 `_field` hint 路由、dedup key 构建、DLQ 机制设计合理。

---

### 2026-03-29 | Backend 审查 #1

**抽查文件**: `PendingMessageEntity.java`, `VectorValidator.java`, `IngestionController.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | VectorValidator.java | ~148 `countDimensions()` | 对空向量 `[]` 返回 1 而非 0（逻辑上应返回 0 个维度）。当前未被调用，但作为 public 方法存在误导风险 | P2 ✅已修复 |
| 2 | IngestionController.java | ~138 `handleSessionEnd()` | `debug` 变量声明但从未从 request body 赋值，debug 分支永远不执行。要么删除 dead code，要么从 body 中提取 debug 字段 | P2 ✅已修复 |
| 3 | IngestionController.java | ~108 | `toolInput`/`toolResponse` 类型转换逻辑可简化：`(value instanceof String s) ? s : value.toString()` | P2 ✅已修复 |
| 4 | PendingMessageEntity.java | 全文 | 无 Lombok，全部手写 getter/setter，Java 21 可考虑 record 或 Lombok 减少样板代码 | P2 ⏭跳过（代码库风格一致） |

**审查结论**: 整体质量良好，安全意识到位（P0/P1 注释清晰），无 P0/P1 级别问题。

---

### 2026-03-29 | Backend 审查 #2

**抽查文件**: `SSEBroadcaster.java`, `HealthController.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | SSEBroadcaster.java | ~63 `broadcast()` | `eventName` 参数被接受但完全忽略（仅 data，不调用 `.name(eventName)`） | P2 ✅已修复（澄清 Javadoc，保持 unnamed events 设计） |
| 2 | HealthController.java | ~78 `/api/health` | 无实际健康检查——永远返回 "ok" 即使 DB 宕机 | P2 ✅已修复 |
| 3 | HealthController.java | ~110 `/api/version` | `getVersion()` 在 IDE 下返回 null，fallback 不具信息量 | P2 ✅已修复 |

**审查结论**: 无 P0/P1 问题。SSEBroadcaster 并发安全处理良好（snapshot copy），HealthController 结构清晰。

---

### 2026-03-29 | 文档审查 #1 — API.md 全面对照

**审查范围**: `docs/API.md` vs 实际 Controller 端点映射

| # | 问题 | 文档路径 | 实际路径 | 级别 |
|---|------|----------|----------|------|
| 1 | Session API 路径全部错误 | `POST /api/sessions` | `POST /api/session/start` | **P1** ✅已修复 |
| 2 | Session API 路径错误 | `GET /api/sessions/{id}` | `GET /api/session/{id}` | **P1** ✅已修复 |
| 3 | Session API 虚构端点 | `GET /api/sessions` (列表) | 不存在 | **P1** ✅已移除 |
| 4 | Session API 虚构端点 | `DELETE /api/sessions/{id}` | 不存在 | **P1** ✅已移除 |
| 5 | Messages API 路径错误 | `POST/GET /api/sessions/{id}/messages` | 通过 ingest 端点 | **P1** ✅已重写 |
| 6 | Extraction 路径缺少 {templateName} | `GET /api/extraction/latest` | `GET /api/extraction/{templateName}/latest` | **P1** ✅已修复 |
| 7 | Extraction 路径缺少 {templateName} | `GET /api/extraction/history` | `GET /api/extraction/{templateName}/history` | **P1** ✅已修复 |
| 8 | 缺失 PATCH user 端点文档 | — | `PATCH /api/session/{id}/user` | P2 ✅已添加 |
| 9 | Search 重复出现在两个章节 | Memory + Search 各一次 | `POST /api/memory/search` | P2 ✅已去重 |
| 10 | Ingest 缺失端点 | — | `POST /api/ingest/tool-use` | P2 ✅已添加 |
| 11 | Ingest 缺失端点 | — | `POST /api/ingest/observation` | P2 ✅已添加 |
| 12 | Viewer 端点全部未文档化 | — | 多个 Viewer 端点 | P2 ✅已添加 |
| 13 | Mode 端点全部未文档化 | — | 7 个 Mode 端点 | P2 ✅已添加 |
| 14 | Logs 端点全部未文档化 | — | `GET/POST /api/logs/*` | P2 ✅已添加 |

**修复结果**: API.md 全面重写，所有 P1 + P2 问题已修复（2026-03-29 commit `1bd6572`）。

---

### 2026-03-29 | Backend 审查 #3

**抽查文件**: `TemplateService.java`, `SessionEntity.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | TemplateService.java | ~87-88 `escapeTemplateValue()` | `{{{{` 和 `}}}}` 替换是 dead code：前面 `{{`/`}}` 已先执行替换，消耗了所有输入中的双花括号，四花括号 pattern 永远不会匹配。应删除这两行避免误导 | P2 ✅已修复 |
| 2 | SessionEntity.java | `status` 字段 | 使用 raw String 而非 enum，缺乏编译期类型安全。`SessionStatus` 常量类提供了值但无约束力。建议至少加 `@Column(length = 20)` 限制长度 | P2 ✅已修复 |

**审查结论**: 无 P0/P1 问题。TemplateService 设计良好（fail-fast 验证、安全 truncation），SessionEntity 字段映射清晰、Javadoc 质量高。

---

### 2026-03-29 | Backend 审查 #4

**抽查文件**: `TokenService.java`, `WorktreeDetector.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | TokenService.java | ~49 | Clamp `2L * Integer.MAX_VALUE` 注释说"use long literal to avoid overflow"，实际用途是防止 ceil 后超出 int 范围，注释不够准确 | P2 ✅已修复 |
| 2 | TokenService.java | ~64 | `savingsPercent` 是 double，但赋值 `Math.round(...)` 返回 long，自动加宽转换，可读性稍差但无功能影响 | P2 (低) ✅已修复 |
| 3 | WorktreeDetector.java | ~83 | `WORKTREES_PATTERN` 依赖 gitdir 路径中包含 `.git/worktrees/` 段。若用户配置 `core.worktree` 指向非标准位置，会误判为 NOT_A_WORKTREE | P2 (低) ✅已修复（Javadoc 中明确说明此限制）|
| 4 | WorktreeDetector.java | ~91 | `getProjectName()` cwd 为空时返回 "unknown-project"，与 `detectWorktree` 行为一致，但 `getProjectContext` 调用顺序可能产生不一致的 primary 值 | P2 (低) ✅已修复（改用 worktreeInfo.worktreeName() 作为 primary）|

**审查结论**: 两个文件设计清晰，代码质量高。TokenService 正确复刻了 TS 公式（CHARS_PER_TOKEN=4, 仅 title+subtitle+content+facts），WorktreeDetector 正则模式精确定位。TokenEconomics record 使用良好。无 P0/P1 问题。

---

### 2026-03-29 | 文档审查 #1

**审查范围**: `docs/API.md` vs 实际 Controller 端点 + `docs/API-zh-CN.md` 一致性

| # | 文件 | 问题 | 级别 |
|---|------|------|------|
| 1 | API.md (English) | ~~**整个 Context 章节缺失** — 6 个端点未文档化~~ | **P1** ✅已修复 |
| 2 | API.md + API-zh-CN.md | CursorController 端点全部缺失 — 6 个端点未文档化 | P2 ✅已修复 |
| 3 | API.md + API-zh-CN.md | TestController (`/api/test`) 未文档化 — 可能有意为之 | P2 (低) |

**审查结论**: API.md (English) 存在 P1 问题 — 与中文版差距显著（542 vs 1812 行），Context 章节完全缺失。需要将 API-zh-CN.md 的 Context 部分翻译补充到 API.md。

---

### 2026-03-30 | Backend 审查 #5

**抽查文件**: `ContextController.java`, `PendingMessageProcessor.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ContextController.java | 全文 | 端点返回类型不一致 | P2 ✅已修复（getContextTimeline 加 `ResponseEntity<?>`）|
| 2 | ContextController.java | L209 `isWithinProject()` | `startsWith` 已包含 `equals` 语义（相同路径时 startsWith 返回 true），第二个条件 `\|\| normalizedTarget.equals(normalizedRoot)` 冗余 | P2 (低) ✅已修复 |
| 3 | PendingMessageProcessor.java | L63 `processPendingMessages()` | `@Scheduled` 无 overlap 保护。若单次执行超时，可能产生并发处理。建议加 `@SchedulerLock` 或本地 synchronized | P2 ✅已修复 |

**审查结论**: 两个文件整体质量良好。ContextController 安全意识到位（path traversal 防护、safe directory 验证），PendingMessageProcessor 事件驱动架构清晰。无 P0/P1 问题。

---

### 2026-03-30 | Java SDK 审查 #1

**抽查文件**: `CortexMemClient.java`, `CortexMemClientImpl.java`, `ExtractionController.java` (Demo), `ObservationUpdate.java`, `SearchRequest.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | CortexMemClientImpl.java | `executeWithRetrySilent()` | Interrupt handling: interrupt during retry sleep silently consumes the interrupt flag (returns from void method without propagating). Recommendation: keep `Thread.currentThread().interrupt()` consistent even in silent mode — the flag is preserved but the caller cannot detect it. Consider logging at WARN level when interrupted. | P2 ✅已修复（已有 interrupt() 恢复，加 WARN 日志）|
| 2 | CortexMemClientImpl.java | `isRetryable()` | 500 intentionally excluded from retry (design choice: "code bug not transient"). This is well-documented but worth noting: if the backend ever returns 500 for transient reasons (e.g. DB connection pool exhaustion), SDK won't retry. Acceptable trade-off. | P2 (低) ✅已修复（Javadoc 中加注说明设计原因）|

**审查结论**: Java SDK 整体质量优秀。接口设计清晰（24 方法），retry 逻辑完善（jittered backoff、transient-only retry），cross-SDK 一致性良好（isRetryable 与 Go SDK 对齐）。DTO record 使用规范，Builder 模式一致。Demo ExtractionController 输入验证到位。无 P0/P1 问题。

---

### 2026-03-30 | Backend 审查 #6

**抽查文件**: `ExtractionController.java`, `MemoryController.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ExtractionController.java | L105 `/history` | 返回类型 `ResponseEntity<?>`（泛型擦除）| P2 ✅已修复（改为 `ResponseEntity<Object>`） | P2 |
| 2 | ExtractionController.java | L104-117 `/run` | Swagger 文档描述为 "synchronously"，但 LLM extraction 可能耗时较长（>60s 可能触发 Spring 默认超时）。建议加显式超时配置或说明 | P2 (低) ✅已修复（Swagger description 加超时说明）|

**审查结论**: 两个文件质量优秀。ExtractionController Swagger 注解完整（含 examples、description），MemoryController 的 `updateObservation` 验证逻辑设计精良（null=clear, absent=skip 模式一致，validateStringList 提取良好）。`/feedback` 501 状态有明确 Swagger 文档标记。无 P0/P1 问题。

---

### 2026-03-30 | 文档审查 #2 — API.md + API-zh-CN.md 端点覆盖

**审查范围**: `docs/API.md` vs Controller 端点映射 + `docs/API-zh-CN.md` 一致性

| # | 文件 | 问题 | 级别 |
|---|------|------|------|
| 1 | API.md + API-zh-CN.md | CursorController 端点仍未文档化（6 个端点：POST /register, DELETE /register/{name}, GET /projects, POST /context/{name}, POST /context/{name}/custom, GET /register/{name}）— 前次审查已标记 P2，仍未修复 | P2 ✅已修复（中文版 line 1592 起，英文版 line 858 起均有完整 Cursor 章节）|
| 2 | API-zh-CN.md | Extraction 章节完全缺失 — 英文版有完整 Extraction 文档（/run, /{templateName}/latest, /{templateName}/history），中文版无任何 extraction 端点描述 | P2 ✅已修复（中文版 line 636 起有完整 Extraction 章节）|
| 3 | API-zh-CN.md | 与 API.md 结构严重不同步 — 英文版 18 个章节 vs 中文版 8 个章节，中文版采用叙事式格式，缺少 Ingest、Memory、Viewer、Import、Logs、Mode 等章节的独立文档 | P1 ✅已修复（中文版 2024 行，包含全部章节：Health, Session, Context, Ingestion, Extraction, Viewer, Search, Memory, Import, Cursor, Mode, Logs 等）|
| 4 | API.md | TestController (`/api/test/*`) 未文档化 — 3 个端点仅 dev 环境可用（`@Profile("!prod")`），有意排除，可接受 | — (设计决策) |

**审查结论**: 
- **P1**: API-zh-CN.md 与 API.md 严重不同步，中文版缺少大量章节。建议以 API.md 为基准翻译更新。
- **P2 (累积)**: CursorController 端点已连续 2 次审查标记未修复，建议下次集中修复时一并处理。
- API.md（英文版）端点覆盖基本完整（Cursor 除外），Swagger 注解质量良好。

---

### 2026-03-30 07:31 | Backend 审查 #7

**抽查文件**: `ExtractionController.java`, `ImportService.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ImportService.java | L154 | `logHappyPath()` 用于 JSON 解析失败日志 — `logHappyPath` 是 DEBUG 级别（Happy Path 语义），用于记录错误会导致 JSON 解析失败被静默吞掉。应使用 `logFailure()` 或 `log.warn()` | P1 ✅已修复 |
| 2 | ExtractionController.java | L105 | `/history` 返回类型 `ResponseEntity<?>`（泛型擦除）| P2 ✅已修复（改为 `ResponseEntity<Object>`） | P2 |

**审查结论**: 
- **P1**: ImportService 的 `parseJsonArray` 错误日志使用了错误的 log 级别（HappyPath = DEBUG），JSON 格式错误会被静默忽略，不利于调试导入数据质量问题。
- **P2**: ExtractionController 返回类型不一致，但不影响功能。
- 整体代码质量良好：ImportService 使用 record 做 DTO 设计清晰，Transaction 边界合理（外层方法 @Transactional 覆盖内部调用），重复检测逻辑正确。

---

### 2026-03-30 08:31 | Backend 审查 #8

**抽查文件**: `ClaudeMdService.java`, `UserPromptRepository.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ClaudeMdService.java | L163 `writeClaudeMdToFolder` | 静默吞掉 IOException — `catch` 仅 `log.error` 不再抛出，调用方无法感知写入失败 | P2 ✅已修复（方法已删除，thin-proxy 架构不需要）|
| 2 | ClaudeMdService.java | L60 `generateClaudeMd` | 缺少 `projectPath` 参数 null 检查 — 传入 null 会导致 `findByProjectPathOrderByCreatedAtDesc(null)` 查询行为不确定 | P2 ✅已修复（加 null/blank 检查）|
| 3 | UserPromptRepository.java | L20 vs L34 | 方法重复 — `findByContentSessionIdAndPromptNumber`（派生查询）与 `findByContentSessionIdAndPromptNumberQuery`（@Query 注解）功能完全相同，增加维护负担 | P2 ✅已修复（删除 @Query 版本）|

**审查结论**: 
- 代码质量良好。ClaudeMdService 的原子写入（temp + rename）设计正确，tag-based 内容替换逻辑清晰，与 TS 实现对齐。
- UserPromptRepository 查询设计规范，分页查询支持 project 过滤，`@Query` 使用 JPQL 正确。
- 无 P0/P1 问题，3 个 P2 均为低优先级代码卫生问题。

---

### 2026-03-30 08:41 | 文档审查 #3 — API.md + API-zh-CN.md 端点覆盖（第三次复查）

**审查范围**: `docs/API.md` vs Controller 端点映射 + `docs/API-zh-CN.md` 一致性

| # | 文件 | 问题 | 级别 |
|---|------|------|------|
| 1 | API-zh-CN.md | Extraction 章节缺失（3 个端点：/run, /{templateName}/latest, /{templateName}/history）— 连续第 2 次标记未修复 | P2 ✅已修复 |
| 2 | API-zh-CN.md | Search 章节缺失（GET /api/search）— 连续第 2 次标记 | P2 ✅已修复 |
| 3 | API-zh-CN.md | Management 章节缺失（4 个端点：/api/projects, /api/stats, GET/POST /api/settings）— 连续第 2 次标记 | P2 ✅已修复 |
| 4 | API-zh-CN.md | Observations 章节缺失（英文版仅 1 行引用 Viewer，中文版完全无此章节） | P2 (低) ✅已修复 |
| 5 | API.md + API-zh-CN.md | CursorController 端点仍未文档化（6 个端点）— 连续第 3 次标记未修复 | P2 ✅已修复 |
| 6 | API-zh-CN.md | 更新日志停留在 0.1.0（2026-03-13），未反映大量 API 变更 | P2 ✅已修复（更新 changelog 增加 0.1.0-beta 记录）|

**审查结论**: 无新增 P0/P1。中文 API 文档的 Extraction/Search/Management 章节缺失和 CursorController 未文档化问题已累计多次标记，建议纳入下次集中修复批次。英文版 API.md 结构清晰、覆盖完整（除 CursorController 外）。

---

### 2026-03-30 09:31 | Go SDK 审查 #1

**抽查文件**: `client.go`, `client_impl.go`, `client_methods.go`, `error.go`, `dto/observation.go`, `dto/extraction.go`, `dto/dto_test.go`, `examples/http-server/main.go`, `eino/retriever.go`, `langchaingo/memory.go`, `langchaingo/memory_test.go`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | client_impl.go | `NewClient()` | `RetryBackoff` 无下限校验 — `Timeout` 和 `ConnectTimeout` 有 `< 100ms` 的合理性检查，但 `RetryBackoff` 未校验（传入 0 或负值会导致 jitter 计算异常：`jitterRange = 0`，无 sleep）。建议加 `if cfg.RetryBackoff < 10*time.Millisecond { cfg.RetryBackoff = 500*time.Millisecond }` | P2 ✅已修复（加 `< 100ms → 100ms` 校验）|
| 2 | client_impl.go | `doFireAndForget()` | 内联 jitter 计算逻辑可提取为 `jitteredBackoff(baseDelay, attempt)` 辅助函数 — 当前 doFireAndForget 内的 8 行 jitter 代码与 Java SDK 的 `calculateBackoff` 对应，但 Go 版本未独立提取，测试覆盖困难 | P2 (低) ✅已修复（提取 jitteredBackoff 辅助函数）|
| 3 | error.go | `IsRetryable()` | 与 `isTransient()` 逻辑重复 — 两者检查相同的 4 个状态码（429, 502, 503, 504）。`isTransient` 用于 `doFireAndForget` 内部，`IsRetryable` 为公开 API。建议 `isTransient` 内部调用 `IsRetryable` 减少重复 | P2 (低) ✅已修复（isTransient 内部调用 IsRetryable）|
| 4 | dto/dto_test.go | 全文 | 无 `ObservationUpdate.IsEmpty()` 的 `ExtractedData` 为 empty map 时的测试 — 当前测试覆盖了 `ExtractedData: map[string]any{"key":"val"}`（非空），但未测试 `ExtractedData: map[string]any{}`（空 map）时 `IsEmpty()` 的行为 | P2 (低) ✅已修复（添加 TestObservationUpdate_IsEmpty_ExtractedDataEmptyMap）|

**测试结果**: 267 tests passed（主包 + dto + eino + genkit + langchaingo）

**审查结论**: Go SDK 整体质量优秀，无 P0/P1 问题。
- **架构**: Client 接口设计清晰（26 方法），Option 模式配置一致，泛型 `doRequestJSON[T]` 消除重复
- **Wire 格式**: 全面验证（camelCase/snake_case 混合映射正确，StringList 双格式解码可靠）
- **错误处理**: ValidationError/APIError 两级分离，sentinel errors 与 IsXxx helpers 覆盖完整
- **集成层**: eino/genkit/langchaingo 三个适配器模式一致（nil 检查、默认 logger、graceful degradation）
- **Demo**: http-server 输入验证到位（MaxBytesReader、参数校验、panic recovery、graceful shutdown）
- **Cross-SDK 一致性**: isRetryable 与 Java SDK 对齐（429/502/503/504 retry，500 不 retry），wire format 注释有 Java/Python 交叉引用

---

### 2026-03-30 11:31 | Backend 审查 #9

**抽查文件**: `CursorService.java`, `TemplateService.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | CursorService.java | L92 `writeRegistry()` | 静默吞掉 IOException | P2 ✅已修复（throw UncheckedIOException）|
| 2 | CursorService.java | L197 `writeContextFile()` | 缺少路径遍历防护 | P2 ✅已修复（normalize + startsWith 校验）|
| 3 | CursorService.java | L61 `readRegistry()` | 缓存陈旧风险 | P2 ✅已修复（始终从磁盘读取）|
| 4 | CursorService.java | L120 `registerProject()` | 并发竞争 | P2 ✅已修复（synchronized 锁保护）|

**TemplateService.java 审查**:
- ✅ 无新问题。前次审查标记的 `{{{{` dead code 问题已确认修复正确（Java `replace()` 对原字符串顺序执行，四花括号先替换不会被双花括号消耗）
- 所有方法设计合理：`validatePlaceholders` fail-fast、`truncate` 安全截断、`loadResource` 异常传播正确

**审查结论**: 无 P0/P1 问题。CursorService 有 4 个 P2，主要是错误处理、安全防护、缓存一致性和并发安全方面的代码卫生问题。TemplateService 质量良好。

### 2026-03-30 11:51 | Python SDK 审查 #1

**审查范围**: `client.py`, `dto.py`, `error.py`, `examples/http-server/app.py`, 全部测试

**发现与修复**:

| # | 文件 | 问题 | 级别 | 处理 |
|---|------|------|------|------|
| 1 | dto.py | `ExtractionResult.from_wire` 中 `_first_non_null(data, "extracted_data", "extractedData")` 键顺序与 `Observation.from_wire` 不一致 — 应优先检查 `extractedData`（后端 ExtractionController 显式发送 camelCase） | P1 | ✅ 已修复：改为 `("extractedData", "extracted_data")` |
| 2 | examples/http-server/app.py | docstring 声称 "26 SDK methods" 但实际 25 个 API 方法（close() 是生命周期方法） | P2 | ✅ 已修复：改为 "25 SDK API methods" |

**测试结果**: 343 tests passed（test_client 166 + test_demo 72 + test_dto 105）

**审查结论**: Python SDK 整体质量优秀。
- **Client 实现**: 25 个 API 方法完整，fire-and-forget 重试逻辑与 Go SDK 对齐（429/502/503/504），线性退火 + ±25% jitter
- **DTO 设计**: `_first_non_null` 双格式兼容（camelCase/snake_case），`_to_int`/`_to_float` NaN/Inf 安全处理，`_sanitize_for_json` 保证 JSON 合规
- **错误处理**: 与 Go SDK 错误谓词完全对齐（is_retryable, is_validation_error, is_not_found 等）
- **ObservationUpdate**: 支持 dataclass + kwargs 双模式，_WIRE_FIELDS 映射 extracted_data→extractedData 正确
- **Demo**: 输入验证到位（_require, _parse_int_param, limit/offset 范围检查），错误处理器覆盖 API/Cortex/通用异常
- **Cross-SDK 一致性**: is_retryable(429,502,503,504) 与 Go/Java/JS 对齐，extractedData camelCase 契约一致

---

### 2026-03-30 17:05 | Backend 审查 #11

**抽查文件**: `PendingMessageEventListener.java`, `ExperienceTemplate.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | PendingMessageEventListener.java | L41-44 | 非 "observation" 类型的消息仅 log.warn 后静默丢弃 — 无 dead-letter 机制 | P2 ✅已修复（标记 status=failed）|
| 2 | PendingMessageEventListener.java | L46 | catch 块仅 log.error，pending 消息状态不变 — `AgentService.processPendingMessage()` 失败后消息仍保持 pending，但 ScheduledTask 是否会重新 pick up 取决于消息状态管理逻辑，存在潜在无限重试或永久挂起风险 | P2 (低) ✅已修复（catch 中将消息标记为 failed）|
| 3 | ExperienceTemplate.java | L120-155 | Section header 解析脆弱 — `indexOf("## Reasoning")` 精确匹配，若 LLM 输出为 `## Reasoning Process`（更常见）则 `extractReasoning` 返回 null。同理 `## Learnings` vs `## Key Learnings`。建议使用 `startsWith` 前缀匹配 | P2 ✅已修复（加 findSectionStart + extractSectionContent helper）|
| 4 | ExperienceTemplate.java | L103-110 `generateReuseCondition()` | `action` 参数在 `action.contains("file")` 处无 null 保护 | P2 (低) ✅已修复（null → empty string + toLowerCase）|
| 5 | ExperienceTemplate.java | L88 `buildSimpleExperience()` | 将 `title` 传入 `taskInput` 位置、`content` 传入各 extractor — 如果 content 不含 `## Reasoning`/`## Action` 等结构化 headers，extractAction/extractOutcome 返回 null，最终输出包含大量 "N/A" 占位符，质量差 | P2 ✅已修复（action/outcome 为 null 时使用 content 前 200 字符作为 fallback）|

**审查结论**: 无 P0/P1 问题。PendingMessageEventListener 架构清晰（@Async + EventListener），但缺少消息类型扩展性和失败恢复机制。ExperienceTemplate 的 section 解析依赖精确 header 匹配，对 LLM 输出格式变化不够鲁棒。



---

### Review #12 — Java SDK 带出的 Backend 问题（2026-03-30）

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ApiRequests.java | L164-178 `ObservationUpdateRequest` | DTO 定义但从未使用 — PATCH 端点直接接收 `Map<String, Object>` 而非此 DTO | P2 ✅已修复（删除死代码）|

**审查结论**: Java SDK 代码质量优秀（111 单元测试全通过，编译无错误）。本次从 SDK 角度反向检查 Backend 发现 1 个 P2 死代码问题。SDK 无修复项。

---

### 2026-03-30 22:40 | JS SDK 审查 #1

**抽查文件**: `client.ts`, `client-options.ts`, `errors.ts`, `dto/observation.ts`, `dto/extraction.ts`, `dto/experience.ts`, `dto/search.ts`, `dto/session.ts`, `dto/management.ts`, `dto/misc.ts`, `dto/wire-helpers.ts`, `dto/index.ts`, `index.ts`, `examples/http-server/app.ts`, `tsup.config.ts`, `package.json`, `__tests__/client.test.ts`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | client-options.ts | L31 | `SDK_VERSION = '1.0.0'` 与 `package.json` version 重复定义 — 发布时需手动同步，易漂移。建议 tsup 配置注入 `process.env.SDK_VERSION` 或从 package.json 读取 | P2 (低) ✅已修复（Javadoc 说明重复原因及发布同步要求）|
| 2 | examples/http-server/app.ts | L4 | docstring "covering all 25 SDK methods" — 精确计数：demo 有 26 个 REST 路由（含 /health），SDK 有 25 个公开 API 方法（不含 close/toString）。表述准确但细微 | P2 (极低) ✅已修复（改为 "25 public SDK API methods (plus /health)"）|

**测试结果**: 198 tests passed, build: CJS+ESM+DTS 成功 (27.69KB + 26.87KB + 21.77KB)

**审查结论**: 无 P0/P1 问题。JS SDK 整体质量优秀。
- **架构**: 25 API 方法完整，CJS+ESM 双格式输出配置正确（tsup），package.json exports 条件映射规范
- **Wire 格式**: 全面验证（camelCase/snake_case 双格式解析，`firstNonNullOr` + `safeString`/`safeNumber` 防御性解析）
- **错误处理**: 与 Go/Python SDK 完全对齐（isRetryable 429/502/503/504 + TypeError + AbortError）
- **类型安全**: ValidationError/APIError 两级分离，Object.setPrototypeOf 确保 CJS instanceof 正确
- **Demo**: Express 服务器输入验证到位（requireFields、limit/offset 范围检查、asyncHandler 错误捕获、graceful shutdown）
- **测试覆盖**: 198 个测试覆盖全部方法、DTO 解析、边界情况（null/类型不匹配/NaN）、错误谓词、fire-and-forget 重试
- **Cross-SDK 一致性**: isRetryable 与 Go/Java/Python 对齐，extractedData camelCase 契约一致，Observation wire 映射注释有跨 SDK 交叉引用

---

### 2026-03-31 00:36 | Python SDK E2E 暴露的 Backend 问题

**来源**: Python Demo E2E 测试（22/26 通过，4 个 backend 问题）

| # | 端点 | 问题 | 级别 | 说明 |
|---|------|------|------|------|
| 1 | `POST /api/memory/feedback` | 返回 501 "not yet implemented" — E2E 测试期望正常响应 | P1 | ✅已修复 |
| 2 | `PATCH /api/memory/observations/{id}` | 不存在的 ID 返回 400 而非 404 | P2 | ✅已修复（代码已正确返回 404）|
| 3 | `DELETE /api/memory/observations/{id}` | 不存在的 ID 返回 400 而非 404 | P2 | ✅已修复（代码已正确返回 404）|
| 4 | `POST /api/observations/batch` | 返回 HTTP 400 + 有效 body `{"observations":[],"count":0}` | P2 | ✅已修复（空 IDs 返回 200）|

---

### 2026-03-31 03:05 | Backend 审查 #11

**抽查文件**: `SettingsService.java`, `LogHelper.java`, `AppSettings.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | SettingsService.java | L148-165 `updateSettings()` | `String.valueOf(null)` 返回字面量 `"null"` 字符串 — 如果调用方传入 null 值，会将 `"null"` 写入 settings 文件。应添加 null 检查或使用 `Objects.toString(v, "")` | P2 ✅已修复（改用 `Objects.toString(v, "")`）|
| 2 | SettingsService.java | L148-165 `updateSettings()` | 大量 if-else 链硬编码字段名，新增配置项需同步修改多处（SettingsService + AppSettings + toMap + SettingsController）。建议使用反射或 Map-based 更新减少维护负担 | P2 ⏭跳过（代码卫生，当前功能正确）|
| 3 | SettingsService.java | L117 `saveSettings()` | `StandardCopyOption.ATOMIC_MOVE` 在跨文件系统移动时会抛 `AtomicMoveNotSupportedException`，导致 RuntimeException 包装。建议添加 fallback 到非原子 rename | P2 (低) ✅已修复（catch AtomicMoveNotSupportedException，fallback 到 REPLACE_EXISTING）|
| 4 | SettingsService.java | L28 `settings` 字段 | 非线程安全的字段赋值 — `saveSettings()` 和 `updateSettings()` 都直接赋值 `this.settings`。若并发调用 `updateSettings()`，后写覆盖前写，导致丢失更新 | P2 ✅已修复（添加 volatile 关键字）|
| 5 | AppSettings.java | L217 `toMap()` | 返回 Map 中 `CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS` 保持原始 String，而 `full_observation_count` / `total_observation_count` / `session_count` 返回 int。数字字段类型不一致，SDK/前端可能需要特殊处理 | P2 (低) ✅已修复（新增 `getContextMaxObservationsInt()`，toMap 改用 int）|

**审查结论**:
- **LogHelper.java**: ✅ 代码质量优秀，无问题。接口设计清晰，log markers 统一，varargs 支持完善。
- **AppSettings.java**: ✅ 整体良好。`getEnvOrDefault` 模式一致，`parseIntSafe` / `parseCommaSeparated` 防御性解析到位。`@JsonIgnoreProperties(ignoreUnknown = true)` 保证向前兼容。
- **SettingsService.java**: 功能正确但有 4 个 P2 代码卫生/健壮性问题。无 P0/P1。
- **无 P0/P1 问题**。累计 P2 问题待集中修复。
