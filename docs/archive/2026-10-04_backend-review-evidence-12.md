# Backend Review 证据归档（第十二批）

> **Moved by**: 定时项目维护任务，第 259 轮（2026-10-04）。
> **Reason**: 承第 258 轮第二批，把**证据类**小节逐字迁出，
> 工作文件只留一行指针。依据第 254 轮经用户决策放开的规则，
> ⏸ 条目的 Scope / Evidence 可迁入归档，**Problem 与 Status 一律留在工作文件**。
> **本次严格按第 258 轮写进 `## Processing Rules` 的两条硬约束执行**：
> ①**单遍顺序重建**，不在原始索引上算好区间后按升序改写同一列表；
> ②边界断言同时覆盖 `- **`、`### ` **与 `## `** 三种行首。
> 归档文件创建后不再修改。

## ### P2-24: V17 反馈机制整体未接线 —— 实体还映射了一个不存在的列

- **Verification**（2026-10-03）：修复后 `mvn package` 通过、后端启动干净、
  日志中 `QuerySyntaxException` / `column does not exist` **零命中**。
  SQL 层双向证明（各自独立连接，避免事务中止干扰）：
  含幽灵列的 6 列 SELECT → `FAILS: column "created_at" does not exist`；
  修复后的 5 列 SELECT → **OK**。

## ### P1-1: `CortexSessionContextBridgeAdvisor.adviseStream` 依赖普通 ThreadLocal，流式下既丢捕获又泄漏会话

- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-8: 读取侧没有维度路由 —— 写入按维度分列，检索恒定比 `embedding_1024`

- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-14: `findNewObservations` 零调用方——增量抽取从未实现，却有索引为它而建

- **量化证据（第 202 轮补测）**：在真实库上按 `refined_from_ids` 统计，
  18,373 次带输入的抽取共涉及 **3,885 个不同观测**，其中 **3,880 个（99.9%）
  被送入 LLM 超过一次**，**单个观测最多被重复发送 689 次**。
  这把「会重复」从代码推断变成了实测幅度。（另一条独立的量化视角：
  当前候选窗口与全部历史输入的交集为 0，说明窗口确实只随时间前移——
  旧观测是**掉出**窗口而非被去重排除。）

## ### P2-17: `EXTRACTION_MAX_BATCHES` 在随附默认值下永远不可能生效

- **精确边界**（避免说成「无条件失效」）：它并非任何时候都无效。当
  `EXTRACTION_MAX_CANDIDATES > EXTRACTION_BATCH_SIZE × EXTRACTION_MAX_BATCHES`
  （随附默认下为 200）时它才开始起作用。所以**单独调高它没有任何效果**，
  必须同时调高候选上限；单独调低到 ≤5 才有效。

## ### P2-25: `maxChars` 的 Swagger 描述承诺了一个后端并不存在的「0 = 默认」分支

- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-26: Go SDK 的 `omitempty` 让 `facts` / `concepts` / `extractedData` 无法清空，且静默返回「updated」

- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-28: `/api/test/all` 丢弃两个子处理器的状态码，故障时仍返回 200

- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-31: Go SDK 仍把负数 `maxChars` 发上 wire，注入被钳到 100 字符

- **Reproduction**: 原始实测记录已归档 → [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md)（第 241 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-32: 两个 Dockerfile 都不设 `SERVER_ADDRESS`，默认部署下服务对外不可达；根镜像的 healthcheck 还写死了端口

- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-34: `GET /api/logs` 的 Swagger 示例漏掉 `files`，且把绝对路径写成 `/logs`

- **实测记录**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成**默认最大值**——请求 50ms 实际得到 30s

- **Scope**: `go-sdk/cortex-mem-go/client_impl.go` 的 `NewClient` 配置归一化段。

## ### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成**默认最大值**——请求 50ms 实际得到 30s

- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-54: Python SDK 另有两处裸 TypeError——且既有测试的 docstring 早已写明我踩的那个坑

- **Scope**: `python-sdk/cortex-mem-python/cortex_mem/client.py` 的
  `retrieve_experiences(count=...)` 与 `get_extraction_history(limit=...)`。

## ### P2-55: 四个 demo 为同一件事立了同一份文法契约，却 2:2 分裂——而且**与后端一致的那两家是「碰巧」一致的**

- **Scope**: `examples/cortex-mem-demo/.../DemoParams.java`（`boundedInt`）、
  `python-sdk/cortex-mem-python/examples/http-server/app.py:126-152`（`_INT_RE`）、
  `go-sdk/cortex-mem-go/examples/http-server/main.go:60-68`（`strconv.Atoi(strings.TrimSpace(s))`）、
  `js-sdk/cortex-mem-js/examples/http-server/app.ts:74-81`（`parseIntParam` + `/^[+-]?\d+$/`）。

## ### P2-55: 四个 demo 为同一件事立了同一份文法契约，却 2:2 分裂——而且**与后端一致的那两家是「碰巧」一致的**

- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-56: Java demo 里四个控制器有三个用了共享校验类，第四个把两个数值参数整个绕过去了——**而那个类的 Javadoc 宣称自己覆盖了所有控制器**

- **Scope**: `examples/cortex-mem-demo/src/main/java/com/example/cortexmem/ExperiencesController.java:41`
  （`@RequestParam(defaultValue = "4") Integer count`）与 `:101`
  （`@RequestParam(defaultValue = "0") Integer maxChars`）；对照
  `DemoParams.java`（`boundedInt` + `InvalidParamAdvice`）、`DemoErrors.java`。

## ### P2-56: Java demo 里四个控制器有三个用了共享校验类，第四个把两个数值参数整个绕过去了——**而那个类的 Javadoc 宣称自己覆盖了所有控制器**

- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。

## ### P2-57: Java SDK 的默认 base URL 是四家里唯一用主机名的——而后端**只绑 IPv4 回环**，一个 JVM 开关就能把它变成连不上

- **Scope**: `cortex-mem-spring-integration/cortex-mem-client/.../config/CortexMemProperties.java:12`
  （`private String baseUrl = "http://localhost:37777";`）。

## ### P2-57: Java SDK 的默认 base URL 是四家里唯一用主机名的——而后端**只绑 IPv4 回环**，一个 JVM 开关就能把它变成连不上

- **Evidence**: 用 JDK 自带 `HttpClient` 对同一后端做对照（脚本 `/tmp/r259v6/V6.java`）：

  | JVM 参数 | `http://localhost:37777` | `http://127.0.0.1:37777` |
  |----------|--------------------------|---------------------------|
  | 默认（允许回退） | HTTP 200 | HTTP 200 |
  | `-Djava.net.preferIPv6Addresses=true` | **FAILED (ConnectException)** | **HTTP 200** |

  `preferIPv6Addresses` 是**有文档的、用户在双栈部署中确实会设置的** JVM 参数。
  一旦设置，**用户什么都没改，Java SDK 的默认端点就从「能用」变成「连不上」**，
  而同样参数下另外三家的默认值不受影响。
  补充：Python 的 `socket.create_connection('localhost', …)` 会逐地址回退、本机可连，
  但在**只把 `localhost` 解析到 `::1`** 的系统上会同样失败——故这是**平台相关**而非必然。
