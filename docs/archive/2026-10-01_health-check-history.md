# Health Check History Archive (2026-04-08 ~ 2026-10-01 00:53)

> **Archived**: 2026-10-01，自 `docs/drafts/health-check-task.md` 压缩迁移。
> **Rule**: 归档文件创建后不得修改。后续历史达阈值时使用新的日期命名归档。

## 巡检历史


### 2026-10-01 00:53 | 统一维护任务（Go SDK 六轮 + SDK README 六轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Go SDK（六轮，dto/experience.go） | `ExperienceRequest`/`ICLPromptRequest` 的 camelCase wire 字段（`requiredConcepts`/`userId`/`maxChars`）与后端 `@JsonProperty` 一致；`Experience` 的 SNAKE_CASE 字段注释标注了 E2E 验证来源。无问题、无修改 |
| 文档审查方向 | ✅ SDK README（六轮，@EnableCortexMem 核验） | Java README Step 3 的 `@EnableCortexMem` 用法与 starter 实现一致：真实 `@Import(CortexMemAutoConfiguration.class)` 注解，含 `captureEnabled`/`retrievalEnabled` 属性。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `c18afbb7…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `32d5dfb` / `c18afbb7…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Go SDK 完成（六轮），下一方向 Python SDK；文档审查轮换推进：SDK README 完成（六轮），下一方向设计文档。

### 2026-10-01 00:50 | 统一维护任务（Backend 六轮 + API 文档六轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Backend（六轮，TemplateService + CursorService） | `TemplateService`：启动时占位符校验（fail-fast）、escape/truncate 助手；`CursorService`：注册表读写含存在性检查与目录创建；注册为低频本地操作，非原子写在单用户部署模型下不构成缺陷（设计观察，无需修改）。无问题、无修改 |
| 文档审查方向 | ✅ API 文档（六轮，memory/refine 章节） | 文档与控制器逐项一致：query 参数 `project` 必填、200 响应 Schema `{"status":"triggered",...}` 与 `@Content` example 相同；缺失参数实测返回 400 ✅。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `c18afbb7…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `32d5dfb` / `c18afbb7…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Backend 完成（六轮），下一方向 Go SDK；文档审查轮换推进：API 文档完成（六轮），下一方向 SDK README。

### 2026-10-01 00:47 | 统一维护任务（Demo 五轮 + 用户指南五轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Demo（五轮，Projects + Tools 控制器） | `ProjectsController`：只读信息端点；`ToolsController`：`CortexSessionContext.begin/end` 在 finally 中保证清理，项目解析含回退。无问题、无修改 |
| 文档审查方向 | ✅ 用户指南（五轮，DEVELOPMENT.md 结构声明） | 项目结构中引用的全部目录与点名文件核验存在（controller/service/test/proxy/openclaw-plugin/docs/scripts 目录 + 4 个 config 类）。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `c18afbb7…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `32d5dfb` / `c18afbb7…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Demo 完成（五轮），下一方向 Backend（第六循环）；文档审查轮换推进：用户指南完成（五轮），下一方向 API 文档（第六循环）。

### 2026-10-01 00:45 | 统一维护任务（JS/TS SDK 五轮 + 架构文档五轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ JS/TS SDK（五轮，observation.ts 解析） | 4 个扩展字段（accessCount/refinedAt/refinedFromIds/userComment）的解析使用 safe 转换 + 双键变体回退（snake_case 主、camelCase 备），与 R1 实测的后端 snake_case wire 一致，与 Go/Python 解析行为对齐。无问题、无修改 |
| 文档审查方向 | ✅ 架构文档（五轮，安全架构端口声明） | "Fat Server 127.0.0.1:37777 本地绑定" 声明与 `application.yml:3` 的 `SERVER_ADDRESS:127.0.0.1` 默认值一致；Docker 覆盖为 0.0.0.0 的差异已在 DOCKER_README 单独记录（R16 核验），两文档各述其部署形态、无矛盾。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `c18afbb7…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `32d5dfb` / `c18afbb7…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：JS/TS SDK 完成（五轮），下一方向 Demo；文档审查轮换推进：架构文档完成（五轮），下一方向用户指南。

### 2026-10-01 00:42 | 统一维护任务（Python SDK 五轮 + 设计文档五轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Python SDK（五轮，record_observation wire 构建） | body 构建与后端 ToolUseRequest 契约逐字段一致（session_id/cwd/tool_name 必填，tool_input/tool_response/prompt_number/source/extractedData 按值省略，空 source 省略附注释说明后端语义差异）；ValidationError 提前校验。无问题、无修改 |
| 文档审查方向 | ✅ 设计文档（五轮，99-changelog 一致性） | changelog 最新 v30 的锁共享声明在代码中核实属实（`MemoryRefineService.tryExecuteWithProjectLock` 存在且被 `reExtractForSession` 调用）；`index.md` 的 24.6.md 描述仍停在 v28，已更新为涵盖 v29（mergeAppendOnly/keep_hint 完善）与 v30（projectLocks 共享）的当前状态 |
| 代码变化检测 | ✅ 无变化 | 指纹 `c18afbb7…`（871 条）与基线完全一致（index.md 为 docs/ 下文档） |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `32d5dfb` / `c18afbb7…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Python SDK 完成（五轮），下一方向 JS/TS SDK；文档审查轮换推进：设计文档完成（五轮），下一方向架构文档。

### 2026-10-01 00:39 | 统一维护任务（Go SDK 五轮 + SDK README 五轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Go SDK（五轮，dto/session.go） | `SessionStartRequest` wire 字段（`session_id`/`project_path`/`user_id`）与后端 DTO 一致；注释正确区分 session-start 用 `project_path` 而 ingest 端点用 `cwd` 的差异。无问题、无修改 |
| 文档审查方向 | ✅ SDK README（五轮，Wire Format 章节） | Python README 的 4 条 wire 声明逐一对照后端 `@JsonProperty` 核实：`project_path`→`cwd`（observation）✅、`project_path`→`project_path`（session-start）✅、`extracted_data`→`extractedData` ✅、`required_concepts`→`requiredConcepts` ✅。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `c18afbb7…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `32d5dfb` / `c18afbb7…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Go SDK 完成（五轮），下一方向 Python SDK；文档审查轮换推进：SDK README 完成（五轮），下一方向设计文档。

### 2026-10-01 00:33 | 统一维护任务（Backend 五轮 + API 文档五轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Backend（五轮，LlmService + PendingMessageProcessor） | `LlmService`：null 安全响应处理、usage 提取、Optional 配置与明确错误信息，干净；`PendingMessageProcessor`：**发现并修复真实缺陷** —— 构造器读取字段注入的 `@Value enabled`，注入前恒为 false，启动日志永远显示 `enabled=false`。修复：日志移至 `@PostConstruct`，重启后实测输出 `enabled=true` 正确反映配置 |
| 连续 3 轮修改后检查 | ✅ 通过 | 全仓扫描"构造器读字段 @Value"模式：仅此一处缺陷（MemoryRefineService 为构造器参数注入，非缺陷）；编译通过；重启后健康 |
| SDK E2E | ⏭ 不适用 | 本轮改动仅 Backend 内部日志时机，未触及 SDK/Demo |
| 文档审查方向 | ✅ API 文档（五轮，extraction/run 示例） | 文档"查询参数 projectPath 必填"与 `ExtractionController` 的 `@RequestParam String projectPath` 及 SDK 调用方式（queryParam）一致；60 秒同步执行提示与 @Operation 描述一致。无问题、无修改 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `9964e2f5…` → `c18afbb7…`（871 条），日志时机修复所致，必须执行完整验收 |
| 回归测试 | ✅ 45/46 | `bash scripts/regression-test.sh --skip-build`，0 失败、1 项按脚本跳过 |
| EXTRACTION 验收 | ✅ 25/25 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`，0 失败 0 跳过 |
| 验收基线 | ✅ 已更新 | `accepted_commit=32d5dfb1b1d5882b9c42b0fe377d6e21e3e0a22e`，指纹 `c18afbb7…`，状态 `passed` |
| Backend findings | ✅ 0 未决 | 本轮发现已当场修复并验收 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Backend 完成（五轮），下一方向 Go SDK；文档审查轮换推进：API 文档完成（五轮），下一方向 SDK README。

### 2026-10-01 00:19 | 统一维护任务（Demo 四轮 + 用户指南四轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Demo（四轮，Experiences + Feedback 控制器） | 校验与错误处理模式与已查控制器一致（badRequest 参数校验、internalServerError 兜底、健康端点）。无问题、无修改 |
| 文档审查方向 | ✅ 用户指南（四轮，硬编码数量检查） | TESTING.md 及中文版均无硬编码的 N/N 测试数量（符合 HEARTBEAT "以脚本实际输出为准" 纪律）。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Demo 完成（四轮），下一方向 Backend（第五循环）；文档审查轮换推进：用户指南完成（四轮），下一方向 API 文档（第五循环）。

### 2026-10-01 00:16 | 统一维护任务（JS/TS SDK 四轮 + 架构文档四轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ JS/TS SDK（四轮，wire-helpers.ts） | 防御性解析助手完备：类型安全转换（拒绝对象→"[object Object]"腐蚀）、`safeStringOrStringList` 双格式（JSON 编码数组/逗号分隔，镜像 Go StringList）、`safeRecord` 拒绝非纯对象、`firstNonNullOr` 键变体回退。无问题、无修改 |
| 文档审查方向 | ✅ 架构文档（四轮，技术栈版本核验） | **发现并修复真实错误**：ARCHITECTURE.md/ZH 两处称代理层为 "Express"，实际 `proxy/package.json` 仅依赖 axios，`proxy.js` 用 Node 内置 `http` —— 已将架构图与技术栈表改为 "Node.js/axios"（中英四处同步）。其余版本声明核验一致：Spring Boot 3.3.13（pom 父版本）、Java 21（Dockerfile temurin:21）、pgvector pg16（compose） |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致（.md 不在指纹范围） |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：JS/TS SDK 完成（四轮），下一方向 Demo；文档审查轮换推进：架构文档完成（四轮），下一方向用户指南。

### 2026-10-01 00:11 | 统一维护任务（Python SDK 四轮 + 设计文档四轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Python SDK（四轮，search 参数构建） | `search()` 参数构建与后端 `/api/search` 契约一致：project 必填、6 个可选参数按真值省略（0 哨兵 = 后端默认），响应经 `SearchResult.from_wire` 解析（118 项 dto 测试覆盖）。无问题、无修改 |
| 文档审查方向 | ✅ 设计文档（四轮，22.md 走查发现落地核验） | 22.md（v21 SDK API 走查）的三项发现已实现核验：experiences 接口的 userId 隔离在 `ExpRagService` 已落地（Phase 3 会话过滤），`ExperienceRequest` DTO 含 source 等扩展字段。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Python SDK 完成（四轮），下一方向 JS/TS SDK；文档审查轮换推进：设计文档完成（四轮），下一方向架构文档。

### 2026-10-01 00:02 | 统一维护任务（Go SDK 四轮 + SDK README 四轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Go SDK（四轮，NewClient + 集成适配器） | `NewClient` 配置解析健壮：默认值、尾斜杠规范化、超时/退避下限保护、Transport 加固（TLS 校验未跳过、IdleConnTimeout）；langchaingo `Memory` 与 genkit `Retriever` 均为函数式选项 + 注入式客户端，模式一致。无问题、无修改 |
| 文档审查方向 | ✅ SDK README（四轮，Go/Java 示例签名） | Go README Quick Start 的 `NewClient`/`Close`/`StartSession(ctx, dto.SessionStartRequest)`/`RecordObservation` 签名与已验证 Client 接口一致；Java README Step 4 的 `CortexMemoryAdvisor` 注入 + `defaultAdvisors` 用法与 starter 自动装配实现一致。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Go SDK 完成（四轮），下一方向 Python SDK；文档审查轮换推进：SDK README 完成（四轮），下一方向设计文档。

### 2026-10-01 00:00 | 统一维护任务（Backend 四轮 + API 文档四轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Backend（四轮，ModeService + SettingsService） | `ModeService`：ConcurrentHashMap 模式缓存、继承 deepMerge、默认模式回退，规范；`SettingsService.updateSettings`：双前缀键（`mode`/`CLAUDE_MEM_MODE`）与 WebUI 契约一致，list-or-string 双格式处理正确。无问题、无修改 |
| 文档审查方向 | ✅ API 文档（四轮，context/generate 示例） | 文档请求体 `{"project_path": "..."}` 与 `ContextGenerateRequest` 的 `@JsonProperty("project_path")` 完全一致；空值回退 cwd 与 Schema 描述一致。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Backend 完成（四轮），下一方向 Go SDK；文档审查轮换推进：API 文档完成（四轮），下一方向 SDK README。

### 2026-09-30 23:53 | 统一维护任务（Demo 三轮 + 用户指南三轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Demo（三轮，SessionLifecycle + Ingest 控制器） | 两控制器校验与错误处理模式一致（badRequest 参数校验、internalServerError 兜底、PATCH /user 三项参数校验）。无问题、无修改 |
| 文档审查方向 | ✅ 用户指南（三轮，DOCKER_README 环境变量表 vs docker-compose.yml） | 抽验 6 项默认值全部一致：`IMAGE_NAME`=ghcr.io/…:main、`POSTGRES_PORT`=5433、`POSTGRES_DATA_PATH`=postgres_data、`SERVER_PORT`=37777、`JAVA_OPTS`=-XX:+UseZGC -XX:MaxRAMPercentage=75.0、`LOGS_PATH`=claude-mem-logs。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Demo 完成（三轮），下一方向 Backend（第四循环）；文档审查轮换推进：用户指南完成（三轮），下一方向 API 文档（第四循环）。

### 2026-09-30 23:48 | 统一维护任务（JS/TS SDK 三轮 + 架构文档三轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ JS/TS SDK（三轮，errors.ts） | 重试语义与 Python/Go/Java 完全一致（429/502/503/504 + fetch TypeError 网络错误 + AbortController 超时；500 排除）；isRateLimited/isClientError/isServerError helper 集完备。无问题、无修改 |
| 文档审查方向 | ✅ 架构文档（三轮，迁移与 Controller 清单） | Flyway 迁移文件实数 16 个（V1–V8、V11–V18；V9/V10 无对应文件，最高版本 V18）—— ARCHITECTURE.md 仅按"哪些迁移改了哪张表"描述，逐项准确，无硬编码总数，无需修改；Controller 13 个清单与目录一致（R0 已核）。信息记录：历史报告（2026-05-03 条目）中的"18 个 migrations"与当前文件数不符，属历史记录不改，以文件系统为准。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：JS/TS SDK 完成（三轮），下一方向 Demo；文档审查轮换推进：架构文档完成（三轮），下一方向用户指南。

### 2026-09-30 23:42 | 统一维护任务（Python SDK 三轮 + 设计文档三轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Python SDK（三轮，client.py 请求层） | `_request`/`_request_json`/`_fire_and_forget` 审查通过：closed 守卫、`raise_for_status` 状态码映射、204/非 JSON 优雅降级为 None、fire-and-forget 线性退避 ±25% 抖动且仅重试暂态错误（与 Go doFireAndForget 对齐）。无问题、无修改 |
| 文档审查方向 | ✅ 设计文档（三轮，index 尺寸表 + 8.md） | `index.md` 尺寸表核验：字节精确测量后与表中数值在 ±1KB 舍入内一致（初判"漂移"系 du 块粒度误判，按宁少勿错不改表）；最大文件 25.md=42KB，全部 ≤50KB 规则成立；`8.md` "10/10 Resolved" 声明属实（10 项均 ✅ Answered）。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限，待推 `24faf55` 起本地提交 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Python SDK 完成（三轮），下一方向 JS/TS SDK；文档审查轮换推进：设计文档完成（三轮），下一方向架构文档。

### 2026-09-30 23:39 | 统一维护任务（Go SDK 三轮 + SDK README 三轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Go SDK（三轮，error.go + StringList） | `StringList` 自定义解码通过（JSON 数组/字符串编码 JSON 双格式、空串→nil，专项测试覆盖）；`error.go` 与 Python error.py 同构（哨兵错误 + Unwrap、IsRetryable 429/502/503/504 排除 500、完整状态码 helper 集）。无代码问题 |
| 文档审查方向 | ✅ SDK README（三轮，Quick Start 对照源码） | 发现并修复 JS README 不准确注释："session.response only contains session_db_id" → 实际 SDK 类型含 `session_db_id`/`context`/`prompt_number`（`dto/session.ts:25`），wire 中的 `session_id`/`updateFiles` 不在 SDK 类型内；中英版已同步修正；其他 3 个 SDK README 无同类声明。设计观察（非缺陷）：JS 的 SessionStartResponse 不含 `session_id` 而 Go 版含（调用方本身已知该值，属简化设计，行为正确） |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致（README 属 .md 排除范围） |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 下轮重试 | 上轮刚确认 403 凭据阻塞 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Go SDK 完成（三轮），下一方向 Python SDK；文档审查轮换推进：SDK README 完成（三轮），下一方向设计文档。

### 2026-09-30 23:34 | 统一维护任务（Backend 三轮 + API 文档三轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Backend（三轮，WorktreeDetector + QualityScorer） | `WorktreeDetector`：边界处理完备（null cwd、根目录、非标准 core.worktree 限制已文档化、正则兼容 Windows 分隔符）；`QualityScorer`：基础分+效率/内容加成、[0,1] 截断、LLM 失败回退规则评分。无问题、无修改 |
| 文档审查方向 | ✅ API 文档（三轮，session-start 示例核验） | EN/ZH 两版 `POST /api/session/start` 请求示例均为正确的 snake_case 契约（`session_id`/`project_path`，与 R1 核实的 DTO `@JsonProperty` 一致）；文档尾部 changelog（+16~+29）显示历年 wire-format 修复均中英同步。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Backend 完成（三轮），下一方向 Go SDK；文档审查轮换推进：API 文档完成（三轮），下一方向 SDK README。

### 2026-09-30 23:32 | 统一维护任务（Demo 二轮 + 用户指南二轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Demo（二轮，patrol 点名的 3 个 Java Demo 控制器） | `SearchController`：输入校验完备（project 必填、limit 0-100 与 SDK 限制对齐、offset 非负）；`ObservationsController`：批量接口校验（ids 非空、批量上限 100、逐元素非空）；`ManagementController`：只读端点 + refine 触发，参数可选处理正确。无问题、无修改 |
| 文档审查方向 | ✅ 用户指南（二轮，脚本引用核验） | TESTING.md、DEVELOPMENT.md、DEPLOYMENT.md 中引用的全部 `scripts/*.sh` 文件逐一对照 scripts/ 目录确认存在；DEVELOPMENT.md 的构建命令为标准 `mvn clean package`。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 本轮重试仍 403 | `wubuku` 凭据无写权限，待推 `24faf55` 起的全部本地提交 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Demo 完成（二轮），下一方向 Backend（第三循环）；文档审查轮换推进：用户指南完成（二轮），下一方向 API 文档（第三循环）。

### 2026-09-30 23:30 | 统一维护任务（JS/TS SDK 二轮 + 架构文档二轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ JS/TS SDK（二轮，client.ts 内部） | 私有 HTTP 层审查通过：每个公共方法 `assertNotClosed` 守卫、`AbortController` 超时 + `finally clearTimeout`、10MB 响应限制（与 Go SDK 一致）、URL 参数自动省略 undefined/空值、Bearer 认证头。无问题、无修改 |
| 文档审查方向 | ✅ 架构文档（二轮，实现对照） | MCP 工具表与 `ClaudeMemMcpTools.java` 完全一致（search/timeline/get_observations/save_memory/recent 恰好 5 个）；`service/` 目录全部 29 个类均在 ARCHITECTURE.md 中有记载。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 低频重试策略 | 下轮重试（上轮刚确认 403 凭据阻塞） |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：JS/TS SDK 完成（二轮），下一方向 Demo；文档审查轮换推进：架构文档完成（二轮），下一方向用户指南。

### 2026-09-30 23:27 | 统一维护任务（Python SDK 二轮 + 设计文档二轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Python SDK（二轮，error.py + Flask demo） | `error.py` 审查通过：完整异常层级（`APIError` 子类按状态码划分）、`is_retryable` 与 Go/Java 一致（429/502/503/504 + 网络错误，500 排除）、错误消息提取覆盖 object/array/string/非 JSON/空体五种格式；Flask demo 路由与错误处理器结构清晰（demo 77 项测试已在 374 套件中覆盖）。无问题、无修改 |
| 文档审查方向 | ✅ 设计文档（二轮，26.md 验收计划 vs 实际脚本） | 对照发现 26.md 尾部 "Definition of Done: ALL 15 tests pass" 易被误读为当前验收数量（实际脚本已演进为超集）；按 HEARTBEAT "不在文档中硬编码过时数量" 规则，补充一条说明指向唯一验收定义（`EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`，以脚本实际输出为准），未写死任何数量 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致（26.md 为 docs/ 下文档，不在指纹范围） |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 低频重试策略 | 维持随轮低频重试（上轮刚确认 403 凭据阻塞） |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Python SDK 完成（二轮），下一方向 JS/TS SDK；文档审查轮换推进：设计文档完成（二轮），下一方向架构文档。

### 2026-09-30 23:22 | 统一维护任务（Go SDK 二轮 + SDK README 二轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Go SDK（二轮，client_impl 内部） | `doRequest` 审查通过：context 快速失败、条件 Content-Type、Bearer 认证头、10MB 响应体上限（`MaxResponseBytes`）防 OOM；`doRequestJSON` 泛型 4xx/5xx → `APIError` 映射与 `extractErrorMessage` 多格式解析（object/array/plain string）完备；空查询参数自动省略。无问题、无修改 |
| 文档审查方向 | ✅ SDK README（二轮，安装指令核验） | 四语言安装声明与构建配置逐一对照：Go `go get` 路径 = go.mod module ✅；Python `pip install -e` 路径与 pyproject name `cortex-mem-python` ✅；JS `npm install @cortex-mem/js-sdk` = package.json name ✅；Java JitPack 坐标 `com.github.Blueforce-Tech-Inc:BlueCortexCE:Tag` 符合 JitPack 约定且 jitpack.yml 佐证 ✅。无问题、无修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ⏭ 本轮未重试 | 上一轮刚确认 403（凭据阻塞），避免连续无效重试；继续随轮低频重试 |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Go SDK 完成（二轮），下一方向 Python SDK；文档审查轮换推进：SDK README 完成（二轮），下一方向设计文档。

### 2026-09-30 23:18 | 统一维护任务（Java SDK 二轮 + API 文档）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Java SDK（二轮，深入 spring-ai/starter） | `CortexMemoryAdvisor` fail-open 设计正确（异常回退原请求）、会话 ID 双来源解析与 unknown-session 过滤完备；`CortexToolAspect` 截断上限 4000 与后端 `Constants.MAX_TOOL_CONTENT_LENGTH` 一致（已对照源码）；`CortexMemAutoConfiguration` 条件装配（`@ConditionalOnMissingBean`/`@ConditionalOnProperty`）规范。无问题、无修改 |
| 文档审查方向 | ✅ API 文档（响应格式实测） | 对运行中后端实测 3 项文档声明：分页响应 `{items, hasMore}`（bool）✅；`/api/search` 缺必填 `project` 返回 400 ✅；`/api/version` 返回 `{service, version, springBoot, java}` 且 `3.3.13` 与 pom 父版本一致 ✅。标题风格分歧（EN 描述性 H3 vs ZH 路径式 H4）维持低优先级 Pending 记录（纯样式，端点覆盖已核验一致） |
| 代码变化检测 | ✅ 无变化 | 指纹 `9964e2f5…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `24faf55` / `9964e2f5…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |
| push | ❌ 仍阻塞 | 本轮随轮重试仍 403（`wubuku` 无写权限）；origin/main 已被并行进程推进至 `f266997`，待推 `24faf55`+`dc1855d` |

**未解决问题**：push 权限阻塞（环境级）；API 文档标题风格分歧（低优先级 Pending）。

代码审查轮换推进：Java SDK 完成（二轮），下一方向 Go SDK；文档审查轮换推进：API 文档完成，下一方向 SDK README。

### 2026-09-30 23:10 | 统一维护任务（Backend + 用户指南）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Backend（抽查 SSEBroadcaster + StreamController + TimelineService） | 发现 `SSEBroadcaster.broadcast()` 仅捕获 `IOException`，而已完成的 emitter 的 `send()` 会抛 `IllegalStateException`，异常会中断整轮广播并跳过剩余客户端 —— 当场修复（补充捕获并按死连接移除）；TimelineService 无问题（有界窗口防 OOM）；StreamController 的 2 处直发无竞态窗口 |
| 构建与重启 | ✅ | `mvn clean compile package -DskipTests` BUILD SUCCESS；重启后 `/api/health` 正常；连续 3 轮检查通过（3 处 `send` 调用点逐一核查、4 个 broadcast 调用方全走统一入口、编译干净） |
| 文档审查方向 | ✅ 用户指南 | **DOCKER_README 中英漂移专项修复完成**：两文件统一为相同的 14 个 H2 结构且顺序一一对应（H3/H4 数量一致）。EN 补齐健康检查命令组（`/api/readiness`、`pg_isready`）与 3 个故障排查小节；ZH 删除与「使用 Dockerfile 构建」重叠的「构建本地镜像」节；测试覆盖以 `docker-e2e-test.sh` 实际验证项为准统一为 11 项清单（原 ZH 表格的 "MCP 服务测试" 声明在脚本中无对应）；更正前轮记录：EN `## Development` 即 ZH「本地开发」对应节 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `27a3c1b9…` → `9964e2f5…`（871 条），SSE 修复所致，必须执行完整验收 |
| 回归测试 | ✅ 45/46 | `bash scripts/regression-test.sh --skip-build`，0 失败、1 项按脚本跳过 |
| EXTRACTION 验收 | ✅ 25/25 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`，0 失败 0 跳过 |
| 验收基线 | ✅ 已更新 | `accepted_commit=24faf55536437b63f0a7b732b3f36ce3a2797ecf`，指纹 `9964e2f5…`，状态 `passed` |
| Backend findings | ✅ 0 未决 | 本次发现已当场修复并验收；无其他待处理项 |
| push | ❌ 仍阻塞 | `wubuku` 凭据对仓库无写权限（403），本地 main 领先 origin/main；已上轮报告用户，本轮重试一次仍失败，继续随轮重试 |

**未解决问题**：push 权限阻塞（环境级，需用户配置凭据）；API 文档中英标题风格分歧（低优先级 Pending，见 `doc-review-task.md`）。

代码审查轮换推进：Backend 完成（第二轮循环开始），下一方向 Java SDK；文档审查轮换推进：用户指南完成，下一方向 API 文档。

### 2026-09-30 09:26 | 统一维护任务（Demo + 架构文档）— 收尾轮（用户指示暂停循环）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Demo | Java Demo 无单元测试（surefire "No tests to run"，验证靠 E2E 脚本），`mvn test` BUILD SUCCESS；12 个 `@RestController` 精确清点并修正基准文档（10→12，漂移同前轮 120→121 性质）；Go 5 个 examples（basic/eino/genkit/http-server/langchaingo）`go vet` 全过（gofmt 已在 05:27 轮修复）；Python/JS Demo 测试分别随 374/212 套件在 05:32/05:37 轮覆盖 |
| 文档审查方向 | ✅ 架构文档 | `ARCHITECTURE.md` 与 `ARCHITECTURE-zh-CN.md` H2/H3 数量对等；外部提交 `d958416`（backend/README + 两份 ARCHITECTURE 各 1-2 行对齐）已确认为纯文档对齐且内容一致 |
| 代码变化检测 | ✅ 无变化 | 指纹 `27a3c1b9…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `b04ccf8` / `27a3c1b9…` 保持不变，未推进） |
| 可构建性确认 | ✅ | 后端 `mvn clean package -DskipTests` BUILD SUCCESS（`cortex-ce-0.1.0-beta.jar`，含 `static/` 内 WebUI 构建产物）；WebUI 嵌套仓库 HEAD `0e8eebe` 与超级项目记录一致、树干净、源码未被本轮修改；构建后已用新 jar 重启后端并确认 `/api/health` 恢复（旧进程日志文件被 `mvn clean` 删除，重启恢复日志路径） |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |

**轮次总结（05:03–09:26 共 5 轮）**：R1 Java SDK+用户指南（`8524384`）；R2 Go SDK+API 文档（gofmt 修复 `b04ccf8` + 完整验收通过、新基线；`29189db`）；R3 Python SDK+SDK README（`51da997`）；R4 JS/TS SDK+设计文档（`8c87d06`）；R5 Demo+架构文档（本轮）。代码审查轮换推进至 Backend，文档审查轮换推进至用户指南。未解决事项仅剩 `doc-review-task.md` 中 2 项文档级 Pending（DOCKER_README 中英漂移、API 文档标题风格），均不影响运行行为与代码指纹。

**用户已指示暂停循环**：本轮为收尾轮，完成后等待下一步指示。

### 2026-09-30 05:37 | 统一维护任务（JS/TS SDK + 设计文档）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ JS/TS SDK | 212/212 测试通过（与基准一致）；`tsc --noEmit` lint 干净；build 成功且 CJS（index.js）+ ESM（index.mjs）+ DTS（index.d.ts/.d.mts）齐全，`exports` 映射与 `files` 字段正确 |
| 代码审查发现 | ✅ 无 | 无代码缺陷，未做修改 |
| 文档审查方向 | ✅ 设计文档 | `phase-3-design/` 30 个文档全部 ≤50KB（规则合规）；`00-quick-ref.md` 的"10 项前置条件全部实现"声明及 15.md/21.md 链接为上轮修正后的准确内容；抽查 `mergeAppendOnly` 去重设计与 `StructuredExtractionService` 的 F-1 修复实现一致 |
| 文档审查发现 | ✅ 无 | 无事实性错误，未做修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `27a3c1b9…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `b04ccf8` / `27a3c1b9…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无 P0/P1/P2 待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |

**未解决问题**：无新增（既有 2 项 Pending 见 `doc-review-task.md`）。

代码审查轮换推进：JS/TS SDK 完成，下一方向 Demo；文档审查轮换推进：设计文档完成，下一方向架构文档。

### 2026-09-30 05:32 | 统一维护任务（Python SDK + SDK README）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Python SDK | 374/374 测试通过（`python3.11 -m pytest tests/ -q`；pyenv 默认 python3 无 pytest，按 SDK 实际可用解释器执行）；Client 25 个 API 方法、ObservationUpdate 双模式（dataclass + kwargs，content/narrative 互斥校验与 Java 行为对齐）；`__pycache__`/`*.pyc`/`egg-info` 均已被 gitignore，无产物误提交 |
| 代码审查发现 | ✅ 无 | 无代码缺陷，未做修改 |
| 文档审查方向 | ✅ SDK README | 4 个 SDK（Java 578/578 行、Go 211/211、Python 165/165、JS 205/205）中英 README 行数完全一致、顶部互链齐全；方法文档对照源码核验：Python 25 行、Java 25 行、JS 25 API + `close()`/`toString()`、Go "25 methods" 且测试计数（255=194+61 + 集成 33）与 R2 实测一致 |
| 文档审查发现 | ✅ 无 | 无事实性错误，未做修改 |
| 代码变化检测 | ✅ 无变化 | 指纹 `27a3c1b9…`（871 条）与基线完全一致 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `b04ccf8` / `27a3c1b9…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无 P0/P1/P2 待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改 |

**未解决问题**：无新增（既有 2 项 Pending 见 `doc-review-task.md`）。

代码审查轮换推进：Python SDK 完成，下一方向 JS/TS SDK；文档审查轮换推进：SDK README 完成，下一方向设计文档。

### 2026-09-30 05:27 | 统一维护任务（Go SDK + API 文档）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Go SDK | 5 个 Go 模块（root/dto/genkit/langchaingo/eino）`go test -count=1` 全过：255 + 13 + 12 + 8 = 288，与基准一致；Client 接口 25 个 API 方法（+`Close`/`String` 生命周期方法）与基准一致 |
| 代码审查发现 | ✅ 当场修复 | 4 个文件不符合 gofmt（注释/字段标签对齐漂移）：`dto/observation.go`、`dto/observations.go`、`dto/session.go`、`genkit/retriever.go`；`gofmt -w` 修复，纯格式无语义变化 |
| SDK E2E | ✅ 39/39 | Go Demo 按 patrol-task 规程在专用端口 `37779` 启动，`scripts/go-sdk-e2e-test.sh` 全过，测试后仅停止本轮启动的 Demo |
| 连续 3 轮修改后检查 | ✅ 通过 | 三轮均无新问题：全仓 gofmt 终扫（含 examples 子模块）clean、`go vet` clean、`git diff --check` clean |
| 文档审查方向 | ✅ API 文档 | 中英端点集合完全对应（59 个路径逐一比对；ZH 多出的 2 个为示例 curl 中 `{templateName}` 实例化，非端点差异）；对照 12 个 Controller `@RequestMapping` 基准无幽灵端点；H2 数量对等 |
| 文档审查发现 | ℹ️ 已记录 | 中英标题风格分歧（EN 描述性 H3 vs ZH 路径式 H4），纯样式不影响准确性，记录到 `doc-review-task.md` Pending 区 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `97792e76…` → `27a3c1b9…`（871 条），gofmt 修复所致，必须执行完整验收 |
| 回归测试 | ✅ 45/46 | `bash scripts/regression-test.sh --skip-build`，0 失败、1 项按脚本跳过（异步摘要警告，符合预期） |
| EXTRACTION 验收 | ✅ 25/25 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`，0 失败 0 跳过 |
| 验收基线 | ✅ 已更新 | `accepted_commit=b04ccf80625b87c818f7f177409a643ea2dbdc56`，指纹 `27a3c1b9…`，状态 `passed` |
| Backend findings | ✅ 0 未决 | 无 P0/P1/P2 待处理项 |

**未解决问题**：无新增；既有 Pending（DOCKER_README 漂移、API 文档标题风格）均不影响运行行为。

代码审查轮换推进：Go SDK 完成，下一方向 Python SDK；文档审查轮换推进：API 文档完成，下一方向 SDK README。

### 2026-09-30 05:03 | 统一维护任务（Java SDK + 用户指南）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ⚠️→✅ 恢复 | 首检 `status=ok`；轮中途进程消失（连接拒绝、无监听），按规程 `bash scripts/start.sh --background` 恢复成功，后续检查均 `status=ok`（专用端口 `37777`） |
| 文档增长检查 | ✅ OK | 开始与结束均执行 `bash scripts/doc-growth-check.sh`，5 个活动文档低于 1000 行/102400 字节 |
| 代码审查方向 | ✅ Java SDK | 3 模块全部通过：client 121 测试（DtoTest 34 + CortexMemClientImplTest 87）、spring-ai 46、starter 7，BUILD SUCCESS；wire format 抽查（`project`/`narrative`/`extractedData`、`/api/search` 参数、`ToolUseRequest` 字段）与后端 `@JsonProperty` 实际定义逐一对照一致；25 个接口方法数与基准一致 |
| 代码审查发现 | ✅ 已落地 | 无代码缺陷；唯一发现为 `patrol-task.md` 基准文档漂移（记录 120 tests/86，实测 121/87），已按实测修正 |
| 文档审查方向 | ✅ 用户指南 | 8 份指南（DEPLOYMENT/TESTING/DEVELOPMENT/DOCKER_README 中英）双语互链齐全；DEPLOYMENT/TESTING/DEVELOPMENT 中英 H2/H3 数量对等；DOCKER_README 存在中英结构漂移（ZH 多 3 个 H2），已记录到 `doc-review-task.md` Pending 区，待专项修复 |
| 文档审查修复 | ✅ 实测验证 | `DEPLOYMENT.md`/`DEPLOYMENT-zh-CN.md` 附录 C 修正 3 处错误：`/api/ingest/session-start`（不存在的端点）→ `/api/session/start`；请求体字段 camelCase + 不存在的 `observation` 字段 → 实际的 snake_case 契约；search 示例补必填 `project` 参数。修复后 5 个示例对运行中后端逐一实测通过（health/session-start/observation/search/stats），测试观察数据已删除 |
| 代码变化检测 | ✅ 无变化 | 指纹 `97792e76…`（871 条记录）与基线完全一致；本轮改动全部为文档，不在指纹范围 |
| 完整验收 | ⏭ 跳过 | 代码指纹未变化，跳过重复测试验收（基线 `fd1f969` / `97792e76…` 保持不变，未推进） |
| Backend findings | ✅ 0 未决 | 无 P0/P1/P2 待处理项 |
| 连续 3 轮检查 | ✅ 不适用 | 本轮无代码修改，仅有文档修改（已实测验证） |
| 环境事件 | ℹ️ 记录 | 会话期间出现外部提交 `d958416`（3 个架构文档，非本轮操作，文档改动不影响指纹）；一次后端进程消失并成功恢复 |

**未解决问题**：DOCKER_README 中英文结构漂移（已记录待专项轮处理）；后端进程中途消失的根因未查明（本轮按规程恢复成功，若再现需排查 OOM/外部 kill）。

代码审查轮换推进：Java SDK 完成，下一方向 Go SDK；文档审查轮换推进：用户指南完成，下一方向 API 文档。

### 2026-09-30 04:23 | 统一维护任务（Demo + 设计文档）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | Backend `127.0.0.1:37777/api/health` 返回 `status=ok`，数据库和消息队列正常；无需重启 |
| 文档增长检查 | ✅ OK | 开始和结束均执行 `bash scripts/doc-growth-check.sh`；所有活动文档低于 1000 行/102400 字节，无需归档 |
| 代码审查方向 | ✅ Demo | 发现 Go `eino`、`genkit`、`langchaingo` 示例缺少实际包装模块 `require`；已在三个 `go.mod` 补齐本地依赖 |
| Demo 快速验证 | ✅ 通过 | Java Demo Maven 测试通过；Go 五个 Demo `go test`/`go vet` 通过；Python 374 项测试和语法检查通过；JS/TS 212 项测试、lint、build 通过 |
| Demo E2E | ✅ 39/39 | Go HTTP Demo 在专用端口 `37779` 启动，完整 SDK 链路 E2E 通过；测试后仅停止本轮启动的进程 |
| 连续 3 轮修改后检查 | ✅ 通过 | 三轮均无新问题或新增修改 |
| 文档审查方向 | ✅ 设计文档 | 对照实现、测试脚本和拆分目录检查；修正 `00-quick-ref.md` 过时的“4 个前置方法”为“10 项前置条件全部实现”，并链接 `15.md`/`21.md` |
| 代码变化检测 | ✅ 检测到变化 | 基线指纹 `8e1e24…` → 当前 `97792e…`，记录数均为 871；因此必须执行完整验收 |
| 回归测试 | ✅ 45/46 | `bash scripts/regression-test.sh --skip-build`，0 失败、1 项按脚本跳过 |
| EXTRACTION 验收 | ✅ 25/25 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` 全部通过，含用户隔离、历史保留、追加/移除和输入校验 |
| Backend findings | ✅ 0 未处理 | 当前无 P0/P1/P2 未处理项；既有 Demo 响应差异已明确标记为跳过 |
| 验收基线 | ✅ 已更新 | `accepted_commit=fd1f96912480f817026e55dfe3b8b6f6916dad74`，指纹版本 1，状态 `passed` |
| 未解决问题 | ✅ 无 | 本轮发现均已修复并验证；无环境阻塞 |

代码与设计修复提交：`fd1f969`（`fix: make Go demo module dependencies explicit`）。本轮未发送外部通知，报告仅保存在本文件。

> 历史条目中的“发送消息到 ...”是旧任务的点状记录，不属于当前执行要求。当前任务只把报告保存到代码库中的本文件；新增报告不得发送外部通知。

### 2026-04-23 19:08 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{'service':'claude-mem-java','status':'ok','timestamp':1776942575639}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 2 个遗留 P2 为设计观察（#51-1 dead code、#51-2 null project），非 bug |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-23 14:21 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 2 个遗留 P2 为设计观察（#51-1 dead code、#51-2 null project），非 bug |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-23 11:55 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | 服务未运行，已重启。`{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 4 个遗留 P2(低/极低) 已全部标记确认/跳过 |

**Backend Review #51**（2026-04-23 11:55）— 遗留 P2 问题确认：

| # | 文件 | 问题 | 处理 |
|---|------|------|------|
| 1 | ExtractionStorageService.java | 并发 session 创建理论重复 | ✅已确认（@Transactional 保证原子性） |
| 2 | API.md | TestController 未文档化 | ✅已跳过（仅 dev/test 可用） |
| 3 | MemoryController.java | PATCH UUID 格式返回 400 | ✅已确认（Spring 验证行为正确） |
| 4 | MemoryController.java | POST feedback UUID 返回 400 | ✅已确认（已有明确错误信息） |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-12 19:33 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-12 18:50 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-12 17:26 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-12 12:15 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-11 17:42 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-11 16:19 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-11 14:21 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-10 03:11 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-09 12:20 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-09 05:15 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-09 04:22 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-08 11:22 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-06 23:04 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-06 20:12 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

**Backend Review #45**（2026-04-09 21:14）— 健康检查 + Backend Review：

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

**Backend Review #44**（2026-04-07 07:08）— Backend 审查问题批量修复：

全部通过，无待处理问题（0 P0/0 P1/0 P2）。

### 2026-04-16 08:06 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

**Backend Review #43**（2026-04-06 20:12）— ImportService + TokenService + ImportController 抽样审查：

**2026-04-16 18:46 | 健康检查**

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | #49-2（ContextCacheService 无限重试循环）已修复 |

**Backend Review #50**（2026-04-16 18:46）— ContextCacheService refreshStaleContexts() 无限重试循环修复：
- 问题：refresh 失败时仅 log error，`needsContextRefresh` 保持 true，导致下一轮 scheduled run 立即再次尝试，形成无限循环
- 修复：失败时设置 `needsContextRefresh=false` + `contextRefreshedAtEpoch=now`，防止无限重试；session 在新 observation 到来时自然重新触发 refresh

**2026-04-17 03:17 | 健康检查**

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

**2026-04-17 05:07 | 健康检查**

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-23 13:10 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 2 个遗留 P2 为设计观察（#51-1 dead code、#51-2 null project），非 bug |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-23 17:37 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 2 个遗留 P2 为设计观察（#51-1 dead code、#51-2 null project），非 bug |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-25 00:22 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | 服务未运行（PostgreSQL 17 已重启），已启动。`{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped: Test 9b 异步摘要警告，符合预期） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 2 个遗留 P2 为设计观察（#51-1 dead code、#51-2 null project），非 bug |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-23 18:12 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok","timestamp":1776939210808}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 2 个遗留 P2 为设计观察（#51-1 dead code、#51-2 null project），非 bug |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-04-25 08:14 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok","timestamp":1777076052932}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 2 个遗留 P2 为设计观察（#51-1 dead code、#51-2 null project），非 bug |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-05-03 12:29 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | PostgreSQL 14 (x86_64) 损坏，切换到 OrbStack PostgreSQL (port 5432, pgvector 0.8.2, claude_mem_dev)。`{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 45/46 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

**DB 迁移说明**：PostgreSQL 14 (x86_64) 因 `pgvector` 扩展不兼容（ARM64 bottle vs x86_64 server）损坏，切换到 OrbStack Docker PostgreSQL (port 5432)。`claude_mem_dev` 数据库包含完整 Flyway schema（18 个 migrations）。

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-05-06 05:08 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok","timestamp":1778015345814}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped: Test 9b 异步摘要警告，符合预期） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | Backend Review #20 P1+P2 已修复（F-1 Fix: mergeAppendOnly 去重修复；F-2 Fix: storeDLQ 异常传播） |

**Backend Review #20 修复**（2026-05-06 05:16）：
- **P1**: `mergeAppendOnly` — 添加 `existingKeys.add()` 防止同一 `applicableAddItems` 列表内重复项重复添加
- **P2**: `storeDLQ` — 移除内部 try-catch，DLQ 失败时异常传播并抛出 RuntimeException（避免无限递归）

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-05-04 01:43 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | 服务未运行，已重启。`{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped: Test 9b 异步摘要警告，符合预期） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh（EXTRACTION_ENABLED=true） |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

### 2026-09-30 01:51 | 统一维护任务端到端执行

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 服务健康 | ✅ OK | `/api/health`；专用端口 `37777`。`scripts/start.sh --background` 等待就绪成功 |
| 回归测试 | ✅ 45/46 | `bash scripts/regression-test.sh --skip-build`；0 failed，1 skipped（摘要生成异步警告） |
| EXTRACTION 验收 | ✅ 25/25 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`；0 failed、0 skipped |
| 代码审查 | ✅ Python SDK | 审查无新增问题；遗留 Java Demo 文案差异 P2-1 已明确标记为设计性跳过 |
| 文档审查 | ✅ API 文档 | 对照 Controller mappings 检查路径、HTTP 方法、基础 URL；修正中英文 H2 顺序差异及中文目录顺序，互链验证通过 |
| Backend Findings | ✅ 0 未决 | 当前 P0/P1/P2 未决数量为 0；P2-1 有明确跳过理由 |
| 文档增长 | ✅ OK | 5 个持续追加文档均低于 1000 行 / 102400 字节阈值 |
| 连续检查 | ✅ 3 轮 | 最终代码修改后连续 3 轮未发现新问题、未再修改 |
| 指纹基线 | ✅ passed | v1 `5f22498429021f4e2bacaebe84ee832c1af015707221f6b76f268439cc4b3cc3` |

**本轮代码修复与验证**：
- 回归脚本等待统一健康端点 `/api/health`，并兼容 Actuator 回退。
- `start.sh` 固定使用 `37777`，后台启动最多等待 60 秒并检查进程及健康端点。
- `start-all.sh` 改用正确 JAR `cortex-ce-0.1.0-beta.jar`、固定 `37777`，并以 `/api/health` 检查就绪。冷启动约 36 秒，首次发现其 30 秒等待超时后调整为 60 秒；备用启动验证成功。
- 启动与验收放在同一周期进程环境内运行；独立命令结束后当前执行器会清理后台子进程，因此不能把后台进程跨命令会话存活当作验证前提。
- 完整验收最终通过；无 Backend 代码问题需要批量修复。Shell 语法、`git diff --check`、增长检查和三轮复核通过；`shellcheck` 未安装。

**文档/状态更新**：轮换状态已更新为 Python SDK 完成、下一方向 JS/TS SDK；文档审查轮换更新为 API 文档完成、下一方向 SDK README。详细实现和验收结果记于 `docs/drafts/backend-fix-progress.md`。

API 文档复核补充：发现中文文档原先把 Health、错误码等章节提前，与英文版章节顺序不同；已按英文版重排中文目录和章节，并补齐 Observations 交叉引用标题。自动检查确认 21 个既有章节正文未变、顺序对应一致，两版互链有效；这是文档范围变更，不改变代码指纹或已通过的验收结果。

**提交**：`cf022c7`（统一维护工具与运行流程）；`e5b9429`（备用启动冷启动等待修正）；`4e01af6`（API 文档中英文结构对齐）。最终验收基线仍指向通过完整测试的代码提交 `e5b94299de05edcd1647b4d31506422bb6642adf`。

### 2026-09-30 03:45 | 统一维护任务端到端执行（本轮最终结果）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `http://127.0.0.1:37777/api/health` 返回 `status=ok`；后端已在专用端口运行，本轮无需重启 |
| 服务启动与端口 | ✅ OK | 按 `patrol-task.md` 启动并验证 Java `37778`、Go `37779`、Python `37780`、JS/TS `37781`；E2E 完成后全部停止，仅保留后端 `37777` |
| 代码审查 | ✅ JS/TS SDK | SDK README 双语语义、Demo 端口和 E2E 严格状态码检查完成；无新增未落地问题 |
| 文档审查 | ✅ SDK README | 英文/中文 README 互链和 API 表一致；修正 best-effort 捕获语义、补充 `toString()`，同步 Demo README 端口和 Python `python3` 启动命令 |
| 文档增长 | ✅ OK | 任务开始和结束检查均低于 1000 行 / 102400 字节，无需压缩或归档 |
| 代码变更检测 | ✅ 检测到变更 | 指纹由旧基线 `5f224984...` 变为 `8e1e24b00a2d73082417314d0f1a0e5cdb6a639f89e61d57542bc068a2ee75d5`，因此执行完整验收 |
| 回归测试 | ✅ 45/46 | `bash scripts/regression-test.sh --skip-build`；45 passed、0 failed、1 skipped（异步摘要未及时出现，符合既有预期） |
| EXTRACTION 验收 | ✅ 25/25 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`；0 failed、0 skipped |
| Backend Findings | ✅ 0 未决 | `backend-review-findings.md` 中没有待修复 P0/P1/P2，本轮无需批量修复 |
| 连续 3 轮复核 | ✅ 3 轮 | 格式/语法与关键错误映射、组件单测/构建、全量 SDK/Demo E2E 与端口复核均无新增问题 |

**本轮发现与修复**：

1. Go Demo 对合法但不存在的 observation UUID 将 Backend `404` 错误错误映射为 `500`。已在 `go-sdk/cortex-mem-go/examples/http-server/main.go` 将 `ErrNotFound` 映射为 `404`、参数错误映射为 `400`，并将 E2E 从“任意 HTTP 响应即通过”收紧为预期状态码。
2. Python SDK E2E 在未安装 editable package 的环境中无法导入 checkout，且 `requests` 依赖警告污染严格输出解析。已在 `scripts/python-sdk-e2e-test.sh` 固定 `PYTHONPATH` 到仓库 SDK、隔离第三方告警，并统一使用 `python3 -m pytest` 方法；Python Demo 启动指引统一为 `python3`。
3. 所有巡检 Demo 已统一使用专用端口：Backend `37777`、Java `37778`、Go `37779`、Python `37780`、JS/TS `37781`，不再使用 `8080`。
4. Java Demo 对更新/删除不存在 observation 的 Backend `404` 现在返回 `404`，不再误报 `500`；Java、Go、Python、JS/TS E2E 均改用合法但不存在的 UUID 并严格验证错误状态码。

**组件与 E2E 结果**：Go `go test ./...`、`go vet ./...` 通过；Python `374/374` 单测通过；JS/TS `212/212`、lint、build 通过；Java Demo Maven 测试通过；Java Demo E2E `28 passed, 0 failed, 1 skipped`（无 OpenAI API key 的 chat 按指引跳过）；Go E2E `39/39`；Python Demo E2E `27/27`；JS Demo E2E `27/27`；Python SDK E2E `28/28`；JS/TS SDK E2E `27/27`。`git diff --check`、所有 shell 脚本语法检查和端口复核通过。

**提交与基线**：代码及测试工具提交为 `88958845c9d1e46e7a8c72de315afde67f2cbce3`。完整回归和 EXTRACTION 验收均成功后，顶部唯一基线已更新为该提交及指纹 `8e1e24b00a2d73082417314d0f1a0e5cdb6a639f89e61d57542bc068a2ee75d5`。本报告和轮换状态更新只影响文档，不改变代码指纹。

**未解决事项**：无。未配置 OpenAI-compatible API key 导致 Java Demo `/chat` 场景按既定规则跳过，不构成验收失败。

**最终基线复核（03:52）**：统一 `python3` 提示后的最终代码指纹仍为 `8e1e24b00a2d73082417314d0f1a0e5cdb6a639f89e61d57542bc068a2ee75d5`；回归测试再次为 45 passed、0 failed、1 skipped，EXTRACTION 再次为 25 passed、0 failed、0 skipped。基线保持 `accepted_commit: 88958845c9d1e46e7a8c72de315afde67f2cbce3`，HEAD 的 `23216a1` 为文档状态提交，不改变代码指纹。

### 2026-09-30 04:32 | 统一维护任务端到端执行（Backend + 架构文档）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `curl http://127.0.0.1:37777/api/health` 返回 HTTP 200、`status=ok`；服务已运行，本轮无需启动或重启 |
| 文档增长（开始） | ✅ OK | `bash scripts/doc-growth-check.sh`；活动文档均低于 1000 行 / 102400 字节 |
| 代码审查 | ✅ 无新增代码问题 | Backend 轮换方向；抽查 `ViewerController`、`StructuredExtractionService` 及 WebUI 契约相关路径，未发现需修复的代码问题 |
| 文档审查 | ✅ 已修复 | 架构文档轮换方向；`backend/README.md` 移除误列在 Viewer API 下的两个 `/api/mode` 端点；中英文架构文档将 ViewerController 方法数从 16 更正为实际启用的 15，并保持两版同步 |
| Backend Findings | ✅ 0 未决 | P0/P1/P2 均为 0；现有 P2-1 继续保持有明确理由的“已跳过”状态 |
| 代码变更检测 | ✅ 未变化 | 指纹脚本输出：v1、871 条记录、`97792e768dcd5cce1899ac4674133b44919e525159cbd165c5f16d05920fb10f`，与顶部通过基线一致；当前 HEAD `d958416` 仅含文档变更 |
| 完整回归测试 | ⏭️ 跳过 | 代码指纹未变化，按跨多轮规则跳过重复 `regression-test.sh --skip-build` |
| EXTRACTION 验收 | ⏭️ 跳过 | 代码指纹未变化，按跨多轮规则跳过重复 `EXTRACTION_ENABLED=true` 验收 |
| 构建与重启 | ⏭️ 不需要 | 本轮没有代码或运行配置修改；保留后端专用端口 `37777` |
| 修改后连续 3 轮检查 | ✅ 适用规则已执行 | 本轮未修改代码，未触发代码修改后的三轮重置要求；文档修正经端点映射、方法计数和中英文一致性三轮复核，无新增问题 |
| 文档增长（结束） | ✅ OK | 结束检查仍低于阈值，无需压缩或归档 |
| 提交 | ✅ `d958416` | `docs: align architecture and patrol state`；不改变代码验收基线 |

**本轮结论**：工具、指纹机制、端口约束、服务启动指引、文档压缩规则和跨多轮跳过逻辑均可按指引执行完成。发现的两处架构文档偏差已修复并提交；所有测试通过的既有代码基线保持有效，本轮无未解决事项。
