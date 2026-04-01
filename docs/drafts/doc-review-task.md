> **用途**: 文档审查与改进任务指令（30分钟 cron 任务专用）
> **维护者**: PM Agent
> **更新频率**: 审查范围或规则变更时更新

# 文档审查与改进 — 任务指令

## 执行规则

- **每次唤醒审查一个方向**（轮换：API 文档 → SDK README → 设计文档 → 架构文档 → 用户指南）
- **总时长 ≤ 15 分钟**
- **发现问题直接修复**（文档修复 + commit）
- **⚠️ 审查过程中发现的代码问题同样必须处理——要么当场修复，要么记录到 `backend-review-findings.md`，不允许只发消息了事**

## 质量标准

### 1. 双语对照

- 重要文档必须有英文版和中文版
- 中文版文件名使用 `-zh-CN` 后缀（如 `API.md` → `API-zh-CN.md`）
- 两个版本**内容必须一致**——不能英文有而中文没有，反之亦然
- 同一文档的中英文版本应在文件顶部互相链接：
  ```markdown
  > English version: [API.md](./API.md)
  > 中文版: [API-zh-CN.md](./API-zh-CN.md)
  ```

### 2. 准确性（最重要）

- **宁可不说，不可说错**——没有把握的内容不要写
- 重要信息（端点路径、参数名、返回格式）必须**通过分析代码实现来验证**
- API 文档的端点路径、HTTP 方法、参数名必须与 Controller 代码一致
- SDK 文档的方法签名、参数类型必须与源码一致
- 版本号、依赖要求必须与 `pom.xml` / `package.json` / `go.mod` 等实际配置一致

### 3. 完整性

- API 文档应覆盖所有 Controller 端点（对照 Controller 源码检查）
- SDK README 应覆盖所有公共 API 方法
- 设计文档应与实现一致（Phase 3 设计 vs 实际 Service/Entity）
- 缺失的端点/方法必须补充

### 4. 一致性

- 同一概念在不同文档中使用相同术语
- 中英文版本使用对等的结构和章节顺序
- 示例代码应可运行（不编造参数或端点）

### 5. 开源项目文档最佳实践

- README 应包含：项目简介、快速开始、API 概览、贡献指南链接
- API 文档应有清晰的分组（按功能域）
- 每个端点应有：方法+路径、参数说明、请求/响应示例
- 变更日志（CHANGELOG）应反映实际版本变更
- 链接不应失效（检查相对路径和锚点）

## 每次唤醒执行步骤

1. **检查服务状态** — `curl -s http://127.0.0.1:37777/api/health`（~5秒）
2. **选择本次审查方向**（轮换）
3. **审查**（~10分钟）：
   - 对照代码实现验证文档准确性
   - 检查中英文版本一致性
   - 修复发现的问题（文档问题直接修，代码问题修或记录到 findings.md）
   - **⚠️ 每个发现的问题必须有落点，不允许只发消息不行动**
4. **git commit**（如有修复）
5. **飞书汇报** — 发送到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

## 审查范围

### 1. API 文档
- `docs/API.md`（英文）vs `docs/API-zh-CN.md`（中文）
- 对照 Controller 源码验证端点覆盖
- 对照 Swagger 注解验证参数描述

### 2. SDK README
- `java-sdk/README.md`、`go-sdk/README.md`、`python-sdk/README.md`、`js-sdk/README.md`
- 方法签名与源码一致
- 示例代码可运行

### 3. 设计文档
- `docs/drafts/phase-3-design.md` vs 实际实现
- `docs/drafts/phase-3-design-walkthrough.md` 场景覆盖

### 4. 架构文档
- `docs/ARCHITECTURE.md`（如有）
- 项目根目录 `README.md`

### 5. 运维文档
- 部署指南、配置说明、故障排查
- Docker / docker-compose 相关文档

## 汇报格式

```
📋 文档审查报告 — [日期]
🔍 本次审查方向：[方向]
📝 发现问题：[数量]
[问题列表 + 修复状态]
```

---
## Last Review Log
- **2026-03-31 04:26**: API 文档 (API.md vs API-zh-CN.md) — 修复 3 问题，commit 0e19580
- **2026-03-31 04:54**: SDK README (Go/JS/Python) — 全部 25 个方法覆盖完整，端点与 Controller 源码一致，中英文版本结构/内容一致，跨链接完整。无修复。
- **2026-03-31 05:13**: 设计文档 (structured-extraction.md + walkthrough) — 修复 2 问题，commit 8c45107
- **2026-03-31 05:45**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md) — 修复 6 问题，commit 4e5a089
- **2026-03-31 06:00**: 运维文档 (DOCKER_README + docker-compose + TESTING + CONTRIBUTING) — 修复 9 问题
- **2026-03-31 06:38**: API 文档 (API.md 新一轮) — 修复 1 问题（Viewer/Management/Mode/Health/Cursor/Logs 章节参数表和响应示例缺失），commit 3e04e6c
- **2026-03-31 07:00**: SDK README (Go/Python/JS 新一轮) — 修复 1 问题（Go README 测试数 278→268），commit 94770bb
- **2026-03-31 07:33**: 设计文档 (structured-extraction.md + walkthrough) — 修复 1 问题（walkthrough 场景4/5/决策4/评估表引用旧版 "LLM re-extraction"，实际实现为 append-only extraction），commit pending
- **2026-03-31 07:54**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md) — 修复 1 问题（backend/README.md 项目结构缺少 6 个包：common/dto/event/exception/logging/mcp），commit f554276
- **2026-03-31 08:20**: 运维文档 (DEPLOYMENT.md + DOCKER_README.md + CONTRIBUTING.md + TESTING.md) — 修复 2 问题（DEPLOYMENT.md 环境变量名错误/不存在变量/docker语法过旧/git URL错误/docker run示例错误；DOCKER_README.md 镜像路径与docker-compose.yml不一致），commit efba5a1
- **2026-03-31 08:42**: API 文档 (API.md + API-zh-CN.md) — 修复 2 问题（英文 Session Start 缺失响应示例；中英文 Delete Observation 响应错误 204→200 OK with body），commit 1fd42d3
- **2026-03-31 09:54**: SDK README (Go/Python/JS 新一轮) — 无修复。验证项：25 方法数（源码确认）、268 测试数（go test 确认）、端点路径与 Controller 源码一致、中英文版本结构/内容/跨链接一致、依赖声明（Go 零依赖 / Python 仅 requests / JS 零依赖）准确。
- **2026-03-31 10:54**: 设计文档 (phase-3-design.md + walkthrough 新一轮) — 修复 2 问题（Section 25 实施进度仪表盘 Steps 6-11 显示 "🔧 Pending" 但代码已实现；更新实施顺序摘要和新文件/修改文件表）; 发现 1 待修复：MemoryController ICL/experiences 端点未接入 userId（DTO 已有字段但 Controller 未使用）
- **2026-03-31 12:27**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 3 问题：(1) Memory Refine 端点文档错误——英文/中文均写为 JSON body `{"project_path":"..."}`，实际代码为 `@RequestParam String project` 查询参数，修正为 `POST /api/memory/refine?project=...`；(2) Feedback 端点字段名错误——英文/中文均写为 `session_id`/`feedback_type`，实际 DTO 为 `observationId`/`feedbackType`，添加字段说明表和响应示例；(3) Experiences 和 ICL Prompt 缺少 `userId` 字段（Phase 3 多用户隔离），补充字段说明。中英文版本同步更新。
- **2026-03-31 13:14**: SDK README (Go/Python/JS 新一轮) — 修复 2 问题：(1) Go/JS SDK README 缺少设计文档交叉链接（Python 有但 Go/JS 无）；(2) JS SDK 缺少 Wire Format 章节。中英文版本同步更新。commit 2224140
- **2026-03-31 13:32**: 设计文档 (phase-3-design.md 新一轮) — 修复 1 问题：Steps 6-11 章节标题仍标记 "🔧 NEXT/Pending" 但代码已全部实现，统一改为 "✅ COMPLETED"；Step 8 进度表 "⚠️ Partial→✅ Done"（MemoryController userId 已接入）；append-only 设计状态 "⚠️ Open→✅ Implemented"；"Remaining work" 更新为 "None"。commit 683a9c1
- **2026-03-31 13:40**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md 新一轮) — 修复 3 问题：(1) 两个语言版本 Controller 路径表不完整——ViewerController 仅列 `/api/observations, /api/search`，实际还处理 summaries/prompts/projects/stats/settings/modes/timeline/processing-status/sdk-sessions；HealthController 仅列 `/api/health`，遗漏 `/api/readiness` 和 `/api/version`；已扩展为完整路径；(2) 服务架构图遗漏 `ExtractionStorageService`（transactional helper，已在 service/ 目录）；Event 类 `MemoryRefineEvent` + listener/publisher 未列出；`XmlParser` 位置标注为 service/ 实际在 util/；`ClaudeMemMcpTools` 位置标注为 service/ 实际在 mcp/；已修正包位置标注并增加 event 类分组；(3) backend/README.md 项目结构遗漏 `mcp/ClaudeMemMcpTools.java`、`util/VectorValidator.java`、`util/SessionStatus.java`、`service/ExtractionStorageService.java`；已补充。中英文版本同步更新。
- **2026-03-31 14:23**: 运维文档 (新一轮轮换) — 修复 6 问题：(1) .env.docker 变量名与 docker-compose.yml 不匹配（OPENAI_API_KEY→SPRING_AI_OPENAI_API_KEY 等，7 个变量）；(2) DOCKER_README.md 环境变量表同样错误且列出不存在的 DB_HOST/DB_PORT；(3) DEPLOYMENT.md (英文) 环境变量表同样错误；(4) DEPLOYMENT-zh-CN.md Flyway 迁移表仅 V1-V8，补充 V11-V16；(5) DEPLOYMENT-zh-CN.md Dockerfile 路径 `java/Dockerfile`→`Dockerfile`，克隆目录 `claude-mem-java`→`BlueCortexCE`；(6) CONTRIBUTING.md 项目结构缺少 common/event/logging/mcp 包。commit 3eaf2b2
- **2026-03-31 15:01**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 3 问题：(1) Chinese doc 缺少 Overview 章节（Base URL + Content-Type 说明），已补充；(2) TestController 3 个端点（/api/test/llm, /api/test/embedding, /api/test/all）未在任何 API 文档中记录，已添加 Test Endpoints 章节到中英文两版；(3) Chinese doc HTTP 状态码表缺少 401 Unauthorized 和 403 Forbidden，已补充。中英文版本同步更新，TOC 同步。commit 6383dba
- **2026-03-31 16:11**: SDK README (Go/Python/JS 新一轮) — 修复 1 问题（Go README Quick Start 缺失 SessionID 字段，服务端要求必填但示例仅提供 ProjectPath，中英文两版同步修复），commit e109771
- **2026-03-31 17:21**: 设计文档 (phase-3-design.md + walkthrough 新一轮) — 修复 1 问题（Section 15.1/15.10/19.2 多处过时的"未实现"声明——全部 8 个前置条件已实现但文档仍标记为 pending/unchecked；changelog v14/v11 未标注后续已实现），commit 0686a2c
- **2026-03-31 18:47**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md 新一轮) — 修复 1 问题（backend/README.md 项目结构中 mcp/ 目录条目重复出现两次），commit 8b70d2e。其他验证项：Controller 列表中英文版本一致（13 个）、Service 列表一致、Flyway 迁移列表一致、Schema SQL 与实际迁移一致、Spring Boot 版本 3.3.13 正确、跨链接完整。
- **2026-03-31 19:30**: 运维文档 (DOCKER_README + .env.example + CONTRIBUTING + DEPLOYMENT) — 修复 3 问题：(1) DOCKER_README.md 5 处错误引用旧名（cortexce 服务名→claude-mem、cortexce 数据库名→claude_mem、cortexce-logs 卷名→claude-mem-logs）；(2) .env.example IMAGE_NAME 路径过时（wubuku/claude-mem-java→blueforce-tech-inc/bluecortexce/cortex-ce）；(3) CONTRIBUTING.md .env 示例包含不存在的 DB_URL 变量（应用使用 SPRING_DATASOURCE_URL）和错误端口 5433。commit 128adb5
- **2026-03-31 19:53**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 3 问题：(1) English Get Session 无响应示例/路径参数/错误响应（中文版有），已补充完整文档；(2) English/Chinese PATCH session user 响应示例仅 `{"status":"ok"}`，实际 DTO 返回 3 字段（status/sessionId/userId），已修正；(3) 环境变量表使用旧名（DB_URL→SPRING_DATASOURCE_URL，OPENAI_API_KEY→SPRING_AI_OPENAI_API_KEY 等），默认值错误（gpt-4o→deepseek-chat，api.openai.com→api.deepseek.com），缺少 embedding base URL 和 Anthropic 变量，已全面修正并添加 fallback 说明。中英文版本同步更新，commit 93dbbfa
- **2026-03-31 22:25**: SDK README (Go/Python/JS 新一轮) — 修复 1 问题（Go README 测试数 268→245，中英文两版同步更新），commit 7da3c06
- **2026-04-01 00:01**: 设计文档 (phase-3-design.md + walkthrough 新一轮) — 修复 5 问题：(1) Section 24 标题未标注 24.1-24.5 已被 append-only 方案替代，添加 "⚠️ SUPERSEDED BY SECTION 24.6" 标注；(2) Section 8 Open Questions #9-10 仍引用旧版 Section 24.1/24.2 作为答案（summarizePriorExtraction / hallucination mitigation），更新为引用 Section 24.6（append-only 方案使这两个问题完全消失）；(3) Section 3 SUPERSEDED 标注和 Section 20.6 仍说 "LLM re-extraction" 处理冲突检测，修正为 "append-only extraction"（remove 操作处理矛盾）；(4) Changelog v28 说 "Design status: open for review before implementation" 但 Section 24.6 已标注 ✅ Implemented，更新为已实现状态；(5) 实施路线图 Phase 3.1 描述、Section 21.3 状态、步骤 6.2.7 说明等多处 "LLM re-extraction" 措辞修正为 append-only extraction。中英文 walkthrough 无问题（已正确引用 append-only）。
- **2026-04-01 00:49**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md 新一轮) — 修复 4 问题：(1) 两版 ARCHITECTURE.md `content_hash` 类型错误 `VARCHAR(64)`→`VARCHAR(16)`（V8 迁移实际为 16）；(2) 两版 ARCHITECTURE.md Spring Boot 配置示例严重过时——数据库名 `cortexce`→`claude_mem_dev`，地址 `localhost:5432`→`127.0.0.1`，LLM 环境变量名 `OPENAI_API_KEY`→`SPRING_AI_OPENAI_API_KEY`，缺失 `claudemem.llm.provider` 和 MCP 配置，已全面更新并添加环境变量表；(3) 两版网络安全部分/密钥管理环境变量名过时（`OPENAI_API_KEY`→`SPRING_AI_OPENAI_API_KEY`，`DB_PASSWORD`→`SPRING_DATASOURCE_PASSWORD`）；(4) backend/README.md 配置示例环境变量名过时（`OPENAI_API_KEY`→`SPRING_AI_OPENAI_API_KEY` 等 3 个），Embedding base URL 多余路径 `/v1/embeddings`，DB 变量名 `DB_USERNAME/DB_PASSWORD`→`SPRING_DATASOURCE_USERNAME/SPRING_DATASOURCE_PASSWORD`，缺失 `CLAUDEMEM_LLM_PROVIDER`。中英文版本同步更新。
- **2026-04-01 02:00**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 3 问题：(1) English doc GET /api/stats 错误列出不存在的 `project` 查询参数（代码无此参数），删除；(2) 中英文 Cursor Register 请求体字段名错误 `projectPath`→`workspacePath`（DTO 实际为 workspacePath）；(3) 中英文 Custom Context 请求体字段名错误 `content`→`context`（DTO 实际为 context）。commit e53c724。其他验证项：13 个 Controller 端点覆盖完整（Session/Ingest/Memory/Extraction/Context/Search/Management/Mode/Viewer/Import/Logs/Health/Cursor/Stream/Test），跨链接完整，响应格式与 DTO 一致。
- **2026-04-01 02:39**: SDK README (Go/Python/JS 新一轮) — 无修复。验证项：25 方法数（源码确认，三 SDK 一致）；Go 245 tests（grep `--- PASS` 确认）；HTTP 端点映射与 Controller 源码一致（25 个端点逐一验证）；triggerRefinement 使用 query param `?project=...`（三 SDK 实现一致）；getObservation 包装 getObservationsByIds（POST /api/observations/batch，三 SDK 一致）；中英文版本结构/内容/跨链接一致；设计文档交叉链接（go-sdk-design.md / python-sdk-design.md / js-sdk-design.md）存在；依赖声明准确（Go 零依赖 / Python 仅 requests / JS 零依赖 Node>=18）。无修复。
- **Next direction**: 架构文档 (新一轮轮换)
- **2026-04-01 09:13**: 运维文档 (DOCKER_README + DEPLOYMENT + DEPLOYMENT-zh-CN + .env.docker 新一轮) — 修复 5 问题：(1) DEPLOYMENT-zh-CN.md Docker 版本要求过严 `≥ 24.0`→`≥ 20.10`（与英文版一致，无 24.0 特性依赖）；(2) 两版 DEPLOYMENT.md Anthropic 模型默认值错误 `claude-sonnet-4-20250514`→`claude-sonnet-4-5`（application-dev.yml 实际默认值），环境变量名更新为 `SPRING_AI_ANTHROPIC_*` 前缀（与代码一致，保留旧名作为别名说明）；(3) DEPLOYMENT-zh-CN.md Section 5.1 `SPRING_PROFILES_ACTIVE` 默认值错误 `dev`→`prd`（docker-compose.yml 实际为 prd）；(4) DOCKER_README.md Quick Start 和 Troubleshooting 健康检查 URL 不一致 `/actuator/health`→`/api/health`（推荐端点），build 指令缺少 `prebuild-webui.sh` 步骤（Dockerfile 注释引用但 README 未提及）；(5) .env.docker 缺少 Anthropic 配置段（DEPLOYMENT 文档列出 Anthropic 选项但 .env.docker 无示例），已添加注释段。中英文版本同步更新。
- **Next direction**: API 文档 (新一轮轮换)
- **2026-04-01 03:00**: 设计文档 (phase-3-design.md + walkthrough 新一轮) — 修复 5 问题：(1) Section 24.6/Status 章节方法名错误——`runAppendOnlyExtraction()` 实际代码为 `extractAppendOnly()`，已修正（2 处）；(2) Section 24.6 方法名错误——`buildAppendOnlyPrompt()` 实际代码拆分为 `buildAppendOnlySystemPrompt()` + `buildAppendOnlyUserPrompt()`，已添加实现说明注释并更新 Section 7.2 Impact 表引用；(3) Section 2.1 缺少类名映射说明——`ExtractionTemplate`（设计名）映射为 `ExtractionConfig.TemplateConfig`（实际代码），record vs POJO getter 风格差异已标注；(4) Section 2.3 `storeExtractionResult()` 方法位置错误——实际已移至 `ExtractionStorageService`（事务安全），已添加注释说明；(5) 全文回归测试计数过时——14 处 `43/43` 更新为 `52/52`（当前 regression-test.sh 有 52 个 log_success 调用）。commit 62a7a5f。walkthrough 无问题——引用的方法名/类名均正确。
- **2026-04-01 01:42**: 运维文档 (DOCKER_README + DEPLOYMENT + DEPLOYMENT-zh-CN + CONTRIBUTING + TESTING) — 修复 3 问题：(1) DOCKER_README.md docker run 示例使用旧变量名 OPENAI_API_KEY→SPRING_AI_OPENAI_API_KEY；(2) DEPLOYMENT.md (英文) 缺少 Anthropic/LLM Provider 环境变量章节（ANTHROPIC_API_KEY/BASE_URL/MODEL、CLAUDEMEM_LLM_PROVIDER）以及 SPRING_PROFILES_ACTIVE、SERVER_ADDRESS、CLAUDEMEM_LOG_DIR，已补充；(3) DEPLOYMENT-zh-CN.md Docker Compose YAML 示例缺少 CLAUDE_MEM_MODE、CLAUDEMEM_LOG_DIR、MEMORY_REFINE_ENABLED、networks 配置，已同步实际 docker-compose.yml。commit 9c186d2
- **2026-04-01 03:30**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md 新一轮) — 修复 1 问题：API Layers 严重不完整——仅列出 5 个控制器组中的 Ingestion/Viewer/Context/Stream/Logs，遗漏 Session/Memory/Mode/Extraction/Cursor/Import/Health/Test 共 8 个；Viewer 行仅列出 2 个路径（/api/observations、/api/search），实际 ViewerController 处理 13 个路径；Application Layer 高级图仅显示 4 个控制器（Ingestion/Viewer/Context/MCP），已更新为全部 13 个。中英文版本同步更新。其他验证项：13 个 Controller 源码一致、29 个 Service 列表完整、14 个 Flyway 迁移一致、Schema SQL 准确、Spring Boot 配置准确、MCP 传输协议配置准确、proxy 目录列表准确。commit bcbd704
- **2026-04-01 04:00**: 运维文档 (DEPLOYMENT + DEPLOYMENT-zh-CN 新一轮) — 修复 1 问题：(1) DEPLOYMENT-zh-CN.md Section 5.5 运行时配置环境变量表缺少 `MEMORY_REFINE_ENABLED`（docker-compose.yml 和 .env.example 均有，中文文档遗漏）；英文 DEPLOYMENT.md 描述优化 "Enable memory refinement" → "Enable memory refinement (self-evolution)"。中英文版本同步更新。commit 823b6d1。其他验证项：Flyway 迁移列表 V1-V16 与实际 migration 文件一致；docker-compose.yml 服务名/volume 名/网络名与文档一致；Dockerfile HEALTHCHECK 路径正确；.env.docker 变量名与 docker-compose.yml 一致；DEPLOYMENT 环境变量表其余条目准确。
- **Next direction**: API 文档 (新一轮轮换)
- **2026-04-01 04:40**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 1 问题：(1) English Search section severely incomplete — missing parameter types column, request example, response example (strategy/fell_back/count fields), and strategy notes; Chinese version already had all of this. Added full parameter table with types, curl request example, JSON response example, and search strategy explanation (vector vs text fallback). Chinese changelog synced. commit 0f80d97。其他验证项：13 个 Controller 源码一致，端点覆盖完整（Session 3 + Ingest 4 + Memory 7 + Extraction 3 + Context 6 + Viewer 15 + Mode 8 + Import 5 + Logs 2 + Health 3 + Cursor 6 + Stream 1 + Test 3 = 66 endpoints）；中英文版本端点覆盖一致；字段名与 DTO 源码一致（SearchResponse: observations/strategy/fell_back/count）；环境变量表准确。
- **2026-04-01 11:02**: SDK README (Go/Python/JS 新一轮) — 无修复。验证项：25 个 API 方法数（三 SDK 源码一致）；Go 245 tests（go test -v 确认）；JS 198 tests（grep 确认）；Python 347 tests（grep 确认）；HTTP 端点映射与 Controller 源码一致（ExtractionController 3 个端点逐一验证）；triggerRefinement 使用 query param（三 SDK 实现一致）；getLatestExtraction/getExtractionHistory 参数名 projectPath/templateName 正确（三 SDK 源码一致）；HealthCheck 返回类型差异（Go/Python 返回 error/void，JS 返回 HealthResponse）为有意设计；中英文版本结构/内容/跨链接一致（Go/JS/Python 各有 design doc 链接且文件存在）；Features 章节三 SDK 双语一致；Wire Format 章节三 SDK 双语一致；依赖声明准确（Go 零依赖 / Python 仅 requests / JS 零依赖 Node>=18）；Quick Start 示例代码可运行。无修复。
- **2026-04-01 05:00**: SDK README (Go/Python/JS 新一轮) — 修复 1 问题：Python SDK README 缺少 Features 章节（Go 和 JS 都有），中英文两版同步添加。commit 6e48691。其他验证项：25 个 API 方法数（三 SDK 源码一致）；Go 245 tests（go test 确认）；JS 198 tests（vitest 确认）；Python 344 tests（grep 确认）；HTTP 端点映射与 Controller 源码一致（25 个端点逐一验证）；triggerRefinement 使用 query param（三 SDK 实现一致）；中英文版本结构/内容/跨链接一致（Go/JS/Python 各有 design doc 链接）；依赖声明准确（Go 零依赖 / Python 仅 requests / JS 零依赖 Node>=18）。
- **Next direction**: 设计文档 (新一轮轮换)
- **2026-04-01 05:30**: 设计文档 (phase-3-design.md + walkthrough 新一轮) — 修复 3 问题：(1) 回归测试计数过时——13 处 `52/52` 更新为 `53/53`（当前 regression-test.sh 有 53 个 log_success 调用）；(2) Section 24.6 `mergeAppendOnly()` 伪代码严重过时——缺少 `template` 参数、LLM 响应键验证、`safeListOfMaps` 调用、`resolveKeyFields` 逻辑、`keep_hint` 移除保护、`buildItemKey(item, keyFields)` 带 keyFields 参数（6 处调用），已更新伪代码匹配实际代码并标注 "Simplified pseudocode — see StructuredExtractionService.java for full implementation"；(3) Section 2.3 `runTemplateExtraction` 伪代码引用不存在的 `ExtractionState`/`getExtractionState`/`updateExtractionState`（实际代码使用 `fetchPriorJson`），添加 IMPLEMENTATION NOTE 说明差异；"LLM re-extraction" 注释修正为 "Fetch prior extraction result for append-only merge" 并标注历史遗留。walkthrough 无问题——引用的方法名/类名均正确。commit pending
- **2026-04-01 06:06**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md 新一轮) — 修复 2 问题：(1) ARCHITECTURE.md + ARCHITECTURE-zh-CN.md 环境变量表缺少 4 个变量——`SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS`、`SPRING_AI_ANTHROPIC_API_KEY`、`SPRING_AI_ANTHROPIC_BASE_URL`、`SPRING_AI_ANTHROPIC_CHAT_MODEL`（application-dev.yml 和 DEPLOYMENT.md 已有但 ARCHITECTURE 遗漏），中英文两版同步补充；(2) backend/README.md API 端点表严重不完整——仅列出 21 个端点（Ingestion 5 + Viewer 15 + Stream 1），遗漏 Session(3)/Context(6)/Memory(7)/Mode(5)/Extraction(3)/Cursor(3)/Import(4)/Logs(2)/Health(3)/Test(3) 共 45 个端点，已全面补充至 66 个端点。commit 79f3bb9。其他验证项：13 个 Controller 源码一致、29 个 Service 列表完整（EN/ZH/backend README 三版一致）、14 个 Flyway 迁移一致、Schema SQL(content_hash VARCHAR(16) 准确)、Spring Boot 3.3.13 正确、ViewerController /api/concepts 端点已注释掉不纳入、MCP 传输协议配置准确、proxy 目录列表准确、中英文版本跨链接完整。
- **Next direction**: API 文档 (新一轮轮换)
- **2026-04-01 07:00**: SDK README (Go/Python/JS 新一轮) — 修复 1 问题：JS SDK README (EN+ZH) `getLatestExtraction` 和 `getExtractionHistory` 参数名与源码不一致——`project`→`projectPath`，`template`→`templateName`，HTTP 路径 `{template}`→`{templateName}`，中英文两版同步修复。commit 8989216。其他验证项：25 个 API 方法数（三 SDK 源码一致）；Go 245 test functions（go test 确认）；JS 198 tests（vitest 确认）；Python 347 tests（pytest 确认）；triggerRefinement 使用 query param（三 SDK 实现一致）；中英文版本结构/内容/跨链接一致（Go/JS/Python 各有 design doc 链接）；依赖声明准确（Go 零依赖 / Python 仅 requests / JS 零依赖 Node>=18）。
- **Next direction**: 设计文档 (新一轮轮换)
- **2026-04-01 06:41**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 1 问题：中英文搜索策略值严重错误——英文文档列出 `vector`/`text`，中文文档列出 `semantic`/`text`，但实际 SearchService 代码返回 `hybrid`/`tsvector`/`filter`/`recent`/`none`。同时修正文本回退描述（代码使用 PostgreSQL tsvector 全文搜索，非 LIKE/ILIKE）。中英文版本同步更新，changelog 同步。commit 2bdba3a。其他验证项：13 个 Controller 端点覆盖完整（66 个端点），中英文版本端点覆盖一致，字段名与 DTO 源码一致，环境变量表准确。
- **Next direction**: SDK README (新一轮轮换)
- **2026-04-01 07:49**: 设计文档 (phase-3-design.md 新一轮) — 修复 1 问题：主文档多个关键章节仍以"LLM re-extraction"为活跃实现描述，与实际 append-only extraction 不符——(1) Section 2.3 extractByTemplate() javadoc 未标注实际代码使用 extractAppendOnly()，且 4 参数签名与实际 3 参数不一致，已添加 IMPLEMENTATION NOTE；(2) Section 2.3 buildPrompt() 伪代码仍显示将 prior context 传给 LLM（含 summarizePriorExtraction），已标记 HISTORICAL；(3) Section 2.3 storeExtractionResult() javadoc 说"no programmatic merge needed"但实际代码通过 mergeAppendOnly() 执行程序化合并，已修正并标注 ExtractionStorageService；(4) Section 7.1/15.8 注释仍引用 "LLM re-extraction"，已更新为 append-only extraction；(5) Section 26 测试表 5 处 "LLM re-extraction" → "Append-only extraction"。walkthrough 无问题（已正确引用 append-only）。commit 8d3c34f。其他验证项：回归测试 53/53 通过、方法名与源码一致、ExtractionState 相关引用已在 IMPLEMENTATION NOTE 中标注差异。
- **2026-04-01 08:27**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md 新一轮) — 修复 1 问题：两个语言版本 mem_sessions CREATE TABLE Schema 缺少 V4__context_caching.sql 添加的 3 个列（cached_context TEXT, context_refreshed_at_epoch BIGINT, needs_context_refresh BOOLEAN DEFAULT FALSE），迁移注释从 "V1 + V12, V13, V15" 更新为 "V1 + V4, V12, V13, V15"，中英文版本同步修复。commit 4528cff。其他验证项：13 个 Controller 列表中英文一致、29 个 Service 列表一致、14 个 Flyway 迁移文件一致、环境变量表 14 个变量中英文一致（含 Anthropic）、Spring Boot 3.3.13 正确、MCP 传输协议准确、跨链接完整、Schema 其余部分准确。
- **Next direction**: API 文档 (新一轮轮换)
- **2026-04-01 10:00**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 4 问题：(1) 英文 Ingest 章节严重不完整——3 个端点（tool-use/user-prompt/session-end）仅有原始 JSON 示例，缺少参数表、响应示例、错误响应（中文版已完整），已补充完整文档（含速率限制说明）；(2) Quality Distribution 信息缺失（中英文两版）——实际响应包含 `unknown` 字段但两版均未记录，英文版更是只有 URL 无参数表/响应示例，已补充参数表、响应示例、`unknown` 字段及 500 错误响应；(3) Batch Get Observations `orderBy` 示例错误——英文写 `created_at`，实际代码只接受 `created_at_epoch`/`createdAtEpoch`，已修正；(4) 英文 Create Observation 缺少参数表（仅原始 JSON），已补充完整参数表。commit 1128736。其他验证项：13 个 Controller 源码一致，66 个端点覆盖完整，中英文端点列表一致，字段名与 DTO 源码一致，环境变量表准确，搜索策略值正确。
- **2026-04-01 11:02**: SDK README (Go/Python/JS 新一轮) — 无修复。验证项：25 个 API 方法数（三 SDK 源码一致）；Go 245 tests（go test -v 确认，含 `--- PASS` 计数）；JS 198 tests（grep 确认）；Python 347 tests（grep 确认）；HTTP 端点映射与 Controller 源码一致（ExtractionController 端点逐一验证）；triggerRefinement 使用 query param `?project=...`（三 SDK 实现一致）；getLatestExtraction/getExtractionHistory 参数名 projectPath/templateName 正确（三 SDK 源码一致）；HealthCheck 返回类型差异（Go 返回 error、Python 返回 void、JS 返回 HealthResponse）为有意设计，文档准确描述；中英文版本结构/内容/跨链接一致（Go/JS/Python 各有 design doc 链接且文件存在）；Features 章节 5 项三 SDK 双语一致；Wire Format 章节三 SDK 双语一致；依赖声明准确（Go 零依赖 go 1.22 / Python 仅 requests>=2.28 / JS 零依赖 Node>=18）；Quick Start 示例代码签名验证可运行；ObservationUpdate 双模式章节中英文一致。无修复。

- **Next direction**: 设计文档 (新一轮轮换)
- **2026-04-01 12:36**: 设计文档 (phase-3-design.md + walkthrough 新一轮) — 修复 3 问题：(1) Acceptance Test 7 标题仍为 "LLM Re-Extraction Updates State"，实际实现为 append-only extraction，修正为 "Append-only Extraction Updates State"；(2) Acceptance Test 8 标题仍为 "LLM Re-Extraction Removes Invalidated Preference"，同样修正；(3) phase-3-design.md 顶部缺少 walkthrough 交叉链接（walkthrough 已链接回但设计文档未链接），已添加 Companion 行。commit 098d139。其他验证项：回归测试 53/53 通过；方法名 extractAppendOnly/mergeAppendOnly/buildAppendOnlySystemPrompt/buildAppendOnlyUserPrompt 与源码一致；Section 24.6 伪代码签名与源码一致；ExtractionState 引用已有 IMPLEMENTATION NOTE；walkthrough 正确引用 append-only；回归测试计数 53/53 全文一致；HISTORICAL/SUPERSEDED 标注完整。
- **Next direction**: 架构文档 (新一轮轮换)
- **2026-04-01 13:38**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md 新一轮) — 修复 4 问题：(1) mem_user_prompts Schema 缺少 project_path 列（V5 迁移添加），中英文两版同步补充；(2) mem_observations Schema 严重不完整——V11 迁移添加了 8 列但仅 quality_score 被记录，补充 feedback_type/last_accessed_at/access_count/refined_at/refined_from_ids/user_comment/feedback_updated_at，中英文两版同步；(3) mem_pending_messages Schema 缺少 started_processing_at_epoch 列（代码实体和 Repository 均使用），中英文两版同步补充；(4) backend/README.md util/ 目录缺少 PathValidationUtil.java，E2E 测试健康检查 URL /actuator/health→/api/health。commit 0d87004。
- **2026-04-01 15:47**: API 文档 (API.md + API-zh-CN.md 新一轮) — 修复 2 问题：(1) Get Settings 响应格式严重过时——两版均显示简单 `{"mode":"code","modeName":"...","modeDescription":"..."}`，实际代码返回 20 个 `CLAUDE_MEM_*` 字段 + modeName/modeDescription（commit f655b6f 引入），已全面更新响应示例并列出所有字段及类型；Update Settings 接受 `CLAUDE_MEM_*` 字段名及 `mode` 简写，已补充说明和 500 错误响应；(2) 中文 PATCH /api/memory/observations/{id} 响应 status 值错误 `ok`→`updated`（与英文版和实际代码一致）。中英文版本同步更新，changelog 同步。commit a9fb8a5。
- **Next direction**: SDK README (新一轮轮换)
