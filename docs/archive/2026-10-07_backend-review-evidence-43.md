# Backend Review Evidence 43 — 第 273–278 轮已结案的整条条目（第 330 轮迁出）

> **归档规则**：承 `-40` / `-41` / `-42` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**（标题 + Scope / Evidence + Problem + Status），工作文件只保留标题与一行
> 指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/backend-review-findings.md` 写入 P2-92 后再次越过行数阈值
> （实测 **1528 行 / 148352 字节**；`doc-growth-check.sh` 行数越线）而迁出 **5 条**：
> P2-64 / P2-65 / P2-66 / P2-67 / P2-68，分别结案于第 273 / 274 / 275 / 277 / 278 轮。
> 上一批（`-42`）因多迁 4 条留出了余量，本批同法多迁一条（5 条），**不留贴线状态**。
>
> **本批 5 条的共同主题**：**注释/Javadoc 的失实陈述**。P2-64 是序列化行为与文档无关的
> Jackson record 误伤，P2-65 是跨 SDK 的同名函数签名不一致，其余三条是注释描述了
> 代码并不具备的能力或并不遵循的优先级。P2-68 记下了一条值得单独记住的经验：
> 同一份 Javadoc 的人类可读部分与 `@Operation` 机器可读部分**互相矛盾**时，
> 后者才是真的 —— 扫描器必须两处都看，只看 Javadoc 会漏。

## 块 1 / 5：P2-64 全文（第 330 轮逐字迁出）

### P2-64: Jackson 把 record 上的 `isX()` 当属性序列化——Java SDK 每个 PATCH 都多发一个调用方从未设置过的 `empty` 字段
- **Scope / Evidence**: `cortex-mem-client/.../dto/ObservationUpdate.java:37`（`isEmpty()`）、
  `ExtractionResponse.java:51`（`isFound()`）。
- **Problem**: Jackson 把 record 的**组件**与**符合 JavaBeans 约定的访问器**
  （`getXxx()` 任意类型、`isXxx()` 布尔）当属性序列化，故 `isEmpty()` 被发上 wire 成
  `"empty": false`——**每个 PATCH 都带一个调用方从未设置的字段**，与该类 Javadoc 自称的
  "only explicitly set fields are sent" **直接矛盾**。**实测（修复前，编译产物直接序列化）**：
  `{"title":"T","empty":false}`；`ExtractionResponse` 同理多出 `"found"`。**后端忽略未知键**
  （活体 PATCH 带 `empty` 仍 200 且 title 已更新），故**无功能损坏**，但报文与成文契约不符。
  **第 276 轮更正过宽表述并全量复查 21 个 DTO**：受控实验证明**只有** JavaBeans 约定的
  `isXxx()` / `getXxx()` 泄漏为属性，普通无参方法不可见，**修复完整、无遗漏** → [`…-36.md`](../archive/2026-10-06_backend-review-evidence-36.md) 第 7 块（第 291 轮逐字迁出）。
- **Status**: ✅ **已修（第 273 轮）** —— 两个访问器加 `@JsonIgnore`。修复后实测 `{"title":"T"}`，
  且 null→省略、`facts=[]`→照发等**原有语义全部保持**，`isFound()` 仍正确求值；
  **无任何测试断言该字段**。Java SDK **196/0/0/0** 全绿。

## 块 2 / 5：P2-65 全文（第 330 轮逐字迁出）

### P2-65: Python SDK 的 `is_retryable` 只收状态码，而 Go/JS 的**同名函数收的是 error**——跨家移植得到一个永远返回 False 的重试判定
- **Scope / Evidence**: `error.py:135-137`（修复前）、`client.py:199`（内部唯一调用点，**用法本就正确**）、`__init__.py:46,93`（**两个名字都在 `__all__` 里公开导出**）。
- **Problem**: Go `IsRetryable(err error)`、JS `isRetryable(err: unknown)` **都只有一个函数且收 error**；
  Python 有**两个**：`is_retryable(status_code)` 与 `is_retryable_error(err)`，而**与 Go/JS 同名的那个收状态码**。
  机械移植的重试循环写成 `is_retryable(e)` 时，**对每个错误都静默返回 False、不抛异常**——实测
  `RateLimitError`(429) 与 `APIError(502/503/504)` 全部 `False`，而 Go/JS 对同样输入返回 `True`。
  **后果是调用方自己的重试循环永不触发且无任何迹象**；不重试的错误返回 False 是对的，故这个坑**只在本该重试时暴露**。
  README 对两个函数**零提及**。
- **Status**: ✅ **已修（第 274 轮）** —— 按「**纯加宽 / 向后兼容即可修**」，把 `is_retryable` 参数**加宽为 `int | BaseException`**：
  收异常转发 `is_retryable_error`，收状态码**行为一行未变**，其它类型 fail-closed 返回 `False`。
  **双向注入的逐条用例明细（7 失败 / 5 对照）**：逐字迁入 [`…-32.md`](../archive/2026-10-06_backend-review-evidence-32.md)（第 282 轮）。
  否则就是本循环反复在抓的「改了测试没回头改这个数」。**未单方面做的**：把两个函数改名以真正对齐 Go/JS 属**改已发布公开 API 的名字**，
  按规则记录不实施。另记**非缺陷**：Go 独有 `IsInternal`(500)，JS 与 Python 无对应谓词——是 Go 多一个。

## 块 3 / 5：P2-66 全文（第 330 轮逐字迁出）

### P2-66: JS SDK 的 `content`/`narrative` 注释写了一条后端**并不遵循**的优先级规则，而它是四家里唯一不做冲突检测的
- **Scope / Evidence**: `js-sdk/.../dto/observation.ts:56,58`（修复前的两条 JSDoc）、`src/client.ts:363-379`（**原样透传**）；后端依据 `MemoryController.java:317-319` 的 `body.getOrDefault("content", body.get("narrative"))`。
- **Problem**: 原注释称 `content` 走「backend uses "narrative" wire field」、`narrative` 则
  「When both are set, backend processes **whichever is present**」——**两条都与实测不符**。
  `mem_observations` **根本没有 `narrative` 列**（只有 `content`），两个 key 是同一列的两个入口。
  **实测四例**与**四家对拍**（Go `HasConflict()` / Java `IllegalStateException` /
  Python `ValidationError`，**只有 JS 一处检测都没有**）逐字见
  [`…-38.md`](../archive/2026-10-06_backend-review-evidence-38.md) 第 1 块（第 292 轮逐字迁出）。
- **Status**: ✅ **注释已修（第 275 轮，零行为变更）** —— 两条 JSDoc 改为如实描述「`content` 存在时 `narrative`
  一律被忽略，**含 `content` 为 null**」并附实测四例。`tsc --noEmit` 干净、**259/259** 全绿。
  **检测不实施、只记录**：给 JS 补上冲突拒绝是**让原本被接受的调用变成抛错**，属收窄已发布契约；
  JSDoc 已写明「prefer setting exactly one」。

## 块 4 / 5：P2-67 全文（第 330 轮逐字迁出）

### P2-67: `AsyncConfig` 的类 Javadoc 把「按任务超时」列为它提供的能力——**全后端不存在任何超时机制**
- **Scope / Evidence**: `backend/.../config/AsyncConfig.java` 类 Javadoc 第二条「Timeout handling for async
  methods」与行内注释「values from application.yml with defaults」。
- **Problem**: `getAsyncExecutor()` 只配了 core/max/queue/threadNamePrefix/拒绝处理器/关机等待，**无任何按任务超时**；
  全后端搜 `setTimeout` / `TimeoutInterceptor` / `Future.get(` **零命中**。唯一与时长有关的是
  `await-termination-seconds`，它约束**关机时等运行中任务多久**，不是**任务能跑多久**。
  5 个 `@Async` 方法**任一都没有时间上限**。**影响**：一次卡住的 LLM 调用会**长期占住一个池线程**；
  队列打满后拒绝处理器回退到调用线程执行，**把阻塞带回调用方**——而 `@Async` 的前提正是不阻塞调用方。
  第二处较轻的不实：注释称线程池参数「values from application.yml」，而 **yml 里没有 `claudemem.async` 块**，四个 `@Value` 默认值（10/50/100/60）永远生效。
- **Status**: ✅ **注释已修（第 277 轮，零行为变更）** —— 类 Javadoc 如实列出它真正提供的两件事，
  并写明「**不存在按任务超时**」及其搜索证据、讲清 `await-termination-seconds` 的真实语义、指向 P2-67；
  行内注释注明 yml 无该配置块。`mvn -o compile` EXIT=0、**后端 167 测试全绿**。
  **能力本身只记录不实施**：加真正的超时需先定策略（中断，还是跑完但丢弃结果），属设计决策。
  **该类其余部分核实为真**：`AsyncUncaughtExceptionHandler` 与点名的两个 critical 方法确实存在，
  回退处理器也确有日志与兜底 try/catch。

## 块 5 / 5：P2-68 全文（第 330 轮逐字迁出）

### P2-68: `updateObservation` 的 Javadoc 说「null 会被忽略」，其下的 `@Operation` 说「null 会清空」——**后者才是真的**
- **Scope / Evidence**: `MemoryController.java:268`（修复前的 Javadoc）与 `:273`（同一方法的 `@Operation`）。
- **Problem**: 同一方法上两处说明**直接相反**：Javadoc 写「**Null values in the body are ignored**」、`@Operation` 写「**null values clear the field**」。**实测站在 `@Operation` 这边**——第 275 轮探针 PATCH `{"content":null,"narrative":"C"}` 落库 **NULL**（narrative 被丢弃），**不是**「忽略」。**危害在于可信度不同**：`@Operation` 是**机器可读的那一份**（`/v3/api-docs`、SDK 生成器、`docs/API.md` 全以它为准），**读源码的人看到的却是 Javadoc**，即恰好相反的指示——「null 被忽略」也正是 P2-26/27/66 一直在绕开的那条错误行为。
- **Status**: ✅ **已修（第 278 轮，零行为变更）** —— Javadoc 改为如实描述并附活体探针证据、写明机器可读的那份一直是对的。**全后端扫过**：错误表述**仅此一处**，正确表述共 **8 处**。`mvn -o compile` EXIT=0。

