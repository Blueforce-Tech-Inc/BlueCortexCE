# 轮次 I 验证证据：优雅停机、模型熔断、前端剩余交互

> 类型：历史快照（2026-11-04 实测，只读）
> 代码基线：`main` @ `de93943` + 本批 I 改动

## 环境

- 构建：`mvn -DskipTests package` → `backend/target/cortex-ce-0.1.0-beta.jar`
- PostgreSQL：`claude-mem-test-pg`（`127.0.0.1:5433`，pgvector 0.8.1），每个实验一个临时库
- provider：本地 TCP 黑洞（接受连接、永不写响应），用 `OPENAI_BASE_URL` 指过去；这样打的是**读超时**路径，而不是"连接被拒"的快速失败
- 每个实验都用真实进程（`java -jar`），通过 HTTP 走真实入库路径（`/api/ingest/tool-use`），不在测试里 mock 队列

## I1 优雅停机与在途任务

### 改前行为（代码阅读，未实测改前进程）

SIGTERM 后 Tomcat 立即断连；队列里已 `claim` 的行留在 `processing`，由 `StaleMessageRecoveryTask` 在 **10 分钟**阈值后回收。数据不丢，但下一个进程在 10 分钟内看不到这批工作，客户端则可能看到被掐断的连接。

### 改后实现

- `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 45s`
- 新增 `GracefulShutdownCoordinator`（`SmartLifecycle`，phase `Integer.MAX_VALUE - 1`，比 Web 服务器先停）：
  1. 先置 `shuttingDown`，`PendingMessageProcessor` / `PendingMessageEventListener` / `AgentService` 在领取前检查并直接返回，**不再领取新任务**；
  2. 轮询 `ProcessingHeartbeat.inFlightIds()` 等在途任务，上限 `claudemem.shutdown.in-flight-timeout-ms`（默认 **30000**）；
  3. 到期仍 `processing` 的任务按本实例持有的 id 精确 `requeueProcessing`：置 `pending`、清 `started_processing_at_epoch`，**不动 `retry_count`**（停机不是一次尝试）；
  4. `UPDATE ... WHERE status = 'processing'`：等待期间已完成的行走不到，多实例场景也不会碰别人的行。
- `claudemem.async.await-termination-seconds` 默认降到 **5s**：drain 已经给过窗口，留给线程池的只是收尾，不再叠加一个 30s 窗口。

### 实测（把 drain 上限调成 5s 以便一次前台跑完）

```
processing rows before SIGTERM: 3
HTTP during SIGTERM: status=200 body={"status":"accepted"}
SIGTERM -> exit: 10.52 s          (5s drain + 5s 线程池收尾 + Spring 销毁)
rows after graceful exit:
  pending:null:0  ×4              (3 个在途 + 停机期间新入队的那条；started_processing_at 已清，retry_count=0)
```

日志：

```
PendingMessageEventListener  Shutdown in progress, leaving pending message 7ba70c12-... unclaimed
GracefulShutdownCoordinator  Graceful shutdown: requeued 3 of 3 in-flight job(s) after 5014 ms
```

结论：**SIGTERM 期间 HTTP 请求得到明确的 200 响应，没有被掐断**；在途 3 条全部回到 `pending`，不需要等 stale 回收。

### SIGKILL 对比

```
rows after SIGKILL:
  processing:<epoch>:0  ×4        (4 条全部留在 processing，lease 还在)
processing count needing stale recovery: 4
```

随后用 `claudemem.stale-message.threshold-minutes=0` 重启：

```
StaleMessageRecoveryTask  Startup: Recovered 4 stale messages from previous run
```

⇒ SIGKILL 不会丢数据，但要等 stale 阈值（默认 10 分钟）或重启时才会被看见；优雅停机把这段等待降为 0。

生产默认值：drain **30s**、生命周期阶段 **45s**、线程池收尾 **5s**；最坏停机时长约 `30 + 5 + 销毁`。

## I2 模型端熔断

### 改后实现

`ProviderCircuitBreaker`（CLOSED / OPEN / HALF_OPEN）：

- 只有**没拿到响应**的失败计数：`TIMEOUT`、`UNAVAILABLE`。`AUTHENTICATION` / `RATE_LIMIT` / `INVALID_RESPONSE` 说明 provider 可达，会清零计数（401 不会被当成宕机，429 不会被熔断掩盖）。
- 连续 `failure-threshold`（默认 **3**）次后 OPEN：`LlmService.chatCompletionWithUsage` 在**发请求前**返回，不建连。
- `cooldown-ms`（默认 **60000**）后放行**恰好一个**探针：任一响应（包括 401/429/空内容）都关闭熔断；探针超时/连不上则重新 OPEN 一个冷却周期。
- `CIRCUIT_OPEN` 是**新的失败种类且为 transient**：任务回到队列，5 分钟周期后再试，60s 冷却早已结束；停机级故障不会因为"没发出去的请求"耗尽重试预算、丢数据。
- 全部可配置，可整体关闭：`claudemem.llm.circuit-breaker.enabled` / `failure-threshold` / `cooldown-ms`。

### 实测（30 个任务、provider 黑洞、单次调用读超时 2s）

为控制实验时长把读超时缩到 2s；单次调用成本随超时线性变化，比例结论不变。每个任务每次尝试会按 `consistency-samples=3` 发 3 路并发调用。

| 场景 | 30 条全部 `failed` 用时 | 队列消耗速度 | 实际发出的 provider 调用 | 快速失败次数 |
|---|---|---|---|---|
| 熔断关闭 | **19.12 s** | 1.57 jobs/s | 270 | 0 |
| 熔断开启 | **4.46 s** | 6.73 jobs/s | 10 | 260 |

- 熔断开启后 3 次连续 TIMEOUT 立即 OPEN：`Provider circuit opened: 3 consecutive unreachable outcomes (last: TIMEOUT); failing fast for 60000 ms`。
- 10 次实际调用正好是第一批线程池任务；其余 260 次在 `tryAcquire` 处直接返回，**没有建连**。
- 两场景最终都是 30 条 `failed`、`retry_count` 合计 90（30×3），无丢失行；熔断场景 DB 里 `failure_kind` 全部为 `CIRCUIT_OPEN`，可直接观测。
- 单任务层面：熔断前一次失败要等满读超时（生产 120s，且每任务 3 次尝试），熔断后排队中的任务在**毫秒级**返回失败。

单测覆盖状态机：连续失败打开 → 冷却后只放行一个探针 → 探针成功关闭 / 探针失败重新打开；可达类失败不计数；关闭开关后不影响调用。

## I3 前端剩余交互

### 设置页逐项编辑（已补，通过）

`scripts/browser/webui-viewer-e2e.mjs` 在原有布尔开关（`#show-read-tokens`）用例之后新增数值项用例：把 `Observations` 数值输入改成 25/30，保存（`✓ Saved`），刷新页面、重开设置，断言数值仍是新值。

```
PASS: a settings toggle survives change -> save -> refresh
PASS: a numeric setting survived 50 -> 30 -> save -> refresh
```

浏览器 E2E 总数 21 项（门禁第 9 步），全部通过。

### 卡片拖拽排序：功能不存在，无法写 E2E（子模块限制）

核查结果：

- `webui/`（只读子模块）的 viewer 列表按 `created_at_epoch` 顺序渲染，源码里**没有** `draggable` / `onDragStart` / `onDrop` / sortable 依赖；
- 后端没有"排序/位置"字段，也没有任何 reorder 端点（`grep reorder|position` 在 controller/entity 中为 0 命中）；
- 也就是说既没有可拖的东西，也没有能保存顺序的地方。写一条"拖动后顺序保持"的 E2E 只会断言一个不存在的功能。

处理：在 E2E 文件头写明该限制与原因；不改只读子模块。若将来要支持，需要子模块加拖拽交互 + `position` 字段 + 后端 reorder 接口，三者缺一不可。

## 测试与门禁

```
tests=569 failures=0 errors=0 skipped=0
  L1: 543  L2: 19  L3: 6  L4: 1
ALL CHECKS PASSED（含文档一致性与浏览器 E2E 21 项）
```

本批新增 **14** 个测试（协调器 5、熔断状态机 6、`LlmService` 快速失败 1、DB 层 requeue 2），`scripts/test-baseline.json` 与 `VERIFY_MIN_TESTS` 已从 555 抬到 569。

一句话：**I1/I2 已上线，I3 的设置编辑已补测，拖拽排序因前端子模块无此功能记为限制、未实现。**
