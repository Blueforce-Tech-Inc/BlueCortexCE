# Backend Review — Provenance Notes (复核记录)

> **Archived**: 2026-10-04 (round 237) from [`../drafts/backend-review-findings.md`](../drafts/backend-review-findings.md).
> **Contents**: the `- **复核记录**:` sections of P2-22 through P2-27, moved **verbatim**.
> **Why this batch**: that file is structurally saturated — 27 entries, 25 of them ⏸
> "recorded, not fixing", which the working file's own rule requires to be kept because
> they hold the reasoning behind each decision. The `复核记录` section is *provenance*
> (which round found it and how the evidence was gathered), not that reasoning, and the
> same text is stored verbatim per round in `patrol-rotation.md` and
> `doc-review-task.md`. The **Scope / Problem / Status** sections — the decision record
> the rule protects — stay in the working file, each with a one-line pointer here.
> **This is the first move of this kind**; if the rule is later read to cover provenance
> too, these sections can be restored from here without loss.
> **Do not modify this file.**

### P2-24（复核记录原文）
- **复核记录**：第 219 轮代码方向，从 P1-3 的同类线索（时间戳列）出发扩展。
  取证：`information_schema.columns` 确认列集；活体 `count(*)` 确认三张表全为 0；
  `grep -rn "ObservationFeedback" main 源码` 确认除实体与 repository 外**零引用**；
  `grep -n "V17" AGENTS.md CLAUDE.md` 确认其完成度声明。

### P2-22（复核记录原文）
- **复核记录**: 第 210 轮文档方向发现。取证：活体 `GET /api/cursor/projects` →
  `{"count":16,"projects":[{"workspacePath":...,"installedAt":"...","projectName":...}]}`，
  `installedAt` 类型实测为 `str`；`CursorService.java:52` 记录分量为 `String installedAt`。


### P2-23（复核记录原文）
- **复核记录**: 第 212 轮代码方向发现。取证：`grep` 全仓确认无 MVC 层异常处理器；
  裸 socket 并发 105 条得到 `{200: 100, 500: 5}` 的首行分布；第 101 条的完整响应头为
  `HTTP/1.1 500` + `Content-Length: 0`；`grep '@Scheduled'` 列出全部四个定时任务
  （ContextCacheService / MemoryRefineService / PendingMessageProcessor /
  StaleMessageRecoveryTask），**均不触及 SSEBroadcaster**。

### P2-25（复核记录原文）
- **复核记录**: 第 225 轮文档方向发现。取证：`curl /v3/api-docs` 读出该描述原文；
  读 `MemoryController:154` 得真实解析式；上表六组取值逐条实测；`grep "0 = backend default"`
  确认**全仓仅此一处**这样的错误描述；`ExpRagService:188` 复核了 DOC-1 的字符串拼接。

### P2-26（复核记录原文）
- **复核记录**: 第 226 轮代码方向发现。取证：Go 探针 `json.Marshal` 逐项输出；
  `MemoryController:346-375` 读三处分支；活体 PATCH 三步并以 `psycopg2` 直读
  `mem_observations.facts/concepts` 确认落库结果；四家 DTO 源码逐个对拍。
  **探针自身错一次并先识别再采信**：先前用 `GET /api/memory/observations/{id}` 读回，
  但该路径**只有 PATCH 与 DELETE、GET 返回 405**，读到的是错误响应里的 `facts: null`；
  改用 `psycopg2` 直查数据库后才拿到真实值——**没有据此得出「后端不写库」的错误结论**。
  另核实 `docs/API.md` 的会话启动路径是 `/api/session/start`、**正确**，
  幻影路径 `/api/ingest/session-start` 只存在于被 gitignore 的 `AGENTS.md`（已在待决策项）。

### P2-27（复核记录原文）
- **复核记录**: 第 227 轮代码方向发现。取证：Python 探针逐例打印
  `is_empty()` / `to_wire()`；活体两次 PATCH 后以 `psycopg2` 直读
  `mem_observations.extracted_data` 确认 NULL 与 `{}` 两种落库结果；
  `ExtractionController:84,132` 读出 `getExtractedData() != null ? ... : Map.of()`
  确认**读取端**两者确实等价；四家 DTO 源码逐个对拍。
  **探针自身错一次并先识别再采信**：查 `MemoryRefineService` 是否含
  `extractionService.runExtraction` 时，工具输出**明确给出了第 273 行**，
  我却据此断定「该行不存在」并准备按此写结论——**是误读了自己的输出**。
  复查后确认该行就在 `deepRefineProjectMemories`（213–292）内，ordering 属实；
  真正的缺陷是那个方法零调用方（见 0.3.md 的 DOC-1），与本条无关。

