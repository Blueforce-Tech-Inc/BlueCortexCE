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
| 3 | WorktreeDetector.java | ~83 | `WORKTREES_PATTERN` 依赖 gitdir 路径中包含 `.git/worktrees/` 段。若用户配置 `core.worktree` 指向非标准位置，会误判为 NOT_A_WORKTREE | P2 (低) |
| 4 | WorktreeDetector.java | ~91 | `getProjectName()` cwd 为空时返回 "unknown-project"，与 `detectWorktree` 行为一致，但 `getProjectContext` 调用顺序可能产生不一致的 primary 值 | P2 (低) |

**审查结论**: 两个文件设计清晰，代码质量高。TokenService 正确复刻了 TS 公式（CHARS_PER_TOKEN=4, 仅 title+subtitle+content+facts），WorktreeDetector 正则模式精确定位。TokenEconomics record 使用良好。无 P0/P1 问题。

---

### 2026-03-29 | 文档审查 #1

**审查范围**: `docs/API.md` vs 实际 Controller 端点 + `docs/API-zh-CN.md` 一致性

| # | 文件 | 问题 | 级别 |
|---|------|------|------|
| 1 | API.md (English) | ~~**整个 Context 章节缺失** — 6 个端点未文档化~~ | **P1** ✅已修复 |
| 2 | API.md + API-zh-CN.md | CursorController 端点全部缺失 — 6 个端点未文档化 | P2 |
| 3 | API.md + API-zh-CN.md | TestController (`/api/test`) 未文档化 — 可能有意为之 | P2 (低) |

**审查结论**: API.md (English) 存在 P1 问题 — 与中文版差距显著（542 vs 1812 行），Context 章节完全缺失。需要将 API-zh-CN.md 的 Context 部分翻译补充到 API.md。

---

### 2026-03-30 | Backend 审查 #5

**抽查文件**: `ContextController.java`, `PendingMessageProcessor.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ContextController.java | 全文 | 端点返回类型不一致：`/inject` 返回 `ResponseEntity<Map>`，`/recent` 返回 `ResponseEntity<?>`，`/prior-messages` 返回裸 `Map`。建议统一为 `ResponseEntity<Map>` | P2 |
| 2 | ContextController.java | L209 `isWithinProject()` | `startsWith` 已包含 `equals` 语义（相同路径时 startsWith 返回 true），第二个条件 `\|\| normalizedTarget.equals(normalizedRoot)` 冗余 | P2 (低) ✅已修复 |
| 3 | PendingMessageProcessor.java | L63 `processPendingMessages()` | `@Scheduled` 无 overlap 保护。若单次执行超时，可能产生并发处理。建议加 `@SchedulerLock` 或本地 synchronized | P2 ✅已修复 |

**审查结论**: 两个文件整体质量良好。ContextController 安全意识到位（path traversal 防护、safe directory 验证），PendingMessageProcessor 事件驱动架构清晰。无 P0/P1 问题。

---

### 2026-03-30 | Java SDK 审查 #1

**抽查文件**: `CortexMemClient.java`, `CortexMemClientImpl.java`, `ExtractionController.java` (Demo), `ObservationUpdate.java`, `SearchRequest.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | CortexMemClientImpl.java | `executeWithRetrySilent()` | Interrupt handling: interrupt during retry sleep silently consumes the interrupt flag (returns from void method without propagating). Recommendation: keep `Thread.currentThread().interrupt()` consistent even in silent mode — the flag is preserved but the caller cannot detect it. Consider logging at WARN level when interrupted. | P2 |
| 2 | CortexMemClientImpl.java | `isRetryable()` | 500 intentionally excluded from retry (design choice: "code bug not transient"). This is well-documented but worth noting: if the backend ever returns 500 for transient reasons (e.g. DB connection pool exhaustion), SDK won't retry. Acceptable trade-off. | P2 (低) |

**审查结论**: Java SDK 整体质量优秀。接口设计清晰（24 方法），retry 逻辑完善（jittered backoff、transient-only retry），cross-SDK 一致性良好（isRetryable 与 Go SDK 对齐）。DTO record 使用规范，Builder 模式一致。Demo ExtractionController 输入验证到位。无 P0/P1 问题。

---

### 2026-03-30 | Backend 审查 #6

**抽查文件**: `ExtractionController.java`, `MemoryController.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | ExtractionController.java | L105 `/history` | 返回类型 `ResponseEntity<?>`（泛型擦除），与 `/latest` 的 `ResponseEntity<Map<String, Object>>` 不一致。建议统一为 `ResponseEntity<List<Map<String, Object>>>` | P2 |
| 2 | ExtractionController.java | L104-117 `/run` | Swagger 文档描述为 "synchronously"，但 LLM extraction 可能耗时较长（>60s 可能触发 Spring 默认超时）。建议加显式超时配置或说明 | P2 (低) |

**审查结论**: 两个文件质量优秀。ExtractionController Swagger 注解完整（含 examples、description），MemoryController 的 `updateObservation` 验证逻辑设计精良（null=clear, absent=skip 模式一致，validateStringList 提取良好）。`/feedback` 501 状态有明确 Swagger 文档标记。无 P0/P1 问题。

---

### 2026-03-30 | 文档审查 #2 — API.md + API-zh-CN.md 端点覆盖

**审查范围**: `docs/API.md` vs Controller 端点映射 + `docs/API-zh-CN.md` 一致性

| # | 文件 | 问题 | 级别 |
|---|------|------|------|
| 1 | API.md + API-zh-CN.md | CursorController 端点仍未文档化（6 个端点：POST /register, DELETE /register/{name}, GET /projects, POST /context/{name}, POST /context/{name}/custom, GET /register/{name}）— 前次审查已标记 P2，仍未修复 | P2 |
| 2 | API-zh-CN.md | Extraction 章节完全缺失 — 英文版有完整 Extraction 文档（/run, /{templateName}/latest, /{templateName}/history），中文版无任何 extraction 端点描述 | P2 |
| 3 | API-zh-CN.md | 与 API.md 结构严重不同步 — 英文版 18 个章节 vs 中文版 8 个章节，中文版采用叙事式格式，缺少 Ingest、Memory、Viewer、Import、Logs、Mode 等章节的独立文档 | P1 |
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
| 2 | ExtractionController.java | L105 | `/history` 返回类型 `ResponseEntity<?>`（泛型擦除），与 `/latest` 的 `ResponseEntity<Map<String, Object>>` 不一致。建议统一为 `ResponseEntity<List<Map<String, Object>>>` | P2 |

**审查结论**: 
- **P1**: ImportService 的 `parseJsonArray` 错误日志使用了错误的 log 级别（HappyPath = DEBUG），JSON 格式错误会被静默忽略，不利于调试导入数据质量问题。
- **P2**: ExtractionController 返回类型不一致，但不影响功能。
- 整体代码质量良好：ImportService 使用 record 做 DTO 设计清晰，Transaction 边界合理（外层方法 @Transactional 覆盖内部调用），重复检测逻辑正确。
