# Backend Review Evidence 70 — 第 369 轮本轮新条目的整条正文（第 369 轮迁出）

> **归档规则**：承 `-62` ~ `-69` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 915 字节），工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：71 行 / 7103 字节。**
>
> 本批迁出 **P2-128** 一条新条目。
>
> **本轮代码方向为 JS/TS SDK，文档方向为 SDK README（doc round 269）。**
>
> **本条与前几条不同**：它不是「文档比实现更不完整」，而是**「实现本身在四家之间就不一致」**——
> Java 是唯一没有 100ms 下限的一家，而这一层差异**此前没有任何文档写出来过**。
> 四家的下限**全部经运行时实测**，不是读代码推断。
---

### P2-128: 四家 SDK 的**参数下限**只有 Python 的 README 写了；而 Java 的 `retry.backoff` / `read-timeout` / `connect-timeout` **根本没有 100ms 下限**（实测 1ns 原样、0 原样），且**四家里只有 Go 有测试钉住这些下限**
- **Problem**: 四家 SDK 的**配置项下限**（调用方传一个过小的值会怎样）**只有 Python 的 README 写了出来**，
  其余三家的 README **一个字都没提**；而**四家实现里 Java 根本没有 100ms 下限** ——
  它是唯一一个「正数多小都原样使用」的那一家。
  更要紧的是：**这些下限只有 Go 一家被测试钉住**，Python / JS / Java 三家的下限**没有任何测试断言**，
  改掉不会让任何测试变红。
- **Scope**:
  | 家 | 实现位置 | `maxRetries` 下限 | `timeout` 下限 | `retryBackoff` 下限 | README 写了下限？ | 测试钉住？ |
  |---|---|---|---|---|---|---|
  | Go | `go-sdk/cortex-mem-go/client_impl.go:136/149/152/155` | **1** | **100ms** | **100ms** | ❌ | ✅ 3 个 |
  | Python | `python-sdk/cortex-mem-python/cortex_mem/client.py:90-92` | **1** | **0.1s** | **0.1s** | ✅ | ❌ |
  | JS | `js-sdk/cortex-mem-js/src/client-options.ts:88-90` | **1** | **100ms** | **100ms** | ❌（**本轮已补**） | ❌ |
  | Java | `cortex-mem-spring-integration/cortex-mem-client/src/main/java/com/ablueforce/cortexce/client/CortexMemClientImpl.java:50/52/69` | **1** | **无** | **无 100ms 下限** | ❌ | ❌ |
  Go 的 `connect-timeout` 也有 100ms 下限（`client_impl.go:152`），Java 的 `connect-timeout`（`CortexMemClientImpl.java:65-67`）**只拒绝负数**、没有 100ms 下限。
- **Evidence**:
  **（一）四家全部**运行时实测**（不是读代码推断）**，2026-10-20：
  - **Go**：`go test -run "TestClientTimeoutIsFlooredNotReset|TestClientRetryBackoffFloor|TestClientTimeoutDefaultsUnchanged" -v ./...` → **3 个测试全 PASS**。这 3 个测试在 `go-sdk/cortex-mem-go/config_internal_test.go:19/48/62`，是**第 255 轮**「超时被静默改成默认 30s」那次的回归钉子。
  - **Python**（读 `CortexMemClient` 的私有字段）：
    `timeout=0.01` → **0.1**；`timeout=0` → **0.1**；`timeout=-1` → **0.1**；`max_retries=0` → **1**；`max_retries=-5` → **1**；`retry_backoff=0.01` → **0.1**；`retry_backoff=0` → **0.1**。
  - **JS**（调 `resolveConfig`）：
    `timeout=1` → **100**；`timeout=0` → **100**；`timeout=-1` → **100**；`maxRetries=0` → **1**；`maxRetries=-5` → **1**；`retryBackoff=1` → **100**；`retryBackoff=0` → **100**；`retryBackoff=-1` → **100**。
  - **Java**（反射读 `CortexMemClientImpl` 的私有字段 `maxRetries` / `retryBackoff`，并沿对象图找到 `readTimeout`）：
    `maxAttempts=0` → **1**；`maxAttempts=-5` → **1**；
    `backoff=1ns` → **`PT0.000000001S`（原样，未被抬到 100ms）**；`backoff=1ms` → **`PT0.001S`（原样）**；
    `backoff=0` → `PT0.5S`；`backoff=-5ms` → `PT0.5S`（**非正数回落默认值 500ms，而非抬到 100ms**）；
    `readTimeout=1ms` → **`PT0.001S`（无任何下限）**；`readTimeout=0` → **`PT0S`**；`readTimeout=-1ms` → **`PT-0.001S`（负数被静默接受）**。
  **（二）Java 的 `readTimeout <= 0` 会让每一次请求都失败，而且失败被伪装成成功**：
  实测 `readTimeout=0` → 请求报 `Invalid duration: PT0S`；`readTimeout=-1ms` → `Invalid duration: PT-0.001S`；
  但 `CortexMemClientImpl.getVersion()`（`:597-607`）把异常吞掉并返回 `Map.of("service", "unknown", "version", "unknown")`
  ⇒ **调用方拿到一个「看起来成功」的结果**，而这个「unknown」其实是一个**配置错误**。
  对照：`connectTimeout=-1ms` 在构造期就抛 `IllegalArgumentException: connectTimeout must not be null or negative`
  （`:64-66`）——**同一份配置里，负数超时一个被拒、一个被收**。
  **注**：`getVersion` 的兜底是**有意设计**（紧邻的 `getProjects`（`:611-623`）明确写着「改为传播，Go/Python/JS 三家都这样」），
  故**不作为缺陷记录**；此处只用来证明「误配被伪装成成功」这一**后果**。
  **（三）README 记载的覆盖面**：
  - Python README **三行都写了**（`README.md:172-174` / `README-zh-CN.md:172-174`：`floored at 0.1` / `floored at 1` / `floored at 0.1`）。
  - Go README 的选项表（`README.md:111-115` / `README-zh-CN.md:111-115`）**五项全无下限**，
    尽管 Go 实现的四个下限里**有三个是被测试钉住的**。
  - Java README 的属性表（`README.md:193-194/202` / `README-zh-CN.md:199-200/208`）**四项全无下限**。
  - JS README（`README.md:89-91` / `README-zh-CN.md:87-89`）**本轮已补**三行下限。
- **Status**: ⏸ **记录 + 部分已修** —— **本轮已实施（JS 侧，纯文档、零行为变更）**：
  JS SDK 的双语 README 补齐了三个参数的下限（`timeout` 与 `retryBackoff` 「下限 `100`」、`maxRetries` 「低于 `1` 的值会被抬到 `1`」），
  这三行此前**一个字都没有**。
  **待决（三选一）**：
  ① **只补文档**（推荐）：把 Go / Java 两家 README 的下限也补上，并把 Java 那三行**按真实行为**写
     （`retry.backoff` **无 100ms 下限**、`read-timeout` **无任何下限**），不碰任何行为；
  ② **改行为**：把 Java 的 `retry.backoff` / `read-timeout` / `connect-timeout` 对齐另三家的 100ms 下限
     —— 属**对外行为契约变更**（会把今天能跑的配置改掉）；
  ③ **维持现状**：只在文档里记下四家差异，不补文档也不改行为。
  **另附一个可独立决策的小项**：Python / JS / Java 三家的下限**没有任何测试钉住**，要不要补回归测试
  （Go 已有 `config_internal_test.go` 那 3 个可直接照抄）。
  **本轮只实施 JS 侧（当轮方向），Go / Java 两家属跨家结转，留待后续轮次或用户决策。**
