# 轮次 J 证据：输入敌意面、备份巡检闭环、并发正确性

> 时点记录（2026-11-04），内容不再更新。对应门禁运行日志
> `/var/folders/17/x4gfsdmj74355x331tbfjts80000gn/T/cortexce-verify-67801`。

## J1 输入敌意面

新增 [`L2HostileInputTest`](../../backend/src/test/java/com/ablueforce/cortexce/layers/L2HostileInputTest.java)，
全部请求通过 `java.net.http.HttpClient` 发裸路径，绕开客户端归一化。

**路径穿越**（基线 `GET /viewer-bundle.js` 为 200，证明静态资源确实挂载）：

| 探测 | 状态 |
|------|------|
| `/../application.yml`、`/../../../../etc/passwd` | 400 |
| `/%2e%2e/%2e%2e/etc/passwd`、大写十六进制变体 | 400 |
| `/%252e%252e%252f...`（双重编码） | 404 |
| `/viewer-bundle.js/../../../../etc/passwd` | 400 |
| `/viewer-bundle.js%00`、`/%00viewer-bundle.js` | 400 |
| `/api/logs/../../../../etc/passwd`、其编码变体 | 400 |
| `//etc/passwd` | 404 |

全部响应体不含 `root:x:0:0`、`spring:`、`org.springframework`、`java.lang.`、
`\tat `、`Caused by:`、`.java:`。

**检索参数**：SQL 片段、4 个反斜杠、NUL、emoji+组合符、孤立高位/低位代理对
（CESU-8）、非法 UTF-8 字节、tsquery 运算符 `**` 全部 200 且返回
`observations` 数组；100 KB 查询串在连接器层被拒（400）。**无 5xx。**

发现并修复：NUL 进入 PostgreSQL 文本参数会触发 SQLSTATE 22021，混合检索与词法
检索两条路径都会失败并被记为 ERROR（信息还误导为 pgvector/schema 问题）。
[`SearchService`](../../backend/src/main/java/com/ablueforce/cortexce/service/SearchService.java)
现在在构造参数前丢弃 `\u0000`；纯 NUL 查询退化为 recent 路径。
[`SearchServiceLimitTest`](../../backend/src/test/java/com/ablueforce/cortexce/service/SearchServiceLimitTest.java)
补 2 个 L1 用例。

**结构攻击**：1000 层嵌套 → 400；60,000 元素数组 → 400；重复键 → 200（后者覆盖，
Jackson 语义）；错误类型（数字/数组代替字符串、对象代替路径、数字代替数组、
字符串代替数字）→ 200 或 400（整数到字符串的合法强制转换返回 200），4xx 均带统一信封。

## J2 备份巡检闭环

[`scripts/backup-restore-drill.sh`](../../scripts/backup-restore-drill.sh) 扩展为可定时执行：

- dump 后计算 SHA-256、`pg_restore --list` 验证归档可读、确认含
  `TABLE DATA public mem_observations`、恢复前再次比对哈希；
- 阶段化退出码：0 通过 / 2 前置 / 3 dump / 4 checksum / 5 恢复 / 6 行数 / 7 检索 /
  8 Flyway / 9 未预期；
- JSON 报告写入 `${HOME}/.claude-mem/backup-drill/reports/`，保留最近 30 份；
- `KEEP_DUMP=1` 时保留刚验证过的 dump 作为真实备份，并按 `DUMP_KEEP_DAYS`（默认 30 天）清理；
- `VALIDATE_FLYWAY=1` 才启动 jar 验证迁移历史（离线巡检不再依赖构建产物）。

10 万条 observation / 158 MB 库实测（2026-11-04）：

| 阶段 | 耗时 | 结果 |
|------|------|------|
| dump | 510–513 ms | 3,689,056 B（3.5 MiB） |
| checksum + 归档校验 | 273–290 ms | sha256 前后一致 |
| 恢复到临时库 | 4,673–6,273 ms | 120 MB |
| 6 张表行数比对 | 645–973 ms | 全部一致 |
| 中文检索（`检索`） | 394–513 ms | 命中数一致，探针标题一致 |
| 端到端 | 约 7 s | 通过 |
| `VALIDATE_FLYWAY=1` | +3,208 ms | V25，迁移历史一致 |

失败路径实测：`SOURCE_DB=no_such_db` → 退出码 2，报告 `verdict=fail`、
`failed_stage=precondition`，临时库与 dump 均清理。

运维结论（频率、位置、失败处置）并入
[`docs/DEPLOYMENT.md`](../DEPLOYMENT.md) 与 [`docs/DEPLOYMENT-zh-CN.md`](../DEPLOYMENT-zh-CN.md)
的 §8.1，未新建散文档。

## J3 并发正确性

**发现并修复**：observation 内容去重此前只按 memory scope + 30 秒窗口过滤。同一项目
的两个 session 共享一个 scope，于是并发写入相同内容时，第二个 session 的写入被吞掉，
API 还把第一个 session 的行返回给它。
[`ObservationRepository.findDuplicateByContentHash`](../../backend/src/main/java/com/ablueforce/cortexce/repository/ObservationRepository.java)
增加 `conversation_id` 条件；scope 条件保留，跨项目仍不会互相抑制。文档
[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) / [`docs/ARCHITECTURE-zh-CN.md`](../ARCHITECTURE-zh-CN.md)
同步说明「去重按 Conversation 隔离」。

新增 [`L2ConcurrentSessionIsolationTest`](../../backend/src/test/java/com/ablueforce/cortexce/layers/L2ConcurrentSessionIsolationTest.java)：

1. 两个 session 同一项目并发写**完全相同**的 observation → 两个 200、两个不同 id；
   `/api/observations` 按 `platformSource + contentSessionId` 各自只看到自己那 1 条；
   `/api/search` 同时返回两条且 `content_session_id` 归属正确；
   `/api/context/inject`（按设计为项目级）包含共享内容。
2. 同一 session 10 个并发相同 observation → 10 个响应同一 id，库中 1 行
   （`lockSessionForUpdate` 串行化 + 会话内去重）。
3. 同一 session 10 个并发相同 tool-use hook → 只准入 1 个 pending（`PESSIMISTIC_WRITE`
   行锁 + `existsBySessionAndTool`），异步处理后 1 条 observation。

文档更正：`ARCHITECTURE` 原称「8 个并发相同 hook 落 8 行」。该描述早于 session 行锁，
本次实测为 1 行；已改写为「准入由 session 行锁串行化，不是数据库唯一约束」，并注明
任何绕开锁的新准入路径都会回退该保证。

**真实 provider 限制（如实记录）**：`~/.claude-mem/.env` 中 DeepSeek 凭据实测返回
HTTP 401（`Authentication Fails, Your api key ... is invalid`），本机无其他可用
chat provider。J3 的两组并发实验使用仓库内的确定性离线模型链路
（`OfflineAiTestModels`），走的是真实 HTTP → 队列 → 行锁 → PostgreSQL 路径；
**没有**用真实模型厂商完成端到端调用。

## 门禁

```
L1: tests=545 failures=0 errors=0 skipped=0
L2: tests=27  failures=0 errors=0 skipped=0
L3: tests=6   failures=0 errors=0 skipped=0
L4: tests=1   failures=0 errors=0 skipped=0
total=579（原 569，新增 10）
浏览器 E2E 21 checks；第 9 步通过
```

同批还修了一个既有脆弱测试：`L2SseContractTest` 的 600 请求突发在负载高的机器上
可能跑够慢、令牌桶回填到从未耗尽；该用例改用 100 容量 / 10 每秒的回填，断言不再
依赖调度速度，生产默认值（200 / 100 每秒）未改。
