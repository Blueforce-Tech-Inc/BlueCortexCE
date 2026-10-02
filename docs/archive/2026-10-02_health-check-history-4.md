## 巡检历史

> **归档**: 2026-04-08 ~ 2026-10-01 00:53 的完整历史报告（58 轮）已迁移至 [`2026-10-01_health-check-history.md`](../archive/2026-10-01_health-check-history.md)（创建后只读）。
>
> **归档 2**: 2026-10-01 05:03 ~ 05:21 的循环轮报告（52 条）已迁移至 [`2026-10-01_health-check-history-2.md`](../archive/2026-10-01_health-check-history-2.md)（创建后只读）。

### 压缩摘要（归档内容概览）

| 阶段 | 覆盖范围 | 关键结果 |
|------|---------|---------|
| 2026-04-08 ~ 2026-05-06 | 多轮健康检查 + Backend 批量修复 | 累计修复 ~65 个 Backend 问题；两次数据库事故（密码静默修改、x86_64 PostgreSQL 损坏迁移 OrbStack）；ContextCacheService 无限重试、OffsetPageRequest 分页等修复 |
| 2026-09-30 01:26 ~ 05:37 | 六方向代码审查第一轮 + 文档五方向 | API 文档中英结构重排；巡检 Demo 专用端口（37778-37781）；Go Demo 404 映射修复；基线演进至 8895884 |
| 2026-09-30 05:03 ~ 09:26 | 第二循环 R1-R5（四 SDK + Demo + 用户指南/API 文档/设计文档/架构文档） | R2 Go gofmt 修复并完整验收（基线 b04ccf8）；DEPLOYMENT API 示例修正并实测；Demo 控制器计数修正 10→12；用户暂停循环 |
| 2026-09-30 22:56 ~ 2026-10-01 00:53 | 循环恢复 R6-R28（Backend/Demo/各 SDK 五~六轮 + 文档五~六轮） | R6 SSEBroadcaster 广播中断修复（基线 24faf55）；DOCKER_README 中英统一为 14 节同序；R22 PendingMessageProcessor 初始化日志修复（基线 32d5dfb）；架构文档 Express→axios 修正 |

### 最新轮次
> **归档 3**: 2026-10-01 05:26 ~ 07:35 的循环轮报告（48 条）已迁移至 [`2026-10-02_health-check-history-3.md`](../archive/2026-10-02_health-check-history-3.md)（创建后只读）。

### 未解决问题（跨轮持续）

| 问题 | 状态 | 落点 |
|------|------|------|
| `wubuku` 凭据 push 返回 403 | 环境级阻塞，非代码问题 | 每轮报告"push"行 |
| 并行巡检进程争写 `patrol-state.json` / `patrol-rotation.md` | 持续观察（机器可读状态以 `patrol-state.json` 为准） | [`patrol-state.json`](./patrol-state.json) |
| `CLAUDE.md` 被 `.gitignore` 忽略 | 其中的修正无法通过提交传播，只能本地生效 | 2026-10-02 用户指南轮 |

### 2026-10-02 10:36 | 统一维护任务（Demo 三十三轮 + 用户指南三十三轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`（服务已在 37777 运行，无需启动）；`/actuator/health` 200、`/api/version` = `0.1.0-beta` / Spring Boot `3.3.13` |
| 服务启动动作 | ➖ 无 | 首轮预检即命中已运行实例，未执行 `start.sh` |
| 文档增长检查 | ✅ OK（首末各一次） | 5 个活动文档全部低于 1000 行 / 102400 字节；`health-check-task.md` 919 行 / 69309B（更新基线后 ~945 行，仍在阈值内） |
| 代码审查方向 | ✅ Demo（三十三轮） | 发现 4 个问题，全部当场修复（见下表） |
| 文档审查方向 | ✅ 用户指南（三十三轮） | 发现 3 个问题，全部直接修复并提交 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `4d31dc3c…` → `84065a66…`（871 条记录不变，仅内容修改） |
| 完整验收 | ✅ 已执行 | 指纹变化触发完整验收，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build`；Backend 未改动，沿用现有 JAR |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Backend findings | ✅ 0 未决 | `backend-review-findings.md` 无待处理项（P2-1 仍为已跳过） |
| 构建与重启 | ➖ 不适用 | 未改动 Backend/前端，无需重建或重启；仅启动并停止了本轮的 Go/JS Demo |
| 连续 3 轮检查 | ✅ 达成 | R1 修复 3 个文档问题 → R2 修复 1 个 JS Demo 问题 → R3 无新问题、无新改动 |
| commit | ✅ `bd46f01` | 代码 + 文档修复；报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限（环境级阻塞） |

**Demo 方向发现与处理（4 项，全部当场修复）**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| D-1 | 5 个 Go 示例 `main.go` | 后端地址硬编码 `http://127.0.0.1:37777`，无法指向其他后端；Python/JS Demo 均已支持 `CORTEX_BASE_URL` | 新增 `backendBaseURL()` 读取同名环境变量，默认值不变；gofmt + build + vet + 39/39 E2E 通过 |
| D-2 | `go-sdk/.../http-server/main.go:214` | `/search` 丢弃 `?orderBy`，而 Java/Python/JS Demo 均透传（`dto.SearchRequest.OrderBy` 早已存在） | 补齐透传并更新启动横幅说明；实测 `orderBy=created_at_epoch` 生效 |
| D-3 | `go-sdk/.../http-server/main.go:911` | 优雅关闭后 `log.Fatal(ListenAndServe())` 把 `http.ErrServerClosed` 当致命错误打印并以退出码 1 结束 | 忽略 `ErrServerClosed`，仅真实错误才 `log.Fatal` |
| D-4 | `js-sdk/.../http-server/app.ts` | `/observations`、`/iclprompt`、`/extraction/history` 用 `parseInt(...) \|\| 0`，非数字入参被静默降级为 0 并返回 200（同一文件 `/search` 已返回 400；Go/Java/Python 也返回 400） | 新增 `parseIntParam()` 统一校验；实测 `limit=abc`→400、`limit=150`→400、`offset=-1`→400、`maxChars=x`→400、`limit=5`→200 |

**用户指南方向发现与处理（3 项，全部直接修复）**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| G-1 | `CLAUDE.md:363`、`docs/drafts/upstream-sync-plan.md:264` | 部署命令写 `docker compose -f docker-compose.prod.yml up -d`，但该文件在仓库中从未存在（`git log --all` 无记录，实际只有 `docker-compose.yml`） | 改为 `docker compose up -d`。注：`CLAUDE.md` 被 `.gitignore:69` 忽略，仅本地生效；`upstream-sync-plan.md` 已提交 |
| G-2 | `scripts/README.md` | 仅记录 37 个脚本中的 13 个，遗漏全部 SDK/Demo E2E（HEARTBEAT.md 要求执行的 4 个）、`run-all-e2e.sh`、`phase3-acceptance-test.sh` 等 22 个 | 补齐 Quick Reference 37 行 + SDK/Demo E2E、`run-all-e2e`、MCP Streamable、Codex Watcher、demo-v14/v15、Evo-Memory、性能、维护工具等分节，并新增 Demo 端口表；每条均以脚本头部注释与实际参数为准 |
| G-3 | `scripts/README.md` Configuration | 文档写 `export DB_PASSWORD=...`，但所有脚本读取的是 `DB_PASS`（`DB_PASSWORD` 仅作为写入 `.env` 的键出现在 Docker 脚本内部），照文档设置无效 | 更正为 `DB_PASS`，并补充 Docker 套件使用独立测试库的说明 |

**已核验但未修改的事实**：`DEPLOYMENT.md`/`-zh-CN`（1112 行对等，端口 37777/5433、compose 服务名、镜像名、Flyway 与备份/恢复命令与 `docker-compose.yml` 一致）、`DOCKER_README.md`/`-zh-CN`（14 节同序，11 项测试与 `docker-e2e-test.sh` 实际测试对应，测试端口 15432/38888 与 15433/38889 与脚本默认值一致）、`Dockerfile` 非 root + 37777 + healthcheck、日志卷 `claude-mem-logs`。

**未解决问题**：push 权限阻塞（`wubuku` 凭据 403，环境级）；API 文档中英标题风格分歧（低优先级 Pending，本轮未涉及该方向）；`CLAUDE.md` 处于 `.gitignore` 中，其修正无法通过提交传播。

代码审查轮换推进：Demo 完成（三十三轮），下一方向 Backend；文档审查轮换推进：用户指南完成（三十三轮），下一方向 API 文档。

### 2026-10-02 10:49 | 统一维护任务（Backend 三十四轮 + API 文档三十四轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；本轮改动 Backend，完整验收前先 `mvn clean package -DskipTests` + `start.sh --background` 重启，2 秒恢复 37777 |
| 文档增长检查 | ✅ OK（首末各一次） | 5 个活动文档均低于 1000 行 / 102400 字节（`health-check-task.md` 更新后 ~1010 行，见下方未解决问题） |
| 代码审查方向 | ✅ Backend（三十四轮，抽查 2 个 Service） | `RateLimitService`、`ProjectFilterService`；发现 3 项，2 项修复 + 1 项明确跳过 |
| 文档审查方向 | ✅ API 文档（三十四轮，EN/ZH 对照 + 端点全量比对） | 发现 1 项（ZH 3 张参数表列错位），已修复并加 changelog；端点覆盖与 EN/ZH 一致性本身无问题 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `84065a66…` → `f11a28ca…`（871 条） |
| 完整验收 | ✅ 已执行 | 指纹变化 + Backend 改动，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build`（新构建产物） |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Backend findings | ✅ 3 项全部落点 | P2-2 ✅已修复、P2-3 ✅已修复、P2-4 ✅已跳过（保留工具类 + 复审触发条件） |
| 构建与重启 | ✅ 完成 | `mvn clean package -DskipTests` BUILD SUCCESS；`start.sh --background` 停旧进程后重启成功 |
| 连续 3 轮检查 | ✅ 达成 | R1 审查发现 3 项 Backend 问题 → R2 修复并编译 → R3 无新问题、无新改动 |
| commit | ✅ `5214b1f`（API 文档）、`78fd765`（Backend 修复） | 报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限（环境级阻塞） |

**Backend 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| P2-2 | `RateLimitService.generateFallbackKey()` | 兜底键拼接 `UUID.randomUUID()`，每次请求键都不同 → 每次都落进全新的窗口（count=1）→ **限流在空键路径上完全失效**，且 300 秒清理还要回收这些一次性条目 | 去掉随机后缀，改为 `fallback:` + hex(hash(标识 + 分钟桶))，同分钟稳定；隐私性不变。当前唯一调用方 `IngestionController:132` 在校验 `session_id` 非空后才拼键，属潜在缺陷而非现网行为变更 |
| P2-3 | `RateLimitService` 类注释 | 示例写成 `tryAcquire("user:123", 10, 60)`，而类只暴露 `tryAcquire(String)`，照抄无法编译 | 改为 `tryAcquire("user:123")` 并注明阈值/窗口来自 `claudemem.rate-limit.max-requests` / `window-seconds` |
| P2-4 | `ProjectFilterService` | 无 `@Service`、无任何生产调用方，仅被自身 40 个单测引用，测试通过会造成"已有覆盖"的错觉 | **已跳过，保留工具类**。依据：`backend/src/main` 全仓无 `Files.walk`/`walkFileTree`/`Files.list`，当前设计不存在文件扫描链路，接入等于新增管线；删除则连带删掉 40 个正确的工具契约测试。复审触发条件：一旦出现目录遍历或 CLAUDE.md 写入的路径过滤需求，改为接入本类 |

**API 文档方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| A-1 | `docs/API-zh-CN.md` 3 张查询参数表 | 数值默认值被写进「必填」列：`GET /api/search` 的 `limit=20`、`offset=0`，`GET /api/context/timeline` 的 `depth_before`/`depth_after=10`，Extraction history 的 `limit=10`——读者会理解为「必填：20」 | Extraction history 按英文版排版改为 ❌ + 说明内标注「（默认 10）」；Search 与 Timeline 两表新增「默认值」列，与英文版逐格对应；两版 changelog 补 `0.1.0-beta+46` |

**端点全量比对（EN ↔ ZH ↔ Controller）**：脚本提取 13 个 Controller 的 68 条映射，与两版文档逐条比对——
67 个生效端点**全部有文档且 EN/ZH 端点集合完全相等**（`EN == ZH` 为 True）。两处"多余/缺失"经核实均为假阳性：
`GET /api/concepts` 来自 `ViewerController:207` 被注释掉的 `@GetMapping`；`PUT /api/modes`、`PATCH|DELETE /api/memory/observations`
出现在 changelog 历史叙述中，实际生效端点为 `POST /api/modes` 与 `/api/memory/observations/{id}`，文档正确。
另抽样 12 个端点实测：health/version/processing-status/readiness/modes/projects/stats/cursor.projects/settings/context.recent/mode 均 200，
`/api/search` 缺 `project` 返回 400、带 `project` 返回 200，与文档「project 必填」一致。

**未解决问题**：`health-check-task.md` 更新基线+本报告后已达 ~1010 行，超过 1000 行阈值但未超 102400 字节——下一轮开始时需先执行归档（保留规则/基线/未解决项，历史移入 `docs/archive/2026-10-02_health-check-history-3.md`）；push 权限阻塞（`wubuku` 403）；API 文档中英标题风格分歧（低优先级 Pending）；`CLAUDE.md` 被 gitignore，上轮对其的修正无法通过提交传播。

代码审查轮换推进：Backend 完成（三十四轮），下一方向 Java SDK；文档审查轮换推进：API 文档完成（三十四轮），下一方向 SDK README。

### 2026-10-02 10:54 | 统一维护任务（Java SDK 三十五轮 + SDK README 三十五轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；本轮无代码改动，服务沿用上一轮重启的实例（pid 23350） |
| 文档增长检查 | ✅ OK（首末各一次） | 归档后 5 个活动文档全部低于阈值；`health-check-task.md` 216 行 |
| 代码审查方向 | ✅ Java SDK（三十五轮） | 逐方法核对 `CortexMemClientImpl`（880 行）26 个 public 方法的错误路径；未发现需要改代码的缺陷（降级行为有 6+ 个单测覆盖且为有意设计），改为补齐文档 |
| 文档审查方向 | ✅ SDK README（三十五轮，Java SDK） | 1 项：降级契约描述不完整 → 已修复并同步中英版 |
| 代码变化检测 | ⚪ 本轮无代码改动 | 仅 `.md` 变更，`is_code_path` 排除 `*.md`，指纹不变（`f11a28ca…`） |
| 完整验收 | ⏭ 跳过 | 指纹与基线 `78fd765` / `f11a28ca…` 完全一致，按规则跳过重复验收，基线不推进 |
| Backend findings | ✅ 0 未决 | 上一轮 3 项已全部落点 |
| 连续 3 轮检查 | ➖ 不适用 | 本轮无代码修改 |
| commit | ✅ `1fa843e` | |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限（环境级阻塞） |

**Java SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| S-1 | `cortex-mem-spring-integration/README.md` + `-zh-CN.md` | 「优雅降级」只写了检索/ICL 两项，实际客户端有 12 个读方法在失败时返回合成值；调用方无法区分「后端挂了」和「没有数据」 | 用脚本逐方法提取 26 个 public 方法的 catch 行为，按「静默降级 7 个 / 回报错误 5 个 / 向上抛出 12 个」三组写入中英版，并给出「先 `healthCheck()` 再信任空结果」的使用建议。EN/ZH 表格 11 行逐格校验一致 |

**未解决问题**：`health-check-task.md` 已在上一轮完成归档，本轮恢复正常；push 权限阻塞（`wubuku` 403）；API 文档中英标题风格分歧（低优先级 Pending）；并行巡检进程争写状态文件（持续观察）；`CLAUDE.md` 被 gitignore。

代码审查轮换推进：Java SDK 完成（三十五轮），下一方向 Go SDK；文档审查轮换推进：SDK README 完成（Java SDK 部分），下一方向继续其余 SDK README。

### 2026-10-02 11:01 | 统一维护任务（Go SDK 三十六轮 + SDK README 三十六轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，5 个活动文档全部低于阈值 |
| 代码审查方向 | ✅ Go SDK（三十六轮） | 审计 `client_impl.go` 请求/重试/错误分类 + 11 个 sentinel；发现 1 项并当场修复 |
| 文档审查方向 | ✅ SDK README（三十六轮，Go / Python / JS 三个 README） | 三份 README 的方法覆盖、签名、HTTP 映射、默认值全部与源码核对一致，**无问题、无修改** |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `f11a28ca…` → `a5efd8b8…`（871 条） |
| 完整验收 | ✅ 已执行 | 指纹变化触发，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build` |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Go 组件验证 | ✅ 全通过 | `gofmt -l .` 无输出、`go vet` 无告警、`go test ./...` 255 个测试函数全绿 |
| Go Demo E2E | ✅ 39/39 | 启动 37779 → E2E → 停止（仅停止本轮启动的进程） |
| Backend findings | ✅ 0 未决 | 上一轮 3 项已全部落点 |
| 连续 3 轮检查 | ✅ 达成 | R1 审查发现 1 项 → R2 修复 + gofmt/vet/test → R3 复核 README 签名映射，无新问题、无新改动 |
| commit | ✅ `77787ed` | 报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限（环境级阻塞） |

**Go SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| G-1 | `go-sdk/cortex-mem-go/client_impl.go:222` `doRequest` | 用 `io.LimitReader(body, MaxResponseBytes)` 读取，**超限时静默截断**且不置错误位；调用方最终看到的是 `failed to parse <path> response: unexpected end of JSON input` —— 与"后端返回了畸形 JSON"无法区分，排查时唯一的线索只是日志里的字节数 | 改为读取 `MaxResponseBytes+1` 字节并在超限时返回明确错误 `response body exceeds N byte limit (raise the page size or split the query)`；内存上限仍为 limit+1，未变松。原测试断言的是"截断后解析失败"的旧契约，已改为断言新错误，并额外断言误导性的 parse 文案不再出现 |

**SDK README 方向核验结论（无修改）**

- **Go README**：API Coverage 表 25 个方法与 `Client` 接口逐一对应（接口 27 个方法中另 2 个为 `Close`/`String` 生命周期方法，不计入 API）；Option 表 8 个选项的默认值与 `DefaultClientConfig()` 完全一致（30s / 10s / 3 / 500ms / nop logger）；Wire Format 4 条断言逐条对照 DTO 验证 —— `ObservationRequest.ProjectPath` 的 tag 确为 `cwd`、`ToolName` 确为 `tool_name`、`ExperienceRequest.RequiredConcepts` 确为 camelCase `requiredConcepts`。
- **Python README**：5 张表覆盖 25 个公开 API 方法（源码 26 个公开方法中另 1 个为 `close` 生命周期方法），签名抽查 `health_check()` / `get_stats(project_path="")` / `get_latest_extraction(...)` 等与 `client.py` 一致；Error Handling 段引用的 `NotFoundError` / `RateLimitError` / `APIError.status_code` 在 `error.py` 中均存在（共 12 个异常类型）。
- **JS README**：Methods 表 25 个 API 方法 + `close()` + `toString()`，与 `src/client.ts` 的 25 个公开 API 方法完全覆盖；抽查 HTTP 映射 —— `getObservation` 确为 `POST /api/observations/batch`（内部委托 `getObservationsByIds`）、`getStats` 确为 `GET /api/stats`、`getVersion` 确为 `GET /api/version`。
- 三份 README 的中英版本行数一致（Go 211、Python 165、JS 206），结构逐节对应。

**未解决问题**：push 权限阻塞（`wubuku` 403）；API 文档中英标题风格分歧（低优先级 Pending）；并行巡检进程争写状态文件；`CLAUDE.md` 被 gitignore。

代码审查轮换推进：Go SDK 完成（三十六轮），下一方向 Python SDK；文档审查轮换推进：SDK README 四语言全部核验完毕（Java/Go/Python/JS），下一方向回到 API 文档。

### 2026-10-02 11:06 | 统一维护任务（Python SDK 三十七轮 + API 文档三十七轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Python SDK（三十七轮） | 审计 `client.py` 请求层、重试、异常映射与 wire format；未发现需改代码的缺陷（重试与错误分类已按注释声明对齐 Go/JS），无修改 |
| 文档审查方向 | ✅ API 文档（三十七轮） | 关闭长期 Pending 项"中英标题风格分歧"，两版各加结构说明 |
| 代码变化检测 | ⚪ 无代码改动 | 仅 `.md` 变更，指纹仍为 `a5efd8b8…` |
| 完整验收 | ⏭ 跳过 | 指纹与基线 `77787ed` / `a5efd8b8…` 一致，按规则跳过，基线不推进 |
| 组件验证 | ✅ 全通过 | `python3 -m pytest tests/ -q` → 374 passed；`python-sdk-e2e-test.sh` → 28/28，26 个公开 API 方法全覆盖 |
| Backend findings | ✅ 0 未决 | |
| 连续 3 轮检查 | ✅ 达成 | R1 审查 Python SDK → R2 复核 wire format 与错误映射 → R3 复核 API 文档端点集，无新问题、无新改动 |
| commit | ✅ `87a4cd4` | |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Python SDK 方向核验结论（无修改）**

- 请求层 `_request` / `_request_json` / `_request_no_content` / `_fire_and_forget` 分层清晰；`_request` 对 ≥400 一律 `raise_for_status`，`_request_json` 对 204 与非 JSON 响应返回 `None`（已在 docstring 说明属有意降级）。
- 重试语义与 Go SDK 逐条对齐且注释写明依据：`is_retryable` = (429, 502, 503, 504)；网络异常一律重试；线性退避 ±25% 抖动；非重试错误立即放弃并吞掉。
- 异常映射 `raise_for_status` 覆盖 400/401/403/404/405/409/422/429/5xx，与 Go 的 11 个 sentinel 一一对应。
- wire format 抽查 `record_observation`：实际发送 `{"session_id", "cwd", "tool_name", ...}`，与 Go `ObservationRequest` 的 json tag 完全一致；`extracted_data` → `extractedData`（camelCase）并在注释中说明对齐后端 `@JsonProperty`；空 `source` 显式不发送，注释写明"后端对空串与 null 处理不同"。
- 记录一项**有意的不一致**（不作为缺陷）：Go 客户端有 10MB 响应体上限，Python 客户端没有。Python SDK 面向应用内嵌调用、服务于可信本地后端，且加限制会改变 `requests` 的既有行为，属于需要设计决策的改动而非当场小修，故本轮只记录不改动；若将来要加，应作为独立的响应大小策略统一到四个 SDK。

**API 文档方向处理**

| # | 问题 | 处理 |
|---|------|------|
| A-2 | "EN 描述式 H3 / ZH 路径式 H4" 自 2026-09-30 起每轮重复登记为低优先级 Pending，实际为呈现方式差异 | **关闭该项**：两版顶部各加结构说明，写明差异是有意的、端点集合已核验一致（脚本复核 67 个生效端点 EN==ZH 仍成立）、且不要单方面"对齐"；`doc-review-task.md` 记录决策理由，避免后续轮次重开 |

**未解决问题**：push 权限阻塞（`wubuku` 403）；并行巡检进程争写状态文件；`CLAUDE.md` 被 gitignore；Python SDK 无响应体大小限制（有意的不一致，已记录）。

代码审查轮换推进：Python SDK 完成（三十七轮），下一方向 JS/TS SDK；文档审查轮换推进：API 文档完成（三十七轮，Pending 项已清空），下一方向 SDK README。

### 2026-10-02 11:11 | 统一维护任务（JS/TS SDK 三十八轮 + SDK README 三十八轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ JS/TS SDK（三十八轮） | 审查 npm 发布配置、类型完整性、wire format、E2E 脚本表述；1 项当场修复，1 项需用户决策 |
| 文档审查方向 | ✅ SDK README（三十八轮，JS SDK） | 方法表与 HTTP 映射已在上轮核验；本轮补充核验 License 声明与实际随包文件一致，无问题 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `a5efd8b8…` → `6e8789d5…`（871 条） |
| 完整验收 | ✅ 已执行 | 指纹变化触发，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build` |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| 组件验证 | ✅ 全通过 | `tsc --noEmit` 无错误；Vitest 212 passed；`npm run build` 成功；`js-sdk-e2e-test.sh` 27/27 |
| npm 打包核验 | ✅ 10 个文件 | `npm pack --dry-run`：LICENSE、README.md、README-zh-CN.md、index.js/.mjs、两个 .d.ts、两个 .map、package.json |
| Backend findings | ✅ 0 未决 | |
| 连续 3 轮检查 | ✅ 达成 | R1 审查发现 1 项 → R2 修复并 pack/test/build/E2E 复验 → R3 复核 README 与实际随包内容，无新问题、无新改动 |
| commit | ✅ `d97f424` | 报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**JS/TS SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| J-1 | `js-sdk/cortex-mem-js/package.json` `files` | 只列了 `README.md`，而仓库维护完整的 `README-zh-CN.md` → **发布的 npm 包里没有中文文档**，中文用户装包后看不到 | `files` 增加 `README-zh-CN.md`；`npm pack --dry-run` 复核 tarball 由 9 个文件变 10 个，中文 README 已在其中 |
| J-2 | `js-sdk/cortex-mem-js/LICENSE` vs 根 `LICENSE` | 两份 LICENSE 的版权归属不同：js-sdk 为 `Copyright (c) 2026 Blueforce Tech Inc`，仓库根为 `Copyright (c) 2026 Cortex Community Edition Contributors`。`package.json` 声明 `"license": "MIT"` 且实际随包发布的是 js-sdk 那份 | **待用户决策，本轮不擅自改**：版权归属属于法律/商务判断，无法从代码推断。两个选项：(a) 统一为公司（Blueforce Tech Inc）——把根 LICENSE 改成公司并同步其他分发物；(b) 统一为社区贡献者——把 js-sdk LICENSE 改成与根一致。已记入下方未解决问题 |

**核验通过、无需改动的部分**

- **类型完整性**：`src/dto/` 导出 30 个 interface/type（`ObservationRequest`、`ObservationUpdate`、`ExperienceRequest`、`ICLPromptRequest`、`SearchRequest`、`SessionStartRequest`、`StatsResponse` 等），25 个 API 方法全部有对应入参/出参类型。
- **wire format**：`ObservationRequest` 字段为 `session_id` / `cwd` / `tool_name` / `extractedData`（camelCase），与 Go、Python 三个 SDK 完全一致；`recordObservation` 对 `session_id`、`cwd`、`tool_name` 三项做必填校验。
- **双格式输出**：`exports` 同时提供 `import` → `index.mjs`、`require` → `index.js`、`types` → `index.d.ts`，并额外产出 `index.d.mts`，ESM 消费者可拿到正确的类型声明。
- **E2E 脚本表述诚实**：`scripts/js-sdk-e2e-test.sh` 开头与结尾都明确声明"在线 HTTP 探针直接调用后端端点，SDK 包装行为由 Vitest 套件覆盖"，并把 Vitest 单测作为其中一项检查——与 `patrol-task.md` 的要求一致，未把 curl 检查描述成 SDK 方法调用。
- **License 声明**：JS README 的 `## License / ## 许可证` 写 "MIT"，与随包 LICENSE 的许可证类型一致（仅归属人不同，见 J-2）；Java SDK README 写"与 BlueCortexCE 主项目相同"，该 SDK 没有自己的 LICENSE、继承根 LICENSE，表述属实。

**未解决问题**：
1. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——需用户决策统一到公司或统一到社区贡献者；本轮未改动。
2. push 权限阻塞（`wubuku` 凭据 403，环境级）。
3. 并行巡检进程争写 `patrol-state.json` / `patrol-rotation.md`。
4. `CLAUDE.md` 被 `.gitignore` 忽略，其修正无法通过提交传播。
5. Python SDK 无响应体大小限制（与 Go 的 10MB 上限不一致，有意保留，已记录）。

代码审查轮换推进：JS/TS SDK 完成（三十八轮），六个方向全部走完一轮 → 下一方向回到 Java SDK 开始新循环；文档审查轮换推进：SDK README 完成（三十八轮）。

### 2026-10-02 11:14 | 统一维护任务（Java SDK 新循环 + 用户指南三十九轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Java SDK（新循环首轮，聚焦 DTO 层与 spring-ai 模块） | 审查 `SearchRequest` DTO 设计、`CortexSessionContext` 线程上下文、桥接 Advisor 的 `begin/end` 配对；**未发现缺陷，无修改** |
| 文档审查方向 | ✅ 用户指南（三十九轮） | 复核上一轮新增的 `scripts/README.md` 分节与实际脚本行为；无问题、无修改 |
| 代码变化检测 | ⚪ 无代码改动 | 仅本轮报告，无指纹变化（仍为 `6e8789d5…`） |
| 完整验收 | ⏭ 跳过 | 指纹与基线 `d97f424` / `6e8789d5…` 一致，按规则跳过，基线不推进 |
| 组件验证 | ✅ 全通过 | `cortex-mem-spring-integration` 全模块 `mvn test` BUILD SUCCESS：client 121（DtoTest 34 + ClientImplTest 87）、spring-ai 46、starter 7，共 174 个测试 0 失败 0 错误 |
| Backend findings | ✅ 0 未决 | |
| 连续 3 轮检查 | ➖ 不适用 | 本轮无代码修改 |
| commit | ✅ 见下方 | 报告与轮换状态 |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Java SDK 方向核验结论（无修改）**

- **`SearchRequest` DTO 设计**（`patrol-task.md` 指定的重点）：record 紧凑构造器对 `project` 做 fail-fast 校验，Builder 的 `build()` 再做一次更友好的报错（"project is required (set via project())"）；`limit` 双端校验（负数与 >100 均抛错），并注明 `limit=0` 允许、调用时省略以让后端取默认值；`offset` 只校验非负并写明"不设上界，极大 offset 交由后端拒绝"，且交叉引用了 `ObservationsRequest.Builder#offset()` 的同一约定；空 `orderBy` 静默置 null，避免向后端发空串。每一个决策都有注释说明**为什么**，而非只记录做了什么。
- **`CortexSessionContext`**：`end()` 调用 `ThreadLocal.remove()` 而非置空；无上下文时 `getSessionId()` 返回 `"unknown-session"`、`getProjectPath()` 返回 `""`、`incrementAndGetPromptNumber()` 返回 0，均为安全默认；计数器用 `AtomicInteger`。
- **上下文配对**：用 grep 核对全部 `CortexSessionContext.begin(` 调用点——生产代码（`CortexSessionContextBridgeAdvisor` 两处、`ToolsController`、`ChatController`、`IngestController`、`SessionLifecycleController` 三处）**全部使用双参重载并成对 `try/finally end()`**；会生成随机 UUID 的单参重载仅被测试使用，因此不存在"每次调用都换 session id 导致会话断裂"的隐患。
- **桥接 Advisor**：`call()` 与 `stream()` 两条路径都在链路前 `begin(conversationId, projectPath)` 并 `incrementAndGetPromptNumber()`，`finally` 中 `end()`，与类注释描述的契约一致；对应有 6 个专门单测。

**未解决问题**：
1. **JS SDK 与根目录 LICENSE 版权归属不一致**（上一轮 J-2）——需用户决策，本轮仍未改动。
2. push 权限阻塞（`wubuku` 403）。
3. 并行巡检进程争写状态文件。
4. `CLAUDE.md` 被 `.gitignore` 忽略。
5. Python SDK 无响应体大小限制（有意的不一致）。

代码审查轮换推进：新循环已从 Java SDK 开始（三十九轮）；文档审查轮换推进：用户指南完成（三十九轮），下一方向 SDK README。

### 2026-10-02 11:20 | 统一维护任务（Go SDK 新循环 + SDK README 四十轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Go SDK（新循环，聚焦 DTO 层与三个框架适配层） | 发现 1 项文档化缺陷并修复；DTO 层与适配层逻辑未发现缺陷 |
| 文档审查方向 | ✅ SDK README（四十轮，Go SDK 框架集成节） | 三个集成示例的构造函数与选项名逐条对照源码核验，均正确；补入错误处理差异说明 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `6e8789d5…` → `af95888…`（871 条） |
| 完整验收 | ✅ 已执行 | 指纹变化触发，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build` |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| 组件验证 | ✅ 全通过 | `gofmt -l .` 无输出、`go vet` 无告警；eino / genkit / langchaingo 三个子模块独立 build + test 全绿；根模块 + dto 全绿 |
| Backend findings | ✅ 0 未决 | |
| 连续 3 轮检查 | ✅ 达成 | R1 审查发现 1 项 → R2 三适配器注释修复 + README 补充并全模块复验 → R3 无新问题、无新改动 |
| commit | ✅ `a517982` | 报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Go SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| G-2 | `eino/retriever.go`、`genkit/retriever.go`、`langchaingo/memory.go` | 三个框架适配器的错误策略**不一致且只有一方解释了原因**：Eino / Genkit 的 Retriever 记录日志后把错误返回给调用方，LangChainGo 的 `Memory` 则降级为空记忆串以免中断提示词链路。只有 LangChainGo 侧写了注释，Eino / Genkit 侧没有——容易被后人当成"疏漏"而"对齐"，从而在无人察觉的情况下改变某个框架的检索语义 | 三个适配器各自补上错误策略说明并互相点名差异（"Both behaviours are intentional; keep them in sync deliberately, not by accident"）；README 中英版在 Framework Integrations 节加入同样的说明。纯注释改动，不改行为 |

**核验通过、无需改动的部分**

- **DTO 层**：`dto/observation.go` 的每个 json tag 都标注了后端事实来源（`@JsonProperty("content_session_id") on entity`、`SNAKE_CASE naming strategy` 等），`ObservationUpdate` 用指针字段 + `omitempty` 天然实现 PATCH 语义，并显式记录 `content`/`narrative` 是同一后端字段的别名、同时设置会被静默忽略这一风险。
- **客户端校验**：`UpdateObservation` 确实调用 `update.Validate()`（拒绝空更新与别名冲突），用 `url.PathEscape(observationID)` 防止路径注入，并注明为何这两个方法**不是** fire-and-forget（用户显式操作，错误必须上抛）。`DeleteObservation` 同样做了空串校验与路径转义。
- **适配器结构一致性**：三个适配器都用 Option 模式、构造器对 nil client `panic`（带明确包名前缀的消息）、空查询返回空结果而非报错；Genkit 额外支持按调用覆盖 project / count / source / userID，为空时回落到构造器默认值。
- **README 集成示例**：`eino.NewRetriever(client, "/my-project", eino.WithRetrieverSource(...))`、`langchaingo.NewMemory(client, "/my-project")`、`genkit.NewRetriever(client, "/my-project", genkit.WithRetrieverCount(20))` 的包名、函数名、选项名与源码完全一致。

**未解决问题**：
1. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策。
2. push 权限阻塞（`wubuku` 403）。
3. 并行巡检进程争写状态文件。
4. `CLAUDE.md` 被 `.gitignore` 忽略。
5. Python SDK 无响应体大小限制（有意的不一致）。

代码审查轮换推进：Go SDK 完成（新循环首轮），下一方向 Python SDK；文档审查轮换推进：SDK README 完成（四十轮），下一方向设计文档。

### 2026-10-02 11:28 | 统一维护任务（Python SDK 新循环 + 设计文档四十一轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Python SDK（新循环，聚焦 DTO 层别名守卫） | 发现 3 个相互关联的问题，全部修复并补测试 |
| 文档审查方向 | ✅ 设计文档（四十一轮） | 发现 1 处体量数据过时并修正；2 条实现锚定断言逐条对照源码验证属实 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `af95888…` → `e52d9024…`（871 条） |
| 完整验收 | ✅ 已执行 | 指纹变化触发，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build` |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| 组件验证 | ✅ 全通过 | `pytest tests/ -q` → 375 passed（新增 1 个）；`python-sdk-e2e-test.sh` → 28/28 |
| Backend findings | ✅ 0 未决 | |
| 连续 3 轮检查 | ✅ 达成 | R1 审查发现 3 项 → R2 修复 + 375 测试 + 28/28 E2E → R3 无新问题、无新改动 |
| commit | ✅ `ccee447` | 报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Python SDK 方向发现与处理（`content`/`narrative` 别名守卫，3 个关联问题）**

跨 SDK 事实核对：Java 的 `ObservationUpdate.Builder.build()` 在两者同时设置时抛 `IllegalStateException`；Go 的 `UpdateObservation` 调用 `Validate()` 返回校验错误（其源码注释"mirrors the fix applied to Java SDK's Builder.build()"经核实属实）。Python 原本只有一条路径有保护。

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| PY-1 | `cortex_mem/error.py` | 类文档承诺抛 `ValidationError`，但 dataclass 构造路径抛的是裸 `ValueError`，而 kwargs 路径抛 `ValidationError`。`ValidationError` 继承 `CortexError` 而非 `ValueError`，因此调用方写 `except ValidationError` **根本捕获不到**构造期错误 | `ValidationError` 改为同时继承 `CortexError` 与 `ValueError`——既让既有的 `except ValueError` 处理器继续可用（向后兼容），又让 `except ValidationError` 覆盖所有路径 |
| PY-2 | `cortex_mem/dto.py` `__post_init__` | 抛出类型与 kwargs 路径不一致 | 改为抛 `ValidationError` 并带上 `field="content\|narrative"` 提示，与 Go/JS 的 `field` 属性对齐 |
| PY-3 | `cortex_mem/dto.py` `to_wire()` | dataclass 是**可变**的，构造后再 `update.narrative = ...` 会绕过 `__post_init__`，此时 `to_wire()` 按源码注释里写的"narrative 后者胜出"**静默丢弃**调用方设置的 `content` | `to_wire()` 重新校验并抛同样的 `ValidationError`，docstring 改为说明"为什么需要重复校验"而非描述静默降级 |

测试：原 `test_update_observation_both_content_and_narrative_dataclass` 断言 `ValueError` 仍然通过（因 `ValidationError` 是其子类），同时补上 `ValidationError` 断言；新增 `test_update_observation_both_set_after_construction` 覆盖构造后改写路径。

**设计文档方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| D-1 | `docs/drafts/phase-3-design/index.md` | 索引表 30 行体量数据与实际逐一比对后，`26.md` 标称 15KB、实际 16521 字节（16.1 KiB），是文件增长后未同步的陈旧值；其余 29 项误差均在 1 KiB 内 | 更正为 16KB |

**设计文档核验通过的部分（不只查体量，也查实现锚定断言）**

- **拆分结构合规**：`phase-3-design/` 下 31 个 `.md`（30 个内容文档 + `index.md`），全部 ≤50KB（最大 `25.md` 43506 字节）；指针文件 `phase-3-design.md` 声称的 30 个子文档、29KB/42KB 两处体量均属实；三个指针目标 `index.md`/`2.md`/`25.md` 均存在。
- **实现锚定断言属实**：索引表称"v30 与 `MemoryRefineService` 共享 projectLocks 的并发修复"——`StructuredExtractionService:120-123` 确实调用 `memoryRefineService.tryExecuteWithProjectLock(...)`，`MemoryRefineService:60` 持有 `projectLocks` map，注释与断言一致；"mergeAppendOnly/keep_hint 完善"——`mergeAppendOnly` 存在于 `StructuredExtractionService:439`，`keep_hint` 确为提示词中定义的操作。

**新记录的一项观察（不作为缺陷，需人工决策）**：`docs/drafts/` 下有 3 个文件超过 50KB——`go-sdk-design.md` 195KB、`perplexity/一个捕获屏幕内容的macOS应用.md` 102KB、`spring-ai-integration-plan.md` 55.6KB。50KB 规范的原文措辞是"**本目录内**每个文档 ≤50KB"（`phase-3-design/index.md`），按字面只约束该子目录，因此这三个文件不违反**已声明的**规范。拆分 195KB 的 Go SDK 设计文档是内容重组工作、且该文件被 Go SDK README 直接引用（拆分需同步改引用），不适合在无人值守轮次中动手，故如实记录而不擅自处理。

**未解决问题**：
1. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策。
2. `docs/drafts/` 三个超 50KB 文件（规范适用范围需明确，或安排拆分）——本轮记录，未处理。
3. push 权限阻塞（`wubuku` 403）。
4. 并行巡检进程争写状态文件。
5. `CLAUDE.md` 被 `.gitignore` 忽略。
6. Python SDK 无响应体大小限制（有意的不一致）。

代码审查轮换推进：Python SDK 完成（新循环首轮），下一方向 JS/TS SDK；文档审查轮换推进：设计文档完成（四十一轮），下一方向 架构文档。

### 2026-10-02 11:35 | 统一维护任务（JS/TS SDK 新循环 + 架构文档四十二轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ JS/TS SDK（新循环，聚焦响应处理与错误层） | 发现 1 项实质性缺陷并修复 + 3 个新测试 |
| 文档审查方向 | ✅ 架构文档（四十二轮） | 版本、Pipeline 类名与职责逐条对照源码核验，全部属实，无修改 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `e52d9024…` → `52814aa1…`（871 条） |
| 完整验收 | ✅ 已执行 | 指纹变化触发，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build` |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| 组件验证 | ✅ 全通过 | `tsc --noEmit` 无错误；Vitest **215 passed**（212 + 3 新增）；`npm run build` 成功；`js-sdk-e2e-test.sh` 27/27；`js-demo-e2e-test.sh` 27/27（启动 37781 → E2E → 停止） |
| Backend findings | ✅ 0 未决 | |
| 连续 3 轮检查 | ✅ 达成 | R1 审查发现 1 项 → R2 修复 + 215 测试 + 双 E2E 复验 → R3 无新问题、无新改动 |
| commit | ✅ `8be65c3` | 报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**JS/TS SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| J-3 | `js-sdk/cortex-mem-js/src/client.ts` `doFetch` | 所谓的"10MB 响应体限制"是 `await resp.text()` **把整份响应读成字符串之后**才比较长度 —— 也就是说限制从未限制过任何东西，内存在那一步早已花掉；后端（或链路上的任何一环）返回大响应时，客户端会把它整个握在手里。注释还写着"Read response body with 10MB size limit"，进一步强化了错误印象 | 读之前先检查 `Content-Length` 声明值，超限直接抛错并附上声明长度；读后检查保留为兜底（应对不声明或谎报长度的服务器）；注释改为如实说明"这是守卫而非流式读取器，峰值内存仍等于响应大小"。新增 `readContentLength()` 对 headers 对象缺失、值不可解析、部分运行时读 header 抛异常三种情况都返回 null（现有测试 mock 与部分 edge 运行时根本没有 headers） |

测试：新增 3 个——声明超限时断言 `text()` **从未被调用**（这正是修复的意义）、服务器谎报长度时兜底检查仍然生效、完全没有 headers 对象时正常解析。

**架构文档方向核验结论（无修改）**

- **中英对等**：两版各 13 个 H2 章节，行数 1105 / 1103，结构一一对应。
- **技术栈版本属实**：文档写 "Java 21+" 与 "Spring Boot 3.3.13"——`backend/pom.xml` 的 `<java.version>21</java.version>` 与 `spring-boot-starter-parent 3.3.13` 均一致（当前运行时 JVM 为 24.0.1，满足 21+ 表述）；`/api/version` 实测 `springBoot: 3.3.13`。
- **Pipeline 描述属实**：文档中的 `IngestionController → AgentService → EmbeddingService / XmlParser` 与源码逐一对应——`XmlParser` 确实位于 `util/` 包（文档也标注了 "XmlParser (util)"），`AgentService` 标注为 "Core orchestration"，`EmbeddingService` 标注为 "Vector embeddings"，与实际类名和职责一致。

**未解决问题**：
1. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策。
2. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围或安排拆分。
3. push 权限阻塞（`wubuku` 403）。
4. 并行巡检进程争写状态文件。
5. `CLAUDE.md` 被 `.gitignore` 忽略。
6. Python SDK 无响应体大小限制（有意的不一致；JS/Go 现均已有 10MB 守卫，Python 仍缺）。

代码审查轮换推进：JS/TS SDK 完成（新循环首轮），下一方向 Demo；文档审查轮换推进：架构文档完成（四十二轮），下一方向 用户指南。

### 2026-10-02 11:44 | 统一维护任务（Demo 新循环 + 用户指南四十三轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = ok；`doc-growth-check.sh` 首末各一次，全部低于阈值 |
| 代码审查方向 | ✅ Demo（新循环，Java Demo 控制器 + 构建配置） | 发现 2 项，其中 1 项是长期潜伏的构建陷阱，均已修复并验证 |
| 文档审查方向 | ✅ 用户指南（四十三轮） | Java Demo README 的构建命令随 pom 修正同步更新；无其他问题 |
| 代码变化检测 | ✅ 检测到变化 | 指纹 `52814aa1…` → `7b68c4dc…`（871 条） |
| 完整验收 | ✅ 已执行 | 指纹变化触发，未跳过 |
| 回归测试 | ✅ 45 通过 / 0 失败 / 1 跳过（共 46） | `bash scripts/regression-test.sh --skip-build` |
| EXTRACTION 验收 | ✅ 25 通过 / 0 失败 / 0 跳过 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Java Demo 组件验证 | ✅ 通过（修复后） | `mvn test`（**不带任何 profile**）exit 0；`java-sdk-e2e-test.sh` 28 通过 / 0 失败 / 1 跳过（跳过项为 `/chat`，需 LLM key） |
| Backend findings | ✅ 0 未决 | |
| 连续 3 轮检查 | ✅ 达成 | R1 审查发现 2 项 → R2 修复 + mvn test + Demo E2E 复验 → R3 无新问题、无新改动 |
| commit | ✅ `57ab0ad` | 报告与基线为后续 docs commit |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Demo 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DEMO-1 | `examples/cortex-mem-demo/pom.xml` profiles | **默认 profile 解析的是一个 6.5 个月前的陈旧制品**：`jitpack` profile 被设为 `activeByDefault`，固定 `com.github.Blueforce-Tech-Inc:BlueCortexCE:6aa5de459c`（2026-03-18）。该 commit 早于 `SessionStartRequest` 与 `CortexMemClient.updateSessionUserId`，而 Demo 源码早已使用这些 API——于是 `patrol-task.md` 规定的验证命令 `cd examples/cortex-mem-demo && mvn test -q` **编译失败**，报出一堆调用者从未碰过的文件里的"找不到符号"错误 | `local` profile 改为 `activeByDefault`（解析本仓库同级的 `cortex-mem-starter:1.0.0-SNAPSHOT`），`jitpack` 降为显式 `-Pjitpack` 的可选项；pom 与 Demo README 均写明原因与一次性 install 前置步骤。新克隆现在会得到清晰的"缺 SNAPSHOT"提示，而不是符号错误的墙 |
| DEMO-2 | `ChatController` `GET /chat` | 成功返回**裸文本**、失败返回 **JSON**，是全项目唯一有两种内容类型的 Demo 端点；而 Go / Python / JS 三个 Demo 的 `/chat` 都返回 JSON 对象 | 统一返回 `{"response", "project", "conversation_id"}` 并声明 `produces = application/json` |

**DEMO-1 的证据链（本轮最有价值的发现）**：`git show 6aa5de459c:.../dto/SessionStartRequest.java` 在该 commit **不存在**（早于该类）；当前 HEAD 为 2026-10-02，与固定 commit 相距约 6.5 个月；先 `mvn install -DskipTests` 安装本地 SDK 后，`mvn test -q -Plocal` **通过**，证明问题在 profile 默认值而非源码。

**DEMO-2 的验证**：`Accept: text/plain` 请求 `/chat` 返回 **406 Not Acceptable**，证明 `produces = application/json` 已生效（修复前该请求会得到 200 + 纯文本）；失败路径仍返回 JSON，两条路径类型一致。

**未解决问题**：
1. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策。
2. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围或安排拆分。
3. push 权限阻塞（`wubuku` 403）。
4. 并行巡检进程争写状态文件。
5. `CLAUDE.md` 被 `.gitignore` 忽略。
6. Python SDK 无响应体大小限制（Go / JS 均有 10MB 守卫）。

代码审查轮换推进：Demo 完成（新循环首轮），下一方向 Backend；文档审查轮换推进：用户指南完成（四十三轮），下一方向 SDK README。

### 2026-10-02 12:03 | 统一维护任务（Backend 新循环 + SDK README 四十四轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` 返回 `status=ok`；`/actuator/health` 200、`/api/version` = `0.1.0-beta` / Spring Boot `3.3.13` |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，`doc-growth-check.sh` exit 0 |
| 代码指纹 | ✅ 变化 | `7b68c4dc…` → `f952a208…`（872 条记录），触发完整验收 |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Backend findings | ✅ 0 未决 | 本轮 2 项当场修复（BE-1 含代码，BE-2 为其注释） |
| 连续 3 轮检查 | ✅ 达成 | R1 通读 + WebUI 契约核对 + 编译 → R2 新增 11 用例单测并反向验证 → R3 diff 复审 + 实测 |
| commit | ✅ `2b35785` | 本文件被 `.gitignore:89` 排除，报告与基线仅存本地；SDK README 与轮换状态文件另行提交 |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Backend 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| BE-1 | `LogsController.getLogs()` | **`totalLines` 返回的是滑动窗口大小，不是真实行数**：`totalLines += window.size()` 使该字段恒 ≤ `lines` 参数。实测 `GET /api/logs?lines=5` 在今日日志有 **24,682 行**时返回 `"totalLines": 5`。而 `@Operation` 描述与 `docs/API.md:1740` 示例（`totalLines: 1523` 配 `returnedLines: 1000`）都称其为总行数——文档示例在旧行为下自相矛盾 | 改为流式读取时逐行累加真实计数；窗口换成 `ArrayDeque` |
| BE-2 | 同上，第 101-102 行注释 | 声称 "O(n) single-pass algorithm"，但 `ArrayList.remove(0)` 每行搬移整个窗口，使单趟扫描实为 O(fileLines × lines) | 随 BE-1 一并修正为真实的单趟流式 + O(1) 丢弃最旧行 |

**BE-1 的证据链**：

- **缺陷可复现**：`wc -l` 得今日日志 24,682 行，而 `?lines=5` 返回 `totalLines=5`；`?lines=50` 返回 50，`?lines=1000` 返回 1000 —— 始终等于请求参数。
- **文档侧证伪**：`docs/API.md` 与 `API-zh-CN.md` 的示例 `totalLines: 1523` / `returnedLines: 1000`，若为窗口大小应为 1000。
- **修复后实测**：重建 jar 并重启 37777 后，`?lines=5 / 50 / 1000` 分别返回 `totalLines` = 24,722 / 24,723 / 24,724 —— 与磁盘行数一致，三次递增恰好对应服务端自身在这些请求之间新写的日志行。
- **回归测试非摆设**：把计数逻辑临时退回旧写法后重跑，`LogsControllerTest` 11 个用例中 **5 个失败**（`expected: <50> but was: <5>` 等），恢复修复后 11/11 通过。
- **WebUI 契约安全**：`webui/src/ui/viewer/components/LogsModal.tsx` 只消费 `data.logs`，不读 `totalLines`/`returnedLines`/`files`；随包的 `viewer-bundle.js` 同样只取 `.logs`。参考实现 `webui/.../LogsRoutes.ts` 的 `totalEstimate` 语义正是"文件总行数"。

**文档方向（SDK README）发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-1 | Go / JS / Python / Java 四个 SDK 的 README（中英共 7 个文件） | **响应体大小上限完全没有文档化**：Go 与 JS 在第 135、141 轮加的 10 MiB 防护是用户不可发现的行为——常量、错误文案、触发条件都没有一处说明；而 Python 与 Java 确实无上限，这一"有意的不对称"也没说明，后人可能误当遗漏而顺手对齐 | Go README 新增「Response Size Limit」小节（`cortexmem.MaxResponseBytes` = `10 << 20`、错误原文、公开方法只返回 `error` 的后果）；JS 新增同名小节（读前 `Content-Length` 检查 + 读后兜底、抛普通 `Error`、解码后长度对多字节 UTF-8 略宽松）；Python 与 Java 明确写出"无上限及其原因"，并说明与 Go/JS 的差异是有意的。中英逐条对齐 |

**DOC-1 的核实过程（三轮检查中收紧了 2 处过度断言）**：

- 初稿写"Go 会连同状态码一起返回该错误"。复核 `doRequestJSON` 后发现它只 `return nil, err`，状态码在公开 API 层已被丢弃 → 改为"内部 `doRequest` 返回状态码，公开方法只返回 `error`"。
- 初稿写 Java"把原始 `java.net.http.HttpClient` 响应交给 `RestClient`"。复核发现 `RestClient` 是 Spring 6 自己的类型，`java.net.http.HttpClient` 只是其底层传输 → 改为"`RestClient`（底层为 `java.net.http.HttpClient`）"。
- 另补记一处真实不一致：JS 错误文案写 `10MB`，实际常量是 `10 * 1024 * 1024`（10 MiB），已在文档中点明，避免读者按字面理解阈值。

**未解决问题**：

1. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司（改根 LICENSE）或 (b) 统一为社区贡献者（改 js-sdk LICENSE）。
2. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
3. push 权限阻塞（`wubuku` 403）。
4. 并行巡检进程争写状态文件。
5. `CLAUDE.md` 被 `.gitignore` 忽略。

> 上轮列出的第 6 项"Python SDK 无响应体大小限制"本轮已落盘：不再是未记录的不对称，而是四个 SDK README 中显式说明的有意差异。

代码审查轮换推进：Backend 完成（新循环首轮），下一方向 Java SDK；文档审查轮换推进：SDK README 完成（四十四轮），下一方向设计文档。

### 2026-10-02 12:17 | 统一维护任务（Java SDK 新循环 + 设计文档四十五轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = `status=ok`；`/actuator/health` 200；`/api/version` = `0.1.0-beta` / Spring Boot `3.3.13` |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 未变 | `f952a208…` 与基线一致（872 条记录）；本轮无代码改动，**按规则跳过完整验收**，基线不推进 |
| Java SDK 测试 | ✅ 174 / 0 / 0 | `cortex-mem-spring-integration` 三模块 BUILD SUCCESS（client 121 + spring-ai 46 + starter 7） |
| Java SDK findings | ⚠️ 1 项 P1 已记录 | 流式会话不传播，需架构变更，本轮不修 |
| 连续 3 轮检查 | ✅ 达成 | R1 通读 advisor/aspect/autoconfig + 写探针实测 → R2 核实泄漏路径可达性 + 收紧 README 措辞 → R3 脚本化复核索引表 |
| commit | ✅ 见下方提交 | 本文件被 `.gitignore:89` 排除，报告仅存本地 |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Java SDK 方向发现（已记录为 P1-1）**

`CortexSessionContextBridgeAdvisor.adviseStream()` 在**调用线程**执行 `CortexSessionContext.begin()`，却把清理放进 `flux.doFinally(...)`。Reactor 的 `doFinally` 运行在**发出终止信号的线程**上，而任何真实模型客户端都会切线程。两个后果：

1. **捕获被静默丢弃** —— 工具实际执行的线程看不到该 ThreadLocal，`CortexToolAspect` 只判断 `isActive()`（`CortexToolAspect.java:43`）便直接 `proceed()` 跳过捕获，`@Tool` 自动捕获在流式下等于失效，且**无任何日志**。
2. **会话上下文泄漏 → 跨会话串号** —— `doFinally` 清掉的是信号线程（一个空 ThreadLocal），调用线程的 ThreadLocal 永不清除。线程池复用该线程后，`begin()` 因 conversation id 缺失而**提前 return 的两条路径都不调用 `end()`**（`CortexSessionContextBridgeAdvisor.java:62-64` 与 `81-83`），于是残留的 `sessionId` 会被下一次请求的 `CortexToolAspect` 当作有效会话（`:64-65` 直接取 `getSessionId()`/`getProjectPath()`），工具观察被归到**上一个会话**。

**确定性复现**：现有 `CortexSessionContextBridgeAdvisorTest.adviseStream_whenConversationIdSet_...` 用 `Flux.just(response)`，在订阅线程同步发射，恰好绕开跨线程场景。改用 `Flux.just(response).subscribeOn(Schedulers.boundedElastic())` 后实测：

```
PROBE callingThread    = main
PROBE emitterThread    = boundedElastic-1
PROBE visibleOnEmitter = false      <-- 后果 1
PROBE activeAfter      = true       <-- 后果 2：调用线程仍处于激活态
PROBE sessionIdAfter   = conv-x     <-- 且绑定的是上一个会话
```

**本轮处置**：三个候选方案（Reactor Context 传播 / 退回同步装配 / 改 Spring AI 工具调用链）都改变并发语义，**不在本轮仓促实施**。已做的是把问题如实落盘——`docs/drafts/backend-review-findings.md` 新增 P1-1 条目（含复现命令、证据、三个方案与复审触发条件），并在 Java SDK README 中英双语的 Design Notes 写明该限制与规避方式（需要 `@Tool` 捕获时用同步 `.call()`）。探针测试文件用完即删，未进入提交。

**设计文档方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-2 | `phase-3-design/index.md` 「核心概念速查」 | **3 个章节指针失效**：`0.1.md §Bug1` 指向的 Bug 1 是 `findBySource` 的 `List<String>` 问题，`BeanOutputConverter` 实际在 **Bug 2**；`2.md §24.6` —— `2.md` 的标题只到 §2.1–§2.4，从无 §24.6，且 `mergeAppendOnly()` 的**定义**在 `24.6.md:100`，`2.md` 只在 337/485/492 行反向引用；`2.md §2.5` —— 无此章节，DLQ 的真实归属是 `11.md §11.3 Dead Letter Queue for Failed Extractions` | 三处分别改指 `0.1.md §Bug 2`、`24.6.md`、`11.md §11.3` |
| DOC-3 | `phase-3-design/index.md` 文档概览表 | `12.md` 标注 4KB，实际 3,114 字节（3.04 KiB），是 30 行中唯一的错值 | 改为 3KB |
| DOC-4 | 同上，`2.md` 行描述 | 把 `2.md` 描述为「模板/管道/**DLQ/mergeAppendOnly**」，但该文件**既不含 DLQ 也不定义 mergeAppendOnly**（`grep "DLQ\|Dead Letter" 2.md` 无命中） | 改为该文件实际内容：ExtractionTemplate 抽象 / YAML 配置模型 / GenericStructuredExtractionService 骨架 / 过敏原提取示例 |

**DOC-2~4 的核实**：用脚本把索引表 30 行的文件存在性、体积（floor/round KiB 两种口径均可接受）与磁盘实际值逐项比对——**修正后 30 行零不符**，页脚「30 个文件，最大 42KB」与实际（30 个内容文件，最大 42.49 KiB）一致；再用脚本确认每个速查指针指向的文件**确实包含对应概念**（`BeanOutputConverter` / `mergeAppendOnly` / `Dead Letter Queue` / `0.0004` / `Idempot*` / namespace / lifecycle 全部命中），且 `### Bug 2:` 与 `### 11.3 Dead Letter Queue` 两个标题真实存在。目录内 31 个 `.md` 的相对链接（2 条）全部可解析。

**未解决问题**：

1. **P1-1 流式会话不传播**（本轮新增）——需架构变更，已记录并给出复审触发条件。
2. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司（改根 LICENSE）或 (b) 统一为社区贡献者（改 js-sdk LICENSE）。
3. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
4. push 权限阻塞（`wubuku` 403）。
5. 并行巡检进程争写状态文件。
6. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Java SDK 完成（新循环首轮），下一方向 Go SDK；文档审查轮换推进：设计文档完成（四十五轮），下一方向架构文档。

### 2026-10-02 12:31 | 统一维护任务（Go SDK 新循环 + 架构文档四十六轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = `status=ok`；`/actuator/health` 200；`/api/version` = `0.1.0-beta` / Spring Boot `3.3.13` |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `f952a208…` → `6358e6c8…`（872 条记录），触发完整验收 |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Go SDK | ✅ 全绿 | `gofmt -d` 无输出、`go vet` 无输出、`go test ./...` 2 模块 ok |
| Java SDK | ✅ 174 / 0 / 0 | 三模块 BUILD SUCCESS（仅改注释，仍复跑确认） |
| 连续 3 轮检查 | ✅ 达成 | R1 通读 error.go / client_methods.go + 交叉核实后端 → R2 回退复现 + 全量测试 → R3 全仓错误说法排查 + 围栏配平脚本 |
| commit | ✅ `3dd69e9` | 报告与基线为后续 docs commit（本文件被 `.gitignore:89` 排除，仅存本地） |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Go SDK 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| GO-1 | `client_methods.go:317` `GetStats` | **调用方传入的 `projectPath` 被静默丢弃**，注释称「`/api/stats` 是全局端点，projectPath 仅为 API 对称性接受、后端忽略」——该说法**与后端相反**。`ViewerController.getStats` 明确支持可选 `project` 参数并在传入时返回该项目的计数，代码里甚至留着「addresses B11-1: SDK projectPath param now respected」的注释 | 传入非空 `projectPath` 时发送 `project` 参数；空白值则省略（保持「空 = 全局」语义） |
| GO-2 | `client_test.go` `TestGetStats` / `TestGetStats_NoQueryParams` | 两个测试**断言了错误行为**——明确要求「不得发送 project 参数」，把缺陷钉死在测试套件里 | 改为断言参数被正确转发；新增 `TestGetStats_BlankProjectOmitted` 覆盖空白值路径 |
| GO-3 | `CortexMemClientImpl.java:555` | Java SDK 注释称「Matches Go SDK behavior」——**这句话描述的正是 Go 的 bug**，错误说法已跨模块扩散 | 改为「Matches the Python and JS SDK behavior」 |

**GO-1 的证据链（本轮最有价值的发现）**：

- **后端支持有据**：`docs/API.md:1040` 已把 `project` 列为可选过滤参数；changelog `0.1.0-beta+34`（2026-04-12，commit a75ad4c）记录了这次后端改动并写明「added optional `project` query parameter for project-scoped statistics」。即后端在 2026-04-12 就为 SDK 参数做的修复，**Go SDK 此后一直没接上，漏了约 6 个月**。
- **Go 是四个 SDK 中唯一的例外**：Python `params["project"] = project_path`、JS `params.project = projectPath`、Java `uriBuilder.queryParam("project", …)` 都发送；只有 Go 传 `nil, nil`。
- **实测差异**：不带参数返回 `totalObservations: 37298` / `totalProjects: 2113`（全局）；`?project=/tmp/defect-probe-xyz` 返回该项目的计数并附带 `projectPath` 字段。Go SDK 用户调 `GetStats(ctx, "/my/project")` 拿到的是**全局数字**，且没有任何报错。
- **文档站在代码这边**：`docs/go-sdk-guide.md:786` 一直写的是「Returns project statistics. Pass empty string for global stats.」——指南是对的，代码是错的。
- **回归测试有效**：临时退回旧实现后 `TestGetStats` 失败（`expected project query param /my-project, got ""`），恢复后全绿。

**附带核实（无缺陷，记录以免重复排查）**：Go 的 fire-and-forget 会在 429/502/503/504 与网络错误上重试 POST 捕获请求，看似可能重复写入。核实后确认安全——后端 `AgentService:149-158` 用 `(content_session_id, tool_name, tool_input_hash)` 去重，重复时**正常返回 200 并 `return`**（不是错误），SDK 因此停止重试；`PendingMessageEntity` 上还有对应的唯一约束。幂等闭环正确，未改动。

**架构文档方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-5 | `docs/ARCHITECTURE-zh-CN.md` 第 652 行后 | **中文版缺少一个闭合代码围栏**：英文版在 `CREATE INDEX idx_feedback_session …;` 之后有一行 ` ``` `（639 行），中文版没有。围栏总数因此为奇数（37 vs 38），从 `#### 语义搜索` 起的 **26 个 Markdown 标题被整体吞进代码块**——包括 7 个 `##` 一级章节（数据流、API 层、技术栈、设计决策、权衡取舍、可扩展性考虑、安全架构、未来架构改进）。中文读者看到的架构文档从「语义搜索」之后**整整 300 多行都是等宽代码**，标题、表格、列表全部失效 | 在对应位置补回 ` ``` ` |

**DOC-5 的核实**：用脚本按 CommonMark 规则（允许至多 3 个前导空格）逐文件配平围栏，英文版 21 个代码块、最终深度 0；中文版修复前 21 个块但最终深度 1，且有 26 个标题落在代码块内。修复后两版**完全一致**：各 21 个代码块、各 1106 行、围栏 38=38、H3 23=23、H4 12=12、表格行 106=106、最终深度均为 0。残留的 5 处「块内标题」是 YAML/shell 注释（`# application.yml` 等），中英数量相同，非缺陷。

**跨 SDK 遗留（已记录，本轮不处理）**：`DatabaseStats` 在 Go / Python / JS 三个 typed SDK 中都没有 `projectPath` 字段，而后端 project-scoped 响应会返回该字段（Java 因返回 `Map<String, Object>` 而天然保留）。三者是**一致**的缺口而非分歧，单改 Go 反而制造新的表面积差异，故留待专门覆盖三个 typed SDK 的轮次统一处理。

**未解决问题**：

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更，已给出复审触发条件。
2. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司（改根 LICENSE）或 (b) 统一为社区贡献者（改 js-sdk LICENSE）。
3. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
4. push 权限阻塞（`wubuku` 403）。
5. 并行巡检进程争写状态文件。
6. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Go SDK 完成（新循环首轮），下一方向 Python SDK；文档审查轮换推进：架构文档完成（四十六轮），下一方向用户指南。

### 2026-10-02 12:45 | 统一维护任务（Python SDK 新循环 + 用户指南四十七轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = `status=ok`；`/actuator/health` 200 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `6358e6c8…` → `52a91b83…`（872 条记录），触发完整验收 |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Python SDK 单测 | ✅ 378 / 0 / 0 | `python3 -m pytest tests/ -q`（+3 新用例） |
| Python SDK E2E | ✅ 28 / 28 | `bash scripts/python-sdk-e2e-test.sh` |
| 连续 3 轮检查 | ✅ 达成 | R1 跨 SDK 端点比对 + 通读请求内核 → R2 回退复现 + 泄漏实验复测 → R3 文档引用/结构/链接全量脚本核验 |
| commit | ✅ `f2af6a7` | 报告与基线为后续 docs commit（本文件被 `.gitignore:89` 排除，仅存本地） |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Python SDK 方向发现与处理（本轮最有价值的发现：安全问题）**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| PY-1 | `cortex_mem/client.py:81-88` `__init__` | **改写了调用方传入的 `requests.Session` 的 headers**（`Accept`/`User-Agent`/`Authorization` 全部写进 `session.headers`）。`session` 参数的存在意义就是让调用方复用连接池，这些 header 因此会附加到调用方在该 session 上发出的**所有**请求，包括发往无关主机的请求 | header 改为保存在 `self._headers` 并**按请求传递**；`session.headers` 不再被写入（自建 session 也不例外）。`requests` 会把 session header 与请求 header 合并，因此自建 session 的实际请求头与修改前逐字节一致 |
| PY-2 | `tests/test_client.py` `test_custom_session_not_closed_on_client_close` | 该测试断言构造后 `s.headers["Accept"] == "application/json"`——**把 header 改写钉死成了预期行为** | 改为断言 session 未被关闭且未被改写；另加两个用例覆盖「header 确实上了线」与「未传 api_key 时不凭空造 Authorization」 |

**PY-1 的凭据泄漏证据（实测，非推演）**：用一个模拟 `Session.send` 截获请求头的方式复现：

```
BEFORE: {'User-Agent': 'my-app/2.0', 'Accept': '*/*', 'X-Trace': 'abc123'}
AFTER : {'User-Agent': 'cortex-mem-python/1.0.0', 'Accept': 'application/json',
         'X-Trace': 'abc123', 'Authorization': 'Bearer sk-secret-token'}   ← SDK 写进了调用方的 session

调用方随后发往 https://third-party.example.com 的请求：
   Authorization: Bearer sk-secret-token     ← API key 泄漏给第三方主机
   User-Agent    : cortex-mem-python/1.0.0    ← 调用方自己的 UA 被覆盖
```

修复后同一实验复测：`session unchanged after construction: True`、本 SDK 自己的请求仍带 `Authorization: Bearer sk-secret-token`、调用方第三方请求 `Authorization: None` 且 `User-Agent: my-app/2.0` 保留。

**回归测试有效**：临时退回改写版后 `TestCustomSession` 5 个用例中 **2 个失败**（`dict(s.headers) == before` 与 `"Authorization" not in c._headers`），恢复后 5/5 通过。

**附带核实（无缺陷）**：
- **四 SDK 端点覆盖一致**：脚本抽取四个 SDK 客户端中的路径字面量并归一化模板语法后，Go / Python / Java 各 23 条、JS 19 条（JS 条目带前导 `/` 未被同一正则捕获），归一化后**四个 SDK 覆盖同一组 23 个端点**，无覆盖缺口。
- **`close()` 语义正确**：`close()` 通过 `_owns_session` 判断，不会关闭非自己创建的 session，这部分本来就对。

**用户指南方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| GUIDE-1 | `docs/DEPLOYMENT.md` / `DEPLOYMENT-zh-CN.md` §4.4 回滚策略 | **`psql ... < rollback_V8.sql` 指向一个仓库中不存在的文件**（`find -iname "*rollback*"` 全仓无命中），文档也未说明需自行编写，照做会直接 `could not open file` 报错。更严重的是文档把回滚 V8 写成常规步骤，而**这会弄坏应用**：`content_hash` 列正在被 `ObservationRepository.findDuplicateByContentHash`（`ObservationRepository.java:405`）与 `AgentService:237-241` 用于 observation 去重，删列即破坏写入链路；同时 V8 建的 `fk_obs_memory_session` 约束**早已被 `V13__unify_session_id_on_content_session.sql:52` 删除**，schema 早已不是 V8 执行后的状态 | 重写为：先说明仓库不提供回滚脚本、Flyway CE 无自动回滚；把「恢复备份」作为推荐路径并给出可直接执行的命令；逆向 DDL 作为**示例**给出并指明唯一依据是迁移文件本身；加显著警告说明上述两个不可逆事实，建议优先恢复备份 |
| GUIDE-2 | `docs/TESTING-zh-CN.md` 变更日志 | 中文版**漏了英文版的 `2026-05-02` 一条**（新增「运行 Docker 部署测试」小节并对齐中英结构），导致中英变更日志行数 57 vs 56 | 补上该条；并核实 `#### 运行 Docker 部署测试` 小节确实存在于中文版第 141 行，翻译属实。修正后中英 h2=5 / h3=11 / h4=6 / 围栏=20 / 表格行=57 **完全一致** |

**GUIDE-1/GUIDE-2 的核实**：脚本扫描 10 份用户指南文档中引用的 `scripts/*` 与根级文件——引用的脚本**零缺失**；`backend/README.md` 引用的 16 个迁移文件与 `db/migration/` 下的 16 个**一一对应，无多无缺**；DEPLOYMENT 中出现的其他 `.sql` 均为 `pg_dump` 输出的示例名，非仓库文件声明。写入前另核对了容器名 `cortex-ce-postgres`（`docker-compose.yml:26`）与恢复命令写法（与文档既有 `gunzip -c … | docker exec -i … psql` 形式一致），并发现自己初稿的示例片段内部不自洽（备份产出 `.sql`、恢复却读 `.sql.gz`），已改为直接读 `.sql`。6 份用户指南文档的相对链接**全部可解析**（broken=0）。

**未解决问题**：

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司（改根 LICENSE）或 (b) 统一为社区贡献者（改 js-sdk LICENSE）。
3. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
4. push 权限阻塞（`wubuku` 403）。
5. 并行巡检进程争写状态文件。
6. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Python SDK 完成（新循环首轮），下一方向 JS/TS SDK；文档审查轮换推进：用户指南完成（四十七轮），下一方向 API 文档。

### 2026-10-02 13:01 | 统一维护任务（JS/TS SDK 新循环 + API 文档四十八轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = `status=ok`；`/actuator/health` 200 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 未变 | `52a91b83…` 与基线一致（872 条记录）；本轮无代码改动，**按规则跳过完整验收**，基线不推进 |
| JS SDK | ✅ 全绿 | `npm test` 215/215；`npm run lint`（tsc --noEmit）无输出；`npm run build` ESM + DTS 均成功 |
| JS SDK findings | ✅ 0 | 本轮未发现代码缺陷（详见下） |
| 连续 3 轮检查 | ✅ 达成 | R1 通读 errors.ts / client-options.ts / wire-helpers.ts + 跨 SDK 比对 → R2 实测边界输入 → R3 结构说明准确性自查与回退 |
| commit | ✅ `bc42a8a` | 纯文档 commit；本文件被 `.gitignore:89` 排除，仅存本地 |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**JS/TS SDK 方向：未发现缺陷（逐项核实记录）**

| 核查项 | 结论 |
|--------|------|
| 错误类型与谓词（`errors.ts`） | 与 Go / Python 一致：400/401/403/404/409/422/429 + isClientError/isServerError + isBadGateway/503/504 + isRetryable（429/502/503/504 + 网络 TypeError + AbortError，不含 500）。JS 无哨兵错误是语言差异，非遗漏 |
| 关闭态保护 | 25 个公开方法**全部**调用 `assertNotClosed()`，无一遗漏（脚本逐方法提取核对） |
| 入参校验 | `validateRequired` 覆盖的字段与 Go / Python 集合一致（session_id / project_path / cwd / tool_name / prompt_text / task / project / id / projectPath / observationId / feedbackType / templateName / ids / limit / update） |
| `getExtractionHistory` 的 limit 语义 | 与 Go / Python 完全一致：负数抛 `ValidationError`，`<= 0` 时**省略参数**以走后端默认值 10（后端 `ExtractionController:117` `defaultValue = "10"`） |
| 是否改写调用方对象 | **否**。`buildHeaders` 用展开把 `config.headers` 拷进新对象后才加 `Content-Type`/`Authorization`，`options.headers` 引用从不被写——与上轮 Python 的缺陷相反，此处本来就正确 |
| `safeStringOrStringList` 边界 | 纯数字字符串（如 `"123"`）会 `JSON.parse` 成功但非数组而落到 `undefined`。**经核实不可达**：后端 `mem_observations.id` 是 UUID（`V1__init_schema.sql:26` `gen_random_uuid()`），`refined_from_ids` 由 UUID 逗号连接而成（`ExtractionStorageService.java:93-95`），必含连字符，`JSON.parse` 必抛错并走逗号切分。实测四种真实形态（单个 UUID / 逗号串 / JSON 数组串 / 真实数组）解析全部正确 |

**API 文档方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-6 | `docs/API.md` `## Context` 节 | **`/api/context/recent`、`/api/context/timeline`、`/api/context/prior-messages`、`/api/context/semantic` 四个 H4 端点标题直接挂在 H2 下、没有描述式父级**，而同节的 `### Inject Context` / `### Generate Context` / `### Preview Context` 都有。EN 的结构说明（第 11-17 行）声称「每个端点都用描述式 H3」，文档自己在 5 处违反该规则 | 为这 4 个补上描述式 H3（`Get Recent Context` / `Get Timeline Context` / `Get Prior Messages` / `Semantic Context Search`），EN 端点标题孤儿数归零 |
| DOC-7 | 两版结构说明 | 说明文字与实际版式不符，且措辞会诱导后续轮次「修复」 | 两版说明改写为如实描述，并明确写上「这种层级不是缺失的父级标题，请勿补齐/统一」 |

**DOC-6/DOC-7 的核实（含一次自我纠错）**：

- **端点覆盖完整**：脚本剥离注释后从 Controller 的 `@*Mapping` 提取，得到 **67 个生效端点**（注释掉的 `/api/concepts` 已正确排除）；`docs/API.md` 67 个、`docs/API-zh-CN.md` 67 个，**两版集合完全相同，任一方向零缺失**，两版围栏最终深度均为 0。
- **自我纠错**：我起初把 ZH 的 `## Context` 节也「修」了（补了 7 个 H3），随后用全量脚本发现 **ZH 全文有 52 个端点 H4 直接挂在 H2 下、只有少数有 H3 父级**——说明 H2→H4 正是 ZH 的既定版式（与第 136 轮决策一致），我加的 7 个 H3 反而破坏了 ZH 自己的规范。**已全部回退，ZH 该文件恢复到与 HEAD 逐字节一致**，只保留结构说明的补充。
- **数字纠错**：ZH 说明初稿写「67 个端点中 45 个如此」，实测为 65 个端点 H4 中 54 个无父级、11 个有父级（45 是回退前的残留值）。已改为不含易腐化数字的表述，只描述主要形态并说明存在少数例外。

**未解决问题**：

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司（改根 LICENSE）或 (b) 统一为社区贡献者（改 js-sdk LICENSE）。
3. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
4. push 权限阻塞（`wubuku` 403）。
5. 并行巡检进程争写状态文件。
6. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：JS/TS SDK 完成（新循环首轮），下一方向 Demo；文档审查轮换推进：API 文档完成（四十八轮），下一方向 SDK README。

### 2026-10-02 13:18 | 统一维护任务（Demo 新循环 + SDK README 四十九轮）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 轻量健康预检 | ✅ OK | `/api/health` = `status=ok`；`/actuator/health` 200 |
| 文档增长检查（轮前/轮后） | ✅ OK | 5 个活动文档均低于阈值，exit 0 |
| 代码指纹 | ✅ 变化 | `52a91b83…` → `523d4b39…`，触发完整验收 |
| 回归测试 | ✅ 45 / 0 / 1 | `bash scripts/regression-test.sh --skip-build`（共 46，1 跳过） |
| Phase 3 验收 | ✅ 25 / 0 / 0 | `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` |
| Go SDK | ✅ 全绿 | `gofmt -l` 无输出、`go vet` 无输出；root + dto + examples 三模块 `go test` 全过 |
| Go SDK E2E | ✅ 39 / 39 | `bash scripts/go-sdk-e2e-test.sh` |
| Demo 端口 | ✅ 已清理 | 本轮启动的 37779 已停止；37778/37780/37781 全关 |
| 连续 3 轮检查 | ✅ 达成 | R1 通读 Python Demo + 三 Demo 契约比对 → R2 补单测并实跑 Demo 验证 → R3 补 Go Demo README 并逐条核实断言 |
| commit | ✅ `7ac0fb8` | 报告与基线为后续 docs commit（本文件被 `.gitignore:89` 排除，仅存本地） |
| push | ❌ 仍 403 | `wubuku` 凭据无写权限 |

**Demo 方向发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DEMO-3 | `go-sdk/cortex-mem-go/examples/http-server/main.go`（24 处） | **Go Demo 把所有 SDK 错误一律压成 500**：后端返回的 404 / 429 / 503 到达调用方时都变成 500。而 Python Demo（`@app.errorhandler(APIError)` → `exc.status_code`）与 JS Demo（express 错误中间件 → `err.statusCode`）都透传真实状态码——同一条件下，按 Python/JS 写的客户端在 Go Demo 上会看到 500 而非 404。Go Demo 自身也不一致：写路径已把 `ErrNotFound` 映射为 404、`ErrBadRequest`/`ValidationError` 映射为 400，只有兜底分支是 500 | 新增 `writeSDKError(w, err, context)` 统一映射：校验类错误 → 400；`*APIError` 且状态码在 `[400,600)` → 原样透传；其余 → 500。24 处兜底调用全部改用，**消息文本保持不变** |
| DEMO-4 | 同上 | 该映射无任何测试守护 | 新增 `main_test.go`，12 个子用例覆盖 404/400/429/503/418/500 透传、**被 `%w` 包裹的 `*APIError`** 仍能解包、两类本地校验错误、普通错误与 nil 仍为 500，以及**越界状态码（302）不得原样回显** |

**DEMO-3 的跨 Demo 证据链**：

- **三方实现对照**：Python `app.py:42-54`（`APIError` → `exc.status_code if 400 <= exc.status_code < 600 else 502`）、JS `app.ts:431-441`（`APIError` → `err.statusCode`，`ValidationError` → 400）、Go 修复前 24 处 `http.StatusInternalServerError`。
- **Go 内部不一致为证**：`main.go:405-422`、`:664` 的写路径已做 404/400 分类，说明 500 只是兜底缺失而非有意设计。
- **修复无回归**：`gofmt -l` 无输出、`go vet` 无输出、root/dto/examples 三模块 `go test` 全过、Go SDK E2E **39/39**；`grep` 确认 24 处兜底 500 已全部替换、剩余 0 处。
- **无 E2E 依赖旧行为**：`scripts/demo-v15-test.sh`、`js-demo-e2e-test.sh`、`python-demo-e2e-test.sh` 中均无对 500/503 的断言，修复不会破坏既有测试。
- **活体验证**：本轮启动 37779 后逐个探测 24 个路由——`/health`、`/version`、`/search`、`/observations`、`/projects`、`/modes`、`/stats`、`/quality`、`/settings`、`/experiences`、`/iclprompt`、`/extraction/latest|history` 全部 200；GET 打 POST 端点返回 405（方法不允许，正确）；缺参与非法 `limit` 仍返回 400。（探测中一次 400 曾疑似回归，实为 Demo 要求 `template`/`project` 参数名而我用了 `templateName`/`projectPath`，非回归。）

**文档方向（SDK README）发现与处理**

| # | 位置 | 问题 | 处理 |
|---|------|------|------|
| DOC-8 | `go-sdk/cortex-mem-go/examples/http-server/` | **整个目录没有任何 README**，而 Python Demo（75 行）与 JS Demo（76 行）都有 Quick Start / API Endpoints / Examples 三段式说明。Go Demo 是四者中功能最全的（24 路由、916 行），却是唯一无法直接照着上手的 | 新建 README：前置条件、启动方式（`CORTEX_BASE_URL` / `PORT` / 默认 37779）、24 条路由表（含各路由的必填参数与取值范围）、**本轮新增的错误状态码契约**、curl 示例、以及两处刻意保留的 404 文案说明 |

**DOC-8 的核实**：脚本比对 `main.go` 的 `mux.HandleFunc` 与 README 路由表——`main.go` 24 条、README 24 条，**双向零差异**；4 条相对链接全部可解析；`#error-responses` 锚点与 `## Error responses` 标题对应。逐条核对行为断言：`/stats` 确实转发 `project`（`main.go:506`）、优雅关闭确实忽略 `http.ErrServerClosed`（`:933`）、请求体上限确实是 1 MB（`:56-61`）、`/health` 后端不可用时确实返回 503（`:127`）。

**未解决问题**：

1. **P1-1 流式会话不传播**（Java SDK，第 144 轮记录）——需架构变更。
2. **JS SDK 与根目录 LICENSE 版权归属不一致**（J-2）——仍待用户决策：(a) 统一为公司（改根 LICENSE）或 (b) 统一为社区贡献者（改 js-sdk LICENSE）。
3. `docs/drafts/` 三个超 50KB 文件（`go-sdk-design.md` 195KB 等）——待明确规范适用范围或安排拆分。
4. push 权限阻塞（`wubuku` 403）。
5. 并行巡检进程争写状态文件。
6. `CLAUDE.md` 被 `.gitignore` 忽略。


---
