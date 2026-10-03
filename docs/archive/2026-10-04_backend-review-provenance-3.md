# Backend Review — Provenance Notes, batch 3 (复核记录)

> **Archived**: 2026-10-04 (round 241) from [`../drafts/backend-review-findings.md`](../drafts/backend-review-findings.md).
> **Contents**: the `- **复核记录**:` sections of P2-24 and P2-32 through P2-37, moved **verbatim**.
> **Why this batch**: round 241's P2-37 pushed the working file to 1033 lines against the
> `MAX_LINES=1000` threshold, the ninth time it has needed compressing. These sections are
> *provenance* — which round found the issue and how the evidence was gathered — not the
> decision reasoning the ⏸ rule protects. **Scope / Problem / Evidence / Status / Reproduction
> stay in the working file**, each replaced by a one-line pointer. The same text is stored
> verbatim per round in `patrol-rotation.md` and `doc-review-task.md`, so it is restorable
> without loss from either.
> **Continues** batches 1 and 2 — `2026-10-03_backend-review-provenance.md` (round 237,
> P2-22 through P2-27) and `2026-10-04_backend-review-provenance-2.md` (round 238,
> P2-28 through P2-31) — both of which are immutable.
> **Verbatim equality against `git show HEAD` was verified before the source lines were removed.**
> **Do not modify this file.**

### P2-24（复核记录原文）
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）


### P2-32（复核记录原文）
- **复核记录**: 第 234 轮文档方向（运维/用户指南）发现。取证：`lsof` + 对非回环地址
  `curl` 实测 bind 行为；逐文件读两个 Dockerfile 与 `application.yml`；
  用脚本把 `DEPLOYMENT.md` 里的 compose 片段与真实文件做 `difflib` 逐行对拍。
  **一处刻意不报**：根 Dockerfile 的 healthcheck 依赖 `wget`，而运行阶段是
  Debian 基的 `eclipse-temurin:21-jre`（**非** Alpine）——`wget` 是否存在**本机无法验证**，
  按「没验证的不写」**不下结论**，故未列入本条。


### P2-33（复核记录原文）
- **复核记录**: 第 235 轮代码方向（Demo）发现，方法是对四家 demo 逐个提取路由注册
  后做集合差集——21 个同名、2 个异名，一眼看出不是随机差异而是**成对的同一处分歧**。
  **本轮同时更正了自己在第 229 轮写下的两处事实错误**：那一条把 Go demo 的写入端点
  记作「`main.go:801` 的 `/observations/create`」，而实际是**第 771 行注册的
  `/create-observation`**——`/observations/create` 是 **JS 与 Python** 两家的路径，
  且 801 行是该 handler **函数体内的 `RecordObservation` 调用**而非注册处。
  该错误已同步更正于 `patrol-rotation.md` 与 `doc-review-task.md` 两处轮换记录。
  **第 229 轮的核心结论经复核仍成立**：Go demo 的 `/chat` handler
  （`main.go:170-213`）内 `client.*` 调用**只有 1 个 `BuildICLPrompt`**，
  `RecordObservation` 与 `RecordToolUse` **各 0 次**——`/chat` 确实什么都没记录。


### P2-34（复核记录原文）
- **复核记录**: 第 236 轮代码方向（Backend）首次审 `LogsController`（13 个 controller 里此前未被作为审查对象的一个）。**三个假设在写成发现前被证伪，全部靠实测而非推理**：①**「截断被 appender 持有的日志文件会产生 NUL 空洞」——证伪。** 用 scratch 文件精确复现机制（持久 `FileOutputStream(append=true)` 写 21 字节 → 旁路 `Files.writeString(p,"")` 截断 → appender 再写）：**结果 size=7、NUL=0、内容 `line-4`**，因为**追加模式强制 `O_APPEND`、每次写都落到当前文件末尾**，根本不存在「记住的偏移量」——不做这个实验就会写成一条假发现。②**「appender 写的文件名与控制器读的不一致」——证伪。** `RollingFileAppender` 写 `${APP_NAME}.log` 而控制器读 `claude-mem-{日期}.log`，看着像不匹配； 但磁盘实况显示**正在被写的是带日期的那个**（01:58 仍在增长），`claude-mem.log` 恒 **0 字节**——项目自带 `ClaudeMemLogAppender`（第 222 行）写的正是同一命名。③**路径穿越不成立**：文件名完全由 `LocalDate.now()` 推导，**无任何用户输入进入路径**；`lines` 钳位实测正确（`0`→1、`-5`→1、`99999`→10000），`0x10`→16 属**已记录的 P2-20**。**核实无误**：`API.md` 与 `API-zh-CN.md` 的示例**六个键齐全**、用绝对路径，中文版前文还解释了 `files` 语义——**两版人工文档本来就正确，无需改动**。


### P2-35（复核记录原文）
- **复核记录**: 第 237 轮代码方向（Java SDK）。切入点是读 `interceptToolExecution`
  的控制流时发现 `proceed()` 的位置。取证：`CortexToolAspectTest` **逐条枚举 4 条测试**、
  `QualityScorer` 的评分档与枚举**从文件读**（不用正则数）。


### P2-36（复核记录原文）
- **复核记录**: 第 238 轮代码方向（Go SDK）。切入点是三个适配器各只有一个文件、
  合计仅 33 个测试，是全 SDK 审计最薄的一块。取证：先 `grep` 确认全 SDK
  `context.Background()` / `context.TODO()` **零命中**（ctx 传递这条线是干净的，
  该假设不成立），再逐个读三个适配器；`MaxRetries=0` 导致 `doFireAndForget` 一次都不执行
  的假设也被 `client_impl.go:123` 的钳位证伪。真正下结论靠**两个探针**：
  ①httptest 抓三个适配器的**实际上线报文**（mock 看不到序列化）；②活体打
  `/api/memory/experiences` 与 `/api/memory/icl-prompt`。**探针错一次并先识别再采信**：
  第一次用 scratchpad 空项目做 `count` 探针，四种取值全返 0 条，**无法区分**
  「负数被拒」与「项目本来就没数据」——换成有 22,763 条观测的真实项目才拿到有效对照。


### P2-37（复核记录原文）
- **复核记录**: 第 241 轮代码方向（Demo）。切入点是第 235 轮那句「21 个完全同名」——
  意识到它比对的是**名字**而非**方法+路径**，遂补做方法维度对拍。取证：四个 handler
  的映射注解与 `checkMethod` 逐个读出；启动 Java demo 取 405 活体证据（本会话首次
  启动 Java demo）。**一处刻意不报**：GET 形式返回的 500 是 LLM 密钥失效，
  按「没验证的不写」不写成缺陷。

