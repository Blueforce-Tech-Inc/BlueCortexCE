# Backend Review — Provenance Notes, batch 2 (复核记录)

> **Archived**: 2026-10-04 (round 238) from [`../drafts/backend-review-findings.md`](../drafts/backend-review-findings.md).
> **Contents**: the `- **复核记录**:` sections of P2-28 through P2-31, moved **verbatim**.
> **Why this batch**: the working file is structurally saturated (28 entries, 26 of them ⏸
> "recorded, not fixing") and round 238's P2-36 pushed it to 1035 lines against the
> `MAX_LINES=1000` threshold. These four sections are *provenance* — which round found
> the issue and how the evidence was gathered — not the decision reasoning the ⏸ rule
> protects. **Scope / Problem / Evidence / Status stay in the working file**, each replaced
> by a one-line pointer. The same text is stored verbatim per round in `patrol-rotation.md`
> and `doc-review-task.md`, so it is restorable without loss from either.
> **Continues** the batch archived in
> [`2026-10-03_backend-review-provenance.md`](2026-10-03_backend-review-provenance.md)
> (round 237, P2-22 through P2-27), which is itself now immutable.
> **Verbatim equality against `git show HEAD` was verified before the source lines were removed.**
> **Do not modify this file.**

### P2-28（复核记录原文）
- **复核记录**: 第 230 轮代码方向发现（首次审 `TestController`）。取证：活体
  三端点分别 curl 取状态码；读 `TestController.java:118-123` 确认
  `testLlm().getBody()` 丢弃了 `ResponseEntity` 的状态部分；
  `grep -rn "api/test"` 确认四家 SDK **零命中**，而项目自带的
  `scripts/test-llm-provider.sh` **只调用 `/llm` 与 `/embedding`**（第 43、76 行
  正是靠 `%{http_code}` 判定）、**从不调用 `/all`** —— 说明仓库自身也绕开了它。
  **探针自身错一次并先识别再采信**：统计 controller 数量时用
  `ls ... | grep -v Test` 过滤测试文件，结果把 `TestController.java` 一并滤掉，
  数出 12 而记录是 13；改用 `grep -rln "@RestController"` 复核得 13，
  **确认是过滤器缺陷、既有记录无误**，没有据此改写任何结论。


### P2-29（复核记录原文）
- **复核记录**: 第 231 轮代码方向（Java SDK）发现。切入点是 Java SDK 的
  `ObservationRequest.toWireFormat()` 发了 `toolResponse`/`promptNumber`/`source`
  却发现它们**都不参与去重**。取证链：读 `AgentService.java:147-158` 与
  `PendingMessageRepository.java:25,38` 确认键与判定条件；`grep` 确认后端
  **零引用** `tool_use_id`（真正的键不是它）；预置 in-flight 行做确定性丢弃
  复现；并发 8 发验证原子性；`pg_constraint` + 事务内重复插入验证约束不存在；
  `grep -rn "UNIQUE" backend/src/main/resources/db/migration/` 确认无迁移创建。
  **探针自身错一次并先识别再采信**：并发探针脚本在同一次运行里查库，
  读到 0 行、险些据此断言「事件根本没落库」；复查发现是**落库晚于响应返回**，
  稍后重查为 8 行——**先识别为探针时序问题再采信**，未据此改写结论。
  另修正了自己一次统计口径错误：最初按 `(session, hash)` 分组把
  `read`/`edit`/`write` 误称为「大小写孪生」得 307 组，改用
  `(session, lower(tool_name), hash)` 精确分组后为 **1 组**。


### P2-30（复核记录原文）
- **复核记录**: 第 232 轮代码方向（Go SDK）发现，359 测试全过。本轮**四个新角度核实无误**：
  错误分类法（`statusCodeToError` 覆盖 11 个状态码、`IsRetryable` 判定与注释逐条吻合）、
  查询参数编码（走 `url.Values.Encode`，无注入面）、响应体上限（`LimitReader` 多读 1 字节后
  显式报错，**不会**退化成 JSON 截断错误）、DTO 时间字段（建模为 `string`/`int64`，无解析失败面）。
  **一个假设在写成发现前被证伪**：怀疑 `GetObservation` 未找到时返回 `nil, nil` 会让调用方
  空指针崩溃——**四家其实完全一致且都有文档**（Java 返回 `null`、Python 返回
  `Observation | None`、JS 返回 `Observation | null`、Go 返回 `nil` 且接口注释写明），
  **不是缺陷**。取证：临时 httptest 文件
  （跑完即删，工作区无残留）确认 Go 实际出参；`grep` 逐家读源码确认四家行为；
  活体 curl 确认后端钳位值。**探针自身错一次并先识别再采信**：统计根模块测试数时用
  `^--- PASS` 只数顶层用例得 270，与基线 359 不符；改用含子测试的模式逐模块统计得
  **299 + 8 + 13 + 12 + 27 = 359**，**确认是计数口径问题、既有记录无误**。


### P2-31（复核记录原文）
- **复核记录**: 第 233 轮代码方向（Python SDK）发现。取证：读四家源码确认守卫形态；
  活体 curl 四种取值裁定后端行为；`grep -rn "max_chars" tests/` 确认**无既有测试
  钉死该行为**（故是改正而非与测试冲突）。**顺带核实无误**：Python 五处路径拼接
  （`session_id` / `observation_id` / `template_name`）**全部**用
  `quote(x, safe='')`，与 Go 的 `url.PathEscape`、JS 的 `encodeURIComponent` 一致，
  **四家无一处漏转义**；请求超时恒有设置（下限 0.1s）；重试为线性退避 + ±25% 抖动、
  仅重试瞬时错误。

