# Backend Review Scope Evidence 10 — 第 364 轮待决条目 `Scope` 段的续行（第 364 轮迁出）

> **归档规则**：承 `-9.md` 的体例（第 254 轮经用户明确决策）——`- **Scope**` 与
> `- **Evidence**` 可与实测记录一并迁入归档，各留一行指针。**Scope 内多为文件
> 行号与实测细节，属可复现证据而非决策推理**；决策推理所在的 `- **Problem**` 与
> `- **Status**` **一律留在工作文件**，未结案条目本身不迁出。
>
> **本批与 `-9.md` 的差别**：`-9.md` 搬的是整段 `- **Scope / Evidence**`；
> 本批搬的是**已经只剩一行指针的条目里、指针之后仍拖着的续行**——
> 那些续行同样是文件路径、行号、代码片段与受控实验记录，**不是决策推理**，
> 故一并搬走；工作文件保留指针行并**追加 `-10` 引用**（不得断链）。
>
> **渲染保真**：归档里每条用同缩进的列表项包住续行，**相对缩进与工作文件原样一致**——
> 否则 6 空格缩进会从「列表项内的段落」变成「顶层代码块」，是迁移引入的失真。
>
> **P2-91 的一处例外**：其 `Scope` 段含一张「位置 / 标签 / 实际类」对照表与紧随其后的
> **解读散文**。本批**只搬表格**（纯证据），**解读散文留在工作文件**——它是对表的
> 结论而非表本身，属谨慎口径。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 **15 条**待决条目 `Scope` 段的续行，共 **48 行 / 4327 字节**。
> 迁出前实测 **1453 行 / 148372 字节**；本归档实测 **164 行 / 8726 字节**。

### P2-20
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  **本轮（第 275 轮）再迁出**活体实测清单与排查陷阱原文 → [`2026-10-06_backend-review-evidence-25.md`](../archive/2026-10-06_backend-review-evidence-25.md)。

### P2-56
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  **第 282 轮的范围更正与完整活体对拍**（用方 3 个文件 5 处 / 绕过 3 个控制器 6 个参数、六组请求的 HTTP 与 body 原文、
  `-1` 与 `16` 的落库记录）见 [`2026-10-06_backend-review-evidence-31.md`](../archive/2026-10-06_backend-review-evidence-31.md)。

### P2-58
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  **第 277 轮再迁出** 7 字段 ↔ 迁移来源的逐行对照表与取样方法 → [`2026-10-06_backend-review-evidence-28.md`](../archive/2026-10-06_backend-review-evidence-28.md)。

### P2-75
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  同文件 `:427`（`@DeleteMapping`）；文档侧 `docs/API.md:593` 起的中英双语小节 + 两处 `@ApiResponse` 注解。

### P2-76
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  `:151-176`（端点）；已做直通的三个点：`FeedbackController.java:75-79`、
  `ObservationsController.java:324-328` 与 `:352-355`；`DemoErrors.clientStatus` 为共用机制。

### P2-77
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  `:74-84`（`/memory/icl`）、`:121` 起（`/memory/icl/truncated`）与 `/memory/experiences/filtered`；
  对照组同文件外的 `ExperiencesController` 两个 `/demo/*` 端点。

### P2-79
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  （`boolean allowCredentials = origins.length > 0 && !origins[0].equals("*");`），
  与紧邻的注释「Determine if we should allow credentials (only if not using wildcard)」。

### P2-82
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  对照 `go-sdk/cortex-mem-go/client_impl.go:173-175,243-250`
  与 `js-sdk/cortex-mem-js/src/client.ts:655-699`。

### P2-87
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  （另一进程于 `6e5890d` 新增的双语段落）：
  > The backend/WebUI relationship is version-paired rather than "latest wins".
  > For the current reviewed capability set, **the backend reference is `ed37a1b227b6067befe5c9ada331989d02cbfad0`**
  > and the compatible `webui/` submodule commit is `72e7804b13f89b03177f746867e0dd341fb12c8a`.

### P2-88
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  `cortex-mem-spring-integration/cortex-mem-starter/src/main/java/com/ablueforce/cortexce/autoconfigure/CortexMemAutoConfiguration.java`
  - `:80` —— `SpringAiAdvisorConfiguration` **整类**带
    `@ConditionalOnProperty(prefix="cortex.mem", name="retrieval-enabled", matchIfMissing=true)`
  - `:83-92` —— `cortexSessionContextBridgeAdvisor` bean 声明在该类**内部**，
    再被 `:85` 的 `context-bridge-enabled` 门控
  - `:148-154` —— `AopCaptureConfiguration`（含 `cortexToolAspect`）另受
    `capture-enabled` 门控，**不**在检索门控之内

### P2-90
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  与 `README-zh-CN.md`（`:13`、`:177`、`:187-188`）

### P2-91
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：


  | 位置 | 标签 | 实际类/主题 |
  |---|---|---|
  | `config/QueueHealthIndicator.java:12` | `P1-2:` | Custom health indicator for message queue monitoring |
  | `service/StaleMessageRecoveryTask.java:17` | `P1-1:` | Stale message recovery task |
  | `service/ContextCacheService.java:16` | `P2-1:` | Context caching service |


### P2-93
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  后端 `MemoryController.java:253-254`（决定性分叉）、
  `go-sdk/cortex-mem-go/dto/misc.go:26`（`omitempty`）、
  `js-sdk/cortex-mem-js/src/client.ts:355`（`if (req.comment)`）、
  `python-sdk/cortex-mem-python/cortex_mem/client.py:615`（`if comment:`）、
  对照组 `cortex-mem-spring-integration/.../CortexMemClientImpl.java:252`（`if (comment != null)`）。

### P2-95
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  而 `:624-640` 的 **17 行**是 Markdown 引用块散文；
  `docs/ARCHITECTURE-zh-CN.md` **同型**：`:546` 开启、`:715` 闭合，`:616-629` **14 行**散文。

### P2-112
（工作文件原指针行：`- **Scope**: → [scope-9](../archive/2026-10-19_backend-review-scope-evidence-9.md)`）

- **Scope**（第 364 轮迁出）：

  - `cortex-mem-spring-integration/cortex-mem-starter/src/main/java/com/ablueforce/cortexce/autoconfigure/CortexMemAutoConfiguration.java:39`
    —— `@ConditionalOnProperty(prefix = "cortex.mem", name = "base-url")`
  - `cortex-mem-spring-integration/cortex-mem-client/src/main/java/com/ablueforce/cortexce/client/CortexMemClientImpl.java:60-63`
    —— `if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl must not be null or blank");`
  - **受控实验**（临时 `ApplicationContextRunner` 探针，并经 `YamlPropertySourceLoader` 装载**真实 `application.yml`**；
    探针编译到 `/tmp`，跑完即删，**仓库零改动**）。三种 yml 写法 ——
    `base-url: ${CORTEX_MEM_URL:}`、`base-url:`（YAML null）、`base-url: ""` ——
    **全部**得到 `STARTUP_FAILURE: IllegalArgumentException: baseUrl must not be null or blank`；
    另测 `base-url: "   "`（纯空白）**同样**失败。
