# 2026-11-04 — H 批：SSE 压力、输入上限、日志与数据目录

> 时点证据（round H / 2026-11-04）。记录测法、原始数字与结论；可复现命令在本机执行过。
> 代码改动见同一批提交。

## 环境

- 后端 jar：本批代码（`mvn -DskipTests package`），`-Xmx768m`，`server.address=127.0.0.1`，端口 37990。
- 数据库：`postgresql` 容器（5432），一次性库 `cortexce_h_run`；真实 embedding provider（`backend/.env`）。
- SSE 压测脚本：临时脚本（未入库），200 个真实 `http.client` 连接读 `/stream`；每 5 s 写 1 条
  `/api/ingest/observation`，每 5 s 发 1 次 `/api/search`，每 10 s 采样 `/actuator/prometheus`。

## H1 — SSE 长连接（50 / 200 客户端，各 600 s）

| 指标 | 50 客户端 | 200 客户端 |
|---|---|---|
| 建连成功 / 失败 | 50 / 0 | 200 / 0 |
| `cortexce_sse_connections`（保持期内） | 50 恒定 | 200 恒定 |
| 线程数（前 → 峰值 → 结束） | 38 → 40 → 38 | 37 → 39 → 38 |
| 堆 used（前 → 峰值 → 结束） | 131.8 → 310.6 → 277.3 MB | 107.9 → 342.8 → 124.6 MB |
| GC 后堆 | 104.9 MB | 111.1 MB |
| 事件送达 | 3000（每客户端 60） | 12000（每客户端 60） |
| 首事件延迟 p50 / p95 | 0.0 / 0.0 ms | 0.0 / 0.1 ms |
| 写事件延迟 p50 / p95（POST 开始 → 客户端收到） | 165.1 / 257.0 ms（58 样本） | 148.6 / 351.8 ms（58 样本） |
| 断开后计数归零耗时 | 30.2 s | 30.2 s |
| 写入 / 检索成功率 | 58/58，58/58（均 200） | 58/58，58/58（均 200） |

结论：

- **没有泄漏**：200 连接 + 10 分钟写入/检索后，GC 后堆 111 MB，对空载基线 107.9 MB，留存
  增量约 3 MB；线程数不随连接数增长（37–39）。
- **断连可回收**：客户端断开后 30.2 s 内 `cortexce_sse_connections` 回到 0。原因是新增的
  15 s 心跳注释帧：无事件时也能发现死连接（此前只在广播/30 分钟超时时才发现）。
- **上限建议**：768 MB 堆下 200 并发 SSE 稳定（峰值 343 MB），默认上限取 **200**
  （`claudemem.sse.max-connections`），超出时返回 `503 service_unavailable` 而非 500。
  若部署堆 ≥ 2 GB，可上调到 400+；按连接约 1.2 MB 峰值估算。

## H2 — 请求体与频率上限

### 现状（改造前）

- Spring/Tomcat 对 JSON 请求体**没有上限**：`server.tomcat.maxPostSize` 只作用于
  form 参数，Jackson 读取的 JSON 不受它约束。
- 没有全局限流；只有 `RateLimitService` 对 `POST /api/ingest/tool-use` 的每会话
  10 次 / 60 秒。
- 413/429 没有统一信封（400 才走第 18 轮错误体）。

### 改造后实测（运行中的服务，默认值）

| 探测 | 结果 |
|---|---|
| 5 MB observation（带 Content-Length） | **413**，0.012 s，`{"error":{"code":"payload_too_large","message":"Request body exceeds 2097152 bytes","details":{"max_bytes":2097152}}}` |
| 5 MB observation（`Transfer-Encoding: chunked`） | **413**，0.015 s，同一信封（`max_bytes` 取实际拦截阈值） |
| 瞬发 500 次 `POST /api/session/start`（25 线程） | 0.86 s：**284 × 200 / 216 × 429** |
| 瞬发 600 次（30 线程） | 0.62 s：**258 × 200 / 342 × 429**；429 带 `Retry-After: 1`，`{"error":{"code":"rate_limited","details":{"capacity":200,"refill_per_second":100.0,"retry_after":"1"}}}` |
| 压测后 `/api/health` | 200，服务无卡死、无 OOM |

默认值：`cortexce.http.max-body-bytes=2097152`（2 MiB，可用
`CORTEXCE_HTTP_MAX_BODY_BYTES` 覆盖）；令牌桶 `capacity=200`、`refill=100/s`，
可用 `cortexce.http.rate-limit.*` 关闭或调整；`server.tomcat.max-swallow-size=8MB`
保证 5 MB 请求被拒后客户端仍能读到 413 而不是连接被重置。

## H3 — `~/.claude-mem` 与日志

### 改造前实测

- `~/.claude-mem` 总计 **1.5 GB**，其中 `logs/` 占 1.5 GB；其余 `settings.json`、
  `.env`、`cursor-projects.json`、`transcript-watch.json` 合计 < 10 KB。
- `logs/`：**105 个按天文件**，最早 2026-02-13，最大单文件 **105.1 MB**
  （`claude-mem-2026-10-22.log`），当天（11-04）单日 51.6 MB。
- 增长源只有日志；无缓存/临时目录。
- `ClaudeMemLogAppender` 每个日志行 `Files.writeString`，**无轮转、无上限**。

### 改造后的真实验证（不是读代码判断）

用 100 KB / 20 KB 的小阈值跑真实进程：

1. 预置 `logs/`：3 个 500 KB 旧文件 + 1 个 300 KB 当日文件 = 1.8 MB；启动时设
   `CORTEXCE_LOG_TOTAL_SIZE_CAP=200KB`。启动后目录只剩 **28 KB**（旧文件按最旧先删）。
2. 设 `CORTEXCE_LOG_MAX_FILE_SIZE=20KB` 跑 50/200 客户端压测 10 分钟后：
   - 出现 `claude-mem-2026-11-04.20261104-202740-207.log`（20,462 B）与新的
     `claude-mem-2026-11-04.log`（6,204 B）；
   - 目录 **100 KB**（6 个文件），即 ≤ 上限 200 KB + 一个活动文件。
3. 单元测试 `ClaudeMemLogAppenderTest` 用 4 KB 阈值验证：多分片、每片 ≤ 上限 + 一行、
   最新行在日期命名活动文件、按最旧删除、超大单行不丢。

### 运维结论

- 日常无需人工清理；`max-file-size`（100 MB）+ `total-size-cap`（1 GB）即可封顶。
- 需要「按天保留」时复用既有 `cortexce.retention.enabled=true` + `logs-days=N`
  （默认惰性），与 prompts/observations/summaries 共用调度与 dry-run。
- 手工只需清理 `~/.claude-mem/logs/claude-mem-*.log`；同目录 JSON/env 是配置，不要删。
- 数据库行是另一处增长源，用 `cortexce.retention.*`（先 dry-run）。

细节已写入 `docs/DEPLOYMENT.md` / `docs/DEPLOYMENT-zh-CN.md` 的 6.2 节。
