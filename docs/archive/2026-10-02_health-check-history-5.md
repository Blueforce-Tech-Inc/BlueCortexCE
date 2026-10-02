# 巡检历史归档 5 — 第 149~157 轮

> **来源**: `docs/drafts/health-check-task.md`
> **归档时间**: 2026-10-02（第 160 轮写入前，活动文件达阈值）
> **内容**: 第 149~157 轮健康检查/巡检历史，共 9 轮
> **规则**: 归档文件创建后不得修改。

> **一处更正**: 归档时发现第 157 轮的区块曾被错误地插入到第 156 轮正文之中（当时用 `rindex` 匹配「下一方向」字样，命中了 156 轮正文内的同一措辞），导致其物理位置排在 159 轮之后。本文件按**轮号**而非物理位置重新提取，故顺序已纠正为时间顺序；内容未作任何改动。

## 第 149 轮 — 2026-10-02（Backend + 设计文档）

**预检**：`/api/health` = `status=ok`、`/actuator/health` 200、`/api/version` = `0.1.0-beta` / Spring Boot `3.3.13`；轮前 `doc-growth-check.sh` exit 0；轮前指纹 `523d4b39…` 与基线一致。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | 后端在线，Demo 端口 37778–37781 全关 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `523d4b39…` → `85fb22fa…`，触发完整验收 |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| 后端单测（新增） | ✅ 21 / 0 | `SearchServiceLimitTest`；退回旧代码后 12 个失败 |
| 活体验证（REST） | ✅ | `/api/search` 无过滤路径由 2 倍收敛为精确 limit |
| 活体验证（MCP） | ✅ | 5 档 limit 全部收敛到 100 行 / 97 KB / ~6.4s |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Backend 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| BE-1 | `SearchService.applyPostFilters()` | **`limit` 参数完全失效：无过滤的搜索一律返回 2 倍结果**。仓储层为给后置过滤留余量，按 `limit * 2` 取行；但当请求不带 `type`/`source`/`concept`/`offset`/`orderBy` 时，`needsProcessing=false` 走提前返回分支，**原样返回整个 `limit*2` 列表，从不回裁**。实测 `/api/search?project=/tmp/phase3-acceptance-test`（22415 条）`limit=5/20/100` 实际返回 **10/40/200** 条，与 `ViewerController:295` 文档声明的「max 100」矛盾 | 改签名为 `applyPostFilters(results, request, limit)`，传入调用方钳制后的 `limit`；提前返回分支经 `trimToLimit()` 裁剪；末尾 `stream.limit(request.limit())` 改为 `stream.limit(limit)`。三处调用点（第 64/98/154 行）均在作用域内已有该变量 |
| BE-2 | `ClaudeMemMcpTools.search()` | **MCP `search` 工具的 `limit` 完全没有上下界**：`effectiveLimit = limit != null ? limit : 20`，直接透传给 SQL `LIMIT`。实测未裁剪时 `limit=10000` 产出 **253 MB / 20000 行 / 34 秒**的单次 JSON-RPC 响应（同一项目 `limit=100` 即 5.0 MB）。REST 侧 `ViewerController:303` 有 `Math.min(..., MAX_PAGE_SIZE)`，MCP 侧没有 | 与 REST 对齐：`Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE)`；`@McpToolParam` 描述改为「Max results, 1-100 (default: 20)」 |
| BE-3 | `SearchService.search()` 第 43–45 行 | **整数溢出**：`limit * 2` 在 `limit ≥ 2^30` 时溢出为负，传给原生 SQL `LIMIT :limit` 被 PostgreSQL 拒绝。边界实测精确落在 `2^30`：`1073741823` 正常，`1073741824` 及以上失败。表现不是报错而是**静默降级**——`fullTextSearch` 抛错被第 101 行吞掉，返回 `count:0, strategy:"none", fell_back:true, isError:false`，调用方无从察觉 | 把钳制收敛到 `SearchService` 单一入口：`Math.min(rawLimit <= 0 ? DEFAULT_LIMIT : rawLimit, Constants.MAX_PAGE_SIZE)`。如此无论调用方是谁，`limit*2 ≤ 200` 恒成立 |

**BE-1/BE-2 的完整证据链**

- **修复前实测（REST，`project=/tmp/phase3-acceptance-test`，该库 22415 条 observation）**：`limit=5→10 条`、`limit=20→40 条`、`limit=100→200 条`（`strategy=recent`）；加 `type=test` 后 `limit=5/20` 精确返回 5/20（`strategy=filter`），证明差异正来自 `needsProcessing` 分支而非仓储层。
- **修复前实测（MCP，同一项目）**：`limit=100→200 行/5.0 MB`、`limit=1000→2000 行/110 MB`、`limit=5000→10000 行/191 MB`、`limit=10000→20000 行/253 MB/34.1s`，**每一档都恰好是请求值的 2 倍**；`limit=1073741824→0 行, strategy=none, fell_back=true`。
- **修复后实测（REST）**：`limit=5/20/100` 一律精确返回 **5/20/100** 条；带 `type=test` 仍为 5/20，行为未变。
- **修复后实测（MCP）**：`limit=100 / 1000 / 5000 / 10000 / 1073741824` **全部收敛为 100 行 / 97302 字节 / ~6.4 秒**，最大负载从 253 MB 降到 97 KB（−99.96%），2^30 不再静默返回空集。
- **下游依赖核查（修复不会改变任何调用方的语义）**：`ContextController./semantic` 把结果集**全部**拼进 prompt（此前被注入 2 倍上下文，修复后才是设计意图的 `limit ≤ 20`）；`TimelineService.findAnchorByQuery` 传 `limit=1` 且只取 `.get(0)`；MCP 原样透传。三者均不依赖 2 倍。
- **回归测试**：`SearchServiceLimitTest` 21 个用例。退回旧代码后 **12 个失败**（`unfilteredSearch_trimsToRequestedLimit`、`limitOfOne_returnsOne`、`smallLimits_areTrimmed`、`maxPageSize_isHonoured`、`oversizedLimit_isClampedToMaxPageSize`、`twoToTheThirty_doesNotProduceNegativeSqlLimit`、`intMaxValue_doesNotOverflow`、`nonPositiveLimits_fallBackToDefault`、`fullTextPath_trimsToLimit`、`hybridPath_trimsToLimit`、`hybridPath_isClamped`、`invalidQueryVector_fallsThroughToTrimmedResult`），恢复后 21/21。
- **过程中的一次自我纠错**：初版测试假设 `new float[]{0.5f}` 是无效向量（想让用例走 full-text 回退），实跑失败。查 `VectorValidator` 后确认 `[0.5000]` 是**合法**的 1 维向量、实际走 hybrid 分支；改用 `new float[0]`（序列化为 `[]`，维度为 0 才被拒），并补了 hybrid 路径自身的裁剪与钳制两个用例。

**文档方向（设计文档）发现与处理**

审查对象：`docs/drafts/phase-3-design-walkthrough.md`（`doc-review-task.md` §审查范围 第 3 项所指的 walkthrough；第 144 轮已审查过同目录的 `index.md`）。

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-9 | walkthrough Scenario 6 | 定时触发指向**错误的章节**：「Scheduled daily at 2am (Section 9.1)」。但 §9.1 的标题是 *Existing Repository Methods*（讲仓储方法），与调度无关。同库 `8.md:3` 自己给出的交叉引用是「Section 9.2 + Section 23.7」 | 改为 `Section 9.2 + Section 23.7`，与 `8.md:3` 对齐 |
| DOC-10 | walkthrough Scenario 6 | 断言「`triggerKeywords` 字段在模板中存在，只是没有用于实时触发」——**该字段在实现里根本不存在**。`ExtractionConfig.TemplateConfig` 只声明了 `name`/`enabled`/`template-class`/`session-id-pattern`/`source-filter`/`key-fields`/`prompt`/`output-schema` 八个字段，全仓 `grep triggerKeywords` 零命中，部署用的 `backend/src/main/resources/application.yml` 模板也没有 `trigger-keywords` 键。它只出现在 `2.md` §2.2 的设计 YAML 示例里 | 改为如实描述：指出该键仅存在于设计示例、并非 `TemplateConfig` 的字段，当前「没有可读的字段」而非「有字段未使用」；并列出实际的八个字段名作为依据 |
| DOC-11 | walkthrough 9 处设计引用 | 全部指向根指针文件 `phase-3-design.md`（564 字节，内容仅是「已迁移 + 从这里开始」），读者点进去仍需自行在 30 个子文档中检索 | 逐条改指拆分后的实际子文档：§2.2 → `2.md`、§24.6 → `24.6.md`、§20.2/§20.3/§20.9 → `20.md`、§23 → `23.md`、总入口 → `index.md`；5 个目标文件均已核实存在 |
| DOC-12 | `doc-review-task.md` §审查范围 | 第 3 项仍写「`docs/drafts/phase-3-design.md` 与 walkthrough」，未反映该文件已拆分为目录的事实，后续轮次容易据此误判 | 改为指向 `phase-3-design/` 目录 + `index.md` 入口，并加一句「引用具体章节应指向对应子文档，不要只指向指针文件」 |

**DOC-9~DOC-12 的核实**（先证实引用正确才动手，避免制造新差异）：

- 9 处章节号在拆分后**全部仍然存在**：`2.md:35 §2.2`、`24.6.md:1 §24.6`、`20.md:72/130/402 §20.2/20.3/20.9`、`23.md:1 §23`、`15.md:194 §15.5`、`9.md:3 §9.1`、`19.md:39 §19.3`。
- 两处**曾怀疑但核实为正确**的引用：§2.2 标题虽是 *Configuration Model (YAML)*，但其 YAML 示例确实内嵌了数组包裹的 `output-schema`（`preferences: [...]`），且 `20.md:375` 明确写「✅ Resolved (Section 2.2)」——不动。§20.9 标题 *Ingestion API user_id Passing* 与 Decision 5 的 Hook/SDK userId 表主题吻合（`99-changelog` 亦记载该节为「ingestion API user_id passing design」）——不动。
- 仅 §9.1 语义不符：§9.2 是 *Integration Points with MemoryRefineService*，提到「New scheduled task for periodic extraction」；具体 cron 默认值 `schedule: "0 0 2 * * ?"` 记在 `23.md:110/208`（§23.7 Recommended Cost Configuration）与 `0.1.md:118`。采用与 `8.md:3` 一致的「§9.2 + §23.7」表述。
- 实现侧交叉核实（`trigger-keywords` 结论的证据）：`ExtractionConfig.java` 全文只有上述 8 个模板字段；`grep -rn "triggerKeywords|trigger-keywords" src/main src/main/resources` 零命中。
- 其他 Scenario 的实现断言逐条核对通过：`mergeAppendOnly()` 在 `StructuredExtractionService.java:376`、`buildAppendOnlySystemPrompt()` 在 `:354`、`initialRunMaxCandidates` 默认 100 在 `ExtractionConfig.java:23`（与 §23.7 的 `initial-run-max-candidates: 100` 一致）、`deepRefineProjectMemories()` 在 `MemoryRefineService.java:203`。

**未解决问题**

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更，本轮未动。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（非本轮引入）：`mvn test` 在干净树上同样失败，根因是当前 JDK 24 下 Mockito inline mock 无法 mock `EmbeddingService`（`MockitoException: Could not modify all classes`），失败发生在 `MockitoExtension.beforeEach`、早于任何断言。已用 `git stash` 排除本轮改动复核确认。本轮验收门控用的是 `scripts/regression-test.sh`（E2E），不受影响。
3. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司（改根 LICENSE）或 (b) 统一为社区贡献者（改 js-sdk LICENSE）。
4. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
5. push 权限阻塞（`wubuku` 403）。
6. 并行巡检进程争写状态文件。
7. `CLAUDE.md` 被 `.gitignore` 忽略。


---

## 第 150 轮 — 2026-10-02（Go SDK + 架构文档）

**预检**：`/api/health` = `status=ok`、`/actuator/health` 200、`/api/version` = `0.1.0-beta` / Spring Boot `3.3.13`；轮前 `doc-growth-check.sh` exit 0；轮前指纹 `85fb22fa…` 与基线一致；37778–37781 全关。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | 后端在线 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `85fb22fa…` → `25c2c0f9…` → `acadb008…`（`.gitignore` 属代码路径，触发第二次验收） |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过），**跑了两遍** |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`，**跑了两遍** |
| Thin Proxy 测试 | ✅ 43 / 43 | `bash scripts/thin-proxy-test.sh`（原 41，新增 2） |
| Go SDK 单测 | ✅ 全绿 | `gofmt -l` 无输出、`go vet` 无输出；root / dto / examples 三模块 |
| Go SDK E2E | ✅ 39 / 39 | `bash scripts/go-sdk-e2e-test.sh`（本轮启动 37779，测完已停） |
| Demo 端口 | ✅ 已清理 | 37778/37779/37780/37781 全关 |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**本轮两个 P1 级缺陷（均为静默失败）**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| SDK-1 | `proxy/wrapper.js` `handleUserPrompt()` | **官方 Claude Code 集成记录的每一条用户 prompt 文本都是空的**。钩子事件里 Claude Code 传的是 `prompt` 字段（官方文档：*"UserPromptSubmit hooks receive the `prompt` field containing the text the user submitted"*），而代码解构的是 `prompt_text` → 恒为 `undefined` → `strippedPromptText = ''`。请求仍返回 200，于是钩子照常打印「User prompt recorded」——**失败完全静默** | 优先取 `event.prompt`，`event.prompt_text` 保留为旧拼写回退；隐私标签剥离与「整条私密则跳过」两处判断都改用解析后的值 |
| SDK-2 | Go SDK `HealthCheck()` | **任何 200 响应都被判为健康**。`if err := json.Unmarshal(data, &resp); err == nil` 把解析错误吞掉，代理错误页、门户劫持页、截断响应、空 200 全部跳过 status 检查直接返回 `nil`。HealthCheck 被当作就绪门禁用，「无法确认」被报成「已确认」 | 解析失败与非健康同等处理，返回错误。JS SDK 已是正确行为（`requestJSON` 解析失败抛异常） |

**SDK-1 的证据链**

- **官方依据**：`https://code.claude.com/docs/en/hooks` 的 *UserPromptSubmit input* 一节明确写 `prompt` 字段，示例负载为 `"prompt": "Write a function to calculate the factorial of a number"`。
- **活体 A/B 对照**（同一后端、同一脚本）：官方 `prompt` 字段 → 落库 `prompt_text=''`；旧 `prompt_text` 字段 → 落库 `'LEGACY_FIELD_VALUE_BETA'`。**两次都打印「User prompt recorded」**。
- **修复后同样 A/B**：官方字段 → `'OFFICIAL_FIELD_VALUE_ALPHA'`，旧字段 → `'LEGACY_FIELD_VALUE_BETA'`，两条路径都正常。
- **回归防护**：原 16g/16h 只用旧字段名、且**只断言 stderr 文本**（不查库），正是这个盲区让缺陷长期未被发现。新增 **16i**（发官方 `prompt` 并从 `/api/prompts` 读回落库值）与 **16j**（钉住旧拼写）。退回修复后 **42/43**，恰好只有 16i 失败。
- **同批核对其余三个事件**：`SessionStart`（`cwd`/`session_id`/`source`）、`PostToolUse`（`tool_name`/`tool_input`/`tool_response`）、`SessionEnd`（`transcript_path`）字段名均与官方一致，且用真实事件跑通（tool-use 正常处理、session-end 正常解析 transcript 路径），未改动。
- **区分清楚两套字段**：后端 API 的 `prompt_text`（`UserPromptRequest` 的 `@JsonProperty("prompt_text")`）是正确的，wrapper 的职责正是把事件字段 `prompt` 映射到它；`docs/API.md:292/300/2354` 描述的都是后端线格式，无需改动。

**SDK-2 的证据链与跨 SDK 现状**

- 新增 `TestHealthCheck_NonJSONBodyIsNotHealthy`，6 个子用例：HTML / 纯文本 / 空 body / 截断 JSON / 顶层数组 / 无 status 的 JSON 对象。**退回旧代码 6 个中 5 个失败**（无 status 的那个因为会走 status 检查，本来就正确报错）。
- **跨 SDK 对照**：JS 正确（`requestJSON` 对 `JSON.parse` 失败抛 `cortex-ce: failed to parse …`）；**Python 有同一缺陷**（`_request_json` 对非 JSON 返回 `None`，而 `health_check` 只判断 `isinstance(data, dict)`，`None` 同样被当作健康）——本轮只修 Go，Python 那条已完整记录在下方「未解决问题」，留给 Python 轮次处理，避免两个客户端只改一半。

**Go SDK 其余部分：未发现新缺陷（核查过但确认无问题）**

- 逐个通读 30 个公开方法的参数校验与端点构造：必填校验字段集与其它 SDK 一致，`url.PathEscape` 用于所有路径插值，`GetExtractionHistory` 的 limit 语义（负数报错、≤0 省略走后端默认 10）与 JS/Python 一致。
- 重试：`doFireAndForget` 只对网络错误与 429/502/503/504 重试，线性退避带 ±25% 抖动；第 145 轮已核实后端按 `(session, tool_name, tool_input_hash)` 去重且重复时返回 200，重试安全。
- DTO 反序列化：`StringList` 同时处理「JSON 数组」与「字符串包裹的 JSON 数组」两种后端形态，`null` 也正确。
- **系统性的字段覆盖比对**（把后端实际返回的叶子字段与 Go DTO 的 json tag 双向比对）：Observation 少建 `content_hash`/`discovery_tokens`/`embedding_*`/`embedding_model_id`/`generated_by_model`/`platform_source`/`relevance_count`/`step_number`，`DatabaseStats` 少 `projectPath`，`ProjectsResponse` 少 V18 的 `sources`/`projectsBySource`。逐项 grep 四个 SDK 后确认：**这 9+1+2 个字段在 Go/Python/JS/Java 中一致地未被建模**，属一致取舍而非 Go 独有缺口；按既定决策不在单轮制造新的表面积差异，留待覆盖三个 typed SDK 的轮次统一处理。
- `examples/http-server/http-server` 二进制虽在工作区，但已被 `go-sdk/cortex-mem-go/.gitignore:5` 忽略且未跟踪，无问题。

**文档方向（架构文档）发现与处理**

审查对象：`backend/README.md` 与根 `README.md`（`doc-review-task.md` §审查范围 第 4 项；`docs/ARCHITECTURE*.md` 第 145 轮已核过中英完全一致）。

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-13 | `backend/README.md` | **只写了单数 `/api/mode/*` 一族，SDK 实际调用的复数 `/api/modes`（GET+POST）完全缺失**。两族由不同 Controller 提供且**响应不可互换**：`GET /api/mode` 返回 `mode_id`，`GET /api/modes` 返回 `id`。而 Go/Python/JS/Java **四个 SDK 全部调用 `GET /api/modes`**。`docs/API.md` 本就两族齐全（1164–1301 单数、1570/1590 复数），`backend/README.md` 是唯一的缺口 | Viewer API 表补上 GET/POST `/api/modes` 两行；Mode 段前置一张对照表写明两族的 Controller、id 字段名与调用方 |
| DOC-14 | `.gitignore` | **`proxy/node_modules` 与 `admin-panel/node_modules` 未被忽略**。根 `.gitignore:68,98` 已覆盖 `openclaw-plugin` 与 `codex-watcher`，`js-sdk` 有自己的规则，唯独这两个漏了。而根 README Step 3 明确让用户 `cd proxy && npm install`——照文档在 clone 里装一遍，`git status` 就会多出数千个未跟踪文件 | 在既有 `openclaw-plugin/node_modules` 规则旁补两行 |

**DOC-13/DOC-14 的核实**

- **端点覆盖**：解析 Controller 时先剥离注释——`ViewerController.java:207` 有一个被注释掉的 `@GetMapping("/concepts")`，不剥离会误判为生效端点（活体确认返回 404）。剥离后**生效端点 67 个，README 记录 67 个，双向零差异**。初版脚本曾报 14 个「缺失」，逐条追查后确认全是正则误报（`@PostMapping(value = "/generate", produces = …)` 带额外属性未被匹配）。
- **Project Structure**：树中 104 个文件名逐个落盘核对，**Java 文件 83/83、迁移 16/16 全部列出，磁盘上无遗漏、无幻影**。
- **根 README 的 Claude Code hooks 配置：确认无误**。四个命令名 `session-start` / `tool-use` / `session-end` / `user-prompt` 与 `wrapper.js` 的 dispatch switch 及 usage 文本一致（`observation`/`summarize` 是 **Cursor** 命令，不是 Claude Code 的）。本轮用真实事件对四个命令逐一实跑，全部正常。Step 3 的 `npm install` 也有写——我最初在本仓库直接跑 wrapper 报 `Cannot find package 'axios'`，那是本地 checkout 未装依赖，不是文档缺陷。
- **`.gitignore`**：`git check-ignore -v` 确认两行生效，`git status` 不再出现 `proxy/node_modules/`。

**未解决问题**

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **Python SDK `health_check` 有与 Go 同一缺陷**（本轮新发现，`python-sdk/cortex-mem-python/cortex_mem/client.py:579-583`）：`_request_json` 对非 JSON 响应返回 `None`（:148-153 的 docstring 写明是「graceful degradation」），`health_check` 只判断 `isinstance(data, dict)`，`None` 不是 dict 因而直接落到函数末尾返回 `None` = 健康。修法：与 Go SDK 对齐，在 `health_check` 里对 `data is None` 或非 dict 抛 `CortexError`。**本轮只修 Go，留给 Python 轮次，避免两个客户端只改一半。**
3. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录，非本轮引入）。
4. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策。
5. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
6. push 权限阻塞（`wubuku` 403）。
7. 并行巡检进程争写状态文件。
8. `CLAUDE.md` 被 `.gitignore` 忽略。


---

## 第 151 轮 — 2026-10-02（Python SDK + 用户指南）

**预检**：`/api/health` = `status=ok`；轮前 `doc-growth-check.sh` exit 0；轮前指纹 `acadb008…` 与基线一致。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | 后端在线 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `acadb008…` → `7c7e1359…`（Python 源码属代码路径），触发完整验收 |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Python SDK 单测 | ✅ 385 | `python3 -m pytest tests/ -q`（原 378，+7） |
| Python SDK E2E | ✅ 28 / 28 | `bash scripts/python-sdk-e2e-test.sh` |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Python SDK 方向：修复第 150 轮记录��同源缺陷**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| SDK-3 | `python-sdk/.../client.py` `health_check()` | **与第 150 轮 Go SDK 完全同源的缺陷**。`_request_json` 对 204 与任何非 JSON 响应返回 `None`（其 docstring 自称「graceful degradation」），而 `health_check` 只判断 `isinstance(data, dict)`，`None` 不是 dict 因而直接落到函数末尾返回 `None`——也就是**判定为健康** | 与 Go SDK 对齐：非 dict 一律视为不健康并抛出 `CortexError`；docstring 写明「无法确认后端应答的健康检查不是健康检查」 |

**SDK-3 的证据链与跨 SDK 收口**

- 新增 7 个用例：HTML / 纯文本 / 空 body / 截断 JSON / 顶层数组 / 无 status 的 JSON 对象 / 204。**退回旧代码后 6 个失败**（无 status 的那个本来就会走到 status 检查，因此原本就正确报错）。
- 至此三个自行解析 body 的客户端里，**Go 与 Python 行为一致**，委托给严格解析器的 **JS 本来就正确**（`requestJSON` 对 `JSON.parse` 失败抛异常）——第 150 轮刻意留下的「只改一半」缺口本轮收口。
- Python SDK 385 单测（原 378）+ E2E 28/28 全绿。

**用户指南方向（DEVELOPMENT.md / DEVELOPMENT-zh-CN.md）发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-15 | `docs/DEVELOPMENT.md` + `DEVELOPMENT-zh-CN.md` §Build Profiles | **文档给出的构建 profile 根本不存在**：两版都把 `./mvnw clean package -DskipTests -Pdev` 写成「开发构建」、`-Pprod` 写成「生产构建（带优化）」。而 `backend/pom.xml` **完全没有 `<profiles>` 段**——实际可用的只有从 `spring-boot-starter-parent` 3.3.13 继承的 `native` / `nativeTest`（都构建 GraalVM 原生镜像，需要 `native-image` 工具链） | 两版改写为：说明实际存在哪些 profile、给出 `./mvnw help:all-profiles` 自查命令、并原文引用那条警告 |

**DOC-15 的核实**

- **实跑证据**：`./mvnw -o validate -Pdev` 与 `-Pprod` 均输出
  `[WARNING] The requested profile "dev" could not be activated because it does not exist.`
  而 `-Pnative` 正常。`./mvnw help:all-probes` 实列结果为 `github`（来自 settings.xml）、`native`、`nativeTest`（来自 pom），**没有 dev / prod**。
- **为什么这条特别危险**：`-P<不存在的profile>` 在 Maven 3.9 下**只是 WARNING、构建照常成功**，使用者会以为「优化构建」跑过了，实际产出的是普通 jar。这一点已写进两版文档的警告块。
- **顺手核实且确认无误、未改动**：Java 21 与 `pom.xml` 的 `<java.version>21` 一致；Spring Boot 3.3.13 与 parent 声明及运行中后端的 `/api/version` 一致；双语版本顶部互相链接齐全；两版各 27 条 TOC 锚点**全部解析成功、零断链**。
- **结构仍完全对应**：13 个 H2、36 个 H3、43 个 H4、130 个围栏、38 行表格，EN/ZH 六项指标逐一对齐。行数相差 3（ZH 末尾多一条回链块 + 中文折行更紧凑），非内容差异。重复锚点（`windows`/`macos`/`linux` 等）是 GitHub 对同名标题的标准 `-1` 后缀行为，两版对称，非缺陷。

**未解决问题**

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **三 SDK 一致缺口：Observation 的 9 个字段、`DatabaseStats.projectPath`、`/api/projects` 的 V18 `sources`/`projectsBySource` 四个 SDK 均未建模**——一致取舍而非分歧，按既定决策留待覆盖三个 typed SDK 的轮次统一处理。
4. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司 或 (b) 统一为社区贡献者。
5. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
6. push 权限阻塞（`wubuku` 403）。
7. 并行巡检进程争写状态文件。
8. `CLAUDE.md` 被 `.gitignore` 忽略。


---

## 第 152 轮 — 2026-10-02（JS/TS SDK + API 文档）

**预检**：`/api/health` = `status=ok`；轮前 `doc-growth-check.sh` exit 0；轮前指纹 `7c7e1359…` 与基线一致。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | 后端在线 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `7c7e1359…` → `20de205f…`（TS 源码属代码路径），触发完整验收 |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| JS SDK 单测 | ✅ 216 | `npm test`（原 215，+1） |
| JS SDK tsc / build | ✅ | `npm run lint`（tsc --noEmit）无输出；CJS/ESM/DTS 三份产物构建成功 |
| JS SDK E2E | ✅ 27 / 27 | `bash scripts/js-sdk-e2e-test.sh` |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**JS/TS SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| SDK-4 | `ObservationsResponse` + `parseObservationsResponse()` | **`listObservations()` 伪造了两个后端从不返回的字段**。活体确认 `GET /api/observations` 只返回 `{items, hasMore}` 两个键（22415 条项目上复测一致），而接口把 `offset`/`limit` 声明为**必填 `number`**，解析器用 `?? 0` 填充——调用方请求 `limit=20` 却读回 `0`。讽刺的是同一个接口里**偶尔**会出现的 `total` 反而是可选的，接口自相矛盾 | `offset`/`limit` 改为可选，且仅在服务端真的返回时才透传，缺失即保持 `undefined` 而非伪造 0 |

**SDK-4 的证据链**

- **活体取证**：`/api/observations?project=…&limit=7&offset=0` 返回的键集合为 `['hasMore','items']`，确认无 `total`/`offset`/`limit`。
- **测试夹具本身就是问题的一部分**：`listObservations` 块里其余用例的 mock 响应**都凭空带了 `offset`/`limit`**，所以伪造值在测试里看起来像是验证过的。新增用例改用真实 wire 形状 `{items, hasMore}`，断言两字段为 `undefined`——**退回旧解析器即失败**（`expected +0 to be undefined`）。
- **文档是对的，SDK 是异类**：`docs/API.md:1328-1362` 把该响应记录为 `{items, hasMore}`，从未声称有那三个字段；Go SDK 同样保留这三个字段，但带明确注释 `// Not returned by backend; for local use only`。**只有 JS 把它当成真实必有值且未加任何说明**。改为可选后与两者一致。
- **一处过程修正**：新用例最初插入时锚点字符串在文件中出现两次，误落进 `search` 块；发现后已移入 `listObservations` 块并复跑确认位置正确。

**JS SDK 其余部分：核查过但确认无问题**

- **跨 SDK 重试语义一致性**（此前从未显式核对过）：`errors.ts` 把 `TypeError`（fetch 网络错误）与 `AbortError`（超时中断）都归为可重试；Go 的 `IsRetryable` 对非 HTTP、非哨兵错误一律返回 true；Python 的 `is_retryable_error` 对 `RequestException`（含 `ConnectionError`/`Timeout`）返回 true。三者一致，且 Python 的注释已显式记录「Matches Go's IsRetryable(err) and JS's isRetryable(err) for cross-SDK parity」。
- `doFetch` 的双层响应大小防护（读前 Content-Length + 读后兜底）与第 141 轮一致；`resolveConfig` 的取值钳制（timeout/maxRetries/retryBackoff 下限 100、baseURL 去尾斜杠）与 Go/Python 对齐。
- `parseObservation` / `parseExperience` 对后端**混合命名**（snake_case 与 camelCase 并存）均用 `firstNonNullOr` 双键读取，已用活体响应对照 `created_at`/`quality_score`/`reuse_condition`/`fell_back`/`has_more` 逐项验证。
- JS SDK 无 `listSummaries` 方法，但后端 `/api/summaries` 也不在四 SDK 共同覆盖的 23 端点范围内，属一致取舍而非缺口。
- 四个 `?? 0` / `?? false` 默认值全部复查过，均作用于后端确实返回的字段（`strategy`/`count`/`hasMore`/`fellBack`），无第二处伪造。

**文档方向（API 文档）：未发现缺陷（已核实，非跳过）**

- **`/api/observations` 响应**：文档记录为 `{items, hasMore}`，与活体逐字段一致——文档本来就是对的。
- **`/api/search` 的 `limit`**：文档写「Max results (max 100)」、示例 `"count": 10` 配 `limit=10`。第 149 轮修复前该示例**实际会返回 20**（2 倍缺陷），修复后代码才与文档一致。
- **`/api/context/preview` 的 9 个参数**：`observationTypes`/`concepts`/`includeObservations`/`includeSummaries`/`maxObservations`/`maxSummaries`/`sessionCount`/`fullCount` 的名称、类型、默认值（`""`/`true`/`true`/`50`/`2`/`10`/`5`）与 `ContextController.java:305-330` **逐项吻合**。
- **全部端点默认值系统比对**：脚本提取 Controller 的 26 个 `@RequestParam(defaultValue=…)` 与 `API.md` 表格的 Default 列比对，初筛 3 处「不符」经逐条追查**全是我脚本的取列错误**（`''` vs `""` 的引号写法差异；`currentSessionId` 那行我的正则把 Description 列当成了 Default 列），**无真实不符**。
- **`platformSource` 参数活体验证**：`?platformSource=claude` 返回 3 条且 `platform_source` 全为 `claude`；`codex` 与 `bogus` 均返回 0 条，`ViewerController:104` 确认参数真实接线。
- **`/api/modes`（复数）响应示例**：`{id, name, description, version, observation_types, observation_concepts}` 与活体逐字段一致。

**未解决问题**

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **三 SDK 一致缺口**：Observation 的 9 个字段、`DatabaseStats.projectPath`、`/api/projects` 的 V18 `sources`/`projectsBySource` 四个 SDK 均未建模。第 152 轮再次确认这在 JS 侧同样存在（`parseModesResponse` 之外无遗漏）。按既定决策仍不在单轮制造新差异。
4. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
5. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围或安排拆分。
6. push 权限阻塞（`wubuku` 403）。
7. 并行巡检进程争写状态文件。
8. `CLAUDE.md` 被 `.gitignore` 忽略。


---

## 第 153 轮 — 2026-10-02（Demo + SDK README）

**预检**：`/api/health` = `status=ok`；轮前 `doc-growth-check.sh` exit 0；轮前指纹 `20de205f…` 与基线一致；37778–37781 全关。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | 后端在线 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ **未变** | `20de205f…`（本轮仅改 `.md`）→ 按门控规则**跳过完整验收、基线不推进** |
| Python Demo E2E | ✅ 27 / 27 | `bash scripts/python-demo-e2e-test.sh`（本轮启动 37780，测完已停） |
| Demo 活体探测 | ✅ 24 端点 | 逐路由实跑，200/400/404 全部正确，**无 500** |
| Demo 端口 | ✅ 已清理 | 37778/37779/37780/37781 全关 |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Demo 方向：未发现缺陷（已核实，非跳过）**

审查对象：Python Demo（`examples/http-server/app.py`，648 行，37780）——第 148 轮深审过 Go Demo，Python Demo 内部此前未细看。

- **路由与 README 双向零差异**：`app.py` 26 条注册、README 26 行表格，按 `{}` 归一化后**双向零差异、无重复注册**。26 条对应 24 个唯一路径（`GET /observations/<observation_id>` 与 `PATCH|DELETE /observations/<obs_id>` 归一后同路径）。
- **与 Go Demo 规模一致**：Go Demo `mux.HandleFunc` 也是 24 个唯一端点。差异仅为命名风格（Python `/observations/batch`、`/observations/create` vs Go `/batch-observations`、`/create-observation`），各自内部自洽且均有文档，非缺陷。
- **活体逐路由探测**：`/health`、`/version`、`/modes`、`/settings`、`/projects`、`/stats`、`/quality`、`/search`、`/observations` 全部 200；`/observations/does-not-exist-1234` 正确 404；缺必填参数正确 400（`project is required`、`template is required`）；缺 `Content-Type` 的写路径正确 400。**24 个端点无一返回 500**。
- **错误处理链正确**：`@app.errorhandler` 按 `APIError`（透传 `exc.status_code`，`[400,600)` 越界回落 502）→ `CortexError`（400）→ `Exception`（500 JSON 而非 Flask 默认 HTML）→ `413` 的顺序，Flask 按最具体类型选取，无遮蔽问题。
- **`/quality` 的 `total` 字段不是伪造**：`QualityDistribution.total` 是 `@property`，返回 `high+medium+low+unknown` 的计算值，而后端确实不返回该字段——属正确的派生值而非 `?? 0` 式伪造（与第 152 轮 JS SDK 的问题性质不同）。
- Python Demo E2E 27/27 通过。

**文档方向（SDK README）发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-16 | Python SDK README（中英两版） | **`close()` 是 `CortexMemClient` 唯一的、完全没有出现在任何表格里的公开方法**（26 个公开方法，表格覆盖 25 个）。JS README 已在 System 表列出 `close()`/`toString()`，属 Python 独有的遗漏 | 新增 `### Lifecycle` 表列出 `close()`，并说明 close 后所有方法抛 `CortexError("client is closed")`、客户端同时支持上下文管理器 |
| DOC-17 | 四个 SDK README（中英两版） | **`session=` 参数的归属语义与安全理由完全没有文档化**。第 146 轮修复的核心（请求头逐次传递、绝不写入调用方共享的 `session.headers`，否则 API key 会泄漏给无关主机；`close()` 不关闭仍归调用方所有的会话）**只存在于 `client.py` 的代码注释里**，任何读 README 的人都看不到 | Python 两版新增 `### Session ownership` 小节；Go 两版在 API 覆盖表补 `Close`/`String` 行（`Close` 此前只作为用法示例里的 `defer client.Close()` 出现过，不在表中） |
| DOC-18 | Go SDK README（中英两版） | `Client` 接口声明了 `Close` 和 `String`，但 API 覆盖表两者都没有 | 同上，补 `Lifecycle` 类别行 |

**DOC-16~18 的核实**（每条断言都对着运行中的代码验证过，不照着实现写）

- **错误信息逐字核对**：`_assert_not_closed` 抛的正是 `CortexError("client is closed")`；实跑 `search`/`get_version`/`get_modes`/`list_observations` 四个方法，确认 close 后全部抛出该异常，`repr` 显示 `CortexMemClient('http://127.0.0.1:37777', closed)`。
- **上下文管理器实跑**：`with CortexMemClient(...) as c:` 块内 `health_check()` 正常，块退出后 `repr` 变为 `closed`，再次调用即抛 `client is closed`。
- **借用会话语义实跑**：传入自带 `X-Mine: keep-me` 的 `requests.Session`，调用 `get_version()` 后**该会话头原封不动**（证明请求头逐次传递、未写入会话），且 `client.close()` 之后**该借用会话仍可正常发起请求**（证明未被 SDK 关闭）——与新写入文档的表述一致。
- **一处需要如实标注的细节**：我原本想用「close 后自建会话不可用」来对照证明差异，实测发现**自建会话 close 后同样可用**——因为 `requests.Session.close()` 只关连接池，后续请求会透明重建。因此两者的差别在**机制**（是否调用 `session.close()`）而非**可观测行为**。文档的措辞已按机制描述，未夸大可观测差异。
- **双语结构对齐**：Python 两版各 21 个标题 / 10 个围栏 / 8 个 H3；Go 两版各 11 个二级标题 / 19 行表格。四个 README 的相对链接全部可解析。

**未解决问题**

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **三 SDK 一致缺口**：Observation 的 9 个字段、`DatabaseStats.projectPath`、`/api/projects` 的 V18 字段四个 SDK 均未建模。按既定决策仍不在单轮制造新差异。
4. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
5. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围或安排拆分。
6. push 权限阻塞（`wubuku` 403）。
7. 并行巡检进程争写状态文件。
8. `CLAUDE.md` 被 `.gitignore` 忽略。


---

## 第 154 轮 — 2026-10-02（Backend + 设计文档）

**预检**：`/api/health` = `status=ok`；轮前 `doc-growth-check.sh` exit 0；轮前指纹 `20de205f…` 与基线一致。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | 后端在线 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `20de205f…` → `a09a2cf…`（Java 源码属代码路径），触发完整验收 |
| 回归测试 | ✅ 45 / 0 | `bash scripts/regression-test.sh --skip-build`；**另跑一次不带 `--skip-build` 得 45/0/0、总计 45**（用于核实文档数字） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| 后端单测 | ✅ 8 / 0 | `ContextCacheServiceTest`（签名未变，原测试全绿） |
| Demo V14 测试 | ✅ 4 / 0 | 启动 Java Demo 37778 实跑，**用于核实文档的「4/4」声明**，测完已停 |
| 活体验证 | ✅ | 两会话项目连续 3 次 `/api/session/start`，context 长度稳定、无错误 |
| Demo 端口 | ✅ 已清理 | 37778/37779/37780/37781 全关 |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Backend 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| BE-4 | `SessionRepository.findByProjectPathAndStatus()` | **上下文缓存的读写两侧都靠「无序查询的第 0 行」决定哪个会话持有项目级缓存**。`ContextCacheService.getContextIfFresh`（读）与 `SessionController.cacheContextForProject`（写）是两次独立查询，各自 `sessions.get(0)`；而该查询是**无 `ORDER BY` 的派生方法**，SQL 不保证行序，`mem_sessions` 上任何 UPDATE 都可能改变谁排在最前。两侧可能落到不同会话 → 缓存键不确定 → 本应避免重复生成上下文的「快速路径」静默退化为持续 miss | 改为显式 JPQL，`ORDER BY s.startedAtEpoch DESC`，与同一 repository 中既有的 `findByProjectPathOrderByStartedAtEpochDesc` 约定一致；两侧从此确定性地选中最近启动的活跃会话。方法签名不变，调用点与 `ContextCacheServiceTest` 不受影响 |

**BE-4 的定性：潜在非确定性，已修，但未复现出实际失败**

这一条必须如实说明，不能拔高成「已证实的 bug」：

- **已核实的事实**：查询确无 `ORDER BY`；读、写两侧确实各自取 `get(0)`；同文件确有带 `startedAtEpoch DESC` 的姊妹方法，排序是本仓库的既定约定。
- **未能复现**：我**没有**拿到活的反例。`GET /api/session/{id}` 不暴露 `cached_context`，无法从 API 侧看出缓存落在哪一行；缓存命中/未命中的日志是 DEBUG 级而服务跑在 INFO 级，看不到；且本实例上当前**没有任何项目存在多于一个活跃会话**（逐项目查 `/api/session` 均为 0 活跃，虽然我确实成功造出了两个同项目活跃会话，但观测面不足以证明缓存因此丢失）。
- **仍然修的理由**：「必须稳定取多行结果的第 0 行」时查询必须有确定顺序，这没有判断空间；而排序方向沿用同文件既有约定，不引入新约定。所以无论能否复现，这个改动都是安全的。
- **实测到的可观测影响范围**：即便发生，表现为**缓存 miss 抖动**（重复生成上下文、更慢的 session 启动），**不是返回错误数据**。
- **验证**：`ContextCacheServiceTest` 8/8；重建 jar 重启后启动日志**无 `QuerySyntaxException`**（Spring Data 在启动期校验命名查询，JPQL 写错会直接启动失败）；两会话项目连续 3 次 `/api/session/start` 返回稳定 context 长度、无异常；日志中的 4 处 "ERROR" 经查全是 INFO 行内出现的 ERROR 字样（Logback 传播 ERROR 级别设置），**无真实错误**。

**顺带核实（无误，未改动）**：`AGENTS.md` 声称存在 `GET /api/sessions`（List sessions）——活体返回 404，`SessionController` 只映射 `/api/session/start`、`/api/session/{id}`、`/api/session/{id}/user`，全仓唯一的 `/sessions` 路径是 `POST /api/import/sessions`。该文件被 gitignore，修正无法通过提交传播，故仅记录。

**文档方向（设计文档）发现与处理**

审查对象：`docs/drafts/phase-3-design/25.md`（实施计划，43.5KB，本方向最大且第 144/149 轮未覆盖）、`26.md`、`19.md`。

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-19 | `25.md` / `26.md` / `19.md` 共 10 处 | **`regression-test.sh` 的「Expected: 46/46 tests passed」与实测不符**。实跑不带 `--skip-build` 得 **Passed 45 / Failed 0 / Skipped 0 / Total 45**；只有带 `--skip-build` 时摘要才显示 46，因为 `build_app()` 会调 `log_skip "Skipping build"`，而摘要的 `Total = PASSED + FAILED + SKIPPED` 把**被跳过的构建步骤算作一条**。也就是说文档把一个构建步骤当成了第 46 个测试；而按巡检惯例恰恰总是用 `--skip-build` 跑，读者核对时会看到 46 并以为文档没错 | 10 处全部改为 45/45，并在每个文件首处附一段简短说明解释 45 与 46 的差异，避免后续轮次「改回去」 |
| DOC-20 | `25.md` | **`phase3-acceptance-test.sh` 的「Result: 15/15 tests passed」已过时**——脚本已增长到 **25** 个测试，本仓历次验收均为 25/25 | 改为 25/25（「15 tests total」一并更正） |

**DOC-19/DOC-20 的核实（先实跑再改）**

- **两个数字都实跑验证，不靠推断**：`bash scripts/regression-test.sh`（不带参数）→ `Passed 45 / Failed 0 / Skipped 0 / Total 45`；`phase3-acceptance-test.sh` → `25/25`。
- **46 的来源查清了**：`scripts/regression-test.sh:1406` 的 `log_skip "Skipping build (--skip-build)"` 会 `TESTS_SKIPPED++`，而 `:1438` 的 `Total` 正是三者相加。
- **一处曾疑似、核实后确认正确故未动**：`25.md` 对 `demo-v14-test.sh` 的「Expected: 4/4 tests passed」。该脚本在 Demo 未运行时只会 SKIP，所以本轮**启动了 Java Demo（37778，`mvn spring-boot:run -Plocal`）实跑**——`Passed 4 / Failed 0`，**声明属实**，保留原样。测完已停，端口已清理。
- 修正后全目录 `grep` 确认 `46/46` 与 `15/15` **零残留**，13 处数字为 45/45 或 25/25。

**未解决问题**

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **`AGENTS.md` 声明的 `GET /api/sessions` 端点不存在**（本轮新发现）——该文件被 gitignore，修正无法经提交传播，需用户决定是否取消忽略。
4. **三 SDK 一致缺口**：Observation 的 9 个字段、`DatabaseStats.projectPath`、`/api/projects` 的 V18 字段四个 SDK 均未建模。按既定决策仍不在单轮制造新差异。
5. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
6. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围或安排拆分。
7. push 权限阻塞（`wubuku` 403）。
8. 并行巡检进程争写状态文件。
9. `CLAUDE.md` 被 `.gitignore` 忽略。


---

## 第 155 轮 — 2026-10-02（Java SDK + 架构文档）

**预检**：`/api/health` = `status=ok`；轮前 `doc-growth-check.sh` exit 0；轮前指纹 `a09a2cf…` 与基线一致。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | 后端在线 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `a09a2cf…` → `413276f…`（Java SDK 源码属代码路径），触发完整验收 |
| 回归测试 | ✅ 45 / 0 | `bash scripts/regression-test.sh --skip-build` |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Java SDK 单测 | ✅ 175 | 三模块全绿（client 122 / spring-ai 46 / starter 7），原 174 |
| 退回验证 | ✅ 精确命中 | 退回生产修复后 88 个用例中**恰好 1 个失败**（新增的失败路径用例） |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Java SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| SDK-5 | `CortexMemClientImpl.getModes()` | **失败时返回一个真实响应里根本不存在的键**：`Map.of("modes", List.of())`。而 `/api/modes` 实际返回的是**单个 mode 对象**（`id`/`name`/`description`/`version`/`observation_types`/`observation_concepts`，已活体确认）。于是失败值的形状是**任何响应（成功或失败）都不曾有过的形状**，且**完全没有错误标记**，调用方无法区分「后端不可达」与「未配置模式」 | 失败值改用端点真实返回的键（读 `observation_types`/`observation_concepts` 得到空列表而非 null），并补 `error` 标记，与同文件既有的 `search()`、`getSettings()` 约定一致 |

**SDK-5 的证据链**

- **让缺陷长期潜伏的正是测试夹具**：`getModes_returnsResult` 的 mock 体是 `{"modes":[{"name":"default"}]}`，**与端点真实 wire 形状不符**，而断言恰恰是 `containsKey("modes")`——**生产代码的错误与测试的错误朝同一方向，于是彼此印证**。这与第 152 轮 JS SDK 的情况同类。
- **修复后的夹具改用活体形状**，断言 `id`/`name`/`observation_types`/`observation_concepts` 存在且 `modes` 不存在；新增 `getModes_onFailure_keepsRealKeysAndFlagsTheError` 用 503 覆盖失败路径。**退回生产修复后 88 个用例中恰好 1 个失败**（正是新增用例）。
- **横向盘点了本文件的失败约定**（这部分只记录未改）：`search()` 返回成功形状 + `error`（最佳范式）、`getStats()` 返回 `{error, fell_back}`、`getSettings()` 返回 `{settings, error}`、`getVersion()` 返回 `{service:"unknown", version:"unknown"}`、`retrieveExperiences()`/`getObservationsByIds()` 返回 `List.of()`、`getProjects()` 返回 `{projects: []}`（键真实但无错误标记）、`buildICLPrompt()`/`getQualityDistribution()`/`listObservations()` 返回各自的类型化空值（无标记）。**`getModes()` 是其中最差的一处**——既虚构键又无标记，故只修它；其余属 lesser 的不一致，留待后续。
- **过程中的一次操作失误**：首次做退回验证时用基于索引的字符串手术改文件，结果破坏了结构（编译报「隐式声明的类」）。已改用精确编辑重做，恢复后 88/88 通过才继续。

**P1-1 复核：维持第 144 轮「记录不修」的决定**

本轮重新阅读 `CortexSessionContextBridgeAdvisor.adviseStream()` 全文以确认该结论仍然成立。`begin()` 在调用线程写 ThreadLocal，`end()` 放在 `flux.doFinally`（Reactor 在终止信号线程执行），三个已知方案（Reactor Context / 退回同步装配 / 改 Spring AI 工具调用链）**都会改变并发语义**；且无法确定无 `subscribeOn` 时真实模型调用是否同线程（若是，则现状并非全错，抽掉可能反而弄坏本来能工作的场景）。**在无法确定收益的前提下改动并发语义是不负责任的**，维持记录 + 双语 README 说明规避方式（改用 `.call()`）的处置。

**文档方向（架构文档）：未发现缺陷（已核实，非跳过）**

- **中英完全对称**：各 1106 行、42 个围栏、13 个 H2 / 23 个 H3 / 12 个 H4、106 行表格，且 **48 个标题的层级序列逐位一致**。第 145 轮修复的围栏缺失保持有效，未回退。
- **API 分层表无幻影**：表中 27 个路径模式**每一个都能匹配到生效端点**，零虚构。41/62 个端点由通配行（如 `/api/ingest/*`）覆盖；余下 4 个（`/api/import`、`/api/logs/clear`、`/api/mode`、`/stream`）经查是我的匹配器对通配/分节归属的处理所致——`/api/mode` 由 `/api/mode/*` 行覆盖、`/stream` 属 SSE 另一节、`/api/logs/clear` 属 Logs 控制器，均非文档缺口。该表是**分层概览**而非端点全清单，全清单由 `docs/API.md` 承担（第 147 轮已核 67/67）。
- **唯一的方法数声明核实为正确**：Viewer 行称「15 methods / 15 个方法」，实际是 11 个单方法端点 + 2 组 `(GET/POST)` = **15**，中英两版都对。
- 两份文档均**未**声明 `/api/modes` 的响应字段，因此 SDK-5 的错误形状没有在架构文档里留下同源错误。

**未解决问题**

1. **P1-1 流式会话不传播**（第 144 轮记录，本轮复核维持不修）。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **`AGENTS.md` 声明的 `GET /api/sessions` 端点不存在**（第 154 轮记录）——该文件被 gitignore。
4. **Java SDK 其余方法的失败返回无统一约定**（本轮盘点记录）：`getProjects()` 的 `{projects: []}` 键真实但无错误标记；`retrieveExperiences()`/`getObservationsByIds()` 等返回空列表无标记。属 lesser 不一致，未在本轮扩大范围。
5. **三 SDK 一致缺口**：Observation 的 9 个字段、`DatabaseStats.projectPath`、`/api/projects` 的 V18 字段四个 SDK 均未建模。按既定决策仍不在单轮制造新差异。
6. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
7. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围或安排拆分。
8. push 权限阻塞（`wubuku` 403）。
9. 并行巡检进程争写状态文件。
10. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Java SDK 完成（新循环第八轮），下一方向 Go SDK；文档审查轮换推进：架构文档完成（五十六轮），下一方向 用户指南。

## 第 156 轮 — 2026-10-02（Go SDK + 用户指南）

### 代码方向：Go SDK

**GO-1（P1）`Close()` 会清空调用方自带的 `http.Client` 连接池。**

`client_methods.go` 的 `Close()` 无条件对 `c.config.HTTPClient.Transport` 调用
`CloseIdleConnections()`，而 `WithHTTPClient` 的文档注释明写 "caller owns the
http.Client"——该客户端通常与调用方自身业务共用。清空它会让此后**经由该客户端发出
的每一次请求**（包括调用方自己发的）被迫重新建连。

用计数 DialContext 实测：第一次请求建连 1 次 → `Close()` → 调用方自己再发一次请求，
旧代码建连 **2** 次（池被清空），修复后为 **1** 次。

Python SDK 早已有这道所有权闸门（`if self._owns_session: self._session.close()`），
Go 是四个 SDK 里唯一的例外。修复：`NewClient` 记录客户端是否由自己构建
（`ownsHTTPClient`），`Close()` 只释放自建客户端的池。

**GO-2（原测试把缺陷钉死）。** `TestClose_CleansUpIdleConnections` 用的**正是借来的
客户端**，并断言 `Close` 清理了它的空闲连接——测试与生产代码朝同一方向错。已改名为
`TestClose_IsIdempotent`，只断言 `Close` 可重复调用不报错；所有权断言移到新用例。

**GO-3（P2）`WithMaxRetries` 的文案与语义差一次。** 代码是
`for attempt := 1; attempt <= MaxRetries`，日志字段名也自己写着 `maxAttempts`，
但注释与两版 README 都说「最大重试次数」。实测：1→1 次、2→2 次、3→3 次请求。
四个 SDK 实际都是计「尝试次数」：Python docstring 写 "Attempts"、Java 直接取
`maxAttempts` 属性。**因此改文案而非改行为**，并加 `TestWithMaxRetries_CountsTotalAttempts`
钉住该语义，防止将来被"修"成真正的 retries。

Go SDK 测试 225 → **228**（core）；dto 61、genkit 13、langchaingo 12、eino 8、
http-server 示例 13，合计 335。`gofmt -l` 无输出、`go vet` 通过、六个模块全绿、
`go-sdk-e2e-test.sh` **39/39**（Go Demo 起于 37779，本轮结束后已停止）。

**核查后确认无误、未改**：ExtractionController 的 `projectPath`/`userId` 参数名、
`/api/memory/experiences` 的 Experience 六个字段（活体比对一致）、
`/api/session/start` 与 `/api/session/{id}/user` 响应键（活体一致）、
`StringList` 的双形态解析（后端确实发 `"[]"` 字符串）、
`jitteredBackoff` 的 ±25% 区间（`rand.Int63n(base/2)` 确为 [-0.25base, +0.25base)）、
`doFireAndForget` 的重试范围。

### 文档方向：用户指南

**DOC-1（P1）`docs/api-json-naming-convention.md` 七处失实。** 该文件自标
"Last verified 2026-03-29" 并在结尾要求 "Always verify with curl"，故逐条实测：

| 原文档 | 实测 |
|--------|------|
| `GET /api/observations/{id}` 存在 | **404**，无此端点；单条走 `POST /api/observations/batch` |
| `GET /api/session/info` 存在 | **404**；SessionController 只映射 `/start`、`/{id}`、`/{id}/user` |
| `PATCH /api/session/{id}` 返回实体 | **405**；PATCH 在 `/{id}/user`，返回 `{status, sessionId, userId}` |
| observation 键 `project_path` | `project` |
| observation 键 `content` | `narrative` |
| observation 键 `extracted_data` | `extractedData` |
| `/api/search` 返回 `results`/`result_count`/`algorithm` 等 | 返回 `observations`/`count`/`strategy`/`fell_back`，其中 `fell_back` 是唯一的 snake_case 键 |

三个键名错误同源：`ObservationEntity` 对 `project`、`narrative`、`extractedData`
都加了 `@JsonProperty` 覆盖，因此该实体是**混合**而非统一 snake_case。已在 Rule 1
加警告、逐端点补实测键集，并新增 Corrections 小节逐条对照，便于后续复核。

**DOC-2（P2）`docs/swagger-annotation-guidelines.md` 注解统计不可复现。**
原表未说明统计方法，多列偏低（MemoryController `@Schema` 记 0、实为 11+7；
ViewerController `@ApiResponse` 记 27、实为 22）。改为可复现口径：剥离注释后统计
**出现次数**，`@Schema` 拆为「字段级」与 `implementation=` 两列。合计行已用脚本与
13 行逐行求和校验一致。方法本身有交叉验证：`@Operation` 合计 **67**，与既往轮次
独立统计的 67 个生效端点吻合。

**DOC-3 核实后确认无误、未改**：
`docs/TESTING.md` 的「phase3-acceptance-test.sh（15 test functions）」——脚本确有
**15** 个 `test_*()` 函数定义（该脚本摘要报 25 是含子断言，两者不矛盾）；
`run-all-e2e.sh`「10 套」——确为 10 套且确实排除 Docker 与 test-llm-provider；
`seed-diverse-data.sh`、`run-all-e2e.sh` 两个被列脚本均存在；`.github/workflows/`
只有 `docker.yml`；TESTING.md 中英 22 个标题层级逐位一致；WebUI 契约里的
`usePagination.ts` 确实存在于 `webui/src/ui/viewer/hooks/` 且第 77 行读 `data.hasMore`。

**DOC-4** `docs/drafts/patrol-task.md` 的实施基准四行全过时（Java 121→175、
Go 288→335、Python 374→385、JS 212→216），已按本轮实测全部更正。

### 验收

指纹 `413276f4…` → `c6b75615…`（Go 代码有改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**（跳过项是
`build_app` 的 "Skipping build"，故摘要 Total 显示 46）；`EXTRACTION_ENABLED=true
phase3-acceptance-test.sh` **25/0/0**。全通过，基线推进至 `d5a6437` / `c6b75615…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，本轮维持不修）。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
4. **JS SDK 的 `maxRetries` 文案同样是 "Max retries"**（本轮发现，未改）——
   语义与 Go 一致为 attempts，但本轮方向不含 JS SDK，留待其轮次处理。
5. **Java SDK 其余方法的失败返回无统一约定**（第 155 轮记录）。
6. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
7. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Go SDK 完成（新循环第九轮），下一方向 Python SDK；文档审查轮换推进：用户指南完成（五十七轮），下一方向 API 文档。

## 第 157 轮 — 2026-10-02（Python SDK + API 文档）

### 代码方向：Python SDK

**PY-1（P1，数据丢失）`_to_str_list` 只接受真实数组，丢弃了后端实际发送的形态。**

后端为 WebUI 把 `mem_observations` 的 JSONB 列序列化成 **JSON 编码的字符串**，
但 `dto.py` 的 `_to_str_list` 只做 `isinstance(v, list)` 判断，其余一律返回 `[]`。

活体 A/B 取证（修复前）：

```
RAW wire concepts : '["allergy","peanut"]'
SDK concepts      : []
```

`facts`、`concepts`、`files_read`、`files_modified`、`refined_from_ids`
在**每一条实际有内容的观察记录上**都是空的。该问题命中全部三条读路径
（`get_observation(s)`、`search`、`list_observations`），因为它们共用
`Observation.from_wire`。Go 早已用 `StringList` 处理双形态，JS 用
`safeStringOrStringList`，**Python 是唯一没做的**。

修复：`_to_str_list` 先尝试 JSON 解码字符串，保留原数组路径；非 JSON 字符串
按逗号切分降级（与 JS 一致），JSON 解码出来不是列表的仍返回默认值。
用 POST/GET 往返确认后，`list_observations`、`search`、`batch` 三条路径
均返回真实 concepts。

**PY-2（原测试把缺陷写成了预期）。** 两个既有测试固化了错误行为：
- `test_to_str_list_with_string_instead_of_list` 断言 `_to_str_list("not a list") == []`，
  docstring 写「if backend returns a string instead of array, return default」——
  它背后的假设正是被证伪的那一条。已按真实、有据的行为改写。
- `test_observation_from_wire_defensive_list_parsing` 拿**字符串**当「错误 wire 类型」
  的例子。它的防御意图对数字/字典/布尔仍然成立，因此保留那三类，字符串另立用例。

退回 `_to_str_list` 旧实现后，389 个测试中**恰好 3 个失败**。Python SDK
385 → **389**，全绿；`python-sdk-e2e-test.sh` **28/28**。两版 README 补上
该 wire 形态说明（此前只有 Go SDK 有）。

**核查后确认无误、未改**：26 个公开方法中 25 个调用 `_assert_not_closed`
（唯一例外是 `close()` 本身，幂等语义要求它必须能调用）；`max_retries` 的
docstring 写的是 "Attempts"，**语义正确**——Go 是四个 SDK 里唯一把 attempt
写成 retry 的（第 156 轮已修）；`/api/projects` 的 `projects`/`sources`
确实是真实数组，未受本缺陷影响；`/api/modes` 的 `observation_types`/
`observation_concepts` 也是真实数组。

### 文档方向：API 文档

**DOC-1（P1）观察记录响应字段表三处失实**，且其中第一处正是 PY-1 的文档根因。

1. `facts`/`concepts`/`files_read`/`files_modified`/`refined_from_ids` 标注为
   `string[]`，实际是 JSON 编码的字符串。**请求侧确实接受真实数组**（已用
   POST/GET 往返验证），所以这份文档「一侧对、一侧错」，不易被发现。
2. 响应字段写作 `session_id`，实际 wire 键是 `content_session_id`
   （V13 `@JsonProperty` 覆盖）。请求侧的 `session_id` 别名仍然有效，未动。
3. 缺 10 个线上真实返回的字段：`content_hash`、`discovery_tokens`、
   `relevance_count`、`generated_by_model`、`step_number`、`embedding_model_id`、
   `user_comment`、`refined_from_ids` 及三个 `embedding_*` 向量列。

**这正是 PY-1 的文档根因**——`_to_str_list` 只认真实数组，很可能就是照着这份
`string[]` 写的。修正文档即拆掉了下一个 SDK 作者会踩的坑。

两版随后**重新对齐**：31 个响应字段现以相同顺序、相同类型出现（此前我的英文表
与中文表顺序不一致，违反仓库的双语一致性要求）。脚本校验结果：键集合相同、
顺序相同、类型零差异、无「文档有而线上无」、无「线上有而文档无」。

**两处自查纠错**（均在提交前被结构检查拦下）：
- 第一次替换英文表时，旧表末尾 5 行成了新表下方的孤立行；
- 第一个重排脚本从中文文件里**删掉了 33 行**。已从事先备份恢复，
  用带行数与重复键断言的实现重做，确认表格行数 285 未变。

**DOC-2 核实后确认无误、未改**：`docs/API.md` **没有**收录上一轮发现的两个
幻影端点（`/api/observations/{id}`、`/api/session/info`；文中出现的
`/api/memory/observations/{id}` 是真实端点）；`project_path` 的出现均在
**请求**侧表格中，位置正确；不可 JSON 解析的示例块均为既有的 `...` 占位摘要块，
与本次改动无关（已确认 diff 未触及）。

### 验收

指纹 `c6b75615…` → `eb7ba4ea…`（Python 代码有改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**
（跳过项为 `build_app` 的 "Skipping build"，故摘要 Total 为 46）；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `5d3c7b3` / `eb7ba4ea…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
3. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
4. **JS SDK 的 `maxRetries` 文案同样是 "Max retries"**（第 156 轮发现，未改）。
5. **Java SDK 其余方法的失败返回无统一约定**（第 155 轮记录）。
6. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
7. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Python SDK 完成（新循环第十轮），下一方向 JS SDK；文档审查轮换推进：API 文档完成（五十八轮），下一方向 SDK README。
