# Backend Review Evidence 67 — 第 367 轮本轮新条目的整条正文（第 367 轮迁出）

> **归档规则**：承 `-62` / `-63` / `-64` / `-65` / `-66` 的体例，迁出**本轮新写**的条目全文
> （写作时 findings 的字节余量只剩 3216 字节），工作文件只保留标题与 `- **Status**` 一行
> （末尾追加本归档指针）；P2-90 的 `- **Status**` 段是**新增**的，亦整体迁入本归档。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 **P2-123** / **P2-124** 两条新条目 + **P2-90** 的一条新 Status 段。
>
> **本轮代码方向为 Go SDK，文档方向为用户指南（doc round 267）。**
> P2-123 是**代码方向**的产物（已修，零行为变更）；P2-124 是**文档方向**的产物（已修，双语）。
>
> 迁出前实测 **1416 行 / 144567 字节**；本归档实测 **129 行 / 13574 字节**。

### P2-123: Go SDK 的 `doFireAndForget` 把「重试 POST 是安全的」写成**承重注释**，而它点名了**不在链上的处理器**、归因了**错误的机制**、覆盖了**三条里只有一条受保护**的操作
- **Scope / Evidence**:
  - **注释原文**（`go-sdk/cortex-mem-go/client_impl.go:380-392`，第 214 轮写入）：
    「Retrying a POST is only safe because the backend deduplicates: RecordObservation
    lands on POST /api/ingest/tool-use -> **IngestionController.handleObservation** ->
    AgentService.saveObservation, which hashes title/narrative/facts/concepts and
    returns the existing row when a match exists inside a 30-second window. The
    default retry span is ~1.5s …, so a retry always lands well inside that window
    and **cannot create a duplicate**. Raising RetryBackoff or MaxRetries past ~30s
    total would break that guarantee — re-check the window before changing either.」
  - **错点一：处理器名不在链上**。`IngestionController` 的类级路由是 `@RequestMapping("/api/ingest")`（`:46`）；
    `@PostMapping("/tool-use")`（`:95`）绑的是 **`handleToolUse`**（`:110`）；
    `handleObservation`（`:315`）绑的是 `@PostMapping("/observation")`（`:303`）——
    **那是另一个端点**。真实链路是
    `POST /api/ingest/tool-use` → `handleToolUse` → `AgentService.processToolUseAsync`。
  - **错点二：归因错了机制**。注释把安全归给「30 秒内容哈希窗口」，但真正拦住一次**重试**的是**更靠前、
    且完全没有时间窗**的一层：`processToolUseAsync` 先查
    `PendingMessageRepository.existsBySessionAndTool`（`AgentService.java:149-158`），
    其 JPQL 条件是 `contentSessionId AND toolName AND toolInputHash AND status <> 'failed'`
    （`PendingMessageRepository.java:38-40`）—— **无任何时间窗**，命中即 `return`
    （日志「Duplicate tool-use event skipped」）。
    `saveObservation` 的 30 秒窗口是**第二道**防线，且它比较的是两次**观测落库时刻**，
    相隔的是 **LLM 延迟**而不是重试跨度。
  - **错点三：「cannot create a duplicate」过强**。pending 检查是 **check-then-insert**，非原子；
    `PendingMessageEntity` 声明的 `@UniqueConstraint` 因 `ddl-auto: none` **在库里不存在**
    ⇒ 这正是既有条目 **P2-29** 记录的内容，**本条不重复它**，只指出注释把它写成了保证。
  - **错点四：覆盖范围被夸大**。`doFireAndForget` 服务**三条**捕获操作
    （`client_methods.go:53 / 65 / 80`：`RecordObservation` / `RecordSessionEnd` / `RecordUserPrompt`），
    而注释只论证了其中一条。另外两条**后端并不幂等**：
    - `RecordUserPrompt` → `POST /api/ingest/user-prompt` → `handleUserPrompt` →
      `userPromptRepository.save(prompt)`（`IngestionController.java:267`）—— **纯插入，无去重**。
      **活体实测（2026-10-20，带对照）**：同一 body 连发两次 → `mem_user_prompts` **2 行**；
      **反向对照**（换一个新 session 连发两次）同样 **2 行** ⇒ 不是「首次建会话」造成的；
      **对照**（只发一次）**1 行** ⇒ 计数探针能分辨 1 与 2。测试行已逐条清理（复核剩 0 行）。
    - `RecordSessionEnd` → `POST /api/ingest/session-end` → `handleSessionEnd` →
      `summaryGenerationService.completeSessionAsync` → `saveSummary` →
      `summaryRepository.save(summary)`（`SummaryGenerationService.java:165`）—— **也是纯插入**；
      而 `completeSessionForSummary`（`SessionManagementService.java:97-113`）对 session 本身幂等
      （已是 `completed` 就不再改），但**总是返回该 session** ⇒ 第二次调用照样走 LLM、再插一条 summary
      （并再广播一次 SSE `new_summary`）。
  - **错误是第 214 轮引入的**：`patrol-rotation.md` 的第 214 轮 History 行（2026-10-03 17:50）
    记的就是同一条错误链；同一错误也出现在 `docs/archive/2026-10-03_doc-review-history-108-119.md:19`。
    **历史正文不改写**（追加式审计链的规矩），更正只落在本轮。
  - **旁证：另三份 SDK 的同类注释是准的**。Java SDK README 的 retry-scope 段写
    「Retrying `recordObservation` is safe from the double-record point of view, because the
    backend's tool-use dedup absorbs a byte-identical resend」——**限定在 `recordObservation`**，
    没有把保证外扩到另两条。**故过宽的声明只在 Go 的代码注释里**。
- **Problem**: 这段注释是**承重**的——它存在的唯一目的就是回答
  「重试 POST 通常不安全，这里安全吗？」。它给出的答案在**四个层面**都不准确：
  ①名字错了（`handleObservation` 不在这条链上）；②机制错了（把 30 秒内容哈希窗口当成防线，
  而真正**没有时间窗**的那道防线没写）；③范围错了（它挂在**三条**捕获操作共用的
  `doFireAndForget` 上，却只论证了其中一条，另外两条**后端根本不幂等**，
  一次「响应丢失型重试」就会多出一行 prompt 或一条 summary）；④**「cannot create a duplicate」
  把一个不成立的保证写成了保证**——而保证不成立时，读者正是最需要被告知的那批人。
  **为什么本轮只改注释、不改行为**：让后端幂等（`/api/ingest/user-prompt` 按
  `(content_session_id, prompt_number)` 去重或改 upsert；`/api/ingest/session-end` 在
  「该 session 已有 summary」时跳过）会**改变重复请求的可观测结果**，属对外行为契约变更；
  收窄 SDK 侧重试范围（只对 `RecordObservation` 重试）同样是行为变更，且**四家 SDK 都这么做**，
  要改就是四家一起改。按既定纪律只记录不单方面实施。
- **待决策（三选一）**：①**只更正注释**（**本轮已实施的部分**）；
  ②**让后端幂等**（上述两条路径去重或 upsert）——属**向后兼容的新增行为**，
  但要定「历史数据怎么办、`prompt_number` 撞号如何区分、session-end 已有一条 summary 时怎么办」；
  ③**SDK 侧收窄重试范围**——属行为变更（失败率会变），且要四家一起改。
  **任一选项都应先补后端对这两条路径的幂等性测试**——目前**零覆盖**。
- **Status**: ⏸ **服务端幂等化与 SDK 重试范围收窄留待用户决策**（对外行为契约变更）—— **注释层失实陈述本轮已修**（Go SDK 一个文件，**可执行行 0**）：链改对（`handleToolUse`）、机制写对（**第一道**防线是 pending 层 `(session, tool, SHA-256(tool_input))` + `status <> 'failed'`，**无时间窗**；30 秒内容哈希窗口是**第二道**防线）、范围写对（另两条**不幂等**）、「cannot create a duplicate」改为「**保证不绝对**」（见 P2-29）。**三选一见归档正文**。 条目全文已逐字迁入 [`…-67.md`](../archive/2026-10-20_backend-review-evidence-67.md)（第 367 轮）。

### P2-124: 部署指南的限流段把 `CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS` 写成「**滑动窗口**」，而实现是**固定窗口** —— 边界处上限可以**翻倍**；同段还把 `retry_after` 说成「窗口长度」，实际是**当前窗口的剩余秒数**
- **Scope / Evidence**:
  - **文档原文**（`docs/DEPLOYMENT.md:566` 与 `docs/DEPLOYMENT-zh-CN.md:555` 的变量表）：
    `CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS` | `60` | **Sliding-window length in seconds** /
    **滑动窗口长度（秒）**。
  - **实现是固定窗口**：`RateLimitService.SlidingWindow.tryIncrement`（`RateLimitService.java:59-69`）：
    `if (now - windowStart >= windowSeconds) { count.set(0); windowStart = now; }` ——
    到期**整窗重置**，请求**不会逐个老化**。类名与后端自己的 javadoc
    （`:51-54`「P1: Sliding window counter」）也都写着 sliding。
  - **可观测后果（活体实测，2026-10-20，同一会话）**：
    批 0 于 t+0.1s 发 1 个 → `200`（开窗）；
    批 A 于 t+58.1s 发 9 个 → **全部 200**（计数到 10）；
    批 B 于 t+60.6s 发 **10 个 → 全部 200**（计数恰在边界处归零，
    而先前那 10 个**才 2.5 秒大**，按真滑动窗口应当**全部被拒**）；
    批 C 同会话再发 1 个 → `429 {"error":"Rate limit exceeded","retry_after":"60"}`。
    ⇒ **约 2.5 秒内放行了 20 个请求**，第 21 个才被拒。
  - **`retry_after` 的描述也不对**：`getRemainingSeconds()`（`RateLimitService.java:79-83`）
    = `max(0, windowSeconds - (now - windowStart))`，即**当前窗口的剩余秒数**，不是窗口长度。
    活体实测：开窗后第 1 秒的请求读到 **`"retry_after":"59"`**（不是 60），
    而紧接着开窗的新窗口又给出 `"60"`（批 C）。文档那段 JSON 示例里的 `60` 取决于
    第 11 次请求**是否落在开窗的同一秒内**。
  - **文档的结论仍然成立**：「按它退避即可确保拿到新的额度」是对的
    （等满剩余时间 ⇒ 窗口到期 ⇒ 计数归零）；**对值的描述**不对。
  - **同段其余断言逐条为真**：限流器**只有 1 个调用点**（`IngestionController.java:132`，
    全仓 `rateLimitService.tryAcquire` 仅此一处），只作用于 `POST /api/ingest/tool-use`、
    以内容会话 id 为键；另三个 ingest 端点与全部读端点**不限流**；活体复核
    同一 session 前 10 次 tool-use 返 `200`、第 11 次返 `429`；
    **13 次 `GET /api/search` 全部 200**（首轮探针因漏 `project` 参数返 400，
    是**探针自身**的问题，补上后全 200）；`cleanup-interval` 确实是内存控制项
    （`MAX_WINDOWS = 10000`，`RateLimitService.java:46`）。
- **Problem**: 「滑动窗口」这个说法**描述了一个代码没有实现的算法**，而它的**可观测后果**
  是限额在窗口边界处**可以翻倍**（实测 2.5 秒内 20 个请求通过）。运维读到
  「每窗口 10 个请求」很容易把它当成**任意 60 秒区间内的硬上限**，而实际上不是。
  这与本仓库已记录的 P2-119（demo 暴露面）、P2-122（SDK 的 API 密钥零保护）是同一族问题：
  **文档里的一个词，让读者对系统的实际约束产生系统性偏高的估计**。
  **为什么本轮只改文档、不改限流器**：把固定窗口改成真滑动窗口 = **改变限流行为**
  （边界处的放行数会变）= 对外行为契约变更，按既定纪律不单方面实施。
- **待决策（三选一）**：①**只更正文档**（**本轮已实施的部分**）；
  ②**把限流器改成真滑动窗口**（逐请求时间戳）——属行为变更，且要动 `mem_*` 之外的内存结构与清理逻辑；
  ③**把类名与 javadoc 的 "sliding" 改成 "fixed"**（**纯改名，零行为变更**）——
  与①配套，可让后端源码里的用词也不再误导。
  **任一选项都应先补后端对该限流器的测试**——目前**零覆盖**。
- **Status**: ⏸ **限流器要不要改成真滑动窗口留待用户决策**（属行为变更）—— **失实陈述本轮已修**（双语，纯文档）：变量表的「Sliding-window / 滑动窗口」改为「**固定**窗口、到期整窗重置」；`retry_after` 改为「**当前窗口的剩余秒数**」（≤ 窗口长度，第 11 次请求实测读到 `59`）；并**新增一段实测**（边界处 20 个请求在约 2.5 秒内通过、第 21 个才 429）。**三选一见归档正文**。 条目全文已逐字迁入 [`…-67.md`](../archive/2026-10-20_backend-review-evidence-67.md)（第 367 轮）。

- **Status**: ⏸ **形态问题（「要不要彻底不写死数字」）仍留待用户决策** —— **数字部分本轮已修（第 367 轮，双语，纯文档）**：本轮**亲跑复测**六个模块（`=== RUN` 计数，含子测试）得根模块 **303**（core 236 + dto 67）+ eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27 ⇒ **合计 363**，与本条记的归因（`7a78266` 新增恰好一个顶层测试）**完全吻合**；**双语的 6 处数字已改**：无日期的失实断言改为实测值并**加上测量日期**，带日期的快照同步为 2026-10-20 的实测。**本行替换了此前的「⏸ 记录不修」**——那一条针对的就是形态问题。
